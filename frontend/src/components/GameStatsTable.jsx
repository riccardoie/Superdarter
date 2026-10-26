export default function GameStatsTable({ players, winner, startingPoints }) {
  const averageScore = (player) =>
    player.turns > 0
      ? ((startingPoints - player.points) / player.turns).toFixed(1)
      : '0';

  return (
    <div className="game-stats-scroll" tabIndex="0" role="region" aria-label="Game statistics">
      <table className="game-stats">
        <thead>
          <tr>
            <th>Player</th>
            <th className="game-stats-num">Points</th>
            <th className="game-stats-num">Turns</th>
            <th className="game-stats-num">Avg</th>
            <th className="game-stats-num">Highest</th>
            <th className="game-stats-num">180s</th>
            <th className="game-stats-num">Bullseyes</th>
          </tr>
        </thead>
        <tbody>
          {players.map((player) => (
            <tr
              key={player.username}
              className={
                player.username === winner ? 'game-stats-winner' : undefined
              }
            >
              <td data-label="Player">{player.username}</td>
              <td className="game-stats-num" data-label="Points">
                {player.points}
              </td>
              <td className="game-stats-num" data-label="Turns">
                {player.turns}
              </td>
              <td className="game-stats-num" data-label="Average">
                {averageScore(player)}
              </td>
              <td className="game-stats-num" data-label="Highest">
                {player.highestScore}
              </td>
              <td className="game-stats-num" data-label="180s">
                {player.triple20s}
              </td>
              <td className="game-stats-num" data-label="Bullseyes">
                {player.bullseyes}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
