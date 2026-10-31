import { PurchaseOrderDetail, SaleDetail } from '../../entities/transaction/ui/TransactionDetails.jsx';
import FeedbackHost from '../../shared/ui/feedback/FeedbackHost.jsx';
import Modal from '../../shared/ui/overlays/Modal.jsx';
import Field from '../../shared/ui/forms/Field.jsx';
import FormActions from '../../shared/ui/forms/FormActions.jsx';

export default function WorkspaceOverlays({ model }) {
  const { state, actions, commands } = model;
  return <>
    {state.modal === 'purchaseDetail' && state.purchaseDetail &&
      <Modal title="Orden de compra" busy={state.loading} onClose={actions.closeDetail}>
        <PurchaseOrderDetail order={state.purchaseDetail} />
      </Modal>}
    {state.modal === 'saleDetail' && state.saleDetail &&
      <Modal title="Venta" busy={state.loading} onClose={actions.closeDetail}>
        <SaleDetail sale={state.saleDetail} />
      </Modal>}
    {state.modal === 'cancelSale' && state.saleToCancel &&
      <Modal title="Solicitar anulación" busy={state.loading} onClose={actions.closeSaleCancellation}>
        <form className="form-grid" onSubmit={commands.submitSaleCancellation}>
          <p className="form-hint wide">Venta <strong>{state.saleToCancel.saleNumber || state.saleToCancel.id || state.saleToCancel.saleId}</strong>. Las existencias vuelven al aprobarse.</p>
          <Field label="Motivo" required wide>
            <textarea rows="3" required maxLength="500" value={state.saleCancelReason}
              onChange={event => actions.setSaleCancelReason(event.target.value)} placeholder="Describe el motivo" />
          </Field>
          <FormActions loading={state.loading} onCancel={actions.closeSaleCancellation} submitLabel="Solicitar anulación" />
        </form>
      </Modal>}
    <FeedbackHost />
  </>;
}
