import GameBoardHits from './GameBoardHits';

const MIKKE_TARGETS = ['20', '19', '18', '17', '16', '15', '14', 'D', 'T', 'B'];
const LABELS = { D: 'Doubles', T: 'Trebles', B: 'Bull' };
const SYMBOLS = ['—', '/', '×', '⊗'];

export default function MikkeScoreboard({
  game,
  players = game.players,
  detailPlayers = players,
  showHeading = true,
  showStats = true,
}) {
  return (
    <section className="cricket-scoreboard" aria-label="Mikke Mus scoreboard">
      {showHeading && (
        <div className="dashboard-section-heading">
          <h2>{game.gametype}</h2>
        </div>
      )}
      <div
        className="cricket-table-wrap"
        tabIndex="0"
        role="region"
        aria-label="Mikke Mus marks"
      >
        <table
          className="cricket-table"
          style={{ minWidth: `${64 + players.length * 44}px` }}
        >
          <colgroup>
            <col style={{ width: 64 }} />
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
            {MIKKE_TARGETS.map((key) => (
              <tr key={key}>
                <th scope="row">{LABELS[key] ?? key}</th>
                {players.map((player) => {
                  const marks = player.mikkeMarks?.[key] ?? 0;
                  return (
                    <td
                      key={player.username}
                      className={
                        player.username === game.turn ? 'cricket-current' : ''
                      }
                      aria-label={`${player.username}: ${marks} of 3 marks on ${LABELS[key] ?? key}`}
                    >
                      {SYMBOLS[marks]}
                    </td>
                  );
                })}
              </tr>
            ))}
            {game.legsToWin > 1 && (
              <tr>
                <th scope="row">Legs</th>
                {players.map((player) => (
                  <td key={player.username}>{player.legsWon ?? 0}</td>
                ))}
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {showStats && <GameBoardHits players={detailPlayers} />}
    </section>
  );
}
