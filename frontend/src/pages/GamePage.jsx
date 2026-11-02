import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  abandonGame,
  deleteGame,
  getGameState,
  playRound,
  rematchGame,
  startGame,
  undoRound,
  resolveBullOff,
} from '../api';
import DartboardInput from '../components/DartboardInput';
import { canSubmitRound, dartInputError, serializeRound } from '../dartRound';
import GameStatsTable from '../components/GameStatsTable';
import GameScoreboard from '../components/GameScoreboard';
import ConfirmDialog from '../components/ConfirmDialog';
import CompletedLegs from '../components/CompletedLegs';
import { matchStats } from '../gameStats';
import { isMatchWinningRound } from '../gameCompletion';
import CricketScoreboard from '../components/CricketScoreboard';
import { cricketRoundPreview } from '../cricket';
import MikkeScoreboard from '../components/MikkeScoreboard';
import MikkeRedemptionNotice from '../components/MikkeRedemptionNotice';
import GameBoardHits from '../components/GameBoardHits';
import MikkeBullOff from '../components/MikkeBullOff';
import MikkeAllocationDialog from '../components/MikkeAllocationDialog';
import {
  needsAllocation,
  allocationChoices,
  pendingAllocationIndex,
  resolveMikkeAllocations,
} from '../mikkeAllocation';

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
  const [enteredSlots, setSlots] = useState(emptySlots);
  const [inputMode, setInputMode] = useState('select');
  const [activeDart, setActiveDart] = useState(0);
  const [managementAction, setManagementAction] = useState(null);
  const [legNotice, setLegNotice] = useState('');
  const [confirmFinish, setConfirmFinish] = useState(false);
  const [allocationPrompt, setAllocationPrompt] = useState(null);
  const rematchBusy = useRef(false);

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
    setAllocationPrompt(null);
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
  const cricket = game.gametype === 'Cricket';
  const mikke = Boolean(game.mikkeMus);
  const slots = mikke
    ? resolveMikkeAllocations(game, enteredSlots)
    : enteredSlots;
  const roundTotal = cricket
    ? (cricketRoundPreview(game, slots)?.scored ?? 0)
    : slots.reduce((sum, slot) => {
        const value = Number(slot.value);
        const multiplier = Number(slot.multiplier);
        return (
          sum + (!dartInputError(slot) && value > 0 ? value * multiplier : 0)
        );
      }, 0);
  const canSubmit = canSubmitRound(slots);

  function updateSlot(index, field, value) {
    if (submitting || allocationPrompt) return;
    const next = slots.map((slot, i) =>
      i === index ? { ...slot, [field]: value } : slot
    );
    if (field === 'value' && (Number(value) === 0 || Number(value) === 25)) {
      next[index].multiplier = 1;
    }
    if (field === 'value' || field === 'multiplier') {
      delete next[index].automaticAllocation;
      next[index].allocation =
        mikke && needsAllocation(next[index])
          ? null
          : Number(next[index].value) === 25
            ? 'BULL'
            : 'NUMBER';
    }
    setSlots(next);
    if (
      mikke &&
      field === 'multiplier' &&
      needsAllocation(resolveMikkeAllocations(game, next)[index])
    )
      setAllocationPrompt({ index });
  }

  function recordBoardHit(value, multiplier) {
    if (submitting || allocationPrompt) return;
    const hit = {
      value,
      multiplier,
      allocation:
        mikke && needsAllocation({ value, multiplier })
          ? null
          : value === 25
            ? 'BULL'
            : 'NUMBER',
    };
    setSlots((prev) =>
      prev.map((slot, index) =>
        index === activeDart
          ? {
              ...hit,
            }
          : slot
      )
    );
    if (
      mikke &&
      needsAllocation(
        resolveMikkeAllocations(
          game,
          slots.map((slot, index) => (index === activeDart ? hit : slot))
        )[activeDart]
      )
    )
      setAllocationPrompt({ index: activeDart, advance: true });
    else setActiveDart((activeDart + 1) % slots.length);
  }

  function chooseAllocation(allocation) {
    const { index, advance } = allocationPrompt;
    setSlots((previous) =>
      previous.map((slot, i) => (i === index ? { ...slot, allocation } : slot))
    );
    setAllocationPrompt(null);
    if (advance) setActiveDart((index + 1) % slots.length);
  }

  async function handleUndo() {
    if (submitting || allocationPrompt || !game.permissions.canUndo) return;
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
    if (!canSubmit || submitting || allocationPrompt) return;
    const unresolved = mikke ? pendingAllocationIndex(slots) : -1;
    if (unresolved >= 0) {
      setAllocationPrompt({ index: unresolved });
      return;
    }
    setActionError('');
    if (isMatchWinningRound(game, slots)) {
      setConfirmFinish(true);
    } else {
      submitRound();
    }
  }

  async function submitRound() {
    if (
      !canSubmit ||
      submitting ||
      allocationPrompt ||
      (mikke && pendingAllocationIndex(slots) >= 0)
    )
      return;
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
        ...(mikke
          ? {
              allocations: slots.map(
                (slot) =>
                  slot.allocation ??
                  (Number(slot.value) === 25 ? 'BULL' : 'NUMBER')
              ),
            }
          : {}),
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

  async function handleBullOff(region, winner, rethrow) {
    if (submitting || !game.permissions.canScore) return;
    setSubmitting(true);
    setActionError('');
    try {
      const fresh =
        region != null
          ? await playRound({
              gameId,
              username: game.turn,
              legId: game.currentLegId,
              revision: game.revision,
              score: region === 0 ? '0 1' : `25 ${region}`,
            })
          : await resolveBullOff({
              gameId,
              legId: game.currentLegId,
              revision: game.revision,
              winner,
              rethrow,
            });
      setGame(fresh);
      setSlots(emptySlots());
      setActiveDart(0);
      if (fresh.currentLegId !== game.currentLegId)
        setLegNotice('Leg complete. The next leg is ready.');
    } catch (err) {
      if ([403, 409].includes(err.status)) {
        try {
          setGame(await getGameState({ gameId }));
          setSlots(emptySlots());
        } catch {
          setActionError('Unable to reload game. Refresh before continuing.');
          return;
        }
      }
      setActionError(`Failed to save bull-off: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRematch() {
    if (rematchBusy.current || submitting || !game.permissions.canRematch)
      return;
    rematchBusy.current = true;
    setSubmitting(true);
    setActionError('');
    try {
      const fresh = await rematchGame({ gameId });
      navigate(`/games/${fresh.gameId}`);
    } catch (err) {
      setActionError(`Failed to start rematch: ${err.message}`);
    } finally {
      rematchBusy.current = false;
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
        <Link
          className="btn game-back-link"
          to={finished ? '/completed-games' : '/games'}
        >
          &larr; {finished ? 'Back to Completed games' : 'Back to Games'}
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

      {mikke ? (
        <>
          <MikkeRedemptionNotice
            key={`${game.currentLegId}-${game.mikkeMus.phase}-${game.mikkeMus.phase === 'BULL_OFF' ? game.mikkeMus.bullAttempt : game.turn}`}
            game={game}
          />
          <MikkeScoreboard
            showStats={false}
            game={game}
            detailPlayers={finished ? matchStats(game) : game.players}
          />
        </>
      ) : cricket ? (
        <CricketScoreboard
          game={game}
          detailPlayers={finished ? matchStats(game) : game.players}
        />
      ) : (
        !finished && <GameScoreboard game={game} slots={slots} />
      )}

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
          {!cricket && !mikke && (
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
          )}
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
      ) : mikke &&
        ['BULL_OFF', 'BULL_DECISION'].includes(game.mikkeMus.phase) ? (
        <MikkeBullOff
          game={game}
          busy={submitting}
          onUndo={handleUndo}
          onThrow={(region) => handleBullOff(region)}
          onDecision={(winner, rethrow) => handleBullOff(null, winner, rethrow)}
        />
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
                    <input
                      aria-label={`Dart ${index + 1} number`}
                      type="text"
                      inputMode="numeric"
                      pattern="([1-9]|1[0-9]|20|25)"
                      placeholder="1–20 or 25"
                      value={slot.value}
                      onBlur={() => {
                        if (mikke && needsAllocation(slot) && !slot.allocation)
                          setAllocationPrompt({ index });
                      }}
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
                    <span
                      className="game-multiplier-label"
                      id={`dart-${index}-multiplier`}
                    >
                      Dart {index + 1} multiplier
                    </span>
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
          {mikke && (
            <div className="mikke-allocations">
              {slots.map(
                (slot, index) =>
                  slot.value !== '' &&
                  needsAllocation(slot) && (
                    <button
                      type="button"
                      className="btn"
                      key={index}
                      disabled={submitting}
                      onClick={() => setAllocationPrompt({ index })}
                    >
                      Dart {index + 1}:{' '}
                      {allocationChoices(slot).find(
                        (choice) => choice.value === slot.allocation
                      )?.label ?? 'Choose how it counts'}
                    </button>
                  )
              )}
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
            {!mikke && (
              <div className="game-round-total">
                {cricket ? 'Round points' : 'Round total'}: {roundTotal}
              </div>
            )}
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
      {mikke && (
        <GameBoardHits players={finished ? matchStats(game) : game.players} />
      )}
      <CompletedLegs game={game} />
      {allocationPrompt && (
        <MikkeAllocationDialog
          slot={slots[allocationPrompt.index]}
          index={allocationPrompt.index}
          onChoose={chooseAllocation}
          onCancel={() => setAllocationPrompt(null)}
        />
      )}
      {finished && game.permissions.canRematch && (
        <div className="game-round-panel">
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleRematch}
            disabled={submitting}
          >
            {submitting ? 'Starting rematch…' : 'Rematch'}
          </button>
        </div>
      )}
      {confirmFinish && (
        <ConfirmDialog
          title={mikke ? 'Has the player finished?' : 'Was the game completed?'}
          body={
            mikke
              ? `These throws close all targets for ${activePlayer}. Confirm the finish, or go back to correct the throws. Eligible players still get redemption; tied finishers throw for bull.`
              : `These throws give ${activePlayer} the match win. Confirm the final round, or go back to correct the throws.`
          }
          confirmLabel={mikke ? 'Yes, confirm finish' : 'Yes, finish game'}
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
