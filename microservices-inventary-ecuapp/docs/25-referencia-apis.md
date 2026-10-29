# 25 — Referencia actual de APIs de Inventary

Este catálogo corresponde a las rutas anotadas en el código fuente del backend del corte documental. Para los campos exactos de cada request/response, los DTOs y el OpenAPI generado por el mismo SHA desplegado son la autoridad final. Las rutas propuestas en documentos de diseño antiguos no deben tratarse como contratos implementados.

- Base REST local: http://localhost:8080/api/v1
- Formato: JSON UTF-8.
- Identificadores: UUID salvo que se indique SKU/identifier.
- Sesión: Authorization: Bearer <accessToken>.
- Integración de máquina WhatsApp: X-Integration-Key validada por la aplicación.
- Fechas: YYYY-MM-DD; zona de reportes por defecto America/Guayaquil, configurable.
- El backend rechaza propiedades JSON desconocidas.
- El permiso del servidor es autoritativo; ocultar botones en UI no es seguridad.
- GET /v3/api-docs y Swagger UI solo están disponibles cuando SWAGGER_ENABLED se habilita en entorno controlado.

## Matriz de implementación y evidencia por familia API

La matriz distingue el código presente, la evidencia histórica y la validación actual. “Prueba histórica” significa que la clase aparece en el informe del 2026-10-23; el informe indica que la copia probada no tenía SHA Git. Los commits de esta consolidación solo modifican Markdown. No se ha vuelto a ejecutar la suite contra el HEAD documental.

| Familia de rutas | Controladores / fuente principal | Pruebas históricas descritas | Estado de validación del HEAD |
|---|---|---|---|
| Login, identidad, usuarios, roles y permisos | auth/AuthController, UserManagementController, RoleManagementController, SecurityConfig | AuthAdminApiTest, SecurityContractApiTest, RoleMatrixApiTest | Código presente; pruebas de otro snapshot; reejecutar |
| Productos, categorías y esquema de atributos | catalog/ProductController, CategoryController, CatalogStructureController | CatalogApiTest, DomainRulesTest | Código presente; pruebas históricas; reejecutar |
| Variantes, unidades, conversiones e imágenes | catalog/CatalogStructureController, media/ProductImageController | CatalogApiTest | Código presente; pruebas históricas; reejecutar |
| Catálogo público REST y GraphQL | catalog/PublicCatalogController, graphql/GraphqlCatalogController, graphql/schema.graphqls | CatalogApiTest, SecurityContractApiTest | Código presente; pruebas históricas; reejecutar |
| Proveedores, clientes y almacenes | partners/PartnerController, warehouse/WarehouseController | CatalogApiTest, InventoryApiTest | Código presente; pruebas históricas; reejecutar |
| Saldos, disponibilidad, movimientos, ajustes, conteos, reposición | inventory/InventoryController, InventoryCountController, ReorderController | InventoryApiTest, StockBalanceTest | Código presente; pruebas históricas; reejecutar |
| Traslados/compensaciones | transfers/StockTransferController | TransfersApiTest | Código presente; pruebas históricas; reejecutar |
| Compras, recepción y devoluciones a proveedor | purchasing/PurchaseOrderController, PurchaseReturnController | PurchasingAndCostingApiTest | Código presente; pruebas históricas; reejecutar |
| Ventas, cancelación y devoluciones de cliente | sales/SalesController, returns/SalesReturnController | SalesApiTest | Código presente; pruebas históricas; reejecutar |
| Política de coste y reportes | inventory/CostingPolicyController, reports/InventoryBalanceReportController | PurchasingAndCostingApiTest, ReportsApiTest, InventoryBalanceReportServiceTest | Código presente; pruebas históricas; reejecutar |
| Integración y backoffice WhatsApp | whatsapp/WhatsAppIntegrationController, WhatsAppOrderBackofficeController | WhatsAppApiTest | Código presente; pruebas históricas; reejecutar |
| Auditoría y Outbox | audit/AuditController, sales/OutboxOperationsController | AuditAndOutboxApiTest | Código presente; pruebas históricas; reejecutar |
| Actuator, CORS, rate limit, errores y OpenAPI | config/*, application.yaml | SecurityContractApiTest | Código/config presentes; pruebas históricas; reejecutar |
| Cliente frontend / consumo REST | app-inventary-ecuapp/src/api.js y features/* | No hay script test/lint declarado en package.json | Build/tests del frontend pendientes de ejecución en HEAD |

Las clases citadas proceden del informe de ejecución histórico y deben comprobarse con el árbol actual antes de usarse como prueba. La declaración exacta del estado de una ruta requiere: controlador y DTO en el SHA; tests positivos y negativos; autorización; transacción/efecto persistido; idempotencia/concurrencia donde aplica; y resultado de ejecución referido a ese SHA.

## 1. Sesión y administración

| Método | Ruta | Acceso / finalidad |
|---|---|---|
| POST | /auth/login-challenge | Público y rate-limited; clave ECDH P-256 de un solo uso (challengeId, algorithm, publicKey, expiresAt) |
| POST | /auth/login | Público y rate-limited; credenciales cifradas (`encrypted`); devuelve accessToken, tokenType, expiresAt. 429 LOGIN_TEMPORARILY_LOCKED tras 5 fallos de la misma cuenta |
| GET | /auth/me | Token vigente; identidad, rol y permisos efectivos |
| POST | /auth/logout | Sesión válida; 204 |
| PUT | /auth/me/password | Contraseña actual y nueva; 204 |
| GET | /admin/users | security:manage; página de usuarios |
| GET | /admin/users/{id} | security:manage; detalle |
| POST | /admin/users | security:manage; creación |
| PATCH | /admin/users/{id} | security:manage; actualización |
| PUT | /admin/users/{id}/password | security:manage; restablecimiento |
| GET | /admin/roles | security:manage |
| GET | /admin/roles/{code} | security:manage |
| POST | /admin/roles | security:manage; crear rol |
| PATCH | /admin/roles/{code} | security:manage |
| GET | /admin/permissions | security:manage |
| PUT | /admin/roles/{code}/permissions | security:manage |

Login con cifrado en la capa de aplicación (obligatorio por defecto, `LOGIN_ENCRYPTION_REQUIRED=true`):

1. `POST /auth/login-challenge` devuelve `challengeId`, `algorithm` (`ECDH-ES+HKDF-SHA256+A256GCM`), `publicKey` (SPKI P-256 en Base64) y `expiresAt` (120 s).
2. El cliente genera una clave ECDH P-256 efímera, deriva el secreto compartido, aplica HKDF-SHA256 (salt = `challengeId` en UTF-8, info = `inventary/login/v1`, 32 bytes) y cifra `{"username":"…","password":"…"}` con AES-256-GCM (IV aleatorio de 12 bytes, AAD = `challengeId`).
3. `POST /auth/login` con `{"encrypted":{"challengeId","publicKey","iv","ciphertext"}}` (Base64 estándar; el ciphertext incluye la etiqueta GCM de 16 bytes).

El desafío se consume en el primer uso: reenviar la misma petición devuelve 400 `LOGIN_CHALLENGE_INVALID`. Un cuerpo `{"username","password"}` en texto plano devuelve 400 `LOGIN_ENCRYPTION_REQUIRED`. Referencias: `src/security/loginCrypto.js` (navegador) y `encrypted_credentials` en `scripts/smoke_test_api.py` (Python).

El controlador actual no expone endpoint de refresh token. El TTL JWT por defecto es 60 minutos, configurable; usar expiresAt y volver a autenticar cuando la sesión expire.

## 2. Catálogo, atributos, unidades, variantes e imágenes

Permisos principales: catalog:read para lecturas y catalog:manage para comandos.

| Método | Ruta | Finalidad y condiciones |
|---|---|---|
| GET | /catalog/categories | Lista paginada; active opcional |
| GET | /catalog/categories/{id} | Detalle |
| POST | /catalog/categories | Crear |
| PUT | /catalog/categories/{id} | Actualizar |
| POST | /catalog/categories/{id}/activate | Activar |
| POST | /catalog/categories/{id}/deactivate | Desactivar |
| DELETE | /catalog/categories/{id} | Borrado físico si no hay referencias |
| GET | /catalog/categories/{id}/attributes | Esquema de propiedades |
| PUT | /catalog/categories/{id}/attributes | Guardar esquema; `key` opcional, se deriva de `displayName` |
| GET | /catalog/attribute-presets | Tipos de dato, propiedades sugeridas y plantillas de categoría |
| GET | /catalog/attribute-presets/suggestion?text= | Plantilla sugerida por nombre y categoría parecida existente con sus propiedades |
| GET | /catalog/product-kinds | Tipos de producto con etiqueta, descripción y ejemplos para el usuario |
| GET | /catalog/products | Listado paginado y filtros implementados en controlador |
| GET | /catalog/products/{id} | Detalle; campos de costo condicionados por permisos |
| POST | /catalog/products | Crear; `sku` opcional (se genera uno único si se omite) |
| PUT | /catalog/products/{id} | Actualizar |
| POST | /catalog/products/{id}/activate | Activar |
| POST | /catalog/products/{id}/deactivate | Desactivar |
| DELETE | /catalog/products/{id} | Borrado físico solo si no tiene existencia ni referencias |
| GET | /catalog/units | Unidades; active opcional; incluye `dimensionLabel` y `allowsFractions` |
| GET | /catalog/unit-dimensions | Formas de medir (cantidad, peso, volumen…) con etiqueta y ejemplos |
| POST | /catalog/units | Crear |
| PUT | /catalog/units/{id} | Actualizar |
| DELETE | /catalog/units/{id} | Borrar si no hay referencias |
| GET | /catalog/unit-conversions | Listar conversiones |
| POST | /catalog/unit-conversions | Crear conversión |
| GET | /catalog/products/{productId}/variants | Listar variantes |
| POST | /catalog/products/{productId}/variants | Crear variante |
| GET | /catalog/variants/{variantId} | Detalle de variante |
| PUT/PATCH | /catalog/variants/{variantId} | Actualizar variante |
| POST | /catalog/variants/{variantId}/deactivate | Desactivar variante |
| GET | /catalog/products/{productId}/images | Listar metadatos de imagen |
| POST | /catalog/products/{productId}/images | Registrar imagen/URL |
| PATCH | /catalog/products/{productId}/images/{imageId} | Editar metadatos |
| DELETE | /catalog/products/{productId}/images/{imageId} | Retirar relación |

Ejemplo representativo de ProductDtos.WriteRequest:

~~~json
{
  "sku": "SKU-001",
  "name": "Producto de ejemplo",
  "description": "Descripción",
  "categoryId": "UUID-DE-CATEGORIA",
  "costPrice": 4.00,
  "salePrice": 8.50,
  "currency": "USD",
  "kind": "STOCKED_GOOD",
  "unitOfMeasureId": "UUID-DE-UNIDAD",
  "attributes": {"color": "negro", "peso": 1.25}
}
~~~

SKU admite letras, números, punto, guion y guion bajo, longitud máxima 64. CategoryId es obligatorio. Importes no negativos con dos decimales. Attributes debe cumplir el esquema activo de la categoría. Una variante tiene SKU, código de barras, combinación de opciones y precio; comparte stock/costo del producto padre y **no** dispone de saldo independiente.

## 3. Catálogo público

| Método | Ruta | Uso |
|---|---|---|
| GET | /public/catalog/products | Catálogo público paginado |
| GET | /public/catalog/products/{identifier} | Ficha pública |
| GET | /public/catalog/categories | Categorías activas/publicables |

Las proyecciones públicas no deben exponer costo o margen.

## 4. Proveedores, clientes, almacenes y ubicaciones

| Método | Ruta | Acceso |
|---|---|---|
| GET | /suppliers y /suppliers/{id} | purchasing:read |
| POST/PUT | /suppliers y /suppliers/{id} | partners:manage |
| POST | /suppliers/{id}/activate | partners:manage |
| POST | /suppliers/{id}/deactivate | partners:manage |
| DELETE | /suppliers/{id} | partners:manage; reglas de historial |
| GET | /customers y /customers/{id} | partners:read |
| POST/PUT | /customers y /customers/{id} | customers:manage |
| POST | /customers/{id}/activate | customers:manage |
| POST | /customers/{id}/deactivate | customers:manage |
| DELETE | /customers/{id} | customers:manage; reglas de historial |
| GET | /warehouses y /warehouses/{id} | inventory:read |
| POST/PUT | /warehouses y /warehouses/{id} | inventory:manage |
| POST | /warehouses/{id}/activate | inventory:manage |
| POST | /warehouses/{id}/deactivate | inventory:manage |
| DELETE | /warehouses/{id} | inventory:manage; ausencia de referencias |
| GET | /warehouses/{id}/locations | inventory:read |
| POST | /warehouses/{id}/locations | inventory:manage |

La aplicación no ofrece picking multi-bin operativo: la restricción de saldo actual mantiene una sola existencia por producto/almacén. La estructura de ubicación no debe confundirse con soporte completo de varias ubicaciones de stock por SKU.

## 5. Inventario, conteos, reposición y transferencias

Permisos principales: inventory:read para lectura, inventory:manage para escribir. Auditoría puede leer algunos listados.

| Método | Ruta | Uso |
|---|---|---|
| GET | /inventory/balances | Lista paginada; productId y warehouseId opcionales |
| GET | /inventory/balances/{productId}/{warehouseId} | Saldo de producto/almacén |
| GET | /inventory/availability | Disponibilidad por productId o SKU y filtros del controlador |
| GET | /inventory/movements | Movimientos/kardex paginado |
| GET | /inventory/movements/{id} | Detalle y valoración |
| POST | /inventory/adjustments | Ajuste; direction INCREASE/DECREASE, quantity y reason |
| GET | /inventory/counts | Lista de conteos |
| GET | /inventory/counts/{id} | Detalle |
| POST | /inventory/counts | Crear conteo |
| POST | /inventory/counts/{id}/post | Contabilizar |
| POST | /inventory/counts/{id}/cancel | Cancelar |
| GET | /inventory/reorder-rules | Reglas de stock mínimo/máximo |
| POST | /inventory/reorder-rules | Crear regla |
| PUT | /inventory/reorder-rules/{id} | Editar regla |
| POST | /inventory/reorder-rules/{id}/deactivate | Desactivar |
| GET | /inventory/replenishment-suggestions | Sugerencias de reposición |
| GET | /inventory/transfers | Lista paginada de transferencias |
| GET | /inventory/transfers/{id} | Detalle |
| POST | /inventory/transfers | Crear transferencia |
| POST | /inventory/transfers/{id}/dispatch | Despachar y descontar origen |
| POST | /inventory/transfers/{id}/receive | Recibir en destino |
| POST | /inventory/transfers/{id}/cancel | Cancelar según estado; compensar cuando corresponda |
| POST | /inventory/transfers/{id}/reverse | Revertir tras recepción con motivo |

Ejemplo de ajuste, sujeto a DTO actual:

~~~json
{
  "productId": "UUID-PRODUCTO",
  "warehouseId": "UUID-ALMACEN",
  "direction": "INCREASE",
  "quantity": 3,
  "reason": "Corrección de conteo aprobada",
  "unitCost": 4.2500
}
~~~

El costo es opcional en entradas. En salidas no exceder stock disponible. No actualizar onHand directamente. La comprobación visual de disponibilidad no reemplaza el control atómico del comando.

## 6. Compras, recepciones y devoluciones a proveedor

Permisos: purchasing:read para consultar; purchasing:manage para comandos; purchasing:approve para aprobar. Cuando PURCHASING_REQUIRE_DISTINCT_APPROVER=true, el aprobador debe ser distinto del usuario que creó/envió la orden.

| Método | Ruta | Uso |
|---|---|---|
| GET | /purchase-orders | Lista |
| GET | /purchase-orders/{id} | Detalle |
| POST | /purchase-orders | Crear borrador |
| POST | /purchase-orders/{id}/submit | Enviar |
| POST | /purchase-orders/{id}/approve | Aprobar |
| POST | /purchase-orders/{id}/cancel | Cancelar si el estado lo permite |
| POST | /purchase-orders/{id}/receipts | Registrar recepción parcial/completa |
| GET | /goods-receipts | Lista de recepciones |
| GET | /goods-receipts/{id} | Detalle de recepción |
| GET | /purchase-returns | Lista de devoluciones a proveedor |
| GET | /purchase-returns/{id} | Detalle |
| POST | /purchase-returns | Devolver cantidades de recepción contabilizada |

Creación de compra, según PurchaseDtos.CreateOrderRequest:

~~~json
{
  "supplierId": "UUID-PROVEEDOR",
  "warehouseId": "UUID-ALMACEN",
  "notes": "Reposición",
  "items": [
    {"productId": "UUID-PRODUCTO", "quantity": 10, "unitCost": 4.00}
  ]
}
~~~

Recepción:

~~~json
{
  "items": [
    {"purchaseOrderLineId": "UUID-LINEA-ORDEN", "quantity": 10}
  ]
}
~~~

La orden por sí sola no incrementa las existencias. El stock cambia al contabilizar la recepción. Las recepciones parciales deben respetar cantidad pedida/recibida; las devoluciones no exceden las unidades recibidas que aún no se han devuelto.

## 7. Ventas y devoluciones de cliente

Permisos: sales:read, sales:manage y sales:cancel para aprobar cancelaciones.

| Método | Ruta | Uso |
|---|---|---|
| GET | /sales | Lista |
| GET | /sales/{id} | Detalle |
| POST | /sales | Venta transaccional multítem |
| POST | /sales/{id}/request-cancellation | Solicitar anulación con motivo |
| POST | /sales/{id}/approve-cancellation | Aprobar; aprobador distinto del solicitante |
| GET | /sales-returns | Lista |
| GET | /sales-returns/{id} | Detalle |
| POST | /sales-returns | Devolver productos de una venta |

Venta representativa según SaleDtos.CreateRequest:

~~~json
{
  "warehouseId": "UUID-ALMACEN",
  "customerName": "Consumidor final",
  "customerPhone": "",
  "channel": "POS",
  "items": [
    {"productId": "UUID-PRODUCTO", "quantity": 2}
  ]
}
~~~

El precio vigente lo decide el backend desde el catálogo. El consumidor no debe enviar un precio autoritativo. El backend vuelve a validar stock dentro de la transacción. El destino de devolución debe ajustarse al DTO actual y puede indicar RESTOCK o SCRAP según regla.

## 8. Reportes y costeo

Todas las rutas /reports exigen reports:financial.

| Método | Ruta | Parámetros principales |
|---|---|---|
| GET | /reports/inventory-balance | from, to, warehouseId requeridos; productId y zone opcionales; page/size |
| GET | /reports/valuation | warehouseId, productId, includeEmpty y page/size |
| GET | /reports/sales | from, to; warehouseId y zone opcionales |
| GET | /reports/purchases | from, to; warehouseId, supplierId y zone opcionales |
| GET | /reports/kardex | productId, warehouseId, from, to; zone opcional |
| GET | /reports/low-stock | warehouseId opcional |
| GET | /costing/policy | reports:financial o costing:manage |
| PUT | /costing/policy | costing:manage |

Ejemplo de balance:

~~~bash
curl -G 'http://localhost:8080/api/v1/reports/inventory-balance' \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'from=2026-10-01' \
  --data-urlencode 'to=2026-10-26' \
  --data-urlencode 'warehouseId=UUID-ALMACEN' \
  --data-urlencode 'productId=UUID-PRODUCTO' \
  --data-urlencode 'zone=America/Guayaquil' \
  --data-urlencode 'page=0' --data-urlencode 'size=50'
~~~

El balance entrega rango, zona, generatedAt, paginación e items con saldos y valores de apertura/cierre, compras, ventas netas, costo de ventas, utilidad bruta y margen según DTO. El cierre histórico no tiene por qué coincidir con la existencia actual, que incluye movimientos posteriores. El periodo se limita a 366 días y el size efectivo a 100. En V14, las capas iniciales para existencia anterior se estiman con product.costPrice; ello no reconstruye el costo histórico real. Cambiar política tras movimientos valorizados requiere migración planificada.

## 9. WhatsApp/n8n

Enviar X-Integration-Key desde un secreto del workflow. Nunca tomar identidad o permisos de un texto generado por un modelo; el vínculo con el cliente debe derivarse del canal verificado.

| Método | Ruta |
|---|---|
| GET | /integrations/whatsapp/catalog/products |
| GET | /integrations/whatsapp/catalog/products/{identifier} |
| POST | /integrations/whatsapp/carts |
| GET | /integrations/whatsapp/carts/{cartId} |
| PUT | /integrations/whatsapp/carts/{cartId}/items/{productId} |
| DELETE | /integrations/whatsapp/carts/{cartId}/items/{productId} |
| POST | /integrations/whatsapp/carts/{cartId}/quotes |
| GET | /integrations/whatsapp/quotes/{quoteId} |
| POST | /integrations/whatsapp/orders |
| GET | /integrations/whatsapp/orders/{orderId} |
| POST | /integrations/whatsapp/orders/{orderId}/cancel |
| GET | /whatsapp-orders | Backoffice: sales:read |
| GET | /whatsapp-orders/{id} | Backoffice: sales:read |
| POST | /whatsapp-orders/{id}/confirm-payment | Backoffice: sales:manage; confirmación humana |

La cotización vence y el pedido vuelve a validar condiciones. La confirmación de pago es manual: no existe pasarela, conciliación o reembolso automático.

## 10. Auditoría, Outbox, GraphQL y Actuator

| Método | Ruta | Acceso |
|---|---|---|
| GET | /audit-events | audit:read |
| GET | /audit-events/{id} | audit:read |
| GET | /operations/outbox | operations:manage |
| GET | /operations/outbox/stats | operations:manage |
| GET | /operations/outbox/{id} | operations:manage |
| POST | /operations/outbox/{id}/retry | operations:manage |
| POST | /graphql | Consultas de catálogo con límites de profundidad/complejidad |
| GET | /actuator/health | Público, sin detalle sensible |
| GET | /actuator/health/readiness | Readiness probe |
| GET | /actuator/health/liveness | Liveness probe |
| GET | /actuator/info | technical:read |
| GET | /actuator/metrics y /actuator/metrics/{name} | technical:read u operations:manage |
| GET | /v3/api-docs | OpenAPI con SWAGGER_ENABLED |
| GET | /swagger-ui.html | Swagger UI con SWAGGER_ENABLED |

El Outbox busca entrega al menos una vez. Consumidores futuros deben deduplicar por eventId; consumidores/proyecciones Kafka no son parte del alcance actual.

## 11. Paginación, errores e idempotencia

Los listados paginados normalmente aceptan page (base cero) y size. Usar size <=100 y solo campos de sort admitidos por el controlador. Balance requiere warehouseId, mientras que kardex requiere productId y warehouseId.

Errores comunes:
- 400: JSON o parámetro inválido; corregir payload.
- 401: token ausente/inválido/vencido; volver a login.
- 403: autoridad insuficiente; no reintentar.
- 404: UUID o recurso inexistente.
- 405: método no admitido.
- 409: conflicto de estado/unicidad/idempotencia; leer el estado antes de repetir.
- 413/415: cuerpo grande o content-type incorrecto.
- 422: regla de negocio; corregir entrada.
- 429: respetar Retry-After si existe.
- 5xx o falla de red después de enviar comando: resultado ambiguo; consultar recurso, movimiento o saldo antes de intentar otra vez.

El handler común usa application/problem+json, status, detalle seguro, code cuando aplique y requestId. No retornar stack traces ni SQL. Nunca registrar token, contraseña o X-Integration-Key.

Para comandos compatibles, usar Idempotency-Key UUID por operación lógica. Reintentar con la misma clave y cuerpo idéntico. Una clave no debe compartirse entre operaciones diferentes ni sustituye validación, transacción o autorización. El cliente web mantiene sus claves temporales en memoria, por lo que tras una recarga hay que comprobar el resultado antes de reemitir un comando.

## 12. Roles y permisos

Roles operativos documentados: SUPER_ADMIN, AUDITOR, MANAGER_ALMACEN, VENDEDOR y SOPORTE_TI. Autoridades del código incluyen security:manage, audit:read, catalog:read, catalog:manage, inventory:read, inventory:manage, purchasing:read, purchasing:manage, purchasing:approve, partners:read, partners:manage, customers:manage, sales:read, sales:manage, sales:cancel, reports:financial, costing:manage, operations:manage y technical:read.

Consultar /auth/me para permisos efectivos y RoleMatrixApiTest / docs/RESULTADO_AUDITORIA_EJECUCION.md §6 como matriz histórica. La configuración y los permisos de PostgreSQL de cada despliegue son autoritativos. reports:financial solo debe darse al rol financiero autorizado.

## 13. No asumir que existe

No se implementan en esta versión: refresh token, pasarela y reembolsos, impuestos fiscales completos/SRI, multiempresa, stock independiente por variante, multi-bin operacional, carga binaria gestionada, consumidores/proyecciones Kafka ni rate limiting distribuido.
