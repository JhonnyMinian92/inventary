# Propuesta V1 — Activación de Kafka, contratos de eventos y mejora de caché Caffeine

**Fecha de revisión:** 29 de octubre de 2026  
**Estado:** guía de implementación; no se cambia la configuración ni se afirma que Kafka esté activo en producción.

Documentos relacionados:
- [Clientes HTTP resilientes](PROPUESTA_ARQUITECTURA_DISTRIBUIDA_CLIENTES_HTTP_RESILIENCIA_V1.md)
- [Plataforma de integración asíncrona](PROPUESTA_PLATAFORMA_INTEGRACIONES_ASINCRONAS_WEBHOOKS_V1.md)
- [Roadmap previo a Inventary V2](ROADMAP_CIERRE_ARQUITECTURA_INVENTARY_PRE_V2.md)
- [Arquitectura y patrones actuales](microservices-inventary-ecuapp/docs/26-arquitectura-y-patrones-actuales.md)
- [Runbook de operación](microservices-inventary-ecuapp/docs/23-runbook-operacion.md)

## 1. Diagnóstico del estado actual

Inventary ya incorpora:
- dependencia Spring Kafka en Maven;
- configuración de productor y consumidor en application.yaml;
- Outbox transaccional en PostgreSQL;
- OutboxPublisher, activable mediante KAFKA_ENABLED;
- consumidor con @KafkaListener;
- tabla de Inbox para deduplicar por eventId y hash del envelope;
- error handler con reintentos acotados y dead-letter topic;
- endpoint operacional para consultar y volver a encolar eventos Outbox;
- Caffeine como caché local del proceso.

Pero Kafka está **deshabilitado por defecto** mediante KAFKA_ENABLED=false. El Compose específico del backend incluye un broker Apache Kafka 3.9.1 bajo perfil kafka. El Compose de raíz no declara un servicio Kafka. Activar la variable en un entorno sin broker accesible no habilita mensajería; la API intentará conectar a una dirección que debe existir y estar configurada.

El consumidor implementado tiene un efecto limitado: invalida la caché local de productos públicos. No existe una constelación completa de consumidores para facturación, mailing, flota o analítica. No describir el estado actual como una plataforma Kafka completa.

Archivos de referencia:
- microservices-inventary-ecuapp/pom.xml
- microservices-inventary-ecuapp/docker-compose.yml
- docker-compose.yml
- microservices-inventary-ecuapp/src/main/resources/application.yaml
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxPublisher.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxRelay.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/KafkaDomainEventConsumer.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/KafkaInboxEventService.java

## 2. Habilitación correcta en desarrollo

### 2.1 Compose del backend

El Compose situado en microservices-inventary-ecuapp/docker-compose.yml incluye un broker bajo el perfil kafka. En un entorno de desarrollo aislado de ese Compose, ejecutar desde la carpeta microservices-inventary-ecuapp estos pasos:

1. Crear/configurar el archivo local .env con todas las variables requeridas por la API, sin subirlo a Git.
2. En .env, fijar KAFKA_ENABLED=true y KAFKA_BOOTSTRAP_SERVERS=kafka:9092 para que la API dentro de Compose use el hostname del broker de la misma red.
3. Ejecutar **docker compose --profile kafka up -d --build**.
4. Revisar **docker compose --profile kafka ps** y **docker compose logs -f kafka api**; confirmar readiness de la API y que el broker haya iniciado.
5. Para apagar el entorno, ejecutar **docker compose --profile kafka down**. Esto detiene los contenedores; no ejecutar down -v salvo que se pretenda eliminar los volúmenes persistentes de desarrollo.

Si KAFKA_ENABLED se deja en false, el broker puede estar encendido pero OutboxPublisher y el consumidor condicional no se activarán. Si se coloca true y el broker no existe/no es accesible, se acumularán eventos pendientes y debe generarse una señal operativa.
4. Iniciar PostgreSQL, Kafka y la API; esperar a readiness y revisar logs.
5. Publicar un evento de prueba y comprobar Outbox PENDING → PROCESSING → PUBLISHED, entrega al consumidor y registro en Inbox.
6. Detener Kafka, ejecutar una prueba controlada que genere Outbox, observar el pendiente y restaurar Kafka; verificar que el evento se publique sin perder la operación.
7. Repetir con evento duplicado, mismo eventId con contenido distinto y consumidor detenido.

No usar kafka:9092 desde una aplicación ejecutada fuera de la red Docker. Un cliente externo requiere una dirección anunciada y accesible desde ese contexto.

### 2.2 Compose de raíz

El Compose de raíz no declara broker propio. Si se configura KAFKA_ENABLED=true allí, debe apuntarse a un broker externo/gestionado accesible mediante KAFKA_BOOTSTRAP_SERVERS. No asumir que el servicio Kafka aparecerá automáticamente.

La validación de configuración debe fallar rápido o impedir el modo de producción si Kafka se declara obligatorio pero no hay bootstrap válido, credenciales o conectividad. Para entornos de desarrollo Kafka puede ser opcional; en producción, las operaciones guardadas en Outbox necesitan métricas y alertas mientras el broker esté desconectado.

### 2.3 Antes de producción

El broker de desarrollo usa un único nodo KRaft, una partición por defecto y PLAINTEXT. Sirve para desarrollo aislado; no proporciona alta disponibilidad ni seguridad apropiada para producción.

Producción requiere:
- broker gestionado o cluster con política de disponibilidad/recuperación probada;
- TLS y autenticación;
- ACLs por productor y consumidor, con mínimos permisos por topic;
- replication factor y min.insync.replicas acordes a la disponibilidad objetivo;
- particiones según volumen, concurrencia y orden por agregado;
- retención y almacenamiento dimensionados;
- cuotas de productor/consumidor y límites de tamaño;
- monitorización de consumer lag, particiones offline, under-replicated partitions y errores;
- secretos externos;
- runbook de actualización, recuperación, incidentes, replay y conservación de datos.

No copiar la configuración de un broker de un nodo con PLAINTEXT a producción.

## 3. Papel del Outbox

Una operación que modifica PostgreSQL y necesita propagar un evento debe guardar el hecho de negocio y el registro Outbox dentro de la misma transacción local. Un relay reclama el registro, publica después del commit y registra el resultado en otra transacción corta.

Flujo:
1. Validar comando e idempotencia.
2. Confirmar venta/stock/documento de dominio.
3. Guardar evento Outbox en la misma transacción.
4. Hacer commit PostgreSQL.
5. El relay reclama un lote mediante lease.
6. Publica fuera de la transacción de claim.
7. Marca PUBLISHED o programa retry.
8. El consumidor valida el envelope y deduplica por eventId.

Outbox hace durables juntos el hecho local y la intención de publicar. No garantiza que el broker ni todos los backends externos ejecuten un efecto de negocio exactamente una vez. La entrega actual es al menos una vez, por lo que todos los consumidores deben ser idempotentes.

Referencia: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html

## 4. Contrato estándar de evento

Cada evento debe incluir:
- eventId único e inmutable;
- eventType estable;
- schemaVersion;
- occurredAt en UTC;
- producer;
- aggregateType, aggregateId y aggregateVersion/secuencia cuando aplique;
- correlationId y causationId;
- scope de entidad legal/tenant cuando el modelo lo soporte;
- payload mínimo necesario;
- metadata de trazabilidad sin secretos.

La clave Kafka debería ser estable por agregado, por ejemplo tipo + ID de agregado, para que los cambios de una misma venta o traslado normalmente caigan en la misma partición. La key de partición ayuda con orden; eventId identifica y deduplica. Son conceptos distintos.

El consumidor valida schema/version, tamaño, identidad, origen y contenido. Debe tolerar campos opcionales compatibles, rechazar versiones incompatibles de forma observable y no almacenar secretos ni PII innecesaria.

## 5. Diseño de topics

No crear un topic por cada evento pequeño ni compartir indiscriminadamente un único topic con payloads sensibles. Comenzar con una taxonomía acotada por dominio y requisitos:

- inventary.domain-events para hechos internos bien tipados si todos los consumidores autorizados pueden recibir el envelope;
- topics separados para comandos que solicitan una acción externa crítica;
- retry topic, DLT o parking-lot según política de errores/orden;
- topics fiscales/PII separados cuando los datos y permisos no sean compatibles con consumidores generales.

Un topic genérico es práctico al inicio, pero con varios servicios deben revisarse ACLs, contratos, esquemas y límites por consumidor.

Para cada topic documentar productor, owner, consumidores, key, schema, compatibilidad, retención, clasificación de datos, ACL, volumen/SLO y procedimiento de replay.

## 6. Eventos de negocio que requieren cobertura uniforme

Hay eventos existentes para ventas completadas/canceladas, recepciones, devoluciones y traslados, y estados de pedidos WhatsApp. Se necesita una revisión sistemática de todos los cambios de dominio. En la revisión, el ajuste manual de inventario y la contabilización de conteos no aparecían escribiendo Outbox aunque cambian existencias.

Catálogo inicial recomendado:
- ProductCreated, ProductUpdated, ProductDeactivated;
- SaleCompleted, SaleCancellationRequested, SaleCancelled;
- SalesReturnPosted;
- PurchaseOrderApproved, GoodsReceiptPosted, PurchaseReturnPosted;
- StockAdjustmentPosted, InventoryCountPosted;
- StockTransferCreated, StockTransferDispatched, StockTransferReceived, StockTransferCancelled, StockTransferReversed;
- SubscriptionCreated, InstallmentDue, BillingAttempted, BillingFailed, SubscriptionCancelled;
- WhatsAppOrderCreated, WhatsAppOrderExpired, WhatsAppOrderConfirmed;
- FiscalEmissionRequested como comando durable, y FiscalDocumentAuthorized/Rejected/Cancelled/CreditNoteResult como hechos devueltos y confirmados por el backend fiscal;
- NotificationRequested y NotificationDelivered/Failed cuando se use mailing.

Los nombres son propuesta. Cada evento necesita dueño, payload versionado y política de retención.

No publicar “completado” antes del commit. Un comando solicita una acción; un evento comunica que ocurrió un hecho confirmado.

## 7. Consumidores y grupos

Un consumer group reparte eventos entre sus miembros; no entrega una copia a cada miembro. Los backends que necesitan el mismo evento deben tener grupos de consumidor independientes.

Ejemplo:
- grupo fiscal: procesa el comando de solicitud de emisión;
- grupo mailing: transforma hechos confirmados en notificaciones;
- grupo analítica: proyecta ventas/devoluciones;
- grupo flota: recibe traslados despachados que requieren transporte;
- grupo cache: invalida su propia proyección o caché, no ejecuta operaciones de stock.

Cada consumidor debe tener Inbox idempotente local o mecanismo equivalente y autorización sobre los topics que lee.

## 8. Retry y Dead Letter Queue

Hay dos niveles en Inventary: Outbox tiene backoff y redrive operacional; Kafka consumer tiene reintentos acotados y DLT. Deben coordinarse para no multiplicar intentos por capas.

- Fallos de infraestructura transitorios pueden reintentarse con límite y backoff.
- Datos inválidos o fallos permanentes se aíslan en DLT y generan una incidencia.
- DLT necesita owner, alertas, dashboard, retención, análisis y procedimiento de replay.
- El replay conserva el eventId original para deduplicación, registra actor/motivo y marca el replay en auditoría.
- Si se consumió el evento y falló el efecto HTTP posterior, el estado debe quedar en la plataforma de integración; no reenviar indefinidamente el evento sin diagnóstico.
- Retry topics no bloqueantes pueden romper orden relativo; no usarlos si el agregado requiere orden estricto sin diseñar la consecuencia.

Documentación Spring Kafka:
- https://docs.spring.io/spring-kafka/reference/retrytopic.html
- https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html

## 9. Diseño de caché con máximos explícitos

Caffeine es local a cada instancia, no un almacén durable ni una caché distribuida. El requisito del negocio es que una entrada no utilizada expire como máximo tras una hora; se recomienda imponer también una edad máxima desde la escritura para que el uso constante no conserve indefinidamente información antigua.

Techo general propuesto:
- expireAfterAccess: 60 minutos como máximo;
- expireAfterWrite: 60 minutos como máximo;
- maximumSize o maximumWeight configurado;
- estadísticas habilitadas;
- TTL específico menor cuando los datos cambien con frecuencia o tengan impacto de seguridad/comercial.

Con ambas expiraciones, una entrada expira al pasar una hora desde el último acceso o desde la última escritura, lo que ocurra primero. Una hora es techo máximo, no recomendación universal de freshness.

### 9.1 TTL inicial por familia

| Datos | TTL inicial | Estrategia |
|---|---:|---|
| Lista pública de productos | 5–15 min | Invalidación selectiva + TTL |
| Detalle público de producto | 5–15 min | Clave por ID/versión; invalidar al editar |
| Categorías públicas | 15–30 min | Invalidación por cambios |
| Metadatos operativos de poco riesgo | 5–15 min | Solo si se mide beneficio |
| Catálogo de datasets/reportes | 15–30 min | Versionar e invalidar al actualizar |
| Permisos e identidad | No cachear por una hora como regla general | TTL corto/versionado e invalidación segura |
| Disponibilidad de stock, reservas, cotización y precio transaccional | No usar caché como fuente de verdad | Validar y bloquear al confirmar en PostgreSQL |
| Estado de factura, pago o devolución | No sustituir el estado real por TTL de una hora | Consultar contrato fiscal o mantener proyección con timestamp de frescura |
| Reporte pesado | Preferir snapshot con generatedAt | Cachear solo con filtros normalizados, ámbito y vigencia declarados |

### 9.2 Caché local con varias réplicas

Existe un problema relevante con la invalidación actual:

- El grupo consumidor es inventary-public-cache.
- Kafka entrega cada evento a un solo consumidor del mismo group, no a todas las réplicas.
- Si cada instancia mantiene Caffeine local, una instancia puede limpiar su caché y las demás conservar valores antiguos hasta que expiren.

Alternativas:
1. TTL corto para catálogo público si el riesgo de stale data es tolerable.
2. Caché compartida (por ejemplo Redis) con invalidación/versión consistente.
3. Señal de invalidación broadcast explícita a cada instancia, con diseño operativo propio.
4. Clave versionada si el dominio ofrece versión fiable.

Para múltiples réplicas, una caché compartida o invalidación explícita suele ser más clara. El saldo de stock transaccional nunca debe depender de la invalidación de Caffeine.

### 9.3 Invalidación selectiva

El consumidor actual invalida publicProductList ante cualquier evento válido. Esto es amplio e incurre en cache misses innecesarios. Definir matriz evento → cache:
- ProductCreated/Updated/Deactivated: listas y detalle público.
- CategoryUpdated: categoría y listas que dependen de ella.
- StockAvailabilityChanged: solo proyección pública de disponibilidad; nunca fuente autoritativa de reservas/ventas.
- SaleCompleted: no invalidar catálogo salvo que cambie un dato público.
- Evento fiscal: no invalidar catálogo.

La señal debe ser mínima, versionada y sin PII.

### 9.4 Reglas de implementación
- Spring cache debe habilitarse explícitamente; las anotaciones por sí solas no activan la caché.
- La autoinvocación dentro del mismo bean puede saltarse el proxy y no aplicar la anotación en modo proxy.
- La clave debe incluir argumentos relevantes, idioma, tenant/scope/permisos si alteran el resultado.
- No cachear coste/margen bajo una clave compartida que mezcle permisos.
- No cachear error como éxito.
- Medir hit/miss/evictions, size, load latency y datos antiguos.
- Probar expiración de acceso/escritura, size limit, cambios de producto, revocación de permisos y varias réplicas.

Documentación oficial:
- https://docs.spring.io/spring-boot/reference/io/caching.html
- https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html

## 10. Observabilidad y operación

Métricas:
- Outbox por estado/edad;
- latencia de publicación;
- Kafka consumer lag;
- Inbox duplicate/conflict;
- DLT count/age;
- retries/replays por destino;
- cache hit/miss, eviction y tamaño;
- eventos por tipo;
- incompatibilidades de schema.

Alertas a calibrar:
- evento PENDING más antiguo que el objetivo;
- cualquier FAILED o DLT sin owner;
- readiness degradado cuando Kafka sea dependencia obligatoria;
- consumer lag creciente;
- fallo de autenticación/TLS;
- tasa excesiva de misses tras invalidación completa.
En producción, una cola Outbox creciente debe ser visible aunque PostgreSQL todavía acepte transacciones.

## 11. Migración segura

1. Ejecutar perfil Kafka en CI.
2. Completar cobertura de Outbox para ajustes, conteos y otros cambios relevantes.
3. Definir envelope, clave, idempotencia, consumidores y retención.
4. Activar primero un consumidor de prueba no destructivo.
5. Activar publicación en staging con broker/credenciales correctos.
6. Probar desconexión, reinicio, duplicados, orden, DLT y redrive.
7. Verificar que un evento no duplica operaciones de negocio.
8. Añadir métricas/alertas/runbook.
9. Activar consumidores productivos uno por uno.
10. Validar broker y caché en la topología real de despliegue.

## 12. Criterios de aceptación

- Configuración Kafka incoherente detectada al arrancar.
- Negocio no se pierde si Kafka se detiene; queda durable en Outbox.
- Cada consumidor deduplica, valida esquema y registra errores.
- Las claves de partición preservan el orden requerido por agregado.
- Retry no duplica factura, correo, vehículo ni movimiento.
- DLT/FAILED visibles y recuperables.
- Cachés con maximumSize y TTL explícito no mayor a 60 minutos.
- Stock transaccional y estado fiscal no dependen de Caffeine.
- Invalidación verificada en varias instancias.
- Cada caché tiene dueño de fuente y retraso admisible declarado.

## 13. Fuentes técnicas
- Spring Kafka retry/DLT: https://docs.spring.io/spring-kafka/reference/retrytopic.html
- Transactional Outbox: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- Spring Boot Caffeine: https://docs.spring.io/spring-boot/reference/io/caching.html
- Spring cache annotations: https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html

## 14. Límite

Este documento no habilita Kafka ni cambia la caché. Describe la configuración y pruebas para desarrollo, staging y producción, con cuidado de no llamar distribuida a una caché local ni activa a una mensajería que está deshabilitada.
