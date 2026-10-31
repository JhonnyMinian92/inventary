import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';

export default function Modal({ title, onClose, children, busy = false }) {
  const dialogRef = useAccessibleDialog(true, onClose);
  return <div className="modal-backdrop" role="presentation" onMouseDown={event => {
    if (!busy && event.target === event.currentTarget) onClose();
  }}>
    <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true"
      aria-labelledby="global-modal-title" aria-busy={busy} tabIndex={-1}>
      <div className="modal-heading">
        <h2 id="global-modal-title">{title}</h2>
        <button type="button" className="icon-button" onClick={onClose} disabled={busy} aria-label="Cerrar ventana">×</button>
      </div>
      {children}
    </section>
  </div>;
}
