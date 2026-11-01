import { useState } from 'react';
import OperationsCatalogs from '../../features/operations/OperationsCatalogs.jsx';
import TaxpayerRegistry from '../../features/partners/TaxpayerRegistry.jsx';

export default function PartnersPage({ onChanged, can = () => false }) {
  const [tab, setTab] = useState('operations');
  const canReadTax = Boolean(can('taxpayer:read'));
  return <section className="panel">
    <div className="catalog-tabs" role="tablist" aria-label="Gestión de datos maestros">
      <button type="button" role="tab" aria-selected={tab === 'operations'} className={tab === 'operations' ? 'catalog-tab active' : 'catalog-tab'} onClick={() => setTab('operations')}>Almacenes y contactos</button>
      {canReadTax && <button type="button" role="tab" aria-selected={tab === 'taxpayer'} className={tab === 'taxpayer' ? 'catalog-tab active' : 'catalog-tab'} onClick={() => setTab('taxpayer')}>Datos tributarios</button>}
    </div>
    {tab === 'operations' ? <OperationsCatalogs can={can} onChanged={onChanged} /> :
      canReadTax ? <TaxpayerRegistry canManage={Boolean(can('taxpayer:manage'))} /> : null}
  </section>;
}
