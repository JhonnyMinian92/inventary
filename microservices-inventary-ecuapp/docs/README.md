# Inventary — documentación técnica del backend

## Propósito

Esta carpeta es la especificación de referencia para diseñar e implementar el backend de Inventary. Describe alcance, requisitos, arquitectura, dominio, contrato REST propuesto, flujos, seguridad, tareas y estimación. No declara implementado ningún endpoint que todavía no exista en el código.

## Estado comprobado del repositorio

El backend ya contiene una aplicación Spring Boot con endpoints REST, servicios, entidades JPA, repositorios, migraciones Flyway y configuración de seguridad. La arquitectura se está migrando gradualmente hacia un monolito modular con límites hexagonales; no todos los módulos históricos están migrados todavía.

La entrega P03 establece una primera rebanada vertical en catálogo de productos: puerto de entrada de casos de uso, comandos/resultados de aplicación, puerto de persistencia, adaptador JPA, DTOs HTTP y errores RFC 9457. El alcance exacto y el límite pendiente del modelo JPA se describen en [13 — Implementación P03: arquitectura hexagonal](13-implementacion-p03-arquitectura.md). Las pruebas y la compilación no se ejecutaron y quedan para la fase final.

## Documentos

| Documento | Contenido |
|---|---|
| [01 — Análisis competitivo](01-analisis-competitivo.md) | Referentes, capacidades comparables y decisiones que sí conviene adoptar. |
| [02 — Alcance y requisitos](02-requisitos-y-alcance.md) | Alcance, supuestos, requisitos funcionales y no funcionales, criterios de aceptación. |
| [03 — Arquitectura y patrones](03-arquitectura-y-patrones.md) | Arquitectura objetivo, módulos, límites y patrones de diseño. |
| [04 — Modelo de dominio](04-modelo-dominio-y-reglas.md) | Entidades, relaciones, stock, estados y reglas transaccionales. |
| [05 — Catálogo de APIs](05-catalogo-apis.md) | Endpoints REST propuestos, permisos, solicitudes, respuestas y errores. |
| [06 — Flujos de negocio y APIs](06-flujos-de-negocio-y-api.md) | Diagramas Mermaid y flujo por familia de endpoints/comandos. |
| [07 — Seguridad y definición de hecho](07-seguridad-y-definicion-de-hecho.md) | Controles, amenazas, revisión estática y checklist para cada API. |
| [08 — Plan de trabajo](08-plan-de-trabajo.md) | Backlog, dependencias, hitos y estimación del 100 % del backend descrito. |
| [09 — WebFlux, n8n, WhatsApp y OpenAI](09-webflux-n8n-whatsapp-openai.md) | Decisión MVC/WebFlux, arquitectura del canal comercial, catálogo público y compra conversacional. |
| [10 — Estimación del agente IA y tokens](10-estimacion-agente-ia-tokens.md) | Tiempo por tarea, tokens orientativos, presupuesto total y factores que modifican la estimación. |
| [11 — Escalabilidad, GraphQL, Kafka, caché y datos](11-escalabilidad-graphql-kafka-cache-datos.md) | Decisiones sobre transacciones, eventos, caché, DataSource/pools, réplicas, particionamiento y vector search. |
| [12 — Decisiones P01](12-decisiones-p01.md) | Defaults de MVP, alcance, roles, permisos y reglas de negocio para desbloquear la implementación. |
| [13 — Implementación P03: arquitectura hexagonal](13-implementacion-p03-arquitectura.md) | Estado real de capas, puertos, adaptadores, errores y límites de la migración. |
| [14 — Implementación P04: esquema PostgreSQL y entidades](14-implementacion-p04-esquema.md) | Migración incremental V9, unidades, variantes, ubicaciones, reservas, costeo e idempotencia. |
| [15 — Implementación P05: seguridad e identidad](15-implementacion-p05-seguridad.md) | JWT, roles/permisos RBAC, invalidación de tokens, bootstrap inicial seguro y CORS. |
| [16 — Implementación P06: ventas atómicas e idempotencia](16-implementacion-p06-ventas-atomicas.md) | Precios del servidor, descuento de stock transaccional, movimientos por línea y deduplicación concurrente de solicitudes. |
| [17 — Implementación P07: inventario operativo](17-implementacion-p07-inventario-operativo.md) | Conteos físicos, diferencias, ajustes transaccionales, idempotencia, trazabilidad y ledger append-only. |

## Convenciones

- Prefijo REST: /api/v1.
- Fechas y horas en ISO 8601 con zona explícita; persistencia temporal en UTC.
- Cantidades y costos: BigDecimal/NUMERIC, nunca float/double para importes.
- Errores de dominio con HTTP coherente y cuerpo application/problem+json.
- Los comandos que cambian inventario deben ser idempotentes, autorizados y auditables.
- Cada operación debe estar acotada por empresa/organización si se confirma el modo multiempresa; nunca confiar un companyId enviado por el cliente sin verificar el contexto autorizado.
- Documentación OpenAPI como contrato a mantener sincronizado con los DTOs.
- Los diagramas representan comportamiento objetivo. Las pruebas funcionales y de integración las ejecutará el responsable localmente; no se ejecutaron pruebas durante la elaboración de estos documentos.

## Decisiones que bloquean una implementación definitiva

Antes de cerrar el modelo y estimación, confirmar:

1. ¿Una sola empresa por instalación o SaaS multiempresa?
2. ¿Se emitirán comprobantes electrónicos SRI en el MVP o solo documentos internos?
3. ¿Qué política de costos estará habilitada y con qué criterio contable? LIFO queda sujeto a aprobación contable y jurisdiccional antes de activarlo.
4. ¿Habrá caja/pagos, contabilidad, e-commerce, fabricación o solo inventario, compras y ventas?
5. ¿Quién emitirá los tokens: proveedor OIDC externo o servicio de identidad propio?
6. Volumen esperado: usuarios concurrentes, productos/SKUs, almacenes y movimientos diarios.

La estimación de 08 — Plan de trabajo identifica estos supuestos y no considera que “cero errores” pueda garantizarse solo mediante revisión estática.

## Canal comercial y medios

El contrato de catálogo público y las rutas de pedidos WhatsApp están documentados en [09 — WebFlux, n8n, WhatsApp y OpenAI](09-webflux-n8n-whatsapp-openai.md); los endpoints de administración y exposición de medios se amplían en [05 — Catálogo de APIs](05-catalogo-apis.md), sección 11.
