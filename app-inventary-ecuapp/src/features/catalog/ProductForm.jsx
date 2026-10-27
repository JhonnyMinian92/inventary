import { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, Boxes } from 'lucide-react';
import StepProgress from '../../components/StepProgress.jsx';
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
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
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
    setValidationMessage('');

    if (step === 0) {
      if (!String(form.name || '').trim() || !String(form.sku || '').trim()) {
        setValidationMessage('Escribe el nombre y el código del producto.');
        return;
      }
      if (!form.categoryId) {
        setValidationMessage('Selecciona una categoría para continuar.');
        return;
      }
      setStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }

    if (schemaLoading) {
      setValidationMessage('Espera a que terminen de cargar los campos de la categoría.');
      return;
    }
    if (schemaError) {
      setValidationMessage('No se puede continuar hasta cargar correctamente los campos de la categoría.');
      return;
    }
    if (String(form.costPrice ?? '').trim() === '' || String(form.salePrice ?? '').trim() === '' ||
        !Number.isFinite(Number(form.costPrice)) || Number(form.costPrice) < 0 ||
        !Number.isFinite(Number(form.salePrice)) || Number(form.salePrice) < 0) {
      setValidationMessage('El costo y el precio deben ser números válidos iguales o mayores que cero.');
      return;
    }

    const attributes = definitions.length ? {} : null;
    for (const definition of definitions) {
      const value = form.attributes[definition.key];
      const isEmpty = value === undefined || value === null || String(value).trim() === '';
      const label = definition.displayName || definition.key;
      if (definition.required && isEmpty) {
        setValidationMessage(`Completa el campo "${label}".`);
        return;
      }
      if (!isEmpty && definition.valueType === 'NUMBER') {
        const numericValue = Number(value);
        const constraints = definition.constraints || {};
        if (!Number.isFinite(numericValue) ||
            (constraints.min != null && numericValue < Number(constraints.min)) ||
            (constraints.max != null && numericValue > Number(constraints.max))) {
          setValidationMessage(`El valor de "${label}" está fuera del rango permitido.`);
          return;
        }
      }
      if (!isEmpty && definition.valueType === 'STRING') {
        const constraints = definition.constraints || {};
        const length = String(value).length;
        if ((constraints.minLength != null && length < Number(constraints.minLength)) ||
            (constraints.maxLength != null && length > Number(constraints.maxLength))) {
          setValidationMessage(`Revisa la longitud de "${label}".`);
          return;
        }
      }
      if (!isEmpty && attributes) attributes[definition.key] = value;
    }

    if (step === 1) {
      setStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    onSubmit({ ...form, name: form.name.trim(), sku: form.sku.trim(), attributes });
  }

  function previousStep() {
    setValidationMessage('');
    setStep(current => Math.max(0, current - 1));
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  return <form className="workflow-form" onSubmit={handleSubmit} noValidate>
    <StepProgress steps={['Información', 'Precios y campos', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
    {validationMessage && <div className="alert error workflow-validation" role="alert"><span>{validationMessage}</span></div>}
    {step === 0 && <section className="workflow-stage" aria-labelledby="product-basic-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Boxes size={20}/></span><div><h2 id="product-basic-title">Identifica el producto</h2><p>Primero define cómo se encontrará en el catálogo.</p></div></div>
      <div className="form-grid workflow-fields">
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
      </div>
    </section>}
    {step === 1 && <section className="workflow-stage" aria-labelledby="product-pricing-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Boxes size={20}/></span><div><h2 id="product-pricing-title">Precios y detalles</h2><p>Revisa los precios y completa solo los campos que correspondan.</p></div></div>
      <div className="form-grid workflow-fields">
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
      </div>
    </section>}
    {step === 2 && <section className="workflow-stage" aria-labelledby="product-review-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Boxes size={20}/></span><div><h2 id="product-review-title">Confirma los datos</h2><p>Comprueba la información antes de guardar.</p></div></div>
      <div className="workflow-review-grid">
        <div className="workflow-review-card"><small>Nombre</small><strong>{form.name.trim() || '—'}</strong></div>
        <div className="workflow-review-card"><small>Código / SKU</small><strong>{form.sku.trim() || '—'}</strong></div>
        <div className="workflow-review-card"><small>Categoría</small><strong>{categories.find(item => String(item.id || item.categoryId) === String(form.categoryId))?.name || '—'}</strong></div>
        <div className="workflow-review-card"><small>Costo</small><strong>{new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(form.costPrice || 0))}</strong></div>
        <div className="workflow-review-card"><small>Precio de venta</small><strong>{new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(form.salePrice || 0))}</strong></div>
        <div className="workflow-review-card"><small>Campos adicionales</small><strong>{Object.values(form.attributes || {}).filter(value => value !== '' && value !== null && value !== undefined).length}</strong></div>
      </div>
      {form.description.trim() && <p className="workflow-review-note"><strong>Descripción:</strong> {form.description.trim()}</p>}
    </section>}
    <div className="form-actions workflow-actions">
      <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
      <div className="workflow-actions-right">
        {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={previousStep}><ArrowLeft size={16}/> Atrás</button>}
        {step < 2 ? <button type="submit" className="button primary" disabled={loading || schemaLoading || Boolean(schemaError)}>{step === 0 ? 'Continuar' : 'Revisar'} <ArrowRight size={16}/></button>
          : <button type="submit" className="button primary" disabled={loading || schemaLoading || Boolean(schemaError)}>{loading ? 'Guardando…' : 'Guardar producto'}</button>}
      </div>
    </div>
  </form>;
}
