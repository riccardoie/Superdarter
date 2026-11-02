import { useEffect, useState } from 'react';
import {
  afterTypingPause,
  countdownRemainingPreview,
} from '../countdownRemaining';

function PausedRemaining({ preview }) {
  const [ready, setReady] = useState(false);
  useEffect(() => afterTypingPause(() => setReady(true)), []);
  return (
    <div className="game-round-remaining" role="status" aria-live="polite">
      {ready && preview && (
        <span>
          <span>
            {preview.status === 'bust' ? 'Bust — remaining:' : 'Remaining:'}
          </span>{' '}
          <strong>{preview.remaining}</strong>
        </span>
      )}
    </div>
  );
}

export default function CountdownRemainingPreview({ game, slots }) {
  const player = game.players.find((entry) => entry.username === game.turn);
  const input = JSON.stringify([
    game.gameId,
    game.currentLegId,
    game.revision,
    game.turn,
    game.gametype,
    player?.points,
    slots,
  ]);
  // Remount on every changed input, including an edit back to an older value.
  // The previous timer is cancelled and no old preview can flash while typing.
  return (
    <PausedRemaining
      key={input}
      preview={countdownRemainingPreview(game, slots)}
    />
  );
}
