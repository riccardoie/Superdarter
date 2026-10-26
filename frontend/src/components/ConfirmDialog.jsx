export default function ConfirmDialog({
  title,
  body,
  confirmLabel,
  busyLabel,
  error,
  busy,
  onConfirm,
  onCancel,
}) {
  return (
    <div className="modal-overlay" onClick={busy ? undefined : onCancel}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="confirm-dialog-title">{title}</h2>
        <p>{body}</p>
        {error && <p className="error-message">{error}</p>}
        <div className="modal-actions">
          <button className="btn" type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            className="btn btn-danger"
            type="button"
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
