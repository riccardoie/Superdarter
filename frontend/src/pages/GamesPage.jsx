import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAllGamemodes } from '../api';
import GamesList from '../components/GamesList';
import CreateGame from '../components/CreateGame';

export default function GamesPage() {
  const navigate = useNavigate();
  const [showCreate, setShowCreate] = useState(false);
  const [availableGamemodes, setAvailableGamemodes] = useState(null);
  const [gamemodeError, setGamemodeError] = useState('');
  const [gamemode, setGamemode] = useState('');

  useEffect(() => {
    getAllGamemodes()
      .then((data) => setAvailableGamemodes(data))
      .catch((err) => setGamemodeError(err.message));
  }, []);

  return (
    <div className="page games-page">
      <div className="games-header">
        <h1>Games</h1>
        <button
          className="btn btn-primary"
          type="button"
          onClick={() => setShowCreate(true)}
        >
          Create game
        </button>
      </div>
      <div className="games-filter" aria-label="Filter games">
        {availableGamemodes === null && !gamemodeError && (
          <p className="state-message">Loading gamemodes...</p>
        )}
        {gamemodeError && <p className="error-message">Failed to load gamemodes: {gamemodeError}</p>}
        {availableGamemodes && (
          <div className="gamemode-picker-row">
            <select
              value={gamemode}
              onChange={(e) => setGamemode(e.target.value)}
              aria-label="Show all"
            >
              <option value="">Show all</option>
              {availableGamemodes.map((gametype) => (
                <option key={gametype.gamemode} value={gametype.gamemode}>
                  {gametype.gamemode}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>
      <GamesList gamemode={gamemode} />
      {showCreate && (
        <CreateGame
          onClose={() => setShowCreate(false)}
          onCreated={(game) => {
            setShowCreate(false);
            navigate(`/games/${game.gameId}`);
          }}
        />
      )}
    </div>
  );
}
