# Roadmap integral — Cierre arquitectónico y operativo de Inventary antes de iniciar la versión 2

**Propósito:** consolidar brechas, dependencias y condiciones de aceptación para que Inventary V2 comience sobre una base transaccional, distribuida, segura y auditable.
**Fecha de corte:** 29 de octubre de 2026.
**Estado:** backlog previo a V2; no implica que las tareas estén implementadas.

## 1. Documentos especializados

- [Clientes HTTP resilientes](PROPUESTA_ARQUITECTURA_DISTRIBUIDA_CLIENTES_HTTP_RESILIENCIA_V1.md)
- [Kafka y caché Caffeine](PROPUESTA_KAFKA_CACHE_CAFFEINE_V1.md)
- [Roles, permisos y ámbitos](PROPUESTA_RBAC_ROLES_PERMISOS_AMBITOS_V1.md)
- [Plataforma asíncrona y webhooks](PROPUESTA_PLATAFORMA_INTEGRACIONES_ASINCRONAS_WEBHOOKS_V1.md)
- [Centro de reportes V2](PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V2.md)
- [Integración fiscal electrónica](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md)
- [Inventario como centro de control](PROPUESTA_INVENTARIO_CENTRO_CONTROL_Y_AUDITORIA_V2.md)
- [Almacenes, contactos y SRI](PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md)
- [Compras V2](PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md)
- [Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Suscripciones](PROPUESTA_UX_SUSCRIPCIONES.md)
- [Pedidos WhatsApp y n8n](PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md)
- [Productos V2](PROPUESTA_UX_PRODUCTOS_V2.md)
- [Motor comercial inteligente](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)
- [Logística de retiro y envíos](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)

## 2. Propósito y criterio de salida

La versión 2 debe comenzar cuando el negocio pueda confiar en que:
- cada operación de stock se contabiliza de forma consistente dentro de Inventary;
- las acciones externas quedan durables, son idempotentes y se pueden reconciliar;
- los usuarios ven solo recursos dentro de sus ámbitos;
- eventos, webhooks, jobs y errores externos son observables y recuperables;
- los reportes distinguen datos operativos, fiscales y contables;
- las decisiones y límites pendientes están documentados y aceptados explícitamente.

El objetivo no es cerrar cualquier característica imaginable antes de avanzar. Es eliminar riesgos fundacionales y declarar con precisión lo que seguirá fuera de alcance.

### Definition of Ready para V2

Una nueva capacidad crítica no debe empezar sin:
- definición del agregado y fuente de verdad;
- estados y transiciones de negocio;
- transacción local e idempotencia;
- estrategia de integración/reintento/resultado asíncrono;
- permisos por acción y ámbito;
- logging, métricas, alertas y runbook;
- pruebas de éxito, fallo, concurrencia, duplicado, timeout y recuperación;
- contrato API/evento versionado;
- auditoría suficiente;
- plan de migración compatible con la base actual.

## 3. Línea base verificada

El backend es un monolito modular Spring Boot 3.5.6/Java 21/PostgreSQL aunque el nombre histórico de carpeta conserva “microservices”. Kafka/Outbox están parcialmente implementados; Kafka está deshabilitado por defecto y el consumidor actual invalida una caché local del catálogo público. Outbox cubre varios flujos comerciales, recepciones, devoluciones y traslados, pero necesita auditoría de cobertura de ajustes manuales, conteos y todo futuro cambio de dominio.

Los servicios centrales de ventas, recepciones, devoluciones y traslados usan transacciones Spring. Eso protege la base de Inventary; no crea atomicidad con otros backends. Cada efecto externo requiere operación durable, idempotencia y reconciliación.

RBAC existe, pero AppUser no tiene asignaciones a locales/bodegas. Algunas rutas validan permiso a nivel de módulo sin scope territorial. Caffeine es local por instancia y la invalidación Kafka actual no garantiza que todas las réplicas limpien su caché. Hay tareas programadas para Outbox, expiración de WhatsApp y billing interno de suscripciones; hace falta estandarizar jobs multiinstancia e integraciones externas.

Fuentes internas:
- microservices-inventary-ecuapp/docs/24-estado-actual-repositorio.md
- microservices-inventary-ecuapp/docs/26-arquitectura-y-patrones-actuales.md
- microservices-inventary-ecuapp/docs/23-runbook-operacion.md
- microservices-inventary-ecuapp/docs/25-referencia-apis.md
- microservices-inventary-ecuapp/src/main/resources/db/migration/V10__security_rbac_and_token_revocation.sql
- microservices-inventary-ecuapp/src/main/resources/db/migration/V14__costing_roles_and_operational_completion.sql
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxPublisher.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxRelay.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/KafkaDomainEventConsumer.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/auth/AppUser.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/config/SecurityConfig.java

## 4. Backlog consolidado por prioridad

### P0 — Integridad transaccional, eventos y seguridad

#### P0.1 — Inventario como fuente única de saldo
- Todo flujo de venta, compra/recepción, devolución, traslado, conteo y ajuste utiliza servicios de dominio y ledger de stock compartidos.
- Cada comando registra movimiento, saldo, valoración, auditoría, idempotencia y Outbox dentro de la misma transacción local cuando corresponda.
- Completar Outbox para ajuste manual/conteo físico o documentar formalmente por qué un hecho no requiere evento.
- Probar concurrencia, disponibilidad, reserva y no negatividad.
- Nunca modificar saldos directamente mediante CRUD de producto.

#### P0.2 — Usuarios, roles y ámbitos
- Crear matriz por pantalla/botón.
- Modelar asignaciones de usuario a bodega, local, establecimiento y entidad según alcance.
- Aplicar scope en backend antes de devolver detalle, paginar, agregar o exportar.
- Separar lectura operativa de coste, margen, finanzas y fiscalidad.
- Mantener separación de funciones en compras, cancelaciones, conteos, ajustes y cierres.

#### P0.3 — Idempotencia consistente
- Una key por operación lógica, con hash canónico.
- Repetir mismo contenido/key devuelve la misma operación/recurso.
- Misma key con contenido distinto produce conflicto.
- Tras timeout ambiguo se consulta la operación del proveedor antes de repetir efecto.
- Probar doble clic, timeout después del commit, webhook retransmitido y retry tras reinicio.

#### P0.4 — Transacciones locales
- Mantener reglas comerciales y cambios de stock en Spring/PostgreSQL.
- No hacer llamadas de red externas dentro de transacciones largas.
- Revisar usos de AOP/transacciones y self-invocation cuando aplique.
- Probar rollback de stock, valoración, idempotencia, auditoría y Outbox.
- Usar bloqueos/constraints que protejan concurrencia real; no confiar en el estado del frontend.

#### P0.5 — Moneda, unidad y estado financiero
- No sumar monedas mixtas sin conversión versionada.
- No sumar unidades con dimensiones incompatibles.
- Separar venta bruta, venta neta, cobro, factura autorizada, devolución, nota de crédito y reembolso.
- Marcar calidad del coste histórico donde no es exacto.
- Prohibir que un subtotal comercial sea presentado como impuesto definitivo sin datos fiscales.

### P1 — Integración distribuida durable

#### P1.1 — Eventos estandarizados
- Definir envelope común: eventId, eventType, schemaVersion, occurredAt, producer, aggregate type/ID/version, correlationId, causationId y payload mínimo.
- Elegir clave Kafka por agregado para ordenar eventos relacionados.
- Establecer compatibilidad, retención, owner, clasificación de datos y seguridad por topic.
- Todos los consumers usan Inbox/idempotencia.
- Documentar entrega al menos una vez; no prometer exactly-once extremo a extremo.

#### P1.2 — Kafka real y operable
- Distinguir Compose raíz (sin broker propio) y Compose backend con broker bajo perfil.
- Crear entornos dev/staging/prod, configuración y prueba end-to-end.
- En producción: broker gestionado o cluster adecuado, TLS/auth, ACL, replicación, lag, DLQ y runbook.
- No declarar Kafka activado solo por definir una variable; comprobar publicación, consumo y recuperación.

#### P1.3 — Clientes HTTP
- Clientes tipados por backend.
- Timeouts, clasificación HTTP, circuito, bulkhead, rate limit y retry.
- 202 significa aceptado/pendiente, no terminado.
- No reintentar POST no idempotente después de resultado ambiguo.
- Mantener operación remota pendiente con ID/estado.

#### P1.4 — Webhooks entrantes y salientes
- Firma, timestamp, replay protection, delivery ID, endpoint allowlist, tamaño y timeout.
- Outbox/Inbox durable.
- Historial de intentos, Retry-After, jitter, DLT/redrive y métricas.
- Bloqueo de SSRF y destinos no aprobados.
- Evaluar Integration Hub reusable para todos los backends.

#### P1.5 — Sagas y consistencia eventual
Definir flujos de varias fases con efectos no reversibles:
- venta y emisión de factura;
- devolución de producto y nota de crédito;
- traslado de inventario y asignación de flota;
- suscripción/cuota y documento fiscal;
- eventos comerciales y notificación;
- preparación y cierre de periodo tributario.

Cada saga tiene estados, timeouts, compensaciones permitidas, revisión manual y reconciliación. No asumir transacción global XA. Una factura autorizada no se deshace por rollback local; necesita el proceso fiscal correspondiente.

### P1 — Operación y recuperación

#### P1.6 — Scheduler multiinstancia
- Jobs @Scheduled idempotentes.
- Definir si se permite ejecución en varias réplicas o si se requiere lock/lease distribuido.
- Registrar jobId, inicio/fin, duración, cantidad procesada, errores y próxima ejecución.
- Un error no puede desaparecer porque el job terminó; mantener estado y reintento.
- Probar DST/zona horaria/feriados cuando aplique a obligaciones y fechas.

#### P1.7 — Retry y DLT
- Clasificar fallos transitorios, permanentes, de seguridad, datos inválidos y resultado desconocido.
- Retry con jitter, límite y presupuesto coordinado.
- DLT tiene owner, alertas y runbook.
- Redrive requiere motivo y auditoría; el payload original no se edita.
- Evitar que retry de HTTP × retry de Kafka × retry del Hub multiplique llamadas.

#### P1.8 — Observabilidad
- Propagar requestId/correlationId a través de API → Outbox → Kafka → HTTP/webhook → destino → callback.
- Métricas de Outbox age/failure, Kafka lag, retry, DLT, integración por estado, cache hit ratio y jobs.
- Alertas por backlog, destino inaccesible, callback ausente, fallo de auth y envejecimiento de facturas.
- Health/liveness/readiness por dependencia y criticidad.
- Calcular conexiones PostgreSQL y pools por número de réplicas.

#### P1.9 — Caché
- Máximo de 60 min tanto desde acceso como desde escritura.
- TTL más corto para catálogo público.
- Invalidación selectiva por evento.
- Caffeine es local; usar cache compartida o broadcast explícito cuando la consistencia entre réplicas lo requiera.
- No cachear saldo autoritativo, reservas, precios transaccionales ni estado fiscal como fuente de verdad.

### P2 — Dominios operativos y datos maestros

- Asociación N:M establecimiento SRI ↔ bodega, con tipo, vigencia y auditoría.
- Separar propietario de stock, entidad legal, establecimiento, bodega y punto de retiro.
- Si una bodega sirve varios RUC, exigir owner/legalEntity; si todavía no existe aislamiento, bloquear el escenario.
- Completar recepción parcial, devolución, traslado, conteos, ajustes, ubicación y costeo con pruebas.
- CRUD de proveedores/clientes y protección de snapshots en documentos históricos.
- Unidades de medida y conversiones dimensionalmente válidas.
- Almacenes muestra estado SRI recibido, fecha de consulta, vigencia y fuente; no permite editar manualmente el registro oficial del SRI.
- Documentos fiscales de proveedor y factura emitida son independientes de la logística física.
- Cambios de asignación no reescriben documentos históricos.
- Multiempresa/multi-tenant no puede anunciarse sin scope y prueba completos.

### P2 — Fiscalidad y contabilidad

- Persistir vínculo venta ↔ solicitud de emisión ↔ documento fiscal externo.
- Registrar estado real, RUC/establecimiento/punto usados, número/clave/ID, fechas y error.
- Separar anulación fiscal, nota de crédito, devolución de mercadería y reembolso.
- Añadir documentos fiscales de proveedor/retenciones antes de afirmar que ATS/IVA está preparado.
- Versionar formularios, esquemas, catálogos ATS y reglas por periodo; preservar hash y referencia.
- Obligaciones de contribuyente y calendario según perfil verificado; no todas aplican a cada RUC.
- Plan de cuentas/asientos balanceados si se esperan estados financieros completos.
- Integrar pagos, bancos, cuentas por cobrar/pagar, gastos y nómina cuando el alcance fiscal lo exige.
- Preparación de declaración no equivale a presentación; almacenar prueba de presentación/pago real.
- Cambios normativos SRI requieren revisión profesional, vigencia y test de regresión.

### P2 — UX y mantenimiento

- Pantallas con loading/skeleton, empty/error/permission states, paginación, filtros persistentes y exportación server-side.
- Inventario como centro operativo, con vistas especializadas sin duplicar fuentes de stock.
- No usar modales para procesos extensos; página o stepper según número de secciones.
- Estados externos visibles: pending, processing, confirmed, failed, action needed.
- Botones siguen permisos, pero la API valida siempre.
- Tests de flujos críticos además del build.
- Accesibilidad, feedback para timeout/reintento y estado de operación durable.
- No presentar datos parciales como completos cuando un informe ha sido truncado.

### P3 — Escala y arquitectura futura

- Separar módulos en servicios independientes cuando haya necesidad comprobada de escalado, ownership o aislamiento.
- Proyecciones analíticas/read models si los reportes y el volumen lo requieren.
- Schema registry/contract tests para Kafka/HTTP entre repositorios.
- SLO por servicio, latencia, escalado y capacidad.
- Evitar event sourcing completo solo por usar Kafka; Outbox + ledger + proyecciones pueden ser suficientes.
- Gestor de secretos, rotación y permisos de servicio.
- OpenTelemetry, logs estructurados redactados, tracing y alertas.
- Backups y restauración probados; recuperar también la capacidad de publicar los Outbox pendientes.
- Retención y exportación de auditoría conforme a política legal/operativa.

## 4. Fuente de verdad por dominio

| Dato/hecho | Fuente de verdad | Integración |
|---|---|---|
| Venta, líneas y cliente comercial | Inventary | SaleCompleted, SaleCancelled, SalesReturnPosted |
| Saldo/movimientos/valoración | Inventary | ReceiptPosted, StockAdjustmentPosted, StockTransferDispatched/Received, SaleCompleted |
| Orden de compra y recepción física | Inventary | PurchaseOrderApproved, GoodsReceiptPosted, PurchaseReturnPosted |
| Identidad fiscal del comprobante emitido | Backend fiscal; Inventary guarda vínculo y snapshot devuelto | FiscalDocumentAuthorized/Rejected/Cancelled |
| XML firmado/clave/número autorizado | Backend fiscal | FiscalDocumentResult con ID real/estado |
| Estado de correo | Proveedor de mailing | NotificationAccepted/Delivered/Failed |
| Vehículo y transporte | Backend de flota | TransportRequested/Assigned/Delivered/Failed |
| KPI analítico | Proyección de analytics; fuente original conserva autoridad | Consumidores idempotentes |
| Identidad/permiso | RBAC/IdP definido | Cambios con revocación |
| Declaración presentada/pago | Evidencia del canal oficial o integración autorizada | Filed/PaymentConfirmed solo con evidencia |

No inferir que un evento y una proyección son dos hechos económicos. La venta vinculada a su factura no debe duplicar ingresos; un movimiento de stock no es una venta adicional.

## 5. Criterios de aceptación globales previos a V2

- Todas las operaciones que cambian stock tienen pruebas de concurrencia e idempotencia.
- No hay rutas que permitan saltar el scope de bodega/local.
- Comandos sensibles tienen permisos atomizados, estado válido y auditoría.
- El cambio comercial y el Outbox se guardan en la misma transacción local cuando procede.
- Kafka se prueba end-to-end en el ambiente objetivo o se declara explícitamente no activo.
- Cada integración tiene owner, contrato, timeout, status/retry, autenticación y reconciliación.
- HTTP 202 no se interpreta como resultado final.
- Outbox, DLT, webhooks y UNKNOWN_OUTCOME tienen recuperación documentada.
- Jobs no ejecutan efectos duplicados en múltiples instancias.
- Caché respeta TTL y no es fuente autoritativa.
- Monedas, unidades, impuestos y totales no se mezclan.
- Facturación real está separada del registro comercial.
- Los informes incluyen periodo, fuente, moneda, freshness y conciliación.
- Migraciones nuevas se agregan sin editar las ya aplicadas.
- CI valida frontend/backend, PostgreSQL de versión objetivo e integraciones relevantes.
- La documentación refleja código y SHA verificado.
- Riesgos fuera de alcance están aceptados por el dueño del producto.

## 6. Riesgos que no se deben ocultar

1. Kafka no convierte llamada remota en transacción global.
2. La entrega puede repetirse; consumidores idempotentes son obligatorios.
3. Outbox solo funciona si cada cambio relevante emite el evento correcto.
4. Webhooks pueden llegar duplicados, tarde o fuera de orden.
5. La respuesta HTTP puede perderse después de que el proveedor ejecutó el comando.
6. El usuario puede manipular IDs del frontend; el backend verifica scope.
7. Mapeo bodega-establecimiento no determina propiedad del stock ni autoriza facturación.
8. Una factura autorizada necesita flujo fiscal para anularse; rollback local no basta.
9. Reportes operativos no equivalen al libro contable ni a una declaración presentada.
10. Multiempresa necesita aislamiento completo, no solo una columna tenantId.
11. Una caché local no se invalida automáticamente en todas las réplicas.
12. Las reintentos sin presupuesto coordinado pueden inundar servicios externos.
13. Un DLT sin owner/alerta convierte fallos recuperables en pérdida operativa silenciosa.
14. Los eventos no deben contener secretos ni enviar documentos fiscales completos a consumidores no autorizados.

## 7. Plan sugerido de ejecución

**Fase 0 — Baseline y decisiones:** inventario de rutas, permisos, eventos, jobs y destinos; decisión de scopes y catálogo de eventos.

**Fase 1 — Seguridad/transacciones:** asignaciones de ámbito, pruebas de rollback/concurrencia/idempotencia, completar Outbox de inventario.

**Fase 2 — Kafka/observabilidad:** ambientes, topics/envelopes, DLQ, dashboards, runbook y pruebas de interrupción.

**Fase 3 — Clientes/Integration Hub:** resiliencia, estado durable, firmas de webhook, primer adaptador fiscal.

**Fase 4 — Integraciones de negocio:** mailing, flota, analytics y consumers independientes.

**Fase 5 — Operación y datos:** TTL, scheduler multiinstancia, reportes y pantalla de incidencias.

**Fase 6 — Fiscal/contable:** estados de documento, conciliación, perfil fiscal y workpapers.

**Fase 7 — Criterios de V2:** ejecutar matriz de aceptación, medir latencias, recuperar fallos, revisar seguridad y asegurar un despliegue reproducible.

Las fases pueden solaparse, pero las dependencias deben respetarse. No declarar “listo para V2” por completar la UI o conseguir que compile.

## 8. Referencias técnicas

- Spring REST Clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Kafka retry/DLT: https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Transactional Outbox: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- Caffeine/Spring Cache: https://docs.spring.io/spring-boot/reference/io/caching.html
- Spring Security method authorization: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html
- OWASP Authorization: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- NIST RBAC: https://csrc.nist.gov/projects/role-based-access-control/faqs
- Webhook signature validation: https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries

## 9. Límite de entrega

Este documento consolida prioridades y Definition of Done. No realiza cambios de código fuente, configuración Kafka, caché, migraciones, permisos ni workflows. Cada propuesta debe materializarse en código y pruebas, con evidencia ligada al SHA que vaya a desplegarse.
