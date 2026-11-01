# Fase 5 — Primer corte de datos tributarios y relación fiscal de almacenes

**Rama:** `implementacion_propuestas`  
**Alcance:** backend inicial, importación manual trazable. No equivale a integración automática con el SRI.

## Implementado

- Migración Flyway V21: `taxpayer_profile`, `sri_establishment`, `taxpayer_snapshot` y `warehouse_establishment_assignment`.
- Perfil tributario con RUC en texto y restricción de 13 dígitos; el establecimiento usa código textual de tres dígitos, conserva ceros iniciales y permite `trade_name` nulo.
- Estados recibidos se conservan literalmente. Los estados desconocidos se normalizan a `UNKNOWN`, nunca a activo.
- La importación se identifica como `MANUAL_IMPORT` y deja `freshness_status=MANUAL_REVIEW`; conserva hash SHA-256, fuente declarada, actor, fecha y snapshot de los campos normalizados.
- La importación no elimina establecimientos omitidos por una carga parcial y no modifica el estado operativo de la bodega.
- Asociación N:M con tipo, prioridad, responsable y vigencia. El cierre es temporal y conserva historia.
- El acceso a la bodega se comprueba con `WarehouseService`. Hasta modelar el propietario legal del stock, se bloquea asociar una misma bodega a RUC diferentes.
- Endpoints:
  - `GET /api/v1/taxpayers?q=&page=&size=`
  - `POST /api/v1/taxpayers/import`
  - `GET /api/v1/taxpayers/{id}`
  - `GET /api/v1/taxpayers/{id}/establishments`
  - `GET /api/v1/taxpayers/{id}/snapshots?page=&size=`
  - `GET|POST /api/v1/warehouses/{warehouseId}/establishment-assignments`
  - `POST /api/v1/establishment-assignments/{assignmentId}/close`
- Todos los endpoints están restringidos temporalmente a `inventory:manage` hasta definir permisos y scopes tributarios propios.

## Contrato de importación

El request debe incluir RUC, razón social, estado recibido, referencia de fuente y al menos un establecimiento. Cada establecimiento requiere código de tres dígitos y estado recibido. El backend conserva valores originales relevantes y calcula la huella del snapshot.

No se incluye cliente HTTP de terceros. La referencia de fuente es metadato de procedencia declarado por el operador; **no prueba por sí misma que la fuente sea oficial**. El estado permanece en revisión manual y no debe utilizarse para autorizar facturación.

## Límites y bloqueos deliberados

- No se conoce ni se ha autorizado un proveedor/API estable del SRI; no se implementó scraping ni se asumió un JSON público.
- No se modela todavía propietario legal del inventario. Por seguridad, las bodegas no pueden vincularse activamente a perfiles de RUC distintos.
- No se implementó aún la interfaz SRI ni la visualización de asociaciones en la ficha de almacén.
- Siguen pendientes roles CUSTOMER/SUPPLIER modificables sobre un único socio, tipo de identificación, formularios contextuales en Compras/Ventas y búsqueda remota en esos formularios.
- La configuración de unidades sigue en el catálogo existente. No se cambió la interpretación de documentos históricos ni se habilitaron unidades alternativas por línea sin un contrato de cantidad base, precio, redondeo y factor snapshot.
- Los permisos tributarios usan temporalmente `inventory:manage`; antes de producción deben existir permisos y scopes específicos y pruebas de aislamiento.

## Validación requerida

El workflow backend CI se debe asociar al SHA final. Debe comprobar Maven/Java 21, Flyway V1–V21 sobre PostgreSQL, Hibernate validation, pruebas HTTP/security y smoke de arranque desde volumen vacío. La prueba actual no sustituye la aceptación de los flujos UI y la integración externa.
