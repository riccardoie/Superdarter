import { useEffect, useRef } from 'react';
import { allocationChoices } from '../mikkeAllocation';

export default function MikkeAllocationDialog({
  slot,
  index,
  onChoose,
  onCancel,
}) {
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    dialog.current?.querySelector('button')?.focus();
    return () => previous?.focus();
  }, []);

  function handleKey(event) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onCancel();
    }
    if (event.key !== 'Tab') return;
    const buttons = [...dialog.current.querySelectorAll('button')];
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div
        className="modal"
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="mikke-allocation-title"
        onClick={(event) => event.stopPropagation()}
        onKeyDown={handleKey}
      >
        <h2 id="mikke-allocation-title">How should dart {index + 1} count?</h2>
        <p>
          {Number(slot.multiplier) === 2 ? 'Double' : 'Treble'}{' '}
          {Number(slot.value) === 25 ? 'bull' : slot.value} — choose one
          category.
        </p>
        <div className="modal-actions">
          {allocationChoices(slot).map((choice) => (
            <button
              type="button"
              className="btn btn-primary"
              key={choice.value}
              onClick={() => onChoose(choice.value)}
            >
              {choice.label}
            </button>
          ))}
          <button type="button" className="btn" onClick={onCancel}>
            Decide later
          </button>
        </div>
      </div>
    </div>
  );
}
