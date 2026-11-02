# 27 — Guía práctica de consumo de APIs

## 1. Requisitos

- API backend Spring Boot y PostgreSQL disponibles.
- Java 21 en backend.
- Para desarrollo directo, configurar URL base y CORS.
- Para Docker raíz, el navegador usa el proxy Nginx y la ruta /inventary/api/v1.

## 2. Login y token

El login utiliza desafío de cifrado de credenciales. La SPA ejecuta el flujo del cliente y conserva el token según su módulo de sesión. La API valida firma, emisor, audiencia, expiración, estado del usuario y versión del token.

Consulta permisos efectivos con:

~~~bash
curl -H "Authorization: Bearer $TOKEN" "$API/auth/me"
~~~

La autenticación JWT protege la mayoría de endpoints, pero hay dos excepciones críticas en la configuración actual: /tax/** y /refunds/** son permitAll. No tomes la ausencia de 401 en esas rutas como comportamiento intencional seguro.

## 3. Crear producto

El catálogo valida SKU, categoría, tipo, moneda, unidad y esquema de atributos en servidor. Las reglas de campos reales deben consultarse en OpenAPI del SHA desplegado.

~~~bash
curl -X POST "$API/catalog/products" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-DEMO","name":"Producto demo","categoryId":"UUID-CATEGORIA","costPrice":1.00,"salePrice":1.50,"currency":"USD"}'
~~~

## 4. Compra, recepción y devolución

Flujo:
1. Crear orden.
2. Enviar a aprobación.
3. Aprobar con permiso distinto cuando aplica la segregación configurada.
4. Registrar recepción parcial/completa con Idempotency-Key.
5. Consultar recepción y cantidad retornable en servidor.
6. Registrar devolución desde una recepción contabilizada.

~~~bash
curl -X GET "$API/goods-receipts/UUID-RECEPCION/returnable-lines" \
  -H "Authorization: Bearer $TOKEN"
~~~

Una orden no incrementa existencias. La recepción contabilizada lo hace. Una devolución física no equivale a nota de crédito ni pago al proveedor.

## 5. Venta, cancelación y devolución

Crear venta con clave idempotente:

~~~bash
curl -X POST "$API/sales" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"warehouseId":"UUID-ALMACEN","channel":"POS","items":[{"productId":"UUID-PRODUCTO","quantity":1}]}'
~~~

El servidor calcula precio/reglas, valida disponibilidad y registra movimientos/costeo. La cancelación requiere una solicitud y un aprobador distinto según las reglas del dominio. La devolución física y el reembolso monetario son documentos/estados distintos.

## 6. Inventario y dashboard

~~~bash
curl "$API/inventory/command-center" -H "Authorization: Bearer $TOKEN"
curl "$API/inventory/balances?warehouseId=UUID-ALMACEN&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
~~~

El endpoint SSE del dashboard es:

~~~bash
curl -N "$API/dashboard/events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream"
~~~

El stream emite conexión y señales snapshot con marca temporal; el frontend vuelve a consultar sus endpoints protegidos. No es un mensaje financiero ni un feed completo de movimientos.

## 7. WhatsApp backoffice

- GET /whatsapp-orders: bandeja paginada.
- GET /whatsapp-orders/work-summary: contadores operativos.
- GET /whatsapp-orders/{id}: detalle.
- POST /whatsapp-orders/{id}/confirm-payment: confirmar pago manualmente con clave idempotente y permiso sales:manage.

La confirmación significa que un usuario autorizado registró la verificación externa. No se llama a una pasarela.

## 8. Operaciones de integración V28

La creación de una operación usa integration:manage e Idempotency-Key:

~~~bash
curl -X POST "$API/integration-operations" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: OP-UUID" \
  -d '{"operationType":"EXAMPLE","partnerCode":"PARTNER","payload":{"reference":"123"}}'
~~~

Consulta GET /integration-operations/{operationId} con integration:read. El estado QUEUED significa encolado, no completado. En el código auditado no se encontró un worker completo que procese la operación y concilie el resultado externo.

Las rutas webhook-subscriptions administran metadatos de suscripción; no debe inferirse que la entrega HTTP esté implementada.

## 9. Idempotencia y resultado incierto

Mismo key + mismo payload: replay lógico del comando cuando el endpoint lo soporta. Mismo key + cuerpo distinto: conflicto. Si hay timeout después de enviar una escritura, consulta primero el recurso/estado para no duplicar stock, venta o recepción.

No reutilizar una clave de una operación para otra ni modificar el payload entre reintentos.

## 10. Filtros, paginación y errores

- page habitualmente es base cero; size máximo suele ser 100, pero verifica cada endpoint.
- Los rangos de reportes usan la zona configurada; por defecto America/Guayaquil.
- Errores comunes: 400 validación/query, 401 autenticación, 403 autorización, 404 recurso, 409 conflicto, 413 body, 415 content type, 422 regla de negocio, 429 Retry-After.
- No devolver stack traces, SQL ni secretos.

## 11. Seguridad y límites

No usar APIs tax ni refunds desde una exposición de red no confiable hasta eliminar permitAll y probar 401/403. La importación tributaria es manual y no verifica ante el SRI; las tarifas no emiten comprobantes. No simular pagos, notas de crédito ni autorización fiscal. Kafka y las operaciones externas siguen opt-in/incompletas.