import GameBoardHits from './GameBoardHits';
import { clockRoundPreview, clockTargetLabel } from '../aroundClock';

export default function AroundClockScoreboard({
  game,
  players = game.players,
  slots = [],
  showHeading = true,
  showStats = true,
  detailPlayers = players,
}) {
  const preview = clockRoundPreview({ ...game, players }, slots);
  const previewActive =
    game.status === 'IN_PROGRESS' &&
    ['PLAY', 'REDEMPTION'].includes(game.aroundClock?.phase);
  const targetLabel = (player) =>
    clockTargetLabel(
      previewActive && player.username === game.turn
        ? (preview?.target ?? player.points)
        : player.points
    );
  return (
    <section
      className="cricket-scoreboard"
      aria-label="Around the Clock scoreboard"
    >
      {showHeading && (
        <div className="dashboard-section-heading">
          <h2>Around the Clock</h2>
        </div>
      )}
      <div
        className="cricket-table-wrap"
        tabIndex="0"
        role="region"
        aria-label="Around the Clock targets"
      >
        <table
          className="cricket-table"
          style={{ minWidth: `${80 + players.length * 60}px` }}
        >
          <thead>
            <tr>
              <th scope="col">Player</th>
              <th scope="col">Next target</th>
              <th scope="col">Turns</th>
              {game.legsToWin > 1 && <th scope="col">Legs</th>}
            </tr>
          </thead>
          <tbody>
            {players.map((player) => (
              <tr
                key={player.username}
                className={
                  player.username === game.turn ? 'cricket-current' : ''
                }
              >
                <th scope="row">{player.username}</th>
                <td
                  aria-label={`${player.username}: ${targetLabel(player)}`}
                  aria-live={
                    player.username === game.turn ? 'polite' : undefined
                  }
                >
                  {targetLabel(player)}
                </td>
                <td>{player.turns ?? 0}</td>
                {game.legsToWin > 1 && <td>{player.legsWon ?? 0}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {showStats && <GameBoardHits players={detailPlayers} />}
    </section>
  );
}
