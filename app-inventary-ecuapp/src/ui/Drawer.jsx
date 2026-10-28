import { X } from 'lucide-react';
import useAccessibleDialog from '../hooks/useAccessibleDialog.js';

/** Panel lateral para el detalle de un registro sin salir del listado. */
export default function Drawer({ open, title, subtitle, onClose, children, footer, busy = false }) {
  const dialogRef = useAccessibleDialog(open, onClose);
  if (!open) return null;
  return <div className="drawer-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <aside ref={dialogRef} className="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" aria-busy={busy} tabIndex={-1}>
      <header className="drawer-header">
        <div><h2 id="drawer-title">{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
        <button type="button" className="icon-button" aria-label="Cerrar panel" onClick={onClose} disabled={busy}><X size={18} /></button>
      </header>
      <div className="drawer-body">{children}</div>
      {footer && <footer className="drawer-footer">{footer}</footer>}
    </aside>
  </div>;
}
