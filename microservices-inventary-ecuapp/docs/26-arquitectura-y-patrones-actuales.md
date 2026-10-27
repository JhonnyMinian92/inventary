# 26 — Arquitectura, jerarquía y patrones actuales

## 1. Decisión arquitectónica efectiva

El backend es un **monolito modular Spring Boot**, ejecutado como una aplicación conectada a PostgreSQL. El nombre “microservices-inventary-ecuapp” es histórico; no implica despliegues separados por dominio. REST y GraphQL comparten proceso, seguridad, migraciones y persistencia. La SPA React/Vite es un artefacto separado y consume la API; no se observó BFF/gateway interno en este repositorio.

## 2. Diagrama lógico

~~~mermaid
flowchart LR
  U[Usuario navegador] --> FE[React + Vite SPA]
  FE -->|HTTPS JSON Bearer| API[Spring Boot MVC]
  W[n8n / WhatsApp] -->|X-Integration-Key| API
  C[Consumidor público] -->|REST / GraphQL| API
  API --> SEC[Spring Security JWT RBAC]
  SEC --> CTRL[REST controllers / GraphQL]
  CTRL --> SVC[Servicios de aplicación y negocio]
  SVC --> DOM[Entidades, reglas e invariantes]
  SVC --> REP[Repositorios JPA / SQL]
  REP --> PG[(PostgreSQL + Flyway)]
  SVC --> OB[Transactional Outbox]
  OB -. relay opcional .-> K[Kafka]
  API --> CACHE[Caffeine local]
  API --> OPS[Actuator / logs / métricas]
~~~

La publicación hacia Kafka es opcional. Los consumidores y proyecciones Kafka no están en alcance. Caffeine es caché local del proceso, no distribuida.

## 3. Jerarquía actual del repositorio

~~~text
inventary/
├── README.md
├── AUDITORIA_TECNICA_PENDIENTES.md
├── .github/workflows/
│   ├── backend-ci.yml
│   └── frontend-ci.yml
├── app-inventary-ecuapp/
│   ├── package.json / .nvmrc / .env.example
│   ├── index.html / vite.config.js
│   ├── src/
│   │   ├── main.jsx
│   │   ├── App.jsx                 # sesión, navegación, coordinación
│   │   ├── api.js                  # REST, token, errores, idempotencia
│   │   ├── components/             # tablas y detalles
│   │   ├── hooks/                  # diálogos y accesibilidad
│   │   ├── features/
│   │   │   ├── catalog/
│   │   │   ├── dashboard/
│   │   │   ├── inventory/
│   │   │   ├── operations/
│   │   │   ├── purchasing/
│   │   │   ├── reports/
│   │   │   └── sales/
│   │   └── styles.css
│   └── docs/                       # cobertura API, roadmap, soporte
└── microservices-inventary-ecuapp/
    ├── pom.xml / Dockerfile / docker-compose.yml / .env.example
    ├── mvnw / mvnw.cmd
    ├── scripts/smoke_test_api.py
    ├── src/main/java/com/ecuapp/inventary/
    │   ├── audit/
    │   ├── auth/
    │   ├── catalog/                # CRUD y arquitectura por puertos en parte
    │   ├── common/                 # errores, páginas, idempotencia, periodos
    │   ├── config/                 # seguridad, CORS, rate limit, filtros
    │   ├── graphql/
    │   ├── inventory/              # saldo, movimientos, valoración, conteos
    │   ├── media/
    │   ├── partners/
    │   ├── purchasing/
    │   ├── reports/
    │   ├── returns/
    │   ├── sales/                  # ventas, outbox y relay
    │   ├── transfers/
    │   ├── warehouse/
    │   └── whatsapp/
    ├── src/main/resources/
    │   ├── application.yaml
    │   ├── graphql/schema.graphqls
    │   └── db/migration/V1..V14
    ├── src/test/java/
    └── docs/                       # contrato, arquitectura, runbook y evidencia
~~~

El diagrama enumera las carpetas principales del árbol de referencia; para editar código, inspeccionar la ruta exacta del SHA que se despliega.

## 4. Límites de capas existentes

### HTTP / presentación

Los controladores reciben peticiones, validan path/query/body con DTOs y anotaciones, identifican el principal cuando corresponde y delegan en servicios. Los DTOs proyectan campos de salida y permiten ocultar costos. El handler común normaliza errores HTTP.

### Seguridad transversal

SecurityConfig configura JWT HS256, issuer/audience, expiración, usuario activo, versión de sesión y authorities. La autorización usa reglas HTTP y verificaciones @PreAuthorize. Filtros agregan request ID, auditoría, rate limit y límite del cuerpo.

### Aplicación / negocio

Servicios como ProductService, InventoryService, PurchaseOrderService, SalesController/service, servicios de devolución, StockTransferService, CatalogStructureService, InventoryCostingService y servicios de reportes coordinan reglas y transacciones. Los comandos que afecten stock deben correr en el backend.

### Persistencia

Spring Data/JPA y consultas SQL especializadas acceden a PostgreSQL. Flyway mantiene V1–V14; Hibernate valida el esquema y open-in-view está deshabilitado. Restricciones, locks, inserción idempotente y triggers append-only complementan las reglas Java.

### Integraciones / plataforma

GraphQL es lectura de catálogo, no una segunda API de comandos. WhatsApp usa una credencial de máquina. Outbox persiste eventos en la transacción de negocio; el relay publica fuera de la transacción de reclamación y permite redrive. Actuator ofrece health/probes e info/métricas bajo permisos.

## 5. Hexagonal: aplicada parcialmente

El módulo de catálogo incluye ProductUseCases, ProductCommand/ProductResult, puertos y un adaptador de persistencia JPA. El resto de los dominios usa mayormente controlador → servicio → repositorio/entidad al estilo Spring. Por tanto, la arquitectura hexagonal no es uniforme y no debe anunciarse como completamente implementada.

No se recomienda reescribir módulos solo para crear interfaces. Extraer puertos cuando exista una necesidad verificable de sustitución de infraestructura, testabilidad o aislamiento.

## 6. Patrones presentes en código

| Patrón | Dónde / para qué |
|---|---|
| Layered architecture / Service Layer | Controladores, servicios, repositorios, DTOs |
| Repository / Data Mapper | Spring Data JPA y consultas SQL específicas |
| DTO / Projection | Contratos de entrada y salida, ocultamiento de costos |
| Dependency Injection | Beans Spring y dependencias por constructor |
| Transaction Script / Unit of Work | Servicios transaccionales |
| RBAC / Policy enforcement | SecurityConfig y @PreAuthorize |
| Idempotency key | Registro backend, hash del comando y reintentos compatibles |
| Optimistic concurrency | Versionado de stock cuando aplica |
| Pessimistic locking | Serializar filas/entidades críticas |
| Append-only ledger | Stock movements y entradas de valoración; triggers |
| Compensating transaction | Cancelaciones/reversiones de operaciones contabilizadas |
| Transactional Outbox | Persistencia de evento en la transacción de negocio |
| Lease + SKIP LOCKED | Reclamar trabajos pendientes de Outbox |
| Retry/backoff/redrive | Reintento operativo de eventos fallidos |
| Local cache-aside | Caffeine con invalidación selectiva |
| Query / Report Service | Consultas financieras y operativas read-only |
| Anti-corruption boundary | DTO y validación del canal WhatsApp frente a input externo |
| Problem Details | Errores HTTP normalizados y requestId |

### No presentes o parciales

- Microservicios independientes: no.
- CQRS completo/event sourcing: no; hay ledger de stock y outbox, no un event store general.
- Arquitectura hexagonal en todos los dominios: no.
- Consumidores/proyecciones Kafka: no.
- Rate limit distribuido: no; el filtro actual es por instancia.
- Stock por variante y picking multi-bin: no.
- Multiempresa/tenant: no.
- Pasarela de pago y facturación fiscal: no.

## 7. Integridad transaccional de inventario

La compra crea un documento; el stock aumenta cuando se contabiliza la recepción. Las ventas, ajustes, conteos, transferencias y devoluciones crean movimientos mediante servicios de negocio. No cambiar onHand manualmente ni corregir el historial mediante UPDATE/DELETE.

FIFO consume capas ordenadas; el costo promedio utiliza estado de valoración correspondiente. El método no debe cambiarse tras movimientos valorizados sin migración planificada. Para existencias anteriores a V14, la capa inicial derivada de product.costPrice es estimación, no reconstrucción de costo contable histórico.

La verificación de disponibilidad en UI es informativa: el comando debe volver a validar disponibilidad, bloquear/serializar saldo, y confirmar documento, saldo, movimiento y valoración en una transacción. La misma Idempotency-Key con mismo payload debe evitar una segunda contabilización.

## 8. Riesgos operativos de arquitectura

- El pool PostgreSQL multiplica conexiones por cada instancia.
- Caffeine cache y rate limiting son locales y no coordinan réplicas.
- Outbox entrega potencialmente al menos una vez; cualquier futuro consumidor debe deduplicar por eventId.
- FORWARD_HEADERS_STRATEGY por defecto es none; native solo detrás de proxy confiable que reescriba headers.
- JWT se almacena en localStorage del navegador; reforzar CSP/XSS.
- El informe histórico ejecutó pruebas locales en PostgreSQL 18.6 aunque Compose/CI definen PostgreSQL 16. Debe certificarse la versión objetivo.
- La carpeta de backend puede tener un nombre legado que confunda a equipos nuevos; no cambiar nombres en bloque sin evaluar paths, scripts y despliegues.

## 9. Evolución sugerida

1. Sincronizar controlador, DTO, referencia API, permiso, tests y guía en cada PR.
2. Habilitar CI automática para frontend y backend, PostgreSQL 16, lockfile npm y reglas de protección de master.
3. Añadir pruebas navegador para compra/recepción → venta total → reposición → reportes.
4. Extraer capas/puertos de manera incremental y orientada a problemas.
5. Tratar multi-bin, stock de variante, pagos, SRI y multiempresa como proyectos con decisión de dominio y migraciones independientes.
