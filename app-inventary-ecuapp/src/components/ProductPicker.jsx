import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, Search, X } from 'lucide-react';
import { api, listFrom } from '../api.js';

function idOf(item) { return item?.id ?? item?.productId ?? ''; }

/** Quita tildes y mayúsculas para que "cafe" encuentre "Café". */
function normalize(value) {
  return String(value || '').normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es');
}

/**
 * Searchable product picker.
 * It uses the supplied bounded list for the initial suggestions and the backend for
 * remote search, so products outside the initial reference page remain selectable.
 */
export default function ProductPicker({
  products = [], value, onChange, excludeIds = [], disabled,
  placeholder = 'Escribe el nombre del producto', describe, ariaLabel = 'Producto',
  productKind
}) {
  const listId = useId();
  const rootRef = useRef(null);
  const inputRef = useRef(null);
  const requestVersion = useRef(0);
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [remoteProducts, setRemoteProducts] = useState([]);
  const [selectedLookup, setSelectedLookup] = useState(null);
  const [loading, setLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);

  const safeProducts = Array.isArray(products) ? products : [];
  const selected = safeProducts.find(item => String(idOf(item)) === String(value))
    || remoteProducts.find(item => String(idOf(item)) === String(value))
    || (String(idOf(selectedLookup)) === String(value) ? selectedLookup : null);

  const excluded = useMemo(() => new Set(excludeIds.map(String)), [excludeIds]);
  const localMatches = useMemo(() => {
    const terms = normalize(query).split(/\s+/).filter(Boolean);
    return safeProducts
      .filter(item => !excluded.has(String(idOf(item))))
      .filter(item => !productKind || (item.kind || 'STOCKED_GOOD') === productKind)
      .filter(item => terms.every(term => normalize([item.name, item.sku].filter(Boolean).join(' ')).includes(term)))
      .sort((a, b) => {
        const q = normalize(query);
        const starts = Number(normalize(b.name).startsWith(q)) - Number(normalize(a.name).startsWith(q));
        return starts || String(a.name).localeCompare(String(b.name), 'es');
      })
      .slice(0, 50);
  }, [safeProducts, excluded, query, productKind]);

  const matches = useMemo(() => {
    if (debouncedQuery.length < 2) return localMatches;
    const merged = new Map();
    for (const item of [...remoteProducts, ...localMatches]) {
      if (excluded.has(String(idOf(item)))) continue;
      if (productKind && (item.kind || 'STOCKED_GOOD') !== productKind) continue;
      merged.set(String(idOf(item)), item);
    }
    const q = normalize(debouncedQuery);
    return [...merged.values()].sort((a, b) => {
      const aName = normalize(a.name), bName = normalize(b.name);
      const aStarts = Number(aName.startsWith(q) || normalize(a.sku).startsWith(q));
      const bStarts = Number(bName.startsWith(q) || normalize(b.sku).startsWith(q));
      return bStarts - aStarts || String(a.name).localeCompare(String(b.name), 'es');
    }).slice(0, 50);
  }, [remoteProducts, localMatches, excluded, productKind, debouncedQuery]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 220);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    setHighlight(0);
    if (debouncedQuery.length < 2) {
      setRemoteProducts([]);
      setSearchError('');
      setLoading(false);
      return undefined;
    }
    const version = ++requestVersion.current;
    let active = true;
    setLoading(true);
    setSearchError('');
    const params = { q: debouncedQuery, page: 0, size: 20, active: true };
    if (productKind) params.kind = productKind;
    api.products(params).then(payload => {
      if (!active || requestVersion.current !== version) return;
      setRemoteProducts(listFrom(payload));
    }).catch(error => {
      if (!active || requestVersion.current !== version) return;
      setRemoteProducts([]);
      setSearchError(error?.status === 403
        ? 'No tienes permiso para consultar productos.'
        : (error?.message || 'No se pudo buscar productos en el servidor.'));
    }).finally(() => {
      if (active && requestVersion.current === version) setLoading(false);
    });
    return () => { active = false; };
  }, [debouncedQuery, productKind]);

  useEffect(() => {
    if (!value || selected) {
      if (!value) setSelectedLookup(null);
      return undefined;
    }
    let active = true;
    api.product(value).then(item => { if (active) setSelectedLookup(item); })
      .catch(() => { if (active) setSelectedLookup(null); });
    return () => { active = false; };
  }, [value, selected]);

  useEffect(() => { setHighlight(0); }, [query]);
  useEffect(() => {
    if (!open) return undefined;
    const close = event => {
      if (!rootRef.current?.contains(event.target)) {
        setOpen(false);
        setQuery('');
        setDebouncedQuery('');
      }
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  function choose(item) {
    onChange(String(idOf(item)));
    setSelectedLookup(item);
    setQuery('');
    setDebouncedQuery('');
    setRemoteProducts([]);
    setSearchError('');
    setOpen(false);
  }

  function clear() {
    onChange('');
    setSelectedLookup(null);
    setQuery('');
    setDebouncedQuery('');
    setRemoteProducts([]);
    setSearchError('');
    setOpen(true);
    inputRef.current?.focus();
  }

  function handleKeyDown(event) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setHighlight(current => Math.min(current + 1, Math.max(0, matches.length - 1)));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlight(current => Math.max(current - 1, 0));
    } else if (event.key === 'Enter' && open) {
      event.preventDefault();
      if (matches[highlight]) choose(matches[highlight]);
    } else if (event.key === 'Escape') {
      setOpen(false);
      setQuery('');
      setDebouncedQuery('');
    }
  }

  const showing = open && !disabled;
  return <div className="product-picker" ref={rootRef}>
    <div className={'product-picker-input' + (selected && !open ? ' has-value' : '')}>
      <Search size={16} aria-hidden="true" />
      <input
        ref={inputRef}
        role="combobox"
        aria-label={ariaLabel}
        aria-expanded={showing}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-busy={loading}
        aria-activedescendant={showing && matches[highlight] ? `${listId}-${highlight}` : undefined}
        disabled={disabled}
        autoComplete="off"
        placeholder={selected ? selected.name : placeholder}
        value={open ? query : (selected?.name || '')}
        onFocus={() => setOpen(true)}
        onChange={event => { setQuery(event.target.value); setOpen(true); }}
        onKeyDown={handleKeyDown}
      />
      {selected && !disabled && <button type="button" className="product-picker-clear" aria-label="Cambiar producto" title="Cambiar producto" onClick={clear}><X size={15} /></button>}
    </div>
    {showing && <ul className="product-picker-list" id={listId} role="listbox" aria-busy={loading}>
      {loading && <li className="product-picker-empty" role="status">Buscando productos…</li>}
      {!loading && searchError && <li className="product-picker-empty" role="alert">{searchError}</li>}
      {!loading && !searchError && !matches.length && <li className="product-picker-empty" role="status">
        {query.length >= 2 ? `No hay productos que coincidan con "${query}".` : 'Escribe al menos dos caracteres para buscar en todo el catálogo.'}
      </li>}
      {!loading && matches.map((item, index) => <li
        key={idOf(item)}
        id={`${listId}-${index}`}
        role="option"
        aria-selected={String(idOf(item)) === String(value)}
        className={index === highlight ? 'active' : undefined}
        onMouseEnter={() => setHighlight(index)}
        onMouseDown={event => { event.preventDefault(); choose(item); }}
      >
        <span className="product-picker-name"><strong>{item.name}</strong><small>{[item.categoryName, describe?.(item)].filter(Boolean).join(' · ')}</small></span>
        {String(idOf(item)) === String(value) ? <Check size={16} /> : <code>{item.sku}</code>}
      </li>)}
    </ul>}
  </div>;
}
