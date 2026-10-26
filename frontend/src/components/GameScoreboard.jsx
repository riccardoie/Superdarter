import CheckoutSuggestion from './CheckoutSuggestion';
import { standingsInStartingOrder } from '../matchFormat';

export default function GameScoreboard({ game, slots }) {
  return (
    <>
      <h2>Scoreboard</h2>
      <div className="game-scoreboard">
        {standingsInStartingOrder(game).map((player) => (
          <div key={player.username}
            className={`game-scoreboard-row${player.username === game.turn ? ' game-scoreboard-turn' : ''}`}>
            <span className="game-scoreboard-name" title={player.username}>{player.username}</span>
            <span className="game-scoreboard-stats">
              {game.legsToWin > 1 && (
                <span className="game-scoreboard-leg-wins">Legs: {player.legsWon} / {game.legsToWin}</span>
              )}
              <span className="game-scoreboard-turns">Turns: {player.turns}</span>
              <span className="game-scoreboard-checkout">
                {[301, 501].includes(game.startingPoints) && (
                  <CheckoutSuggestion points={player.points}
                    slots={player.username === game.turn ? slots : []} />
                )}
              </span>
              <span className="game-scoreboard-points" aria-label={`Remaining points: ${player.points}`}>
                {player.points}
              </span>
            </span>
          </div>
        ))}
      </div>
    </>
  );
}
