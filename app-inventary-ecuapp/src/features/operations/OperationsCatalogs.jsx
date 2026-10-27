import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, listFrom, listAllPages } from '../../api';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';
import UnitConversions from './UnitConversions.jsx';

const configs = {
  warehouses: {
    label: 'Almacenes',
    singular: 'almacén',
    fields: [
      { key: 'code', label: 'Código', required: true, maxLength: 40, pattern: '[A-Za-z0-9][A-Za-z0-9._-]{0,39}' },
      { key: 'name', label: 'Nombre', required: true, maxLength: 140 },
      { key: 'address', label: 'Dirección', maxLength: 500 }
    ],
    list: api.warehouses,
    create: api.createWarehouse,
    update: api.updateWarehouse,
    activate: api.activateWarehouse,
    deactivate: api.deactivateWarehouse
  },
  suppliers: {
    label: 'Proveedores',
    singular: 'proveedor',
    fields: [
      { key: 'code', label: 'Código', required: true, maxLength: 40 },
      { key: 'name', label: 'Nombre comercial', required: true, maxLength: 180 },
      { key: 'taxId', label: 'RUC / identificación tributaria', maxLength: 32 },
      { key: 'email', label: 'Correo electrónico', type: 'email', maxLength: 254 },
      { key: 'phone', label: 'Teléfono', maxLength: 40 },
      { key: 'address', label: 'Dirección', maxLength: 500 }
    ],
    list: api.suppliers,
    create: api.createSupplier,
    update: api.updateSupplier,
    activate: api.activateSupplier,
    deactivate: api.deactivateSupplier
  },
  customers: {
    label: 'Clientes',
    singular: 'cliente',
    fields: [
      { key: 'code', label: 'Código', required: true, maxLength: 40 },
      { key: 'name', label: 'Nombre', required: true, maxLength: 180 },
      { key: 'taxId', label: 'RUC / identificación tributaria', maxLength: 32 },
      { key: 'email', label: 'Correo electrónico', type: 'email', maxLength: 254 },
      { key: 'phone', label: 'Teléfono', maxLength: 40 },
      { key: 'address', label: 'Dirección', maxLength: 500 }
    ],
    list: api.customers,
    create: api.createCustomer,
    update: api.updateCustomer,
    activate: api.activateCustomer,
    deactivate: api.deactivateCustomer
  },
  units: {
    label: 'Unidades',
    singular: 'unidad de medida',
    fields: [
      { key: 'code', label: 'Código', required: true, maxLength: 24, pattern: '[A-Za-z][A-Za-z0-9_]{0,23}' },
      { key: 'name', label: 'Nombre', required: true, maxLength: 100 },
      { key: 'dimension', label: 'Dimensión', required: true, options: ['COUNT', 'MASS', 'LENGTH', 'VOLUME', 'AREA', 'TIME', 'OTHER'] },
      { key: 'decimalScale', label: 'Decimales permitidos', required: true, type: 'number', min: 0, max: 3 }
    ],
    list: api.units,
    create: api.createUnit,
    update: api.updateUnit
  }
};

function idOf(item) { return item?.id ?? item?.warehouseId ?? item?.supplierId ?? item?.customerId ?? item?.unitOfMeasureId ?? ''; }
function inactive(item) { return item?.active === false || String(item?.status || '').toUpperCase() === 'INACTIVE'; }
function freshData(kind, item = null) {
  const values = Object.fromEntries(configs[kind].fields.map(field => [field.key, item?.[field.key] == null ? (field.key === 'decimalScale' ? '0' : '') : String(item[field.key])]));
  if (kind === 'units') values.active = item?.active == null ? 'true' : String(item.active);
  return values;
}

export default function OperationsCatalogs({ onChanged }) {
  const [kind, setKind] = useState('warehouses');
  const [records, setRecords] = useState({ warehouses: [], suppliers: [], customers: [], units: [] });
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const dialogRef = useAccessibleDialog(modal, () => setModal(false));

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    const [warehouses, suppliers, customers, units] = await Promise.allSettled([
      listAllPages(api.warehouses, { size: 100 }), listAllPages(api.suppliers, { size: 100 }), listAllPages(api.customers, { size: 100 }), api.units()
    ]);
    const result = { warehouses: [], suppliers: [], customers: [], units: [] };
    const failures = [];
    for (const [key, response] of [['warehouses', warehouses], ['suppliers', suppliers], ['customers', customers], ['units', units]]) {
      if (response.status === 'fulfilled') result[key] = listFrom(response.value);
      else failures.push(configs[key].label.toLowerCase() + ': ' + (response.reason?.message || 'error'));
    }
    setRecords(current => Object.fromEntries(Object.keys(result).map(key => [key, failures.some(failure => failure.startsWith(configs[key].label.toLowerCase() + ':')) ? current[key] : result[key]])));
    if (failures.length) setError('Algunos catálogos no se pudieron cargar. ' + failures.join(' · '));
    setLoading(false);
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  const visible = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    if (!query) return records[kind];
    return records[kind].filter(item => configs[kind].fields.some(field => String(item?.[field.key] || '').toLocaleLowerCase().includes(query)));
  }, [records, kind, search]);

  function open(item = null) {
    setEditing(item);
    setForm(freshData(kind, item));
    setError('');
    setNotice('');
    setModal(true);
  }

  async function save(event) {
    event.preventDefault();
    const config = configs[kind];
    for (const field of config.fields.filter(field => field.required)) {
      if (!String(form[field.key] || '').trim()) {
        setError('El campo "' + field.label + '" es obligatorio.');
        return;
      }
    }
    let payload;
    if (kind === 'units') {
      const decimalScale = Number(form.decimalScale);
      if (!Number.isInteger(decimalScale) || decimalScale < 0 || decimalScale > 3) {
        setError('La escala decimal debe ser un entero entre 0 y 3.');
        return;
      }
      payload = editing
        ? { name: String(form.name || '').trim(), decimalScale, active: form.active !== 'false' }
        : { code: String(form.code || '').trim(), name: String(form.name || '').trim(), dimension: form.dimension, decimalScale };
    } else {
      payload = Object.fromEntries(config.fields.map(field => [field.key, String(form[field.key] || '').trim()]).filter(([key, value]) => value !== '' || config.fields.some(field => field.key === key && field.required)));
    }
    setLoading(true);
    setError('');
    setNotice('');
    try {
      if (editing) await config.update(idOf(editing), payload);
      else await config.create(payload);
      setModal(false);
      setEditing(null);
      setNotice('Se guardó el registro de ' + config.singular + '.');
      await refresh();
      await onChanged?.();
    } catch (err) {
      setError(err?.message || 'No se pudo guardar el registro.');
    } finally {
      setLoading(false);
    }
  }

  async function toggle(item) {
    const config = configs[kind];
    const action = inactive(item) ? config.activate : config.deactivate;
    if (!action) return;
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await action(idOf(item));
      setNotice(inactive(item) ? 'Registro activado.' : 'Registro desactivado.');
      await refresh();
      await onChanged?.();
    } catch (err) {
      setError(err?.message || 'No se pudo cambiar el estado.');
    } finally {
      setLoading(false);
    }
  }

  const config = configs[kind];

  return <section className="panel">
    <div className="panel-heading">
      <div className="panel-title-icon"><span> <strong>◎</strong> </span><div><h2>Almacenes, contactos y unidades</h2><p>Administra los datos auxiliares requeridos por compras, productos y ventas.</p></div></div>
      <button className="button primary" onClick={() => open()}>+ Nuevo {config.singular}</button>
    </div>
    <div className="catalog-tabs" role="tablist" aria-label="Catálogos auxiliares">
      {Object.entries(configs).map(([key, entry]) => <button key={key} role="tab" aria-selected={kind === key} className={kind === key ? 'catalog-tab active' : 'catalog-tab'} onClick={() => { setKind(key); setSearch(''); setError(''); setNotice(''); }}>{entry.label}<span>{records[key].length}</span></button>)}
    </div>
    {error && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success" role="status" aria-live="polite"><span>{notice}</span><button type="button" aria-label="Cerrar notificación" onClick={() => setNotice('')}>×</button></div>}
    <div className="toolbar">
      <div className="search-box"><input aria-label={'Buscar en ' + config.label.toLowerCase()} value={search} onChange={event => setSearch(event.target.value)} placeholder={'Buscar ' + config.label.toLowerCase() + '…'} /></div>
      <span className="muted">{loading ? 'Actualizando…' : visible.length + ' registros'}</span>
    </div>
    <div className="table-wrap"><table><thead><tr>{config.fields.map(field => <th key={field.key}>{field.label}</th>)}<th>Estado</th><th>Acciones</th></tr></thead><tbody>
      {visible.map((item, index) => <tr key={idOf(item) || index}>
        {config.fields.map(field => <td key={field.key}>{item?.[field.key] || '—'}</td>)}
        <td><span className={'pill ' + (inactive(item) ? 'inactive' : 'active')}>{inactive(item) ? 'Inactivo' : 'Activo'}</span></td>
        <td><div className="row-actions"><button className="small-action" disabled={loading} onClick={() => open(item)}>Editar</button>{config.activate && config.deactivate && <button className="small-action" disabled={loading} onClick={() => toggle(item)}>{inactive(item) ? 'Activar' : 'Desactivar'}</button>}</div></td>
      </tr>)}
    </tbody></table>{!visible.length && <div className="empty-state">{loading ? 'Cargando registros…' : 'No hay registros que coincidan con la búsqueda.'}</div>}</div>
    {kind === 'units' && <UnitConversions units={records.units} />}

    {modal && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setModal(false); }}>
      <section ref={dialogRef} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="operations-catalog-modal-title" tabIndex={-1}>
        <div className="modal-heading"><h2 id="operations-catalog-modal-title">{editing ? 'Editar ' : 'Crear '}{config.singular}</h2><button type="button" className="icon-button" aria-label="Cerrar ventana" onClick={() => setModal(false)}>×</button></div>
        <form className="form-grid" onSubmit={save}>
          {config.fields.map(field => <label className="field" key={field.key}>
            <span>{field.label}{field.required && <b> *</b>}</span>
            {field.options
              ? <select required={Boolean(field.required)} value={form[field.key] || ''} onChange={event => setForm(current => ({ ...current, [field.key]: event.target.value }))}><option value="">Seleccionar</option>{field.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
              : <input required={Boolean(field.required)} disabled={Boolean(editing && field.key === 'code')} type={field.type || 'text'} min={field.min} max={field.max} maxLength={field.maxLength} pattern={field.pattern} value={form[field.key] || ''} onChange={event => setForm(current => ({ ...current, [field.key]: event.target.value }))} />}
          </label>)}
          {kind === 'units' && editing && <label className="field"><span>Estado</span><select value={form.active || 'true'} onChange={event => setForm(current => ({ ...current, active: event.target.value }))}><option value="true">Activa</option><option value="false">Inactiva</option></select></label>}
          {error && <div className="alert error wide">{error}</div>}
          <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => setModal(false)}>Cancelar</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : 'Guardar registro'}</button></div>
        </form>
      </section>
    </div>}
  </section>;
}
