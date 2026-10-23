# Inventary Backend

Backend de inventario y ventas construido con Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA y PostgreSQL. Las entidades usan UUID, importes `NUMERIC(14,2)`, cantidades `NUMERIC(14,3)`, marcas temporales `TIMESTAMPTZ`, claves foráneas y restricciones explícitas.

## Requisitos y ejecución

- Java 21 y Maven 3.9 o superior.
- PostgreSQL 15 o superior. Docker Compose usa PostgreSQL 16.
- Docker Engine y Docker Compose son opcionales.

### Con PostgreSQL instalado localmente

La configuración JDBC por defecto apunta a `jdbc:postgresql://localhost:5432/inventary`, usuario `postgres` y contraseña local `CLAVESECRETA`, según la cadena suministrada para este proyecto.

PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/inventary"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "CLAVESECRETA"
$env:JWT_SECRET = "usa-un-secreto-aleatorio-de-al-menos-32-bytes"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "cambia-esta-clave-administrativa-123!"
mvn -B verify
mvn spring-boot:run
```

Bash:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/inventary'
export DB_USERNAME='postgres'
export DB_PASSWORD='CLAVESECRETA'
export JWT_SECRET='usa-un-secreto-aleatorio-de-al-menos-32-bytes'
export BOOTSTRAP_ADMIN_USERNAME='admin'
export BOOTSTRAP_ADMIN_PASSWORD='cambia-esta-clave-administrativa-123!'
mvn -B verify
mvn spring-boot:run
```

Ejecuta los comandos Maven desde `microservices-inventary-ecuapp/`. Para entornos compartidos o producción, no uses estas credenciales de ejemplo: proporciona los secretos con el gestor de secretos del entorno. No guardes contraseñas reales en commits.

### Con Docker Compose

```bash
cd microservices-inventary-ecuapp
cp .env.example .env
# Edita .env: reemplaza al menos DB_PASSWORD, JWT_SECRET y BOOTSTRAP_ADMIN_PASSWORD.
docker compose up --build
```

API: http://localhost:8080  
Swagger/OpenAPI: http://localhost:8080/swagger-ui.html  
Health: http://localhost:8080/actuator/health  
GraphQL: http://localhost:8080/graphql (consulta únicamente datos públicos de catálogo)

### Creación del esquema

Flyway controla las migraciones en `src/main/resources/db/migration/V*__*.sql`. Hibernate se configura con `ddl-auto: validate`: al arrancar verifica el mapeo contra PostgreSQL, pero no modifica ni “adivina” el esquema. No sustituyas Flyway por `ddl-auto=update` en producción. Nunca se ejecuta `flyway clean` automáticamente.

Si la base contiene tablas creadas manualmente o una tabla `flyway_schema_history` ajena a estas migraciones, revisa primero el estado y haz una copia de seguridad; no borres datos para forzar el arranque.

## Autenticación y roles

`POST /api/v1/auth/login` recibe `{"username":"admin","password":"..."}` y devuelve un JWT. Envíalo como `Authorization: Bearer <token>`. `GET /api/v1/auth/me` devuelve la identidad autenticada.

No se crea administrador por defecto. El bootstrap inicial solo se ejecuta si configuras `BOOTSTRAP_ADMIN_USERNAME` y `BOOTSTRAP_ADMIN_PASSWORD`; la contraseña requiere 12 caracteres y el proceso no sobreescribe usuarios existentes. El bootstrap es para inicializar el entorno; luego los usuarios se gestionan mediante:

- `GET/POST /api/v1/admin/users`
- `GET/PATCH /api/v1/admin/users/{id}`
- `PUT /api/v1/admin/users/{id}/password`

Roles disponibles: `ADMIN`, `INVENTORY_MANAGER`, `SALES` y `AUDITOR`. En cada solicitud autenticada el backend comprueba en PostgreSQL que el usuario siga activo y lee su rol actual; no confía únicamente en los roles embebidos en el JWT.

## API implementada

Todas las rutas requieren JWT, salvo login, health, OpenAPI/Swagger, catálogo público, GraphQL y las rutas de integración WhatsApp que exigen su propia clave de máquina.

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
| Transferencias | `/api/v1/inventory/transfers` | Crear, despachar, recibir completo o cancelar antes del despacho |
| Ventas | `/api/v1/sales` | Registrar ventas, consulta paginada y detalle |
| Devoluciones de venta | `/api/v1/sales-returns` | Restock o merma, limitando el total devuelto a la cantidad original |
| Clientes | `/api/v1/customers` | Crear y mantener contactos de cliente |
| Proveedores | `/api/v1/suppliers` | Crear y mantener contactos de proveedor |
| Compras | `/api/v1/purchase-orders` | Borrador, envío, aprobación, cancelación permitida y consulta |
| Recepciones | `POST /api/v1/purchase-orders/{id}/receipts`, `GET /api/v1/goods-receipts/{id}` | Recepción parcial e idempotente, actualiza saldo y ledger en transacción |
| Devoluciones a proveedor | `/api/v1/purchase-returns` | Devolver cantidades de recepciones verificadas; impide exceder el recibido o el stock |
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
- `StockBalance` es el saldo operativo; `StockMovement` es el ledger.
- Las órdenes de compra separan cantidad ordenada, recibida y pendiente.
- Las órdenes conversacionales guardan snapshots de producto, precio y cantidad.

## Pruebas y CI

Ejecuta `mvn -B verify` desde esta carpeta. El workflow `.github/workflows/backend-ci.yml` compila Java 21 y levanta PostgreSQL para las migraciones, validación de mappings JPA y pruebas de integración. No consideres un cambio listo hasta que el workflow del último commit termine correctamente.

## Pendiente de alcance original

Aún requieren validación/implementación adicional: integración de pagos y estados de pago, facturación electrónica/SRI y reglas fiscales Ecuador; subida binaria/almacenamiento de imágenes; confirmación y conciliación externa para WhatsApp; consumidores Kafka con deduplicación/observabilidad; controles de tasa y límites explícitos de complejidad GraphQL; informes, valoración de existencias según política contable, separación multiempresa y pruebas de concurrencia amplias. El código actual no significa cobertura completa de esos requisitos hasta cerrar sus issues con código y tests.
