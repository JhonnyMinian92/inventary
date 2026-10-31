import { ArrowLeft } from 'lucide-react';

export default function WorkflowPage({ onCancel, children }) {
  return <section className="workflow-page">
    <div className="workflow-page-heading">
      <button type="button" className="button secondary workflow-exit" onClick={onCancel}>
        <ArrowLeft size={16} /> Volver
      </button>
    </div>
    <div className="workflow-page-body">{children}</div>
  </section>;
}
