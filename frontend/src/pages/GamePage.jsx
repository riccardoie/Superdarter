import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  abandonGame,
  deleteGame,
  getGameState,
  playRound,
  startGame,
  undoRound,
} from '../api';
import DartboardInput from '../components/DartboardInput';
import { canSubmitRound, dartInputError, serializeRound } from '../dartRound';
import GameStatsTable from '../components/GameStatsTable';
import GameScoreboard from '../components/GameScoreboard';
import ConfirmDialog from '../components/ConfirmDialog';
import CompletedLegs from '../components/CompletedLegs';
import { matchStats } from '../gameStats';
import { isMatchWinningRound } from '../gameCompletion';

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
  const [inputMode, setInputMode] = useState('select');
  const [activeDart, setActiveDart] = useState(0);
  const [managementAction, setManagementAction] = useState(null);
  const [legNotice, setLegNotice] = useState('');
  const [confirmFinish, setConfirmFinish] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setError('');
    setGame(null);
    setSlots(emptySlots());
    setActiveDart(0);
    setManagementAction(null);
    setActionError('');
    setLegNotice('');
    setConfirmFinish(false);
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
  const boardFocus =
    !finished &&
    game.status !== 'PENDING' &&
    game.permissions.canScore &&
    inputMode === 'board';
  const activePlayer = game.turn;
  const roundTotal = slots.reduce((sum, slot) => {
    const value = Number(slot.value);
    const multiplier = Number(slot.multiplier);
    return sum + (!dartInputError(slot) && value > 0 ? value * multiplier : 0);
  }, 0);
  const canSubmit = canSubmitRound(slots);

  function updateSlot(index, field, value) {
    setSlots((prev) => {
      const next = prev.map((slot, i) =>
        i === index ? { ...slot, [field]: value } : slot
      );
      if (field === 'value' && (Number(value) === 0 || Number(value) === 25)) {
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

  async function handleUndo() {
    if (submitting || !game.permissions.canUndo) return;
    setSubmitting(true);
    setActionError('');
    try {
      setGame(await undoRound({ gameId, revision: game.revision }));
      setSlots(emptySlots());
      setActiveDart(0);
      setLegNotice('Previous round undone. Enter the corrected throws.');
    } catch (err) {
      if (err.status === 403 || err.status === 409) {
        try {
          setGame(await getGameState({ gameId }));
          setSlots(emptySlots());
          setActiveDart(0);
          setLegNotice('');
        } catch (reloadError) {
          setActionError(`Unable to reload game: ${reloadError.message}`);
          return;
        }
      }
      setActionError(`Failed to undo round: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit() {
    if (!canSubmit || submitting) return;
    setActionError('');
    if (isMatchWinningRound(game, slots)) {
      setConfirmFinish(true);
    } else {
      submitRound();
    }
  }

  async function submitRound() {
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
      if (fresh.currentLegId !== game.currentLegId) {
        const completed = fresh.legs.find(
          (leg) => leg.legId === game.currentLegId
        );
        setLegNotice(
          `${completed.winner} wins leg ${completed.number}. The next leg is ready.`
        );
      } else {
        setLegNotice('');
      }
      setGame(fresh);
      setConfirmFinish(false);
      setSlots(emptySlots());
      setActiveDart(0);
    } catch (err) {
      if (err.status === 403 || err.status === 409) {
        try {
          setGame(await getGameState({ gameId }));
          setConfirmFinish(false);
          setSlots(emptySlots());
          setActiveDart(0);
          setLegNotice('');
          setActionError(
            'Game state changed. Check the current player and enter the round again.'
          );
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
        const ended = await abandonGame({ gameId });
        if (ended.legs.some((leg) => leg.status === 'COMPLETED')) {
          setGame(ended);
          setSlots(emptySlots());
        } else {
          navigate('/games');
        }
      }
      setManagementAction(null);
    } catch (err) {
      setActionError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className={`page game-page${
        finished ? ' game-page-finished' : ' game-page-in-progress'
      }${boardFocus ? ' game-page-board-focus' : ''}`}
    >
      <div className="game-page-header">
        <Link className="btn game-back-link" to="/games">
          &larr; Back to Games
        </Link>
        {(game.permissions.canDelete || game.permissions.canAbandon) && (
          <button
            className="btn game-manage-button"
            type="button"
            disabled={submitting}
            onClick={() => {
              setActionError('');
              setManagementAction(
                game.permissions.canDelete ? 'delete' : 'abandon'
              );
            }}
          >
            {game.permissions.canDelete ? 'Delete game' : 'Abandon game'}
          </button>
        )}
      </div>

      {actionError && !managementAction && !confirmFinish && (
        <p className="error-message" role="alert">
          {actionError}
        </p>
      )}
      {legNotice && !finished && (
        <p className="game-leg-notice" role="status">
          {legNotice}
        </p>
      )}

      {!finished && <GameScoreboard game={game} slots={slots} />}

      {finished ? (
        <>
          <div className="game-winner">
            <p className="game-result-label">
              {game.status === 'ABANDONED' ? 'Game abandoned' : 'Game complete'}
            </p>
            <h2>{game.winner || 'No winner'}</h2>
            <p className="game-result-summary">
              {game.winner
                ? 'Takes the win'
                : 'History saved. Completed legs count towards player totals; the unfinished leg does not.'}
            </p>
          </div>
          <div className="game-stats-section">
            <h2>
              {game.legsToWin > 1 ? 'Match stats — all legs' : 'Game stats'}
            </h2>
            <GameStatsTable
              players={matchStats(game)}
              showTurns={false}
              showLegsWon
              winner={game.winner}
              startingPoints={game.startingPoints}
            />
          </div>
        </>
      ) : game.status === 'PENDING' ? (
        <div className="game-round-panel">
          <h2>Ready to play</h2>
          <p className="game-shared-hint">
            {game.players
              .map(
                (player) =>
                  `${player.username}${player.guest ? ' (guest)' : ''}`
              )
              .join(' · ')}
          </p>
          {game.permissions.canStart && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleStart}
              disabled={submitting}
            >
              {submitting ? 'Starting…' : 'Start game'}
            </button>
          )}
        </div>
      ) : game.permissions.canScore ? (
        <div className="game-round-panel">
          <div
            className="game-input-mode"
            role="group"
            aria-label="Score input method"
          >
            <button
              type="button"
              className={inputMode === 'select' ? 'game-input-mode-active' : ''}
              onClick={() => setInputMode('select')}
              disabled={submitting}
            >
              Text
            </button>
            <button
              type="button"
              className={inputMode === 'board' ? 'game-input-mode-active' : ''}
              onClick={() => setInputMode('board')}
              disabled={submitting}
            >
              Dartboard
            </button>
          </div>
          {inputMode === 'select' ? (
            <div className="game-dart-slots">
              {slots.map((slot, index) => (
                <div key={index} className="game-dart-slot">
                  <label>
                    <span>D{index + 1} number</span>
                    <input
                      type="text"
                      inputMode="numeric"
                      pattern="([1-9]|1[0-9]|20|25)"
                      placeholder="1–20 or 25"
                      value={slot.value}
                      onChange={(e) => {
                        if (e.target.value !== '0') {
                          updateSlot(index, 'value', e.target.value);
                        }
                      }}
                      aria-describedby={`dart-${index}-error`}
                      aria-invalid={!!dartInputError(slot)}
                      disabled={submitting}
                    />
                  </label>
                  <div className="game-multiplier-field">
                    <span id={`dart-${index}-multiplier`}>Multiplier</span>
                    <div
                      className="game-multiplier-options"
                      role="group"
                      aria-labelledby={`dart-${index}-multiplier`}
                    >
                      {[1, 2, 3].map((multiplier) => (
                        <button
                          key={multiplier}
                          type="button"
                          aria-pressed={Number(slot.multiplier) === multiplier}
                          disabled={
                            submitting ||
                            (slot.value !== '' && Number(slot.value) === 0) ||
                            (Number(slot.value) === 25 && multiplier === 3)
                          }
                          onClick={() =>
                            updateSlot(index, 'multiplier', multiplier)
                          }
                        >
                          {multiplier}
                        </button>
                      ))}
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn"
                    disabled={submitting}
                    onClick={() => updateSlot(index, 'value', 0)}
                  >
                    Miss
                  </button>
                  {dartInputError(slot) && (
                    <span
                      id={`dart-${index}-error`}
                      className="error-message"
                      role="alert"
                    >
                      {dartInputError(slot)}
                    </span>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="game-board-entry">
              <DartboardInput onHit={recordBoardHit} disabled={submitting} />
            </div>
          )}
          <div
            className={
              boardFocus ? 'game-board-controls' : 'game-text-controls'
            }
          >
            {boardFocus && (
              <div className="game-board-slots">
                {slots.map((slot, index) => (
                  <button
                    key={index}
                    type="button"
                    className={
                      index === activeDart ? 'game-board-slot-active' : ''
                    }
                    onClick={() => setActiveDart(index)}
                    aria-pressed={index === activeDart}
                    disabled={submitting}
                  >
                    D{index + 1}:{' '}
                    {slot.value === ''
                      ? '—'
                      : Number(slot.value) === 0
                        ? 'Miss'
                        : `${slot.multiplier > 1 ? `x${slot.multiplier} ` : ''}${slot.value}`}
                  </button>
                ))}
              </div>
            )}
            <div className="game-round-total">Round total: {roundTotal}</div>
            <div className="game-round-actions">
              <button
                type="button"
                className="btn game-undo"
                onClick={handleUndo}
                disabled={submitting || !game.permissions.canUndo}
              >
                {boardFocus ? 'Undo round' : 'Undo previous round'}
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
          </div>
        </div>
      ) : (
        <p className="state-message">
          This game is in progress. Only participants can score.
        </p>
      )}
      <CompletedLegs game={game} />
      {confirmFinish && (
        <ConfirmDialog
          title="Was the game completed?"
          body={`These throws give ${activePlayer} the match win. Confirm the final round, or go back to correct the throws.`}
          confirmLabel="Yes, finish game"
          cancelLabel="No, correct throws"
          confirmClassName="btn btn-primary"
          busyLabel="Finishing…"
          busy={submitting}
          error={actionError}
          onConfirm={submitRound}
          onCancel={() => {
            if (!submitting) {
              setConfirmFinish(false);
              setActionError('');
            }
          }}
        />
      )}
      {managementAction && (
        <ConfirmDialog
          title={
            managementAction === 'delete'
              ? 'Delete this game?'
              : 'Abandon this game?'
          }
          body={
            managementAction === 'delete'
              ? 'This permanently deletes the pending game. No played history is removed.'
              : game.legs.some((leg) => leg.status === 'COMPLETED')
                ? 'The game will end without an overall winner. Completed leg results and statistics are kept; the unfinished leg will not count towards player totals.'
                : 'No leg has been completed. This permanently deletes the game and all its scores. No statistics will be kept.'
          }
          confirmLabel={
            managementAction === 'delete' ? 'Delete game' : 'Abandon game'
          }
          busyLabel="Saving…"
          busy={submitting}
          error={actionError}
          onConfirm={handleManagement}
          onCancel={() => {
            if (!submitting) {
              setManagementAction(null);
              setActionError('');
            }
          }}
        />
      )}
    </div>
  );
}
