# 27 — Guía práctica de consumo de APIs

El catálogo de rutas está en 25-referencia-apis.md. Los DTOs y el OpenAPI del mismo build son la autoridad final para campos, tipos y respuestas.

## 1. Requisitos y ejecución

Requisitos: Java 21, PostgreSQL compatible, Node 22.12.x, npm 10/11. Obtener valores reales para las variables desde los archivos .env.example, sin subir .env ni usar claves de ejemplo en producción.

Backend:

~~~bash
cd microservices-inventary-ecuapp
./mvnw -B verify
./mvnw spring-boot:run
~~~

Windows: mvnw.cmd. Configurar SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS y credenciales de bootstrap siguiendo .env.example. JWT_SECRET debe ser aleatorio y de al menos 32 bytes. Flyway realiza migraciones; nunca usar ddl-auto=update ni alterar balances manualmente.

Frontend:

~~~bash
cd app-inventary-ecuapp
cp .env.example .env
npm install
npm run dev
~~~

La base URL se configura en VITE_API_BASE_URL; el valor local predeterminado es http://localhost:8080/api/v1. CORS_ALLOWED_ORIGINS debe aceptar el origen real del frontend, normalmente http://localhost:5173. Para producción, npm run build genera dist. El árbol de referencia no incluye package-lock.json; crear/versionar lockfile antes de adoptar npm ci.

## 2. Login y token

1. Pedir POST /api/v1/auth/login-challenge y enviar POST /api/v1/auth/login con las credenciales cifradas (ver el contrato en 25-referencia-apis.md, sección 1).
2. Usar accessToken como Bearer en todas las rutas privadas.
3. Consultar GET /api/v1/auth/me para conocer identidad, rol y permisos.
4. Manejar expiresAt; al recibir 401, limpiar sesión y volver a login.
5. POST /api/v1/auth/logout cierra la sesión en el backend.
6. El controlador actual no expone refresh token.

~~~bash
export API='http://localhost:8080/api/v1'
export TOKEN='PEGAR_ACCESS_TOKEN'
curl -sS "$API/auth/me" -H "Authorization: Bearer $TOKEN"
~~~

Nunca colocar el token en query string, logs, tickets, capturas o Git. El permiso efectivo del usuario no sustituye las reglas server-side.

## 3. Crear producto

Leer primero las categorías y unidades permitidas:

~~~bash
curl -sS "$API/catalog/categories?page=0&size=20" -H "Authorization: Bearer $TOKEN"
curl -sS "$API/catalog/units" -H "Authorization: Bearer $TOKEN"
~~~

Crear un producto:

~~~bash
curl -sS -X POST "$API/catalog/products" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "sku":"SKU-001",
    "name":"Cable USB-C",
    "description":"Cable de 1 m",
    "categoryId":"UUID-CATEGORIA",
    "costPrice":2.10,
    "salePrice":4.50,
    "currency":"USD",
    "attributes":{"lengthCm":100}
  }'
~~~

El ejemplo es válido solo si la categoría declara lengthCm como atributo y acepta el tipo/valor. El backend valida SKU, importes, categoría, unidad y atributos. Campos desconocidos causan error.

## 4. Compra, recepción y stock

1. POST /purchase-orders con supplierId, warehouseId, items(productId, quantity, unitCost) crea una orden borrador.
2. POST /purchase-orders/{id}/submit.
3. POST /purchase-orders/{id}/approve con purchasing:approve y aprobador distinto del creador cuando se exige segregación.
4. POST /purchase-orders/{id}/receipts con purchaseOrderLineId y quantity recibida.
5. Consultar /inventory/balances y /inventory/movements.

Ejemplo de orden:

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

Ejemplo de recepción parcial:

~~~json
{
  "items": [
    {"purchaseOrderLineId": "UUID-LINEA-ORDEN", "quantity": 4}
  ]
}
~~~

La orden no incrementa existencias hasta que la recepción se contabiliza. Una recepción parcial de 4 unidades aumenta el stock por 4, no por la cantidad total solicitada. Las devoluciones a proveedor refieren a recepción contabilizada y respetan unidades no devueltas y stock disponible.

## 5. Venta, cancelación y devolución

POST /sales recibe warehouseId, channel, items con productId y quantity, y datos de cliente cuando aplican. El servidor consulta el precio autorizado del catálogo; no enviar ni confiar en precio autoritativo desde el navegador. La disponibilidad se valida otra vez dentro de la transacción.

Para anular:
1. POST /sales/{id}/request-cancellation, con motivo.
2. Un actor distinto con sales:cancel ejecuta POST /sales/{id}/approve-cancellation.

No borrar una venta contabilizada para corregirla. Usar POST /sales-returns de acuerdo con su DTO, limitando la devolución a la cantidad vendida y todavía no devuelta. El destino puede ser RESTOCK o SCRAP según contrato.

## 6. Consultar balance por fechas

El balance requiere from, to y warehouseId; productId y zone son opcionales. El usuario debe tener reports:financial.

~~~bash
curl -G "$API/reports/inventory-balance" \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'from=2026-10-01' \
  --data-urlencode 'to=2026-10-26' \
  --data-urlencode 'warehouseId=UUID-ALMACEN' \
  --data-urlencode 'zone=America/Guayaquil' \
  --data-urlencode 'page=0' --data-urlencode 'size=50'
~~~

La respuesta incluye rango, zona, generatedAt, paginación e items. closingStock es el saldo histórico al final del periodo y puede ser distinto de currentStock si hubo movimientos posteriores.

Para verificar reposición: recibir 10, vender 10, recibir otras 15 unidades del mismo producto, y consultar rangos anteriores/posteriores a la nueva recepción. Añadir devolución, cancelación aprobada y kardex/valoración. Ejecutar contra PostgreSQL real, no solo mocks.

## 7. Idempotencia y resultado incierto

Para comandos compatibles, enviar Idempotency-Key con una UUID nueva por operación lógica:

~~~bash
export IDEMPOTENCY_KEY="$(uuidgen)"
curl -X POST "$API/sales" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{"warehouseId":"UUID-ALMACEN","channel":"POS","items":[{"productId":"UUID-PRODUCTO","quantity":1}]}'
~~~

En un reintento lógico, conservar clave y payload. No reutilizar la misma clave con otro cuerpo ni en otra operación. Si la conexión falla después del envío, el resultado es ambiguo: consultar la venta, orden, movimiento o saldo antes de crear otra petición. La caché de clave del frontend solo vive temporalmente en la pestaña, y no sobrevive a recargas.

## 8. Filtros y paginación

page suele ser base cero y size debe ser <=100. Ordenar solo por campos admitidos por el endpoint. Balance requiere warehouseId; kardex requiere productId y warehouseId. Las fechas se interpretan mediante la zona del informe, por defecto America/Guayaquil; fijar zone cuando el consumidor necesite reproducir otro corte.

## 9. Errores

El contrato común usa application/problem+json, status y requestId, con detalle seguro y code cuando corresponde.

| HTTP | Acción |
|---|---|
| 400 | Corregir ruta, JSON o filtros |
| 401 | Limpiar token y volver a login |
| 403 | Resolver permiso/rol; no insistir |
| 404 | Confirmar UUID/estado |
| 409 | Consultar estado antes de repetir comando |
| 413 | Reducir body |
| 415 | Enviar content-type esperado |
| 422 | Corregir regla/valor de negocio |
| 429 | Respetar Retry-After |
| 5xx/red caída | Comprobar si el comando se persistió antes de repetir |

Nunca devolver stack trace/SQL ni registrar Authorization, password o X-Integration-Key.

## 10. WhatsApp/n8n

Enviar X-Integration-Key desde un secreto del workflow. La identidad del cliente debe derivarse de una notificación validada del canal, no de texto libre o salida de LLM. El backend calcula precios, cotiza con expiración y revalida al crear el pedido. La confirmación de pago es manual; no hay pasarela ni reembolso integrado.

## 11. Criterios de integración fiable

Antes de producción, verificar OpenAPI, errores y permisos por HTTP, CORS, migraciones V1–V14 desde cero/upgrade, escenarios de stock agotado y reposición, devoluciones, cancelación, ledger, concurrencia, idempotencia y backup/restore. Documentar el comando ejecutado, versiones y SHA exacto.
