import { useEffect, useState } from 'react';
import { api } from '../../api';

function objectValue(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return { ...value };
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch {
      return {};
    }
  }
  return {};
}

function normalizeProduct(product) {
  return {
    sku: product?.sku || product?.code || '',
    name: product?.name || '',
    categoryId: product?.categoryId || product?.category?.id || '',
    description: product?.description || '',
    costPrice: String(product?.costPrice ?? 0),
    salePrice: String(product?.salePrice ?? 0),
    attributes: objectValue(product?.attributes)
  };
}

function toAttributeValue(value, definition) {
  if (value === '') return '';
  if (definition.valueType === 'NUMBER') return Number(value);
  if (definition.valueType === 'BOOLEAN') return value === 'true';
  return value;
}

function AttributeInput({ definition, value, onChange }) {
  const label = definition.displayName || definition.key;
  const constraints = definition.constraints || {};
  const common = {
    id: 'attribute-' + definition.key,
    required: Boolean(definition.required),
    value: value === undefined || value === null ? '' : String(value),
    onChange: event => onChange(definition.key, toAttributeValue(event.target.value, definition)),
    'aria-label': label
  };

  if (definition.valueType === 'ENUM') {
    return <label className="field" htmlFor={common.id}>
      <span>{label}{definition.required && <b> *</b>}</span>
      <select {...common}>
        <option value="">Seleccionar</option>
        {(Array.isArray(definition.options) ? definition.options : []).map(option =>
          <option key={String(option)} value={String(option)}>{String(option)}</option>
        )}
      </select>
    </label>;
  }

  if (definition.valueType === 'BOOLEAN') {
    return <label className="field" htmlFor={common.id}>
      <span>{label}{definition.required && <b> *</b>}</span>
      <select {...common}>
        <option value="">Seleccionar</option>
        <option value="true">Sí</option>
        <option value="false">No</option>
      </select>
    </label>;
  }

  const type = definition.valueType === 'NUMBER' ? 'number' : definition.valueType === 'DATE' ? 'date' : 'text';
  return <label className="field" htmlFor={common.id}>
    <span>{label}{definition.required && <b> *</b>}</span>
    <input
      {...common}
      type={type}
      step={type === 'number' ? 'any' : undefined}
      min={type === 'number' ? constraints.min : undefined}
      max={type === 'number' ? constraints.max : undefined}
      minLength={type === 'text' ? constraints.minLength : undefined}
      maxLength={type === 'text' ? constraints.maxLength : undefined}
    />
  </label>;
}

export default function ProductForm({ initialValue, categories, loading, onCancel, onSubmit, onError }) {
  const [form, setForm] = useState(() => normalizeProduct(initialValue));
  const [definitions, setDefinitions] = useState([]);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [schemaError, setSchemaError] = useState('');
  const [attributesText, setAttributesText] = useState(() => JSON.stringify(objectValue(initialValue?.attributes), null, 2));

  useEffect(() => {
    const categoryId = form.categoryId;
    if (!categoryId) {
      setDefinitions([]);
      setSchemaError('');
      setSchemaLoading(false);
      return undefined;
    }

    let active = true;
    setSchemaLoading(true);
    setSchemaError('');
    api.categoryAttributes(categoryId)
      .then(schema => {
        if (!active) return;
        setDefinitions(Array.isArray(schema?.definitions) ? schema.definitions : []);
      })
      .catch(error => {
        if (!active) return;
        setDefinitions([]);
        setSchemaError(error?.message || 'No se pudo leer el esquema de propiedades.');
        onError?.('No se pudo cargar el esquema de la categoría: ' + (error?.message || 'error desconocido'));
      })
      .finally(() => {
        if (active) setSchemaLoading(false);
      });

    return () => { active = false; };
  }, [form.categoryId]);

  function changeField(key, value) {
    setForm(current => ({ ...current, [key]: value }));
  }

  function changeCategory(categoryId) {
    setForm(current => ({
      ...current,
      categoryId,
      attributes: categoryId === current.categoryId ? current.attributes : {}
    }));
    if (categoryId !== form.categoryId) setAttributesText('{}');
  }

  function changeAttribute(key, value) {
    setForm(current => ({ ...current, attributes: { ...current.attributes, [key]: value } }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (schemaLoading) return;
    let attributes = form.attributes;
    if (!form.categoryId || definitions.length === 0) {
      try {
        attributes = JSON.parse(attributesText || '{}');
      } catch {
        setSchemaError('Las propiedades deben contener un objeto JSON válido.');
        return;
      }
      if (!attributes || typeof attributes !== 'object' || Array.isArray(attributes)) {
        setSchemaError('Las propiedades del producto deben ser un objeto JSON.');
        return;
      }
    }
    if (schemaError && form.categoryId) {
      onError?.('No se puede guardar hasta cargar el esquema de la categoría correctamente.');
      return;
    }
    onSubmit({ ...form, attributes });
  }

  return <form className="form-grid" onSubmit={handleSubmit}>
    <label className="field">
      <span>Nombre del producto<b> *</b></span>
      <input required maxLength="160" value={form.name} onChange={event => changeField('name', event.target.value)} placeholder="Ej. Teclado mecánico" autoComplete="off" />
    </label>
    <label className="field">
      <span>SKU / código<b> *</b></span>
      <input required maxLength="80" value={form.sku} onChange={event => changeField('sku', event.target.value)} placeholder="Ej. TEC-001" autoComplete="off" />
    </label>
    <label className="field">
      <span>Categoría</span>
      <select value={form.categoryId} onChange={event => changeCategory(event.target.value)}>
        <option value="">Sin categoría</option>
        {categories.map(category => <option key={category.id || category.categoryId} value={category.id || category.categoryId}>{category.name}</option>)}
      </select>
    </label>
    <label className="field">
      <span>Costo de compra<b> *</b></span>
      <input type="number" min="0" step="0.0001" required value={form.costPrice} onChange={event => changeField('costPrice', event.target.value)} />
    </label>
    <label className="field">
      <span>Precio de venta<b> *</b></span>
      <input type="number" min="0" step="0.0001" required value={form.salePrice} onChange={event => changeField('salePrice', event.target.value)} />
    </label>
    <label className="field wide">
      <span>Descripción</span>
      <textarea rows="2" maxLength="1000" value={form.description} onChange={event => changeField('description', event.target.value)} />
    </label>

    <div className="form-subheading wide">
      <strong>Propiedades del producto</strong>
      <small>{form.categoryId ? 'Se definen según el esquema de la categoría seleccionada.' : 'Selecciona una categoría para usar sus campos definidos.'}</small>
    </div>
    {schemaLoading && <p className="form-hint wide">Cargando las propiedades de la categoría…</p>}
    {!schemaLoading && !schemaError && definitions.map(definition =>
      <AttributeInput key={definition.key} definition={definition} value={form.attributes[definition.key]} onChange={changeAttribute} />
    )}
    {!schemaLoading && !schemaError && definitions.length === 0 &&
      <label className="field wide">
        <span>Propiedades adicionales (objeto JSON)</span>
        <textarea rows="4" spellCheck="false" value={attributesText} onChange={event => { setAttributesText(event.target.value); setSchemaError(''); }} />
        <small className="field-help">Usa un objeto JSON, por ejemplo: {"{\"color\":\"Negro\",\"garantiaMeses\":12}"}.</small>
      </label>
    }
    {schemaError && <div className="alert error wide">{schemaError}</div>}
    {schemaError && form.categoryId && <button type="button" className="button secondary wide" onClick={() => changeCategory('')}>Quitar categoría y editar propiedades manualmente</button>}
    <div className="form-actions wide">
      <button type="button" className="button secondary" onClick={onCancel}>Cancelar</button>
      <button className="button primary" disabled={loading || schemaLoading || Boolean(schemaError)}>{loading ? 'Guardando…' : 'Guardar producto'}</button>
    </div>
  </form>;
}
