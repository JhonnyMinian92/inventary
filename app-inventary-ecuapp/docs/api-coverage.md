# Cobertura de API del frontend

Esta matriz se revisó contra los controladores Spring bajo \`microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary\` y el cliente \`src/api.js\`. Describe las rutas que consume el frontend; no sustituye las reglas de autorización del servidor.

| Módulo de UI | Rutas REST consumidas | Propósito |
|---|---|---|
| Sesión | \`POST /auth/login\`, \`GET /auth/me\`, \`POST /auth/logout\` | Iniciar sesión, recuperar usuario y cerrar sesión. |
| Productos | \`GET /catalog/products\`, \`GET /catalog/products/{id}\`, \`POST /catalog/products\`, \`PUT /catalog/products/{id}\`, \`POST /catalog/products/{id}/activate\`, \`POST /catalog/products/{id}/deactivate\` | Catálogo, detalle y estado del producto. |
| Categorías y atributos | \`GET /catalog/categories\`, \`POST /catalog/categories\`, \`PUT /catalog/categories/{id}\`, \`POST /catalog/categories/{id}/activate\`, \`POST /catalog/categories/{id}/deactivate\`, \`GET /catalog/categories/{id}/attributes\`, \`PUT /catalog/categories/{id}/attributes\` | Categorías y esquema de propiedades dinámicas. |
| Unidades | \`GET /catalog/units\`, \`POST /catalog/units\`, \`PUT /catalog/units/{id}\`, \`GET /catalog/unit-conversions\`, \`POST /catalog/unit-conversions\` | Unidades y factores de conversión. |
| Almacenes | \`GET /warehouses\`, \`GET /warehouses/{id}\`, \`POST /warehouses\`, \`PUT /warehouses/{id}\`, \`POST /warehouses/{id}/activate\`, \`POST /warehouses/{id}/deactivate\` | Catálogo de almacenes. |
| Proveedores y clientes | \`GET/POST /suppliers\`, \`GET/PUT /suppliers/{id}\`, acciones \`activate/deactivate\`; equivalentes en \`/customers\` | Contrapartes de compras y ventas. |
| Saldos y movimientos | \`GET /inventory/balances\`, \`GET /inventory/balances/{productId}/{warehouseId}\`, \`GET /inventory/availability\`, \`GET /inventory/movements\`, \`POST /inventory/adjustments\` | Consulta de stock, disponibilidad y trazabilidad; ajustes autorizados. |
| Traslados | \`GET /inventory/transfers\`, \`GET /inventory/transfers/{id}\`, \`POST /inventory/transfers\`, acciones \`dispatch\`, \`receive\`, \`cancel\`, \`reverse\` bajo \`/inventory/transfers/{id}\` | Crear y procesar movimientos entre almacenes. |
| Conteos y reposición | \`GET/POST /inventory/counts\`, \`GET /inventory/counts/{id}\`, acciones \`post/cancel\`; \`GET/POST /inventory/reorder-rules\`, \`PUT /inventory/reorder-rules/{id}\`, \`POST /inventory/reorder-rules/{id}/deactivate\`, \`GET /inventory/replenishment-suggestions\` | Conteos físicos y abastecimiento. |
| Compras y recepción | \`GET /purchase-orders\`, \`GET /purchase-orders/{id}\`, \`POST /purchase-orders\`, acciones \`submit/approve/cancel\`, \`POST /purchase-orders/{id}/receipts\`, \`GET /goods-receipts\`, \`GET /goods-receipts/{id}\` | Orden, aprobación y contabilización de recepciones. |
| Devoluciones a proveedores | \`GET /purchase-returns\`, \`GET /purchase-returns/{id}\`, \`POST /purchase-returns\` | Devolver cantidades de recepciones contabilizadas. |
| Ventas | \`GET /sales\`, \`GET /sales/{id}\`, \`POST /sales\`, \`POST /sales/{id}/request-cancellation\`, \`POST /sales/{id}/approve-cancellation\` | Venta y aprobación separada de anulaciones. |
| Devoluciones de clientes | \`GET /sales-returns\`, \`GET /sales-returns/{id}\`, \`POST /sales-returns\` | Devoluciones por artículo de una venta. |
| Informes | \`GET /reports/inventory-balance\`, \`GET /reports/sales\`, \`GET /reports/purchases\`, \`GET /reports/kardex\`, \`GET /reports/valuation\`, \`GET /reports/low-stock\` | Balance histórico, ventas/margen, compras, kardex, valoración y alertas de stock bajo. |

## Reglas de autorización relevantes

- Las rutas de informe están protegidas por \`reports:financial\`; la UI muestra la falta de permiso sin inventar datos.
- El backend exige permisos propios para catálogo, inventario, compras, aprobación de compras, ventas, cancelación de ventas y administración de contrapartes. Los controles del frontend solo mejoran la experiencia; no sustituyen la autorización del servidor.
- Una orden de compra no aumenta el inventario hasta que el backend registra la recepción aprobada.
- La venta, los traslados, los ajustes, los conteos y las devoluciones se contabilizan a través de las APIs, no modificando saldos localmente.
- El cliente añade \`Idempotency-Key\` a las solicitudes marcadas como idempotentes y bloquea/inhabilita acciones en curso donde corresponde. En caso de resultado de red ambiguo, el backend y la clave de idempotencia deben seguir siendo la autoridad para evitar contabilizaciones duplicadas.

## Rutas del backend que no son pantallas de este frontend

No se incluye aquí administración avanzada de usuarios/roles, auditoría completa, políticas de costeo, catálogo público/GraphQL, imágenes de producto, variantes, operaciones de outbox ni integraciones de WhatsApp. Son áreas que existen en el backend o sus controladores y deben tratarse como alcance adicional; no deben interpretarse como pantallas implementadas por este frontend.
