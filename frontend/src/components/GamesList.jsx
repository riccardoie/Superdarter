import { useEffect, useState } from 'react';
import { getGames } from '../api';
import GameCard from './GameCard';
import { gamesForView } from '../gameOrder';

export default function GamesList({ gamemode, completed = false }) {
  const [games, setGames] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setGames(null);
    setError('');
    getGames({ gamemode })
      .then((data) => {
        if (!cancelled) setGames(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [gamemode]);

  if (error)
    return <p className="error-message">Failed to load games: {error}</p>;
  if (games === null) return <p className="state-message">Loading games...</p>;
  const visibleGames = gamesForView(games, completed);
  if (visibleGames.length === 0) {
    return (
      <p className="state-message">
        No {gamemode ? `${gamemode} ` : ''}
        {completed ? 'completed' : 'active'} games yet
      </p>
    );
  }
  return (
    <div className="games-list">
      {visibleGames.map((game) => (
        <GameCard key={game.gameId} game={game} />
      ))}
    </div>
  );
}
