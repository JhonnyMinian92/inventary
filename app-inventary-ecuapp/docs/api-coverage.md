# Cobertura de API consumida por el frontend

**Código auditado:** rama implementacion_propuestas, SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481.

Esta matriz describe los endpoints que la SPA consume. No es el inventario completo del backend ni una certificación de que cada operación esté autorizada de forma segura. La referencia completa está en [25 — APIs del backend](../../microservices-inventary-ecuapp/docs/25-referencia-apis.md).

| Módulo UI | Rutas REST consumidas | Uso |
|---|---|---|
| Sesión | POST /auth/login, POST /auth/login-challenge, GET /auth/me, POST /auth/logout | Inicio, desafío de credenciales, sesión y cierre. |
| Catálogo | /catalog/products, /catalog/categories, /catalog/units, /catalog/unit-dimensions, /catalog/unit-conversions, /catalog/attribute-presets, /catalog/product-kinds, rutas de variantes | Productos, categorías, atributos, tipo de producto, unidades y variantes. |
| Imágenes | GET/POST/DELETE /catalog/products/{id}/images... | Galería, carga PNG/JPEG y retirada de imágenes. |
| Contactos | /customers, /suppliers, /partners | Clientes, proveedores y tipo de contraparte. |
| Almacenes | /warehouses, /warehouses/{id}, /warehouses/{id}/establishment-assignments | Almacenes y asociaciones fiscales de establecimientos. |
| Datos tributarios | /taxpayers, /taxpayers/{id}/establishments, /taxpayers/{id}/snapshots, /tax/rates, /tax/products/{id} | Registro manual de contribuyentes, tarifas y tratamiento fiscal de productos. No es consulta oficial ni emisión SRI. |
| Inventario | /inventory/command-center, /inventory/balances, /inventory/availability, /inventory/movements, /inventory/adjustments | Centro de mando, existencias, disponibilidad, movimientos y ajustes. |
| Operaciones de inventario | /inventory/counts, /inventory/transfers, /inventory/reorder-rules, /inventory/replenishment-suggestions | Conteos, traslados, reposición y reglas. |
| Compras | /purchase-orders, /goods-receipts, /goods-receipts/{id}/returnable-lines, /purchase-returns | Órdenes, recepciones y devoluciones a proveedores. |
| Ventas | /sales, /sales/{id}, /sales/{id}/request-cancellation, /sales/{id}/approve-cancellation | Ventas y cancelaciones aprobadas. |
| Devoluciones/reembolsos | /sales-returns, /refunds, /refunds/{id}/... | Devoluciones físicas y estados iniciales de reembolso. La API de reembolsos tiene bloqueo crítico de autorización en backend. |
| Suscripciones | /subscriptions, /subscriptions/options, /subscriptions/preview, acciones de facturación/prepago/cancelación | Calendarios y facturación interna. |
| WhatsApp backoffice | /whatsapp-orders, /whatsapp-orders/work-summary, /whatsapp-orders/{id}, /whatsapp-orders/{id}/confirm-payment | Bandeja de trabajo, detalle y confirmación manual de pago. |
| Reportes | /reports/inventory-balance, /reports/sales, /reports/purchases, /reports/kardex, /reports/valuation, /reports/low-stock | Balance por periodo, resultados comerciales, trazabilidad y valoración. |

## Funciones backend sin pantalla equivalente en esta SPA

- Administración completa de usuarios, roles y permisos.
- Consulta detallada de auditoría y operaciones Outbox.
- Operaciones de integración, suscripciones de webhooks y diagnóstico de entregas.
- GraphQL de catálogo.
- Gestión operativa de Kafka.
- Herramientas de operación/Actuator.

## Reglas del cliente

- El frontend centraliza autorización Bearer, request ID, Problem Details y claves de idempotencia en el transporte compartido.
- La clave de idempotencia debe reutilizarse solo con el mismo payload y la misma operación lógica.
- Ante timeout o pérdida de conexión después de enviar una escritura, consultar el estado en backend antes de repetirla.
- Un 401 significa sesión ausente/no válida; un 403 significa que el usuario no tiene el permiso requerido. Ninguno debe convertirse en lista vacía o cifra cero.
- El estado visual de una acción no sustituye la autorización ni la validación transaccional del servidor.

## Riesgo de seguridad que afecta a esta matriz

Aunque la UI muestre gestión fiscal y reembolsos solo a ciertos permisos, el código backend auditado mantiene permitAll para /api/v1/tax/** y /api/v1/refunds/**. La ocultación de pestañas no protege esos endpoints. No exponerlos a redes no confiables hasta corregir SecurityConfig/controladores y probar respuestas 401/403 por HTTP.