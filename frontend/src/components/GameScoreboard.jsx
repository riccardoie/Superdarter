import CheckoutSuggestion from './CheckoutSuggestion';
import { standingsInStartingOrder } from '../matchFormat';
import { matchStats } from '../gameStats';
import { isSingleOut } from '../gameSetup';

export default function GameScoreboard({ game, slots }) {
  const activeIndex = game.players.findIndex(
    (player) => player.username === game.turn
  );
  const activePlayer = game.players[activeIndex < 0 ? 0 : activeIndex];
  const nextPlayer =
    game.players.length > 1
      ? game.players[
          ((activeIndex < 0 ? 0 : activeIndex) + 1) % game.players.length
        ]
      : null;
  const stats = new Map(
    matchStats(game).map((player) => [player.username, player])
  );
  const averages = new Map(
    [...stats.values()].map((player) => [
      player.username,
      player.average ??
        (player.turns > 0
          ? (game.startingPoints - player.points) / player.turns
          : 0),
    ])
  );
  return (
    <>
      <div
        className={`game-turn-overview${nextPlayer ? '' : ' game-turn-solo'}`}
      >
        <section className="game-current-player" aria-label="Current player">
          <h2>{game.turn ? 'Now throwing' : 'Ready to play'}</h2>
          {[activePlayer].filter(Boolean).map((player) => (
            <div
              key={player.username}
              className={`game-scoreboard-row${player.username === game.turn ? ' game-scoreboard-turn' : ''}`}
            >
              <span className="game-scoreboard-name" title={player.username}>
                <span className="game-scoreboard-player-name">
                  {player.username}
                </span>
              </span>
              <span
                className="game-scoreboard-average"
                title="Average across all legs"
              >
                Avg: {averages.get(player.username).toFixed(1)}
              </span>
              <span className="game-scoreboard-stats">
                {game.legsToWin > 1 && (
                  <span className="game-scoreboard-leg-wins">
                    Legs: {player.legsWon} / {game.legsToWin}
                  </span>
                )}
                <span className="game-scoreboard-turns">
                  Turns: {player.turns}
                </span>
                <span className="game-scoreboard-checkout">
                  {[301, 501].includes(game.startingPoints) && (
                    <CheckoutSuggestion
                      doubleOut={!isSingleOut(game.gametype)}
                      points={player.points}
                      slots={player.username === game.turn ? slots : []}
                    />
                  )}
                </span>
                <span
                  className="game-scoreboard-points"
                  aria-label={`Remaining points: ${player.points}`}
                >
                  {player.points}
                </span>
              </span>
            </div>
          ))}
        </section>
        {nextPlayer && (
          <aside className="game-next-player" aria-label="Next player">
            <span>Next up</span>
            <strong title={nextPlayer.username}>{nextPlayer.username}</strong>
            <span aria-label={`Remaining points: ${nextPlayer.points}`}>
              {nextPlayer.points}
            </span>
          </aside>
        )}
        <details className="game-all-standings">
          <summary>All players ({game.players.length})</summary>
          <div
            className="game-standings-scroll"
            role="region"
            aria-label="All player statistics"
            tabIndex="0"
          >
            <table className="game-standings-table" aria-label="All players">
              <thead>
                <tr>
                  <th scope="col">Players</th>
                  <th scope="col">Avg</th>
                  <th scope="col">
                    First 9<br />
                    avg
                  </th>
                  <th scope="col">
                    Best
                    <br />
                    score
                  </th>
                  <th scope="col">Legs</th>
                  <th scope="col">
                    Points
                    <br />
                    left
                  </th>
                </tr>
              </thead>
              <tbody>
                {standingsInStartingOrder(game).map((player) => {
                  const playerStats = stats.get(player.username);
                  const firstNine = playerStats.scoringStats?.firstNineAverage;
                  return (
                    <tr
                      key={player.username}
                      aria-current={
                        player.username === game.turn ? 'true' : undefined
                      }
                    >
                      <th scope="row" title={player.username}>
                        {player.username}
                        {player.username === game.turn && (
                          <small> · Now throwing</small>
                        )}
                      </th>
                      <td>{averages.get(player.username).toFixed(1)}</td>
                      <td>
                        {Number.isFinite(firstNine)
                          ? firstNine.toFixed(1)
                          : '—'}
                      </td>
                      <td>
                        {Number.isFinite(playerStats.highestScore)
                          ? playerStats.highestScore
                          : 0}
                      </td>
                      <td>{player.legsWon ?? 0}</td>
                      <td aria-label={`Remaining points: ${player.points}`}>
                        {player.points}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </details>
      </div>
    </>
  );
}
