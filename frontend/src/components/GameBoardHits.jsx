import ProfileBoardHits from './ProfileBoardHits';

export default function GameBoardHits({ players }) {
  return (
    <div className="game-player-heatmaps">
      {players.map((player) => (
        <details className="game-stats-details" key={player.username}>
          <summary>
            More stats{' '}
            <span className="game-stats-details-player">
              — {player.username}
            </span>
          </summary>
          <ProfileBoardHits boardHits={player.boardHits} compact />
        </details>
      ))}
    </div>
  );
}
