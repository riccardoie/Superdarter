export default function MikkeRedemptionNotice({ game }) {
  const state = game.aroundClock ?? game.mikkeMus;
  const tiebreaker = state?.phase === 'BULL_OFF';
  if (
    game.status !== 'IN_PROGRESS' ||
    (!tiebreaker && state?.phase !== 'REDEMPTION')
  )
    return null;
  return (
    <div className="mikke-redemption-notice" role="status" aria-live="polite">
      <div className="mikke-redemption-card">
        <strong>{tiebreaker ? 'TIEBREAKER!' : 'Redemption!'}</strong>
        <span>
          {tiebreaker ? 'Closest to bull wins' : 'One final turn to finish'}
        </span>
      </div>
    </div>
  );
}
