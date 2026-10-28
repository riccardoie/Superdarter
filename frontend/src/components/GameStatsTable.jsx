import { Fragment } from 'react';

const SCORE_COUNTS = [
  '40+',
  '60+',
  '80+',
  '100+',
  '120+',
  '140+',
  '160+',
  '180',
];

function RecordedStat({ value, decimal = false }) {
  return value == null ? (
    <span title="Not available for this game">—</span>
  ) : decimal ? (
    value.toFixed(1)
  ) : (
    value
  );
}

export default function GameStatsTable({
  players,
  winner,
  startingPoints,
  showTurns = true,
  showLegsWon = false,
}) {
  const averageScore = (player) =>
    player.average != null
      ? player.average.toFixed(1)
      : player.turns > 0
        ? ((startingPoints - player.points) / player.turns).toFixed(1)
        : '0';

  return (
    <div
      className="game-stats-scroll"
      tabIndex="0"
      role="region"
      aria-label="Game statistics"
    >
      <table className="game-stats">
        <thead>
          <tr>
            <th>Player</th>
            {showLegsWon && <th className="game-stats-num">Legs won</th>}
            {showTurns && <th className="game-stats-num">Turns</th>}
            {showTurns && <th className="game-stats-num">Darts thrown</th>}
            <th className="game-stats-num">Avg</th>
            <th className="game-stats-num">First 9 avg</th>
            <th className="game-stats-num">Highest score</th>
            <th className="game-stats-num">Highest checkout</th>
          </tr>
        </thead>
        <tbody>
          {players.map((player) => (
            <Fragment key={player.username}>
              <tr
                className={
                  player.username === winner ? 'game-stats-winner' : undefined
                }
              >
                <td data-label="Player">
                  {player.username}
                  {player.guest && (
                    <small className="player-guest-label"> Guest</small>
                  )}
                </td>
                {showLegsWon && (
                  <td className="game-stats-num" data-label="Legs won">
                    {player.legsWon ?? 0}
                  </td>
                )}
                {showTurns && (
                  <td className="game-stats-num" data-label="Turns">
                    {player.turns}
                  </td>
                )}
                {showTurns && (
                  <td className="game-stats-num" data-label="Darts thrown">
                    <RecordedStat value={player.scoringStats?.dartsThrown} />
                  </td>
                )}
                <td className="game-stats-num" data-label="Average">
                  {averageScore(player)}
                </td>
                <td className="game-stats-num" data-label="First 9 avg">
                  <RecordedStat
                    value={player.scoringStats?.firstNineAverage}
                    decimal
                  />
                </td>
                <td className="game-stats-num" data-label="Highest score">
                  {player.highestScore}
                </td>
                <td className="game-stats-num" data-label="Highest checkout">
                  {player.highestCheckout ?? 0}
                </td>
              </tr>
              <tr className="game-stats-details-row">
                <td colSpan={(showTurns ? 7 : 5) + (showLegsWon ? 1 : 0)}>
                  <details className="game-stats-details">
                    <summary>
                      More stats{' '}
                      <span className="game-stats-details-player">
                        — {player.username}
                      </span>
                    </summary>
                    <dl className="game-stats-extra">
                      {!showTurns && (
                        <div>
                          <dt>Darts thrown</dt>
                          <dd data-label="Darts thrown">
                            <RecordedStat
                              value={player.scoringStats?.dartsThrown}
                            />
                          </dd>
                        </div>
                      )}
                      <div>
                        <dt>Bullseyes</dt>
                        <dd data-label="Bullseyes">{player.bullseyes}</dd>
                      </div>
                    </dl>
                    <h3>Score counts</h3>
                    <dl className="game-stats-counts">
                      {SCORE_COUNTS.map((score) => (
                        <div key={score}>
                          <dt>{score}</dt>
                          <dd data-label={score}>
                            <RecordedStat
                              value={
                                player.scoringStats?.[
                                  `score${parseInt(score, 10)}`
                                ]
                              }
                            />
                          </dd>
                        </div>
                      ))}
                    </dl>
                  </details>
                </td>
              </tr>
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
