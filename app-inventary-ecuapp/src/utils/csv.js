/**
 * Escape spreadsheet CSV cells and neutralize formula injection from untrusted text.
 * Numeric values remain numeric; textual values beginning with spreadsheet operators
 * (including after whitespace/control characters) are prefixed with an apostrophe.
 */
export function asCsvValue(value) {
  const isNumeric = typeof value === 'number' && Number.isFinite(value);
  const text = value === null || value === undefined || (typeof value === 'number' && !Number.isFinite(value))
    ? ''
    : typeof value === 'object'
      ? JSON.stringify(value)
      : String(value);
  const safeText = !isNumeric && /^[\u0000-\u0020\uFEFF]*[=+@-]/.test(text)
    ? `'${text}`
    : text;
  return '"' + safeText.replaceAll('"', '""') + '"';
}

export function buildCsv(columns, rows) {
  const header = columns.map(column => asCsvValue(column.label)).join(',');
  const records = (rows || []).map(row => columns.map(column =>
    asCsvValue(typeof column.csvValue === 'function' ? column.csvValue(row) : row[column.key])
  ).join(','));
  return [header, ...records].join('\r\n');
}
