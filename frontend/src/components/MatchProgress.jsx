import { matchFormatLabel } from '../matchFormat';

export default function MatchProgress({ game }) {
  if (!(game.legsToWin > 1)) return null;
  const current = game.legs.find((leg) => leg.legId === game.currentLegId);
  return (
    <section className="match-progress" aria-label="Match format">
      <h2>{matchFormatLabel(game.legsToWin)}</h2>
      <p className="game-shared-hint">Leg {current?.number} · {game.gametype}</p>
    </section>
  );
}
