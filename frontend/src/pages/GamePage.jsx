import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { abandonGame, deleteGame, getGameState, playRound, startGame } from '../api';
import DartboardInput from '../components/DartboardInput';
import { canSubmitRound, serializeRound, undoLastDart } from '../dartRound';
import GameStatsTable from '../components/GameStatsTable';
import CheckoutSuggestion from '../components/CheckoutSuggestion';
import ConfirmDialog from '../components/ConfirmDialog';

// Array of all numbers from 1-20+25
const DART_VALUES = Array.from({ length: 20 }, (_, i) => i + 1).concat(25);

function emptySlots() {
  return [
    { value: '', multiplier: 1 },
    { value: '', multiplier: 1 },
    { value: '', multiplier: 1 },
  ];
}

export default function GamePage() {
  const { gameId } = useParams();
  const navigate = useNavigate();
  const [game, setGame] = useState(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [slots, setSlots] = useState(emptySlots);
  const [inputMode, setInputMode] = useState('board');
  const [activeDart, setActiveDart] = useState(0);
  const [managementAction, setManagementAction] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setError('');
    setGame(null);
    setSlots(emptySlots());
    setActiveDart(0);
    setManagementAction(null);
    setActionError('');
    getGameState({ gameId })
      .then((data) => {
        if (!cancelled) setGame(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [gameId]);

  if (error) {
    return (
      <div className="page game-page">
        <p className="error-message">Failed to load game: {error}</p>
        <Link className="btn game-back-link" to="/games">
          &larr; Back to Games
        </Link>
      </div>
    );
  }

  if (game === null) {
    return (
      <div className="page game-page">
        <p className="state-message">Loading game...</p>
      </div>
    );
  }

  const finished = ['COMPLETED', 'ABANDONED'].includes(game.status);
  const activePlayer = game.turn;
  const roundTotal = slots.reduce((sum, slot) => {
    const value = Number(slot.value);
    const multiplier = Number(slot.multiplier);
    return sum + (value > 0 && multiplier > 0 ? value * multiplier : 0);
  }, 0);
  const canSubmit = canSubmitRound(slots);

  function updateSlot(index, field, value) {
    setSlots((prev) => {
      const next = prev.map((slot, i) =>
        i === index ? { ...slot, [field]: value } : slot
      );
      if (
        field === 'value' &&
        (Number(value) === 0 || Number(value) === 25)
      ) {
        next[index].multiplier = 1;
      }
      return next;
    });
  }

  function recordBoardHit(value, multiplier) {
    if (submitting) return;
    setSlots((prev) =>
      prev.map((slot, index) =>
        index === activeDart ? { ...slot, value, multiplier } : slot
      )
    );
    setActiveDart((activeDart + 1) % slots.length);
  }

  function handleUndo() {
    if (submitting) return;
    const undone = undoLastDart(slots);
    setSlots(undone.slots);
    setActiveDart(undone.activeDart);
    setActionError('');
  }

  async function handleSubmit() {
    if (!canSubmit || submitting) return;
    setSubmitting(true);
    setActionError('');
    try {
      const score = serializeRound(slots);
      const fresh = await playRound({
        gameId,
        username: activePlayer,
        score,
        legId: game.currentLegId,
        revision: game.revision,
      });
      setGame(fresh);
      setSlots(emptySlots());
      setActiveDart(0);
    } catch (err) {
      if (err.status === 403 || err.status === 409) {
        try {
          setGame(await getGameState({ gameId }));
          setSlots(emptySlots());
          setActiveDart(0);
          setActionError('Game state changed. Check the current player and enter the round again.');
        } catch (reloadError) {
          setActionError(`Unable to reload game: ${reloadError.message}`);
        }
      } else {
        setActionError(`Failed to register round: ${err.message}`);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleStart() {
    setSubmitting(true);
    setActionError('');
    try {
      setGame(await startGame({ gameId }));
    } catch (err) {
      setActionError(`Failed to start game: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleManagement() {
    setSubmitting(true);
    setActionError('');
    try {
      if (managementAction === 'delete') {
        await deleteGame({ gameId });
        navigate('/games');
      } else {
        setGame(await abandonGame({ gameId }));
        setSlots(emptySlots());
      }
      setManagementAction(null);
    } catch (err) {
      setActionError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const multipliersFor = (slot) =>
    Number(slot.value) === 0
      ? [1]
      : Number(slot.value) === 25
        ? [1, 2]
        : [1, 2, 3];

  return (
    <div
      className={`page game-page${
        finished ? ' game-page-finished' : ' game-page-in-progress'
      }`}
    >
      <div className="game-page-header">
        <Link className="btn game-back-link" to="/games">
          &larr; Back to Games
        </Link>
        <span className="game-format-label">{game.gametype} · Single leg · Double-out</span>
        {(game.permissions.canDelete || game.permissions.canAbandon) && (
          <button className="btn game-manage-button" type="button" disabled={submitting}
            onClick={() => { setActionError(''); setManagementAction(game.permissions.canDelete ? 'delete' : 'abandon'); }}>
            {game.permissions.canDelete ? 'Delete game' : 'Abandon game'}
          </button>
        )}
      </div>

      {actionError && !managementAction && <p className="error-message" role="alert">{actionError}</p>}

      {!finished && (
        <>
          <h2>Scoreboard</h2>
          <div className="game-scoreboard">
            {game.players.map((player) => (
              <div
                key={player.username}
                className={`game-scoreboard-row${
                  player.username === game.turn ? ' game-scoreboard-turn' : ''
                }`}
              >
                <span>{player.username}</span>
                <span className="game-scoreboard-stats">
                  {player.points}{' '}
                  <span className="game-scoreboard-turns">
                    Turns: {player.turns}
                  </span>
                  {[301, 501].includes(game.startingPoints) && (
                    <CheckoutSuggestion
                      points={player.points}
                      slots={player.username === activePlayer ? slots : []}
                    />
                  )}
                </span>
              </div>
            ))}
          </div>
        </>
      )}

      {finished ? (
        <>
          <div className="game-winner">
            <p className="game-result-label">{game.status === 'ABANDONED' ? 'Game abandoned' : 'Game complete'}</p>
            <h2>{game.winner || 'No winner'}</h2>
            <p className="game-result-summary">{game.winner ? 'Takes the win' : 'History saved. This leg does not count towards player totals.'}</p>
          </div>
          <div className="game-stats-section">
            <h2>Game stats</h2>
            <GameStatsTable
              players={game.players}
              winner={game.winner}
              startingPoints={game.startingPoints}
            />
          </div>
        </>
      ) : game.status === 'PENDING' ? (
        <div className="game-round-panel">
          <h2>Ready to play</h2>
          <p className="game-shared-hint">{game.players.map((player) => player.username).join(' · ')}</p>
          {game.permissions.canStart && (
            <button type="button" className="btn btn-primary" onClick={handleStart} disabled={submitting}>
              {submitting ? 'Starting…' : 'Start game'}
            </button>
          )}
        </div>
      ) : game.permissions.canScore ? (
        <div className="game-round-panel">
          <div className="game-turn-banner">Next up: {activePlayer}</div>
          <div
            className="game-input-mode"
            role="group"
            aria-label="Score input method"
          >
            <button
              type="button"
              className={inputMode === 'board' ? 'game-input-mode-active' : ''}
              onClick={() => setInputMode('board')}
              disabled={submitting}
            >
              Dartboard
            </button>
            <button
              type="button"
              className={inputMode === 'select' ? 'game-input-mode-active' : ''}
              onClick={() => setInputMode('select')}
              disabled={submitting}
            >
              Select
            </button>
          </div>
          {inputMode === 'select' ? (
            <div className="game-dart-slots">
              {slots.map((slot, index) => (
                <div key={index} className="game-dart-slot">
                  <label>
                    <span>D{index + 1} number</span>
                    <select
                      value={slot.value}
                      onChange={(e) =>
                        updateSlot(index, 'value', e.target.value)
                      }
                      disabled={submitting}
                    >
                      <option value="">—</option>
                      <option value="0">Miss (0)</option>
                      {DART_VALUES.map((value) => (
                        <option key={value} value={value}>
                          {value}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>Multiplier</span>
                    <select
                      value={slot.multiplier}
                      onChange={(e) =>
                        updateSlot(index, 'multiplier', e.target.value)
                      }
                      disabled={submitting || Number(slot.value) === 0}
                    >
                      {multipliersFor(slot).map((mult) => (
                        <option key={mult} value={mult}>
                          &times;{mult}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              ))}
            </div>
          ) : (
            <div className="game-board-entry">
              <div className="game-board-slots">
                {slots.map((slot, index) => (
                  <button
                    key={index}
                    type="button"
                    className={
                      index === activeDart ? 'game-board-slot-active' : ''
                    }
                    onClick={() => setActiveDart(index)}
                    disabled={submitting}
                  >
                    D{index + 1}:{' '}
                    {slot.value === ''
                      ? 'Select a target'
                      : Number(slot.value) === 0
                        ? 'Miss'
                        : `${slot.multiplier > 1 ? `x${slot.multiplier} ` : ''}${slot.value}`}
                  </button>
                ))}
              </div>
              <DartboardInput onHit={recordBoardHit} disabled={submitting} />
            </div>
          )}
          <div className="game-round-total">Round total: {roundTotal}</div>
          <div className="game-round-actions">
            <button
              type="button"
              className="btn game-undo"
              onClick={handleUndo}
              disabled={submitting || slots.every((slot) => slot.value === '')}
            >
              Undo last dart
            </button>
            <button
              type="button"
              className="btn game-register"
              onClick={handleSubmit}
              disabled={!canSubmit || submitting}
            >
              {submitting ? 'Registering...' : 'Register Round'}
            </button>
          </div>
          <p className="game-shared-hint">
            Playing on one device — each player enters their throws when it's
            their turn.
            {' '}Enter 1–3 darts in order; leave unused darts blank. Finish on a double.
            {' '}Register an empty round if all darts missed.
          </p>
        </div>
      ) : <p className="state-message">This game is in progress. Only participants can score.</p>}
      {managementAction && (
        <ConfirmDialog
          title={managementAction === 'delete' ? 'Delete this game?' : 'Abandon this game?'}
          body={managementAction === 'delete'
            ? 'This permanently deletes the pending game. No played history is removed.'
            : 'The game will end without a winner. Scores are preserved, but the unfinished leg will not count towards player totals.'}
          confirmLabel={managementAction === 'delete' ? 'Delete game' : 'Abandon game'}
          busyLabel="Saving…"
          busy={submitting}
          error={actionError}
          onConfirm={handleManagement}
          onCancel={() => { if (!submitting) { setManagementAction(null); setActionError(''); } }}
        />
      )}
    </div>
  );
}
