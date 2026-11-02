export default function MikkeBullOff({
  game,
  busy,
  onThrow,
  onDecision,
  onUndo,
}) {
  const state = game.aroundClock ?? game.mikkeMus;
  const name = (position) =>
    game.players.find((player) => player.position === position)?.username;
  const regions = ['Outside bull', 'Outer bull', 'Bullseye'];
  return (
    <section className="game-round-panel" aria-label="Bull-off">
      <h2>Closest to bull wins</h2>
      {state.bullHistory.length > 0 && (
        <ul className="mikke-bull-history">
          {state.bullHistory.map((hit, index) => (
            <li key={index}>
              Throw-off {hit.attempt}: {name(hit.position)} —{' '}
              {regions[hit.region]}
            </li>
          ))}
        </ul>
      )}
      {state.phase === 'BULL_OFF' ? (
        <>
          <h3>{game.turn}’s throw</h3>
          <div className="game-round-actions">
            {regions.map((region, value) => (
              <button
                className="btn"
                type="button"
                key={region}
                disabled={busy || !game.permissions.canScore}
                onClick={() => onThrow(value)}
              >
                {region}
              </button>
            ))}
          </div>
        </>
      ) : (
        <>
          <p>
            The tied throws are outside bull. Select the closest dart, or throw
            again if still tied.
          </p>
          <div className="game-round-actions">
            {state.bullCandidates.map((position) => (
              <button
                className="btn btn-primary"
                key={position}
                type="button"
                disabled={busy || !game.permissions.canScore}
                onClick={() => onDecision(name(position), false)}
              >
                {name(position)} is closest
              </button>
            ))}
            <button
              type="button"
              className="btn"
              disabled={busy || !game.permissions.canScore}
              onClick={() => onDecision(undefined, true)}
            >
              Still tied — throw again
            </button>
          </div>
        </>
      )}
      <button
        className="btn"
        type="button"
        disabled={busy || !game.permissions.canUndo}
        onClick={onUndo}
      >
        Undo previous action
      </button>
    </section>
  );
}
