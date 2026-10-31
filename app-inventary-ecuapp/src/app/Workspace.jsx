import { Plus } from 'lucide-react';
import LoginPage from '../pages/auth/LoginPage.jsx';
import AppShell from './layouts/AppShell.jsx';
import WorkspaceContent from '../widgets/workspace/WorkspaceContent.jsx';
import WorkspaceOverlays from '../widgets/workspace/WorkspaceOverlays.jsx';
import FeedbackHost from '../shared/ui/feedback/FeedbackHost.jsx';
import useWorkspaceController from './model/useWorkspaceController.js';
import { WORKFLOW_TITLES } from './navigation/navigationConfig.js';

export default function Workspace() {
  const model = useWorkspaceController();
  const { auth, state, data, nav, actions } = model;

  if (!auth.token) return <>
    <LoginPage onLogin={async credentials => {
      await auth.login(credentials);
      model.feedback.clearToasts();
    }} />
    <FeedbackHost />
  </>;

  const headerAction = state.section === 'purchases' && auth.can('purchasing:manage')
    ? <button type="button" className="button primary" onClick={() => actions.startWorkflow('purchase')}><Plus size={16} />Nueva compra</button>
    : state.section === 'sales' && auth.can('sales:manage')
      ? <button type="button" className="button primary" onClick={() => actions.startWorkflow('sale')}><Plus size={16} />Nueva venta</button>
      : null;
  const roleLabel = auth.user?.role ? auth.user.role.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, value => value.toUpperCase()) : 'Sesión activa';

  return <AppShell
    navGroups={nav.groups} section={state.section} whatsappPending={data.whatsappPending} can={auth.can}
    navigateSection={actions.navigateSection} displayName={auth.user?.username || 'Usuario'}
    apiStatus={auth.apiStatus} roleLabel={roleLabel} onLogout={actions.handleLogout}
    isWorkflow={state.isWorkflow} modal={state.modal} editing={state.editing}
    workflowTitles={WORKFLOW_TITLES} sectionTitles={nav.sectionTitles} headerAction={headerAction}
    refreshing={state.refreshing} loading={state.loading} onRefresh={() => void data.loadData()}
    overlays={<WorkspaceOverlays model={model} />}
  >
    <WorkspaceContent model={model} />
  </AppShell>;
}
