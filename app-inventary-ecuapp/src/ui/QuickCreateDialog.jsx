import { useState } from 'react';
import { createPortal } from 'react-dom';
import { Plus } from 'lucide-react';
import useAccessibleDialog from '../hooks/useAccessibleDialog.js';
import { toast } from './feedback.js';

/**
 * Alta rápida de un registro auxiliar sin abandonar el formulario actual.
 * fields: [{ key, label, required, maxLength, pattern, hint, type: 'text'|'textarea'|'select', options: [[value, label]], initial, transform }]
 */
export default function QuickCreateDialog({ title, fields, submitLabel = 'Crear', onCreate, onCreated, onClose }) {
  const [values, setValues] = useState(() => Object.fromEntries(fields.map(field => [field.key, field.initial ?? ''])));
  const [busy, setBusy] = useState(false);
  const dialogRef = useAccessibleDialog(true, () => { if (!busy) onClose(); });

  async function submit(event) {
    event.preventDefault();
    // Se renderiza en un portal pero los eventos de React siguen subiendo al formulario que lo abrió.
    event.stopPropagation();
    if (!event.currentTarget.checkValidity()) {
      const invalid = fields.find(field => !event.currentTarget.elements[field.key]?.validity.valid);
      toast.error(invalid?.hint || `Revisa el campo ${invalid?.label || 'obligatorio'}.`);
      event.currentTarget.elements[invalid?.key]?.focus();
      return;
    }
    setBusy(true);
    try {
      const payload = Object.fromEntries(fields.map(field => {
        const value = typeof values[field.key] === 'string' ? values[field.key].trim() : values[field.key];
        return [field.key, field.transform ? field.transform(value) : value];
      }));
      const created = await onCreate(payload);
      onCreated(created);
    } catch (error) {
      toast.error(error?.message || 'No se pudo crear el registro.');
      setBusy(false);
    }
  }

  return createPortal(<div className="modal-backdrop confirm-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <form ref={dialogRef} className="modal-card quick-create" role="dialog" aria-modal="true" aria-labelledby="quick-create-title" onSubmit={submit} noValidate tabIndex={-1}>
      <div className="modal-heading"><h2 id="quick-create-title">{title}</h2></div>
      <div className="quick-create-fields">
        {fields.map(field => <label key={field.key} className={'field' + (field.type === 'textarea' ? ' wide' : '')}>
          <span>{field.label}{field.required && <b> *</b>}</span>
          {field.type === 'select'
            ? <select name={field.key} required={field.required} value={values[field.key]} onChange={event => setValues(current => ({ ...current, [field.key]: event.target.value }))}>
              {field.options.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            : field.type === 'textarea'
              ? <textarea name={field.key} rows="2" maxLength={field.maxLength} value={values[field.key]} onChange={event => setValues(current => ({ ...current, [field.key]: event.target.value }))} />
              : <input name={field.key} required={field.required} maxLength={field.maxLength} pattern={field.pattern} placeholder={field.placeholder} autoComplete="off" value={values[field.key]}
                onChange={event => setValues(current => ({ ...current, [field.key]: event.target.value }))} />}
        </label>)}
      </div>
      <div className="form-actions">
        <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancelar</button>
        <button type="submit" className="button primary" disabled={busy}><Plus size={16} />{busy ? 'Creando…' : submitLabel}</button>
      </div>
    </form>
  </div>, document.body);
}

/** Selector con botón "+" a la derecha para crear una opción nueva. */
export function SelectWithCreate({ label, required, createLabel, onCreate, children, labelId }) {
  return <div className="field">
    <span id={labelId}>{label}{required && <b> *</b>}</span>
    <div className="select-with-action">
      {children}
      {onCreate && <button type="button" className="icon-button select-add" aria-label={createLabel} title={createLabel} onClick={onCreate}><Plus size={18} /></button>}
    </div>
  </div>;
}
