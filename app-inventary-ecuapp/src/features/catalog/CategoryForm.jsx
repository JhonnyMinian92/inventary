import { useEffect, useState } from 'react';
import { toast, useMessageToast } from '../../ui/feedback.js';
import { ArrowLeft, ArrowRight, Tags } from 'lucide-react';
import { api } from '../../api';
import StepProgress from '../../components/StepProgress.jsx';
import { LIST_TYPES, PropertiesEditor, SuggestionNote, TemplatePicker, fromDefinition, normalizeCatalog, rowsForTemplate, toRequest, useCategorySuggestion, validateRows } from './CategoryProperties.jsx';

export default function CategoryForm({ initialValue, loading, onCancel, onSubmit, onError }) {
  const isNew = !initialValue;
  const [step, setStep] = useState(0);
  const [validationMessage, setValidationMessage] = useState('');
  useMessageToast(validationMessage, setValidationMessage);
  const [name, setName] = useState(initialValue?.name || '');
  const [description, setDescription] = useState(initialValue?.description || '');
  const [templateId, setTemplateId] = useState('');
  // Mientras el usuario no elija ni edite nada, las sugerencias del backend se aplican solas.
  const [manual, setManual] = useState(!isNew);
  const [rows, setRows] = useState([]);
  const [catalog, setCatalog] = useState(null);
  const [schemaLoading, setSchemaLoading] = useState(true);
  const [schemaError, setSchemaError] = useState('');
  useEffect(() => { if (schemaError) toast.error(schemaError); }, [schemaError]);
  const suggestion = useCategorySuggestion(name, isNew);

  const typeLabel = code => catalog?.valueTypes?.find(type => type.code === code)?.label || code;

  useEffect(() => {
    const categoryId = initialValue?.id || initialValue?.categoryId;
    let active = true;
    setSchemaLoading(true);
    setSchemaError('');
    Promise.all([api.attributePresets(), categoryId ? api.categoryAttributes(categoryId) : Promise.resolve(null)])
      .then(([presets, schema]) => {
        if (!active) return;
        setCatalog(normalizeCatalog(presets));
        if (schema) setRows((Array.isArray(schema.definitions) ? schema.definitions : []).map(fromDefinition));
      })
      .catch(error => {
        if (!active) return;
        setSchemaError(error?.message || 'No se pudieron cargar las características.');
        onError?.('No se pudieron cargar las características de la categoría: ' + (error?.message || 'error desconocido'));
      })
      .finally(() => { if (active) setSchemaLoading(false); });
    return () => { active = false; };
  }, [initialValue?.id, initialValue?.categoryId]);

  useEffect(() => {
    if (manual || !catalog || !suggestion?.templateId || suggestion.templateId === templateId) return;
    setTemplateId(suggestion.templateId);
    setRows(rowsForTemplate(catalog.templates.find(item => item.id === suggestion.templateId), catalog.presets));
  }, [suggestion, catalog, manual]);

  function chooseTemplate(template) {
    setManual(true);
    setTemplateId(template.id);
    setRows(rowsForTemplate(template, catalog.presets));
  }

  function copySimilar() {
    setManual(true);
    setTemplateId('');
    setRows((suggestion?.similarDefinitions || []).map(fromDefinition));
    toast.success(`Copiamos las características de ${suggestion.similarCategoryName}.`);
  }

  function goTo(nextStep) {
    setStep(nextStep);
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function handleSubmit(event) {
    event.preventDefault();
    setValidationMessage('');

    if (step === 0) {
      if (!name.trim()) {
        setValidationMessage('Escribe un nombre para la categoría.');
        return;
      }
      goTo(1);
      return;
    }
    if (schemaLoading) {
      setValidationMessage('Espera a que terminen de cargar las características.');
      return;
    }
    if (schemaError) {
      setValidationMessage('No se puede continuar sin cargar las características. Vuelve a abrir la categoría.');
      return;
    }
    const problem = validateRows(rows);
    if (problem) {
      setValidationMessage(problem);
      return;
    }
    if (step === 1) {
      goTo(2);
      return;
    }
    onSubmit({ name: name.trim(), description: description.trim(), definitions: rows.map(toRequest) });
  }

  return <form className="workflow-form" onSubmit={handleSubmit} noValidate>
    <StepProgress steps={['Datos básicos', 'Características', 'Confirmar']} activeStep={step} onSelect={index => { setValidationMessage(''); setStep(index); }} />
    {step === 0 && <section className="workflow-stage" aria-labelledby="category-basic-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Tags size={20}/></span><div><h2 id="category-basic-title">Crea una categoría clara</h2><p>El nombre ayudará a identificar y organizar los productos.</p></div></div>
      <div className="form-grid workflow-fields">
        <label className="field">
          <span>Nombre<b> *</b></span>
          <input required maxLength="120" value={name} onChange={event => setName(event.target.value)} placeholder="Ej. Camisetas, Bebidas, Celulares" />
        </label>
        <label className="field wide">
          <span>Descripción <small>(opcional)</small></span>
          <textarea rows="2" maxLength="1000" value={description} onChange={event => setDescription(event.target.value)} placeholder="Ej. Camisetas de algodón para hombre y mujer" />
        </label>
        {isNew && <div className="field wide">
          <span>¿Qué vendes en esta categoría?</span>
          <small className="field-help">Lo usamos para proponerte qué datos pedir de cada producto (por ejemplo, talla y color en ropa). Lo elegimos solo según el nombre, pero puedes cambiarlo.</small>
          <SuggestionNote suggestion={suggestion} applied={!manual && templateId === suggestion?.templateId} onCopySimilar={copySimilar} />
          {schemaLoading && <p className="form-hint">Cargando tipos de productos…</p>}
          {catalog && <TemplatePicker catalog={catalog} value={templateId} suggestedId={suggestion?.templateId} onChoose={chooseTemplate} />}
        </div>}
      </div>
    </section>}
    {step === 1 && <section className="workflow-stage" aria-labelledby="category-properties-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Tags size={20}/></span><div><h2 id="category-properties-title">¿Qué datos quieres llenar en cada producto?</h2><p>Toca una característica para añadirla o quitarla. Todas son opcionales.</p></div></div>
      {schemaLoading && <p className="form-hint">Cargando características…</p>}
      {!schemaLoading && !schemaError && catalog && <PropertiesEditor catalog={catalog} rows={rows} onChange={next => { setManual(true); setRows(next); }} />}
      {schemaError && <p className="form-hint">Vuelve a abrir la categoría para intentar cargar sus características.</p>}
    </section>}
    {step === 2 && <section className="workflow-stage" aria-labelledby="category-review-title">
      <div className="workflow-stage-heading"><span className="workflow-stage-icon"><Tags size={20}/></span><div><h2 id="category-review-title">Confirma la categoría</h2><p>Revisa la información y las características antes de guardar.</p></div></div>
      <div className="workflow-review-grid">
        <div className="workflow-review-card"><small>Nombre</small><strong>{name.trim() || '—'}</strong></div>
        <div className="workflow-review-card"><small>Características</small><strong>{rows.length}</strong></div>
      </div>
      {description.trim() && <p className="workflow-review-note"><strong>Descripción:</strong> {description.trim()}</p>}
      {rows.length > 0 && <div className="workflow-review-lines">
        {rows.map((row, index) => <div className="workflow-review-line" key={row.key || index}>
          <div><strong>{row.displayName}</strong><small>{typeLabel(row.valueType)}{LIST_TYPES.has(row.valueType) ? ': ' + row.options.join(', ') : ''}</small></div>
          <span className="workflow-review-tag">{row.required ? 'Obligatorio' : 'Opcional'}</span>
        </div>)}
      </div>}
    </section>}
    <div className="form-actions workflow-actions">
      <button type="button" className="button secondary" disabled={loading} onClick={onCancel}>Cancelar</button>
      <div className="workflow-actions-right">
        {step > 0 && <button type="button" className="button secondary" disabled={loading} onClick={() => { setValidationMessage(''); goTo(step - 1); }}><ArrowLeft size={16}/> Atrás</button>}
        {step < 2 ? <button type="submit" className="button primary" disabled={loading || (step === 1 && (schemaLoading || Boolean(schemaError)))}>{step === 0 ? 'Continuar' : 'Revisar'} <ArrowRight size={16}/></button>
          : <button type="submit" className="button primary" disabled={loading || schemaLoading || Boolean(schemaError)}>{loading ? 'Guardando…' : 'Guardar categoría'}</button>}
      </div>
    </div>
  </form>;
}
