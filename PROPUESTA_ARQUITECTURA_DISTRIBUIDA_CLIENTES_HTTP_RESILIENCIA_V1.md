# Propuesta V1 — Clientes HTTP resilientes y comunicación entre backends

**Propósito:** preparar Inventary para consumir servicios externos de forma consistente, observable y segura sin modificar ahora el código.
**Fecha:** 29 de octubre de 2026.
**Estado:** propuesta técnica; las capacidades descritas no se consideran implementadas hasta incorporarse y probarse.

Documentos complementarios:
- [Kafka, Outbox y caché](PROPUESTA_KAFKA_CACHE_CAFFEINE_V1.md)
- [Integración asíncrona y webhooks reutilizables](PROPUESTA_PLATAFORMA_INTEGRACIONES_ASINCRONAS_WEBHOOKS_V1.md)
- [Roles, permisos y ámbitos](PROPUESTA_RBAC_ROLES_PERMISOS_AMBITOS_V1.md)
- [Plan integral previo a Inventary V2](ROADMAP_CIERRE_ARQUITECTURA_INVENTARY_PRE_V2.md)
- [Contrato de facturación electrónica SRI](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md)

## 1. Resumen y recomendación

Inventary debe disponer de una capa uniforme para comunicarse con cada backend externo. Esta capa estandariza URL, configuración, autenticación de servicio, timeouts, clasificación de errores HTTP, reintentos, circuit breaker, límites de concurrencia, idempotencia, trazabilidad, métricas y manejo de respuestas asíncronas.

La recomendación es conservar Spring Boot como backend transaccional de Inventary. Para llamadas HTTP salientes, centralizar la lógica común en un módulo de clientes de integración y crear un cliente tipado por servicio de destino. No permitir que cada servicio de dominio construya llamadas HTTP ad hoc con timeouts, headers, reintentos y tratamiento de errores diferentes.

El backend observado utiliza Spring Boot 3.5.6, Java 21 y Spring MVC. Para peticiones síncronas nuevas, Spring Framework ofrece RestClient como cliente HTTP síncrono moderno. WebClient es alternativa no bloqueante para streaming, concurrencia alta o flujos reactivos; no se recomienda migrar todo el backend MVC a WebFlux solo para consumir otros servicios.

Referencias:
- Spring REST clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- Resilience4j: https://resilience4j.readme.io/docs/getting-started

## 2. Inventario de backends y decisión por interacción

| Sistema destino | Interacción | Modelo | Reglas principales |
|---|---|---|---|
| Backend fiscal SRI | Solicitar emisión, consultar estado, solicitar anulación/notas de crédito conforme al contrato fiscal | Solicitud síncrona breve o asíncrona; autorización posterior | HTTP 200/202 no basta por sí solo para declarar autorizada una factura |
| Mailing/notificaciones | Correo, SMS o notificación de un hecho confirmado | Asíncrono | No bloquear la venta; deduplicar por notificationId y aplicar límites por proveedor |
| Control de flota | Solicitar recogida/vehículo para un traslado aprobado | Asíncrono, con aceptación inmediata opcional | La asignación de vehículo no equivale a contabilizar la recepción de stock |
| Analítica de ventas | Alimentar proyecciones e indicadores | Asíncrono por Kafka, CDC o lote controlado | La analítica construye proyecciones; Inventary conserva la fuente transaccional |
| Identidad/organización, si se adopta | Resolver identidad, scope o perfil legal | Síncrono para autorización indispensable; eventos para cambios | No crear una dependencia remota innecesaria en cada operación de stock |
| n8n/WhatsApp | Webhooks entrantes y comandos de canal | Webhook de entrada + API interna + eventos posteriores | Inventary mantiene precio y disponibilidad autoritativos |
| Otros servicios futuros | Consulta o comando tipado | Según necesidad de negocio | No encadenar llamadas síncronas solo porque sea posible |

Cada integración debe declarar propietario, URL por entorno, contrato/versionado, método de autenticación, criticidad, presupuesto de latencia, política de retry, límite de concurrencia, comportamiento ante indisponibilidad y responsable operativo.

## 3. Arquitectura por capas

1. **Dominio/caso de uso:** decide qué operación se solicita y qué estado interno debe guardarse.
2. **Puerto de aplicación:** interfaz tipada, por ejemplo FiscalBillingPort, MailingPort o FleetDispatchPort.
3. **Adaptador HTTP/Kafka:** implementa el puerto y convierte request/response externo a DTO interno.
4. **Cliente común de transporte:** timeout, autenticación, requestId, correlationId, parsing, límites y clasificación HTTP.
5. **Políticas de resiliencia:** retry permitido, circuit breaker, bulkhead, rate limiting y deadline.
6. **Orquestación durable:** para acciones críticas o lentas, el Outbox crea una operación pendiente y un dispatcher la procesa fuera de la transacción de venta/compra.
7. **Persistencia de integración:** guarda estado externo, ID remoto, intentos y resultado; no usar la memoria del proceso como única cola de trabajo.

El cliente común estandariza el transporte; cada adaptador conoce su contrato y la semántica de aceptación/finalización de su proveedor.

## 4. Clientes recomendados en Spring Boot

### 4.1 Estilo de cliente
- **RestClient:** opción predeterminada para llamadas síncronas en Spring MVC.
- **WebClient:** solo si existe una necesidad real de I/O no bloqueante, streaming o composición reactiva.
- No añadir una biblioteca declarativa únicamente por moda; evaluar si el equipo necesita contratos generados.
- Crear beans configurados por destino; no crear un cliente nuevo por petición.
- No repartir retries entre varias capas sin declarar cuál tiene la responsabilidad principal.

### 4.2 Clientes tipados por destino
- **FiscalClient:** submitInvoice, getInvoiceStatus, requestCancellation, getCreditNoteStatus, según contrato externo aprobado.
- **FleetClient:** requestTransport, getTransportStatus y cancelTransport si el proveedor lo soporta.
- **NotificationClient:** enqueueNotification y getDeliveryStatus si existe tracking.
- **Analytics:** preferir eventos para alimentar proyecciones en vez de una llamada HTTP por transacción.
- **IntegrationDeliveryClient:** consulta de entregas si se adopta el Hub reusable del documento de plataforma.

Los clientes devuelven modelos internos tipados, no mapas libres que obliguen a cada consumidor a interpretar JSON.

### 4.3 Configuración externa
Cada cliente obtiene de configuración:
- base URL por entorno y allowlist de hosts;
- connect timeout, response/read timeout y deadline total;
- identidad de servicio y secret reference;
- pool, conexiones máximas y límites por host;
- tamaño máximo de request/response;
- política de error HTTP y retry;
- rate limit, bulkhead y circuit breaker;
- nivel de log y campos sensibles;
- versión de API aceptada.

No almacenar secretos en Git, en el frontend, en payloads de eventos o en logs. En producción usar un gestor de secretos y un mecanismo de rotación. Los secretos nunca deben devolverse por el API administrativo.

## 5. Control de códigos HTTP

La clasificación es política inicial, ajustable al contrato de cada proveedor.

| Resultado | Semántica común | Acción |
|---|---|---|
| 200/201 | Operación finalizada sincrónicamente o recurso creado | Validar cuerpo y guardar ID/resultado; no inferir autorización fiscal sin un estado formal |
| 202 | Solicitud aceptada, resultado pendiente | Guardar operationId/correlationId; marcar ACCEPTED/PENDING y esperar callback o consultar estado |
| 204 | Éxito sin cuerpo | Solo si el contrato permite respuesta vacía |
| 301/302/307/308 | Redirección | No seguir ciegamente con credenciales; rechazar destinos no aprobados |
| 400 | Request inválido | No retry automático; corregir datos/mapeo |
| 401 | Credencial inválida/expirada | No retry agresivo; alerta y refresh solo con mecanismo acordado |
| 403 | Falta de permiso/scope | No retry; revisar configuración de autorización |
| 404 | Recurso inexistente o endpoint incorrecto | No retry salvo consistencia eventual documentada para lectura |
| 408 | Timeout del lado servidor/proxy | Retry solo si la operación es segura e idempotente |
| 409 | Conflicto o duplicado | Interpretar código de dominio; puede indicar recurso ya creado o transición inválida |
| 422 | Datos semánticamente inválidos | No retry automático; corregir información |
| 429 | Rate limit | Respetar Retry-After válido y usar backoff con jitter |
| 500/502/503/504 | Fallo transitorio del servidor/gateway | Retry limitado para operaciones seguras y circuit breaker según umbral |
| Timeout sin respuesta | Resultado posiblemente desconocido | No asumir que el servidor no actuó; consultar por operationId/idempotency key |
| DNS/TLS/certificado | Infraestructura/configuración/seguridad | No reintentar infinitamente; alerta y corrección |

Reglas adicionales:
- Leer el body de error para identificar código remoto, mensaje seguro y request ID.
- Sanitizar mensajes: no guardar tokens, cookies, certificados ni cuerpos completos con PII.
- Taxonomía interna sugerida: TRANSIENT, PERMANENT, AUTHENTICATION, AUTHORIZATION, CONFLICT, RATE_LIMIT, UNKNOWN_OUTCOME y CONFIGURATION.
- No mostrar al frontend los errores brutos del backend externo.

## 6. Timeouts y presupuesto de latencia

Definir por separado timeout de conexión, timeout de respuesta, deadline del caso de uso y timeout del job.

Punto de partida para medir, no SLA prometido:
- lectura interna: conexión 1–2 s; respuesta 2–3 s;
- servicio externo síncrono: conexión 1–2 s; respuesta 3–5 s;
- operación que necesita validación inmediata y admite espera mayor: máximo 8–10 s solo si la experiencia y el negocio lo permiten;
- operación potencialmente larga: no mantener la petición abierta; devolver 202 con ID y permitir consultar estado.

Calibrar según latencias p95/p99 de cada destino. La llamada interna debe consumir menos que el deadline de quien la invoca, dejando tiempo para cerrar la operación y responder.

Nunca mantener una transacción PostgreSQL abierta mientras se espera una llamada de red lenta. Primero registrar operación local durable y luego despacharla de forma asíncrona, salvo validación síncrona indispensable y justificada.

## 7. Reintentos: matriz de seguridad

| Operación | ¿Retry? | Condición |
|---|---|---|
| GET idempotente | Sí, acotado | Solo fallo transitorio y dentro de deadline |
| GET de recurso aún no propagado | Solo según contrato | Consistencia eventual conocida, backoff y límite |
| POST crear factura | Solo con idempotencia del proveedor | Misma key/body/sourceOperationId y consulta de estado |
| POST solicitar vehículo | Solo con deduplicación externa | Evitar asignación doble de vehículo |
| POST enviar correo | Si proveedor soporta deduplicación o duplicados están aceptados explícitamente | Key lógica por notificación |
| PUT/PATCH idempotente | Depende del contrato | Repetir mismo estado deseado |
| POST sin idempotencia | No tras timeout ambiguo | Consultar estado o revisión manual |
| 400/401/403/404/409/422 | Normalmente no | Solo excepciones documentadas |
| 429/5xx/timeout transitorio | Sí, limitado | Retry-After, backoff, jitter y circuit breaker |

Para llamadas cortas: hasta 2–3 intentos totales como punto inicial, solo para operaciones seguras. Para comandos externos, preferir retry durable del dispatcher. No combinar automáticamente 3 retries de cliente y 10 del dispatcher sin un presupuesto coordinado.

## 8. Resilience4j

Introducir módulos solo donde haya necesidad verificable:
- Retry: solo fallos transitorios declarados.
- CircuitBreaker: deja de llamar a un destino persistentemente fallido y permite recuperar gradualmente.
- Bulkhead: limita concurrencia por proveedor.
- RateLimiter: respeta cuotas externas.
- TimeLimiter: limita duración cuando el tipo de operación lo permite; no garantiza cancelar una ejecución remota.
- Fallback: únicamente para lecturas no críticas. Nunca simular factura, pago o asignación de vehículo.

Configurar métricas por integración y distinguir error técnico de 400/422 funcional. Un circuito abierto debe producir estado degradado/pendiente entendible, no un 500 genérico.

Referencias:
- Retry: https://resilience4j.readme.io/docs/retry
- CircuitBreaker: https://resilience4j.readme.io/docs/circuitbreaker
- RateLimiter: https://resilience4j.readme.io/docs/ratelimiter

## 9. Estado durable de operación remota

Cada comando externo importante requiere ID durable y estado:
- CREATED;
- PENDING_DELIVERY;
- IN_FLIGHT;
- ACCEPTED;
- SUCCEEDED;
- RETRY_SCHEDULED;
- FAILED_PERMANENT;
- UNKNOWN_OUTCOME;
- NEEDS_REVIEW;
- COMPENSATION_PENDING / COMPENSATED, solo si hay compensación formal.

Guardar operationId, partner, externalId, idempotency key, requestHash, attemptCount, nextAttemptAt, lastStatusCode, safeErrorCode, createdAt, updatedAt, completedAt y correlationId. Payload sensible solo si es necesario y con retención definida.

No guardar solo el último HTTP code. Separar la operación lógica de sus intentos.

## 10. Seguridad de servicio a servicio

- Credenciales de máquina distintas de sesiones humanas.
- Preferir OAuth 2.0 client credentials, mTLS o HMAC según contrato.
- Validar certificado y hostname TLS; nunca trust-all.
- Allowlist de destino para prevenir SSRF.
- No seguir redirects a hosts no permitidos.
- No poner secretos ni datos personales en URL.
- No incluir Authorization, firmas, XML fiscal completo o PII en logs.
- Least privilege por servicio: mailing no necesita costes de inventario; analítica no requiere credenciales fiscales.

## 11. Observabilidad

Métricas de baja cardinalidad:
- solicitudes por partner/operación/resultado;
- clases de status HTTP;
- latencia p50/p95/p99;
- retries por razón;
- estado de circuit breaker;
- operaciones pendientes y edad de la más antigua;
- UNKNOWN_OUTCOME;
- fallos de autenticación.

No etiquetar por RUC, cliente, invoice ID o URLs arbitrarias. Guardar IDs específicos en logs estructurados con permisos y retención. Transportar requestId/correlationId/operationId y añadir tracing distribuido cuando el ecosistema lo permita.

## 12. Pruebas y aceptación

- Timeout de conexión/lectura y pérdida de respuesta tras que el proveedor ejecutó el comando.
- Respuestas 200 con payload inválido, 202, 204, 400, 401, 403, 404, 409, 422, 429, 500, 502, 503 y 504.
- Retry conserva key y body; distinto cuerpo con la misma key produce conflicto.
- Circuit breaker abre y recupera.
- Un destino lento no bloquea todos los hilos.
- No se reintenta POST no idempotente tras resultado desconocido.
- Retry-After inválido/excesivo se limita de forma segura.
- No se sigue redirección a host no aprobado.
- TLS inválido y credencial rechazada no filtran secretos.
- El Outbox continúa procesando cuando un destino falla.
- Reiniciar el proceso no pierde operaciones pendientes.
- Pruebas con MockWebServer/WireMock o servidor controlado.
- Medir p50/p95/p99 antes de declarar SLA.

## 13. Entregables

1. Catálogo de integraciones y contratos.
2. Módulo común de configuración, clientes y errores.
3. Clientes tipados por destino.
4. Registro durable de operaciones asíncronas.
5. Matriz de HTTP status/retry por operación.
6. Logs, métricas, trazas, alertas y runbook por proveedor.
7. Tests de fallos, idempotencia y resultados ambiguos.
8. OpenAPI/AsyncAPI o contrato equivalente.
9. Documentación actualizada y evidencia de CI ligada al SHA desplegable.

## 14. Fuentes técnicas
- Spring REST Clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- Resilience4j Retry: https://resilience4j.readme.io/docs/retry
- Resilience4j CircuitBreaker: https://resilience4j.readme.io/docs/circuitbreaker
- Resilience4j RateLimiter: https://resilience4j.readme.io/docs/ratelimiter
- Transactional Outbox: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html

## 15. Límite

Esta propuesta no implementa clientes ni circuit breakers. La primera entrega debe priorizar los destinos fiscales, mailing y flota según riesgo y construir la base común antes de añadir otras integraciones.
