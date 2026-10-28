import { useEffect, useRef, useState } from 'react';
import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-react';
import useAccessibleDialog from '../hooks/useAccessibleDialog.js';
import { dismissToast, settleConfirm, subscribeFeedback } from './feedback.js';
import './ui.css';

const ICONS = { success: CircleCheck, error: CircleAlert, warning: TriangleAlert, info: Info };

function ToastItem({ item }) {
  const timer = useRef(null);
  const Icon = ICONS[item.tone] || Info;
  const start = () => { timer.current = window.setTimeout(() => dismissToast(item.id), item.duration); };
  const stop = () => window.clearTimeout(timer.current);
  useEffect(() => { start(); return stop; }, []);
  return <div className={'toast ' + item.tone} role={item.tone === 'error' ? 'alert' : 'status'} onMouseEnter={stop} onMouseLeave={start} onFocus={stop} onBlur={start}>
    <Icon size={18} aria-hidden="true" />
    <p>{item.message}</p>
    <button type="button" aria-label="Cerrar aviso" onClick={() => dismissToast(item.id)}><X size={15} /></button>
  </div>;
}

function ConfirmModal({ confirm }) {
  const dialogRef = useAccessibleDialog(true, () => settleConfirm(false));
  return <div className="modal-backdrop confirm-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) settleConfirm(false); }}>
    <section ref={dialogRef} className={'confirm-card ' + confirm.tone} role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby={confirm.message ? 'confirm-message' : undefined} tabIndex={-1}>
      <span className="confirm-icon" aria-hidden="true">{confirm.tone === 'danger' ? <TriangleAlert size={22} /> : <Info size={22} />}</span>
      <h2 id="confirm-title">{confirm.title}</h2>
      {confirm.message && <p id="confirm-message">{confirm.message}</p>}
      <div className="confirm-actions">
        <button type="button" className="button secondary" onClick={() => settleConfirm(false)}>{confirm.cancelLabel}</button>
        <button type="button" className={'button ' + (confirm.tone === 'danger' ? 'danger' : 'primary')} onClick={() => settleConfirm(true)}>{confirm.confirmLabel}</button>
      </div>
    </section>
  </div>;
}

export default function FeedbackHost() {
  const [feedback, setFeedback] = useState({ toasts: [], confirm: null });
  useEffect(() => subscribeFeedback(setFeedback), []);
  return <>
    <div className="toast-region" aria-live="polite">{feedback.toasts.map(item => <ToastItem key={item.id} item={item} />)}</div>
    {feedback.confirm && <ConfirmModal confirm={feedback.confirm} />}
  </>;
}
