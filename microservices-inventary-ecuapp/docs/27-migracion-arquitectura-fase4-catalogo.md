# Fase 4 — Primera migración vertical del catálogo

**Repositorio:** JhonnyMinian92/inventary  
**Rama:** `implementacion_propuestas`  
**Fecha:** 2026-10-31  
**Objetivo:** separar los adaptadores de entrada REST/GraphQL de los servicios de aplicación sin modificar el contrato observable ni los mappings de persistencia.

## Paquetes establecidos

```text
catalog/
├── api/
│   ├── rest/controller/
│   │   ├── ProductController.java
│   │   ├── CategoryController.java
│   │   ├── PublicCatalogController.java
│   │   └── CatalogStructureController.java
│   └── graphql/controller/
│       └── GraphqlCatalogController.java
├── application/
│   ├── service/
│   │   ├── ProductService.java
│   │   ├── CategoryService.java
│   │   ├── CatalogStructureService.java
│   │   ├── ProductVariantService.java
│   │   └── CategorySuggestionService.java
│   ├── port/out/ProductPersistencePort.java
│   └── ... contratos de caso de uso ya existentes
├── infrastructure/
│   └── adapter/out/persistence/JpaProductPersistenceAdapter.java
└── (paquete raíz transicional: entidades, repositorios, enums y DTOs existentes)
```

## Cambios de dependencia

- Los controladores REST delegan en servicios/casos de uso; no agregan acceso a repositorios.
- GraphQL ya no inyecta `CategoryRepository`: obtiene la página de categorías activas mediante `CategoryService`.
- WhatsApp conserva el mismo contrato de catálogo y cambia únicamente el import de `ProductService`.
- Los mappers estáticos `from(...)` de DTOs usados desde los nuevos paquetes se marcaron públicos; no cambia su JSON.

## Compatibilidad preservada

No se modificaron rutas REST, métodos HTTP, nombres de argumentos, DTOs serializados, operaciones/campos del schema GraphQL, tablas, columnas, constraints, índices, triggers, secuencia/checksums Flyway, transacciones de negocio ni payloads Outbox. No se hicieron migraciones SQL.

## Protección automatizada de la frontera API\n\nSe añadió `src/test/java/com/ecuapp/inventary/architecture/CatalogApiBoundaryTest.java` con dos reglas de regresión: los adaptadores REST/GraphQL de catálogo no pueden inyectar repositorios Spring Data, y los métodos HTTP/GraphQL no pueden aceptar ni exponer entidades anotadas con `@Entity`. El test inspecciona también tipos genéricos (por ejemplo, `Page<T>`) y no necesita iniciar el contexto Spring.\n\n## Brechas pendientes para completar Fase 4

1. Mover entidades JPA y repositorios Spring Data a `infrastructure.persistence`, incluyendo actualización de relaciones JPA y referencias cruzadas sin cambiar nombres SQL.
2. Separar los modelos de dominio de entidades JPA. Actualmente `ProductPersistencePort` todavía expone `catalog.Product`, que es JPA.
3. Introducir puertos de aplicación para las dependencias que el catálogo mantiene hacia inventory, purchasing, transfers y media; no mover interfaces sin un caso de uso concreto.
4. Separar DTOs de API de comandos/resultados de aplicación, especialmente `CategoryDtos` y `CatalogStructureDtos`, que todavía participan en servicios.
5. Completar `platform` y `shared` y aplicar el mismo patrón a auth, audit, inventory, media, partners, purchasing, reports, returns, sales, subscriptions, transfers, warehouse y whatsapp.
6. Ampliar las reglas de arquitectura más allá del adaptador API de catálogo: prohibir dependencias Domain→Spring/JPA/HTTP/Kafka y API→infraestructura en todos los contextos, además de pruebas de contrato que comparen rutas y schema.
7. Validar con `./mvnw -B --no-transfer-progress verify`, PostgreSQL 16, smoke Compose con volumen vacío y pruebas HTTP/GraphQL/security.

## Estado

**En progreso.** Esta entrega establece una primera separación verificable de adaptadores de entrada y servicios del piloto catálogo. No se declara Fase 4 completada mientras existan las brechas anteriores ni antes de tener CI verde asociado al SHA final.
