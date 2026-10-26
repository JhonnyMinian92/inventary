import { useEffect, useState } from 'react';
import { api } from '../../api';

function emptyDefinition() {
  return { key: '', displayName: '', valueType: 'STRING', required: false, optionsText: '', maxLength: '' };
}

function normalizeDefinition(definition) {
  return {
    key: definition?.key || '',
    displayName: definition?.displayName || '',
    valueType: definition?.valueType || 'STRING',
    required: Boolean(definition?.required),
    optionsText: Array.isArray(definition?.options) ? definition.options.join('\n') : '',
    maxLength: definition?.constraints?.maxLength == null ? '' : String(definition.constraints.maxLength),
    constraints: definition?.constraints && typeof definition.constraints === 'object' ? { ...definition.constraints } : {}
  };
}

function buildDefinitions(rows) {
  return rows.filter(row => row.key.trim() || row.displayName.trim()).map(row => {
    const definition = {
      key: row.key.trim(),
      displayName: row.displayName.trim(),
      valueType: row.valueType,
      required: Boolean(row.required)
    };
    if (row.valueType === 'ENUM') {
      definition.options = row.optionsText.split(/[\n,]/).map(value => value.trim()).filter(Boolean);
    }
    const constraints = { ...(row.constraints || {}) };
    delete constraints.maxLength;
    if (row.valueType === 'STRING' && row.maxLength !== '') constraints.maxLength = Number(row.maxLength);
    if (Object.keys(constraints).length) definition.constraints = constraints;
    return definition;
  });
}

export default function CategoryForm({ initialValue, loading, onCancel, onSubmit, onError }) {
  const [name, setName] = useState(initialValue?.name || '');
  const [description, setDescription] = useState(initialValue?.description || '');
  const [definitions, setDefinitions] = useState([]);
  const [schemaLoading, setSchemaLoading] = useState(Boolean(initialValue));
  const [schemaLoaded, setSchemaLoaded] = useState(!initialValue);
  const [schemaError, setSchemaError] = useState('');

  useEffect(() => {
    const categoryId = initialValue?.id || initialValue?.categoryId;
    if (!categoryId) {
      setDefinitions([]);
      setSchemaLoading(false);
      setSchemaLoaded(true);
      return undefined;
    }

    let active = true;
    setSchemaLoading(true);
    setSchemaLoaded(false);
    setSchemaError('');
    api.categoryAttributes(categoryId)
      .then(schema => {
        if (!active) return;
        const source = Array.isArray(schema?.definitions) ? schema.definitions : [];
        setDefinitions(source.map(normalizeDefinition));
        setSchemaLoaded(true);
      })
      .catch(error => {
        if (!active) return;
        setSchemaError(error?.message || 'No se pudo leer el esquema actual.');
        onError?.('No se pudo cargar el esquema de la categoría: ' + (error?.message || 'error desconocido'));
      })
      .finally(() => {
        if (active) setSchemaLoading(false);
      });

    return () => { active = false; };
  }, [initialValue?.id, initialValue?.categoryId]);

  function updateDefinition(index, field, value) {
    setDefinitions(current => current.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row));
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (!schemaLoaded) {
      onError?.('Espera a que se cargue el esquema de la categoría antes de guardar.');
      return;
    }
    const rows = definitions.filter(row => row.key.trim() || row.displayName.trim());
    for (const row of rows) {
      if (!row.key.trim() || !row.displayName.trim()) {
        onError?.('Cada propiedad debe tener una clave y un nombre visible.');
        return;
      }
      if (row.valueType === 'ENUM' && !row.optionsText.split(/[\n,]/).some(value => value.trim())) {
        onError?.('Cada propiedad de tipo lista debe incluir al menos una opción.');
        return;
      }
      if (row.maxLength !== '' && (!Number.isInteger(Number(row.maxLength)) || Number(row.maxLength) < 1)) {
        onError?.('La longitud máxima debe ser un entero positivo.');
        return;
      }
    }
    const keys = rows.map(row => row.key.trim().toLowerCase());
    if (new Set(keys).size !== keys.length) {
      onError?.('No puede haber claves de propiedades repetidas.');
      return;
    }

    onSubmit({
      name: name.trim(),
      description: description.trim(),
      definitions: buildDefinitions(rows)
    });
  }

  return <form className="form-grid" onSubmit={handleSubmit}>
    <label className="field">
      <span>Nombre<b> *</b></span>
      <input required maxLength="120" value={name} onChange={event => setName(event.target.value)} />
    </label>
    <label className="field wide">
      <span>Descripción</span>
      <textarea rows="2" maxLength="1000" value={description} onChange={event => setDescription(event.target.value)} />
    </label>

    <div className="form-subheading wide">
      <strong>Propiedades de esta categoría</strong>
      <small>Define los campos que estarán disponibles en todos los productos de la categoría.</small>
    </div>
    {schemaLoading && <p className="form-hint wide">Cargando propiedades existentes…</p>}
    {schemaError && <div className="alert error wide">{schemaError}</div>}
    {!schemaError && definitions.map((definition, index) =>
      <section className="attribute-definition wide" key={index}>
        <div className="attribute-definition-heading">
          <strong>Propiedad {index + 1}</strong>
          <button type="button" className="text-button danger-text" onClick={() => setDefinitions(current => current.filter((_, rowIndex) => rowIndex !== index))}>Eliminar</button>
        </div>
        <div className="form-grid compact-grid">
          <label className="field">
            <span>Clave técnica<b> *</b></span>
            <input required maxLength="80" pattern="[A-Za-z][A-Za-z0-9_-]*" title="Empieza con una letra; usa letras, números, guion o guion bajo." value={definition.key} onChange={event => updateDefinition(index, 'key', event.target.value)} placeholder="Ej. talla" />
          </label>
          <label className="field">
            <span>Nombre visible<b> *</b></span>
            <input required maxLength="120" value={definition.displayName} onChange={event => updateDefinition(index, 'displayName', event.target.value)} placeholder="Ej. Talla" />
          </label>
          <label className="field">
            <span>Tipo de dato</span>
            <select value={definition.valueType} onChange={event => updateDefinition(index, 'valueType', event.target.value)}>
              <option value="STRING">Texto</option>
              <option value="NUMBER">Número</option>
              <option value="BOOLEAN">Sí / No</option>
              <option value="DATE">Fecha</option>
              <option value="ENUM">Lista de opciones</option>
            </select>
          </label>
          <label className="field checkbox-field">
            <span>Obligatorio</span>
            <input type="checkbox" checked={definition.required} onChange={event => updateDefinition(index, 'required', event.target.checked)} />
          </label>
          {definition.valueType === 'ENUM' &&
            <label className="field wide">
              <span>Opciones (una por línea o separadas por coma)<b> *</b></span>
              <textarea rows="3" required value={definition.optionsText} onChange={event => updateDefinition(index, 'optionsText', event.target.value)} placeholder={'Pequeña\nMediana\nGrande'} />
            </label>
          }
          {definition.valueType === 'STRING' &&
            <label className="field">
              <span>Longitud máxima</span>
              <input type="number" min="1" step="1" value={definition.maxLength} onChange={event => updateDefinition(index, 'maxLength', event.target.value)} placeholder="Sin límite" />
            </label>
          }
        </div>
      </section>
    )}
    {!definitions.length && !schemaLoading && !schemaError && <p className="form-hint wide">Esta categoría todavía no tiene propiedades definidas. Puedes guardar sin ellas o añadir la primera propiedad.</p>}
    <div className="wide">
      <button type="button" className="button secondary" disabled={schemaLoading || Boolean(schemaError)} onClick={() => setDefinitions(current => [...current, emptyDefinition()])}>+ Añadir propiedad</button>
    </div>
    <div className="form-actions wide">
      <button type="button" className="button secondary" onClick={onCancel}>Cancelar</button>
      <button className="button primary" disabled={loading || schemaLoading || !schemaLoaded || Boolean(schemaError)}>{loading ? 'Guardando…' : 'Guardar categoría'}</button>
    </div>
    {schemaError && initialValue && <p className="form-hint wide">Cierra y vuelve a abrir el editor para intentar leer el esquema de nuevo antes de modificarlo.</p>}
  </form>;
}
