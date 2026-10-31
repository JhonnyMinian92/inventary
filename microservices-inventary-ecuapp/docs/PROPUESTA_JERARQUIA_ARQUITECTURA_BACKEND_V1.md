# Propuesta de jerarquía y arquitectura del backend Inventary

**Repositorio:** JhonnyMinian92/inventary  
**Módulo:** microservices-inventary-ecuapp  
**Rama de referencia:** master  
**Estado:** propuesta de arquitectura; no aplicada al código  
**Fecha:** 2026-10-30

---

## 1. Objetivo

Definir una estructura de directorios y paquetes uniforme, mantenible y escalable para el backend de Inventary, separando explícitamente:

- Contratos HTTP y DTOs de entrada/salida.
- Controladores REST y GraphQL.
- Casos de uso y servicios de aplicación.
- Modelo y reglas del dominio.
- Entidades y repositorios de persistencia.
- Adaptadores e integraciones externas.
- Configuración e infraestructura transversal.
- Pruebas unitarias, de integración y de arquitectura.

La propuesta toma como referencia el código y la documentación que existen actualmente en el repositorio. No supone que todos los dominios deban convertirse en procesos desplegables independientes ni que se deba reescribir el backend de una sola vez.

## 2. Diagnóstico del estado actual

El backend es actualmente un **monolito modular Spring Boot**, no un conjunto de microservicios desplegados de forma independiente. REST, GraphQL, seguridad, transacciones, persistencia y migraciones se ejecutan dentro de la misma aplicación.

La carpeta conserva el nombre histórico microservices-inventary-ecuapp, pero el nombre del directorio no cambia la topología real.

### 2.1 Problema de organización

En los paquetes de negocio se mezclan responsabilidades. Por ejemplo, el paquete catalog contiene, entre otros:

- ProductController y PublicCatalogController.
- ProductDtos, CategoryDtos y CatalogStructureDtos.
- Product y Category, que son entidades JPA.
- ProductRepository y otros repositorios Spring Data.
- ProductService y servicios auxiliares.
- Tipos de dominio, como ProductKind y BillingFrequency.

El mismo patrón se repite, con variaciones, en auth, inventory, purchasing, sales, partners, media, reports, returns, subscriptions, transfers, warehouse, whatsapp y audit.

La revisión del árbol de código encontró DTOs agrupados en archivos como ProductDtos, InventoryDtos, SaleDtos, PurchaseDtos, AuthDtos, UserDtos, RoleDtos y otros, ubicados junto a las entidades y servicios de sus dominios.

### 2.2 Arquitectura aplicada de forma desigual

El módulo catalog ya contiene una separación parcial con:

- application: comandos, resultados y casos de uso.
- application.port.out: puerto de persistencia.
- infrastructure.adapter.out.persistence: adaptador JPA.

Esta separación es una buena base, pero convive con controladores, DTOs, entidades y repositorios en el paquete raíz del dominio. Los demás dominios no mantienen un patrón equivalente.

También existen dependencias entre servicios de negocio que acceden directamente a repositorios o entidades de otros dominios. La propuesta establece límites más claros para evitar que esa dependencia crezca.

### 2.3 Objetivos de la reorganización

La estructura futura debe permitir:

1. Localizar un contrato HTTP sin buscarlo entre entidades o servicios.
2. Identificar inequívocamente qué clases representan solicitudes y cuáles respuestas.
3. Evitar que una entidad JPA sea devuelta directamente por un controlador.
4. Mantener las reglas de negocio fuera de los controladores y de la infraestructura.
5. Poder sustituir o probar persistencia e integraciones mediante puertos y adaptadores.
6. Mantener límites entre dominios, incluso mientras todos se despliegan como una aplicación.
7. Facilitar una futura extracción de servicios independientes cuando exista una necesidad real.

## 3. Arquitectura objetivo

Se recomienda una **arquitectura modular por dominios, con separación interna de capas y principios de arquitectura hexagonal**.

La organización será primero por contexto de negocio y después por responsabilidad técnica. No se recomienda un único directorio global que reúna todos los DTOs, controladores, entidades o servicios de toda la aplicación.

Cada dominio tendrá su propio espacio y será responsable de sus contratos, casos de uso, modelo e infraestructura.

### 3.1 Capas y responsabilidades

| Capa | Responsabilidad | Puede depender de |
|---|---|---|
| API / presentación | HTTP, GraphQL, validación de contratos, serialización y autenticación de la solicitud | Casos de uso y DTOs de API |
| Application | Orquestar casos de uso, transacciones, comandos, consultas y puertos | Dominio y puertos |
| Domain | Entidades de negocio, objetos de valor, invariantes, políticas y eventos de dominio | Java y reglas propias del dominio |
| Infrastructure | JPA, SQL, mensajería, almacenamiento, clientes HTTP y adaptadores | Puertos de aplicación y tecnologías externas |
| Platform / shared | Configuración técnica y capacidades transversales realmente compartidas | Componentes de infraestructura y contratos compartidos mínimos |

La dirección de las dependencias debe apuntar hacia el dominio y los contratos internos. El dominio no debe conocer Spring MVC, Spring Data, Hibernate, Kafka, PostgreSQL ni formatos HTTP.

### 3.2 Regla fundamental para DTOs y entidades

Los DTOs deben residir exclusivamente en los paquetes de API. Las entidades de persistencia deben residir exclusivamente en infraestructura de persistencia.

La estructura propuesta distingue cuatro conceptos:

- Request DTO: representa los datos que el cliente puede enviar.
- Response DTO: representa exclusivamente los datos que la API puede exponer.
- Entidad de dominio: representa conceptos, estado e invariantes del negocio.
- Entidad JPA: representa el mapeo de persistencia de esos datos en la base.

Una entidad de dominio y una entidad JPA pueden compartir algunos atributos, pero no deben confundirse ni intercambiarse automáticamente. Solo deben existir modelos separados cuando haya una frontera real que justifique el mapeo.

**Nunca se debe devolver una entidad JPA desde un controlador ni aceptar una entidad JPA como cuerpo de una petición.**

## 4. Jerarquía de directorios propuesta

La siguiente es la estructura objetivo para el backend completo. Los paquetes internos se repiten por dominio cuando corresponda; no es obligatorio crear directorios vacíos.

~~~text
microservices-inventary-ecuapp/
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .env.example
├── .gitignore
├── mvnw
├── mvnw.cmd
├── .mvn/
│
├── scripts/
│   ├── smoke_test_api.py
│   └── [scripts de mantenimiento y validación]
│
├── docs/
│   ├── arquitectura/
│   │   ├── jerarquia-backend.md
│   │   ├── limites-de-dominios.md
│   │   └── decisiones-arquitectonicas/
│   ├── api/
│   ├── operaciones/
│   └── desarrollo/
│
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/ecuapp/inventary/
    │   │       │
    │   │       ├── InventaryApplication.java
    │   │       │
    │   │       ├── platform/
    │   │       │   ├── config/
    │   │       │   ├── security/
    │   │       │   │   ├── jwt/
    │   │       │   │   ├── authorization/
    │   │       │   │   └── filters/
    │   │       │   ├── observability/
    │   │       │   ├── cache/
    │   │       │   ├── messaging/
    │   │       │   └── web/
    │   │       │       ├── error/
    │   │       │       └── filter/
    │   │       │
    │   │       ├── shared/
    │   │       │   ├── domain/
    │   │       │   │   ├── exception/
    │   │       │   │   └── valueobject/
    │   │       │   ├── application/
    │   │       │   │   └── pagination/
    │   │       │   └── infrastructure/
    │   │       │       └── persistence/
    │   │       │
    │   │       ├── auth/
    │   │       ├── audit/
    │   │       ├── catalog/
    │   │       ├── inventory/
    │   │       ├── media/
    │   │       ├── partners/
    │   │       ├── purchasing/
    │   │       ├── reports/
    │   │       ├── returns/
    │   │       ├── sales/
    │   │       ├── subscriptions/
    │   │       ├── transfers/
    │   │       ├── warehouse/
    │   │       └── whatsapp/
    │   │
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-dev.yaml
    │       ├── application-prod.yaml
    │       ├── db/
    │       │   └── migration/
    │       │       └── V*__*.sql
    │       └── graphql/
    │           └── schema.graphqls
    │
    └── test/
        ├── java/
        │   └── com/ecuapp/inventary/
        │       ├── architecture/
        │       ├── integration/
        │       │   ├── api/
        │       │   ├── persistence/
        │       │   └── messaging/
        │       ├── support/
        │       ├── auth/
        │       ├── audit/
        │       ├── catalog/
        │       ├── inventory/
        │       ├── media/
        │       ├── partners/
        │       ├── purchasing/
        │       ├── reports/
        │       ├── returns/
        │       ├── sales/
        │       ├── subscriptions/
        │       ├── transfers/
        │       ├── warehouse/
        │       └── whatsapp/
        │
        └── resources/
            └── application-test.yaml
~~~

### 4.1 Estructura interna estándar de un dominio

Esta es la estructura de referencia que se debe aplicar a cada contexto de negocio:

~~~text
<dominio>/
│
├── api/
│   ├── rest/
│   │   ├── controller/
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   └── response/
│   │   └── mapper/
│   │
│   └── [adaptadores de entrada específicos, si existen]
│
├── application/
│   ├── command/
│   ├── query/
│   ├── usecase/
│   ├── service/
│   ├── mapper/
│   └── port/
│       ├── in/
│       └── out/
│
├── domain/
│   ├── model/
│   ├── valueobject/
│   ├── enums/
│   ├── event/
│   ├── policy/
│   └── exception/
│
└── infrastructure/
    ├── persistence/
    │   ├── entity/
    │   ├── mapper/
    │   ├── repository/
    │   └── adapter/
    │
    ├── messaging/
    ├── integration/
    └── storage/
~~~

**Nota:** cada dominio solo incorpora los subdirectorios que realmente necesite. Por ejemplo, un dominio sin mensajería no necesita crear messaging; uno que no almacene archivos no necesita storage.

## 5. Aplicación concreta al dominio catalog

Catalog debe servir como primer módulo de referencia porque ya tiene una implementación parcial de puertos y adaptadores.

~~~text
catalog/
│
├── api/
│   ├── rest/
│   │   ├── controller/
│   │   │   ├── ProductController.java
│   │   │   ├── PublicCatalogController.java
│   │   │   ├── CategoryController.java
│   │   │   └── CatalogStructureController.java
│   │   │
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── ProductCreateRequest.java
│   │   │   │   ├── ProductUpdateRequest.java
│   │   │   │   ├── CategoryCreateRequest.java
│   │   │   │   └── CategoryUpdateRequest.java
│   │   │   │
│   │   │   └── response/
│   │   │       ├── ProductResponse.java
│   │   │       ├── PublicProductResponse.java
│   │   │       ├── ProductVariantResponse.java
│   │   │       ├── ProductImageResponse.java
│   │   │       └── CategoryResponse.java
│   │   │
│   │   └── mapper/
│   │       └── ProductApiMapper.java
│   │
│   └── graphql/
│       └── controller/
│
├── application/
│   ├── command/
│   │   ├── CreateProductCommand.java
│   │   └── UpdateProductCommand.java
│   ├── query/
│   │   ├── ProductQuery.java
│   │   └── ProductSearchQuery.java
│   ├── usecase/
│   │   ├── ProductUseCases.java
│   │   └── CategoryUseCases.java
│   ├── service/
│   │   ├── ProductApplicationService.java
│   │   └── CategoryApplicationService.java
│   ├── mapper/
│   │   └── ProductResultMapper.java
│   └── port/
│       ├── in/
│       └── out/
│           ├── ProductPersistencePort.java
│           ├── InventoryQueryPort.java
│           ├── PurchasingQueryPort.java
│           └── ProductImageQueryPort.java
│
├── domain/
│   ├── model/
│   │   ├── Product.java
│   │   ├── Category.java
│   │   ├── ProductVariant.java
│   │   └── CategoryAttributeDefinition.java
│   ├── valueobject/
│   ├── enums/
│   │   ├── ProductKind.java
│   │   ├── BillingFrequency.java
│   │   ├── ServiceBilling.java
│   │   └── UnitDimension.java
│   ├── policy/
│   ├── event/
│   └── exception/
│
└── infrastructure/
    └── persistence/
        ├── entity/
        │   ├── ProductJpaEntity.java
        │   ├── CategoryJpaEntity.java
        │   ├── ProductVariantJpaEntity.java
        │   └── CategoryAttributeDefinitionJpaEntity.java
        ├── mapper/
        │   └── ProductPersistenceMapper.java
        ├── repository/
        │   ├── SpringDataProductRepository.java
        │   └── SpringDataCategoryRepository.java
        └── adapter/
            └── JpaProductPersistenceAdapter.java
~~~

Los nombres del árbol representan la organización deseada, no archivos que ya existan. En la migración se conservarán las reglas, contratos y comportamientos vigentes. Los puertos de consulta entre dominios son ilustrativos: solo se crearán cuando una dependencia real los necesite.

### 5.1 División de ProductDtos

El archivo actual ProductDtos agrupa diferentes contratos. Se recomienda dividirlo por responsabilidad:

| Contrato actual | Destino propuesto |
|---|---|
| WriteRequest | ProductCreateRequest y ProductUpdateRequest, si sus reglas difieren |
| View | ProductResponse |
| PublicView | PublicProductResponse |
| PublicVariantView | ProductVariantResponse |
| PublicImageView | ProductImageResponse |
| PublicDetailView | PublicProductDetailResponse |
| PublicCategoryView | PublicCategoryResponse |

No se deben separar clases mecánicamente si tienen el mismo propósito y contrato estable; la división debe seguir los casos de uso, la seguridad y la semántica real de cada endpoint.

## 6. Organización de los demás dominios

Los dominios actuales se conservan como contextos independientes dentro del monolito modular.

| Dominio | Responsabilidades y ubicación principal |
|---|---|
| auth | API de login y administración; aplicación de autenticación y roles; modelo de usuario, rol y permiso; adaptadores de persistencia y seguridad |
| audit | Contratos de consulta, casos de uso, modelo de eventos y persistencia de auditoría |
| catalog | Productos, categorías, atributos, variantes, unidades y catálogo público |
| inventory | Saldos, movimientos, reservas, ajustes, conteos, reposición y costeo |
| media | Contratos de imágenes, validación, metadatos y adaptador de almacenamiento |
| partners | Clientes, proveedores y socios comerciales |
| purchasing | Órdenes de compra, recepciones y devoluciones a proveedor |
| reports | Consultas, proyecciones de lectura, cálculos y contratos de reportes |
| returns | Devoluciones de ventas y sus reglas |
| sales | Ventas, líneas, precios y operaciones de Outbox |
| subscriptions | Planes, cuotas, ciclos de facturación y procesos programados |
| transfers | Transferencias entre almacenes, despacho, recepción y reversión |
| warehouse | Almacenes y ubicaciones |
| whatsapp | Carritos, cotizaciones, pedidos y adaptadores de integración |

### 6.1 Particularidades por módulo

**auth:** los controladores y DTOs pertenecen a API; usuarios, roles y permisos al modelo del dominio; la verificación JWT, filtros y configuración de Spring Security deben quedar en platform/security o en los adaptadores de seguridad correspondientes. Las políticas de acceso y reglas de negocio no deben mezclarse con la configuración HTTP.

**inventory:** los movimientos y registros que constituyen el historial de inventario deben tener una separación explícita respecto de los saldos calculados o materializados. La reorganización de paquetes no debe alterar el comportamiento transaccional, las restricciones ni los bloqueos.

**purchasing y sales:** órdenes, recepciones y ventas deben conservar sus límites transaccionales. Los DTOs de aprobación, recepción, cancelación y creación deben ser contratos distintos cuando tengan campos o permisos diferentes.

**sales y messaging:** OutboxEvent, OutboxEventRepository, OutboxPublisher, OutboxRelay, KafkaDomainEventConsumer y los componentes de Inbox deben ubicarse en infraestructura de mensajería. La API de operaciones del Outbox debe permanecer en el adaptador de entrada del dominio o en un módulo administrativo claramente delimitado.

**media:** la API gestiona los metadatos y contratos de imagen. El almacenamiento en disco o en otro medio corresponde a infraestructura/storage; las entidades JPA y repositorios corresponden a infraestructura/persistence.

**reports:** los modelos de lectura y proyecciones de reportes no deben confundirse con DTOs HTTP ni reutilizar entidades JPA como respuesta pública. Deben existir mappers explícitos hacia los contratos de respuesta.

**whatsapp:** los controladores de integración, claves de máquina, DTOs y reglas de negocio deben estar separados. La comunicación HTTP externa, cuando exista, corresponde a infrastructure/integration.

## 7. Platform y shared: límites de lo transversal

No todo lo que actualmente está en common debe acabar en shared. Esa carpeta no debe convertirse en un repositorio general de clases sin dueño.

### 7.1 Platform

Debe contener capacidades técnicas que sirven a toda la aplicación:

- Configuración de Spring, caché, Kafka y OpenAPI.
- Seguridad HTTP, filtros, manejo JWT e integración con Spring Security.
- Correlación de solicitudes y observabilidad.
- Manejo HTTP de excepciones y problemas de API.
- Configuración de infraestructura y arranque.

Las reglas de negocio de un dominio no deben moverse a platform por el simple hecho de que se utilicen desde varios controladores.

### 7.2 Shared

Solo debe contener conceptos compartidos y estables:

- Excepciones realmente genéricas del dominio o contratos base.
- Objetos de valor comunes cuando exista una semántica compartida.
- Paginación y tipos técnicos compartidos.
- Interfaces o utilidades que no introduzcan dependencia entre dominios.

No debe contener DTOs de Product, Sale, Purchase, Inventory ni otras áreas comerciales.

### 7.3 Reubicación orientativa de paquetes transversales actuales

| Ubicación actual | Destino propuesto |
|---|---|
| config | platform/config, platform/security y platform/web/filter, según responsabilidad |
| common/GlobalExceptionHandler | platform/web/error |
| common/ApiProblemFactory | platform/web/error |
| common/BusinessRuleException y errores técnicos compartidos | shared/domain/exception, solo si su semántica es realmente transversal |
| common/Pages y tipos de paginación | shared/application/pagination |
| common/IdempotencyRecord y su persistencia | shared/infrastructure/persistence o módulo propietario de idempotencia, según alcance |
| common/TimeRange | shared/domain/valueobject, si tiene semántica transversal |
| domain/DomainException y DomainErrorCode | shared/domain/exception solo si no pertenecen a un dominio específico |
| graphql/GraphqlCatalogController | catalog/api/graphql/controller |
| configuración GraphQL | platform/config o platform/security |

La ubicación definitiva debe decidirse por responsabilidad, no únicamente por el nombre de la clase.

## 8. Reglas obligatorias de dependencias

Para evitar que la nueva jerarquía se convierta solo en un cambio cosmético, se establecen estas reglas:

1. Los controladores no acceden directamente a repositorios Spring Data ni a entidades JPA.
2. Los controladores reciben Request DTOs y devuelven Response DTOs.
3. Los DTOs no contienen lógica de persistencia ni exponen referencias JPA.
4. El dominio no importa clases de Spring, Hibernate, Kafka, HTTP ni detalles de PostgreSQL.
5. La aplicación coordina casos de uso y depende de interfaces/puertos, no de implementaciones de infraestructura.
6. Los adaptadores de persistencia implementan puertos y encapsulan Spring Data/JPA.
7. Los mappers de API transforman contratos HTTP; los mappers de persistencia transforman modelos de dominio y entidades JPA. No se deben confundir.
8. Un dominio no debe importar entidades ni repositorios internos de otro dominio.
9. Las consultas entre dominios se realizan mediante contratos de aplicación o puertos explícitos.
10. Los cambios de stock, costo, ventas, compras, recepciones y transferencias deben conservar sus límites transaccionales y controles de concurrencia.
11. Los eventos de dominio expresan hechos de negocio; los envelopes Kafka y los registros Outbox/Inbox son responsabilidad de integración.
12. La configuración, seguridad, almacenamiento y mensajería no deben introducir dependencias hacia el dominio.

### 8.1 Flujo de una solicitud REST

~~~text
Cliente HTTP
    |
    v
Controller REST
    |
    v
Request DTO + validación de contrato
    |
    v
API Mapper
    |
    v
Caso de uso / Application Service
    |
    +----> Modelo de dominio y reglas de negocio
    |
    +----> Puerto de salida
               |
               v
        Adaptador de infraestructura
               |
               v
        Repositorio Spring Data / JPA
               |
               v
           PostgreSQL

Resultado del caso de uso
    |
    v
Response Mapper
    |
    v
Response DTO
    |
    v
Respuesta HTTP
~~~

Las transacciones deben delimitarse en el caso de uso o servicio de aplicación apropiado. La capa API no debe contener reglas transaccionales de negocio.

## 9. Convenciones de nombres

| Tipo | Convención | Ejemplo |
|---|---|---|
| Controlador REST | Nombre + Controller o RestController | ProductController |
| Solicitud de creación | Nombre + CreateRequest | ProductCreateRequest |
| Solicitud de actualización | Nombre + UpdateRequest | ProductUpdateRequest |
| Solicitud de acción | Nombre + ActionRequest | ApprovePurchaseOrderRequest |
| Respuesta | Nombre + Response | ProductResponse |
| Respuesta pública | Public + nombre + Response | PublicProductResponse |
| Mapper de API | Nombre + ApiMapper | ProductApiMapper |
| Caso de uso | Verbo + nombre + UseCase o contrato agrupado por caso de uso | CreateProductUseCase |
| Servicio de aplicación | Nombre + ApplicationService | ProductApplicationService |
| Comando | Verbo + nombre + Command | CreateProductCommand |
| Consulta | Nombre + Query | SearchProductsQuery |
| Entidad de dominio | Nombre del concepto | Product |
| Entidad JPA | Nombre + JpaEntity | ProductJpaEntity |
| Repositorio Spring Data | SpringData + nombre + Repository | SpringDataProductRepository |
| Puerto de persistencia | Nombre + PersistencePort | ProductPersistencePort |
| Adaptador JPA | Jpa + nombre + Adapter | JpaProductPersistenceAdapter |
| Mapper de persistencia | Nombre + PersistenceMapper | ProductPersistenceMapper |
| Evento de dominio | Nombre del hecho + Event | ProductCreatedEvent |

Evitar archivos agregadores de DTOs como ProductDtos.java, InventoryDtos.java o SaleDtos.java cuando mezclen contratos de entrada, salida, públicos y administrativos. Los contratos deben dividirse por uso y responsabilidad.

No es necesario crear una clase por cada campo ni multiplicar interfaces sin una necesidad concreta.

## 10. Estrategia de migración recomendada

La reorganización se debe ejecutar por etapas, conservando el comportamiento observable. No se recomienda mover los 250 archivos Java de una sola vez ni aplicar reemplazos masivos sin validar las dependencias.

### Fase 0 — Línea base y protección

- Registrar el SHA de partida.
- Ejecutar Maven verify contra PostgreSQL de prueba.
- Registrar resultados de pruebas y contratos HTTP.
- Capturar rutas, respuestas, códigos HTTP, permisos y reglas de negocio relevantes.
- Confirmar las migraciones Flyway y los perfiles de ejecución.
- Establecer que no se modificará el esquema ni se eliminarán datos por una reorganización de paquetes.

### Fase 1 — Separar DTOs y API

- Crear los paquetes api/rest/controller, api/rest/dto/request, api/rest/dto/response y api/rest/mapper por dominio.
- Mover los controladores y contratos conservando temporalmente sus nombres y semántica.
- Separar los DTOs de entrada y salida en clases explícitas cuando sus responsabilidades sean diferentes.
- Garantizar que ningún controlador exponga una entidad JPA.
- Verificar validaciones, serialización JSON, paginación y códigos HTTP.

Esta fase resuelve primero el problema visible de mezclar DTOs con entidades.

### Fase 2 — Unificar aplicación y casos de uso

- Aplicar una estructura consistente de application.
- Separar comandos, consultas, servicios y puertos.
- Normalizar el patrón parcial ya existente en catalog.
- Evitar duplicar servicios o contratos que ya estén implementados.
- Reemplazar dependencias directas entre dominios por puertos o contratos explícitos cuando sea necesario.

### Fase 3 — Separar dominio y persistencia

- Identificar las clases que representan entidades JPA.
- Extraer el modelo de dominio independiente de infraestructura cuando aporte aislamiento real.
- Mover las clases JPA a infrastructure/persistence/entity.
- Ubicar repositorios Spring Data bajo infrastructure/persistence/repository.
- Crear mappers de persistencia y adaptadores para los puertos definidos.
- Preservar claves, relaciones, restricciones, índices, locks, control de concurrencia y semántica transaccional.

Esta fase requiere especial cuidado: algunas clases actuales combinan anotaciones JPA y comportamiento del negocio. No se debe limitar a renombrarlas ni duplicar modelos sin una estrategia de mapeo.

### Fase 4 — Separar infraestructura transversal e integraciones

- Reorganizar seguridad, configuración, filtros, caché y observabilidad.
- Separar Outbox, Inbox y Kafka de los servicios de negocio.
- Separar almacenamiento de imágenes del contrato de API.
- Mantener los procesos programados dentro de su dominio o adaptador correspondiente.

### Fase 5 — Pruebas y reglas arquitectónicas

- Reorganizar las pruebas por unidad, integración y arquitectura.
- Añadir pruebas de límites entre módulos y de dependencias permitidas.
- Validar el arranque de Spring y el escaneo de componentes.
- Ejecutar Maven verify y pruebas de integración con PostgreSQL.
- Validar Docker Compose y el perfil Kafka cuando corresponda.
- Verificar que el contrato HTTP y el comportamiento de negocio sean equivalentes.

### Fase 6 — Cierre documental

- Actualizar README y documentación de arquitectura.
- Documentar los paquetes definitivos y las reglas para crear nuevos módulos.
- Eliminar referencias obsoletas a las rutas antiguas.
- Registrar los archivos movidos y las excepciones arquitectónicas justificadas.

## 11. Pruebas y criterios de aceptación

La migración estructural solo se considera terminada cuando se cumplan todos estos puntos:

- [ ] DTOs de cada dominio separados de entidades y repositorios.
- [ ] Requests y responses ubicados en paquetes diferentes.
- [ ] Ningún controlador retorna entidades JPA.
- [ ] Ningún controlador recibe entidades JPA como entrada.
- [ ] Casos de uso accesibles mediante contratos claros.
- [ ] Repositorios Spring Data ubicados en infraestructura.
- [ ] Adaptadores implementan los puertos que correspondan.
- [ ] El dominio no depende de Spring ni de infraestructura.
- [ ] Los dominios no acceden directamente a repositorios internos de otros dominios.
- [ ] Los límites transaccionales e invariantes de negocio se mantienen.
- [ ] Las migraciones Flyway existentes permanecen íntegras y en el mismo orden.
- [ ] No se generan cambios de esquema por el mero cambio de paquetes.
- [ ] Las respuestas JSON conservan los contratos compatibles.
- [ ] Las reglas RBAC y la protección de información sensible continúan funcionando.
- [ ] Outbox, Inbox y Kafka conservan idempotencia, deduplicación y política de reintentos.
- [ ] Maven verify termina correctamente.
- [ ] Pruebas de integración contra PostgreSQL terminan correctamente.
- [ ] Docker Compose arranca correctamente con el volumen de datos esperado.
- [ ] Documentación y pruebas reflejan la estructura final.

## 12. Consideraciones importantes para no introducir regresiones

### 12.1 No cambiar lógica al mover paquetes

La primera etapa debe ser estructural. No se deben aprovechar los movimientos para modificar reglas de negocio, fórmulas de costo, precios, cálculo de saldos, políticas de cancelación, permisos o contratos de API.

Cualquier corrección funcional debe realizarse en un cambio separado, con sus propias pruebas.

### 12.2 No cambiar las migraciones ni recrear la base

La reorganización Java no justifica cambiar nombres de tablas, columnas, restricciones, índices, claves foráneas ni archivos Flyway. Las migraciones existentes deben conservarse íntegras.

### 12.3 No convertir artificialmente el proyecto en microservicios

Separar paquetes no equivale a separar procesos. En esta propuesta se mantienen una aplicación Spring Boot, un ciclo de despliegue, una configuración de arranque y el mecanismo de persistencia actual.

Una extracción futura de servicios requerirá límites de datos, propiedad de cada contexto, contratos de comunicación, observabilidad y despliegue independiente. No forma parte de esta reorganización.

### 12.4 No versionar archivos generados de Maven

La inspección encontró un directorio con nombre de marcador de posición de Maven dentro del backend, con contenido generado como classes, recursos copiados y test-classes. No forma parte de la jerarquía fuente deseada.

Debe revisarse por separado y evitar que se versionen salidas de compilación. El directorio de build debe ser administrado por Maven y excluido de Git. Esta propuesta no elimina esos archivos.

## 13. Decisión recomendada

Se recomienda aprobar esta jerarquía como arquitectura objetivo y utilizar catalog como módulo piloto, porque ya cuenta con una separación parcial de casos de uso y puertos de persistencia.

El orden recomendado es:

1. Separar DTOs, controladores y mappers por dominio.
2. Unificar application y los contratos de casos de uso.
3. Aislar entidades JPA y repositorios en infrastructure/persistence.
4. Normalizar dependencias entre dominios.
5. Aplicar las mismas reglas al resto de módulos.
6. Añadir pruebas que impidan que la arquitectura vuelva a degradarse.

**Resultado esperado:** una base de código organizada por capacidades de negocio, con contratos API independientes, modelo de dominio protegido de infraestructura, persistencia encapsulada y módulos preparados para evolucionar sin convertir el backend en un conjunto de paquetes fuertemente acoplados.

---

## 14. Alcance de este documento

Este documento define una propuesta. No ejecuta movimientos, renombrados, refactorizaciones ni eliminación de archivos.

La implementación debe realizarse posteriormente, por etapas, con pruebas y revisión de cada cambio. La estructura objetivo no autoriza a cambiar contratos públicos, datos, migraciones ni comportamiento funcional sin una decisión específica.
