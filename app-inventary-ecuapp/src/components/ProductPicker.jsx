import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, Search, X } from 'lucide-react';

function idOf(item) { return item?.id ?? item?.productId ?? ''; }

/** Quita tildes y mayúsculas para que "cafe" encuentre "Café". */
function normalize(value) {
  return String(value || '').normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es');
}

/**
 * Buscador de productos por nombre: el usuario escribe parte del nombre y elige de la lista.
 * El código interno solo se muestra como referencia; no hace falta conocerlo.
 */
export default function ProductPicker({ products, value, onChange, excludeIds = [], disabled, placeholder = 'Escribe el nombre del producto', describe, ariaLabel = 'Producto' }) {
  const listId = useId();
  const rootRef = useRef(null);
  const inputRef = useRef(null);
  const selected = products.find(item => String(idOf(item)) === String(value)) || null;
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);

  const excluded = useMemo(() => new Set(excludeIds.map(String)), [excludeIds]);
  const matches = useMemo(() => {
    const terms = normalize(query).split(/\s+/).filter(Boolean);
    return products
      .filter(item => !excluded.has(String(idOf(item))))
      .filter(item => { const name = normalize(item.name); return terms.every(term => name.includes(term)); })
      .sort((a, b) => {
        // Primero los que empiezan con lo escrito, luego orden alfabético.
        const q = normalize(query);
        const starts = Number(normalize(b.name).startsWith(q)) - Number(normalize(a.name).startsWith(q));
        return starts || String(a.name).localeCompare(String(b.name), 'es');
      })
      .slice(0, 50);
  }, [products, excluded, query]);

  useEffect(() => { setHighlight(0); }, [query]);
  useEffect(() => {
    if (!open) return undefined;
    const close = event => { if (!rootRef.current?.contains(event.target)) { setOpen(false); setQuery(''); } };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  function choose(item) {
    onChange(String(idOf(item)));
    setQuery('');
    setOpen(false);
  }

  function clear() {
    onChange('');
    setQuery('');
    setOpen(true);
    inputRef.current?.focus();
  }

  function handleKeyDown(event) {
    if (event.key === 'ArrowDown') { event.preventDefault(); setOpen(true); setHighlight(current => Math.min(current + 1, matches.length - 1)); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setHighlight(current => Math.max(current - 1, 0)); }
    else if (event.key === 'Enter' && open) { event.preventDefault(); if (matches[highlight]) choose(matches[highlight]); }
    else if (event.key === 'Escape') { setOpen(false); setQuery(''); }
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
    {showing && <ul className="product-picker-list" id={listId} role="listbox">
      {!matches.length && <li className="product-picker-empty" role="presentation">{query ? `No hay productos que coincidan con "${query}".` : 'No hay más productos disponibles.'}</li>}
      {matches.map((item, index) => <li
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
