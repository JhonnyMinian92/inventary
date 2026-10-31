import { ArrowDownToLine, ArrowUpFromLine, Boxes, Building2, ChartNoAxesCombined, LayoutDashboard, MessageCircle, Repeat, Warehouse } from 'lucide-react';

export const NAV_GROUPS = [
  { label: 'General', items: [{ id: 'overview', label: 'Resumen', icon: LayoutDashboard }] },
  { label: 'Catálogo', items: [{ id: 'products', label: 'Productos', icon: Boxes, permission: 'catalog:read', sections: ['products', 'categories'] }] },
  { label: 'Operaciones', items: [
    { id: 'sales', label: 'Ventas', icon: ArrowUpFromLine, permission: 'sales:read' },
    { id: 'subscriptions', label: 'Suscripciones', icon: Repeat, permission: 'sales:read' },
    { id: 'whatsapp', label: 'Pedidos WhatsApp', icon: MessageCircle, permission: 'sales:read', badge: 'whatsapp' },
    { id: 'purchases', label: 'Compras', icon: ArrowDownToLine, permission: 'purchasing:read' },
    { id: 'inventory', label: 'Inventario', icon: Warehouse, permission: 'inventory:read' }
  ] },
  { label: 'Gestión', items: [
    { id: 'partners', label: 'Almacenes y contactos', icon: Building2 },
    { id: 'reports', label: 'Reportes', icon: ChartNoAxesCombined, permission: 'reports:financial' }
  ] }
];

export const SECTION_TITLES = {
  overview: 'Resumen', products: 'Productos', categories: 'Productos', sales: 'Ventas',
  subscriptions: 'Suscripciones', whatsapp: 'Pedidos WhatsApp', purchases: 'Compras',
  inventory: 'Inventario', partners: 'Almacenes y contactos', reports: 'Reportes'
};
export const VALID_SECTIONS = new Set(Object.keys(SECTION_TITLES));
export const WORKFLOW_MODALS = new Set(['product', 'category', 'purchase', 'sale', 'receive']);
export const WORKFLOW_SECTIONS = { product: 'products', category: 'categories', purchase: 'purchases', sale: 'sales', receive: 'purchases' };
export const WORKFLOW_TITLES = {
  product: ['Nuevo producto', 'Editar producto'], category: ['Nueva categoría', 'Editar categoría'],
  purchase: ['Nueva compra'], sale: ['Nueva venta'], receive: ['Registrar recepción']
};
export const DETAIL_SECTIONS = { purchase: 'purchases', sale: 'sales', product: 'products', whatsapp: 'whatsapp', subscription: 'subscriptions' };
export const MODAL_DETAILS = new Set(['purchase', 'sale']);
