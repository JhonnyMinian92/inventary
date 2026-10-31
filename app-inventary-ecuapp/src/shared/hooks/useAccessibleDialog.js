import { useEffect, useRef } from 'react';

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
// Los diálogos abiertos se apilan: solo el superior gestiona Escape y el ciclo de Tab.
const openDialogs = [];

export default function useAccessibleDialog(open, onClose) {
  const dialogRef = useRef(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!open || !dialogRef.current) return undefined;
    const dialog = dialogRef.current;
    const previouslyFocused = document.activeElement;
    const getFocusables = () => [...dialog.querySelectorAll(FOCUSABLE)]
      .filter(element => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true');
    const first = getFocusables()[0];
    (first || dialog).focus();
    openDialogs.push(dialog);

    function handleKeyDown(event) {
      if (openDialogs[openDialogs.length - 1] !== dialog) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        closeRef.current?.();
        return;
      }
      if (event.key !== 'Tab') return;
      const nodes = getFocusables();
      if (!nodes.length) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      const firstNode = nodes[0], lastNode = nodes[nodes.length - 1];
      if (event.shiftKey && (document.activeElement === firstNode || !dialog.contains(document.activeElement))) {
        event.preventDefault();
        lastNode.focus();
      } else if (!event.shiftKey && (document.activeElement === lastNode || !dialog.contains(document.activeElement))) {
        event.preventDefault();
        firstNode.focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      const index = openDialogs.lastIndexOf(dialog);
      if (index >= 0) openDialogs.splice(index, 1);
      document.removeEventListener('keydown', handleKeyDown);
      if (previouslyFocused && typeof previouslyFocused.focus === 'function') previouslyFocused.focus();
    };
  }, [open]);

  return dialogRef;
}
