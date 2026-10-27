import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, ClipboardCheck, PackagePlus, Warehouse, Wrench } from 'lucide-react';
import { api, listFrom, listAllPages } from '../../api';
import useAccessibleDialog from '../../hooks/useAccessibleDialog.js';
import StepProgress from '../../components/StepProgress.jsx';

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
  const [workflowStep, setWorkflowStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  const [workflowDirty, setWorkflowDirty] = useState(false);
  const [ruleEditing, setRuleEditing] = useState(null);
  const [reasonDialog, setReasonDialog] = useState(null);
  const [reasonText, setReasonText] = useState('');
  const reasonDialogRef = useAccessibleDialog(Boolean(reasonDialog), () => setReasonDialog(null));
  const [transferForm, setTransferForm] = useState({ sourceWarehouseId: '', destinationWarehouseId: '', items: [emptyLine()] });
  const [countForm, setCountForm] = useState({ warehouseId: '', items: [emptyLine()] });
  const [ruleForm, setRuleForm] = useState({ productId: '', warehouseId: '', minimumQuantity: '0', targetQuantity: '1', leadTimeDays: '1', preferredSupplierId: '' });
  const [adjustmentForm, setAdjustmentForm] = useState({ productId: '', warehouseId: warehouses[0]?.id || '', direction: 'INCREASE', quantity: '1', unitCost: '0', reason: '' });

  const refresh = useCallback(async () => {
    const results = await Promise.allSettled([
      listAllPages(api.inventoryTransfers, { size: 100 }),
      listAllPages(api.inventoryCounts, { size: 100 }),
      listAllPages(api.reorderRules),
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
    setValidationMessage('');
  }

  function startOperation(kind) {
    setError('');
    setNotice('');
    setValidationMessage('');
    setWorkflowStep(0);
    setWorkflowDirty(false);
    setModal(kind);
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function closeOperation() {
    if (loading) return;
    if (workflowDirty && !window.confirm('¿Salir de este formulario? Se perderán los cambios que no hayas guardado.')) return;
    setModal('');
    setWorkflowStep(0);
    setWorkflowDirty(false);
    setValidationMessage('');
    setRuleEditing(null);
    setError('');
  }

  function addLine(formKind) {
    const setter = formKind === 'transfer' ? setTransferForm : setCountForm;
    setter(current => ({ ...current, items: [...current.items, emptyLine()] }));
    setWorkflowDirty(true);
  }

  function removeLine(formKind, index) {
    const setter = formKind === 'transfer' ? setTransferForm : setCountForm;
    setter(current => ({ ...current, items: current.items.filter((_, lineIndex) => lineIndex !== index) }));
    setWorkflowDirty(true);
  }

  async function operate(action, success) {
    setLoading(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      setModal('');
      setWorkflowStep(0);
      setWorkflowDirty(false);
      setValidationMessage('');
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
    setValidationMessage('');
    if (workflowStep === 0) {
      if (!transferForm.sourceWarehouseId || !transferForm.destinationWarehouseId) {
        setValidationMessage('Selecciona el almacén de origen y el de destino.');
        return;
      }
      if (transferForm.sourceWarehouseId === transferForm.destinationWarehouseId) {
        setValidationMessage('El almacén de origen y el de destino deben ser diferentes.');
        return;
      }
      setWorkflowStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    const items = transferForm.items.map(line => ({ productId: line.productId, quantity: Number(line.quantity) }));
    const hasDuplicateProducts = new Set(items.map(item => item.productId)).size !== items.length;
    if (!items.length || hasDuplicateProducts || items.some(item => !item.productId || !Number.isFinite(item.quantity) || item.quantity <= 0)) {
      setValidationMessage(hasDuplicateProducts ? 'Cada producto debe aparecer una sola vez en el traslado.' : 'Añade productos con cantidades mayores que cero.');
      return;
    }
    if (workflowStep === 1) {
      setWorkflowStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
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
    setValidationMessage('');
    if (workflowStep === 0) {
      if (!countForm.warehouseId) {
        setValidationMessage('Selecciona el almacén donde se hizo el conteo.');
        return;
      }
      setWorkflowStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    const items = countForm.items.map(line => ({ productId: line.productId, countedQuantity: Number(line.quantity) }));
    const hasDuplicateProducts = new Set(items.map(item => item.productId)).size !== items.length;
    if (!items.length || hasDuplicateProducts || items.some(item => !item.productId || !Number.isFinite(item.countedQuantity) || item.countedQuantity < 0)) {
      setValidationMessage(hasDuplicateProducts ? 'Cada producto debe aparecer una sola vez en el conteo físico.' : 'Completa cada producto con una cantidad contada igual o mayor que cero.');
      return;
    }
    if (workflowStep === 1) {
      setWorkflowStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    void operate(() => api.createInventoryCount({ warehouseId: countForm.warehouseId, items }), 'Conteo creado. Revisa las cantidades antes de contabilizar los ajustes.');
  }

  function submitRule(event) {
    event.preventDefault();
    setValidationMessage('');
    if (workflowStep === 0) {
      if (!ruleForm.productId || !ruleForm.warehouseId) {
        setValidationMessage('Selecciona un producto y un almacén para esta regla.');
        return;
      }
      setWorkflowStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    const minimumQuantity = Number(ruleForm.minimumQuantity);
    const targetQuantity = Number(ruleForm.targetQuantity);
    const leadTimeDays = Number(ruleForm.leadTimeDays);
    if (minimumQuantity < 0 || targetQuantity <= minimumQuantity || leadTimeDays < 0 ||
        !Number.isFinite(minimumQuantity) || !Number.isFinite(targetQuantity) || !Number.isFinite(leadTimeDays)) {
      setValidationMessage('El objetivo debe ser mayor que el mínimo. El mínimo y el plazo no pueden ser negativos.');
      return;
    }
    if (workflowStep === 1) {
      setWorkflowStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    const data = { productId: ruleForm.productId, warehouseId: ruleForm.warehouseId, minimumQuantity, targetQuantity, leadTimeDays };
    if (ruleForm.preferredSupplierId) data.preferredSupplierId = ruleForm.preferredSupplierId;
    else data.preferredSupplierId = null;
    void operate(() => ruleEditing ? api.updateReorderRule(idOf(ruleEditing), data) : api.createReorderRule(data), ruleEditing ? 'Regla de reposición actualizada.' : 'Regla de reposición creada.');
  }

  function submitAdjustment(event) {
    event.preventDefault();
    setValidationMessage('');
    if (workflowStep === 0) {
      if (!adjustmentForm.productId || !adjustmentForm.warehouseId || !adjustmentForm.direction) {
        setValidationMessage('Selecciona el almacén, el producto y el tipo de ajuste.');
        return;
      }
      setWorkflowStep(1);
      window.scrollTo({ top: 0, behavior: 'auto' });
      return;
    }
    const quantity = Number(adjustmentForm.quantity);
    const unitCost = Number(adjustmentForm.unitCost);
    if (!adjustmentForm.reason.trim() || !Number.isFinite(quantity) || quantity <= 0 ||
        (adjustmentForm.direction === 'INCREASE' && (!Number.isFinite(unitCost) || unitCost < 0))) {
      setValidationMessage('Indica un motivo y una cantidad positiva. Para entradas, el costo unitario debe ser válido.');
      return;
    }
    if (workflowStep === 1) {
      setWorkflowStep(2);
      window.scrollTo({ top: 0, behavior: 'auto' });
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
    {error && !modal && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}
    {notice && <div className="alert success" role="status" aria-live="polite"><span>{notice}</span><button type="button" aria-label="Cerrar notificación" onClick={() => setNotice('')}>×</button></div>}
    {!modal && <>
    <div className="catalog-tabs" role="tablist" aria-label="Operaciones de inventario">
      <button className={tab === 'transfers' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'transfers'} onClick={() => setTab('transfers')}>Traslados <span>{transfers.length}</span></button>
      <button className={tab === 'counts' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'counts'} onClick={() => setTab('counts')}>Conteos físicos <span>{counts.length}</span></button>
      <button className={tab === 'reorder' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'reorder'} onClick={() => setTab('reorder')}>Reposición <span>{suggestions.length}</span></button>
      <button className={tab === 'adjustments' ? 'catalog-tab active' : 'catalog-tab'} role="tab" aria-selected={tab === 'adjustments'} onClick={() => setTab('adjustments')}>Ajustes</button>
    </div>

    {tab === 'transfers' && <>
      <div className="toolbar"><p className="muted">Un traslado reduce el origen al despacharse y aumenta el destino al recibirse.</p><button className="button primary" onClick={() => { setTransferForm({ sourceWarehouseId: warehouses[0]?.id || '', destinationWarehouseId: warehouses[1]?.id || '', items: [emptyLine()] }); startOperation('transfer'); }}>+ Nuevo traslado</button></div>
      <div className="table-wrap"><table><thead><tr><th>Documento</th><th>Origen</th><th>Destino</th><th>Estado</th><th>Fecha</th><th>Acciones</th></tr></thead><tbody>
        {transfers.map((transfer, index) => {
          const status = transferStatus(transfer);
          return <tr key={idOf(transfer) || index}>
            <td><strong>{transfer.transferNumber || transfer.number || transfer.code || String(idOf(transfer)).slice(0, 10)}</strong></td>
            <td>{transfer.sourceWarehouseCode || transfer.sourceWarehouseName || transfer.sourceWarehouse?.name || '—'}</td><td>{transfer.destinationWarehouseCode || transfer.destinationWarehouseName || transfer.destinationWarehouse?.name || '—'}</td>
            <td><span className={'pill ' + (['RECEIVED','COMPLETED','POSTED'].includes(status) ? 'active' : ['CANCELLED','REVERSED'].includes(status) ? 'inactive' : 'pending')}>{titleCase(status)}</span></td>
            <td>{date(transfer.createdAt)}</td>
            <td><div className="row-actions">
              {['DRAFT','PENDING'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.dispatchInventoryTransfer(idOf(transfer)), 'Traslado despachado.')}>Despachar</button>}
              {['DISPATCHED','IN_TRANSIT'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.receiveInventoryTransfer(idOf(transfer)), 'Traslado recibido en el almacén destino.')}>Recibir</button>}
              {['DRAFT','PENDING','DISPATCHED','IN_TRANSIT'].includes(status) && <button className="small-action" disabled={loading} onClick={() => { setReasonText(''); setReasonDialog({ kind: 'cancel', id: idOf(transfer), label: transfer.transferNumber || transfer.number || transfer.code || idOf(transfer) }); }}>Cancelar</button>}
              {['RECEIVED','COMPLETED'].includes(status) && <button className="small-action" disabled={loading} onClick={() => { setReasonText(''); setReasonDialog({ kind: 'reverse', id: idOf(transfer), label: transfer.transferNumber || transfer.number || transfer.code || idOf(transfer) }); }}>Revertir</button>}
            </div></td>
          </tr>;
        })}
      </tbody></table>{!transfers.length && <div className="empty-state">{loading ? 'Cargando traslados…' : 'No hay traslados registrados.'}</div>}</div>
    </>}

    {tab === 'counts' && <>
      <div className="toolbar"><p className="muted">Registra lo contado físicamente; el sistema conserva el ajuste para contabilizarlo después.</p><button className="button primary" onClick={() => { setCountForm({ warehouseId: warehouses[0]?.id || '', items: [emptyLine()] }); startOperation('count'); }}>+ Nuevo conteo</button></div>
      <div className="table-wrap"><table><thead><tr><th>Documento</th><th>Almacén</th><th>Estado</th><th>Fecha</th><th>Acciones</th></tr></thead><tbody>
        {counts.map((count, index) => {
          const status = statusOf(count);
          return <tr key={idOf(count) || index}><td><strong>{count.countNumber || count.number || count.code || String(idOf(count)).slice(0, 10)}</strong></td><td>{count.warehouseCode || count.warehouseName || count.warehouse?.name || '—'}</td><td><span className={'pill ' + (['POSTED','COMPLETED'].includes(status) ? 'active' : status === 'CANCELLED' ? 'inactive' : 'pending')}>{titleCase(status)}</span></td><td>{date(count.createdAt)}</td><td><div className="row-actions">
            {['DRAFT','PENDING','OPEN'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.postInventoryCount(idOf(count)), 'Conteo contabilizado. Los ajustes físicos se reflejan en los saldos.')}>Contabilizar</button>}
            {['DRAFT','PENDING','OPEN'].includes(status) && <button className="small-action" disabled={loading} onClick={() => void operate(() => api.cancelInventoryCount(idOf(count)), 'Conteo cancelado.')}>Cancelar</button>}
          </div></td></tr>;
        })}
      </tbody></table>{!counts.length && <div className="empty-state">{loading ? 'Cargando conteos…' : 'No hay conteos físicos registrados.'}</div>}</div>
    </>}

    {tab === 'adjustments' && <>
      <div className="toolbar"><p className="muted">Registra entradas o salidas justificadas. Todos los cambios quedan auditados por el backend.</p><button className="button primary" onClick={() => { setAdjustmentForm({ productId: '', warehouseId: warehouses[0]?.id || '', direction: 'INCREASE', quantity: '1', unitCost: '0', reason: '' }); startOperation('adjustment'); }}>+ Nuevo ajuste</button></div>
      <div className="form-hint wide">Usa este flujo para diferencias justificadas de inventario. Para movimientos entre ubicaciones utiliza la pestaña Traslados.</div>
    </>}

    {tab === 'reorder' && <>
      <div className="toolbar"><p className="muted">Define mínimos y objetivos para recibir sugerencias cuando el stock sea bajo.</p><button className="button primary" onClick={() => { setRuleEditing(null); setRuleForm({ productId: '', warehouseId: warehouses[0]?.id || '', minimumQuantity: '0', targetQuantity: '1', leadTimeDays: '1', preferredSupplierId: '' }); startOperation('rule'); }}>+ Nueva regla</button></div>
      <h3 className="subheading">Sugerencias de reposición</h3>
      <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Almacén</th><th>Stock</th><th>Mínimo</th><th>Objetivo</th><th>Sugerido</th></tr></thead><tbody>
        {suggestions.map((item, index) => <tr key={idOf(item) || index}><td>{item.productName || item.product?.name || item.sku || item.productId || '—'}</td><td>{item.warehouseCode || item.warehouseName || item.warehouse?.name || '—'}</td><td>{item.currentQuantity ?? item.onHand ?? item.available ?? '—'}</td><td>{item.minimumQuantity ?? '—'}</td><td>{item.targetQuantity ?? '—'}</td><td><strong>{item.suggestedQuantity ?? item.recommendedQuantity ?? '—'}</strong></td></tr>)}
      </tbody></table>{!suggestions.length && <div className="empty-state">No hay sugerencias disponibles.</div>}</div>
      <h3 className="subheading">Reglas configuradas</h3>
      <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Almacén</th><th>Mínimo</th><th>Objetivo</th><th>Plazo (días)</th><th>Proveedor sugerido</th><th></th></tr></thead><tbody>
        {rules.map((rule, index) => <tr key={idOf(rule) || index}><td>{rule.productName || rule.product?.name || rule.productId || '—'}</td><td>{rule.warehouseCode || rule.warehouseName || rule.warehouse?.name || '—'}</td><td>{rule.minimumQuantity}</td><td>{rule.targetQuantity}</td><td>{rule.leadTimeDays}</td><td>{rule.preferredSupplierName || rule.preferredSupplier?.name || '—'}</td><td><div className="row-actions"><button className="small-action" disabled={loading || rule.active === false} onClick={() => { setRuleEditing(rule); setRuleForm({ productId: rule.productId || rule.product?.id || '', warehouseId: rule.warehouseId || rule.warehouse?.id || '', minimumQuantity: String(rule.minimumQuantity ?? 0), targetQuantity: String(rule.targetQuantity ?? 1), leadTimeDays: String(rule.leadTimeDays ?? 1), preferredSupplierId: rule.preferredSupplierId || rule.preferredSupplier?.id || '' }); startOperation('rule'); }}>Editar</button><button className="small-action" disabled={loading || rule.active === false} onClick={() => void operate(() => api.deactivateReorderRule(idOf(rule)), 'Regla de reposición desactivada.')}>Desactivar</button></div></td></tr>)}
      </tbody></table>{!rules.length && <div className="empty-state">No hay reglas de reposición configuradas.</div>}</div>
    </>}

    </>}
    {modal && <section className="inventory-workspace" aria-labelledby="inventory-workflow-title">
      <header className="workflow-page-heading">
        <div className="workflow-page-copy">
          <span className="eyebrow">OPERACIÓN DE INVENTARIO</span>
          <h2 id="inventory-workflow-title">{modal === 'transfer' ? 'Nuevo traslado' : modal === 'count' ? 'Nuevo conteo físico' : modal === 'rule' ? (ruleEditing ? 'Editar regla de reposición' : 'Nueva regla de reposición') : 'Nuevo ajuste de stock'}</h2>
          <p>{modal === 'transfer' ? 'Mueve productos entre dos almacenes.' : modal === 'count' ? 'Compara las existencias registradas con el conteo físico.' : modal === 'rule' ? 'Define cuándo necesitas reponer un producto.' : 'Registra una entrada o salida extraordinaria y deja el motivo.'}</p>
        </div>
        <button type="button" className="button secondary" disabled={loading} onClick={closeOperation}>Salir del formulario</button>
      </header>
      <StepProgress
        steps={modal === 'transfer' || modal === 'count' ? ['Ubicación', 'Productos', 'Revisar'] : modal === 'rule' ? ['Producto', 'Límites', 'Confirmar'] : ['Artículo', 'Detalles', 'Confirmar']}
        activeStep={workflowStep}
        onSelect={index => { setValidationMessage(''); setWorkflowStep(index); }}
      />
      {validationMessage && <div className="alert error workflow-validation" role="alert"><span>{validationMessage}</span></div>}
      {error && <div className="alert error" role="alert" aria-live="assertive"><span>{error}</span><button type="button" aria-label="Cerrar error" onClick={() => setError('')}>×</button></div>}

      {modal === 'transfer' && <form className="workflow-form" onSubmit={submitTransfer} onChange={() => setWorkflowDirty(true)} noValidate>
        {workflowStep === 0 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Warehouse size={20}/></span><div><h2>¿De dónde sale y a dónde va?</h2><p>El origen y el destino deben ser almacenes diferentes.</p></div></div>
          <div className="form-grid workflow-fields">
            <label className="field"><span>Almacén de origen<b> *</b></span><select value={transferForm.sourceWarehouseId} onChange={event => { setTransferForm(current => ({...current,sourceWarehouseId:event.target.value})); setValidationMessage(''); }}><option value="">Seleccionar origen</option>{warehouses.filter(item=>item.active!==false&&item.status!=='INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}{item.code ? ' · '+item.code : ''}</option>)}</select></label>
            <label className="field"><span>Almacén de destino<b> *</b></span><select value={transferForm.destinationWarehouseId} onChange={event => { setTransferForm(current => ({...current,destinationWarehouseId:event.target.value})); setValidationMessage(''); }}><option value="">Seleccionar destino</option>{warehouses.filter(item=>item.active!==false&&item.status!=='INACTIVE').map(item => <option key={idOf(item)} value={idOf(item)}>{item.name}{item.code ? ' · '+item.code : ''}</option>)}</select></label>
          </div>
        </section>}
        {workflowStep === 1 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><PackagePlus size={20}/></span><div><h2>Añade los productos</h2><p>Una línea por producto y una cantidad mayor que cero.</p></div></div>
          <div className="workflow-line-list">{transferForm.items.map((line,index)=><section className="line-editor workflow-line" key={index}>
            <div className="line-editor-heading"><strong>Producto {index+1}</strong>{transferForm.items.length>1&&<button type="button" className="text-button danger-text" disabled={loading} onClick={()=>removeLine('transfer',index)}>Quitar</button>}</div>
            <div className="form-grid compact-grid">
              <label className="field wide"><span>Producto<b> *</b></span><select value={line.productId} onChange={event=>updateLine('transfer',index,'productId',event.target.value)}><option value="">Seleccionar producto</option>{products.filter(item=>item.active!==false&&item.status!=='INACTIVE'&&(item.kind||'STOCKED_GOOD')==='STOCKED_GOOD'&&!transferForm.items.some((other,i)=>i!==index&&String(other.productId)===String(idOf(item)))).map(item=><option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku||item.code||idOf(item)}</option>)}</select></label>
              <label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" inputMode="decimal" value={line.quantity} onChange={event=>updateLine('transfer',index,'quantity',event.target.value)}/></label>
            </div>
          </section>)}</div>
          <div className="workflow-inline-actions"><button type="button" className="button secondary" disabled={loading||transferForm.items.length>=100||!products.some(item=>item.active!==false&&item.status!=='INACTIVE'&&(item.kind||'STOCKED_GOOD')==='STOCKED_GOOD'&&!transferForm.items.some(line=>String(line.productId)===String(idOf(item))))} onClick={()=>addLine('transfer')}><PackagePlus size={16}/> Añadir producto</button><span className="muted">{transferForm.items.length} líneas</span></div>
        </section>}
        {workflowStep === 2 && <section className="workflow-stage">
          <div className="workflow-stage-heading"><span className="workflow-stage-icon"><ClipboardCheck size={20}/></span><div><h2>Revisa el traslado</h2><p>El documento se creará como borrador. Después podrás despacharlo.</p></div></div>
          <div className="workflow-review-grid"><div className="workflow-review-card"><small>Origen</small><strong>{warehouses.find(item=>String(idOf(item))===String(transferForm.sourceWarehouseId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Destino</small><strong>{warehouses.find(item=>String(idOf(item))===String(transferForm.destinationWarehouseId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Productos</small><strong>{transferForm.items.length}</strong></div></div>
          <div className="workflow-review-lines">{transferForm.items.map((line,index)=><div className="workflow-review-line" key={index}><div><strong>{products.find(item=>String(idOf(item))===String(line.productId))?.name||'Producto sin seleccionar'}</strong><small>{line.quantity} unidades</small></div></div>)}</div>
        </section>}
        <div className="form-actions workflow-actions"><button type="button" className="button secondary" disabled={loading} onClick={closeOperation}>Cancelar</button><div className="workflow-actions-right">{workflowStep>0&&<button type="button" className="button secondary" disabled={loading} onClick={()=>{setValidationMessage('');setWorkflowStep(value=>Math.max(0,value-1));}}><ArrowLeft size={16}/> Atrás</button>}{workflowStep<2?<button type="submit" className="button primary" disabled={loading}>{workflowStep===0?'Continuar':'Revisar traslado'} <ArrowRight size={16}/></button>:<button type="submit" className="button primary" disabled={loading}>{loading?'Creando…':'Crear traslado'}</button>}</div></div>
      </form>}

      {modal === 'count' && <form className="workflow-form" onSubmit={submitCount} onChange={() => setWorkflowDirty(true)} noValidate>
        {workflowStep === 0 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><Warehouse size={20}/></span><div><h2>¿Qué almacén vas a contar?</h2><p>Selecciona dónde se hizo el conteo.</p></div></div><label className="field"><span>Almacén<b> *</b></span><select value={countForm.warehouseId} onChange={event=>{setCountForm(current=>({...current,warehouseId:event.target.value}));setValidationMessage('');}}><option value="">Seleccionar almacén</option>{warehouses.filter(item=>item.active!==false&&item.status!=='INACTIVE').map(item=><option key={idOf(item)} value={idOf(item)}>{item.name}{item.code?' · '+item.code:''}</option>)}</select></label></section>}
        {workflowStep === 1 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><PackagePlus size={20}/></span><div><h2>Introduce las cantidades contadas</h2><p>Usa cero si no quedan unidades de ese producto.</p></div></div>
          <div className="workflow-line-list">{countForm.items.map((line,index)=><section className="line-editor workflow-line" key={index}><div className="line-editor-heading"><strong>Producto {index+1}</strong>{countForm.items.length>1&&<button type="button" className="text-button danger-text" disabled={loading} onClick={()=>removeLine('count',index)}>Quitar</button>}</div><div className="form-grid compact-grid"><label className="field wide"><span>Producto<b> *</b></span><select value={line.productId} onChange={event=>updateLine('count',index,'productId',event.target.value)}><option value="">Seleccionar producto</option>{products.filter(item=>item.active!==false&&item.status!=='INACTIVE'&&(item.kind||'STOCKED_GOOD')==='STOCKED_GOOD'&&!countForm.items.some((other,i)=>i!==index&&String(other.productId)===String(idOf(item)))).map(item=><option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku||item.code||idOf(item)}</option>)}</select></label><label className="field"><span>Cantidad física<b> *</b></span><input type="number" min="0" step="0.001" inputMode="decimal" value={line.quantity} onChange={event=>updateLine('count',index,'quantity',event.target.value)}/></label></div></section>)}</div>
          <button type="button" className="button secondary" disabled={loading||countForm.items.length>=100||!products.some(item=>item.active!==false&&item.status!=='INACTIVE'&&(item.kind||'STOCKED_GOOD')==='STOCKED_GOOD'&&!countForm.items.some(line=>String(line.productId)===String(idOf(item))))} onClick={()=>addLine('count')}><PackagePlus size={16}/> Añadir producto</button>
        </section>}
        {workflowStep === 2 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><ClipboardCheck size={20}/></span><div><h2>Revisa el conteo físico</h2><p>Se creará un documento pendiente. El stock cambia al contabilizarlo.</p></div></div><div className="workflow-review-grid"><div className="workflow-review-card"><small>Almacén</small><strong>{warehouses.find(item=>String(idOf(item))===String(countForm.warehouseId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Productos contados</small><strong>{countForm.items.length}</strong></div></div><div className="workflow-review-lines">{countForm.items.map((line,index)=><div className="workflow-review-line" key={index}><div><strong>{products.find(item=>String(idOf(item))===String(line.productId))?.name||'Producto sin seleccionar'}</strong><small>Cantidad física</small></div><strong>{Number(line.quantity||0).toLocaleString('es-EC')}</strong></div>)}</div></section>}
        <div className="form-actions workflow-actions"><button type="button" className="button secondary" disabled={loading} onClick={closeOperation}>Cancelar</button><div className="workflow-actions-right">{workflowStep>0&&<button type="button" className="button secondary" disabled={loading} onClick={()=>{setValidationMessage('');setWorkflowStep(value=>Math.max(0,value-1));}}><ArrowLeft size={16}/> Atrás</button>}{workflowStep<2?<button type="submit" className="button primary" disabled={loading}>{workflowStep===0?'Continuar':'Revisar conteo'} <ArrowRight size={16}/></button>:<button type="submit" className="button primary" disabled={loading}>{loading?'Guardando…':'Crear conteo'}</button>}</div></div>
      </form>}

      {modal === 'rule' && <form className="workflow-form" onSubmit={submitRule} onChange={() => setWorkflowDirty(true)} noValidate>
        {workflowStep === 0 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><Wrench size={20}/></span><div><h2>¿Qué producto quieres reponer?</h2><p>Asocia esta regla a un producto y un almacén.</p></div></div><div className="form-grid workflow-fields"><label className="field wide"><span>Producto<b> *</b></span><select value={ruleForm.productId} onChange={event=>{setRuleForm(current=>({...current,productId:event.target.value}));setValidationMessage('');}}><option value="">Seleccionar producto</option>{products.filter(item=>item.active!==false&&item.status!=='INACTIVE'&&(item.kind||'STOCKED_GOOD')==='STOCKED_GOOD').map(item=><option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku||item.code||idOf(item)}</option>)}</select></label><label className="field"><span>Almacén<b> *</b></span><select value={ruleForm.warehouseId} onChange={event=>setRuleForm(current=>({...current,warehouseId:event.target.value}))}><option value="">Seleccionar almacén</option>{warehouses.filter(item=>item.active!==false&&item.status!=='INACTIVE').map(item=><option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label><label className="field"><span>Proveedor preferido <small>(opcional)</small></span><select value={ruleForm.preferredSupplierId} onChange={event=>setRuleForm(current=>({...current,preferredSupplierId:event.target.value}))}><option value="">Sin preferencia</option>{suppliers.filter(item=>item.active!==false&&item.status!=='INACTIVE').map(item=><option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label></div></section>}
        {workflowStep === 1 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><Wrench size={20}/></span><div><h2>Define el mínimo y el objetivo</h2><p>El objetivo debe ser mayor que el mínimo.</p></div></div><div className="form-grid workflow-fields"><label className="field"><span>Cantidad mínima<b> *</b></span><input type="number" min="0" step="0.001" inputMode="decimal" value={ruleForm.minimumQuantity} onChange={event=>setRuleForm(current=>({...current,minimumQuantity:event.target.value}))}/></label><label className="field"><span>Cantidad objetivo<b> *</b></span><input type="number" min="0.001" step="0.001" inputMode="decimal" value={ruleForm.targetQuantity} onChange={event=>setRuleForm(current=>({...current,targetQuantity:event.target.value}))}/></label><label className="field"><span>Plazo de suministro (días)<b> *</b></span><input type="number" min="0" step="1" value={ruleForm.leadTimeDays} onChange={event=>setRuleForm(current=>({...current,leadTimeDays:event.target.value}))}/></label></div></section>}
        {workflowStep === 2 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><ClipboardCheck size={20}/></span><div><h2>Confirma la regla</h2><p>Al llegar al mínimo aparecerán sugerencias de reposición.</p></div></div><div className="workflow-review-grid"><div className="workflow-review-card"><small>Producto</small><strong>{products.find(item=>String(idOf(item))===String(ruleForm.productId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Almacén</small><strong>{warehouses.find(item=>String(idOf(item))===String(ruleForm.warehouseId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Mínimo</small><strong>{ruleForm.minimumQuantity}</strong></div><div className="workflow-review-card"><small>Objetivo</small><strong>{ruleForm.targetQuantity}</strong></div><div className="workflow-review-card"><small>Plazo</small><strong>{ruleForm.leadTimeDays} días</strong></div></div></section>}
        <div className="form-actions workflow-actions"><button type="button" className="button secondary" disabled={loading} onClick={closeOperation}>Cancelar</button><div className="workflow-actions-right">{workflowStep>0&&<button type="button" className="button secondary" disabled={loading} onClick={()=>{setValidationMessage('');setWorkflowStep(value=>Math.max(0,value-1));}}><ArrowLeft size={16}/> Atrás</button>}{workflowStep<2?<button type="submit" className="button primary" disabled={loading}>{workflowStep===0?'Continuar':'Revisar regla'} <ArrowRight size={16}/></button>:<button type="submit" className="button primary" disabled={loading}>{loading?'Guardando…':ruleEditing?'Guardar cambios':'Crear regla'}</button>}</div></div>
      </form>}

      {modal === 'adjustment' && <form className="workflow-form" onSubmit={submitAdjustment} onChange={() => setWorkflowDirty(true)} noValidate>
        {workflowStep === 0 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><Wrench size={20}/></span><div><h2>Selecciona qué vas a ajustar</h2><p>Una entrada aumenta existencias y una salida las reduce.</p></div></div><div className="form-grid workflow-fields"><label className="field"><span>Almacén<b> *</b></span><select value={adjustmentForm.warehouseId} onChange={event=>setAdjustmentForm(current=>({...current,warehouseId:event.target.value}))}><option value="">Seleccionar almacén</option>{warehouses.filter(item=>item.active!==false&&item.status!=='INACTIVE').map(item=><option key={idOf(item)} value={idOf(item)}>{item.name}</option>)}</select></label><label className="field"><span>Producto<b> *</b></span><select value={adjustmentForm.productId} onChange={event=>setAdjustmentForm(current=>({...current,productId:event.target.value}))}><option value="">Seleccionar producto</option>{products.filter(item=>item.active!==false&&item.status!=='INACTIVE'&&(item.kind||'STOCKED_GOOD')==='STOCKED_GOOD').map(item=><option key={idOf(item)} value={idOf(item)}>{item.name} · {item.sku||item.code||idOf(item)}</option>)}</select></label><label className="field"><span>Tipo de ajuste<b> *</b></span><select value={adjustmentForm.direction} onChange={event=>setAdjustmentForm(current=>({...current,direction:event.target.value}))}><option value="INCREASE">Entrada / aumentar</option><option value="DECREASE">Salida / disminuir</option></select></label></div></section>}
        {workflowStep === 1 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><Wrench size={20}/></span><div><h2>Indica cantidad y motivo</h2><p>El motivo queda registrado en el historial.</p></div></div><div className="form-grid workflow-fields"><label className="field"><span>Cantidad<b> *</b></span><input type="number" min="0.001" step="0.001" inputMode="decimal" value={adjustmentForm.quantity} onChange={event=>setAdjustmentForm(current=>({...current,quantity:event.target.value}))}/></label>{adjustmentForm.direction==='INCREASE'&&<label className="field"><span>Costo por unidad<b> *</b></span><input type="number" min="0" step="0.0001" inputMode="decimal" value={adjustmentForm.unitCost} onChange={event=>setAdjustmentForm(current=>({...current,unitCost:event.target.value}))}/></label>}<label className="field wide"><span>Motivo<b> *</b></span><textarea rows="3" maxLength="500" value={adjustmentForm.reason} onChange={event=>setAdjustmentForm(current=>({...current,reason:event.target.value}))} placeholder="Ej. Diferencia detectada en conteo físico"/></label></div></section>}
        {workflowStep === 2 && <section className="workflow-stage"><div className="workflow-stage-heading"><span className="workflow-stage-icon"><ClipboardCheck size={20}/></span><div><h2>Confirma el ajuste</h2><p>Comprueba los datos antes de registrar.</p></div></div><div className="workflow-review-grid"><div className="workflow-review-card"><small>Tipo</small><strong>{adjustmentForm.direction==='INCREASE'?'Entrada / aumentar':'Salida / disminuir'}</strong></div><div className="workflow-review-card"><small>Producto</small><strong>{products.find(item=>String(idOf(item))===String(adjustmentForm.productId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Almacén</small><strong>{warehouses.find(item=>String(idOf(item))===String(adjustmentForm.warehouseId))?.name||'—'}</strong></div><div className="workflow-review-card"><small>Cantidad</small><strong>{adjustmentForm.quantity}</strong></div>{adjustmentForm.direction==='INCREASE'&&<div className="workflow-review-card"><small>Costo unitario</small><strong>{new Intl.NumberFormat('es-EC',{style:'currency',currency:'USD'}).format(Number(adjustmentForm.unitCost||0))}</strong></div>}</div><p className="workflow-review-note"><strong>Motivo:</strong> {adjustmentForm.reason.trim()||'—'}</p></section>}
        <div className="form-actions workflow-actions"><button type="button" className="button secondary" disabled={loading} onClick={closeOperation}>Cancelar</button><div className="workflow-actions-right">{workflowStep>0&&<button type="button" className="button secondary" disabled={loading} onClick={()=>{setValidationMessage('');setWorkflowStep(value=>Math.max(0,value-1));}}><ArrowLeft size={16}/> Atrás</button>}{workflowStep<2?<button type="submit" className="button primary" disabled={loading}>{workflowStep===0?'Continuar':'Revisar ajuste'} <ArrowRight size={16}/></button>:<button type="submit" className="button primary" disabled={loading}>{loading?'Guardando…':'Confirmar ajuste'}</button>}</div></div>
      </form>}
    </section>}
    {reasonDialog && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !loading) setReasonDialog(null); }}>
      <section ref={reasonDialogRef} className="modal-card reason-modal" role="dialog" aria-modal="true" aria-labelledby="transfer-reason-modal-title" tabIndex={-1}>
        <div className="modal-heading"><h2 id="transfer-reason-modal-title">{reasonDialog.kind === 'cancel' ? 'Cancelar traslado' : 'Revertir traslado'}</h2><button type="button" className="icon-button" aria-label="Cerrar ventana" onClick={() => setReasonDialog(null)}>×</button></div>
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
