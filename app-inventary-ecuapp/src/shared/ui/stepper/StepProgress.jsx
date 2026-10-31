import { Check } from 'lucide-react';

export default function StepProgress({ steps, activeStep, onSelect }) {
  return <nav className="workflow-stepper-wrap" aria-label="Pasos del formulario">
    <ol className="workflow-stepper">
      {steps.map((step, index) => {
        const completed = index < activeStep;
        const current = index === activeStep;
        return <li key={step} className={['workflow-step', completed ? 'completed' : '', current ? 'current' : ''].filter(Boolean).join(' ')}>
          <button type="button" className="workflow-step-button" onClick={() => completed && onSelect?.(index)}
            disabled={!completed} aria-current={current ? 'step' : undefined}
            aria-label={'Paso ' + (index + 1) + ': ' + step + (completed ? ', completado; volver a este paso' : current ? ', actual' : ', pendiente')}>
            <span className="workflow-step-number">{completed ? <Check size={15} aria-hidden="true" /> : index + 1}</span>
            <span className="workflow-step-label">{step}</span>
          </button>
          {index < steps.length - 1 && <span className="workflow-step-connector" aria-hidden="true" />}
        </li>;
      })}
    </ol>
  </nav>;
}
