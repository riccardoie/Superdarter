import CheckoutSuggestion from './CheckoutSuggestion';
import { standingsInStartingOrder } from '../matchFormat';
import { matchStats } from '../gameStats';

export default function GameScoreboard({ game, slots }) {
  const averages = new Map(
    matchStats(game).map((player) => [
      player.username,
      player.average ??
        (player.turns > 0
          ? (game.startingPoints - player.points) / player.turns
          : 0),
    ])
  );
  return (
    <>
      <h2>Scoreboard</h2>
      <div className="game-scoreboard">
        {standingsInStartingOrder(game).map((player) => (
          <div
            key={player.username}
            className={`game-scoreboard-row${player.username === game.turn ? ' game-scoreboard-turn' : ''}`}
          >
            <span className="game-scoreboard-name" title={player.username}>
              <span className="game-scoreboard-player-name">
                {player.username}
              </span>
              <span
                className="game-scoreboard-average"
                title="Average across all legs"
              >
                Avg: {averages.get(player.username).toFixed(1)}
              </span>
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
      </div>
    </>
  );
}
