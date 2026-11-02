# Fase 7 — Integraciones durables y contrato fiscal

**Código auditado:** dc028a346ccf7b94cfbb60b4fb6cd5493518b481 · **Estado:** EN PROGRESO.

## Infraestructura presente

- Transactional Outbox con envelope versionado: schemaVersion, eventId, aggregate identity, event type, occurredAt, payload y metadatos de correlación/causalidad.
- OutboxRelay reclama lotes con transacción corta, FOR UPDATE SKIP LOCKED y lease; envía fuera de la transacción y registra resultado/reintento en otra transacción. La entrega es al menos una vez.
- Inbox persistente con validación de versión, canonicalización y deduplicación por eventId/hash. Mismo eventId y payload distinto es conflicto.
- Kafka es opt-in y continúa apagado por defecto. El consumidor existente invalida caché de catálogo; no genera proyecciones analíticas generales.
- Endpoints de operación Outbox para stats, consulta y retry auditado.
- Migración V28 crea tablas integration_operation, webhook_subscription y webhook_delivery e incorpora permisos integration:read/manage.
- IntegrationOperationController ofrece creación idempotente con estado QUEUED, consulta/listado, reintento administrativo, y CRUD limitado de suscripciones de webhook.
- Dashboard SSE autenticado: emite conexión y señales snapshot periódicas; el cliente refresca consultas protegidas.

## APIs agregadas por V28

- POST /api/v1/integration-operations: crear operación QUEUED con integration:manage e Idempotency-Key.
- GET /api/v1/integration-operations y GET /{operationId}: lectura, integration:read.
- POST /api/v1/integration-operations/{operationId}/retry: reencolar estados terminales, integration:manage.
- POST /api/v1/webhook-subscriptions, GET /api/v1/webhook-subscriptions y DELETE /{id}: administración de suscripciones.

## Lo que todavía no está implementado

- No se encontró worker que consuma integration_operation, ejecute llamadas a sistemas externos, controle outcomes ambiguos y actualice status/completed_at.
- No se encontró dispatcher de webhook_delivery con firma HMAC, timestamp/replay protection, límites, retry/backoff, DLQ y redrive.
- La tabla webhook_delivery no es evidencia de que se hayan enviado webhooks.
- No hay contrato de API del proveedor fiscal SRI aprobado (endpoint, credenciales, payload, callbacks/polling, idempotencia, códigos de error, sandbox).
- No se implementan XML, firma digital, secuenciales, RIDE, autorización, nota de crédito o anulación electrónica.
- La tasa calculada no equivale a factura autorizada ni a un snapshot tributario histórico inmutable.
- Kafka no está configurado para producción con TLS/ACL/replicación/DLQ/lag y runbook.

## Dependencias y criterios de salida

1. Aprobar proveedor fiscal y ownership de firma/certificados/secuenciales.
2. Aprobar catálogo tributario y reglas por contribuyente/establecimiento con responsable contable.
3. Definir contratos versionados de operation request/response, idempotencia, operationId, polling y callbacks.
4. Implementar worker y dispatcher seguros con destinos permitidos, TLS, secretos referenciados, firma, timeout, retry y conciliación.
5. Persistir estados de operación y resultados sin afirmar éxito ante timeout ambiguo.
6. Pruebas contra sandbox real: éxito, rechazo, duplicado, timeout, callback falso/duplicado, reintento y recuperación.
7. Operar métricas de backlog/antigüedad/fallos, redrive auditado y runbook.

**Conclusión:** Outbox/Inbox y el modelo inicial de integración V28 son base durable, no una integración externa terminada ni una facturación SRI operativa.