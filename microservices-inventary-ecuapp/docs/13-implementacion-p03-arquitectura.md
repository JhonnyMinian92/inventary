> **Marcador de vigencia — 2026-10-26:** este archivo se conserva como registro histórico de decisión/hito, no como inventario vivo de endpoints ni prueba de que el HEAD actual pasó validación. Para estado vigente, usar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia API actual](25-referencia-apis.md), [26 — Arquitectura/patrones](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar cada ruta y comportamiento en el código del SHA desplegado.

# P03 — Implementación de la arquitectura hexagonal

## Estado y alcance

Esta entrega establece una primera rebanada vertical de arquitectura hexagonal en el módulo de catálogo de productos. No declara migrados automáticamente todos los módulos existentes: las familias de inventario, ventas, compras, autenticación, auditoría y WhatsApp conservan servicios y repositorios históricos y deben migrarse por dominio en tareas posteriores, sin mezclar esta base con cambios funcionales de gran alcance.

## Capas implementadas

- **Adaptador de entrada:** `catalog/ProductController` expone REST y depende de `catalog.application.ProductUseCases`, no de la clase concreta de servicio.
- **Puerto de entrada / casos de uso:** `ProductUseCases` define consultas y comandos; `ProductCommand` y `ProductResult` son contratos de aplicación independientes de HTTP.
- **Servicio de aplicación existente:** `ProductService` implementa el puerto de entrada y mantiene las transacciones, reglas comerciales y registro de auditoría existentes.
- **Puerto de salida:** `ProductPersistencePort` abstrae las operaciones de persistencia utilizadas por el flujo de productos.
- **Adaptador de salida:** `JpaProductPersistenceAdapter` encapsula el acceso a Spring Data JPA.
- **DTOs HTTP y mapeo:** `ProductDtos` valida requests y transforma resultados de aplicación en respuestas REST. Las entidades JPA no se devuelven directamente desde el controlador.
- **Errores:** `ApiProblemFactory` produce respuestas RFC 9457 `ProblemDetail` consistentes, con estado, título, código, ruta y `requestId` de correlación cuando está disponible. El manejador global normaliza validación, conflictos, credenciales, errores de dominio y errores inesperados sin revelar SQL ni stack traces.
- **Dominio:** `DomainErrorCode` y `DomainException` permiten representar fallos de negocio con códigos estables sin acoplar esa excepción a HTTP.

## Límite técnico conocido

El modelo `catalog.Product` todavía contiene anotaciones JPA y el puerto de persistencia lo utiliza como tipo. Esto mantiene el comportamiento actual y reduce el riesgo de una migración masiva, pero no equivale a dominio completamente puro. La siguiente etapa de refactor debe introducir un agregado de dominio separado y mappers JPA↔dominio, y después aplicar el patrón a categorías, almacenes, inventario, compras y ventas.

## Convenciones para los siguientes módulos

1. Controladores delgados; no incluir reglas de negocio ni acceso directo a repositorios.
2. Los casos de uso aceptan comandos de aplicación, no objetos HTTP ni entidades JPA.
3. Los puertos de salida pertenecen a la aplicación; los adaptadores implementan persistencia o integraciones.
4. El dominio no debe importar Spring MVC, Spring Data, Jakarta Persistence ni DTOs REST.
5. Los DTOs validan el formato de entrada; el dominio y los casos de uso vuelven a proteger las invariantes comerciales.
6. Las operaciones de posting deben mantener documento, movimientos, saldo, auditoría/outbox y clave idempotente en una misma transacción cuando compartan PostgreSQL.
7. Los errores API usan `application/problem+json`; nunca exponen credenciales, SQL ni stack traces.
8. Las pruebas, compilación, CI y validación de entorno quedan expresamente reservadas para la fase final, después de completar el código fuente.

## Validación de esta entrega

No se ejecutaron pruebas, compilación Maven, CI ni comprobaciones del entorno. La implementación queda pendiente de validación final por la restricción del plan de trabajo.
