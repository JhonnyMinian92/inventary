import { useEffect } from 'react';

// Estado de avisos compartido por la SPA. El host visual solo se monta una vez por vista.
const listeners = new Set();
let state = { toasts: [], confirm: null };
let sequence = 0;

function emit(patch) {
  state = { ...state, ...patch };
  listeners.forEach(listener => listener(state));
}
export function subscribeFeedback(listener) {
  listeners.add(listener);
  listener(state);
  return () => listeners.delete(listener);
}
export function dismissToast(id) {
  emit({ toasts: state.toasts.filter(item => item.id !== id) });
}
export function clearToasts() { emit({ toasts: [] }); }
export function toast(message, { tone = 'info', duration } = {}) {
  const text = String(message || '').trim();
  if (!text) return null;
  const repeated = state.toasts.find(item => item.message === text && item.tone === tone);
  if (repeated) return repeated.id;
  const id = ++sequence;
  const item = { id, tone, message: text, duration: duration ?? (tone === 'error' ? 8000 : 4500) };
  emit({ toasts: [...state.toasts, item].slice(-4) });
  return id;
}
toast.success = (message, options) => toast(message, { ...options, tone: 'success' });
toast.error = (message, options) => toast(message, { ...options, tone: 'error' });
toast.warning = (message, options) => toast(message, { ...options, tone: 'warning' });
toast.info = (message, options) => toast(message, { ...options, tone: 'info' });

export function confirmDialog({ title, message = '', confirmLabel = 'Confirmar', cancelLabel = 'Cancelar', tone = 'default' }) {
  state.confirm?.resolve(false);
  return new Promise(resolve => emit({ confirm: { title, message, confirmLabel, cancelLabel, tone, resolve } }));
}
export function settleConfirm(result) {
  const pending = state.confirm;
  if (!pending) return;
  emit({ confirm: null });
  pending.resolve(result);
}
export function useMessageToast(message, clear, tone = 'error') {
  useEffect(() => {
    if (!message) return;
    toast(message, { tone });
    clear('');
  }, [message, clear, tone]);
}
