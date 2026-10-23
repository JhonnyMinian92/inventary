# Contexto operativo del backend de Inventary

## Misión

Implementar un backend de inventario mantenible y seguro para administrar catálogo mixto de productos/servicios, atributos dinámicos gobernados por esquema, almacenes, movimientos, compras, ventas, costos y auditoría.

## Estado base confirmado

- Aplicación de arranque Spring Boot en Java 21.
- POM con starter básico y dependencia de pruebas; sin dependencia visible de Spring Web, seguridad, JPA ni driver de base de datos.
- application.yaml declara únicamente el nombre de la aplicación.
- No hay controladores REST, modelo de dominio, repositorios, migraciones ni políticas de seguridad implementadas.
- La documentación de las APIs describe el estado objetivo, no rutas existentes.

## Orden de lectura obligatorio

1. [Requisitos y alcance](02-requisitos-y-alcance.md).
2. [Arquitectura y patrones](03-arquitectura-y-patrones.md).
3. [Modelo y reglas de dominio](04-modelo-dominio-y-reglas.md).
4. [Catálogo de APIs](05-catalogo-apis.md).
5. [Flujos de negocio](06-flujos-de-negocio-y-api.md).
6. [Seguridad y definición de hecho](07-seguridad-y-definicion-de-hecho.md).
7. [Plan de trabajo](08-plan-de-trabajo.md).

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