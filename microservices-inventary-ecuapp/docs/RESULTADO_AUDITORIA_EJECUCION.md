> **Adenda de vigencia — 2026-10-27:** las pruebas y el runtime descritos en el informe histórico de 2026-10-23 siguen referidos al snapshot de entonces. La validación actual de backend pasó Maven verify en PostgreSQL 16, la configuración de Compose normal/Kafka y el smoke test desde un volumen PostgreSQL vacío en [backend-ci 40504351485](https://github.com/JhonnyMinian92/inventary/actions/runs/40504351485), SHA `ac60c93a97c90c6384262880bf970dea813a6dce`. El frontend pasó `npm test` y `npm run build` en [Frontend Build 40504502526](https://github.com/JhonnyMinian92/inventary/actions/runs/40504502526), SHA `26dc33cd209133be037beb13fb7990933cde394d`. El workflow backend más reciente de limpieza de Compose está en [backend-ci 40504639896](https://github.com/JhonnyMinian92/inventary/actions/runs/40504639896); Maven y la configuración de Compose están verdes y el smoke test está en curso al publicar esta adenda. No reutilizar números de pruebas del informe histórico como si fueran el recuento del HEAD actual.

> **Vigencia de esta evidencia:** registro histórico fechado 2026-10-23, no certificación del HEAD de master del 2026-10-26. El propio informe indica que la copia local probada no era un repositorio Git y no se registraron SHA inicial/final. Las 76 pruebas, el smoke HTTP y PostgreSQL 18.6 describen aquella ejecución concreta; no sustituyen validación actual en PostgreSQL 16, Docker Compose ni CI sobre el SHA a publicar. Que el frontend estuviera fuera de alcance en aquella corrida no significa que el frontend actual no exista. Consultar [24](24-estado-actual-repositorio.md) y [28](28-plan-cierre-y-validacion.md) para estado vivo y próximos pasos.

# Resultado de la ejecución de la auditoría técnica — backend Inventary

> Documento de evidencia solicitado en `AUDITORIA_TECNICA_PENDIENTES.md` §12.
> Alcance de esta entrega: **solo backend** (decisión del responsable del proyecto). El frontend Angular queda fuera de alcance de forma explícita.
> Fecha de ejecución: 2026-10-23.

## 1. Entorno y punto de partida

| Elemento | Valor |
|---|---|
| Código | Copia de trabajo local `microservices-inventary-ecuapp/` (snapshot P12). **No es un repositorio git**: no hay SHA inicial/final que registrar; el estado entregado es el de esta carpeta. |
| Java | Oracle JDK 21.0.5 |
| Maven | 3.9.11 |
| Spring Boot | 3.5.6 (Hibernate 6.6, Spring Security 6.5, Flyway 11) |
| PostgreSQL | 18.6 local (Windows). Docker Compose define PostgreSQL 16. |
| Docker | **No disponible en el equipo**; no se construyó la imagen ni se levantó Compose (bloqueado por entorno, ver §8). |

Bases usadas:

| Base | Uso |
|---|---|
| `inventary` | Base indicada por el responsable. Se migró V1→V14 y se ejecutó la prueba HTTP extremo a extremo de todas las APIs (§4). Contiene ahora datos de prueba con prefijo `SMK`. |
| `inventary_test` | Suite automatizada. Tenía V1–V13 aplicadas: sirvió para validar la **actualización** a V14 con datos existentes. |
| `inventary_fresh` | Base vacía creada para validar la migración **desde cero** V1→V14 y la suite completa. |

### Estado inicial encontrado

- `mvn -B -DskipTests clean compile` → **34 errores de compilación** en 3 archivos (`RoleManagementService`, `SecurityConfig`, `SalesReturnService`): métodos de repositorio inexistentes, constructor ausente en `AppRole`, una API de JWT inexistente (`Jwt.getClaimAsInt`), imports faltantes.
- Los tests existentes tampoco compilaban (constructores y firmas desactualizados).
- Conclusión: el backend **no compilaba ni arrancaba** en el estado entregado (P1-01 confirmado).

## 2. Comandos ejecutados y resultado final

```text
# Suite completa sobre base con V1–V13 previas (upgrade a V14)
TEST_DB_USERNAME=postgres TEST_DB_PASSWORD=*** mvn -B clean verify
  -> Tests run: 76, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

# Suite completa sobre base vacía
TEST_DATASOURCE_URL=jdbc:postgresql://localhost:5432/inventary_fresh ... mvn -B verify
  -> Flyway: "Successfully applied 14 migrations" — Tests run: 76, Failures: 0 — BUILD SUCCESS

# API real contra la base `inventary` + prueba HTTP de todas las operaciones
java -jar target/inventary-0.1.0-SNAPSHOT.jar   (perfil dev, SWAGGER_ENABLED=true)
python scripts/smoke_test_api.py --base-url http://localhost:8080 --admin-user admin ...
  -> HTTP calls: 192  checks: 19  failures: 0
  -> Documented operations: 138  covered: 138  missing: 0
  -> Log del servidor durante la corrida: 0 líneas ERROR; único WARN: Flyway aún no certifica PostgreSQL 18
```

Evidencia de Flyway (base vacía):

| Versión | Descripción | Éxito |
|---|---|---|
| 1–13 | Migraciones históricas sin modificar | sí |
| 14 | `costing roles and operational completion` | sí |

Hibernate `ddl-auto=validate` termina sin diferencias en las tres bases. `InventaryApplicationTests` lo verifica y comprueba que la última versión aplicada es la 14 y que no hay migraciones fallidas.

## 3. Suite de pruebas

76 pruebas, todas contra PostgreSQL real (nunca H2), con filtros, seguridad y JWT reales (`MockMvc` sobre el contexto completo y tokens emitidos por `POST /api/v1/auth/login`).

| Clase | Pruebas | Qué cubre |
|---|---:|---|
| `SecurityContractApiTest` | 10 | 401 (sin token, malformado, firma ajena, expirado, audiencia incorrecta), revocación por desactivación/reset/logout, 403 sin efectos laterales y auditado, `requestId`, 400/404/405/413/415, rate limit 429 con `Retry-After` y auditoría, CORS permitido/denegado, cabeceras de seguridad, health/readiness/liveness, info y métricas protegidas, OpenAPI. |
| `AuthAdminApiTest` | 4 | `me` con permisos efectivos, cambio de contraseña, usuarios (unicidad, política, roles), roles personalizados asignables con efecto inmediato, desactivación de rol, **último SUPER_ADMIN secuencial y concurrente**. |
| `CatalogApiTest` | 9 | Categorías, unidades y conversiones, plantillas de atributos versionadas y validación, SKU inmutable, ocultamiento de costo, tipo/unidad inmutables con stock, variantes, catálogo público, imágenes, GraphQL (auth, profundidad y complejidad). |
| `InventoryApiTest` | 7 | Almacenes y ubicaciones, ajustes idempotentes y concurrentes, saldos, disponibilidad, kardex filtrado y detalle valorizado, conteos con snapshot obsoleto, reposición, **triggers append-only en PostgreSQL**. |
| `PurchasingAndCostingApiTest` | 7 | Proveedores, ciclo de OC con segregación y transiciones idempotentes, recepciones parciales, **promedio ponderado y FIFO verificados numéricamente**, política de costeo, devoluciones a proveedor concurrentes. |
| `SalesApiTest` | 5 | Precio de servidor, validaciones, idempotencia, **12 ventas concurrentes por 5 unidades**, reintentos concurrentes con la misma clave, cancelación con aprobador distinto, devoluciones RESTOCK/SCRAP concurrentes. |
| `TransfersApiTest` | 4 | Despacho/recepción con costo trasladado, cancelación en tránsito y reversión compensadas una sola vez. |
| `WhatsAppApiTest` | 5 | Clave de integración, carrito/cotización/pedido/reserva, confirmación de pago → venta, cancelación y expiración con liberación única, dos clientes compitiendo por las últimas unidades. |
| `ReportsApiTest` | 2 | Escenario de `PENDIENTES.md` con todas las cifras, validación de parámetros y permisos. |
| `AuditAndOutboxApiTest` | 3 | Auditoría filtrable sin secretos, relevo Outbox (fallo, backoff, FAILED, redrive), lease vencido y relays concurrentes sin duplicados. |
| `RoleMatrixApiTest` | 1 | Matriz de 20 operaciones × 5 roles (§6). |
| `DomainRulesTest`, `StockBalanceTest`, `AuditControllerTest`, `InventoryBalanceReportServiceTest` | 18 | Unitarias: política de contraseñas, paginación, validador de atributos, periodos con zona horaria, backoff del Outbox, saldos y reservas. |
| `InventaryApplicationTests` | 1 | Contexto, Flyway V14 y validación de mapeos. |

Los tests de servicio previos (`ProcurementIntegrationTest`, `SalesReturnIntegrationTest`, `SaleCancellationIntegrationTest`, `StockTransferIntegrationTest`, `WhatsAppCommerceIntegrationTest`, `InventoryIntegrationTest`) no compilaban. Se **reemplazaron** por pruebas HTTP que conservan cada escenario y aserción original (recepciones parciales e idempotentes, devoluciones no excedentes, cancelación con aprobador distinto, transferencias compensadas una vez, reserva y liberación WhatsApp) y los amplían.

### 3.1 Pruebas de concurrencia e invariantes

| Escenario | Resultado |
|---|---|
| 12 ventas simultáneas de 1 u. con 5 u. en stock | Exactamente 5 éxitos (201), 7 rechazos (422); saldo final 0; suma de movimientos SALE = 5. |
| 5 reintentos simultáneos de la misma venta (misma clave) | 5 respuestas 201 con el mismo id; una sola venta; stock descontado una vez. |
| 6 ajustes simultáneos con la misma `Idempotency-Key` | 1 solo movimiento en el ledger. |
| 2 devoluciones simultáneas de las 2 u. restantes de una línea | 1 éxito; total devuelto = cantidad vendida. |
| 4 devoluciones a proveedor simultáneas de 3 u. sobre 4 recibidas | 1 éxito; stock 1; total devuelto 3. |
| 2 administradores se degradan mutuamente siendo los últimos SUPER_ADMIN | 1 éxito, el otro rechazado; siempre queda 1 SUPER_ADMIN activo. |
| 2 pedidos WhatsApp simultáneos de 2 u. con 3 u. en stock | 1 reserva; reservado 2, disponible 1. |
| 3 relays Outbox simultáneos + lease vencido | Cada evento enviado exactamente una vez; el evento con lease vencido se recupera. |

### 3.2 Verificación del costeo (P1-02)

| Caso | Esperado | Obtenido |
|---|---|---|
| Promedio ponderado: recibir 10 a 10,00 y 10 a 14,00; vender 5 | Costo unitario 12,00 (no el de catálogo 9,99); valor restante 180,00 | 12,00 / 180,00 |
| Cancelación de esa venta | Reingreso a 12,00; valor 240,00 | 240,00 |
| FIFO: 10 a 10,00 + 10 a 14,00; vender 12 | 10×10 + 2×14 = 128,00 en dos entradas de valoración | 128,0000, 2 entradas |
| FIFO: devolver 3 u. de la segunda recepción | Sale a 14,00 de su capa; quedan 5×14 = 70,00 | 14,00 / 70,00 |
| Escenario PENDIENTES (promedio) | Ver §5 | Coincide |

## 4. Prueba HTTP extremo a extremo sobre la base `inventary`

`scripts/smoke_test_api.py` (Python estándar, sin dependencias) recorre un ciclo de negocio completo con HTTP real y compara lo ejercitado contra `/v3/api-docs`. Es la colección reproducible pedida en §6.4.

Resultado de la corrida final (código definitivo): **192 llamadas, 19 verificaciones de negocio, 0 fallos, 138 de 138 operaciones documentadas cubiertas**, más `/graphql`, `/actuator/health`, `/actuator/info` y `/actuator/metrics`.

El número de llamadas varía en una unidad entre corridas cuando el login recibe 429 y el script reintenta tras `Retry-After`. Fila del reporte consolidado obtenida en una corrida (producto `SMK-CAM-…`):
`openingStock 0, purchasedQuantity 24, purchaseAmount 110,00, soldQuantity 11, salesAmount 110,00, closingStock 14 = currentStock 14, closingValue 68,35, costOfSales 46,65, grossProfit 63,35`.
Comprobación manual del costo de ventas con promedio ponderado: 40,00 (10 a 4,00) + 10,00 − 10,00 (venta cancelada) − 8,00 (devolución a 4,00) + 14,65 (3 u. a 4,8824) = 46,65.

### 4.1 Inventario de operaciones y resultado

Todas las operaciones se ejercitaron con el código esperado (columna "2xx"); los rechazos listados se verificaron en la suite automatizada.

| Área | Operaciones | 2xx verificado | Rechazos verificados |
|---|---|---|---|
| Autenticación | `POST /auth/login`, `GET /auth/me`, `POST /auth/logout`, `PUT /auth/me/password` | 200/204 | 400, 401 genérico, 422 política, 429 |
| Administración | `GET/POST /admin/users`, `GET/PATCH /admin/users/{id}`, `PUT /admin/users/{id}/password`, `GET/POST /admin/roles`, `GET/PATCH /admin/roles/{code}`, `PUT /admin/roles/{code}/permissions`, `GET /admin/permissions` | 200/201/204 | 403, 404, 409, 422 (último admin, `security:manage`, reservado) |
| Categorías y atributos | `GET/POST /catalog/categories`, `GET/PUT /catalog/categories/{id}`, `POST …/activate`, `POST …/deactivate`, `GET/PUT …/attributes` | 200/201/204 | 400, 403, 409, 422 |
| Unidades | `GET/POST /catalog/units`, `PUT /catalog/units/{id}`, `GET/POST /catalog/unit-conversions` | 200/201 | 409, 422 |
| Productos | `GET/POST /catalog/products`, `GET/PUT /catalog/products/{id}`, `POST …/activate`, `POST …/deactivate` | 200/201/204 | 400, 403, 409 (SKU, tipo, unidad), 422 (atributos) |
| Imágenes | `GET/POST …/images`, `PATCH/DELETE …/images/{imageId}` | 200/201/204 | 400 (HTTP), 403, 404, 409 |
| Variantes | `GET/POST /catalog/products/{productId}/variants`, `GET/PUT/PATCH /catalog/variants/{variantId}`, `POST …/deactivate` | 200/201/204 | 404, 409, 422 |
| Catálogo público | `GET /public/catalog/products`, `GET /public/catalog/products/{identifier}`, `GET /public/catalog/categories` | 200 | 400 (sort), 401 (escritura), 404 (inactivo) |
| Almacenes | `GET/POST /warehouses`, `GET/PUT /warehouses/{id}`, `POST …/activate`, `POST …/deactivate`, `GET/POST …/locations` | 200/201/204 | 403, 409 (código, stock), 422 |
| Inventario | `POST /inventory/adjustments`, `GET /inventory/balances`, `GET /inventory/balances/{productId}/{warehouseId}`, `GET /inventory/availability`, `GET /inventory/movements`, `GET /inventory/movements/{id}` | 200/201 | 400, 403, 404, 409 (clave), 422 (stock) |
| Conteos | `GET/POST /inventory/counts`, `GET /inventory/counts/{id}`, `POST …/post`, `POST …/cancel` | 200/201 | 403, 409 (snapshot obsoleto), 422 |
| Reposición | `GET/POST /inventory/reorder-rules`, `PUT …/{id}`, `POST …/{id}/deactivate`, `GET /inventory/replenishment-suggestions` | 200/201/204 | 404, 409, 422 |
| Transferencias | `GET/POST /inventory/transfers`, `GET …/{id}`, `POST …/dispatch`, `…/receive`, `…/cancel`, `…/reverse` | 200/201 | 400, 403, 404, 409, 422 |
| Proveedores y clientes | `GET/POST /suppliers`, `GET/PUT /suppliers/{id}`, `POST …/activate`, `…/deactivate` y equivalentes en `/customers` | 200/201/204 | 400, 403, 404, 409, 422 |
| Compras | `GET/POST /purchase-orders`, `GET …/{id}`, `POST …/submit`, `…/approve`, `…/cancel`, `…/receipts`, `GET /goods-receipts`, `GET /goods-receipts/{id}` | 200/201 | 403, 409 (segregación, estado, clave), 422 |
| Devolución a proveedor | `GET/POST /purchase-returns`, `GET /purchase-returns/{id}` | 200/201 | 422 (excedente, concurrencia) |
| Ventas | `GET/POST /sales`, `GET /sales/{id}`, `POST …/request-cancellation`, `POST …/approve-cancellation` | 200/201 | 400, 403, 404, 409, 422 |
| Devolución de venta | `GET/POST /sales-returns`, `GET /sales-returns/{id}` | 200/201 | 422 |
| WhatsApp (n8n) | `GET …/catalog/products`, `GET …/catalog/products/{identifier}`, `POST …/carts`, `GET …/carts/{cartId}`, `PUT/DELETE …/items/{productId}`, `POST …/quotes`, `GET …/quotes/{quoteId}`, `POST …/orders`, `GET …/orders/{orderId}`, `POST …/cancel` | 200/201 | 400, 401, 404 (`customerRef`), 409, 422 |
| Pedidos WhatsApp (personal) | `GET /whatsapp-orders`, `GET /whatsapp-orders/{id}`, `POST …/confirm-payment` | 200 | 403, 409 |
| Reportes | `GET /reports/inventory-balance`, `/valuation`, `/sales`, `/purchases`, `/kardex`, `/low-stock` | 200 | 400, 403, 404 |
| Costeo | `GET/PUT /costing/policy` | 200 | 400, 403, 409 |
| Auditoría | `GET /audit-events`, `GET /audit-events/{id}` | 200 | 400, 403, 405 |
| Operaciones Outbox | `GET /operations/outbox`, `GET …/stats`, `GET …/{id}`, `POST …/{id}/retry` | 200 | 400, 403, 409 |
| Otros | `POST /graphql`, `GET /actuator/health(/readiness,/liveness)`, `GET /actuator/info`, `GET /actuator/metrics` | 200 | 401, 403 |

Prefijo omitido en la tabla: `/api/v1`.

## 5. Escenario de `PENDIENTES.md`

Compra de 10 → venta de todo → recompra de 15 del **mismo** producto → devolución de 2 por el cliente (`ReportsApiTest`):

| Campo de `/reports/inventory-balance` | Valor |
|---|---|
| openingStock / purchasedQuantity / purchaseAmount | 0 / 25 / 115,00 |
| soldQuantity / salesAmount | 8 / 80,00 (10 vendidas − 2 devueltas) |
| closingStock = currentStock | 17 |
| openingValue / closingValue | 0,00 / 83,00 (15 × 5,00 + 2 × 4,00) |
| costOfSales / grossProfit | 32,00 / 48,00 |
| Mismo producto consultado para el día anterior | closingStock 0 (lo de hoy queda fuera), currentStock 17 |

Los reportes de ventas, compras, kardex (saldo acumulado por movimiento) y valoración (conciliada con el saldo físico) devuelven cifras coherentes con la misma operación.

## 6. Matriz RBAC verificada (`RoleMatrixApiTest`)

✔ = autorizado, ✘ = 403.

| Operación | SUPER_ADMIN | AUDITOR | MANAGER_ALMACEN | VENDEDOR | SOPORTE_TI |
|---|:-:|:-:|:-:|:-:|:-:|
| Usuarios y roles | ✔ | ✘ | ✘ | ✘ | ✘ |
| Auditoría | ✔ | ✔ | ✘ | ✘ | ✘ |
| Catálogo (lectura) / GraphQL | ✔ | ✔ | ✔ | ✔ | ✘ |
| Crear categoría | ✔ | ✘ | ✔ | ✘ | ✘ |
| Saldos / almacenes | ✔ | ✔ | ✔ | ✔ | ✘ |
| Kardex / conteos | ✔ | ✔ | ✔ | ✘ | ✘ |
| Órdenes de compra / proveedores | ✔ | ✔ | ✔ | ✘ | ✘ |
| Aprobar orden de compra | ✔ | ✘ | ✘ | ✘ | ✘ |
| Clientes (lectura) | ✔ | ✔ | ✔ | ✔ | ✘ |
| Ventas / pedidos WhatsApp | ✔ | ✔ | ✘ | ✔ | ✘ |
| Aprobar cancelación de venta | ✔ | ✘ | ✘ | ✘ | ✘ |
| Reportes financieros / política de costeo | ✔ | ✘ | ✘ | ✘ | ✘ |
| Operaciones Outbox / `actuator/info` | ✔ | ✘ | ✘ | ✘ | ✔ |

Decisiones explícitas (P2-09, P2-10): la lectura de proveedores pertenece a compras (`purchasing:read`), por eso VENDEDOR no ve proveedores aunque sí clientes (`partners:read`). La aprobación de compras se mantiene solo en SUPER_ADMIN y además exige un aprobador distinto de quien creó o envió la orden (`PURCHASING_REQUIRE_DISTINCT_APPROVER=true`). No se ampliaron permisos de roles existentes; los permisos nuevos `costing:manage` y `operations:manage` se asignaron a SUPER_ADMIN y `operations:manage` también a SOPORTE_TI.

## 7. Estado de cada hallazgo

| Hallazgo | Estado | Resolución y evidencia |
|---|---|---|
| P1-01 Compila, migra y arranca | **RESUELTO** | Corregidos los 34 errores de compilación; V1–V14 en base vacía y en upgrade; `mvn -B verify` 76/76; API en marcha sobre `inventary`. CI manual con PostgreSQL 16 en `.github/workflows/backend-ci.yml` (no ejecutada en GitHub desde este equipo). |
| P1-02 Costeo FIFO/promedio | **RESUELTO** | `InventoryCostingService` integrado en ajustes, conteos, recepciones, devoluciones a proveedor, ventas, cancelaciones, devoluciones de cliente, transferencias (despacho, recepción, cancelación, reversión) y confirmación de pedidos WhatsApp. Capas FIFO, estado de promedio (`inventory_cost_state`, V14) y ledger de valoración append-only. Cambio de método bloqueado tras existir movimientos valorizados (principio de uniformidad). §3.2. |
| P1-03 Cobertura HTTP/RBAC | **RESUELTO** | 61 pruebas HTTP con JWT real + matriz de roles + prueba E2E de 138/138 operaciones. |
| P1-04 401/403 RFC 9457 | **RESUELTO** | Además del `AuthenticationEntryPoint`, se conectó el handler al resource server (los tokens inválidos devolvían 401 sin cuerpo) y se agregó `AccessDeniedException` al `@RestControllerAdvice` (los rechazos de `@PreAuthorize` devolvían **500**). 401/403/413/429 comparten formato, `code` y `requestId`. |
| P1-05 Atributos dinámicos, variantes y unidades | **RESUELTO** | APIs de unidades y conversiones, plantillas de atributos versionadas por categoría con validación de tipos/opciones/rangos/obligatorios, atributos JSONB en producto, variantes con SKU/código de barras/combinación únicos y precio propio. Las variantes no llevan stock independiente (el stock y el costo son por producto); decisión documentada. |
| P1-06 Frontend | **NO APLICA** (exclusión del responsable) | Entrega declarada solo backend. |
| P2-01 CORS en Compose | **RESUELTO** (ya estaba en el snapshot) | Verificado preflight permitido/denegado en pruebas. Compose no ejecutado (sin Docker). |
| P2-02 429 y auditoría | **RESUELTO** | 429 RFC 9457 con `Retry-After`, `code`, `requestId` y evento `HTTP_RATE_LIMITED`; límites configurables. |
| P2-03 Rate limit por instancia / proxy | **RESUELTO (alcance local)**; distribuido **ABIERTO** | `FORWARD_HEADERS_STRATEGY=none` por defecto (no se confía en `X-Forwarded-For`); `native` solo detrás de proxy confiable. El límite distribuido entre réplicas debe hacerse en gateway/WAF (documentado en el runbook). |
| P2-04 Sort sin allowlist | **RESUELTO** | `Pages` con allowlist en todos los listados; propiedad inválida → 400; tamaño máximo 100. Se corrigió además un fallo de PostgreSQL con filtros de fecha nulos (500 en kardex, ventas, compras y auditoría). |
| P2-05 Multiubicación | **NO APLICA en esta versión** (exclusión documentada) | Una ubicación operativa (`MAIN`) por producto y almacén; se exponen consulta y alta de ubicaciones para el layout. |
| P2-06 Último SUPER_ADMIN concurrente | **RESUELTO** | Bloqueo de fila dedicado (`security_lock`, V14) que serializa los cambios; prueba concurrente real. |
| P2-07 Matcher de cancelación | **RESUELTO** (ya alineado) | Verificado: solicitud con `sales:manage`, aprobación con `sales:cancel` y actor distinto. |
| P2-08 Outbox con locks durante red | **RESUELTO** | `OutboxRelay`: reclamo con `FOR UPDATE SKIP LOCKED` y lease, envío fuera de transacción, registro del resultado en transacción corta; `last_error`; redrive (`POST /operations/outbox/{id}/retry`); métricas `inventary.outbox.*`. Entrega al menos una vez: los consumidores deben deduplicar por `eventId`. Consumidores Kafka: fuera de alcance. |
| P2-09 Coherencia de permisos | **RESUELTO** | Matriz única en `SecurityConfig`, `@PreAuthorize`, semillas y prueba ejecutable (§6). Se eliminó el matcher huérfano `/api/v1/partners/**`. |
| P2-10 Aprobación de compras | **RESUELTO** | Decisión documentada y probada (§6). |
| P2-11 Impuestos, pagos, SRI | **NO APLICA en esta versión** (exclusión) | Impuesto 0. Se agregó la confirmación manual de pago verificado por personal autorizado, que convierte el pedido WhatsApp en venta; no se simula ni integra ningún proveedor de pagos, ni factura electrónica SRI. |
| P2-12 Medios binarios | **NO APLICA en esta versión** (exclusión) | Solo URL HTTPS; el backend nunca descarga URLs (sin SSRF). Probado. |
| P2-13 Idempotencia sistemática | **RESUELTO** | Tabla en el runbook. Las transiciones de compra son idempotentes por estado (`submit`, `approve`, `cancel` repetidos devuelven el estado actual). |
| P2-14 Auditoría y ledger | **RESUELTO** | `UPDATE`/`DELETE` sobre `stock_movement`, `inventory_valuation_entry` e `inventory_count_line` fallan en PostgreSQL (probado); auditoría de 401/403/429 correlacionada y sin secretos. |
| P3-01 Hexagonal parcial | **ABIERTO (aceptado)** | Monolito modular en transición; solo catálogo usa puertos. No se reescribió el resto. |
| P3-02 Observabilidad | **RESUELTO (básico)** | Probes de readiness/liveness, `/actuator/metrics` protegido, métricas de Outbox y caché. Logs JSON opcionales con `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`. Alertas y backups: runbook. |
| P3-03 Alcance vigente vs visión | **RESUELTO** | §8 y `docs/23-runbook-operacion.md`. |

### Defectos adicionales encontrados y corregidos durante la ejecución

1. `ProductService.deactivateProduct` corría sin transacción (auto-invocación): **la desactivación de productos nunca se guardaba**.
2. Los rechazos de `@PreAuthorize` se convertían en 500 por el `catch-all` del `@RestControllerAdvice`.
3. Los tokens inválidos devolvían 401 sin cuerpo (el entry point no estaba conectado al resource server).
4. Los roles personalizados no podían asignarse a usuarios (CHECK fijo en `app_user.role` y enum en los DTOs).
5. Filtros de fecha opcionales nulos producían 500 en PostgreSQL ("no se pudo determinar el tipo del parámetro").
6. Ruta inexistente o método no permitido devolvían 500.
7. Saldos creados con `save()` simple en recepciones, devoluciones y conteos: dos primeras recepciones simultáneas podían chocar con la clave única; ahora usan `INSERT … ON CONFLICT` + bloqueo.
8. La cotización WhatsApp vencida se marcaba como `EXPIRED` dentro de una transacción que luego se revertía.
9. El listado de ventas cargaba colecciones con paginación en memoria; ahora usa carga por lotes y Hibernate falla ante paginación sobre colecciones.
10. El catálogo público no se invalidaba al editar o desactivar productos o categorías.
11. Mensajes de validación en inglés; se fijó el locale `es`.

## 8. Exclusiones y elementos bloqueados

| Elemento | Estado |
|---|---|
| Frontend Angular | Fuera de alcance por decisión del responsable. |
| Docker / Docker Compose / imagen | **Bloqueado**: Docker no instalado en el equipo. Los archivos se actualizaron pero no se ejecutaron. |
| CI en GitHub | Workflow preparado (manual); no ejecutado desde este equipo. |
| PostgreSQL 16 | La validación se hizo con PostgreSQL 18.6. Flyway advierte que su soporte oficial llega a la versión 17; Compose y CI usan 16. |
| Pagos (proveedor), reembolsos, conciliación | No implementado. Solo confirmación manual de pago verificado. |
| Impuestos y facturación electrónica SRI | No implementado (impuesto 0). No usar los totales como comprobante fiscal. |
| Carga binaria de imágenes | No implementado (solo URL HTTPS). |
| Multiubicación de stock, stock por variante | No implementado; una ubicación por producto/almacén, stock por producto. |
| Consumidores Kafka y proyecciones | No implementado; Outbox con publicador opcional. |
| Multiempresa / tenant | No implementado. |
| Rate limit distribuido | Debe hacerlo el gateway/WAF. |

## 9. Riesgos aceptados

- El cambio de método de costeo solo es posible antes del primer movimiento valorizado. Cambiarlo después requiere una migración contable planificada.
- La moneda es única por documento; los reportes resumen importes sin conversión entre monedas.
- La base `inventary` contiene ahora datos de prueba con prefijo `SMK` y el usuario `admin` creado por el arranque inicial; se recomienda cambiar su contraseña o recrear la base antes de cargar datos reales.
