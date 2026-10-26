import { useState } from 'react';

export default function GameTypeCard({
  gamemode,
  count,
  onStart,
  onDelete,
  deletable = false,
  finishedMessage,
  playable = true,
}) {
  const [loading, setLoading] = useState(false);

  const handleStart = async () => {
    setLoading(true);
    try {
      await onStart();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card game-type-card">
      <div className="game-type-card-info">
        <span className="game-type-card-label">{gamemode}</span>
        <span className="game-type-card-count">{count} game{count === 1 ? '' : 's'} scheduled</span>
      </div>
      <div className="game-type-card-actions">
        {finishedMessage ? (
          <span className="game-type-card-finished">{finishedMessage}</span>
        ) : (
          playable && (
            <button
              type="button"
              className="btn btn-primary game-type-card-start"
              onClick={handleStart}
              disabled={loading}
            >
              {loading ? 'Starting…' : 'Start'}
            </button>
          )
        )}
        {deletable && (
          <button
            type="button"
            className="game-type-card-remove"
            aria-label={`Remove an unstarted ${gamemode} game`}
            onClick={onDelete}
          >
            Remove
          </button>
        )}
      </div>
    </div>
  );
}
