# Cobertura de API consumida por el frontend

**Código funcional de referencia:** rama `implementacion_propuestas` (consulta el HEAD de la rama para el commit vigente). La documentación por pantalla está en [Estado funcional pendiente por pantalla](../../ESTADO_FUNCIONAL_POR_PANTALLA.md). El flujo de acceso está auditado en [Login y sesión](login-session-audit.md).

Esta matriz describe los endpoints que la SPA consume. No es el inventario completo del backend ni una certificación de que cada operación esté autorizada de forma segura. La referencia completa está en [25 — APIs del backend](../../microservices-inventary-ecuapp/docs/25-referencia-apis.md).

| Módulo UI | Rutas REST consumidas | Uso |
|---|---|---|
| Sesión | POST /auth/login, POST /auth/login-challenge, GET /auth/me, POST /auth/logout, PUT /auth/me/password | Login cifrado, desafío de credenciales, perfil/permisos, cierre e invalidación/cambio de contraseña. El cliente usa fallback volátil si Storage está bloqueado. |
| Catálogo | /catalog/products, /catalog/categories, /catalog/units, /catalog/unit-dimensions, /catalog/unit-conversions, /catalog/attribute-presets, /catalog/product-kinds, /catalog/products/{id}/variants, /catalog/variants/{id} | Productos con filtros y paginación, orden por categoría, conteo activo por categoría, atributos versionados, fiscalidad, unidades y CRUD de variantes. La pantalla de productos documenta el alcance de stock y CSV por página en [auditoría de Productos](products-screen-audit.md). |
| Imágenes | GET/POST/DELETE /catalog/products/{id}/images..., POST /catalog/products/{id}/images/upload | Galería, carga PNG/JPEG, retirada, texto alternativo e imagen principal. |
| Contactos | /customers, /suppliers, /partners | Clientes, proveedores y tipo de contraparte. |
| Almacenes | /warehouses, /warehouses/{id}, /warehouses/{id}/establishment-assignments | Almacenes y asociaciones fiscales de establecimientos. |
| Datos tributarios | /taxpayers, /taxpayers/{id}/establishments, /taxpayers/{id}/snapshots, /tax/rates, /tax/products/{id} | Registro manual de contribuyentes, tarifas y tratamiento fiscal de productos. No es consulta oficial ni emisión SRI. |
| Inventario | /inventory/command-center, /inventory/balances, /inventory/availability, /inventory/movements, /inventory/adjustments | Centro de mando, existencias, disponibilidad, movimientos y ajustes. |
| Operaciones de inventario | /inventory/counts, /inventory/transfers, /inventory/reorder-rules, /inventory/replenishment-suggestions | Conteos, traslados, reposición y reglas. |
| Compras | /purchase-orders, /goods-receipts, /goods-receipts/{id}/returnable-lines, /purchase-returns | Órdenes, recepciones y devoluciones a proveedores. |
| Ventas | /sales, /sales/{id}, /sales/{id}/request-cancellation, /sales/{id}/approve-cancellation | Ventas y cancelaciones aprobadas. |
| Devoluciones/reembolsos | /sales-returns, /refunds, /refunds/{id}/... | Devoluciones físicas y estados iniciales de reembolso. Desde el commit 5567df1 las rutas fiscales y de reembolso requieren permisos; la suite de seguridad está en validación CI. |
| Suscripciones | /subscriptions, /subscriptions/options, /subscriptions/preview, acciones de facturación/prepago/cancelación | Calendarios y facturación interna. |
| WhatsApp backoffice | /whatsapp-orders, /whatsapp-orders/work-summary, /whatsapp-orders/{id}, /whatsapp-orders/{id}/confirm-payment | Bandeja de trabajo, detalle y confirmación manual de pago. |
| Reportes | /reports/inventory-balance, /reports/sales, /reports/purchases, /reports/kardex, /reports/valuation, /reports/low-stock | Balance por periodo, resultados comerciales, trazabilidad y valoración. |

## Funciones backend sin pantalla equivalente en esta SPA

- Recuperación de contraseña por autoservicio: no existe endpoint ni pantalla de solicitud/verificación/restablecimiento. La pantalla de login informa que debe contactarse al administrador.
- MFA/passkeys: no implementados.

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

## Estado de seguridad tras los cambios recientes

- /api/v1/tax/** exige security:manage.
- GET /api/v1/refunds/** exige sales:read.
- POST /api/v1/refunds/*/approve exige sales:cancel.
- Otras operaciones de reembolso exigen sales:manage.
- Se añadió una prueba HTTP de contrato para rechazar llamadas sin autenticación y verificar permisos de lectura.

La protección por ruta no cierra por sí sola el proceso financiero: siguen pendientes el ledger de pagos liquidados, conciliación, pasarela externa y confirmación autenticada del proveedor. La suite CI debe terminar correctamente antes de considerar verificado el cambio.
