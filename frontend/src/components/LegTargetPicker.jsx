export default function LegTargetPicker({ value, onChange, disabled = false }) {
  const count = Number(value) || 1;
  return (
    <div className="game-format-picker">
      <label htmlFor="legs-to-win">First to win</label>
      <div className="leg-target-stepper">
        <button type="button" aria-label="Decrease legs to win"
          disabled={disabled || count <= 1}
          onClick={() => onChange(String(Math.max(1, count - 1)))}>
          −
        </button>
        <input id="legs-to-win" type="number" min="1" max="99" step="1" required
          value={value} onChange={(event) => onChange(event.target.value)}
          disabled={disabled} aria-describedby="legs-to-win-hint" />
        <button type="button" aria-label="Increase legs to win"
          disabled={disabled || count >= 99}
          onClick={() => onChange(String(Math.min(99, count + 1)))}>
          +
        </button>
      </div>
      <span>legs</span>
    </div>
  );
}
