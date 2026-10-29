# Propuesta V1 — Plataforma reutilizable de integración asíncrona, webhooks salientes y reintentos

**Propósito:** formalizar una capacidad reusable entre Inventary y todos los backends del ecosistema: facturación electrónica, mailing, flota vehicular, analítica y futuras aplicaciones.
**Fecha:** 29 de octubre de 2026.
**Estado:** propuesta; no existe todavía una plataforma completa implementada.

Documentos relacionados:
- [Clientes HTTP resilientes](PROPUESTA_ARQUITECTURA_DISTRIBUIDA_CLIENTES_HTTP_RESILIENCIA_V1.md)
- [Kafka y caché](PROPUESTA_KAFKA_CACHE_CAFFEINE_V1.md)
- [Roles y ámbitos](PROPUESTA_RBAC_ROLES_PERMISOS_AMBITOS_V1.md)
- [Contrato fiscal SRI](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md)
- [Roadmap previo a Inventary V2](ROADMAP_CIERRE_ARQUITECTURA_INVENTARY_PRE_V2.md)

## 1. Decisión: sí, puede ser un backend reusable

Dado que varios backends necesitan reintentos, control de estado HTTP, autenticación, recepción de callbacks y auditoría, es razonable proponer un **Integration Hub / Integration Delivery Service** independiente. Otros backends podrían usarlo si comparten el mismo contrato de entrega durable y seguridad.

Pero no debe absorber las reglas de negocio:
- Inventary decide venta, recepción, stock y traslado.
- El backend fiscal genera/firmar/transmite comprobantes y devuelve su estado oficial.
- Mailing gestiona la entrega de mensajes.
- Flota gestiona vehículo, ruta y transporte.
- Analítica mantiene proyecciones.
- El Hub gestiona transporte, entrega durable, callbacks, estado de integración, reintentos y observabilidad.

No mover reglas de stock o tributarias a este Hub ni compartir tablas internas de Inventary.

### ¿Microservicio nuevo desde el primer día?

Diseñar el contrato reusable desde el principio y evolucionar por fases:
1. Definir contratos, seguridad, estados y modelo de delivery.
2. Implementar primero un dispatcher durable dentro del dominio de integración de Inventary si eso reduce riesgo.
3. Extraerlo como backend independiente cuando varios sistemas lo usen, necesite escalado/operación propia o requiera aislamiento de secretos.
4. Preservar un contrato API estable para que los productores no cambien todos a la vez.

Si varios backends ya necesitan la misma capacidad, también es válido crear el Hub independiente desde la primera etapa, pero debe tener owner, base durable propia, despliegue de alta disponibilidad, secretos gestionados, SLO, alertas y soporte. No decidir solo porque los microservicios “escalan más”: introducen consistencia eventual y coste operativo.

## 2. Responsabilidades y límites

### El Hub sí debe
- aceptar una solicitud de entrega autorizada;
- registrar operación con idempotencia;
- encolar durablemente;
- llamar al destino HTTP o publicar a Kafka;
- controlar HTTP status, timeouts, rate limit, retry, circuit breaker y límites de concurrencia;
- entregar webhooks firmados;
- recibir/verificar webhooks entrantes;
- guardar estados, intentos, IDs remotos y correlación;
- consultar estado y hacer redrive manual;
- manejar destinos y referencias a secretos por servicio/entidad;
- medir, alertar y mantener runbooks.

### El Hub no debe
- decidir si una factura cumple el SRI;
- generar XML fiscal si el otro backend es dueño de esa función;
- aprobar compras/cancelaciones;
- cambiar stock, movimientos o coste;
- inventar un resultado de correo, factura o vehículo;
- sustituir n8n en conversaciones y automatizaciones;
- aceptar URL arbitrarias proporcionadas por usuarios;
- devolver valores de secretos a operadores.

## 3. Componentes de la solución

1. **Integration API:** acepta operación/comando validado con idempotency key.
2. **Registry de integraciones:** destinos, capacidades, versión API, método de autenticación, límites y contratos.
3. **Delivery store durable:** operación, outbox, estado, intento, nextAttemptAt, lease y resultado seguro.
4. **Dispatcher/workers:** reclaman lote, llaman al destino fuera de la transacción de claim y actualizan el resultado.
5. **Webhook dispatcher:** entrega eventos salientes con firma y headers de trazabilidad.
6. **Inbound webhook gateway:** autentica proveedores, deduplica IDs y registra Inbox.
7. **Kafka adapter:** publica/consume eventos cuando se requiere bus.
8. **Status API:** consulta estado por operationId sin revelar secretos o payloads completos.
9. **Operator API/UI:** busca fallos, edad, error remoto seguro y permite redrive con motivo.
10. **Observability:** métricas, logs redactados, traces y alertas.
11. **Secret resolver:** lee secretRef desde Vault/KMS/secret manager.
12. **Schema/contract registry:** versiona eventos/comandos/requests/responses y compatibilidad.

## 4. Flujos de referencia

### 4.1 Solicitar factura
1. Inventary confirma su venta y guarda en PostgreSQL el vínculo fiscal pendiente y evento Outbox en la misma transacción.
2. Relay publica el evento o un worker toma la tarea durable.
3. Hub entrega solicitud idempotente al backend fiscal con saleId/sourceOperationId, snapshot comercial, contexto fiscal y correlationId.
4. Si el backend responde 202, marcar ACCEPTED/PENDING, no autorizada.
5. El backend fiscal notifica por webhook firmado o se consulta el estado.
6. Hub verifica origen/firma/deduplicación y persiste la respuesta.
7. Solo al confirmar estado fiscal se emite evento FiscalDocumentAuthorized/Rejected.
8. Inventary consume el resultado idempotentemente y actualiza su vínculo fiscal.
9. Reportes distingue pendientes, rechazadas, autorizadas, anuladas y conciliaciones.
10. Ante timeout, consultar por operationId/idempotency key antes de crear otra solicitud.

### 4.2 Solicitud de transporte
1. Traslado confirmado como despachado emite StockTransferDispatched.
2. Hub crea solicitud de transporte idempotente enlazada a transferId.
3. Flota acepta/rechaza y devuelve vehicleAssignmentId/estado.
4. Flota notifica asignación, salida, llegada o entrega.
5. Inventary mantiene su estado físico del traslado aparte de la asignación de vehículo.
6. El bodeguero de destino confirma la recepción; esa acción registra cantidades reales en Inventary.
7. Fallos de flota no deben modificar por sí solos el saldo de stock.

### 4.3 Mailing
1. Un hecho confirmado genera NotificationRequested con templateId y destinatario referenciado.
2. Hub deduplica por logicalNotificationId.
3. Proveedor acepta y devuelve providerMessageId.
4. Estado de entrega puede llegar después por callback.
5. Dirección inválida se trata como fallo permanente que requiere corrección; no repetir infinitamente.
6. La venta no se revierte por fallo de correo.

## 5. Contratos HTTP propuestos

Rutas ilustrativas, aún no implementadas:
- POST /api/v1/integration-operations: registra una operación async idempotente, devuelve 202 + operationId.
- GET /api/v1/integration-operations/{operationId}: estado, destino, resultado seguro, intentos, nextAttemptAt y correlationId.
- GET /api/v1/integration-operations: búsqueda paginada por partner/type/status/date/correlationId con scope.
- POST /api/v1/integration-operations/{operationId}/retry: redrive manual con permiso y motivo.
- POST /api/v1/integration-operations/{operationId}/cancel: solo si la operación/destino soporta cancelación efectiva.
- POST /api/v1/webhook-subscriptions: crea suscripción de un servicio a eventos permitidos.
- GET/PUT/DELETE /api/v1/webhook-subscriptions/{id}: gestiona vigencia y filtros, con historial.
- POST /api/v1/webhook-endpoints/{providerKey}/events: endpoint entrante específico por proveedor.
- GET /api/v1/webhook-deliveries/{deliveryId}: evidencia de intento.
- Health/readiness y métricas con acceso restringido.

El contrato incluye idempotency key, request hash canónico, schemaVersion, eventId, sourceOperationId, partnerId, correlationId, timestamps, estado, clave remota y error seguro. Los destinos deben registrarse por administración/infraestructura, nunca llegar arbitrariamente de un usuario.

## 6. Modelo durable recomendado

**integration_partner**
- id, code, nombre, owner/team, active, environment.

**integration_endpoint**
- id, partnerId, endpointKey, baseUrl, transport HTTP/KAFKA, API version, authType, secretRef, active, allowlistedHost, configVersion.
- El secreto se guarda en secret manager; la tabla contiene la referencia y metadatos no secretos.

**integration_policy**
- timeout/deadline, maxAttempts, backoff/jitter, retryable statuses, circuit threshold, concurrency/rate limits, payload max y retención.

**integration_operation**
- operationId, partnerId, type, sourceService, sourceOperationId, idempotencyKey, requestHash, status, createdAt, nextAttemptAt, completedAt, correlationId, causationId, schemaVersion y expiresAt.

**integration_attempt**
- attemptId, operationId, attemptNumber, start/end, outcome, HTTP status, safeErrorCode, remoteRequestId, latency y workerId. Payload/response solo redactado o hash cuando baste.

**webhook_subscription**
- id, consumer/serviceId, endpoint, eventos permitidos, secretRef, estado, schemaVersion, vigencia y creador.

**webhook_delivery**
- deliveryId, eventId, subscriptionId, attempts, nextAttemptAt, response status/latency y estado terminal.

**webhook_inbox**
- providerId + providerDeliveryId unique, payloadHash, resultado de validación de firma, receivedAt, processedAt y outcome.

**integration_audit**
- actor, acción, destino, motivo, correlationId, metadata redactada y timestamp.

Una operación lógica es distinta de un intento: retry incrementa el intento, pero no crea una nueva factura, correo o solicitud de transporte.

## 7. Estados normalizados

- RECEIVED: solicitud validada.
- PENDING: durable, esperando worker.
- PROCESSING: worker la reclama mediante lease.
- ACCEPTED: proveedor la aceptó pero no confirmó resultado final.
- SUCCEEDED: resultado final confirmado.
- RETRY_SCHEDULED: fallo transitorio con próxima fecha.
- FAILED_PERMANENT: error no reintentable.
- UNKNOWN_OUTCOME: timeout ambiguo; consultar destino.
- NEEDS_REVIEW: necesita intervención.
- CANCEL_REQUESTED: cancelación solicitada, no confirmada.
- CANCELLED: cancelación confirmada por el proveedor.
- DEAD_LETTER: agotó intentos y queda aislada.

PUBLISHED en Kafka no equivale a SUCCEEDED de una operación externa. El broker pudo aceptar un evento sin que una factura sea autorizada o un vehículo asignado.

## 8. HTTP y reintentos

- GET idempotente: retry limitado en 408, 429 y fallos transitorios.
- POST con idempotencia del proveedor: misma key, body canónico y sourceOperationId.
- POST sin deduplicación: tras timeout, marcar UNKNOWN_OUTCOME y consultar/conciliar antes de reintentar.
- 400/422: error de datos, sin retry automático.
- 401/403: detener y revisar identidad/permisos.
- 404: revisar recurso/contrato; retry solo con consistencia eventual documentada.
- 409: interpretar error de dominio; puede ser duplicado o conflicto.
- 429: respetar Retry-After dentro de límites y añadir jitter.
- 5xx/red/timeouts transitorios: backoff acotado y circuit breaker.
- TLS/certificado/firma inválida: detener, alertar y no degradar seguridad.

No combinar reintentos HTTP, Kafka y Hub sin un presupuesto total. El retry debe tener un dueño por fase.

## 9. Seguridad de webhooks salientes

Cada entrega:
- HTTPS y endpoint registrado;
- delivery ID único;
- event ID, event type, schema version, occurredAt y correlationId;
- firma HMAC-SHA256 o mecanismo acordado, calculada sobre timestamp + bytes exactos del body;
- timestamp para proteger contra replay;
- rotación de secretos con versionado/solapamiento;
- misma identidad y contenido lógico en retry;
- destino protegido contra SSRF, hosts no autorizados, localhost/redes privadas no permitidas, redirects, DNS rebinding;
- respuesta HTTP, tiempo, intento y error sanitizado guardados;
- secretos fuera del payload.

Verificar firma sobre los bytes exactos recibidos y comparar constant-time. No tratar el endpoint secreto como único control de seguridad.

Referencia: https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries

## 10. Seguridad de webhooks entrantes

1. Asociar endpoint a un proveedor conocido y método específico.
2. Verificar firma/token, timestamp, audience y cuenta cuando aplique.
3. Comprobar tamaño/contenido antes de parsear.
4. Registrar providerDeliveryId con unique constraint y hash de payload.
5. Duplicado devuelve resultado idempotente sin repetir efectos.
6. Guardar ingreso durable antes de confirmar recepción cuando exista procesamiento posterior.
7. Ejecutar negocio por worker si el proveedor exige respuesta rápida.
8. No ejecutar ningún comando por callback no verificado.
9. Usar autenticación firmada, mTLS o token rotatorio.
10. Protegerse de replay, payloads excesivos y campos inesperados.

El callback fiscal valida operación/documento contra la solicitud original. No aceptar únicamente texto libre “autorizada”.

## 11. Kafka o webhook

- Kafka: eventos de dominio entre sistemas del bus, fan-out y proyecciones analíticas.
- Webhook: notificación HTTP hacia proveedores que ofrecen callbacks y apps que no consumen Kafka.
- REST síncrono: validación/consulta que necesita respuesta inmediata.
- Outbox/dispatcher durable: asegura que la entrega pendiente sobreviva a un reinicio.

No crear Kafka→webhook directo sin registro durable de delivery.

## 12. Permisos y aislamiento

- El productor solo solicita tipos de operación autorizados.
- Mailing no puede ordenar emisión fiscal.
- Flota no cambia stock.
- El cliente fiscal no consulta eventos de otras entidades.
- integration:delivery:retry requiere scope, motivo y auditoría.
- Secret resolver entrega secretos solo al proceso que llama; no los expone.
- Suscripción a eventos necesita aprobación; no todos los consumidores reciben todo.

## 13. Observabilidad y alertas

Métricas:
- operations_total por partner/type/status;
- attempts por partner/outcome/http class;
- oldest pending age;
- webhook latency;
- retries y DLT;
- 429, circuit state;
- invalid signature, duplicate delivery;
- unknown outcome y permanent failures.

Alertas para backlog, antigüedad, autenticación fallida, callbacks con firma inválida, spikes de 429/5xx, DLT sin atender, destino sin respuesta dentro del SLA, operación fiscal sin conciliar.

El operador puede redrivear tras diagnosticar, no modificar el request original para “hacerlo pasar”.

## 14. Alternativas evaluadas

| Alternativa | Ventaja | Riesgo |
|---|---|---|
| Cada backend implementa webhooks propios | Inicio simple | Policies, auditoría y recuperación divergentes |
| n8n maneja todos los retries | Flexible para automatizaciones | Puede convertirse en fuente de verdad para comandos críticos; el estado durable/idempotencia necesita otro diseño |
| Kafka solamente | Buen fan-out de eventos | Proveedores HTTP no consumen Kafka; hace falta bridge durable |
| Integration Hub compartido | Auth, retry, estado, webhook y observabilidad comunes | Servicio crítico adicional que exige HA, seguridad y soporte |
| Librería común | Menos duplicación de cliente | Cada backend opera su cola y estado; no hay delivery central |

Recomendación: Integration Hub + Kafka si varios backends requieren la misma capacidad. La librería común sigue siendo útil para clientes HTTP, pero no sustituye el estado durable. n8n puede continuar en conversaciones y automatizaciones sin custodiar ventas, stock ni estados fiscales.

## 15. Roadmap

A. Contratos, catálogo de destinos, estados, auth y esquema.  
B. Dispatcher durable con lease, retry, HTTP status e idempotencia.  
C. Webhooks salientes firmados e inbound gateway con Inbox.  
D. Integración fiscal y callbacks de estados de factura.  
E. Mailing, flota y otros adaptadores.  
F. Despliegue independiente, HA, secret manager, tracing, métricas, alertas y runbooks.

## 16. Pruebas obligatorias

- Operación sobrevive a caída de base, worker, red o proveedor.
- Misma idempotency key/body no duplica; distinto body produce conflicto.
- Timeout ambiguo consulta estado antes de POST no idempotente.
- Firma/timestamp/tamaño/delivery ID validado.
- Firma mala, replay y duplicados no crean efectos.
- Destino no puede provocar SSRF.
- Retry-After/backoff/jitter aplicados.
- Dos workers no ejecutan el mismo lease.
- Respuesta 202 no se marca final.
- Cambio de esquema tiene prueba de compatibilidad.
- Operación fiscal se reconcilia por operationId/estado confirmado.
- Operadores no modifican payload ni reciben secretos.
- La evidencia durable y auditoría sobreviven al reinicio.

## 17. Fuentes
- Transactional Outbox: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- Spring REST Clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Kafka retry topics: https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Validación de webhooks: https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries
- OWASP Authorization: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html

## 18. Límite

Las rutas son contratos ilustrativos. Este documento no implementa el Hub. Antes de que otros backends dependan de él se requieren OpenAPI, scopes, pruebas de carga, gestión de secretos y acuerdos de SLA/ownership.
