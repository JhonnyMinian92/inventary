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
    kind: product?.kind || 'STOCKED_GOOD',
    categoryId: product?.categoryId || product?.category?.id || '',
    unitOfMeasureId: product?.unitOfMeasureId || product?.unitOfMeasure?.id || '',
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

export default function ProductForm({ initialValue, categories, units = [], loading, onCancel, onSubmit, onError }) {
  const [form, setForm] = useState(() => normalizeProduct(initialValue));
  const [definitions, setDefinitions] = useState([]);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [schemaError, setSchemaError] = useState('');

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
  }

  function changeAttribute(key, value) {
    setForm(current => ({ ...current, attributes: { ...current.attributes, [key]: value } }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (schemaLoading) return;
    if (!String(form.name || '').trim() || !String(form.sku || '').trim()) {
      onError?.('Completa el nombre y el SKU del producto.');
      return;
    }
    if (!form.categoryId) {
      onError?.('Selecciona una categoría para crear o actualizar el producto.');
      return;
    }
    if (schemaError) {
      onError?.('No se puede guardar hasta cargar correctamente las propiedades de la categoría.');
      return;
    }

    let attributes = null;
    if (definitions.length > 0) {
      attributes = {};
      for (const definition of definitions) {
        const value = form.attributes[definition.key];
        const isEmpty = value === undefined || value === null || String(value).trim() === '';
        if (definition.required && isEmpty) {
          onError?.(`Completa el campo "${definition.displayName || definition.key}".`);
          return;
        }
        if (!isEmpty && definition.valueType === 'NUMBER') {
          const numericValue = Number(value);
          const constraints = definition.constraints || {};
          if (!Number.isFinite(numericValue) ||
              (constraints.min != null && numericValue < Number(constraints.min)) ||
              (constraints.max != null && numericValue > Number(constraints.max))) {
            onError?.(`El valor de "${definition.displayName || definition.key}" está fuera del rango permitido.`);
            return;
          }
        }
        if (!isEmpty && definition.valueType === 'STRING') {
          const constraints = definition.constraints || {};
          const length = String(value).length;
          if ((constraints.minLength != null && length < Number(constraints.minLength)) ||
              (constraints.maxLength != null && length > Number(constraints.maxLength))) {
            onError?.(`Revisa la longitud de "${definition.displayName || definition.key}".`);
            return;
          }
        }
        if (!isEmpty) attributes[definition.key] = value;
      }
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
      <input required disabled={Boolean(initialValue)} maxLength="64" pattern="[A-Za-z0-9][A-Za-z0-9._-]{0,63}" title="Usa hasta 64 caracteres: letras, números, punto, guion o guion bajo." value={form.sku} onChange={event => changeField('sku', event.target.value)} placeholder="Ej. TEC-001" autoComplete="off" />
    </label>
    <label className="field">
      <span>Categoría<b> *</b></span>
      <select required value={form.categoryId} onChange={event => changeCategory(event.target.value)}>
        <option value="">Seleccionar categoría</option>
        {categories.filter(category => category.active !== false && category.status !== 'INACTIVE').map(category => <option key={category.id || category.categoryId} value={category.id || category.categoryId}>{category.name}</option>)}
      </select>
    </label>
    <label className="field">
      <span>Tipo de producto<b> *</b></span>
      <select required value={form.kind} onChange={event => changeField('kind', event.target.value)}>
        <option value="STOCKED_GOOD">Producto con inventario</option>
        <option value="NON_STOCKED_GOOD">Producto sin control de stock</option>
        <option value="SERVICE">Servicio</option>
      </select>
    </label>
    <label className="field">
      <span>Unidad de medida</span>
      <select value={form.unitOfMeasureId} onChange={event => changeField('unitOfMeasureId', event.target.value)}>
        <option value="">Unidad predeterminada del backend</option>
        {units.filter(unit => unit.active !== false && unit.status !== 'INACTIVE').map(unit => <option key={unit.id} value={unit.id}>{unit.name} · {unit.code}</option>)}
      </select>
    </label>
    <label className="field">
      <span>Costo de compra<b> *</b></span>
      <input type="number" min="0" step="0.01" required value={form.costPrice} onChange={event => changeField('costPrice', event.target.value)} />
    </label>
    <label className="field">
      <span>Precio de venta<b> *</b></span>
      <input type="number" min="0" step="0.01" required value={form.salePrice} onChange={event => changeField('salePrice', event.target.value)} />
    </label>
    <label className="field wide">
      <span>Descripción</span>
      <textarea rows="2" maxLength="2000" value={form.description} onChange={event => changeField('description', event.target.value)} />
    </label>

    <div className="form-subheading wide">
      <strong>Propiedades del producto</strong>
      <small>{form.categoryId ? 'Se definen según el esquema de la categoría seleccionada.' : 'Selecciona una categoría para usar sus campos definidos.'}</small>
    </div>
    {schemaLoading && <p className="form-hint wide">Cargando las propiedades de la categoría…</p>}
    {!schemaLoading && !schemaError && definitions.map(definition =>
      <AttributeInput key={definition.key} definition={definition} value={form.attributes[definition.key]} onChange={changeAttribute} />
    )}
    {!schemaLoading && !schemaError && form.categoryId && definitions.length === 0 &&
      <p className="form-hint wide">Esta categoría todavía no tiene propiedades definidas. Para capturar campos como talla, color o material, configúralos primero en Categorías; el producto se guardará sin propiedades adicionales.</p>
    }
    {schemaError && <div className="alert error wide">{schemaError}</div>}
    <div className="form-actions wide">
      <button type="button" className="button secondary" onClick={onCancel}>Cancelar</button>
      <button className="button primary" disabled={loading || schemaLoading || Boolean(schemaError)}>{loading ? 'Guardando…' : 'Guardar producto'}</button>
    </div>
  </form>;
}
