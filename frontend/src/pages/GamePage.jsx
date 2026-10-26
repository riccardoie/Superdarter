import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getGameState, playRound } from '../api';
import DartboardInput from '../components/DartboardInput';
import GameStatsTable from '../components/GameStatsTable';

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
  const { id, gameId } = useParams();
  const [game, setGame] = useState(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [slots, setSlots] = useState(emptySlots);
  const [inputMode, setInputMode] = useState('select');
  const [activeDart, setActiveDart] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getGameState({ sessionId: id, gameId })
      .then((data) => {
        if (!cancelled) setGame(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [id, gameId]);

  if (error) {
    return (
      <div className="page game-page">
        <p className="error-message">Failed to load game: {error}</p>
        <Link className="btn session-detail-back" to={`/session/${id}`}>
          &larr; Back to Session
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

  const finished = game.isFinished;
  const activePlayer = game.turn;
  const roundTotal = slots.reduce((sum, slot) => {
    const value = Number(slot.value);
    const multiplier = Number(slot.multiplier);
    return sum + (value > 0 && multiplier > 0 ? value * multiplier : 0);
  }, 0);
  const allFilled = slots.every((slot) => slot.value !== '');

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

  async function handleSubmit() {
    setSubmitting(true);
    setActionError('');
    try {
      const score = slots
        .filter((slot) => Number(slot.value) > 0)
        .map((slot) => `${slot.value} ${slot.multiplier}`)
        .join(' ');
      await playRound({
        sessionId: id,
        gameId,
        username: activePlayer,
        score,
      });
      const fresh = await getGameState({ sessionId: id, gameId });
      setGame(fresh);
      setSlots(emptySlots());
      setActiveDart(0);
    } catch (err) {
      if (err.status === 403) {
        const fresh = await getGameState({ sessionId: id, gameId });
        setGame(fresh);
        setActionError(
          "It's not that player's turn — play the round for the highlighted player."
        );
      } else {
        setActionError(`Failed to register round: ${err.message}`);
      }
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
        <Link className="btn session-detail-back" to={`/session/${id}`}>
          &larr; Back to Session
        </Link>
      </div>

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
                <span>
                  {player.points}{' '}
                  <span className="game-scoreboard-turns">
                    Turns: {player.turns}
                  </span>
                </span>
              </div>
            ))}
          </div>
        </>
      )}

      {finished ? (
        <>
          <div className="game-winner">
            <p className="game-result-label">Game complete</p>
            <h2>{game.winner || game.turn}</h2>
            <p className="game-result-summary">Takes the win</p>
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
      ) : (
        <div className="game-round-panel">
          <div className="game-turn-banner">Next up: {activePlayer}</div>
          <p className="game-shared-hint">
            Playing on one device — each player enters their throws when it's
            their turn.
          </p>
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
              Select
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
              <button
                type="button"
                className="game-board-miss"
                onClick={() => recordBoardHit(0, 1)}
                disabled={submitting}
              >
                Record miss for D{activeDart + 1}
              </button>
            </div>
          )}
          <div className="game-round-total">Round total: {roundTotal}</div>
          {actionError && <p className="error-message">{actionError}</p>}
          <button
            type="button"
            className="btn game-register"
            onClick={handleSubmit}
            disabled={!allFilled || submitting}
          >
            {submitting ? 'Registering...' : 'Register Round'}
          </button>
        </div>
      )}
    </div>
  );
}
