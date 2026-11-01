# Fase 7 — Integraciones durables y contrato fiscal

**Rama:** `implementacion_propuestas`  
**Estado:** EN PROGRESO — infraestructura de eventos versionados implementada; adaptador fiscal SRI bloqueado por ausencia del contrato del proveedor y aprobación de reglas tributarias.

## Implementación de este corte

### Contrato de eventos durable

- Migración `V24__version_outbox_event_envelope.sql` añade `schema_version`, `correlation_id`, `causation_id` y `aggregate_version` a `outbox_event`.
- Los registros existentes se migran con schema version 1; la migración no elimina ni reescribe payloads ni estados pendientes.
- `OutboxEvent` crea nuevos eventos con schema version 1, captura el `X-Request-ID` propagado en MDC y admite causation/aggregate version explícitos. No inventa aggregate versions cuando el dominio no las ofrece.
- `OutboxRelay` publica un envelope versionado con eventId, aggregate identity, eventType, occurredAt, payload y los metadatos de correlación/causalidad disponibles.
- `KafkaInboxEventService` valida la versión antes de insertar el Inbox; acepta envelopes sin versión como legacy v1 durante la migración, rechaza versiones futuras/desconocidas y valida tipos/tamaños de los metadatos opcionales.
- La deduplicación existente sigue canonicalizando JSON antes del hash, por lo que reordenar propiedades no duplica el evento; mismo eventId con contenido distinto sigue siendo conflicto.
- La API de operaciones Outbox expone versión y metadatos de correlación/causalidad, sin exponer payloads que podrían contener datos comerciales.
- Kafka continúa opt-in. La infraestructura no declara Kafka productivo ni implica que un evento se haya consumido por un sistema externo.

### Pruebas añadidas

- Prueba de contexto de persistencia valida que Flyway aplica V24 y Hibernate valida el mapping.
- Prueba de Outbox comprueba schemaVersion, identidad de agregado, timestamp y payload del envelope.
- Prueba de contexto comprueba captura de request correlation, causation y aggregateVersion explícitos.
- Prueba Inbox comprueba versión soportada, rechazo de versión desconocida antes de persistir y deduplicación con propiedades reordenadas.

## Lo que no se ha simulado

No se creó un cliente HTTP de SRI ni se generaron XML, firmas, secuenciales, RIDE, códigos de autorización o tasas. El repositorio no contiene un contrato de API de un proveedor fiscal externo aprobado ni su ambiente, autenticación, esquema de solicitud/respuesta, idempotencia, callback/polling o reglas de conciliación. Inventar cualquiera de esos datos podría emitir documentos tributarios inválidos o duplicados.

Tampoco se declara autorizada una venta por el mero hecho de crearla o publicar un evento Outbox. El modelo actual de venta conserva `tax=0`; eso no es una base suficiente para calcular una factura SRI ni para elegir tarifa/regla tributaria.

## Bloqueos para completar la fase

1. **Contrato del backend fiscal:** proveedor, URL/ambientes oficiales, autenticación y referencia a secretos, límites/timeouts, endpoint de emisión/consulta/anulación, esquema versionado, respuesta, operationId, política de idempotencia y códigos de error.
2. **Ownership fiscal:** confirmar si el backend externo es dueño de certificados, firma, secuenciales, XML, transmisión, RIDE y autorización; definir qué referencias y snapshots retorna a Inventary.
3. **Reglas tributarias:** catálogo de impuestos/tarifas/códigos, vigencias por fecha, régimen del emisor/comprador, consumidor final, retenciones y notas de crédito; aprobación de responsable contable/fiscal para Ecuador y aplicabilidad de resoluciones SRI citadas en el roadmap.
4. **Identidad del emisor:** contribuyente legal y establecimiento fiscal efectivo de cada venta, con política para almacenes asociados a establecimientos. No basta con el registro manual marcado `MANUAL_REVIEW`.
5. **Callbacks/polling:** firma y rotación de claves, timestamp, replay window, deduplicación, conciliación y tratamiento de outcomes desconocidos/rechazados.
6. **Operación:** secretos externos por referencia segura, TLS, allowlist de destinos, retry/backoff, circuit breaker, bulkhead, métricas, redrive auditado, Kafka ACL/TLS/DLT si se habilita y runbook de recuperación.
7. **Pruebas E2E:** sandbox real del proveedor, emisión, consulta, rechazo, timeout ambiguo, callback duplicado/falso, anulación, nota de crédito y conciliación.

## Criterios de salida pendientes

- Contrato fiscal aprobado y cliente tipado probado contra sandbox real.
- Snapshot fiscal inmutable ligado a venta y líneas, con cálculo tributario validado por reglas vigentes.
- Estado fiscal separado de estado comercial/pago: PENDING/ACCEPTED nunca equivale a AUTHORIZED.
- Callback firmado o polling por operationId con anti-replay e Inbox persistente.
- Autorización/rechazo/anulación/notas de crédito conciliados sin duplicar documentos.
- Política de retry que no repite POST de outcome ambiguo sin idempotencia confirmada por el proveedor.
- CI, pruebas de seguridad/concurrencia y evidencia de sandbox enlazadas al SHA final.

**Conclusión:** la base Outbox/Inbox ya transmite eventos con contrato versionado y trazabilidad mejorada. La Fase 7 permanece abierta hasta integrar y validar el proveedor fiscal real; no es técnicamente correcto cerrarla sin esos datos y aprobaciones.
