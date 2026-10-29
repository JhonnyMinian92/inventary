import { useEffect, useMemo, useState } from 'react';
import { toast } from '../../ui/feedback.js';
import { Check, Plus, Sparkles, Trash2, X } from 'lucide-react';
import { api } from '../../api';

/**
 * Piezas compartidas para definir las características de una categoría.
 * Las plantillas, propiedades sugeridas y tipos de dato vienen del backend (GET /catalog/attribute-presets).
 */
export const LIST_TYPES = new Set(['ENUM', 'STRING_LIST']);
export const MAX_PROPERTIES = 50;

export function fromPreset(preset) {
  return {
    key: preset.key,
    displayName: preset.displayName,
    valueType: preset.valueType,
    required: false,
    options: Array.isArray(preset.options) ? preset.options.map(String) : [],
    constraints: preset.constraints || {},
    custom: false
  };
}

export function fromDefinition(definition) {
  return {
    key: definition?.key || '',
    displayName: definition?.displayName || '',
    valueType: definition?.valueType || 'STRING',
    required: Boolean(definition?.required),
    options: Array.isArray(definition?.options) ? definition.options.map(String) : [],
    constraints: definition?.constraints && typeof definition.constraints === 'object' ? { ...definition.constraints } : {},
    custom: false
  };
}

/** La clave técnica la deriva el backend del nombre cuando la propiedad es nueva. */
export function toRequest(row) {
  const definition = { displayName: row.displayName.trim(), valueType: row.valueType, required: Boolean(row.required) };
  if (row.key) definition.key = row.key;
  if (LIST_TYPES.has(row.valueType)) definition.options = row.options;
  if (row.constraints && Object.keys(row.constraints).length) definition.constraints = row.constraints;
  return definition;
}

/** Devuelve un mensaje para el usuario o null si las características son válidas. */
export function validateRows(rows) {
  const names = new Set();
  for (const row of rows) {
    const label = row.displayName.trim();
    if (!label) return 'Escribe el nombre de cada característica que creaste.';
    if (label.length > 120) return `El nombre "${label.slice(0, 30)}…" es demasiado largo.`;
    if (names.has(label.toLocaleLowerCase('es'))) return `La característica "${label}" está repetida.`;
    names.add(label.toLocaleLowerCase('es'));
    if (LIST_TYPES.has(row.valueType) && !row.options.length) return `Añade al menos una opción a "${label}".`;
  }
  return null;
}

export function rowsForTemplate(template, presets) {
  const byKey = new Map(presets.map(preset => [preset.key, preset]));
  return (template?.presetKeys || []).map(key => byKey.get(key)).filter(Boolean).map(fromPreset);
}

export function normalizeCatalog(presets) {
  return {
    valueTypes: Array.isArray(presets?.valueTypes) ? presets.valueTypes : [],
    presets: Array.isArray(presets?.presets) ? presets.presets : [],
    templates: Array.isArray(presets?.templates) ? presets.templates : []
  };
}

/** Pide al backend qué tipo de categoría parece por lo que el usuario escribe (con una pequeña espera). */
export function useCategorySuggestion(text, enabled = true) {
  const [suggestion, setSuggestion] = useState(null);
  useEffect(() => {
    const value = String(text || '').trim();
    if (!enabled || value.length < 3) { setSuggestion(null); return undefined; }
    let active = true;
    const timer = setTimeout(() => {
      api.categorySuggestion(value)
        .then(result => { if (active) setSuggestion(result || null); })
        .catch(() => { if (active) setSuggestion(null); });
    }, 350);
    return () => { active = false; clearTimeout(timer); };
  }, [text, enabled]);
  return suggestion;
}

/** Tarjetas de "¿Qué vendes en esta categoría?", cada una muestra qué datos pedirá. */
export function TemplatePicker({ catalog, value, suggestedId, onChoose }) {
  const names = useMemo(() => new Map(catalog.presets.map(preset => [preset.key, preset.displayName])), [catalog]);
  return <div className="template-grid">
    {catalog.templates.map(template => {
      const selected = value === template.id;
      const asks = template.presetKeys.map(key => names.get(key)).filter(Boolean);
      return <button type="button" key={template.id} aria-pressed={selected} className={'template-card' + (selected ? ' selected' : '')} onClick={() => onChoose(template)}>
        {suggestedId === template.id && <span className="template-badge"><Sparkles size={11} /> Sugerido</span>}
        <strong>{template.name}</strong>
        <small>{template.description}</small>
        <small className="template-asks">{asks.length ? 'Pedirá: ' + asks.join(', ') : 'No pedirá datos extra; puedes añadirlos tú.'}</small>
        {selected && <Check size={16} className="template-check" />}
      </button>;
    })}
  </div>;
}

/** Aviso de lo que el sistema dedujo del nombre, con la opción de copiar una categoría parecida del propio negocio. */
export function SuggestionNote({ suggestion, applied, onCopySimilar }) {
  if (!suggestion?.templateId && !suggestion?.similarCategoryId) return null;
  return <div className="suggestion-note" role="status">
    <Sparkles size={16} />
    <div>
      {suggestion.templateId && <p>{applied ? 'Elegimos' : 'Te sugerimos'} <strong>{suggestion.templateName}</strong> porque mencionas «{suggestion.matchedTerms.slice(0, 3).join(', ')}».</p>}
      {suggestion.similarCategoryId && <p>Ya tienes la categoría <strong>{suggestion.similarCategoryName}</strong>.{' '}
        <button type="button" className="text-button inline" onClick={onCopySimilar}>Usar sus mismas características</button></p>}
    </div>
  </div>;
}

function OptionsEditor({ row, suggested, onChange }) {
  const [draft, setDraft] = useState('');
  const missing = suggested.filter(option => !row.options.includes(option));

  function add(value) {
    const option = value.trim();
    if (!option) return;
    if (option.length > 120) { toast.error('Cada opción puede tener hasta 120 caracteres.'); return; }
    if (row.options.some(item => item.toLocaleLowerCase('es') === option.toLocaleLowerCase('es'))) { toast.error(`"${option}" ya está en la lista.`); return; }
    onChange([...row.options, option]);
    setDraft('');
  }

  return <div className="field wide">
    <span>Opciones para elegir<b> *</b></span>
    <div className="option-chips">
      {row.options.map(option => <span className="option-chip selected removable" key={option}>
        {option}
        <button type="button" aria-label={`Quitar ${option}`} title="Quitar" onClick={() => onChange(row.options.filter(item => item !== option))}><X size={13} /></button>
      </span>)}
      {missing.map(option => <button type="button" className="option-chip suggested" key={option} onClick={() => onChange([...row.options, option])}><Plus size={12} /> {option}</button>)}
      {!row.options.length && !missing.length && <small className="field-help">Todavía no hay opciones. Escribe la primera abajo.</small>}
    </div>
    <div className="option-add">
      <input value={draft} maxLength="120" placeholder="Escribe una opción y presiona Enter" aria-label={`Nueva opción para ${row.displayName || 'la característica'}`}
        onChange={event => setDraft(event.target.value)}
        onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); add(draft); } }} />
      <button type="button" className="button secondary" disabled={!draft.trim()} onClick={() => add(draft)}><Plus size={15} /> Añadir</button>
    </div>
  </div>;
}

/** Etiquetas para activar características sugeridas y tarjetas para ajustar cada una. */
export function PropertiesEditor({ catalog, rows, onChange, compact = false }) {
  const presetsByKey = useMemo(() => new Map(catalog.presets.map(preset => [preset.key, preset])), [catalog]);
  const typeLabel = code => catalog.valueTypes.find(type => type.code === code)?.label || code;
  const selectedKeys = new Set(rows.map(row => row.key).filter(Boolean));

  function togglePreset(preset) {
    if (selectedKeys.has(preset.key)) onChange(rows.filter(row => row.key !== preset.key));
    else if (rows.length < MAX_PROPERTIES) onChange([...rows, fromPreset(preset)]);
  }
  function updateRow(index, changes) {
    onChange(rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...changes } : row));
  }
  function addCustom() {
    if (rows.length >= MAX_PROPERTIES) { toast.error(`Una categoría admite hasta ${MAX_PROPERTIES} características.`); return; }
    onChange([...rows, { key: '', displayName: '', valueType: 'ENUM', required: false, options: [], constraints: {}, custom: true }]);
  }

  return <>
    <div className="preset-chips" role="group" aria-label="Características sugeridas">
      {catalog.presets.map(preset => {
        const active = selectedKeys.has(preset.key);
        return <button type="button" key={preset.key} title={preset.description} aria-pressed={active}
          className={'option-chip' + (active ? ' selected' : '')} onClick={() => togglePreset(preset)}>
          {active ? <Check size={13} /> : <Plus size={13} />} {preset.displayName}
        </button>;
      })}
      <button type="button" className="option-chip custom" onClick={addCustom}><Plus size={13} /> Crear otra</button>
    </div>

    {!rows.length && <p className="form-hint">No hay características seleccionadas. Toca las que quieras pedir en cada producto.</p>}
    <div className={'property-list' + (compact ? ' compact' : '')}>
      {rows.map((row, index) => {
        const preset = row.key ? presetsByKey.get(row.key) : null;
        return <section className="attribute-definition" key={row.key || 'custom-' + index} aria-label={row.displayName || 'Nueva característica'}>
          <div className="attribute-definition-heading">
            <div className="property-title">
              <strong>{row.displayName || 'Nueva característica'}</strong>
              {!row.custom && <span className="workflow-review-tag">{typeLabel(row.valueType)}</span>}
            </div>
            <button type="button" className="text-button danger-text" onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}><Trash2 size={14}/> Quitar</button>
          </div>
          <div className="form-grid compact-grid">
            <label className="field">
              <span>Nombre que verás<b> *</b></span>
              <input required maxLength="120" value={row.displayName} placeholder="Ej. Sabor, Talla, Modelo" onChange={event => updateRow(index, { displayName: event.target.value })} />
            </label>
            <label className="checkbox-field property-required">
              <input type="checkbox" checked={row.required} onChange={event => updateRow(index, { required: event.target.checked })} />
              <span>Obligatorio<small>No se podrá guardar un producto sin este dato.</small></span>
            </label>
            {row.custom && <div className="field wide">
              <span>¿Cómo se llenará?</span>
              <div className="type-grid">
                {catalog.valueTypes.map(type => <button type="button" key={type.code} aria-pressed={row.valueType === type.code}
                  className={'type-card' + (row.valueType === type.code ? ' selected' : '')}
                  onClick={() => updateRow(index, { valueType: type.code, options: type.requiresOptions ? row.options : [] })}>
                  <strong>{type.label}</strong><small>{type.example}</small>
                </button>)}
              </div>
            </div>}
            {LIST_TYPES.has(row.valueType) && <OptionsEditor row={row} suggested={preset?.options?.map(String) || []} onChange={options => updateRow(index, { options })} />}
          </div>
        </section>;
      })}
    </div>
  </>;
}
