> **Estado y documentación consolidada (2026-10-27):** comenzar por [estado actual por dominio](docs/24-estado-actual-repositorio.md), [referencia de APIs](docs/25-referencia-apis.md), [arquitectura/patrones realmente presentes](docs/26-arquitectura-y-patrones-actuales.md), [guía de consumo](docs/27-guia-consumo-apis.md) y [plan de validación](docs/28-plan-cierre-y-validacion.md). La CI actual ejecuta `./mvnw verify` con PostgreSQL 16 en push/PR y publica reportes. Los documentos históricos no sustituyen evidencia del SHA actual.
>
# Inventary Backend

Backend de inventario y ventas construido con Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA y PostgreSQL. Las entidades usan UUID, importes `NUMERIC(14,2)`, cantidades `NUMERIC(14,3)`, marcas temporales `TIMESTAMPTZ`, claves foráneas y restricciones explícitas.

## Requisitos y ejecución

- Java 21 y Maven 3.9 o superior.
- PostgreSQL 15 o superior. Docker Compose usa PostgreSQL 16.
- Docker Engine y Docker Compose son opcionales.

### Con PostgreSQL instalado localmente

La URL JDBC por defecto apunta a `jdbc:postgresql://localhost:5432/inventary`. El usuario (`DB_USERNAME`) y la contraseña (`DB_PASSWORD`) son obligatorios por variables de entorno; no hay credenciales de base de datos predeterminadas en la configuración de la aplicación.

PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/inventary"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "local-only-change-this-password"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SWAGGER_ENABLED = "true"
$env:JWT_SECRET = "usa-un-secreto-aleatorio-de-al-menos-32-bytes"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "Cambia-esta-clave-inicial-segura-123!"
mvn -B verify
mvn spring-boot:run
```

Bash:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/inventary'
export DB_USERNAME='postgres'
export DB_PASSWORD='local-only-change-this-password'
export SPRING_PROFILES_ACTIVE='dev'
export SWAGGER_ENABLED='true'
export JWT_SECRET='usa-un-secreto-aleatorio-de-al-menos-32-bytes'
export BOOTSTRAP_ADMIN_USERNAME='admin'
export BOOTSTRAP_ADMIN_PASSWORD='Cambia-esta-clave-inicial-segura-123!'
mvn -B verify
mvn spring-boot:run
```

Ejecuta los comandos Maven desde `microservices-inventary-ecuapp/`. La configuración base no habilita Swagger y no incluye valores predeterminados para `DB_USERNAME`, `DB_PASSWORD` ni `JWT_SECRET`. Para desarrollo, activa explícitamente `SPRING_PROFILES_ACTIVE=dev`; el perfil `prod` mantiene Swagger, OpenAPI y GraphiQL deshabilitados. Para entornos compartidos o producción, proporciona secretos únicos mediante el gestor de secretos del entorno. El archivo `.env` está excluido de Git y nunca debe contener credenciales de producción en un entorno de desarrollo.

### Con Docker Compose

```bash
cd microservices-inventary-ecuapp
cp .env.example .env
# .env.example contiene valores de ejemplo solo para desarrollo; cambia las claves antes de usar datos reales.
# El perfil dev habilita OpenAPI; en entornos compartidos usa SPRING_PROFILES_ACTIVE=prod y SWAGGER_ENABLED=false.
docker compose up --build
```

API: http://localhost:8080  
Swagger/OpenAPI: http://localhost:8080/swagger-ui.html (solo con perfil dev o `SWAGGER_ENABLED=true`)  
Health: http://localhost:8080/actuator/health  
GraphQL: http://localhost:8080/graphql (consulta únicamente datos públicos de catálogo)

### Creación del esquema

Flyway controla las migraciones en `src/main/resources/db/migration/V*__*.sql`. Hibernate se configura con `ddl-auto: validate`: al arrancar verifica el mapeo contra PostgreSQL, pero no modifica ni “adivina” el esquema. No sustituyas Flyway por `ddl-auto=update` en producción. Nunca se ejecuta `flyway clean` automáticamente.

Si la base contiene tablas creadas manualmente o una tabla `flyway_schema_history` ajena a estas migraciones, revisa primero el estado y haz una copia de seguridad; no borres datos para forzar el arranque.

## Autenticación y roles

POST /api/v1/auth/login recibe credenciales y devuelve un JWT. Envíalo como Authorization: Bearer <token>. GET /api/v1/auth/me devuelve la identidad autenticada.

No existen credenciales administrativas predeterminadas. Si la base no tiene usuarios, el arranque exige BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD; el primer usuario recibe SUPER_ADMIN. La contraseña inicial debe tener entre 8 y 15 caracteres e incluir mayúscula, minúscula, número y símbolo. El bootstrap solo actúa con la tabla vacía.

Roles canónicos iniciales: SUPER_ADMIN, AUDITOR, MANAGER_ALMACEN, VENDEDOR y SOPORTE_TI. SUPER_ADMIN puede crear roles personalizados, consultar el catálogo de permisos y asignar permisos existentes. No se pueden modificar los permisos de SUPER_ADMIN desde la API.

- GET/POST /api/v1/admin/users
- GET/PATCH /api/v1/admin/users/{id}
- PUT /api/v1/admin/users/{id}/password
- GET/POST /api/v1/admin/roles
- GET /api/v1/admin/permissions
- GET/PATCH /api/v1/admin/roles/{code}
- PUT /api/v1/admin/roles/{code}/permissions
- POST /api/v1/auth/logout (revoca todos los tokens del usuario)
- PUT /api/v1/auth/me/password (cambio de contraseña propia con la actual)

Los roles personalizados creados por API se asignan a usuarios igual que los canónicos (`role` es el código del rol). Cambiar los permisos de un rol tiene efecto inmediato; desactivarlo bloquea a sus usuarios. La API garantiza, incluso con peticiones concurrentes, que siempre quede un SUPER_ADMIN activo.

Los permisos RBAC se guardan en PostgreSQL y se derivan de la asignación vigente en cada solicitud. JWT valida firma HS256, emisor, audiencia y expiración. Cambiar rol, desactivar usuario o restablecer contraseña invalida tokens anteriores mediante una versión de sesión.

Configura JWT_SECRET con al menos 32 bytes aleatorios y CORS_ALLOWED_ORIGINS con orígenes exactos separados por coma. El comodín * no está permitido. El límite de login es por instancia; producción debe complementarlo en gateway/WAF.

## Imágenes de producto y almacenamiento

La API permite adjuntar imágenes al catálogo mediante `multipart/form-data`: `POST /api/v1/catalog/products/{productId}/images/upload` con la parte `file` y parámetros opcionales `altText`, `sortOrder` y `primaryImage`. Solo se aceptan PNG/JPEG que pasan validación de firma, decodificación y resolución; el tamaño por defecto es 5 MiB y el cuerpo multipart 6 MiB. La ruta se configura con `MEDIA_STORAGE_PATH` y en Compose persiste en el volumen `inventary-media-data`. Se usan nombres UUID generados por el servidor; la ruta pública solo sirve un archivo si su fila de imagen sigue publicada. La subida requiere `catalog:manage`; los errores de tamaño devuelven 413.

## Kafka opcional y Outbox

`KAFKA_ENABLED=false` por defecto. Para probar el broker local: `docker compose --profile kafka up --build` con `KAFKA_ENABLED=true` y `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`. El relay publica eventos Outbox después del commit y el consumidor procesa envelopes mediante `kafka_inbox_event`, deduplica por `eventId` y hash del contenido e invalida la caché de catálogo público. Errores persistentes se reintentan de manera acotada y se envían al topic DLQ configurable. Este consumidor no implementa proyecciones analíticas; el broker y consumidor no se habilitan por defecto.

## Ventas e idempotencia

La creación de ventas usa `POST /api/v1/sales` con el header `Idempotency-Key`. El servidor obtiene los precios del catálogo; el cliente envía únicamente producto y cantidad. Stock, venta, líneas, movimientos, auditoría, Outbox y registro de idempotencia se guardan en una sola transacción. Los reintentos con la misma clave y contenido devuelven la venta creada; reutilizar la clave con otra solicitud devuelve conflicto. Ver [P06 — Ventas con descuento atómico de stock](docs/16-implementacion-p06-ventas-atomicas.md).

## API implementada

Las rutas privadas requieren JWT y permiso RBAC; login, health, GET del catálogo público y la integración WhatsApp (con clave de máquina propia) son excepciones. GraphQL requiere permiso de lectura del catálogo. OpenAPI/Swagger está deshabilitado por defecto y no sustituye autenticación. Todos los listados son paginados (`?page=&size=&sort=`, tamaño máximo 100, ordenamiento solo por propiedades permitidas) y responden `{"content":[...],"page":{"size","number","totalElements","totalPages"}}`.

| Área | Rutas | Funcionalidad |
|---|---|---|
| Autenticación | `/api/v1/auth/login`, `/me`, `/logout`, `/me/password` | JWT, permisos efectivos, revocación y cambio de contraseña |
| Administración | `/api/v1/admin/users`, `/api/v1/admin/roles`, `/api/v1/admin/permissions` | Usuarios, roles personalizados y permisos |
| Categorías | `/api/v1/catalog/categories` | CRUD, activar/desactivar, filtro `active`; al crear, `definitions` publica las propiedades en la misma transacción (o `templateId` las de una plantilla) |
| Plantillas de atributos | `GET/PUT /api/v1/catalog/categories/{id}/attributes`, `GET /api/v1/catalog/attribute-presets` | Esquema versionado (STRING, NUMBER, BOOLEAN, DATE, ENUM, STRING_LIST) con obligatorios, opciones y rangos; `key` opcional (se deriva del nombre visible). `attribute-presets` entrega tipos de dato, propiedades sugeridas y plantillas por tipo de negocio; `attribute-presets/suggestion?text=` sugiere la plantilla a partir del nombre (vocabulario en español sin tildes ni plurales) y una categoría parecida del propio negocio para reutilizar sus propiedades |
| Unidades | `/api/v1/catalog/units`, `/api/v1/catalog/unit-dimensions`, `/api/v1/catalog/unit-conversions` | Unidades por dimensión y factores de conversión; al crear, `code` y `decimalScale` son opcionales (se derivan del nombre y de `allowsFractions`/dimensión). Unidades comunes precargadas (V16) |
| Productos | `/api/v1/catalog/products`, `/api/v1/catalog/product-kinds` | CRUD, filtros `q`, `categoryId`, `active`, `kind`; tipo (`STOCKED_GOOD`, `NON_STOCKED_GOOD`, `SERVICE`), unidad y atributos validados; SKU opcional al crear (si se omite se genera uno único `PRD-XXXXXXXX`) e inmutable; costo y precio opcionales: cada recepción de compra fija el costo (costo unitario de la línea) y recalcula el precio con `marginPercent` (25 % por defecto, precio = costo × 1,25 redondeado hacia arriba); el costo solo se muestra con `catalog:manage` o `reports:financial` |
| Variantes | `/api/v1/catalog/products/{productId}/variants`, `/api/v1/catalog/variants/{variantId}` | SKU, código de barras y combinación de opciones únicos; precio propio opcional. El stock se lleva por producto |
| Imágenes | `/api/v1/catalog/products/{productId}/images`, `POST /api/v1/catalog/products/{productId}/images/upload`, `GET /api/v1/public/media/products/{fileName}` | Metadatos por URL HTTPS, subida PNG/JPEG validada por contenido/tamaño/resolución, almacenamiento por UUID, publicación y retirada segura |
| Catálogo público | `/api/v1/public/catalog/products`, `/products/{sku|uuid|sku de variante|código de barras}`, `/categories` | Solo activos, disponibilidad vendible, variantes e imágenes publicadas; nunca costos |
| Almacenes | `/api/v1/warehouses`, `/{id}/locations` | CRUD, activar/desactivar (bloqueado con stock o reservas), ubicaciones |
| Inventario | `/api/v1/inventory/balances`, `/availability`, `/movements`, `/movements/{id}` | Saldos filtrables, disponibilidad informativa, kardex filtrable por producto, almacén, tipo, referencia y fechas, detalle con valoración |
| Ajustes | `POST /api/v1/inventory/adjustments` | Entrada/salida con `Idempotency-Key`, motivo y costo unitario opcional para entradas |
| Conteos físicos | `/api/v1/inventory/counts` | Captura, contabilización de diferencias (rechaza snapshot obsoleto) y cancelación |
| Reposición | `/api/v1/inventory/reorder-rules`, `/replenishment-suggestions` | Mínimos, objetivos, plazo y proveedor preferido |
| Transferencias | `/api/v1/inventory/transfers` | Despacho/recepción idempotentes al costo de salida, cancelación compensatoria y reversión |
| Clientes y proveedores | `/api/v1/customers`, `/api/v1/suppliers` | CRUD, búsqueda `q`, activar/desactivar |
| Compras | `/api/v1/purchase-orders`, `/api/v1/goods-receipts` | Órdenes idempotentes, envío/aprobación/cancelación idempotentes por estado, aprobador distinto del creador, recepciones parciales valorizadas, filtros |
| Devoluciones a proveedor | `/api/v1/purchase-returns` | Límite por cantidad recibida (también concurrente), salida al costo de la recepción |
| Ventas | `/api/v1/sales`, `/api/v1/sales/pricing` | Precio sugerido por el servidor (costo de compra + ganancia) y editable por línea, rechazando precios bajo el costo real de las unidades vendidas; costo de venta según política, filtros por estado, almacén, cliente, canal y fechas; cancelación solicitada y aprobada por actores distintos |
| Devoluciones de venta | `/api/v1/sales-returns` | RESTOCK o SCRAP al costo de la venta original, límite por cantidad vendida |
| Integración WhatsApp/n8n | `/api/v1/integrations/whatsapp/**` | Catálogo, carrito, cotización, pedido con reserva y cancelación |
| Pedidos WhatsApp (personal) | `/api/v1/whatsapp-orders`, `/{id}/confirm-payment` | Seguimiento y confirmación de un pago verificado que convierte el pedido en venta |
| Costeo | `/api/v1/costing/policy` | Consultar y fijar FIFO o promedio ponderado (solo antes del primer movimiento valorizado) |
| Reportes | `/api/v1/reports/inventory-balance`, `/valuation`, `/sales`, `/purchases`, `/kardex`, `/low-stock` | Ver "Costeo y reportes" |
| Auditoría | `GET /api/v1/audit-events`, `/{id}` | Lectura con `audit:read` |
| Operaciones | `/api/v1/operations/outbox` | Estado, diagnóstico y redrive del Outbox (`operations:manage`) |
| GraphQL | `/graphql` | Consultas públicas de productos/categorías con límites de profundidad y complejidad |

Las rutas exactas, parámetros y esquemas se exponen en Swagger/OpenAPI. La matriz de permisos por rol está en [docs/RESULTADO_AUDITORIA_EJECUCION.md](docs/RESULTADO_AUDITORIA_EJECUCION.md#6-matriz-rbac-verificada-rolematrixapitest).

## Costeo y reportes

Cada movimiento físico se valoriza en la misma transacción que cambia el saldo:

- **Promedio ponderado** (por defecto) o **FIFO**, configurable en `PUT /api/v1/costing/policy` mientras no existan movimientos valorizados.
- Las entradas abren capas de costo (recepción al costo de la orden; ajuste al costo indicado o al promedio vigente). Las salidas consumen capas en orden FIFO y se valoran según la política.
- Cancelaciones de venta, devoluciones de cliente y transferencias reingresan al costo exacto con el que salieron; las devoluciones a proveedor salen al costo de su recepción.
- El ledger de valoración (`inventory_valuation_entry`) es append-only y se consulta en `GET /api/v1/inventory/movements/{id}`.

Reportes (permiso `reports:financial`; fechas `YYYY-MM-DD` inclusivas, máximo 366 días, en la zona `REPORTS_TIME_ZONE`, por defecto `America/Guayaquil`, o el parámetro `zone`):

| Reporte | Contenido |
|---|---|
| `GET /api/v1/reports/inventory-balance?from&to&warehouseId[&productId]` | Por producto: existencia inicial y final del periodo, compras netas e importe, ventas netas e importe, existencia actual/reservada/disponible, **valor inicial y final, costo de ventas y utilidad bruta** |
| `GET /api/v1/reports/valuation[?warehouseId&productId&includeEmpty]` | Cantidad, costo promedio y valor por producto y almacén, conciliado contra el saldo físico, con totales por moneda |
| `GET /api/v1/reports/sales?from&to[&warehouseId]` | Ventas brutas, cancelaciones, devoluciones, ventas netas, costo de ventas, utilidad bruta, detalle diario y por producto |
| `GET /api/v1/reports/purchases?from&to[&warehouseId&supplierId]` | Recepciones y devoluciones a proveedor por proveedor y producto |
| `GET /api/v1/reports/kardex?productId&warehouseId&from&to` | Movimientos del periodo con saldo acumulado en cantidad y valor |
| `GET /api/v1/reports/low-stock[?warehouseId]` | Productos bajo su mínimo de reposición |

Reponer un producto agotado no requiere crear un producto nuevo: las recepciones del mismo `productId` vuelven a sumar existencias y el reporte consolidado distingue la existencia al cierre del periodo de la existencia actual.

## Contratos de inventario

### Ajuste de stock

```http
POST /api/v1/inventory/adjustments
Authorization: Bearer <JWT>
Idempotency-Key: ajuste-inicial-001
Content-Type: application/json

{
  "productId": "UUID_DEL_PRODUCTO",
  "warehouseId": "UUID_DEL_ALMACEN",
  "direction": "INCREASE",
  "quantity": 12.500,
  "reason": "Saldo inicial aprobado"
}
```

Las cantidades son positivas y admiten tres decimales. El servicio bloquea el saldo afectado, evita cantidades negativas o consumo del stock reservado y escribe el movimiento en la misma transacción. Misma clave y mismo contenido devuelve el resultado original; misma clave con contenido distinto responde conflicto.

### Conteo físico

```http
POST /api/v1/inventory/counts
Authorization: Bearer <JWT>
Idempotency-Key: conteo-bodega-001
Content-Type: application/json

{
  "warehouseId": "UUID_DEL_ALMACEN",
  "items": [
    {"productId": "UUID_DEL_PRODUCTO", "countedQuantity": 14.000}
  ]
}
```

La creación captura el saldo esperado y la cantidad física contada. Consulta el conteo en `GET /api/v1/inventory/counts/{id}`; después, `POST /api/v1/inventory/counts/{id}/post` aplica únicamente la diferencia. Si el saldo cambió desde la captura, la contabilización se rechaza para evitar sobrescribir movimientos posteriores. `POST /api/v1/inventory/counts/{id}/cancel` cancela solo conteos abiertos. Las líneas capturadas no se editan; una corrección requiere un nuevo conteo.

### Devoluciones y anulaciones aprobadas

Las devoluciones de clientes y proveedores exigen `Idempotency-Key`; repetir clave y contenido devuelve el documento original y cambiar el contenido produce conflicto. Los movimientos enlazan documento, línea fuente, ubicación y unidad de medida. Para cancelar una venta, primero se usa `POST /api/v1/sales/{id}/request-cancellation` con un motivo; no cambia el stock hasta que un actor autorizado distinto confirma con `POST /api/v1/sales/{id}/approve-cancellation`. Una venta con devoluciones no puede cancelarse.

Detalle técnico: [P09 — devoluciones y anulaciones](docs/19-implementacion-p09-devoluciones-y-anulaciones.md).

## Compras y recepciones parciales

Crear una orden requiere `Idempotency-Key`:

```http
POST /api/v1/purchase-orders
Authorization: Bearer <JWT>
Idempotency-Key: oc-proveedor-001
Content-Type: application/json

{
  "supplierId": "UUID_DEL_PROVEEDOR",
  "warehouseId": "UUID_DEL_ALMACEN",
  "items": [
    {"productId": "UUID_DEL_PRODUCTO", "quantity": 20.000, "unitCost": 4.25}
  ]
}
```

Una orden sigue `DRAFT → SUBMITTED → APPROVED → PARTIALLY_RECEIVED → RECEIVED`; las órdenes en borrador o enviadas se pueden cancelar. Aprobar requiere `purchasing:approve`. Recibir requiere `purchasing:manage` y una clave idempotente:

```http
POST /api/v1/purchase-orders/{orderId}/receipts
Authorization: Bearer <JWT>
Idempotency-Key: recepcion-proveedor-001
Content-Type: application/json

{
  "items": [
    {"purchaseOrderLineId": "UUID_DE_LINEA", "quantity": 7.000}
  ]
}
```

El servicio valida que cada línea pertenezca a la orden, que la cantidad no supere el pendiente y que el proveedor, almacén y productos sean válidos. Cada recepción actualiza saldo, cantidad recibida, ledger, auditoría y Outbox dentro de una sola transacción. Los reintentos no duplican el ingreso de stock. Ver [P08 — Compras y recepciones parciales](docs/18-implementacion-p08-compras-y-recepciones.md).

### Venta

```http
POST /api/v1/sales
Authorization: Bearer <JWT>
Idempotency-Key: pedido-pos-123
Content-Type: application/json

{
  "warehouseId": "UUID_DEL_ALMACEN",
  "customerName": "Cliente",
  "customerPhone": "+593...",
  "channel": "POS",
  "items": [{"productId": "UUID_DEL_PRODUCTO", "quantity": 2.000}]
}
```

El precio procede del producto guardado en PostgreSQL; el cliente no puede indicar el precio. En una transacción se bloquea el stock y se graban venta, líneas, movimiento y evento Outbox. Una falla revierte toda la operación. Los impuestos están temporalmente en cero porque la política fiscal/SRI no ha sido integrada; tampoco se emiten facturas ni se procesan pagos en esta ruta.

## Integración WhatsApp/n8n

Configura `WHATSAPP_INTEGRATION_API_KEY` con una clave aleatoria de al menos 32 bytes. Sin ella, estas llamadas son rechazadas. En cada solicitud de máquina envía `X-Integration-Key`; usa además `Idempotency-Key` al cotizar o crear una orden.

Flujo: consulta catálogo → crea carrito → agrega productos → solicita cotización para un almacén → presenta al cliente el precio y vencimiento → registra orden solo con `consentConfirmed: true`. La cotización dura 10 minutos por defecto; la orden pendiente reserva stock por 20 minutos. Los TTL se configuran con `WHATSAPP_QUOTE_TTL_MINUTES` y `WHATSAPP_ORDER_TTL_MINUTES`. Una tarea programada libera reservas al cancelar o expirar la orden. Las consultas de carrito, cotización y orden aceptan `?customerRef=` para comprobar que el recurso pertenece al cliente de la conversación (si no coincide, 404).

No se integra un proveedor de pagos. Cuando el personal verifica el pago (transferencia, efectivo, tarjeta), lo registra con `POST /api/v1/whatsapp-orders/{id}/confirm-payment` (`sales:manage`, `Idempotency-Key`, referencia y método de pago): la reserva se consume y se crea la venta de canal `WHATSAPP` con su costo de venta. La orden nunca se marca pagada por la respuesta de un LLM o de n8n.

La API solo almacena URLs HTTPS de imágenes; no descarga las URLs recibidas, lo que evita usar el backend como proxy SSRF. Para subir archivos binarios hace falta un servicio de almacenamiento de objetos y validación de contenido real.

## Consistencia, caché y eventos

- PostgreSQL es fuente de verdad de los precios, disponibilidad, ventas, compras y movimientos.
- Caffeine almacena durante 60 segundos listados públicos; checkout y cotización vuelven a validar precio y saldo en la base.
- El patrón Outbox registra eventos en la misma transacción que el documento de negocio.
- El publicador Kafka es opcional y se activa con `KAFKA_ENABLED=true`, configurando `KAFKA_BOOTSTRAP_SERVERS`. Kafka no es necesario para iniciar el backend si la integración no se habilita.
- Los consumidores externos deben deduplicar por `eventId` debido a la semántica de entrega/reintentos; no se debe asumir exactly-once entre PostgreSQL y Kafka.
- La API no implementa todavía un consumidor de Kafka para proyecciones ni un proveedor de cache distribuido.

## Convenciones de base de datos

- UUID como identificador; `TIMESTAMPTZ` para instantes.
- `NUMERIC(14,2)` para importes, `NUMERIC(14,3)` para cantidades.
- Claves foráneas y `ON DELETE RESTRICT` para conservar documentos históricos.
- Restricciones de dominio, unicidad, índices de consulta e idempotencia declarados mediante migraciones.
- `StockBalance` es el saldo operativo; `StockMovement` es el ledger append-only protegido por triggers de base de datos. Las correcciones se representan con movimientos compensatorios.
- Las órdenes de compra separan cantidad ordenada, recibida y pendiente.
- Las órdenes conversacionales guardan snapshots de producto, precio y cantidad.

## Pruebas y CI

Las pruebas usan PostgreSQL real (nunca H2) con el perfil `test`. Crea una base aislada y ejecuta desde esta carpeta:

```bash
createdb inventary_test   # o CREATE DATABASE inventary_test;
export TEST_DATASOURCE_URL=jdbc:postgresql://localhost:5432/inventary_test
export TEST_DB_USERNAME=postgres TEST_DB_PASSWORD='<clave>'
mvn -B verify
```

La suite (76 pruebas) incluye pruebas unitarias, de integración PostgreSQL, de contrato HTTP con JWT real para todas las áreas, una matriz RBAC ejecutable y pruebas de concurrencia (sobreventa, idempotencia, devoluciones, último administrador, reservas, Outbox). Cada prueba crea sus datos con claves aleatorias, por lo que puede repetirse sobre la misma base.

Prueba extremo a extremo con la API levantada (`SWAGGER_ENABLED=true`), que recorre las 138 operaciones REST y verifica la cobertura contra OpenAPI:

```bash
python scripts/smoke_test_api.py --base-url http://localhost:8080 \
  --admin-user <usuario> --admin-password '<clave>' --integration-key '<WHATSAPP_INTEGRATION_API_KEY>'
```

El workflow `.github/workflows/backend-ci.yml` (manual) ejecuta `mvn -B verify` con PostgreSQL 16. Resultados de la última ejecución: [docs/RESULTADO_AUDITORIA_EJECUCION.md](docs/RESULTADO_AUDITORIA_EJECUCION.md). Operación, respaldos y redrive: [docs/23-runbook-operacion.md](docs/23-runbook-operacion.md).

## Fuera de alcance de esta versión

No implementado y no debe presentarse como disponible: proveedor de pagos, reembolsos y conciliación; impuestos y facturación electrónica SRI (el impuesto es 0 y los totales no son comprobantes fiscales); carga binaria de imágenes; stock por ubicación múltiple dentro de un almacén y stock por variante; consumidores Kafka y proyecciones; multiempresa; rate limit distribuido (debe hacerlo el gateway/WAF); frontend Angular.

## Transferencias entre almacenes

Los comandos de creación, despacho, recepción, cancelación y reversión requieren `Idempotency-Key`. Si una transferencia en tránsito se cancela, se restaura el stock del origen con un movimiento compensatorio; una transferencia completada se revierte mediante `POST /api/v1/inventory/transfers/{id}/reverse` y un motivo obligatorio. La reversión descuenta el destino y repone el origen en una transacción, sin editar movimientos históricos. Ver [P10 — Transferencias consistentes](docs/20-implementacion-p10-transferencias-consistentes.md).


## Auditoría, errores y OpenAPI (P11)

- La bitácora se consulta con `GET /api/v1/audit-events`, permiso `audit:read`, filtros por actor/acción/recurso/resultado/intervalo y paginación máxima de 100 eventos.
- Cada respuesta REST incluye `X-Request-ID`. Se acepta un valor entrante solo si cumple el patrón seguro; de lo contrario se genera uno nuevo. El mismo valor aparece en el cuerpo del error como `requestId`.
- Los errores siguen `application/problem+json`. Los errores inesperados no exponen stack traces, SQL ni secretos; los detalles técnicos se registran en logs del servidor junto al requestId.
- Los errores HTTP de `/api/**` generan eventos de auditoría con actor disponible, ruta sin query string, método, estado y correlación. Nunca se guardan cuerpos ni credenciales.
- OpenAPI descubre las rutas de los controladores. JWT Bearer se documenta para rutas privadas y `X-Integration-Key` para WhatsApp. Habilita Swagger solo en desarrollo con `SWAGGER_ENABLED=true`.
- Véase [21 — Implementación P11](docs/21-implementacion-p11-auditoria-documentacion-api.md) y [05 — Catálogo de APIs](docs/05-catalogo-apis.md).


## Integración y entrega (P12)

- La rama de integración P12 incluye las fases P01–P11, incluida la auditoría y el contrato OpenAPI.
- Docker Compose levanta PostgreSQL 16 con healthcheck y volumen persistente; la API espera a que PostgreSQL esté saludable antes de arrancar.
- El Dockerfile empaqueta con Java 21 y ejecuta la aplicación en JRE como usuario no root.
- El workflow `.github/workflows/backend-ci.yml` está preparado para ejecutar `mvn -B verify` contra PostgreSQL 16, pero por ahora solo se inicia manualmente desde GitHub Actions para no disparar validaciones sin autorización.
- P12 se entregó sin ejecutar build ni pruebas; la validación completa se realizó después (ver [resultado de la auditoría](docs/RESULTADO_AUDITORIA_EJECUCION.md)).
- Guía: [22 — P12: integración del código y entrega](docs/22-entrega-p12-integracion-codigo.md).
