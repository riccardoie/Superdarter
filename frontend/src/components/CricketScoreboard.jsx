import { CRICKET_TARGETS } from '../cricket';
import { matchFormatLabel } from '../matchFormat';
import GameBoardHits from './GameBoardHits';

const MARK_SYMBOLS = ['—', '/', '×', '⊗'];

export default function CricketScoreboard({
  game,
  players = game.players,
  showHeading = true,
  detailPlayers = players,
}) {
  return (
    <section className="cricket-scoreboard" aria-label="Cricket scoreboard">
      {showHeading && (
        <div className="dashboard-section-heading">
          <h2>
            Cricket <small>· {matchFormatLabel(game.legsToWin)}</small>
          </h2>
        </div>
      )}
      <div
        className="cricket-table-wrap"
        tabIndex="0"
        role="region"
        aria-label="Cricket marks and scores"
      >
        <table
          className="cricket-table"
          style={{ minWidth: `${56 + players.length * 44}px` }}
        >
          <colgroup>
            <col className="cricket-target-column" />
            {players.map((player) => (
              <col key={player.username} />
            ))}
          </colgroup>
          <thead>
            <tr>
              <th scope="col">Target</th>
              {players.map((player) => (
                <th
                  scope="col"
                  key={player.username}
                  title={player.username}
                  className={
                    player.username === game.turn ? 'cricket-current' : ''
                  }
                >
                  {player.username}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {CRICKET_TARGETS.map((target) => {
              const closed = players.every(
                (player) => (player.cricketMarks?.[target] ?? 0) === 3
              );
              return (
                <tr
                  key={target}
                  className={closed ? 'cricket-target-closed' : ''}
                >
                  <th scope="row">
                    {target === 25 ? 'Bull' : target}
                    {closed && <small>Closed</small>}
                  </th>
                  {players.map((player) => {
                    const marks = player.cricketMarks?.[target] ?? 0;
                    return (
                      <td
                        key={player.username}
                        aria-label={`${player.username}: ${marks} of 3 marks on ${target}`}
                        className={
                          player.username === game.turn ? 'cricket-current' : ''
                        }
                      >
                        {MARK_SYMBOLS[marks]}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
            <tr className="cricket-points">
              <th scope="row">Points</th>
              {players.map((player) => (
                <td key={player.username}>{player.points}</td>
              ))}
            </tr>
            {game.legsToWin > 1 && (
              <tr>
                <th scope="row">Legs won</th>
                {players.map((player) => (
                  <td key={player.username}>{player.legsWon ?? 0}</td>
                ))}
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <GameBoardHits players={detailPlayers} />
    </section>
  );
}
