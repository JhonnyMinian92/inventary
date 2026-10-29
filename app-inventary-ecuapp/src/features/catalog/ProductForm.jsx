import { useEffect, useRef, useState } from 'react';
import { confirmDialog, toast, useMessageToast } from '../../ui/feedback.js';
import { ArrowLeft, ArrowRight, Boxes, ImagePlus, Star, Trash2, UploadCloud } from 'lucide-react';
import StepProgress from '../../components/StepProgress.jsx';
import { api, resolveApiUrl } from '../../api';
import QuickCreateDialog, { SelectWithCreate } from '../../ui/QuickCreateDialog.jsx';
import CategoryQuickCreate from './CategoryQuickCreate.jsx';

const DEFAULT_UNIT_ID = '00000000-0000-0000-0000-000000000001';
const FRACTION_OPTIONS = [['', 'Según cómo se mide (recomendado)'], ['false', 'No, solo cantidades enteras (1, 2, 3)'], ['true', 'Sí, permite decimales (ej. 1,5)']];

/** El formulario de alta rápida de unidades se arma con las opciones que entrega el backend. */
function unitQuickCreateConfig(dimensions) {
  return {
    title: 'Nueva forma de venta',
    description: 'Indica cómo cuentas este producto al comprar y vender (por unidad, por kilo, por litro…). Las características como talla, color o peso van en la categoría.',
    fields: [
      { key: 'name', label: 'Nombre', required: true, maxLength: 100, placeholder: 'Ej. Caja de 12, Saco de 50 kg, Galón', hint: 'Escribe el nombre de la unidad.',
        help: 'Así aparecerá al elegir cómo se vende un producto.' },
      { key: 'dimension', label: '¿Cómo se mide?', type: 'select', required: true, initial: dimensions[0]?.code || '',
        options: dimensions.map(item => [item.code, `${item.label} — ${item.examples}`]) },
      { key: 'allowsFractions', label: '¿Se puede vender en partes?', type: 'select', initial: '', options: FRACTION_OPTIONS,
        transform: value => value === '' ? undefined : value === 'true', help: 'Por ejemplo, medio kilo o 1,5 metros.' }
    ],
    create: values => api.createUnit(values)
  };
}

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
    // Solo lectura: el costo sale de la última compra recibida y el precio de venta suma la ganancia.
    costPrice: product?.costPrice ?? null,
    salePrice: product?.salePrice ?? null,
    marginPercent: String(product?.marginPercent ?? 25),
    // Servicios y productos sin existencias no se compran: su precio se escribe a mano.
    manualPrice: product?.kind && product.kind !== 'STOCKED_GOOD' ? String(product?.salePrice ?? '') : '',
    // Solo servicios: una sola vez o por suscripción con su plan por defecto.
    serviceBilling: product?.serviceBilling || 'ONE_TIME',
    billingFrequency: product?.billingFrequency || 'MONTHLY',
    termMonths: String(product?.termMonths || 1),
    attributes: objectValue(product?.attributes)
  };
}

/** Agrupa las unidades activas por forma de medir, usando la etiqueta que entrega el backend. */
function unitGroups(units) {
  const groups = new Map();
  for (const unit of units.filter(item => item.active !== false && item.status !== 'INACTIVE')) {
    const group = unit.dimensionLabel || unit.dimension || 'Otras';
    if (!groups.has(group)) groups.set(group, []);
    groups.get(group).push(unit);
  }
  return [...groups.entries()];
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

  const options = Array.isArray(definition.options) ? definition.options.map(String) : [];
  if (definition.valueType === 'STRING_LIST' || (definition.valueType === 'ENUM' && options.length && options.length <= 12)) {
    const multiple = definition.valueType === 'STRING_LIST';
    const current = multiple ? (Array.isArray(value) ? value.map(String) : []) : (value == null ? '' : String(value));
    const toggle = option => {
      if (multiple) onChange(definition.key, current.includes(option) ? current.filter(item => item !== option) : [...current, option]);
      else onChange(definition.key, current === option ? '' : option);
    };
    return <div className="field" role="group" aria-labelledby={common.id + '-label'}>
      <span id={common.id + '-label'}>{label}{definition.required && <b> *</b>}{multiple && <small className="field-note"> · puedes marcar varias</small>}</span>
      <div className="option-chips">
        {options.map(option => {
          const active = multiple ? current.includes(option) : current === option;
          return <button type="button" key={option} className={'option-chip' + (active ? ' selected' : '')} aria-pressed={active} onClick={() => toggle(option)}>{option}</button>;
        })}
      </div>
    </div>;
  }

  if (definition.valueType === 'ENUM') {
    return <label className="field" htmlFor={common.id}>
      <span>{label}{definition.required && <b> *</b>}</span>
      <select {...common}>
        <option value="">Seleccionar</option>
        {options.map(option => <option key={option} value={option}>{option}</option>)}
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

export default function ProductForm({ initialValue, categories, units = [], loading, onCancel, onSubmit, onError, onCategoryCreated, onUnitCreated }) {
  const productId = initialValue?.id || initialValue?.productId || '';
  const imageInputRef = useRef(null);
  const [form, setForm] = useState(() => normalizeProduct(initialValue));
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  useMessageToast(validationMessage, setValidationMessage);
  const [definitions, setDefinitions] = useState([]);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const [schemaError, setSchemaError] = useState('');
  useEffect(() => { if (schemaError) toast.error(schemaError); }, [schemaError]);
  const [images, setImages] = useState([]);
  const [imagesLoading, setImagesLoading] = useState(false);
  const [imageBusy, setImageBusy] = useState(false);
  const [imageFile, setImageFile] = useState(null);
  const [imageAltText, setImageAltText] = useState('');
  const [imagePrimary, setImagePrimary] = useState(false);
  const [imageError, setImageError] = useState('');
  useMessageToast(imageError, setImageError);
  const [imageNotice, setImageNotice] = useState('');
  useMessageToast(imageNotice, setImageNotice, 'success');
  const [quickCreate, setQuickCreate] = useState(null);
  const [dimensions, setDimensions] = useState(null);
  const [productKinds, setProductKinds] = useState([]);
  const [billingOptions, setBillingOptions] = useState(null);
  const [planPreview, setPlanPreview] = useState(null);
  const isSubscription = form.kind === 'SERVICE' && form.serviceBilling === 'SUBSCRIPTION';

  useEffect(() => {
    let active = true;
    api.productKinds()
      .then(kinds => { if (active) setProductKinds(Array.isArray(kinds) ? kinds : []); })
      .catch(() => { if (active) setProductKinds([]); });
    api.subscriptionOptions()
      .then(result => { if (active) setBillingOptions(result); })
      .catch(() => { if (active) setBillingOptions(null); });
    if (onUnitCreated) api.unitDimensions()
      .then(result => { if (active) setDimensions(Array.isArray(result) ? result : []); })
      .catch(() => { if (active) setDimensions(null); });
    return () => { active = false; };
  }, []);

  // Resumen del plan calculado por el backend, p. ej. "3 cobros de $20,00 cada 30 días · total $60,00".
  useEffect(() => {
    const price = Number(form.manualPrice);
    if (!isSubscription || String(form.manualPrice).trim() === '' || !Number.isFinite(price) || price < 0) { setPlanPreview(null); return undefined; }
    let active = true;
    const timer = setTimeout(() => {
      api.subscriptionPreview({ billingFrequency: form.billingFrequency, termMonths: Number(form.termMonths), monthlyPrice: price })
        .then(result => { if (active) setPlanPreview(result); })
        .catch(() => { if (active) setPlanPreview(null); });
    }, 300);
    return () => { active = false; clearTimeout(timer); };
  }, [isSubscription, form.billingFrequency, form.termMonths, form.manualPrice]);

  function handleCreated(created) {
    const kind = quickCreate;
    setQuickCreate(null);
    if (kind === 'category') {
      onCategoryCreated?.(created);
      changeCategory(created.id);
      toast.success(`Categoría ${created.name} creada. Completa sus datos en el siguiente paso.`);
    } else {
      onUnitCreated?.(created);
      changeField('unitOfMeasureId', created.id);
      toast.success(`Unidad ${created.name} creada y seleccionada.`);
    }
  }

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

  useEffect(() => {
    if (!productId) {
      setImages([]);
      setImagesLoading(false);
      return undefined;
    }
    let active = true;
    setImagesLoading(true);
    setImageError('');
    api.productImages(productId)
      .then(result => { if (active) setImages(Array.isArray(result) ? result : []); })
      .catch(error => { if (active) setImageError(error?.message || 'No se pudieron cargar las imágenes del producto.'); })
      .finally(() => { if (active) setImagesLoading(false); });
    return () => { active = false; };
  }, [productId]);

  async function uploadImage() {
    setImageError('');
    setImageNotice('');
    if (!productId) {
      setImageError('Guarda el producto primero. Después podrás abrirlo para añadir imágenes.');
      return;
    }
    if (!imageFile) {
      setImageError('Selecciona un archivo PNG o JPEG.');
      return;
    }
    if (!['image/png', 'image/jpeg'].includes(imageFile.type)) {
      setImageError('Elige una imagen PNG o JPEG. Otros formatos no están permitidos.');
      return;
    }
    if (imageFile.size < 1 || imageFile.size > 5 * 1024 * 1024) {
      setImageError('La imagen debe pesar menos de 5 MB.');
      return;
    }
    setImageBusy(true);
    try {
      const created = await api.uploadProductImage(productId, imageFile, {
        altText: imageAltText.trim(),
        sortOrder: images.length,
        primaryImage: imagePrimary || images.length === 0
      });
      setImages(current => [...current, created].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
      setImageFile(null);
      setImageAltText('');
      setImagePrimary(false);
      if (imageInputRef.current) imageInputRef.current.value = '';
      setImageNotice('Imagen guardada.');
    } catch (error) {
      setImageError(error?.message || 'No se pudo guardar la imagen.');
    } finally {
      setImageBusy(false);
    }
  }

  async function removeImage(image) {
    const imageId = image?.id;
    if (!productId || !imageId || imageBusy) return;
    if (!(await confirmDialog({ title: 'Quitar imagen', message: 'La imagen dejará de mostrarse en el catálogo público.', confirmLabel: 'Quitar', tone: 'danger' }))) return;
    setImageError('');
    setImageNotice('');
    setImageBusy(true);
    try {
      await api.unpublishProductImage(productId, imageId);
      setImages(current => current.filter(item => String(item.id) !== String(imageId)));
      setImageNotice('Imagen retirada del catálogo público.');
    } catch (error) {
      setImageError(error?.message || 'No se pudo retirar la imagen.');
    } finally {
      setImageBusy(false);
    }
  }

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
      if (!String(form.name || '').trim()) {
        setValidationMessage('Escribe el nombre del producto.');
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
    if (form.kind !== 'STOCKED_GOOD') {
      const price = Number(form.manualPrice);
      if (String(form.manualPrice).trim() === '' || !Number.isFinite(price) || price < 0 || Math.abs(Math.round(price * 100) - price * 100) > 1e-6) {
        setValidationMessage('Escribe el precio de venta (hasta 2 decimales).');
        return;
      }
    }
    const margin = Number(form.marginPercent);
    if (String(form.marginPercent).trim() === '' || !Number.isFinite(margin) || margin < 0 || margin > 1000) {
      setValidationMessage('La ganancia sugerida debe ser un porcentaje entre 0 y 1000.');
      return;
    }

    const attributes = definitions.length ? {} : null;
    for (const definition of definitions) {
      const value = form.attributes[definition.key];
      const isEmpty = value === undefined || value === null || String(value).trim() === '' || (Array.isArray(value) && !value.length);
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
    onSubmit({ ...form, name: form.name.trim(), attributes });
  }

  function previousStep() {
    setValidationMessage('');
    setStep(current => Math.max(0, current - 1));
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  return <form className="workflow-form" onSubmit={handleSubmit} noValidate>
    <StepProgress steps={['Información', 'Ganancia y datos', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
    {step === 0 && <section className="workflow-stage" aria-labelledby="product-basic-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Boxes size={20}/></span><div><h2 id="product-basic-title">Identifica el producto</h2><p>Primero define cómo se encontrará en el catálogo.</p></div></div>
      <div className="form-grid workflow-fields">
    <label className="field">
      <span>Nombre del producto<b> *</b></span>
      <input required maxLength="160" value={form.name} onChange={event => changeField('name', event.target.value)} placeholder="Ej. Teclado mecánico" autoComplete="off" />
    </label>
    <div className="field">
      <span>Código del producto</span>
      <div className="readonly-value">{form.sku ? <code>{form.sku}</code> : <em>Se genera automáticamente al guardar</em>}</div>
      <small className="field-help">Es único y sirve como referencia. Para vender o comprar solo buscas el producto por su nombre.</small>
    </div>
    <SelectWithCreate label="Categoría" required labelId="product-category-label" createLabel="Crear categoría" onCreate={onCategoryCreated && (() => setQuickCreate('category'))}
      help="Agrupa productos parecidos y define qué datos llenar (talla, color, marca…). Con el + creas una nueva con sus datos.">
      <select required aria-labelledby="product-category-label" value={form.categoryId} onChange={event => changeCategory(event.target.value)}>
        <option value="">Seleccionar categoría</option>
        {categories.filter(category => category.active !== false && category.status !== 'INACTIVE').map(category => <option key={category.id || category.categoryId} value={category.id || category.categoryId}>{category.name}</option>)}
      </select>
    </SelectWithCreate>
    <SelectWithCreate label="¿Cómo se vende y se cuenta?" labelId="product-unit-label" createLabel="Crear otra forma de venta" onCreate={onUnitCreated && dimensions && (() => setQuickCreate('unit'))}
      help="Por unidad, por kilo, por litro, por caja… Así se cuentan las existencias al comprar y vender.">
      {/* Sin selección el backend asigna la unidad base, que ya figura en la lista. */}
      <select aria-labelledby="product-unit-label" value={form.unitOfMeasureId || (units.some(unit => unit.id === DEFAULT_UNIT_ID) ? DEFAULT_UNIT_ID : '')} onChange={event => changeField('unitOfMeasureId', event.target.value)}>
        {!units.some(unit => unit.id === DEFAULT_UNIT_ID) && <option value="">Por unidad (predeterminado)</option>}
        {unitGroups(units).map(([group, items]) => <optgroup key={group} label={group}>
          {items.map(unit => <option key={unit.id} value={unit.id}>{unit.name}</option>)}
        </optgroup>)}
      </select>
    </SelectWithCreate>
    <div className="field wide" role="radiogroup" aria-labelledby="product-kind-label">
      <span id="product-kind-label">¿Qué estás registrando?<b> *</b></span>
      <small className="field-help">Define si el sistema lleva la cuenta de cuántos tienes.</small>
      {productKinds.length ? <div className="kind-grid">
        {productKinds.map(kind => <button type="button" role="radio" key={kind.code} aria-checked={form.kind === kind.code}
          className={'type-card' + (form.kind === kind.code ? ' selected' : '')} onClick={() => changeField('kind', kind.code)}>
          <strong>{kind.label}</strong><small>{kind.description}</small><small className="kind-examples">Ej.: {kind.examples}</small>
        </button>)}
      </div> : <p className="form-hint">Cargando opciones…</p>}
    </div>
      </div>
    </section>}
    {step === 1 && <section className="workflow-stage" aria-labelledby="product-pricing-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Boxes size={20}/></span><div><h2 id="product-pricing-title">{form.kind === 'STOCKED_GOOD' ? 'Ganancia y detalles' : 'Precio y detalles'}</h2><p>{form.kind === 'STOCKED_GOOD' ? 'No necesitas escribir precios: se calculan solos con cada compra.' : 'Como no se compra para revender, indica cuánto cobras.'}</p></div></div>
      <div className="form-grid workflow-fields">
    {form.kind === 'SERVICE' && <div className="field wide" role="radiogroup" aria-labelledby="service-billing-label">
      <span id="service-billing-label">¿Cómo se cobra este servicio?<b> *</b></span>
      <div className="kind-grid two">{(billingOptions?.billingModes || []).map(mode => <button type="button" role="radio" key={mode.code} aria-checked={form.serviceBilling === mode.code}
        className={'type-card' + (form.serviceBilling === mode.code ? ' selected' : '')} onClick={() => changeField('serviceBilling', mode.code)}>
        <strong>{mode.label}</strong><small>{mode.description}</small></button>)}</div>
    </div>}
    {form.kind !== 'STOCKED_GOOD' && <label className="field">
      <span>{isSubscription ? 'Precio por mes' : 'Precio de venta'}<b> *</b></span>
      <input type="number" min="0" step="0.01" inputMode="decimal" required value={form.manualPrice} onChange={event => changeField('manualPrice', event.target.value)} placeholder="0.00" />
      <small className="field-help">{isSubscription ? 'Con este valor se calcula cada cobro. Al crear la suscripción de un cliente podrás ajustarlo.' : 'Al vender podrás ajustarlo.'}</small>
    </label>}
    {isSubscription && <>
      <div className="field wide"><span>¿Cada cuánto se cobra?<b> *</b></span>
        <div className="type-grid">{(billingOptions?.frequencies || []).map(item => <button type="button" key={item.code} aria-pressed={form.billingFrequency === item.code}
          className={'type-card' + (form.billingFrequency === item.code ? ' selected' : '')} onClick={() => changeField('billingFrequency', item.code)}>
          <strong>{item.label}</strong><small>{item.description}</small></button>)}</div>
      </div>
      <div className="field wide"><span>¿Cuánto dura el plan?<b> *</b></span>
        <div className="option-chips">{(billingOptions?.terms || []).map(term => <button type="button" key={term.months} aria-pressed={Number(form.termMonths) === term.months}
          className={'option-chip' + (Number(form.termMonths) === term.months ? ' selected' : '')} onClick={() => changeField('termMonths', String(term.months))}>{term.label}</button>)}</div>
        {planPreview && <small className="field-help plan-summary">{planPreview.summary}</small>}
      </div>
    </>}
    {form.kind === 'STOCKED_GOOD' && <>
    <label className="field">
      <span>Ganancia sugerida (%)<b> *</b></span>
      <input type="number" min="0" max="1000" step="0.01" inputMode="decimal" required value={form.marginPercent} onChange={event => changeField('marginPercent', event.target.value)} />
      <small className="field-help">Se suma al costo de compra para sugerir el precio. Ej.: costo $8 + 25 % = $10. Al vender puedes cambiar el precio, pero nunca por debajo del costo.</small>
    </label>
    <div className="pricing-summary">
      <div><small>Costo (última compra)</small><strong>{Number(form.costPrice) > 0 ? new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(form.costPrice)) : 'Aún sin compras'}</strong></div>
      <div><small>Precio sugerido</small><strong>{Number(form.costPrice) > 0 ? new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Math.ceil(Number(form.costPrice) * (1 + Number(form.marginPercent || 0) / 100) * 100 - 1e-9) / 100) : 'Se calculará con la primera compra'}</strong></div>
    </div>
    </>}
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
    {schemaError && <p className="form-hint wide">No se pudieron cargar las propiedades de esta categoría.</p>}
      </div>
      {initialValue && productId ? <section className="product-image-manager wide" aria-labelledby="product-images-title">
        <div className="product-image-manager-heading">
          <span className="workflow-stage-icon"><ImagePlus size={19}/></span>
          <div><h3 id="product-images-title">Imágenes del producto</h3><p>Se aceptan imágenes PNG o JPEG de hasta 5 MB.</p></div>
        </div>
        <div className="product-image-upload-fields">
          <label className="field"><span>Archivo de imagen<b> *</b></span>
            <input ref={imageInputRef} type="file" accept="image/png,image/jpeg" disabled={imageBusy || loading} onChange={event => { setImageFile(event.target.files?.[0] || null); setImageError(''); setImageNotice(''); }} />
            <small className="field-help">{imageFile ? `${imageFile.name} · ${(imageFile.size / 1024 / 1024).toFixed(2)} MB` : 'El archivo se valida también en el servidor.'}</small>
          </label>
          <label className="field"><span>Texto alternativo <small>(opcional)</small></span>
            <input maxLength="300" value={imageAltText} disabled={imageBusy || loading} onChange={event => setImageAltText(event.target.value)} placeholder="Describe brevemente la imagen" />
          </label>
          <label className="checkbox-field product-image-primary">
            <span>Marcar como imagen principal</span>
            <input type="checkbox" checked={imagePrimary || images.length === 0} disabled={imageBusy || loading || images.length === 0} onChange={event => setImagePrimary(event.target.checked)} />
          </label>
          <button type="button" className="button secondary" disabled={imageBusy || loading || imagesLoading || !imageFile} onClick={uploadImage}>
            <UploadCloud size={16}/>{imageBusy ? 'Guardando imagen…' : 'Subir imagen'}
          </button>
        </div>
        <div className="product-image-gallery" aria-live="polite">
          {imagesLoading && <p className="form-hint">Cargando imágenes…</p>}
          {!imagesLoading && !images.length && <p className="form-hint">Todavía no hay imágenes adicionales para este producto.</p>}
          {images.map(image => <article className="product-image-card" key={image.id}>
            <img src={resolveApiUrl(image.imageUrl)} alt={image.altText || form.name || 'Imagen del producto'} loading="lazy" />
            <div className="product-image-card-details">
              <strong>{image.altText || 'Imagen del producto'}</strong>
              {image.primaryImage && <span className="product-image-primary-label"><Star size={12} fill="currentColor"/> Principal</span>}
              <button type="button" className="text-button danger-text" disabled={imageBusy || loading} onClick={() => removeImage(image)}><Trash2 size={14}/> Quitar imagen</button>
            </div>
          </article>)}
        </div>
      </section> : <p className="form-hint wide product-image-first-save">Guarda el producto y vuelve a editarlo para añadir imágenes.</p>}
    </section>}
    {step === 2 && <section className="workflow-stage" aria-labelledby="product-review-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Boxes size={20}/></span><div><h2 id="product-review-title">Confirma los datos</h2><p>Comprueba la información antes de guardar.</p></div></div>
      <div className="workflow-review-grid">
        <div className="workflow-review-card"><small>Nombre</small><strong>{form.name.trim() || '—'}</strong></div>
        <div className="workflow-review-card"><small>Código</small><strong>{form.sku || 'Se asignará al guardar'}</strong></div>
        <div className="workflow-review-card"><small>Categoría</small><strong>{categories.find(item => String(item.id || item.categoryId) === String(form.categoryId))?.name || '—'}</strong></div>
        <div className="workflow-review-card"><small>Tipo</small><strong>{productKinds.find(kind => kind.code === form.kind)?.label || form.kind}</strong></div>
        <div className="workflow-review-card"><small>Se vende por</small><strong>{units.find(unit => unit.id === (form.unitOfMeasureId || DEFAULT_UNIT_ID))?.name || 'Unidad'}</strong></div>
        {form.kind === 'STOCKED_GOOD' && <div className="workflow-review-card"><small>Ganancia sugerida</small><strong>{Number(form.marginPercent || 0)} %</strong></div>}
        {isSubscription && <div className="workflow-review-card"><small>Plan</small><strong>{planPreview?.summary || '—'}</strong></div>}
        <div className="workflow-review-card"><small>{isSubscription ? 'Precio por mes' : 'Precio de venta'}</small><strong>{form.kind !== 'STOCKED_GOOD' ? new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(form.manualPrice || 0)) : Number(form.costPrice) > 0 ? new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Math.ceil(Number(form.costPrice) * (1 + Number(form.marginPercent || 0) / 100) * 100 - 1e-9) / 100) : 'Con la primera compra'}</strong></div>
        <div className="workflow-review-card"><small>Campos adicionales</small><strong>{Object.values(form.attributes || {}).filter(value => value !== '' && value !== null && value !== undefined).length}</strong></div>
      </div>
      {form.description.trim() && <p className="workflow-review-note"><strong>Descripción:</strong> {form.description.trim()}</p>}
    </section>}
    <div className="form-actions workflow-actions">
      <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
      <div className="workflow-actions-right">
        {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={previousStep}><ArrowLeft size={16}/> Atrás</button>}
        {step < 2 ? <button type="submit" className="button primary" disabled={loading || (step > 0 && (schemaLoading || Boolean(schemaError)))}>{step === 0 ? 'Continuar' : 'Revisar'} <ArrowRight size={16}/></button>
          : <button type="submit" className="button primary" disabled={loading || schemaLoading || Boolean(schemaError)}>{loading ? 'Guardando…' : 'Guardar producto'}</button>}
      </div>
    </div>
    {quickCreate === 'category' && <CategoryQuickCreate productName={form.name} onCreated={handleCreated} onClose={() => setQuickCreate(null)} />}
    {quickCreate === 'unit' && dimensions && (() => {
      const config = unitQuickCreateConfig(dimensions);
      return <QuickCreateDialog title={config.title} description={config.description} fields={config.fields} submitLabel="Crear y seleccionar"
        onCreate={config.create} onCreated={handleCreated} onClose={() => setQuickCreate(null)} />;
    })()}
  </form>;
}
