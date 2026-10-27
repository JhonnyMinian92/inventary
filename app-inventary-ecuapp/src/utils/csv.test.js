import test from 'node:test';
import assert from 'node:assert/strict';
import { asCsvValue, buildCsv } from './csv.js';

test('escapes quotes, commas and newlines as valid CSV cells', () => {
  assert.equal(asCsvValue('a,"b"\nc'), '"a,""b""\nc"');
});

test('neutralizes formula injection for dangerous leading characters', () => {
  for (const value of ['=1+1', '+SUM(A1:A2)', '-cmd|/C calc!A0', '@SUM(1,2)']) {
    assert.equal(asCsvValue(value), `"'${value.replaceAll('"', '""')}"`);
  }
});

test('neutralizes formula injection after leading whitespace and controls', () => {
  const hyperlink = asCsvValue('  =HYPERLINK("https://example.invalid","open")');
  assert.match(hyperlink, /^"'  =HYPERLINK\(/);
  assert.match(hyperlink, /""https:\/\/example\.invalid""/);
  assert.match(hyperlink, /""open""\)"$/);
  assert.equal(asCsvValue('\t@SUM(1,2)'), "\"'\\t@SUM(1,2)\"");
  assert.equal(asCsvValue('\uFEFF+1+1'), "\"'\\uFEFF+1+1\"".replaceAll('\\uFEFF', '\uFEFF'));
});

test('preserves finite numeric cells, including negative numeric values', () => {
  assert.equal(asCsvValue(-12.5), '"-12.5"');
  assert.equal(asCsvValue(0), '"0"');
  assert.equal(asCsvValue(Number.NaN), '""');
});

test('builds a CSV with safe headers and rows without losing Unicode', () => {
  const csv = buildCsv(
    [{ key: 'sku', label: 'SKU' }, { key: 'description', label: 'Descripción' }, { key: 'qty', label: 'Cantidad' }],
    [{ sku: 'SKU-1', description: '=2+2', qty: 3 }, { sku: 'ñ-2', description: 'café, grande', qty: -1 }]
  );
  assert.equal(csv.split('\r\n').length, 3);
  assert.match(csv, /"SKU-1","'=2\+2","3"/);
  assert.match(csv, /"ñ-2","café, grande","-1"/);
});
