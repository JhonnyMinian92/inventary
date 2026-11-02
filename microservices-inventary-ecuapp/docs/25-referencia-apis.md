# 25 — Referencia de APIs de Inventary

**Código auditado:** implementacion_propuestas · dc028a346ccf7b94cfbb60b4fb6cd5493518b481.

Este documento resume rutas presentes en controladores del backend. OpenAPI generado por la aplicación es el contrato HTTP detallado del SHA desplegado. La presencia de una ruta no prueba autorización correcta, ejecución de un sistema externo ni cobertura de tests del endpoint.

## 1. Familias de APIs

| Familia | Rutas principales | Estado |
|---|---|---|
| Sesión | /api/v1/auth/** | JWT, login challenge, sesión, logout. |
| Administración | /api/v1/admin/** | Usuarios, roles, permisos, revocación. |
| Catálogo | /api/v1/catalog/** | Productos, categorías, atributos, unidades, variantes, imágenes. |
| Catálogo público | /api/v1/public/**, GraphQL | Consulta pública acotada y GraphQL protegido. |
| Partners/almacenes | /api/v1/customers/**, /suppliers/**, /warehouses/**, /partners/** | Contrapartes, bodegas y relaciones. |
| Registro fiscal | /api/v1/taxpayers/**, /warehouses/{id}/establishment-assignments/** | Importación manual y scopes fiscales. |
| Tarifas fiscales | /api/v1/tax/** | CRUD inicial de tarifas y tratamiento de producto; público por SecurityConfig. |
| Inventario | /api/v1/inventory/** | Saldos, disponibilidad, movimientos, command-center, ajustes, conteos y reposición. |
| Transferencias | /api/v1/inventory/transfers/** | Crear, despachar, recibir, cancelar y revertir. |
| Compras | /api/v1/purchase-orders/**, /goods-receipts/**, /purchase-returns/** | Órdenes, recepciones y devoluciones. |
| Ventas | /api/v1/sales/**, /sales-returns/** | Ventas, cancelaciones aprobadas y devoluciones físicas. |
| Reembolsos | /api/v1/refunds/** | Estados iniciales de reembolso; público por SecurityConfig. |
| Suscripciones | /api/v1/subscriptions/** | Plan/calendario, cuotas, previsualización, facturación interna y cancelación. |
| Reportes | /api/v1/reports/** | Balance histórico, ventas, compras, Kárdex, valoración y stock bajo. |
| WhatsApp integración | /api/v1/integrations/whatsapp/** | El matcher JWT permite la ruta; el controlador valida X-Integration-Key. |
| WhatsApp backoffice | /api/v1/whatsapp-orders/** | Bandeja, work-summary, detalle y confirmación manual. |
| Auditoría | /api/v1/audit-events/** | Lectura paginada con audit:read y scopes. |
| Outbox | /api/v1/operations/outbox/** | Diagnóstico, métricas y retry con operations:manage. |
| Operaciones externas | /api/v1/integration-operations/** | Crear, consultar, listar y reintentar operaciones. |
| Webhooks | /api/v1/webhook-subscriptions/** | Crear, listar y desactivar suscripciones. No acredita dispatch completo. |
| Dashboard realtime | GET /api/v1/dashboard/events | SSE autenticado; señales periódicas para refresco de datos. |
| Operación | /actuator/health/**, /actuator/metrics/** | Health público limitado; métricas/info requieren permisos. |

## 2. Catálogo y entidades

Principales recursos REST:
- Productos: listar/buscar/paginar, detalle, crear/actualizar, activar/desactivar y variantes.
- Categorías: listar/crear/actualizar y activar/desactivar.
- Definición de atributos por categoría y presets.
- Unidades, dimensiones y conversiones.
- Imágenes gestionadas PNG/JPEG, retirada y exposición pública condicionada a publicación.

Las variantes tienen SKU/código de barras/combinación/precio; no mantienen stock/costo independiente. Las imágenes no son un CDN multi-región.

## 3. Inventario

- GET /api/v1/inventory/balances: saldos paginados por producto/almacén.
- GET /api/v1/inventory/availability: consulta informativa; no reserva.
- GET /api/v1/inventory/movements, /{id}, /{id}/trace: libro de movimientos.
- GET /api/v1/inventory/command-center: agregados y alertas para el mando operativo.
- POST /api/v1/inventory/adjustments: ajuste con Idempotency-Key.
- /api/v1/inventory/counts: conteos físicos, contabilización/cancelación y maker-checker.
- /api/v1/inventory/reorder-rules y /replenishment-suggestions: reposición.
- /api/v1/inventory/transfers: transferencias con despacho/recepción/cancelación/reversión idempotentes.

El modelo operativo tiene una ubicación de stock por producto/almacén; no es WMS multi-bin.

## 4. Compras y devoluciones a proveedores

- /api/v1/purchase-orders: crear, listar, detalle, enviar, aprobar, cancelar y registrar recepción.
- La búsqueda q admite número de orden y proveedor, con filtros server-side, paginación y scopes de almacén.
- /api/v1/goods-receipts: listado/detalle de recepciones, con búsqueda paginada.
- GET /api/v1/goods-receipts/{id}/returnable-lines: cantidad recibida, devuelta y restante calculada por el servidor.
- /api/v1/purchase-returns: listar/detallar/crear devoluciones físicas, con búsqueda server-side.

Una orden no suma stock hasta la recepción. Una devolución física no es una nota de crédito de proveedor ni un pago.

## 5. Ventas, retornos y reembolsos

- /api/v1/sales: crear/listar/detallar venta; solicitud y aprobación separada de cancelación.
- /api/v1/sales-returns: devoluciones físicas desde la venta original.
- /api/v1/refunds: solicitar/listar y cambiar estados de reembolso (approve, processing, complete, fail, cancel).

**Advertencia:** RefundController no declara @PreAuthorize y SecurityConfig permite /api/v1/refunds/** sin autenticación. Además, el servicio no acredita libro de pago liquidado, reembolso acumulado permitido ni confirmación de gateway. La API existe, pero no debe usarse como mecanismo financiero seguro.

## 6. Datos tributarios y tarifas

- /api/v1/taxpayers: importación manual, consulta, establecimientos y snapshots; requiere permisos taxpayer en las rutas protegidas.
- /api/v1/warehouses/{warehouseId}/establishment-assignments: asociación temporal de almacén/establecimiento.
- GET /api/v1/tax/rates: lista tarifas.
- POST /api/v1/tax/rates, PUT /api/v1/tax/rates/{id}: crear/modificar tarifas.
- PUT /api/v1/tax/products/{id}: tratamiento y código tributario de producto.

**Advertencia:** SecurityConfig permite toda la familia /api/v1/tax/** y TaxController no exige permisos por método. El parámetro date de GET rates no se aplica actualmente como filtro de vigencia. La implementación no conecta con proveedor oficial SRI ni emite comprobantes.

## 7. Reportes

- GET /api/v1/reports/inventory-balance: balance histórico por fechas/almacén y producto opcional.
- GET /api/v1/reports/sales: resumen, serie diaria y productos.
- GET /api/v1/reports/purchases: compras por periodo.
- GET /api/v1/reports/kardex: movimientos/valorización para producto y almacén.
- GET /api/v1/reports/valuation: valoración.
- GET /api/v1/reports/low-stock: alertas de reposición.

Los reportes no sustituyen contabilidad formal, bancos, cuentas por pagar/cobrar ni obligaciones tributarias.

## 8. Suscripciones

La API expone catálogo de opciones, previsualización, CRUD/consulta, acciones de facturación, prepago y cancelación. La facturación genera ventas internas; no implica un cobro procesado por banco o pasarela.

## 9. WhatsApp

### Integración técnica

/integrations/whatsapp/** ofrece catálogo, carritos, cotizaciones, pedidos, consulta y cancelación. La integración debe enviar X-Integration-Key desde un secreto del workflow. El backend calcula precio/cotización y revalida la orden.

### Backoffice

- GET /api/v1/whatsapp-orders: bandeja paginada.
- GET /api/v1/whatsapp-orders/work-summary: contadores de la bandeja.
- GET /api/v1/whatsapp-orders/{id}: detalle.
- POST /api/v1/whatsapp-orders/{id}/confirm-payment: confirmación manual con clave idempotente, método y referencia, permiso sales:manage.

La confirmación representa verificación humana registrada; no llama a gateway.

## 10. Operaciones e integración V28

### Integration operations

- POST /api/v1/integration-operations: crear operación QUEUED, requiere integration:manage e Idempotency-Key.
- GET /api/v1/integration-operations y /{operationId}: lectura, integration:read.
- POST /api/v1/integration-operations/{operationId}/retry: reencolar estados terminales, integration:manage.

### Webhook subscriptions

- POST /api/v1/webhook-subscriptions: alta, integration:manage.
- GET /api/v1/webhook-subscriptions: listar, integration:read.
- DELETE /api/v1/webhook-subscriptions/{id}: desactivar, integration:manage.

V28 crea integration_operation, webhook_subscription y webhook_delivery. En el árbol auditado no se encontró un ejecutor completo de integration_operation ni un dispatcher que entregue/reintente webhook_delivery. Son capacidades de administración/almacenamiento inicial, no integración distribuida terminada.

## 11. Outbox, auditoría y realtime

- /api/v1/operations/outbox: stats, lista, detalle y requeue con operations:manage; payload de negocio no se expone en la vista operativa.
- /api/v1/audit-events: consulta paginada de eventos auditables con audit:read y ámbito.
- GET /api/v1/dashboard/events: SSE autenticado. Emite connected y snapshot periódico con updatedAt; el frontend vuelve a consultar sus endpoints. El evento no contiene el dashboard completo.
- Outbox entrega al menos una vez; consumidores deben deduplicar eventId. Kafka está opt-in y el consumidor existente invalida caché pública.

## 12. Paginación, errores e idempotencia

- La mayoría de listados aceptan page base cero y size limitado (habitualmente máximo 100); revisa el controlador específico.
- Errores comunes usan Problem Details y requestId.
- Códigos habituales: 400 validación/query, 401 autenticación, 403 autorización, 404 recurso, 409 conflicto, 413 body, 415 content type, 422 regla de negocio, 429 Retry-After.
- Para un reintento lógico conserva la misma Idempotency-Key y exactamente el mismo payload. Una clave no autoriza a cambiar el cuerpo.

## 13. Permisos y superficie pública

La mayoría de rutas sensibles está protegida por autoridades específicas, pero los dos casos siguientes son excepciones críticas que deben corregirse:
- /api/v1/tax/**: permitAll; operaciones de lectura/escritura fiscales públicas.
- /api/v1/refunds/**: permitAll; consulta/transiciones de reembolso públicas.
- /api/v1/integrations/whatsapp/**: permitAll a nivel JWT, pero cada operación del controlador valida X-Integration-Key.
- /api/v1/public/**: lectura de catálogo público.

No usar los permisos declarados en la UI como sustituto de esta matriz de seguridad real.