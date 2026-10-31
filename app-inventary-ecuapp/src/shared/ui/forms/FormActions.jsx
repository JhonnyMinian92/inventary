export default function FormActions({ loading = false, onCancel, submitLabel = 'Guardar cambios' }) {
  return <div className="form-actions wide">
    <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
    <button className="button primary" disabled={loading}>{loading ? 'Guardando…' : submitLabel}</button>
  </div>;
}
