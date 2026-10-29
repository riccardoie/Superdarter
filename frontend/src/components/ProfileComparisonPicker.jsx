import { useEffect, useRef } from 'react';

export default function ProfileComparisonPicker({
  candidates,
  owner,
  selected,
  loading,
  error,
  onChange,
  onRetry,
}) {
  const dropdown = useRef(null);

  useEffect(() => {
    const closeOutside = (event) => {
      if (!dropdown.current.contains(event.target))
        dropdown.current.open = false;
    };
    const closeOnEscape = (event) => {
      if (event.key === 'Escape' && dropdown.current.open) {
        dropdown.current.open = false;
        dropdown.current.querySelector('summary').focus();
      }
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, []);

  function choose(name) {
    onChange(name);
    dropdown.current.open = false;
    dropdown.current.querySelector('summary').focus();
  }

  return (
    <details className="profile-compare-picker" ref={dropdown}>
      <summary className="btn" aria-label="Compare with another player">
        Compare
      </summary>
      <div className="profile-compare-options" aria-label="Comparison choices">
        <button
          type="button"
          aria-pressed={!selected}
          onClick={() => choose('')}
        >
          No comparison
        </button>
        {candidates.map((name) => (
          <button
            key={name}
            type="button"
            aria-pressed={selected === name}
            onClick={() => choose(name)}
          >
            {name === owner ? `${name} (you)` : name}
          </button>
        ))}
        {loading && <p className="player-hint">Loading friends…</p>}
        {!loading && !error && candidates.length === 0 && (
          <p className="player-hint">Add a friend to compare statistics.</p>
        )}
        {error && (
          <div role="alert">
            <p className="error-message">
              Unable to load comparison friends: {error}
            </p>
            <button type="button" onClick={onRetry}>
              Retry
            </button>
          </div>
        )}
      </div>
    </details>
  );
}
