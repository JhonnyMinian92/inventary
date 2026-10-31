
import { request } from '../http/request.js';
const query = params => new URLSearchParams(params).size ? '?' + new URLSearchParams(params) : '';
export const catalogApi = {
  products: (params = {}) => request('/catalog/products' + query(params)),
  product: id => request('/catalog/products/' + encodeURIComponent(id)),
  productImages: id => request('/catalog/products/' + encodeURIComponent(id) + '/images'),
  uploadProductImage: (id, file, options = {}) => {
    const body = new FormData();
    body.append('file', file);
    if (options.altText) body.append('altText', options.altText);
    body.append('sortOrder', String(options.sortOrder ?? 0));
    body.append('primaryImage', String(Boolean(options.primaryImage)));
    return request('/catalog/products/' + encodeURIComponent(id) + '/images/upload', { method: 'POST', body });
  },
  unpublishProductImage: (productId, imageId) => request('/catalog/products/' + encodeURIComponent(productId) + '/images/' + encodeURIComponent(imageId), { method: 'DELETE' }),
  createProduct: data => request('/catalog/products', { method: 'POST', body: data }),
  updateProduct: (id, data) => request('/catalog/products/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deleteProduct: id => request('/catalog/products/' + encodeURIComponent(id), { method: 'DELETE' }),
  activateProduct: id => request('/catalog/products/' + encodeURIComponent(id) + '/activate', { method: 'POST' }),
  deactivateProduct: id => request('/catalog/products/' + encodeURIComponent(id) + '/deactivate', { method: 'POST' }),
  categories: (params = {}) => request('/catalog/categories' + query(params)),
  createCategory: data => request('/catalog/categories', { method: 'POST', body: data }),
  updateCategory: (id, data) => request('/catalog/categories/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deleteCategory: id => request('/catalog/categories/' + encodeURIComponent(id), { method: 'DELETE' }),
  activateCategory: id => request('/catalog/categories/' + encodeURIComponent(id) + '/activate', { method: 'POST' }),
  deactivateCategory: id => request('/catalog/categories/' + encodeURIComponent(id) + '/deactivate', { method: 'POST' }),
  units: (params = {}) => request('/catalog/units' + query(params)),
  unitConversions: () => request('/catalog/unit-conversions'),
  createUnitConversion: data => request('/catalog/unit-conversions', { method: 'POST', body: data }),
  createUnit: data => request('/catalog/units', { method: 'POST', body: data }),
  unitDimensions: () => request('/catalog/unit-dimensions'),
  attributePresets: () => request('/catalog/attribute-presets'),
  categorySuggestion: text => request('/catalog/attribute-presets/suggestion?' + new URLSearchParams({ text })),
  productKinds: () => request('/catalog/product-kinds'),
  updateUnit: (id, data) => request('/catalog/units/' + encodeURIComponent(id), { method: 'PUT', body: data }),
  deleteUnit: id => request('/catalog/units/' + encodeURIComponent(id), { method: 'DELETE' }),
  categoryAttributes: id => request('/catalog/categories/' + encodeURIComponent(id) + '/attributes'),
  saveCategoryAttributes: (id, data) => request('/catalog/categories/' + encodeURIComponent(id) + '/attributes', { method: 'PUT', body: data })
};
