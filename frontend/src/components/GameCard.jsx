import { Link } from 'react-router-dom';
import { matchFormatLabel } from '../matchFormat';

const STATUS_LABELS = {
  PENDING: 'Ready to play',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  ABANDONED: 'Abandoned',
};

export default function GameCard({ game }) {
  return (
    <Link className="card match-card" to={`/games/${game.gameId}`}>
      <div className="match-card-header">
        <time className="match-card-date" dateTime={game.createdAt}>
          {new Date(game.createdAt).toLocaleDateString('en-GB', {
            dateStyle: 'medium',
          })}
        </time>
        <span className="match-card-badge">{STATUS_LABELS[game.status]}</span>
      </div>
      <p className="match-card-gametype">
        {game.gametype} <small>· {matchFormatLabel(game.legsToWin)}</small>
      </p>
      <p className="match-card-players">
        {game.players
          .map(
            (player) =>
              `${player.username}${player.guest ? ' (guest)' : ''}: ${player.legsWon ?? 0}`
          )
          .join(' · ')}
      </p>
      {game.winner && (
        <p className="match-card-result">Winner: {game.winner}</p>
      )}
    </Link>
  );
}
