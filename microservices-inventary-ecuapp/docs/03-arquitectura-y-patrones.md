# 03 — Arquitectura backend y patrones de diseño

## 1. Decisión de arquitectura

**Recomendación:** empezar con un **monolito modular con Arquitectura Hexagonal/Clean Architecture**, empaquetado como una aplicación Spring Boot, y extraer microservicios solo cuando existan límites de dominio, carga independiente, requisitos de despliegue o equipos que justifiquen el costo operacional.

El README plantea microservicios y un gateway como evolución. El código actual contiene una sola aplicación de arranque sin dominios implementados; desplegar varios microservicios desde el primer día elevaría complejidad de transacciones, autenticación, trazabilidad, despliegue y consistencia sin ofrecer todavía un beneficio medido. El diseño modular permite extraer servicios después sin mezclar reglas de negocio con infraestructura.

### Vista lógica

~~~mermaid
flowchart TB
    Client[Angular / clientes] --> Edge[TLS, reverse proxy / gateway futuro]
    Edge --> Api[Adaptadores REST Spring]
    Api --> Auth[Autenticación y autorización]
    Api --> App[Casos de uso / aplicación]
    App --> Domain[Dominio: catálogo, stock, compras y ventas]
    App --> Ports[Puertos de persistencia y eventos]
    Ports --> Pg[(PostgreSQL)]
    App --> Outbox[Outbox transaccional]
    Outbox --> Worker[Publicador de eventos]
    Worker --> Broker[(RabbitMQ / Kafka, si se justifica)]
    Worker --> Audit[Auditoría / integraciones]
~~~

La cola no es requisito del primer commit. Para una bitácora local, transacción y tabla outbox pueden bastar; añadir broker cuando se confirme necesidad de entrega asíncrona, reintentos o desacoplamiento. La auditoría crítica no debe depender de un “log temporal” en memoria.

## 2. Capas y responsabilidades

### Dominio (domain)

Entidades y reglas puras: Product, ProductVariant, Warehouse, Location, StockBalance, StockMovement, PurchaseOrder, GoodsReceipt, SalesOrder, SalesReturn, Supplier, Customer, User, Role y AuditEvent.

Debe contener invariantes: cantidades positivas, transiciones de estado permitidas, documento contabilizado inmutable, prohibición de stock disponible negativo y relación entre movimientos y fuente. Sin anotaciones ni tipos propios de Spring/JPA en objetos de dominio cuando sea viable.

### Aplicación (application)

Casos de uso/servicios: CreateProduct, PostGoodsReceipt, ReserveStock, DispatchSale, TransferStock, PostAdjustment, RegisterReturn y GetMovementHistory.

Orquesta políticas, transacciones y permisos de aplicación. No debería exponer JpaRepository ni aceptar objetos HTTP directamente. Los cambios de estado relevantes pasan por estos casos de uso.

### Puertos (application.port)

Interfaces de salida como ProductRepositoryPort, StockRepositoryPort, MovementRepositoryPort, PurchaseRepositoryPort, IdempotencyPort, AuditPort, ClockPort y EventPublisherPort.

Permiten que el caso de uso no dependa de PostgreSQL, broker o proveedor de identidad.

### Adaptadores (infrastructure.adapter.in y out)

- Entrada REST: controladores delgados, DTOs, validación de formato, mapeo y conversión de resultados a HTTP.
- Salida persistencia: repositorios JPA, entidades persistentes, consultas, locking y mappers.
- Salida externa: cliente SRI/pagos/proveedores solo si se aprueba integración.
- Observabilidad: métricas, tracing, log estructurado y health.

### Configuración (infrastructure.config)

Spring Security, CORS, Jackson, OpenAPI, manejo global de excepciones, límites de request, métricas, pool de conexión y perfiles. Toda configuración debe diferenciar local/desarrollo/producción y fail-fast ante secretos faltantes en producción.

## 3. Módulos de dominio propuestos

1. identity-access: integración de identidad, usuarios, roles y permisos.
2. catalog: productos, servicios, categorías, variantes, UOM y esquemas de atributos.
3. warehouse: almacenes, ubicaciones y reglas de ubicación.
4. inventory: saldos, ledger, reservas, transferencias, conteos y ajustes.
5. procurement: proveedores, órdenes de compra, recepciones y devoluciones a proveedor.
6. sales: clientes, órdenes, despacho, devoluciones y aprobaciones de anulación.
7. costing: capas de costo, políticas de valoración y cálculo de costo.
8. reporting: proyecciones de consulta y reportes paginados.
9. audit: registro consultable de actividad, con separación de permisos.
10. platform: errores, clock, ids, idempotencia, health y observabilidad.

En la primera etapa estos módulos pueden ser paquetes Maven dentro de un deployable. No crear servicios distribuidos solo porque exista una carpeta llamada microservices.

## 4. Patrones a implementar y cuándo

| Patrón | Aplicación | Restricción / beneficio |
|---|---|---|
| Hexagonal / Ports and Adapters | Dominio y casos de uso separados de Spring, BD y HTTP | Facilita mantener y extraer servicios; evitar interfaces triviales para cada clase sin valor. |
| Repository | Persistencia a través de puertos/implementaciones | El contrato del dominio no debe devolver entidades JPA. |
| Unit of Work / transacción | Posting de recepción, despacho, transferencia y devolución | Documento, ledger y balance deben confirmar o revertir juntos cuando comparten BD. |
| Strategy | Valuación FIFO, promedio ponderado y futuras políticas autorizadas | Selección de política explícita y determinista; no mezclar algoritmos en if/else central. |
| State Machine / transición de estado | Órdenes draft/confirmed/posted/cancelled | Rechazar transiciones inválidas; posting no se repite y los históricos se compensan. |
| Specification / Query Object | Filtros de catálogos, saldos y reportes | Construir filtros allowlisted, parametrizados y con paginación. |
| CQRS pragmático | Separar métodos de comando de consultas y DTOs de lectura | No exige event sourcing ni bases separadas en MVP. |
| Adapter | JPA, OIDC, correo, SRI, colas y sistemas externos | Cada dependencia externa queda reemplazable y con timeout. |
| Facade (aplicación) | Orquestación de flujos multi-repositorio | Mantener el controlador fino; una fachada no reemplaza reglas de dominio. |
| Domain Events + Transactional Outbox | Avisos/integraciones posteriores a commit | Evita publicar un evento para una transacción que luego revierte; consumidores idempotentes. |
| Idempotency Key | Comandos con efectos no repetibles | Clave delimitada por actor/operación; persistir respuesta/estado y hash del payload. |
| Optimistic Lock / guarded update | Saldo y edición de datos no contables | @Version ayuda a detectar colisiones; la regla de no stock negativo también debe imponerse en BD/SQL atómico. |
| Policy / RBAC | Permisos por recurso y acción | Autorización tanto por endpoint como por objeto y campos sensibles. |
| Outbox / retry con backoff | Procesamiento asíncrono recuperable | Dead-letter, límites y métricas si se añade broker. |

### Patrones que no se adoptarán automáticamente

- **Microservicios desde el inicio:** posponer salvo requerimiento de despliegue independiente o aislamiento verificable.
- **Event Sourcing:** no necesario para obtener historial; un ledger inmutable más eventos de auditoría cubre el MVP con menor complejidad.
- **Base de lectura separada/CQRS distribuido:** posponer hasta que volumen o latencia lo demanden.
- **EAV relacional universal:** evitarlo para los atributos de producto; usar plantilla de atributos tipados más JSONB validado/indexado. Para datos críticos (SKU, coste, vencimiento si gobierna FEFO), evaluar columnas/tabla normalizada, no ocultarlos en JSON arbitrario.
- **Bloqueo optimista como única defensa de stock:** insuficiente sin control atómico de disponibilidad y transacción.

## 5. Estructura de paquetes sugerida

~~~text
com.ecuapp.inventary
  identity/
    domain/
    application/
      port/in/
      port/out/
      usecase/
    infrastructure/adapter/in/web/
    infrastructure/adapter/out/persistence/
  catalog/...
  warehouse/...
  inventory/...
  procurement/...
  sales/...
  costing/...
  reporting/...
  audit/...
  platform/
    error/
    idempotency/
    observability/
    config/
~~~

No hace falta duplicar la estructura exacta de paquetes para cada submódulo si crea más ceremonia que claridad. La dependencia debe apuntar hacia dominio: los controladores no contienen cálculo comercial, y el dominio no importa Spring MVC/JPA.

## 6. Persistencia y transacciones

- PostgreSQL con Flyway o Liquibase; toda modificación de esquema es una migración versionada.
- NUMERIC(p,s) para cantidad y dinero con precisión definida; fechas UTC y moneda explícita.
- Constraints e índices de unicidad/FK/check en la base como última barrera.
- Indexar búsquedas frecuentes: SKU, código de barras, fecha/documento, combinación producto-ubicación y claves idempotentes. Validar índices JSONB según consultas reales.
- Una transacción de posting actualiza estado del documento, ledger, saldo/proyección, costo y outbox; si cualquier paso falla, no se confirma parcialmente.
- READ COMMITTED suele ser suficiente con restricciones, updates condicionados o bloqueo explícito bien diseñado; seleccionar el aislamiento por flujo, no elevarlo indiscriminadamente.
- No mantener transacciones abiertas mientras se llama a APIs externas. Usar outbox/saga si una integración requiere coordinación distribuida.
- Los reportes pesados no deben bloquear la contabilización; usar consultas acotadas, índices y vistas/proyecciones cuando sean necesarias.

## 7. Stack propuesto (sujeto a validación)

- Java 21, Spring Boot compatible con el parent definido en pom.xml.
- Spring Web MVC, Jakarta Bean Validation, Spring Security/OAuth2 Resource Server, Spring Data JPA/Hibernate.
- PostgreSQL y migraciones Flyway.
- Springdoc OpenAPI para contrato REST.
- Actuator, Micrometer y logging estructurado.
- Testcontainers y JUnit como herramientas para pruebas automatizadas escritas, aunque la ejecución local queda a cargo del usuario.
- Docker para entorno reproducible; CI con compilación, análisis de dependencias y chequeos de calidad.
- RabbitMQ/Kafka solo ante necesidad demostrada de eventos asíncronos.

Referencia de implementación de JWT en Spring Security: [OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## 8. Evaluación explícita de Spring WebFlux

**Decisión para el MVP: mantener Spring MVC como servidor HTTP principal y usar WebClient solo donde aporta valor.** La persistencia propuesta es PostgreSQL con JPA/Hibernate y el backend realiza transacciones multi-escritura críticas para stock, ventas y recepciones. Spring documenta que MVC es la elección más natural cuando se usan APIs de persistencia bloqueantes como JPA/JDBC; WebFlux aprovecha su modelo no bloqueante cuando toda la cadena puede evitar bloquear y hay suficiente concurrencia de E/S.

Referencia: [Spring Framework — Spring MVC or WebFlux?](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html).

El canal WhatsApp no obliga a que el backend haga streaming reactivo: el webhook y la conversación los orquesta n8n, mientras Inventary responde con operaciones breves de catálogo/carrito/cotización/pedido. Por tanto, el LLM no debe mantenerse dentro de una transacción del backend.

Condiciones para revaluar WebFlux en un servicio aislado:
- carga medida de conexiones simultáneas/streaming que lo justifique;
- cliente HTTP no bloqueante en toda la cadena;
- persistencia reactiva real (por ejemplo, R2DBC) en la ruta seleccionada;
- diseño y revisión específicos de transacciones, locking, backpressure, cancelación y timeouts;
- benchmark objetivo que demuestre una mejora con carga representativa.

No combinar WebFlux con JPA bloqueante invocado directamente desde event loops; mover llamadas bloqueantes a boundedElastic evita bloquear el loop pero no convierte esa arquitectura en reactiva de extremo a extremo. Empezar con MVC reduce complejidad y deja la posibilidad de un adaptador/servicio WebFlux futuro. Véase también [09 — integración conversacional](09-webflux-n8n-whatsapp-openai.md).