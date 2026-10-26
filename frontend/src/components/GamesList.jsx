import { useEffect, useState } from 'react';
import { getGames } from '../api';
import GameCard from './GameCard';

export default function GamesList({ gamemode }) {
  const [games, setGames] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setGames(null);
    setError('');
    getGames({ gamemode })
      .then((data) => { if (!cancelled) setGames(data); })
      .catch((err) => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [gamemode]);

  if (error) return <p className="error-message">Failed to load games: {error}</p>;
  if (games === null) return <p className="state-message">Loading games...</p>;
  if (games.length === 0) {
    return <p className="state-message">{gamemode ? `No ${gamemode} games yet` : 'No games yet'}</p>;
  }
  return (
    <div className="games-list">
      {games.map((game) => <GameCard key={game.gameId} game={game} />)}
    </div>
  );
}
