import { useState } from 'react';
import GameStatsTable from './GameStatsTable';

export default function CompletedLegs({ game }) {
  const [expanded, setExpanded] = useState(false);
  const completed = game.legs.filter((leg) => leg.status === 'COMPLETED');
  if (!(game.legsToWin > 1) || completed.length === 0) return null;
  return (
    <section className="match-leg-history" aria-label="Completed legs">
      <button type="button" className="btn" aria-expanded={expanded}
        aria-controls="completed-legs-panel" onClick={() => setExpanded((value) => !value)}>
        Completed legs ({completed.length})
      </button>
      <div id="completed-legs-panel" hidden={!expanded}>
        {completed.map((leg) => (
          <details key={leg.legId}>
            <summary>Leg {leg.number} — {leg.winner} wins</summary>
            <GameStatsTable players={leg.players} winner={leg.winner}
              startingPoints={game.startingPoints} />
          </details>
        ))}
      </div>
    </section>
  );
}
