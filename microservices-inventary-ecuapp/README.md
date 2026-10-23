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

No existen credenciales administrativas predeterminadas. Si la base no tiene usuarios, el arranque exige BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD; el primer usuario recibe SUPER_ADMIN. La contraseña inicial debe tener al menos 16 caracteres e incluir mayúscula, minúscula, número y símbolo. El bootstrap solo actúa con la tabla vacía.

Roles canónicos iniciales: SUPER_ADMIN, AUDITOR, MANAGER_ALMACEN, VENDEDOR y SOPORTE_TI. SUPER_ADMIN puede crear roles personalizados, consultar el catálogo de permisos y asignar permisos existentes. No se pueden modificar los permisos de SUPER_ADMIN desde la API.

- GET/POST /api/v1/admin/users
- GET/PATCH /api/v1/admin/users/{id}
- PUT /api/v1/admin/users/{id}/password
- GET/POST /api/v1/admin/roles
- GET /api/v1/admin/permissions
- PUT /api/v1/admin/roles/{code}/permissions

Los permisos RBAC se guardan en PostgreSQL y se derivan de la asignación vigente en cada solicitud. JWT valida firma HS256, emisor, audiencia y expiración. Cambiar rol, desactivar usuario o restablecer contraseña invalida tokens anteriores mediante una versión de sesión.

Configura JWT_SECRET con al menos 32 bytes aleatorios y CORS_ALLOWED_ORIGINS con orígenes exactos separados por coma. El comodín * no está permitido. El límite de login es por instancia; producción debe complementarlo en gateway/WAF.

## Ventas e idempotencia

La creación de ventas usa `POST /api/v1/sales` con el header `Idempotency-Key`. El servidor obtiene los precios del catálogo; el cliente envía únicamente producto y cantidad. Stock, venta, líneas, movimientos, auditoría, Outbox y registro de idempotencia se guardan en una sola transacción. Los reintentos con la misma clave y contenido devuelven la venta creada; reutilizar la clave con otra solicitud devuelve conflicto. Ver [P06 — Ventas con descuento atómico de stock](docs/16-implementacion-p06-ventas-atomicas.md).

## API implementada

Las rutas privadas requieren JWT y permiso RBAC; login, health, GET del catálogo público y la integración WhatsApp (con clave de máquina propia) son excepciones. GraphQL requiere permiso de lectura del catálogo. OpenAPI/Swagger está deshabilitado por defecto y no sustituye autenticación.

| Área | Rutas | Funcionalidad |
|---|---|---|
| Categorías | `/api/v1/catalog/categories` | CRUD y desactivación lógica |
| Productos | `/api/v1/catalog/products` | CRUD, búsqueda, SKU único; el SKU es inmutable después del alta |
| Catálogo público | `/api/v1/public/catalog/products` | Información pública, precio, imagen y disponibilidad sin exponer costo interno |
| Imágenes | `/api/v1/catalog/products/{productId}/images` | Metadatos por URL HTTPS, descripción alternativa y orden; no carga binarios |
| Almacenes | `/api/v1/warehouses` | CRUD y desactivación lógica |
| Inventario | `/api/v1/inventory/balances`, `/api/v1/inventory/movements` | Consultar saldos y ledger |
| Ajustes | `POST /api/v1/inventory/adjustments` | Entrada/salida con Idempotency-Key y motivo |
| Reposición | `/api/v1/inventory/reorder-rules`, `GET /api/v1/inventory/replenishment-suggestions` | Mínimos, objetivos, plazo y proveedor preferido |
| Transferencias | `/api/v1/inventory/transfers` | Despacho/recepción idempotentes, cancelación compensatoria en tránsito y reversión de transferencias completadas; ledger, auditoría y Outbox atómicos |
| Conteos físicos | `/api/v1/inventory/counts` | Capturar cantidades, consultar diferencias, contabilizar ajustes o cancelar; snapshots inmutables e idempotencia |
| Ventas | `/api/v1/sales` | Registrar y consultar ventas; solicitud y aprobación segregadas de cancelaciones |
| Devoluciones de venta | `/api/v1/sales-returns` | Idempotencia concurrente, restock/merma, límite por cantidad vendida y ledger trazable |
| Clientes | `/api/v1/customers` | Crear y mantener contactos de cliente |
| Proveedores | `/api/v1/suppliers` | CRUD de contactos; lectura protegida por `purchasing:read` |
| Compras | `/api/v1/purchase-orders` | Crear idempotentemente, enviar, aprobar, cancelar antes de aprobación y consultar |
| Recepciones | `POST /api/v1/purchase-orders/{id}/receipts`, `GET /api/v1/goods-receipts/{id}` | Recepción parcial idempotente; actualiza saldo, ledger, auditoría y Outbox en transacción |
| Devoluciones a proveedor | `/api/v1/purchase-returns` | Idempotencia concurrente, recepción fuente verificada, límite recibido y ledger trazable |
| Auditoría | `GET /api/v1/audit-events` | Lectura restringida a ADMIN/AUDITOR |
| Integración WhatsApp/n8n | `/api/v1/integrations/whatsapp/**` | Catálogo por SKU/UUID, carrito, cotización temporal y orden pendiente de pago |
| GraphQL | `/graphql` | Consultas públicas de productos/categorías con paginación acotada |

Las rutas exactas y parámetros se exponen también en Swagger/OpenAPI.

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

Flujo: consulta catálogo → crea carrito → agrega productos → solicita cotización para un almacén → presenta al cliente el precio y vencimiento → registra orden solo con `consentConfirmed: true`. La cotización dura 10 minutos por defecto; la orden pendiente reserva stock por 20 minutos. Los TTL se configuran con `WHATSAPP_QUOTE_TTL_MINUTES` y `WHATSAPP_ORDER_TTL_MINUTES`. Una tarea programada libera reservas al cancelar o expirar la orden. No se integra todavía un proveedor de pagos ni una confirmación de cobro, así que la orden no se debe marcar pagada solo con la respuesta de un LLM o de n8n.

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

Ejecuta `mvn -B verify` desde esta carpeta. El workflow `.github/workflows/backend-ci.yml` compila Java 21 y levanta PostgreSQL para las migraciones, validación de mappings JPA y pruebas de integración. No consideres un cambio listo hasta que el workflow del último commit termine correctamente.

## Pendiente de alcance original

Aún requieren validación/implementación adicional: integración de pagos y estados de pago, facturación electrónica/SRI y reglas fiscales Ecuador; subida binaria/almacenamiento de imágenes; confirmación y conciliación externa para WhatsApp; consumidores Kafka con deduplicación/observabilidad; controles de tasa y límites explícitos de complejidad GraphQL; informes, valoración de existencias según política contable, separación multiempresa y pruebas de concurrencia amplias. El código actual no significa cobertura completa de esos requisitos hasta cerrar sus issues con código y tests.

## Transferencias entre almacenes

Los comandos de creación, despacho, recepción, cancelación y reversión requieren `Idempotency-Key`. Si una transferencia en tránsito se cancela, se restaura el stock del origen con un movimiento compensatorio; una transferencia completada se revierte mediante `POST /api/v1/inventory/transfers/{id}/reverse` y un motivo obligatorio. La reversión descuenta el destino y repone el origen en una transacción, sin editar movimientos históricos. Ver [P10 — Transferencias consistentes](docs/20-implementacion-p10-transferencias-consistentes.md).


## Auditoría, errores y OpenAPI (P11)

- La bitácora se consulta con `GET /api/v1/audit-events`, permiso `audit:read`, filtros por actor/acción/recurso/resultado/intervalo y paginación máxima de 100 eventos.
- Cada respuesta REST incluye `X-Request-ID`. Se acepta un valor entrante solo si cumple el patrón seguro; de lo contrario se genera uno nuevo. El mismo valor aparece en el cuerpo del error como `requestId`.
- Los errores siguen `application/problem+json`. Los errores inesperados no exponen stack traces, SQL ni secretos; los detalles técnicos se registran en logs del servidor junto al requestId.
- Los errores HTTP de `/api/**` generan eventos de auditoría con actor disponible, ruta sin query string, método, estado y correlación. Nunca se guardan cuerpos ni credenciales.
- OpenAPI descubre las rutas de los controladores. JWT Bearer se documenta para rutas privadas y `X-Integration-Key` para WhatsApp. Habilita Swagger solo en desarrollo con `SWAGGER_ENABLED=true`.
- Véase [21 — Implementación P11](docs/21-implementacion-p11-auditoria-documentacion-api.md) y [05 — Catálogo de APIs](docs/05-catalogo-apis.md).
