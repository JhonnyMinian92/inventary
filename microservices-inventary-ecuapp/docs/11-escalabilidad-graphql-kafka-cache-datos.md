# 11 — Escalabilidad, GraphQL, Kafka, caché y estrategia de datos

## 1. Decisiones arquitectónicas recomendadas

Esta especificación fija decisiones técnicas para el backend transaccional y la evolución hacia gran volumen. “Millones de usuarios” no es por sí mismo una métrica de capacidad suficiente: es necesario definir usuarios activos concurrentes, lecturas/escrituras por segundo, picos, tamaño del catálogo, tasa de movimientos y objetivos de latencia. La plataforma se construye desde el inicio con límites, métricas y opciones de escala, pero no se agrega complejidad distribuida sin medir la necesidad.

| Área | Decisión para Inventary | Justificación |
|---|---|---|
| Base de verdad | PostgreSQL relacional | ACID, constraints, transacciones, joins y recuperación; adecuado para pedidos, stock y ledger. |
| API pública/operativa | REST como contrato estable para integraciones y comandos de negocio | Sencillo de versionar, cachear e integrar desde n8n; errores HTTP e idempotencia explícitos. |
| Consultas flexibles | GraphQL adicional, enfocado en vistas compuestas/catálogo del frontend | El consumidor selecciona campos y reduce múltiples llamadas. No sustituye la lógica de dominio ni obliga a migrar todas las rutas REST. |
| Escrituras comerciales | Casos de uso de aplicación únicos, ejecutados en una transacción PostgreSQL | Las ventas, reservas, stock, pedido y auditoría local se confirman o revierten conjuntamente. |
| Integración por eventos | Kafka + Transactional Outbox cuando se habilite la escala/eventos de integración | Desacopla notificaciones, analítica, buscadores y proyecciones; Kafka no reemplaza al registro transaccional. |
| Caché de objetos estables | Caffeine local inicialmente; Redis compartido cuando se ejecuten varias réplicas o se requiera invalidación común | Producto/imagen/descripción cambian menos que el stock. Redis permite coherencia distribuida aproximada, no convierte los datos cacheados en fuente de verdad. |
| Pool de conexiones | HikariCP en la aplicación; evaluar PgBouncer cuando la cantidad total de conexiones de réplicas/clientes lo requiera | Poolar conexiones mejora uso de conexiones, pero no aumenta mágicamente el rendimiento de queries ni sustituye una réplica. |
| Escalado de PostgreSQL | Afinamiento e índices primero; particionamiento selectivo; réplicas de lectura solo para cargas apropiadas; distribución/sharding como última etapa | Menor complejidad operativa y transaccional al principio, con ruta de crecimiento medible. |
| Búsqueda vectorial | No necesaria para el núcleo. Evaluar pgvector para búsqueda semántica de catálogo si aporta valor real | Una base vectorial no es adecuada para saldo, pedidos ni transacciones comerciales. |

## 2. Qué significa DataSource y por qué no elimina la necesidad de réplicas

En Java, DataSource es una interfaz/abstracción de conexión a base de datos. HikariCP es un pool de conexiones común para administrar y reutilizar conexiones de PostgreSQL. PgBouncer es un pooler/proxy del lado de PostgreSQL que permite reutilizar conexiones de servidor entre clientes, según su modo de pooling.

No debe confundirse:
- **Pool de conexiones:** limita y reutiliza conexiones de base de datos; evita abrir una conexión por request.
- **Caché:** evita algunas lecturas repetidas, si los datos toleran la política de frescura.
- **Particionamiento:** divide tablas grandes en particiones para facilitar mantenimiento y, según consultas, rendimiento.
- **Read replica:** mantiene otra copia física para descargar lecturas; requiere gestionar retraso de replicación y lecturas potencialmente obsoletas.
- **Sharding/distribución:** divide los datos entre nodos; introduce restricciones en joins, transacciones y clave de distribución.

No existe una tecnología que permita garantizar que una sola instancia de PostgreSQL sea suficiente para siempre sin conocer y medir la carga. Sí se puede retrasar una ampliación horizontal mediante datos bien modelados, índices, caché selectiva, consultas acotadas, pool bien dimensionado, reportes desacoplados y una máquina primaria adecuadamente dimensionada.

### Política por etapas

**Etapa A — MVP y primer despliegue**
- Un PostgreSQL primary duradero, backups/PITR y recuperación documentada.
- HikariCP con pool acotado; no crear pool ilimitado o de tamaño enorme.
- Índices para SKU, producto/variante/ubicación, claves idempotentes y fecha de movimientos.
- Consultas paginadas y planes de consulta medidos; evitar N+1.
- No crear replicas ni sharding sin datos que lo justifiquen.

**Etapa B — más tráfico con una base primaria**
- Revisar pg_stat_statements, métricas de pool/locks/IOPS/CPU, tiempos p95/p99, WAL y tamaño de índices.
- Ajustar consultas, índices, autovacuum, pool/conexiones y máquina antes de escalar bases.
- Usar Redis para lecturas repetitivas donde la frescura está definida.
- Particionar tablas append-only de alto crecimiento (por ejemplo, stock_movement, audit_event y outbox_event) si volumen/retención y planes de consulta lo justifican. Diseñar la clave para no romper unicidad, FKs ni consulta transaccional.
- Separar consultas de analítica/reporte de los flujos de venta mediante proyecciones o consumidores Kafka cuando los reportes compitan por recursos con el checkout.

**Etapa C — cuello de botella de lecturas**
- Añadir una o varias replicas PostgreSQL para reportes o consultas cuya eventual consistencia sea aceptable.
- Enrutar a primary: checkout, cotización autoritativa, reserva/despacho, cambios de saldo, lectura inmediatamente posterior al posting y confirmación del estado de un pedido.
- Enrutar a replica solo consultas que toleren lag (analítica, reportes, listados no críticos, dashboards), con política de fallback si el lag supera el límite.
- No presentar datos de una réplica atrasada como stock disponible garantizado.

**Etapa D — un nodo ya no basta**
- Evaluar una extensión/plataforma de distribución de PostgreSQL como Citus solo después de analizar volumen, modelo multiempresa, shard key y consultas cruzadas. Citus distribuye tablas y paraleliza consultas; implica un modelo de datos y operación más exigentes.
- Considerar un servicio especializado de base de datos gestionada o rediseño de almacenamiento solo con pruebas de carga, requisitos de RPO/RTO y evaluación de consistencia.
- No introducir sharding únicamente por la frase “millones de usuarios”; puede ser innecesario con miles de transacciones si están distribuidas en el tiempo y la carga está bien diseñada.

Referencias: [PgBouncer — pooling modes](https://www.pgbouncer.org/config), [Citus documentation](https://www.citusdata.com/docs/), [PostgreSQL documentation](https://www.postgresql.org/docs/).

## 3. Ventas transaccionales: la regla más importante

Una venta es una transacción de negocio, no un mensaje Kafka. PostgreSQL tiene que ser el punto de commit autoritativo para:
1. validar cotización y expiración;
2. confirmar los precios vigentes, descuentos autorizados y totales en servidor;
3. reservar o descontar disponibilidad mediante bloqueo/actualización atómica;
4. crear el pedido y sus líneas;
5. registrar movimientos/reservas de stock;
6. guardar el registro de idempotencia;
7. insertar eventos de auditoría y filas de Outbox pertinentes.

Todo lo anterior que pertenece al mismo contexto transaccional se confirma o revierte unido en PostgreSQL. No se debe publicar a Kafka antes del commit y después esperar a ver si la venta funcionó. La reserva tiene que bloquear la posibilidad de sobreventa en la base de datos.

### Patrón Transactional Outbox + Kafka

1. En la misma transacción que escribe la venta, insertar una fila en outbox_event.
2. Un publicador o CDC (por ejemplo, Debezium, si se aprueba) publica después el evento a Kafka.
3. Usar eventId, aggregateId, aggregateVersion, eventType, occurredAt, schemaVersion y una clave de partición estable.
4. Los consumidores validan el esquema y deduplican; el procesamiento repetido debe ser seguro.
5. Configurar retry con backoff, dead-letter/re-drive, métricas de lag y alertas.
6. Un consumidor puede enviar confirmación WhatsApp, alimentar analítica o actualizar un índice de búsqueda; su fallo no debe revertir una venta ya commitida.
7. Si un flujo que consume eventos va a cambiar stock o crear un efecto comercial, debe usar su propia idempotencia y transacción, no asumir entrega exactamente una vez entre Kafka y PostgreSQL.

Kafka ofrece garantías transaccionales para determinados flujos Kafka-a-Kafka, pero no hace atómica automáticamente una transacción PostgreSQL + Kafka externa. Para esa frontera se recomienda Outbox e idempotencia en consumidores.

Fuentes: [Apache Kafka — Design and delivery semantics](https://kafka.apache.org/43/design/design/), [Citus — scale out PostgreSQL](https://www.citusdata.com/docs/).

### Eventos iniciales sugeridos

- ProductPublished, ProductUpdated, ProductImageChanged
- StockAvailabilityChanged (proyección/aviso; no reemplaza la lectura autoritativa del checkout)
- PurchaseReceiptPosted, StockAdjusted, StockTransferred
- OrderCreated, OrderConfirmed, StockReserved, OrderDispatched, OrderCancelled
- ReturnPosted
- CustomerNotificationRequested

Publicar sin datos personales que el consumidor no necesita. El contrato de eventos se versiona; nunca enviar tokens/secrets. Para claves de partición, los eventos de orden deberían usar orderId, mientras que proyecciones de stock requieren una clave apropiada a la unidad de stock que deba mantenerse ordenada.

## 4. GraphQL: sí, pero como complemento acotado

Spring for GraphQL es una integración soportada para construir GraphQL en aplicaciones Spring. En Inventary, la decisión es implementar:
- REST para n8n, webhooks de integración, operaciones administrativas, comandos de inventario y APIs públicas estables.
- GraphQL para consultas del frontend que reúnen datos del catálogo, imágenes, variantes, categorías y disponibilidad no autoritativa con selección flexible de campos.
- Mutaciones GraphQL solo si realmente simplifican el cliente. Deben delegar a los mismos casos de uso de aplicación que REST; no duplicar reglas de stock ni cálculos.

Un ejemplo de query orientativa:

~~~graphql
query ProductCatalog($first: Int!, $after: String, $category: ID) {
  products(first: $first, after: $after, categoryId: $category) {
    edges {
      cursor
      node {
        id
        sku
        name
        description
        images { url alt }
        salePrice { amount currency }
        availability { status }
      }
    }
    pageInfo { hasNextPage endCursor }
  }
}
~~~

availability.status es una proyección apta para mostrar catálogo; no reserva stock ni ofrece garantía de compra. No exponer costo/margen en el tipo público.

### Controles GraphQL obligatorios

- Autenticación y autorización por campo/recurso y ámbito (tenant/warehouse), igual que en REST.
- Limitar profundidad, breadth/aliases, cantidad de items, costo de query, request size y duración.
- Paginación cursor-based para listados grandes; no devolver listas ilimitadas.
- DataLoader/batching para resolver relaciones sin N+1.
- Persisted/trusted queries para clientes propios donde sea viable; deshabilitar mutaciones arbitrarias y configurar introspection de acuerdo con el entorno.
- Timeouts, límites de concurrencia y rate limiting por actor/operación/costo, no solo por número de HTTP requests.
- Errores sin stack traces ni detalles internos; validar variables e inputs.
- No almacenar respuestas GraphQL de stock preciso en un CDN público ni crear caché compartida de respuestas que mezclen usuarios/tenants.

Referencias: [Spring for GraphQL](https://docs.spring.io/spring-graphql/reference/index.html), [GraphQL security](https://graphql.org/learn/security/), [OWASP GraphQL Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/GraphQL_Cheat_Sheet.html).

## 5. Estrategia de caché: fuerte para el catálogo, mínima para stock

No usar un TTL igual para todos los datos. Separar DTOs/cache keys por concepto, incluir versión de esquema cuando corresponda y evitar que las cachés compartidas filtren datos por empresa o permisos.

| Datos | Recomendación inicial | Invalidación/frescura |
|---|---|---|
| Bytes de imagen / miniaturas públicas | CDN u object storage con URL versionada; cache largo | Cambiar URL/version al sustituir la imagen. |
| Descripción, nombre, slug, categoría | Caffeine local en un nodo; Redis compartido al escalar | TTL 5–15 min y evicción/evento al editar/publicar. |
| Páginas de catálogo público | Redis/HTTP cache selectiva | TTL 30–120 s solo para contenido publicable; ETag/versionado si es útil. |
| Precio de venta publicado | Cache corto y versionado si se habilita | Revalidar precio del servidor durante quote y checkout; no usar precio cacheado para cerrar la venta. |
| Stock exacto y reservas | No cache compartida en comandos transaccionales | Leer saldo autoritativo en primary al reservar/checkout; puede mostrarse una proyección breve o disponibilidad discreta en catálogo. |
| Estado de pedido recién creado/pagado/cancelado | Leer primary o leer desde estado transaccional | No usar réplica/cache retrasada para confirmar pago/estado crítico. |
| Catálogos de roles/permisos | Cache pequeño si hay invalidación fiable | Invalidar al cambiar asignaciones/permisos; fallar seguro en acciones sensibles. |
| Reportes/analítica | Cache de consulta con TTL explícito | Eventual consistency y periodo de frescura visible. |

### Manejo de cambio de producto

- Actualizar el dato en PostgreSQL y añadir evento Outbox en la misma transacción.
- Después del commit, consumer de eventos invalida/actualiza Redis y proyecciones.
- Para reducir ventanas de inconsistencia, usar claves versionadas o borrado de caché directo tras el commit y un consumidor como mecanismo de reparación eventual. La verdad sigue siendo PostgreSQL.
- El checkout no depende de que se invalide Redis a tiempo.
- Para el stock mostrado en catálogo puede usarse inStock/lowStock/outOfStock con algunos segundos de frescura; cuando el cliente pulse comprar se vuelve a validar con precisión.

El TTL concreto se ajusta observando tasa de modificación, miss ratio, p95/p99 y tolerancia del negocio. Empezar con una caché reducida; no intentar cachear todas las consultas ni implementar invalidación distribuida prematuramente.

Referencia: [Spring Boot caching](https://docs.spring.io/spring-boot/reference/io/caching.html).

## 6. Datasource, pool y límites de conexiones

- Configurar HikariCP con maximumPoolSize, connection timeout, idle timeout, lifetime y métricas explícitas.
- Estimar presupuesto global: réplicas aplicación × pool máximo por réplica + jobs + n8n + otros clientes. No dimensionar cada réplica de la aplicación como si fuese la única.
- La concurrencia de request no debe superar sin límite el pool y las conexiones disponibles. Aplicar queue/timeout/backpressure del lado HTTP si la base está saturada.
- Empezar sin PgBouncer si la topología es pequeña y el pool de la aplicación basta.
- Añadir PgBouncer cuando el número de conexiones cliente/worker crezca o haya muchos procesos de corta vida. Elegir el modo de pool con cuidado: transaction pooling puede afectar funciones que dependen de sesión, temporary tables, prepared statements y estado de conexión, según versiones/configuración; comprobar compatibilidad con el driver/ORM.
- Mantener transacciones cortas. Nunca incluir llamadas a ChatGPT, WhatsApp o servicios externos dentro de la transacción de checkout.
- Usar read/write routing explícito solo después de tener réplicas; no enviar automáticamente una lectura post-commit a una réplica potencialmente retrasada.

Referencias: [PgBouncer config](https://www.pgbouncer.org/config), [PgBouncer usage](https://www.pgbouncer.org/usage).

## 7. Particionamiento e índices de alto crecimiento

Posibles candidatos tras medir volumen y retención:
- stock_movement por rango temporal, con acceso adicional por SKU/ubicación y source document.
- audit_event por tiempo/periodo de retención.
- outbox_event por estado/fecha para archivar filas entregadas sin afectar el consumidor.
- tablas de eventos o históricos de catálogo si la política requiere un gran historial.

Particionar exige diseñar claves de unicidad y FKs compatibles con PostgreSQL, consultas que incluyan filtros de partición y una política de detach/archiving. No particionar tablas pequeñas por estética. Mantener tabla de balance transaccional con una clave bien definida para updates atómicos; el particionamiento no reemplaza el locking/concurrency control.

## 8. Base de datos vectorial: decisión definitiva para MVP

**No usar una base de datos vectorial como base principal de Inventary.** No almacena ni impone las invariantes transaccionales necesarias para controlar stock y pedidos de la misma forma que el modelo relacional.

Para el caso de WhatsApp, inicialmente usar búsqueda SQL normal (SKU, nombre, categoría, descripción y sinónimos controlados). Si se detecta un valor claro en consultas como “algo para regalar a una persona que viaja” o productos descritos en lenguaje natural, introducir embeddings:
1. Generar embeddings de nombre, descripción, categoría y atributos comerciales publicables.
2. Guardarlos en una tabla vectorial de catálogo usando pgvector para conservar IDs y joins con los productos relacionales.
3. Recuperar candidatos semánticos, pero revalidar estado, precio y disponibilidad contra la tabla autoritativa antes de mostrarlos/comprar.
4. Actualizar el vector vía Outbox/Kafka después de la publicación del producto; el índice vectorial es una proyección reconstruible.
5. Evaluar un motor vectorial separado solo si el tamaño/latencia/operación de búsqueda vectorial lo justifica con mediciones.

pgvector añade búsqueda por similitud exacta o aproximada, índices HNSW/IVFFlat y uso integrado con las capacidades ACID de PostgreSQL, pero introduce memoria/CPU y coste de indexado. No se incorpora por la cantidad de usuarios, sino por la necesidad de búsqueda semántica.

Fuentes: [pgvector repository/documentation](https://github.com/pgvector/pgvector), [PostgreSQL documentation](https://www.postgresql.org/docs/).

## 9. Observabilidad y criterios de escalado

Antes de desplegar réplicas o sharding, medir y registrar:
- Requests/s por ruta, p50/p95/p99, tasa de 4xx/5xx y timeouts.
- Tiempo de query/lock, conexiones activas y pendientes, cache hit/miss, CPU, memoria, disco/IOPS.
- WAL, replication lag si hay réplicas, tamaño/crecimiento de tablas e índices, retraso de Kafka y profundidad de Outbox.
- Tasa de consumo/reintento de webhooks n8n/WhatsApp y número de pedidos idempotentes/conflictivos.
- Resultado de conciliación entre ledger y balances.
- Presupuesto de costo de ChatGPT/OpenAI y latencia del proveedor por separado de la latencia de la API de Inventary.

Definir SLO objetivo tras obtener carga de negocio real. No se promete un número de transacciones ni millones de usuarios concurrentes sin hardware, patrón de tráfico y medición.