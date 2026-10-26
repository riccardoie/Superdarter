import { Link } from 'react-router-dom';

export default function FinishedGamesList({ games, sessionId }) {
  if (games.length === 0) {
    return (
      <p className="state-message session-detail-empty">
        No finished games yet
      </p>
    );
  }

  return (
    <div className="session-detail-finished">
      {games.map((game) => (
        <div key={game.gameId} className="card finished-game-card">
          <span className="finished-game-label">
            {game.gametype} &middot; Game #{game.gameId}
          </span>
          <span className="finished-game-winner">Winner: {game.winner}</span>
          <Link
            className="finished-game-view"
            to={`/session/${sessionId}/game/${game.gameId}`}
            aria-label={`View ${game.gametype} game ${game.gameId}`}
          >
            View
          </Link>
        </div>
      ))}
    </div>
  );
}
