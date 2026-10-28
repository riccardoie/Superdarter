import { useEffect, useState } from 'react';
import { getAllGamemodes, getUsername, MAX_TOTAL_PLAYERS } from '../api';
import { countdownModes, playerNameError } from '../gameSetup';

export default function CreateGame({ onClose, onContinue }) {
  const [available, setAvailable] = useState(null);
  const [gametype, setGametype] = useState('501');
  const [error, setError] = useState('');

  const [players, setPlayers] = useState([]);
  const [guests, setGuests] = useState([]);
  const [playerType, setPlayerType] = useState('registered');
  const [playerInput, setPlayerInput] = useState('');
  const [playerError, setPlayerError] = useState('');

  const owner = getUsername(localStorage.getItem('token'));

  useEffect(() => {
    getAllGamemodes()
      .then((data) => {
        const modes = countdownModes(data);
        setAvailable(modes);
        setGametype(
          modes.find((mode) => mode.gamemode === '501')?.gamemode ??
            modes[0]?.gamemode ??
            ''
        );
      })
      .catch((err) => setError(err.message));
  }, []);

  function addPlayer() {
    const username = playerInput.trim();
    const error = playerNameError(username, {
      owner,
      players,
      guests,
      maxPlayers: MAX_TOTAL_PLAYERS,
    });
    if (error) {
      setPlayerError(error);
      return;
    }
    if (playerType === 'guest') setGuests([...guests, username]);
    else setPlayers([...players, username]);
    setPlayerInput('');
    setPlayerError('');
  }

  function removePlayer(index) {
    setPlayers(players.filter((_, i) => i !== index));
    setPlayerError('');
  }

  function handleSubmit(e) {
    e.preventDefault();
    const mode = available?.find((mode) => mode.gamemode === gametype);
    if (mode)
      onContinue({ gametype, players, guests, finishes: mode.finishes });
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
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
          {available?.length === 0 && (
            <p className="state-message">No supported gamemodes available.</p>
          )}
          {available && (
            <div className="gamemode-picker">
              <div className="gamemode-picker-row">
                <select
                  value={gametype}
                  onChange={(event) => setGametype(event.target.value)}
                  aria-label="Game type"
                >
                  {available.map((gametype) => (
                    <option key={gametype.gamemode} value={gametype.gamemode}>
                      {gametype.gamemode}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}
          <div className="player-picker">
            <div className="player-picker-header">
              <h3>Players</h3>
              <span className="player-counter">
                {players.length + guests.length + 1} / {MAX_TOTAL_PLAYERS}
              </span>
            </div>
            <div className="player-chips">
              <span className="player-chip player-chip-owner">
                {owner} (you)
              </span>
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
              {guests.map((name) => (
                <span key={name} className="player-chip">
                  {name} <small className="player-guest-label">Guest</small>
                  <button
                    type="button"
                    aria-label={`Remove guest ${name}`}
                    onClick={() => {
                      setGuests(guests.filter((guest) => guest !== name));
                      setPlayerError('');
                    }}
                  >
                    &times;
                  </button>
                </span>
              ))}
            </div>
            <div className="gamemode-picker-row">
              <select
                aria-label="Player type"
                value={playerType}
                onChange={(event) => {
                  setPlayerType(event.target.value);
                  setPlayerError('');
                }}
              >
                <option value="registered">Registered player</option>
                <option value="guest">Guest player</option>
              </select>
            </div>
            <div className="player-picker-row">
              <input
                className="player-input"
                type="text"
                maxLength={255}
                aria-label={
                  playerType === 'guest' ? 'Guest name' : 'Opponent username'
                }
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
                placeholder={
                  playerType === 'guest'
                    ? 'Guest display name'
                    : 'Opponent username'
                }
              />
              <button className="btn" type="button" onClick={addPlayer}>
                Add
              </button>
            </div>
            {playerError && <p className="error-message">{playerError}</p>}
            <p className="player-hint">
              {playerType === 'guest'
                ? 'Guests play in this match only. Their results are not saved to any profile.'
                : 'Enter usernames exactly as registered — no search yet.'}
            </p>
          </div>
          <div className="modal-actions">
            <button className="btn" type="button" onClick={onClose}>
              Cancel
            </button>
            <button
              className="btn btn-primary"
              type="submit"
              disabled={!available?.length}
            >
              Continue
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
