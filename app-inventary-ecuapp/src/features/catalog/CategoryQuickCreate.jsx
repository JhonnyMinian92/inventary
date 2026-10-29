import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Plus } from 'lucide-react';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';
import { toast } from '../../ui/feedback.js';
import { api } from '../../api';
import { PropertiesEditor, SuggestionNote, TemplatePicker, fromDefinition, normalizeCatalog, rowsForTemplate, toRequest, useCategorySuggestion, validateRows } from './CategoryProperties.jsx';

/**
 * Crea una categoría con sus características sin salir del producto.
 * Categoría y características se guardan en una sola operación, así ningún producto queda en una categoría sin datos.
 */
export default function CategoryQuickCreate({ productName = '', onCreated, onClose }) {
  const [name, setName] = useState('');
  const [catalog, setCatalog] = useState(null);
  const [templateId, setTemplateId] = useState('');
  const [rows, setRows] = useState([]);
  const [manual, setManual] = useState(false);
  const [busy, setBusy] = useState(false);
  const dialogRef = useAccessibleDialog(true, () => { if (!busy) onClose(); });
  // El nombre del producto también ayuda: "Vaso plástico" sugiere Hogar aunque la categoría se llame distinto.
  const suggestion = useCategorySuggestion(`${name} ${productName}`.trim());

  useEffect(() => {
    let active = true;
    api.attributePresets()
      .then(presets => { if (active) setCatalog(normalizeCatalog(presets)); })
      .catch(error => { toast.error(error?.message || 'No se pudieron cargar los tipos de productos.'); if (active) onClose(); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (manual || !catalog || !suggestion?.templateId || suggestion.templateId === templateId) return;
    setTemplateId(suggestion.templateId);
    setRows(rowsForTemplate(catalog.templates.find(item => item.id === suggestion.templateId), catalog.presets));
  }, [suggestion, catalog, manual]);

  function chooseTemplate(template) {
    setManual(true);
    setTemplateId(template.id);
    setRows(rowsForTemplate(template, catalog.presets));
  }

  function copySimilar() {
    setManual(true);
    setTemplateId('');
    setRows((suggestion?.similarDefinitions || []).map(fromDefinition));
  }

  async function submit(event) {
    event.preventDefault();
    // Se renderiza en un portal pero los eventos de React siguen subiendo al formulario del producto.
    event.stopPropagation();
    if (!name.trim()) { toast.error('Escribe el nombre de la categoría.'); return; }
    const problem = validateRows(rows);
    if (problem) { toast.error(problem); return; }
    setBusy(true);
    try {
      const created = await api.createCategory({ name: name.trim(), definitions: rows.map(toRequest) });
      onCreated(created);
    } catch (error) {
      toast.error(error?.message || 'No se pudo crear la categoría.');
      setBusy(false);
    }
  }

  return createPortal(<div className="modal-backdrop confirm-backdrop" role="presentation" onMouseDown={event => { if (!busy && event.target === event.currentTarget) onClose(); }}>
    <form ref={dialogRef} className="modal-card category-quick-create" role="dialog" aria-modal="true" aria-labelledby="category-quick-title" onSubmit={submit} noValidate tabIndex={-1}>
      <div className="modal-heading"><div>
        <h2 id="category-quick-title">Nueva categoría</h2>
        <p className="quick-create-description">Agrupa productos parecidos y define qué datos pedirás de cada uno.</p>
      </div></div>

      <label className="field">
        <span>Nombre de la categoría<b> *</b></span>
        <input autoFocus maxLength="120" value={name} onChange={event => setName(event.target.value)} placeholder="Ej. Camisetas, Bebidas, Celulares" autoComplete="off" />
      </label>

      {!catalog && <p className="form-hint">Cargando tipos de productos…</p>}
      {catalog && <>
        <div className="quick-section">
          <h3>1. ¿Qué vendes en esta categoría?</h3>
          <p>Lo elegimos solo según el nombre para proponerte los datos típicos. Cámbialo si no corresponde.</p>
          <SuggestionNote suggestion={suggestion} applied={!manual && templateId === suggestion?.templateId} onCopySimilar={copySimilar} />
          <TemplatePicker catalog={catalog} value={templateId} suggestedId={suggestion?.templateId} onChoose={chooseTemplate} />
        </div>
        <div className="quick-section">
          <h3>2. ¿Qué datos pedirás de cada producto?</h3>
          <p>Toca para añadir o quitar. Se llenarán al crear cada producto de esta categoría.</p>
          <PropertiesEditor catalog={catalog} rows={rows} compact onChange={next => { setManual(true); setRows(next); }} />
        </div>
      </>}

      <div className="form-actions quick-create-actions">
        <button type="button" className="button secondary" disabled={busy} onClick={onClose}>Cancelar</button>
        <button type="submit" className="button primary" disabled={busy || !catalog}><Plus size={16} />{busy ? 'Creando…' : 'Crear y seleccionar'}</button>
      </div>
    </form>
  </div>, document.body);
}
