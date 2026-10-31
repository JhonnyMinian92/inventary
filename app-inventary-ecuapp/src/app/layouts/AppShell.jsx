import { BrandMark } from '../../shared/ui/branding/BrandLogo.jsx';
import ecuappLogo from '../../assets/ecuapp-logo.png';
import { LogOut, RefreshCw } from 'lucide-react';
import { isPlainLeftClick, sectionPath } from '../router/index.js';

export default function AppShell({
  navGroups, section, whatsappPending, can, navigateSection, displayName,
  apiStatus, roleLabel, onLogout, isWorkflow, modal, editing,
  workflowTitles, sectionTitles, headerAction, refreshing, loading, onRefresh,
  children, overlays
}) {
  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><BrandMark size={38} className="brand-mark-svg" /><div><strong>inventary</strong><small>CONTROL DE EXISTENCIAS</small></div></div>
      <nav aria-label="Navegación principal" className="side-nav">
        {navGroups.map(group => {
          const items = group.items.filter(item => can(item.permission));
          if (!items.length) return null;
          return <div className="nav-group" key={group.label}>
            <span className="nav-group-label">{group.label}</span>
            {items.map(item => {
              const current = (item.sections || [item.id]).includes(section);
              const badge = item.badge === 'whatsapp' && whatsappPending > 0 ? whatsappPending : 0;
              return <a key={item.id} href={sectionPath(item.id)} title={item.label}
                aria-current={current ? 'page' : undefined} className={current ? 'nav-item active' : 'nav-item'}
                onClick={event => { if (!isPlainLeftClick(event)) return; event.preventDefault(); navigateSection(item.id); }}>
                <item.icon size={18} /><span>{item.label}</span>
                {badge > 0 && <b className="nav-badge" aria-label={badge + ' pendientes'}>{badge > 99 ? '99+' : badge}</b>}
              </a>;
            })}
          </div>;
        })}
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-profile">
          <span className="avatar" aria-hidden="true">{displayName.slice(0, 1).toUpperCase()}</span>
          <span className="profile-copy"><strong>{displayName}</strong><small className={apiStatus === 'error' ? 'offline' : ''}>{apiStatus === 'error' ? 'Sin conexión' : roleLabel}</small></span>
          <button type="button" className="icon-button ghost" onClick={onLogout} aria-label="Cerrar sesión" title="Cerrar sesión"><LogOut size={17} /></button>
        </div>
        <div className="sidebar-legal"><img src={ecuappLogo} alt="" width="18" height="18" /><span>© {new Date().getFullYear()} EcuApp. Todos los derechos reservados.</span></div>
      </div>
    </aside>
    <main className="main-content">
      <header className="topbar">
        <h1>{isWorkflow ? (workflowTitles[modal]?.[editing && workflowTitles[modal][1] ? 1 : 0] || sectionTitles[section]) : sectionTitles[section] || 'Resumen'}</h1>
        <div className="topbar-actions">
          {!isWorkflow && headerAction}
          {!isWorkflow && <button type="button" className={refreshing ? 'icon-button is-loading' : 'icon-button'}
            onClick={onRefresh} disabled={loading} aria-busy={loading} title="Actualizar datos" aria-label="Actualizar datos"><RefreshCw size={17} /></button>}
        </div>
      </header>
      {children}
    </main>
    {overlays}
  </div>;
}
