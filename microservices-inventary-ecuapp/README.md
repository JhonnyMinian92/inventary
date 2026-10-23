# Inventary — backend API

Backend modular sobre Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA y PostgreSQL. Este primer tramo implementa autenticación JWT, categorías, productos, almacenes, saldos, ajustes, movimientos de inventario y ventas con descuento transaccional de stock. El trabajo restante se rastrea en GitHub Issues.

## Requisitos

- Java 21 y Maven 3.9+.
- PostgreSQL 15+; Docker Compose ofrece PostgreSQL 16 para desarrollo.
- Docker Engine y Docker Compose, si se desea levantar la API y la base en contenedores.

## PostgreSQL local

La configuración predeterminada local usa la dirección JDBC jdbc:postgresql://localhost:5432/inventary, el usuario postgres y la contraseña CLAVESECRETA, coincidiendo con la conexión compartida. La contraseña es solo una comodidad de desarrollo: usa variables de entorno en cualquier entorno compartido o de producción.

PowerShell:
```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/inventary"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "CLAVESECRETA"
$env:JWT_SECRET = "reemplaza-esta-clave-por-una-secreta-de-al-menos-32-bytes"
```

Bash:
```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/inventary'
export DB_USERNAME='postgres'
export DB_PASSWORD='CLAVESECRETA'
export JWT_SECRET='reemplaza-esta-clave-por-una-secreta-de-al-menos-32-bytes'
```

Flyway ejecuta las migraciones en src/main/resources/db/migration. Hibernate está configurado con ddl-auto validate: no altera el esquema implícitamente; comprueba que las entidades concuerden con las tablas migradas. No cambies a update para ocultar diferencias del modelo.

## Ejecutar con Docker Compose

```bash
cp .env.example .env
# Edita .env y cambia JWT_SECRET y BOOTSTRAP_ADMIN_PASSWORD antes de ejecutar.
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Si PostgreSQL ya corre en tu máquina, arranca solo la API con Maven y configura las variables anteriores.

## Administrador inicial

La aplicación no contiene un administrador ni una contraseña predeterminada. Para crear el primero, configura ambas variables: BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD. La contraseña requiere mínimo 12 caracteres. El bootstrap crea el usuario solo si no existe y nunca reemplaza una contraseña ya guardada. El rol inicial es ADMIN. Si no se definen ambas variables, no se crea ningún administrador.

## API implementada

Todas las rutas requieren JWT Bearer salvo login, catálogo público, health y OpenAPI.

| Método | Ruta | Uso |
|---|---|---|
| POST | /api/v1/auth/login | Devuelve JWT tras validar credenciales |
| GET | /api/v1/auth/me | Identidad del JWT |
| GET/POST | /api/v1/catalog/categories | Listar y crear categorías |
| GET/PUT | /api/v1/catalog/categories/{id} | Consultar o editar categoría |
| POST | /api/v1/catalog/categories/{id}/deactivate | Inactivar categoría |
| GET/POST | /api/v1/catalog/products | Buscar o crear productos |
| GET/PUT | /api/v1/catalog/products/{id} | Consultar o editar producto |
| POST | /api/v1/catalog/products/{id}/deactivate | Inactivar producto |
| GET | /api/v1/public/catalog/products | Catálogo público sin costo interno |
| GET/POST | /api/v1/warehouses | Listar o crear almacenes |
| GET/PUT | /api/v1/warehouses/{id} | Consultar o editar almacén |
| POST | /api/v1/warehouses/{id}/deactivate | Inactivar almacén |
| GET | /api/v1/inventory/balances | Consultar saldos paginados |
| GET | /api/v1/inventory/balances/{productId}/{warehouseId} | Saldo por producto y almacén |
| GET | /api/v1/inventory/movements | Consultar ledger |
| POST | /api/v1/inventory/adjustments | Ajuste con Idempotency-Key |
| GET/POST | /api/v1/sales | Consultar ventas o registrar venta |
| GET | /api/v1/sales/{id} | Consultar una venta y sus líneas |

Escrituras de catálogo e inventario requieren rol ADMIN o INVENTORY_MANAGER. Crear ventas requiere ADMIN, INVENTORY_MANAGER o SALES. AUDITOR tiene acceso de lectura a ventas y movimientos.

## Ajuste de stock

```http
POST /api/v1/inventory/adjustments
Authorization: Bearer <JWT>
Idempotency-Key: ajuste-inicial-001
Content-Type: application/json
```

```json
{
  "productId": "UUID_DEL_PRODUCTO",
  "warehouseId": "UUID_DEL_ALMACEN",
  "direction": "INCREASE",
  "quantity": 12.500,
  "reason": "Saldo inicial aprobado"
}
```

La cantidad se guarda con hasta tres decimales y siempre es positiva; la dirección se expresa mediante INCREASE o DECREASE. El servicio no permite stock negativo ni consumir unidades reservadas. Una misma clave con el mismo payload devuelve el movimiento anterior; si el payload cambia, responde 409 Conflict.

## Registrar una venta

```http
POST /api/v1/sales
Authorization: Bearer <JWT>
Idempotency-Key: pedido-whatsapp-123
Content-Type: application/json
```

```json
{
  "warehouseId": "UUID_DEL_ALMACEN",
  "customerName": "Cliente",
  "customerPhone": "+593...",
  "channel": "WHATSAPP",
  "items": [
    {"productId": "UUID_DEL_PRODUCTO", "quantity": 2}
  ]
}
```

El servidor recupera el precio vigente desde PostgreSQL y calcula subtotal y total: el cliente no puede imponer un precio. En una transacción se bloquean los saldos, se comprueba disponibilidad, se descuenta inventario, se registra venta/líneas, se genera el movimiento de salida y se inserta un evento Outbox pendiente. Si falla un paso, la transacción revierte todas las escrituras. Por el momento, impuestos quedan en cero; esta API no emite factura electrónica ni registra pagos.

## Convenciones de datos

- UUID para identificadores.
- Instantes UTC con PostgreSQL TIMESTAMPTZ.
- Importes NUMERIC(14,2) y cantidades NUMERIC(14,3).
- Relaciones con ON DELETE RESTRICT para preservar historia.
- StockMovement es el historial; StockBalance es el saldo operativo.
- Outbox tiene esquema y recibe eventos de venta, pero aún no hay publicador Kafka.
- Los errores se presentan mediante ProblemDetail y no exponen consultas SQL, stacktraces o secretos.
- La clave JWT predeterminada es solo para desarrollo local; reemplázala antes de exponer el servicio.

## Pruebas

```bash
mvn -B verify
```

Las pruebas de contexto e integración necesitan PostgreSQL disponible. GitHub Actions configura un servicio PostgreSQL y ejecuta Maven Verify en cada push o pull request relevante.

## Alcance pendiente

Siguen abiertas tareas para compras y recepciones; clientes, devoluciones y transferencias; reservas; reabastecimiento y valoración; gestión de imágenes; integración n8n/WhatsApp; GraphQL; publicación de Outbox/Kafka; auditoría avanzada y hardening. No deben considerarse implementadas hasta que sus issues estén cerrados con código y pruebas.
