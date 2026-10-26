import { useCallback, useEffect, useState } from 'react';
import { api, listFrom } from '../../api';

const emptyLine = () => ({ productId: '', quantity: '1' });
function idOf(item) { return item?.id ?? item?.productId ?? ''; }
function statusOf(item) { return String(item?.status || 'DRAFT').toUpperCase(); }
function date(value) { if (!value) return '—'; const parsed = new Date(value); return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleDateString('es-EC'); }
function titleCase(value = '') { return String(value).replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, value => value.toUpperCase()); }

export default function InventoryOperations({ products, warehouses, suppliers, onChanged }) {
  const [tab, setTab] = useState('transfers');
  const [transfers, setTransfers] = useState([]);
  const [counts, setCounts] = useState([]);
  const [rules, setRules] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState('');
  const [ruleEditing, setRuleEditing] = useState(null);
  const [reasonDialog, setReasonDialog] = useState(null);
  const [reasonText, setReasonText] = useState('');
  const [transferForm, setTransferForm] = useState({ sourceWarehouseId: '', destinationWarehouseId: '', items: [emptyLine()] });
  const [countForm, setCountForm] = useState({ warehouseId: '', items: [emptyLine()] });
  const [ruleForm, setRuleForm] = useState({ productId: '', warehouseId: '', minimumQuantity: '0', targetQuantity: '1', leadTimeDays: '1', preferredSupplierId: '' });
  const [adjustmentForm, setAdjustmentForm] = useState({ productId: '', warehouseId: warehouses[0]?.id || '', direction: 'INCREASE', quantity: '1', unitCost: '0', reason: '' });

  const refresh = useCallback(async () => {
    const results = await Promise.allSettled([
      api.inventoryTransfers(),
      api.inventoryCounts(),
      api.reorderRules(),
      api.replenishmentSuggestions()
    ]);
    const setters = [setTransfers, setCounts, setRules, setSuggestions];
    const labels = ['traslados', 'conteos físicos', 'reglas de reposición', 'sugerencias'];
    const failures = [];
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') setters[index](listFrom(result.value));
      else failures.push(labels[index] + ': ' + (result.reason?.message || 'error'));
    });
    if (failures.length) setError('Algunas operaciones no se pudieron cargar. ' + failures.join(' · '));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  function updateLine(formKind, index, field, value) {
    const setter = formKind === 'transfer' ? setTransferForm : setCountForm;
    setter(current => ({ ...current, items: current.items.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line) }));
  }

  function addLine(formKind) {
    const setter = formKind === 'transfer' ? setTransferForm : setCountForm;
    setter(current => ({ ...current, items: [...current.items, emptyLine()] }));
  }

  function removeLine(formKind, index) {
    const setter = formKind === 'transfer' ? setTransferForm : setCountForm;
    setter(current => ({ ...current, items: current.items.filter((_, lineIndex) => lineIndex !== index) }));
  }

  async function operate(action, success) {
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      setModal('');
      setRuleEditing(null);
      await refresh();
      await onChanged?.();
      return true;
    } catch (err) {
      setError(err?.message || 'No se pudo completar la operación.');
      return false;
    } finally {
      setLoading(false);
    }
  }

  function submitTransfer(event) {
    event.preventDefault();
    const items = transferForm.items.map(line => ({ productId: line.productId, quantity: Number(line.quantity) }));
    if (!transferForm.sourceWarehouseId || !transferForm.destinationWarehouseId || transferForm.sourceWarehouseId === transferForm.destinationWarehouseId ||
        !items.length || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0)) {
      setError('Selecciona almacenes diferentes y completa todas las líneas con cantidades positivas.');
      return;
    }
    void operate(() => api.createInventoryTransfer({
      sourceWarehouseId: transferForm.sourceWarehouseId,
      destinationWarehouseId: transferForm.destinationWarehouseId,
      items
    }), 'Traslado creado. Despáchalo para iniciar el movimiento de mercancía.');
  }

  function submitCount(event) {
    event.preventDefault();
    const items = countForm.items.map(line => ({ productId: line.productId, countedQuantity: Number(line.quantity) }));
    if (!countForm.warehouseId || !items.length || items.some(item => !item.productId || !Number.isFinite(item.countedQuantity) || item.countedQuantity < 0)) {
      setError('Selecciona el almacén y productos, con cantidades contadas iguales o mayores que cero.');
      return;
    }
    void operate(() => api.createInventoryCount({ warehouseId: countForm.warehouseId, items }), 'Conteo creado. Revisa las cantidades antes de contabilizar los ajustes.');
  }

  function submitRule(event) {
    event.preventDefault();
    const minimumQuantity = Number(ruleForm.minimumQuantity);
    const targetQuantity = Number(ruleForm.targetQuantity);
    const leadTimeDays = Number(ruleForm.leadTimeDays);
    if (!ruleForm.productId || !ruleForm.warehouseId || minimumQuantity < 0 || targetQuantity <= minimumQuantity || leadTimeDays < 0 ||
        !Number.isFinite(minimumQuantity) || !Number.isFinite(targetQuantity) || !Number.isFinite(leadTimeDays)) {
      setError('El objetivo debe superar el mínimo; el mínimo y el plazo no pueden ser negativos.');
      return;
    }
    const data = { productId: ruleForm.productId, warehouseId: ruleForm.warehouseId, minimumQuantity, targetQuantity, leadTimeDays };
    if (ruleForm.preferredSupplierId) data.preferredSupplierId = ruleForm.preferredSupplierId;
    else data.preferredSupplierId = null;
    void operate(() => ruleEditing ? api.updateReorderRule(idOf(ruleEditing), data) : api.createReorderRule(data), ruleEditing ? 'Regla de reposición actualizada.' : 'Regla de reposición creada.');
  }

  function submitAdjustment(event) {
    event.preventDefault();
    const quantity = Number(adjustmentForm.quantity);
    const unitCost = Number(adjustmentForm.unitCost);
    if (!adjustmentForm.productId || !adjustmentForm.warehouseId || !adjustmentForm.reason.trim() || !Number.isFinite(quantity) || quantity <= 0 ||
        (adjustmentForm.direction === 'INCREASE' && (!Number.isFinite(unitCost) || unitCost < 0))) {
      setError('Selecciona producto y almacén, indica un motivo y usa una cantidad positiva. Para entradas, el costo unitario debe ser válido.');
      return;
    }
    const payload = {
      productId: adjustmentForm.productId,
      warehouseId: adjustmentForm.warehouseId,
      direction: adjustmentForm.direction,
      quantity,
      reason: adjustmentForm.reason.trim()
    };
    if (adjustmentForm.direction === 'INCREASE') payload.unitCost = unitCost;
    void operate(() => api.createInventoryAdjustment(payload), 'Ajuste registrado por el backend y reflejado en los movimientos.');
  }

  const transferStatus = statusOf;
  return <section className="panel inventory-operations">
    <div className="panel-heading"><div className="panel-title-icon"><span>⇄</span><div><h2>Operaciones de inventario</h2><p>Traslados entre almacenes, conteos físicos y reposición de existencias.</p></div></div><button className="button secondary" onClick={() => void refresh()} disabled={loading}>Actualizar</button></div>
    {error && <div className="alert error"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success"><span>{notice}</span><button onClick={() => setNotice('')}>×</button></div>}
    <div className="catalog-tabs" role="tablist" aria-label="Operaciones de inventario">
      <button className={tab === 'transfers' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'transfers'} onClick={() => setTab('transfers')}>Traslados <span>{transfers.length}</span></button>
      <button className={tab === 'counts' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'counts'} onClick={() => setTab('counts')}>Conteos físicos <span>{counts.length}</span></button>
      <button className={tab === 'reorder' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'reorder'} onClick={() => setTab('reorder')}>Reposición <span>{suggestions.length}</span></button>
      <button className={tab === 'adjustments' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'adjustments'} onClick={() => setTab('adjustments')}>Ajustes</button>
    </div>

    {tab === 'transfers' && <>
      <div className="toolbar"><p className="muted">Un traslado reduce el origen al despacharse y aumenta el destino al recibirse.</p><button className="button primary" onClick={() => { setTransferForm({ sourceWarehouseId: warehouses[0]?.id || '', destinationWarehouseId: warehouses[1]?.id || '', items: [emptyLine()] }); setModal('transfer'); }}>+ Nuevo traslado</button></div>
      <div className="table-wrap"><table><thead><tr><th>Documento</th><th>Origen</th><th>Destino</th><th>Estado</th><th>Fecha</th><th>Acciones</th></tr></thead><tbody>
        {transfers.map((transfer, index) => {
          const status = transferStatus(transfer);
          return <tr key={idOf(transfer) || index}>
            <td><strong>{transfer.number || transfer.code || String(idOf(transfer)).slice(0, 10)}</strong></td>
            <td>{transfer.sourceWarehouseName || transfer.sourceWarehouse?.name || '—'}</td><td>{transfer.destinationWarehouseName || transfer.destinationWarehouse?.name || '—'}</td>
            <td><span className={'pill ' + (['RECEIVED','COMPLETED','POSTED'].includes(status) ? 'active' : ['CANCELLED','REVERSED'].includes(status) ? 'inactive' : 'pending')}>{titleCase(status)}</span></td>
            <td>{date(transfer.createdAt)}</td>
            <td><div className="row-actions">
              {['DRAFT','PENDING'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.dispatchInventoryTransfer(idOf(transfer)), 'Traslado despachado.')}>Despachar</button>}
              {['DISPATCHED','IN_TRANSIT'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.receiveInventoryTransfer(idOf(transfer)), 'Traslado recibido en el almacén destino.')}>Recibir</button>}
              {['DRAFT','PENDING','DISPATCHED','IN_TRANSIT'].includes(status) && <button className="small-action" disabled={loading} onClick={() => { setReasonText(''); setReasonDialog({ kind: 'cancel', id: idOf(transfer), label: transfer.number || transfer.code || idOf(transfer) }); }}>Cancelar</button>}
              {['RECEIVED','COMPLETED'].includes(status) && <button className="small-action" disabled={loading} onClick={() => { setReasonText(''); setReasonDialog({ kind: 'reverse', id: idOf(transfer), label: transfer.number || transfer.code || idOf(transfer) }); }}>Revertir</button>}
            </div></td>
          </tr>;
        })}
      </tbody></table>{!transfers.length && <div className="empty-state">{loading ? 'Cargando traslados…' : 'No hay traslados registrados.'}</div>}</div>
    </>}

    {tab === 'counts' && <>
      <div className="toolbar"><p className="muted">Registra lo contado físicamente; el sistema conserva el ajuste para contabilizarlo después.</p><button className="button primary" onClick={() => { setCountForm({ warehouseId: warehouses[0]?.id || '', items: [emptyLine()] }); setModal('count'); }}>+ Nuevo conteo</button></div>
      <div className="table-wrap"><table><thead><tr><th>Documento</th><th>Almacén</th><th>Estado</th><th>Fecha</th><th>Acciones</th></tr></thead><tbody>
        {counts.map((count, index) => {
          const status = statusOf(count);
          return <tr key={idOf(count) || index}><td><strong>{count.number || count.code || String(idOf(count)).slice(0, 10)}</strong></td><td>{count.warehouseName || count.warehouse?.name || '—'}</td><td><span className={'pill ' + (['POSTED','COMPLETED'].includes(status) ? 'active' : status === 'CANCELLED' ? 'inactive' : 'pending')}>{titleCase(status)}</span></td><td>{date(count.createdAt)}</td><td><div className="row-actions">
            {['DRAFT','PENDING','OPEN'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.postInventoryCount(idOf(count)), 'Conteo contabilizado. Los ajustes físicos se reflejan en los saldos.')}>Contabilizar</button>}
            {['DRAFT','PENDING','OPEN'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.cancelInventoryCount(idOf(count)), 'Conteo cancelado.')}>Cancelar</button>}
          </div></td></tr>;
        })}
      </tbody></table>{!counts.length && <div className="empty-state">{loading ? 'Cargando conteos…' : 'No hay conteos físicos registrados.'}</div>}</div>
    </>}

    {tab === 'adjustments' && <>
      <div className="toolbar"><p className="muted">Registra entradas o salidas justificadas. Todos los cambios quedan auditados por el backend.</p><button className="button primary" onClick={() => { setAdjustmentForm({ productId: '', warehouseId: warehouses[0]?.id || '', direction: 'INCREASE', quantity: '1', unitCost: '0', reason: '' }); setModal('adjustment'); }}>+ Nuevo ajuste</button></div>
      <div className="form-hint wide">Usa este flujo para diferencias justificadas de inventario. Para movimientos entre ubicaciones utiliza la pestaña Traslados.</div>
    </>}

    {tab === 'reorder' && <>
      <div className="toolbar"><p className="muted">Define mínimos y objetivos para recibir sugerencias cuando el stock sea bajo.</p><button className="button primary" onClick={() => { setRuleEditing(null); setRuleForm({ productId: '', warehouseId: warehouses[0]?.id || '', minimumQuantity: '0', targetQuantity: '1', leadTimeDays: '1', preferredSupplierId: '' }); setModal('rule'); }}>+ Nueva regla</button></div>
      <h3 className="subheading">Sugerencias de reposición</h3>
      <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Almacén</th><th>Stock</th><th>Mínimo</th><th>Objetivo</th><th>Sugerido</th></tr></thead><tbody>
        {suggestions.map((item, index) => <tr key={idOf(item) || index}><td>{item.productName || item.product?.name || item.sku || item.productId || '—'}</td><td>{item.warehouseName || item.warehouse?.name || '—'}</td><td>{item.currentQuantity ?? item.onHand ?? item.available ?? '—'}</td><td>{item.minimumQuantity ?? '—'}</td><td>{item.targetQuantity ?? '—'}</td><td><strong>{item.suggestedQuantity ?? item.recommendedQuantity ?? '—'}</strong></td></tr>)}
      </tbody></table>{!suggestions.length && <div className="empty-state">No hay sugerencias disponibles.</div>}</div>
      <h3 className="subheading">Reglas configuradas</h3>
      <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Almacén</th><th>Mínimo</th><th>Objetivo</th><th>Plazo (días)</th><th>Proveedor sugerido</th><th></th></tr></thead><tbody>
        {rules.map((rule, index) => <tr key={idOf(rule) || index}><td>{rule.productName || rule.product?.name || rule.productId || '—'}</td><td>{rule.warehouseName || rule.warehouse?.name || '—'}</td><td>{rule.minimumQuantity}</td><td>{rule.targetQuantity}</td><td>{rule.leadTimeDays}</td><td>{rule.preferredSupplierName || rule.preferredSupplier?.name || '—'}</td><td><div className="row-actions"><button className="small-action" disabled={loading || rule.active === false} onClick={() => { setRuleEditing(rule); setRuleForm({ productId: rule.productId || rule.product?.id || '', warehouseId: rule.warehouseId || rule.warehouse?.id || '', minimumQuantity: String(rule.minimumQuantity ?? 0), targetQuantity: String(rule.targetQuantity ?? 1), leadTimeDays: String(rule.leadTimeDays ?? 1), preferredSupplierId: rule.preferredSupplierId || rule.preferredSupplier?.id || '' }); setModal('rule'); }}>Editar</button><button className="small-action" disabled={loading || rule.active === false} onClick={() => void operate(() => api.deactivateReorderRule(idOf(rule)), 'Regla de reposición desactivada.')}>Desactivar</button></div></td></tr>)}
      </tbody></table>{!rules.length && <div className="empty-state">No hay reglas de reposición configuradas.</div>}</div>
    </>}

    {modal && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setModal(''); }}>
      <section className="modal-card"><div className="modal-heading"><h2>{modal === 'transfer' ? 'Crear traslado entre almacenes' : modal === 'count' ? 'Registrar conteo físico' : modal === 'rule' ? (ruleEditing ? 'Editar regla de reposición' : 'Configurar regla de reposición') : 'Registrar ajuste de stock'}</h2><button className="icon-button" aria-label="Cerrar" onClick={() => setModal('')}>×</button></div>
        {modal === 'transfer' && <form className="form-grid" onSubmit={submitTransfer}>
          <label className="field"><span>Almacén de origen<b> *</b></span><select required value={transferForm.sourceWarehouseId} onChange={event => setTransferForm(current => ({ ...current, sourceWarehouseId: event.target.value }))}><option value="">Seleccionar origen</option>{warehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label>
          <label className="field"><span>Almacén de destino<b> *</b></span><select required value={transferForm.destinationWarehouseId} onChange={event => setTransferForm(current => ({ ...current, destinationWarehouseId: event.target.value }))}><option value="">Seleccionar destino</option>{warehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label>
          <div className="form-subheading wide"><strong>Líneas del traslado</strong></div>
          {transferForm.items.map((line, index) => <section className="line-editor wide" key={index}>
            <div className="line-editor-heading"><strong>Producto {index + 1}</strong>{transferForm.items.length > 1 && <button type="button" className="text-button danger-text" onClick={() => removeLine('transfer', index)}>Quitar</button>}</div>
            <div className="form-grid compact-grid"><label className="field wide"><span>Producto<b> *</b></span><select required value={line.productId} onChange={event => updateLine('transfer', index, 'productId', event.target.value)}><option value="">Seleccionar producto</option>{products.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}</select></label><label className="field"><span>Cantidad<b> *</b></span><input type="number" required min="0.001" step="0.001" value={line.quantity} onChange={event => updateLine('transfer', index, 'quantity', event.target.value)} /></label></div>
          </section>)}
          <div className="wide"><button className="button secondary" type="button" onClick={() => addLine('transfer')}>+ Añadir producto</button></div>
          <div className="form-actions wide"><button type="button" className="button secondary" onClick={() => setModal('')}>Cancelar</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : 'Crear traslado'}</button></div>
        </form>}
        {modal === 'count' && <form className="form-grid" onSubmit={submitCount}>
          <label className="field wide"><span>Almacén del conteo<b> *</b></span><select required value={countForm.warehouseId} onChange={event => setCountForm(current => ({ ...current, warehouseId: event.target.value }))}><option value="">Seleccionar almacén</option>{warehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label>
          <div className="form-subheading wide"><strong>Productos contados</strong></div>
          {countForm.items.map((line, index) => <section className="line-editor wide" key={index}>
            <div className="line-editor-heading"><strong>Producto {index + 1}</strong>{countForm.items.length > 1 && <button type="button" className="text-button danger-text" onClick={() => removeLine('count', index)}>Quitar</button>}</div>
            <div className="form-grid compact-grid"><label className="field wide"><span>Producto<b> *</b></span><select required value={line.productId} onChange={event => updateLine('count', index, 'productId', event.target.value)}><option value="">Seleccionar producto</option>{products.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}</select></label><label className="field"><span>Cantidad física<b> *</b></span><input type="number" required min="0" step="0.001" value={line.quantity} onChange={event => updateLine('count', index, 'quantity', event.target.value)} /></label></div>
          </section>)}
          <div className="wide"><button className="button secondary" type="button" onClick={() => addLine('count')}>+ Añadir producto</button></div>
          <p className="form-hint wide">El conteo se crea como documento pendiente. Contabilizar aplica el ajuste de acuerdo con el backend.</p>
          <div className="form-actions wide"><button type="button" className="button secondary" onClick={() => setModal('')}>Cancelar</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : 'Crear conteo'}</button></div>
        </form>}
        {modal === 'rule' && <form className="form-grid" onSubmit={submitRule}>
          <label className="field wide"><span>Producto<b> *</b></span><select required value={ruleForm.productId} onChange={event => setRuleForm(current => ({ ...current, productId: event.target.value }))}><option value="">Seleccionar producto</option>{products.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}</select></label>
          <label className="field"><span>Almacén<b> *</b></span><select required value={ruleForm.warehouseId} onChange={event => setRuleForm(current => ({ ...current, warehouseId: event.target.value }))}><option value="">Seleccionar almacén</option>{warehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label>
          <label className="field"><span>Proveedor preferido</span><select value={ruleForm.preferredSupplierId} onChange={event => setRuleForm(current => ({ ...current, preferredSupplierId: event.target.value }))}><option value="">Sin preferencia</option>{suppliers.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label>
          <label className="field"><span>Cantidad mínima<b> *</b></span><input type="number" required min="0" step="0.001" value={ruleForm.minimumQuantity} onChange={event => setRuleForm(current => ({ ...current, minimumQuantity: event.target.value }))} /></label>
          <label className="field"><span>Cantidad objetivo<b> *</b></span><input type="number" required min="0.001" step="0.001" value={ruleForm.targetQuantity} onChange={event => setRuleForm(current => ({ ...current, targetQuantity: event.target.value }))} /></label>
          <label className="field"><span>Plazo de suministro (días)<b> *</b></span><input type="number" required min="0" step="1" value={ruleForm.leadTimeDays} onChange={event => setRuleForm(current => ({ ...current, leadTimeDays: event.target.value }))} /></label>
          <div className="form-actions wide"><button type="button" className="button secondary" onClick={() => setModal('')}>Cancelar</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : ruleEditing ? 'Actualizar regla' : 'Guardar regla'}</button></div>
        </form>}
        {modal === 'adjustment' && <form className="form-grid" onSubmit={submitAdjustment}>
          <label className="field"><span>Almacén<b> *</b></span><select required value={adjustmentForm.warehouseId} onChange={event => setAdjustmentForm(current => ({ ...current, warehouseId: event.target.value }))}><option value="">Seleccionar almacén</option>{warehouses.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label>
          <label className="field"><span>Producto<b> *</b></span><select required value={adjustmentForm.productId} onChange={event => setAdjustmentForm(current => ({ ...current, productId: event.target.value }))}><option value="">Seleccionar producto</option>{products.map(item => <option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku || item.code || idOf(item)}</option>)}</select></label>
          <label className="field"><span>Dirección<b> *</b></span><select required value={adjustmentForm.direction} onChange={event => setAdjustmentForm(current => ({ ...current, direction: event.target.value }))}><option value="INCREASE">Entrada / aumentar</option><option value="DECREASE">Salida / disminuir</option></select></label>
          <label className="field"><span>Cantidad<b> *</b></span><input type="number" required min="0.001" step="0.001" value={adjustmentForm.quantity} onChange={event => setAdjustmentForm(current => ({ ...current, quantity: event.target.value }))} /></label>
          {adjustmentForm.direction === 'INCREASE' && <label className="field"><span>Costo unitario<b> *</b></span><input type="number" required min="0" step="0.0001" value={adjustmentForm.unitCost} onChange={event => setAdjustmentForm(current => ({ ...current, unitCost: event.target.value }))} /></label>}
          <label className="field wide"><span>Motivo del ajuste<b> *</b></span><textarea rows="3" required maxLength="500" value={adjustmentForm.reason} onChange={event => setAdjustmentForm(current => ({ ...current, reason: event.target.value }))} placeholder="Describe la razón de la entrada o salida" /></label>
          <p className="form-hint wide">La API confirma el ajuste de manera transaccional e idempotente; no se editan saldos manualmente en el navegador.</p>
          <div className="form-actions wide"><button type="button" className="button secondary" onClick={() => setModal('')}>Cancelar</button><button className="button primary" disabled={loading}>{loading ? 'Guardando…' : 'Registrar ajuste'}</button></div>
        </form>}
      </section>
    </div>}
    {reasonDialog && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setReasonDialog(null); }}>
      <section className="modal-card reason-modal">
        <div className="modal-heading"><h2>{reasonDialog.kind === 'cancel' ? 'Cancelar traslado' : 'Revertir traslado'}</h2><button className="icon-button" aria-label="Cerrar" onClick={() => setReasonDialog(null)}>×</button></div>
        <form className="form-grid" onSubmit={async event => {
          event.preventDefault();
          const reason = reasonText.trim();
          if (!reason) { setError('Debes indicar el motivo.'); return; }
          const action = reasonDialog.kind === 'cancel'
            ? () => api.cancelInventoryTransfer(reasonDialog.id, reason)
            : () => api.reverseInventoryTransfer(reasonDialog.id, reason);
          const completed = await operate(action, reasonDialog.kind === 'cancel' ? 'Traslado cancelado.' : 'Reversión del traslado procesada.');
          if (completed) { setReasonDialog(null); setReasonText(''); }
        }}>
          <p className="form-hint wide">Documento: <strong>{reasonDialog.label}</strong>. El motivo queda registrado en la operación del backend.</p>
          <label className="field wide"><span>Motivo<b> *</b></span><textarea rows="3" required maxLength="500" value={reasonText} onChange={event => setReasonText(event.target.value)} placeholder="Describe el motivo de esta operación" /></label>
          <div className="form-actions wide"><button type="button" className="button secondary" disabled={loading} onClick={() => setReasonDialog(null)}>Cancelar</button><button className="button primary" disabled={loading || !reasonText.trim()}>{loading ? 'Procesando…' : 'Confirmar'}</button></div>
        </form>
      </section>
    </div>}
  </section>;
}
