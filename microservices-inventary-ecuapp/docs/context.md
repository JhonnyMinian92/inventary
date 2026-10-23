# Contexto operativo del backend de Inventary

## Misión

Implementar un backend de inventario mantenible y seguro para administrar catálogo mixto de productos/servicios, atributos dinámicos gobernados por esquema, almacenes, movimientos, compras, ventas, costos y auditoría.

## Estado base confirmado

- Aplicación de arranque Spring Boot en Java 21.
- Backend implementado con Spring Boot 3.5, Java 21, Spring MVC, JPA, PostgreSQL, Flyway, JWT HS256/RBAC y controladores REST.
- Hay migraciones incrementales, catálogo, inventario, compras, ventas, devoluciones, transferencias y auditoría.
- Las guías P03–P11 documentan el estado de implementación y distinguen el código fuente de las validaciones aplazadas.
- La documentación de las APIs describe el estado objetivo, no rutas existentes.

## Estado P11 — auditoría, documentación y API

P11 agrega correlación `X-Request-ID`, errores RFC 9457, registro de fallos HTTP sin cuerpos ni credenciales, filtros de auditoría paginados y OpenAPI con seguridad Bearer/clave de integración. Véase [21 — Implementación P11](21-implementacion-p11-auditoria-documentacion-api.md). La validación local queda aplazada hasta completar toda la implementación fuente acordada.

## Orden de lectura obligatorio

1. [Requisitos y alcance](02-requisitos-y-alcance.md).
2. [Arquitectura y patrones](03-arquitectura-y-patrones.md).
3. [Modelo y reglas de dominio](04-modelo-dominio-y-reglas.md).
4. [Catálogo de APIs](05-catalogo-apis.md).
5. [Flujos de negocio](06-flujos-de-negocio-y-api.md).
6. [Seguridad y definición de hecho](07-seguridad-y-definicion-de-hecho.md).
7. [Plan de trabajo](08-plan-de-trabajo.md).
8. [WebFlux, n8n, WhatsApp y OpenAI](09-webflux-n8n-whatsapp-openai.md).
9. [Estimación de tiempo de agente IA y tokens](10-estimacion-agente-ia-tokens.md).

## Principios no negociables

- Los movimientos de stock son append-only y se vinculan a documentos fuente.
- No se permite stock disponible negativo, ni siquiera bajo concurrencia.
- Los comandos de posting son transaccionales e idempotentes.
- Los documentos contabilizados se corrigen con operaciones compensatorias; no se borran físicamente.
- Validación, autorización de función y autorización por objeto se implementan en servidor.
- Costos y márgenes no se exponen a todos los roles.
- Configuración, credenciales y llaves secretas nunca entran al control de versiones.
- Todo endpoint tiene contrato OpenAPI, errores, permisos, auditoría apropiada y checklist de revisión.
- Una revisión estática no garantiza cero bugs; las pruebas locales quedan a cargo del propietario del proyecto.

## Decisiones pendientes de aprobación

Multiempresa/tenant; autenticación OIDC externa frente a proveedor propio; alcance de facturación electrónica SRI; métodos de valoración admitidos; gestión obligatoria de lotes/series/caducidad; operación de caja/pagos y límites de volumen. No cerrar diseño de base de datos hasta resolver decisiones que alteren el modelo.

## Convenciones técnicas previstas

Java 21; Spring Boot compatible con la versión fijada en POM tras comprobar compatibilidad; PostgreSQL; Flyway; Spring Web MVC; Bean Validation; Spring Security Resource Server; JPA/Hibernate; OpenAPI; Actuator/Micrometer. El despliegue inicial recomendado es un monolito modular hexagonal; microservicios y broker se incorporan solo con una razón de negocio/operación.

## Canal comercial prioritario

El primer flujo de venta por mensajería debe contemplar catálogo público con imagen, descripción, precio y disponibilidad vendible; carritos y cotizaciones con expiración; y creación idempotente de pedidos desde n8n tras consentimiento explícito del cliente. La IA no decide stock ni precio y no accede directamente a la base de datos. El callback de WhatsApp necesita HTTPS accesible desde Internet aunque n8n esté autoalojado/local en desarrollo.

La decisión del backend para el MVP es Spring MVC + PostgreSQL/JPA. WebFlux se reevalúa solo si una carga representativa demuestra una necesidad no bloqueante de extremo a extremo.

## Escalabilidad y transacciones

La fuente de verdad comercial es PostgreSQL. Pedido, líneas, cotización vigente, reserva de stock, clave de idempotencia y Outbox deben confirmarse en una transacción local cuando forman parte del mismo caso de uso. Kafka se utiliza para propagar eventos posteriores al commit mediante Transactional Outbox; no es la autoridad de stock.

REST se conserva para comandos e integraciones n8n. GraphQL se añade para consultas compuestas de catálogo/lecturas que se benefician de selección de campos. Redis/Caffeine cachean producto e imágenes con frescura explícita; la confirmación de compra siempre valida precio y stock contra la fuente transaccional.

HikariCP es el pool de conexiones inicial. PgBouncer, réplicas de lectura, particionamiento o distribución PostgreSQL son mecanismos de evolución condicionados por métricas, no un requisito de partida solo por una previsión de usuarios. Base vectorial separada no se requiere para el MVP; pgvector es opción para búsqueda semántica de productos. Véase [11 — Escalabilidad, GraphQL, Kafka, caché y datos](11-escalabilidad-graphql-kafka-cache-datos.md).