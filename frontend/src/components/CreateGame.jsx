import { useEffect, useState } from 'react';
import { createGame, getAllGamemodes, getUsername, MAX_TOTAL_PLAYERS } from '../api';
import LegTargetPicker from './LegTargetPicker';

const MAX_ADDED_PLAYERS = MAX_TOTAL_PLAYERS - 1;

export default function CreateGame({ onClose, onCreated }) {
  const [available, setAvailable] = useState(null);
  const [gametype, setGametype] = useState('501');
  const [legsToWin, setLegsToWin] = useState('1');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [players, setPlayers] = useState([]);
  const [playerInput, setPlayerInput] = useState('');
  const [playerError, setPlayerError] = useState('');

  const owner = getUsername(localStorage.getItem('token'));

  useEffect(() => {
    getAllGamemodes()
      .then((data) => setAvailable(data.filter((g) => ['301', '501'].includes(g.gamemode))))
      .catch((err) => setError(err.message));
  }, []);

  function addPlayer() {
    const username = playerInput.trim();
    if (!username.trim()) {
      setPlayerError('Enter a username');
      return;
    }
    if (username === owner) {
      setPlayerError('That is you — you are already in the game');
      return;
    }
    if (players.includes(username)) {
      setPlayerError('That username is already added');
      return;
    }
    if (players.length >= MAX_ADDED_PLAYERS) {
      setPlayerError(`Max ${MAX_TOTAL_PLAYERS} players in total (including you)`);
      return;
    }
    setPlayers([...players, username]);
    setPlayerInput('');
    setPlayerError('');
  }

  function removePlayer(index) {
    setPlayers(players.filter((_, i) => i !== index));
    setPlayerError('');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const game = await createGame({ gametype, players, legsToWin: Number(legsToWin) });
      onCreated(game);
    } catch (err) {
      setError(
        err.status === 404
          ? 'One or more usernames not found — check the spelling'
          : err.message
      );
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={submitting ? undefined : onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-game-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="create-game-title">Create game</h2>
        <form className="form" onSubmit={handleSubmit}>
          {available === null && !error && (
            <p className="state-message">Loading gamemodes...</p>
          )}
          {error && <p className="error-message">{error}</p>}
          {available && (
            <div className="gamemode-picker">
              <div className="gamemode-picker-row">
                <select value={gametype} onChange={(event) => setGametype(event.target.value)}
                  aria-label="Game type" disabled={submitting}>
                  {available.map((gametype) => (
                    <option key={gametype.gamemode} value={gametype.gamemode}>
                      {gametype.gamemode}
                    </option>
                  ))}
                </select>
              </div>
              <p className="player-hint">Double-out on every leg.</p>
            </div>
          )}
          <LegTargetPicker value={legsToWin} onChange={setLegsToWin} disabled={submitting} />
          <p className="player-hint" id="legs-to-win-hint">
            Choose 1 for a single leg, or 2–99 for a first-to-wins game. Starting player rotates each leg.
          </p>
          <div className="player-picker">
            <div className="player-picker-header">
              <h3>Players</h3>
              <span className="player-counter">
                {players.length + 1} / {MAX_TOTAL_PLAYERS}
              </span>
            </div>
            <div className="player-chips">
              <span className="player-chip player-chip-owner">{owner} (you)</span>
              {players.map((username, index) => (
                <span key={index} className="player-chip">
                  {username}
                  <button
                    type="button"
                    onClick={() => removePlayer(index)}
                    aria-label={`Remove ${username}`}
                  >
                    &times;
                  </button>
                </span>
              ))}
            </div>
            <div className="player-picker-row">
              <input
                className="player-input"
                type="text"
                value={playerInput}
                onChange={(e) => {
                  setPlayerInput(e.target.value);
                  setPlayerError('');
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    addPlayer();
                  }
                }}
                placeholder="Opponent username"
              />
              <button className="btn" type="button" onClick={addPlayer}>
                Add
              </button>
            </div>
            {playerError && <p className="error-message">{playerError}</p>}
            <p className="player-hint">
              Enter usernames exactly as registered — no search yet.
            </p>
          </div>
          <div className="modal-actions">
            <button className="btn" type="button" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button
              className="btn btn-primary"
              type="submit"
              disabled={!available?.length || submitting}
            >
              {submitting ? 'Creating…' : 'Create game'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
