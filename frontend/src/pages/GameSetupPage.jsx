import { useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { createGame, deleteGame, getUsername, startGame } from '../api';
import LegTargetPicker from '../components/LegTargetPicker';
import { startDraftGame } from '../gameSetup';

export default function GameSetupPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const draft = state?.draft;
  const [legsToWin, setLegsToWin] = useState('1');
  const [finish, setFinish] = useState(
    draft?.finishes?.double ? 'double' : 'single'
  );
  const [pendingGame, setPendingGame] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const busy = useRef(false);

  if (!draft) return <Navigate to="/games" replace />;

  const owner = getUsername(localStorage.getItem('token'));

  async function handleStart(event) {
    event.preventDefault();
    if (busy.current) return;
    busy.current = true;
    setSubmitting(true);
    setError('');
    try {
      const game = await startDraftGame(
        {
          gametype: draft.finishes[finish],
          players: draft.players,
          guests: draft.guests ?? [],
          legsToWin: Number(legsToWin),
        },
        { createGame, startGame, deleteGame },
        pendingGame
      );
      navigate(`/games/${game.gameId}`, { replace: true });
    } catch (err) {
      setPendingGame(err.pendingGame ?? null);
      setError(
        err.status === 404
          ? 'One or more usernames not found — go back and check the spelling.'
          : err.message
      );
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  async function handleCancel() {
    if (busy.current) return;
    busy.current = true;
    setSubmitting(true);
    setError('');
    try {
      if (pendingGame) await deleteGame({ gameId: pendingGame.gameId });
      navigate('/games', { replace: true });
    } catch (err) {
      setError(`Unable to cancel: ${err.message}`);
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="page game-page game-page-in-progress game-setup-page">
      <div className="game-page-header">
        <button
          type="button"
          className="btn game-back-link"
          onClick={handleCancel}
          disabled={submitting}
        >
          &larr; Cancel setup
        </button>
        <span className="game-format-label">{draft.gametype} · Game setup</span>
      </div>
      <form
        className="game-round-panel game-setup-panel"
        onSubmit={handleStart}
      >
        <h1>Ready to play {draft.gametype}?</h1>
        <h2>Players</h2>
        <div className="player-chips">
          <span className="player-chip player-chip-owner">{owner} (you)</span>
          {draft.players.map((username) => (
            <span key={username} className="player-chip">
              {username}
            </span>
          ))}
          {(draft.guests ?? []).map((name) => (
            <span key={name} className="player-chip">
              {name} <small className="player-guest-label">Guest</small>
            </span>
          ))}
        </div>
        {draft.guests?.length > 0 && (
          <p className="player-hint">
            Guests have no account. Their results stay in this match, not in a
            profile.
          </p>
        )}
        <LegTargetPicker
          value={legsToWin}
          onChange={setLegsToWin}
          disabled={submitting || Boolean(pendingGame)}
        />
        <p className="player-hint" id="legs-to-win-hint">
          Choose 1 for a single leg, or 2–99 for a first-to-wins game. Starting
          player rotates each leg.
        </p>
        <fieldset
          className="game-finish-picker"
          disabled={submitting || Boolean(pendingGame)}
        >
          <legend>Finishing rule</legend>
          {Object.keys(draft.finishes).map((rule) => (
            <label key={rule}>
              <input
                type="radio"
                name="finish"
                value={rule}
                checked={finish === rule}
                onChange={(event) => setFinish(event.target.value)}
              />
              {rule === 'single' ? 'Single out' : 'Double out'}
            </label>
          ))}
        </fieldset>
        <p className="player-hint">
          {finish === 'single'
            ? 'Reach exactly zero with any dart.'
            : 'Reach exactly zero with a double.'}
        </p>
        <p className="game-shared-hint">
          {pendingGame
            ? 'A pending game is waiting to start. Retry or cancel to remove it.'
            : 'Nothing is saved until you press Start game.'}
        </p>
        {error && (
          <p className="error-message" role="alert">
            {error}
          </p>
        )}
        <div className="game-round-actions">
          <button
            type="button"
            className="btn"
            onClick={handleCancel}
            disabled={submitting}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting}
          >
            {submitting ? 'Please wait…' : 'Start game'}
          </button>
        </div>
      </form>
    </div>
  );
}
