import { useState } from 'react';
import GameStatsTable from './GameStatsTable';
import CricketScoreboard from './CricketScoreboard';

export default function CompletedLegs({ game }) {
  const [expanded, setExpanded] = useState(false);
  const completed = game.legs.filter((leg) => leg.status === 'COMPLETED');
  if (!(game.legsToWin > 1) || completed.length === 0) return null;
  return (
    <section className="match-leg-history" aria-label="Completed legs">
      <button
        type="button"
        className="match-leg-toggle"
        aria-expanded={expanded}
        aria-controls="completed-legs-panel"
        onClick={() => setExpanded((value) => !value)}
      >
        <span>Show completed legs ({completed.length})</span>
        <span aria-hidden="true">{expanded ? '−' : '+'}</span>
      </button>
      <div id="completed-legs-panel" hidden={!expanded}>
        {completed.map((leg) => (
          <section key={leg.legId} className="game-stats-section">
            <h2>
              Leg {leg.number} — {leg.winner} wins
            </h2>
            {game.gametype === 'Cricket' ? (
              <CricketScoreboard
                game={{ ...game, turn: null }}
                players={leg.players}
                showHeading={false}
              />
            ) : (
              <GameStatsTable
                players={leg.players}
                winner={leg.winner}
                startingPoints={game.startingPoints}
                showTurns={false}
              />
            )}
          </section>
        ))}
      </div>
    </section>
  );
}
