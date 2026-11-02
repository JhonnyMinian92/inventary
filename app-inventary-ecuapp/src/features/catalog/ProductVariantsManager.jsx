import { useEffect, useMemo, useState } from 'react';
import { Barcode, Plus, Power, RefreshCw, Tags } from 'lucide-react';
import { api } from '../../api.js';
import { confirmDialog, toast } from '../../ui/feedback.js';

function optionInput(definition, value, onChange) {
  const options = Array.isArray(definition.options) ? definition.options : [];
  const label = definition.displayName || definition.key;
  if (definition.valueType === 'ENUM' && options.length) {
    return <label className="field" key={definition.key}><span>{label}</span>
      <select value={value} onChange={event => onChange(definition.key, event.target.value)}>
        <option value="">Seleccionar</option>{options.map(option => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>;
  }
  if (definition.valueType === 'BOOLEAN') {
    return <label className="field" key={definition.key}><span>{label}</span>
      <select value={value} onChange={event => onChange(definition.key, event.target.value)}>
        <option value="">Seleccionar</option><option value="true">Sí</option><option value="false">No</option>
      </select>
    </label>;
  }
  const type = definition.valueType === 'NUMBER' ? 'number' : definition.valueType === 'DATE' ? 'date' : 'text';
  return <label className="field" key={definition.key}><span>{label}</span>
    <input type={type} step={type === 'number' ? 'any' : undefined} value={value}
      onChange={event => onChange(definition.key, event.target.value)} />
  </label>;
}

export default function ProductVariantsManager({ productId, definitions = [], disabled = false }) {
  const [variants, setVariants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [sku, setSku] = useState('');
  const [barcode, setBarcode] = useState('');
  const [salePrice, setSalePrice] = useState('');
  const [options, setOptions] = useState({});
  const usableDefinitions = useMemo(
    () => definitions.filter(item => item.valueType !== 'STRING_LIST'),
    [definitions]
  );

  async function load() {
    setLoading(true);
    setError('');
    try {
      const result = await api.productVariants(productId);
      setVariants(Array.isArray(result) ? result : []);
    } catch (failure) {
      setError(failure?.message || 'No se pudieron cargar las variantes.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [productId]);

  function changeOption(key, value) {
    setOptions(current => ({ ...current, [key]: value }));
  }

  async function create(event) {
    event.preventDefault();
    setError('');
    const optionValues = {};
    for (const definition of usableDefinitions) {
      const raw = options[definition.key];
      if (raw === undefined || raw === null || String(raw).trim() === '') continue;
      if (definition.valueType === 'NUMBER') {
        const numeric = Number(raw);
        if (!Number.isFinite(numeric)) { setError('Revisa el valor numérico de ' + (definition.displayName || definition.key) + '.'); return; }
        optionValues[definition.key] = numeric;
      } else if (definition.valueType === 'BOOLEAN') {
        optionValues[definition.key] = raw === true || raw === 'true';
      } else optionValues[definition.key] = raw;
    }
    if (!sku.trim()) { setError('El SKU de la variante es obligatorio.'); return; }
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(sku.trim())) { setError('El SKU admite letras, números, punto, guion y guion bajo (máximo 64 caracteres).'); return; }
    if (barcode.trim() && !/^[A-Za-z0-9-]{4,128}$/.test(barcode.trim())) { setError('El código de barras debe tener entre 4 y 128 letras, números o guiones.'); return; }
    if (!Object.keys(optionValues).length) { setError('Indica al menos una opción para identificar la variante.'); return; }
    if (salePrice.trim() && (!Number.isFinite(Number(salePrice)) || Number(salePrice) < 0 || !/^\\d+(?:\\.\\d{1,2})?$/.test(salePrice.trim()))) {
      setError('El precio debe ser positivo o cero y tener hasta dos decimales.'); return;
    }
    setBusy(true);
    try {
      await api.createProductVariant(productId, {
        sku: sku.trim().toUpperCase(),
        ...(barcode.trim() ? { barcode: barcode.trim() } : {}),
        optionValues,
        ...(salePrice.trim() ? { salePrice: Number(salePrice) } : {})
      });
      setSku(''); setBarcode(''); setSalePrice(''); setOptions({});
      await load();
      toast.success('Variante creada.');
    } catch (failure) {
      setError(failure?.message || 'No se pudo crear la variante.');
    } finally {
      setBusy(false);
    }
  }

  async function deactivate(variant) {
    if (!(await confirmDialog({ title: 'Desactivar variante', message: 'La variante ' + variant.sku + ' dejará de estar disponible para nuevas operaciones.', confirmLabel: 'Desactivar', tone: 'danger' }))) return;
    setBusy(true); setError('');
    try {
      await api.deactivateProductVariant(variant.id);
      await load();
      toast.success('Variante desactivada.');
    } catch (failure) {
      setError(failure?.message || 'No se pudo desactivar la variante.');
    } finally { setBusy(false); }
  }

  return <section className="product-variants-manager wide" aria-labelledby="product-variants-title">
    <div className="product-image-manager-heading">
      <span className="workflow-stage-icon"><Tags size={19}/></span>
      <div><h3 id="product-variants-title">Variantes y códigos de barras</h3>
        <p>Registra tallas, colores u otras combinaciones de la categoría. Cada variante tiene su propio SKU y puede tener precio propio.</p>
      </div>
      <button type="button" className="icon-button" aria-label="Actualizar variantes" title="Actualizar" disabled={loading || busy} onClick={() => void load()}><RefreshCw size={15}/></button>
    </div>
    {error && <p className="alert error" role="alert">{error}</p>}
    {usableDefinitions.length === 0
      ? <p className="form-hint">Esta categoría no tiene propiedades simples para formar variantes. Agrega campos como talla, color o capacidad en la configuración de la categoría.</p>
      : <form className="variant-create-form" onSubmit={create}>
        <div className="form-grid workflow-fields">
          <label className="field"><span>SKU de variante<b> *</b></span><input required maxLength="64" value={sku} onChange={event => setSku(event.target.value)} placeholder="Ej. CAM-NEG-M"/></label>
          <label className="field"><span>Código de barras</span><input maxLength="128" value={barcode} onChange={event => setBarcode(event.target.value)} placeholder="EAN / código interno"/></label>
          {usableDefinitions.map(definition => optionInput(definition, options[definition.key] ?? '', changeOption))}
          <label className="field"><span>Precio propio (opcional)</span><input type="number" min="0" step="0.01" value={salePrice} onChange={event => setSalePrice(event.target.value)} placeholder="Usar precio del producto"/></label>
          <div className="field variant-create-action"><span>&nbsp;</span><button type="submit" className="button secondary" disabled={disabled || busy || loading}><Plus size={15}/>Crear variante</button></div>
        </div>
      </form>}
    {loading ? <p className="form-hint">Cargando variantes…</p> : variants.length ? <div className="variant-list">
      {variants.map(variant => <article className={'variant-row' + (!variant.active ? ' is-inactive' : '')} key={variant.id}>
        <div className="variant-row-main"><strong>{variant.sku}</strong><small>{Object.entries(variant.optionValues || {}).map(([key, value]) => {
          const definition = usableDefinitions.find(item => item.key === key);
          return (definition?.displayName || key) + ': ' + (Array.isArray(value) ? value.join(', ') : String(value));
        }).join(' · ')}</small></div>
        <div className="variant-row-meta">{variant.barcode && <span><Barcode size={13}/>{variant.barcode}</span>}<strong>{new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(Number(variant.effectivePrice || 0))}</strong></div>
        <span className={'pill ' + (variant.active ? 'active' : 'inactive')}>{variant.active ? 'Activa' : 'Inactiva'}</span>
        {variant.active && <button type="button" className="icon-button ghost" title="Desactivar variante" aria-label={'Desactivar variante ' + variant.sku} disabled={disabled || busy} onClick={() => void deactivate(variant)}><Power size={15}/></button>}
      </article>)}
    </div> : <p className="form-hint">Todavía no hay variantes registradas.</p>}
  </section>;
}
