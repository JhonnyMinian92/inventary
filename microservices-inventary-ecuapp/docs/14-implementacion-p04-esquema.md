# P04 — Esquema PostgreSQL y entidades del dominio

## Objetivo

Completar el esquema relacional del MVP con una migración Flyway incremental, manteniendo las migraciones V1–V8 intactas. La migración V9 agrega estructuras necesarias para unidades, variantes, atributos tipados/versionados, ubicaciones, reservas, valoración de inventario e idempotencia.

## Cambios de persistencia

- **Unidades:** `unit_of_measure` y `unit_conversion`, con dimensión y factor decimal positivo. Se crea `EA` como unidad inicial para migrar productos existentes sin perder datos.
- **Catálogo:** se añaden a `product` el tipo (`STOCKED_GOOD`, `NON_STOCKED_GOOD`, `SERVICE`), unidad, atributos JSONB y versión de esquema de atributos. Se crean `product_variant` y `category_attribute_definition` para SKU independiente y plantillas de atributos versionadas.
- **Ubicaciones:** `warehouse_location` permite almacenamiento, tránsito, cuarentena, merma y picking. Se crea una ubicación `MAIN` para cada almacén existente y se asocia a cada saldo.
- **Ledger:** `stock_movement` incorpora origen/destino, unidad, línea de origen, correlación y estrategia de valoración. Se agregan triggers para impedir UPDATE/DELETE de movimientos y asientos de valoración; las correcciones deben ser compensatorias.
- **Reservas:** `stock_reservation` registra estado de ciclo de vida, expiración e idempotencia.
- **Costeo:** `inventory_costing_config` registra una única estrategia de instalación, con `MOVING_AVERAGE` como default inicial; `inventory_cost_layer` soporta capas FIFO y `inventory_valuation_entry` conserva el cálculo histórico. LIFO no se habilita.
- **Idempotencia:** `idempotency_record` guarda ámbito, clave, hash de solicitud, estado y respuesta para hacer posible el control común de reintentos.
- **Integridad:** claves foráneas restrictivas, unicidad, cantidades positivas, monedas normalizadas, estados permitidos e índices de consulta.

## Entidades Java añadidas o ampliadas

- `UnitOfMeasure`, `UnitConversion`, `ProductVariant`, `CategoryAttributeDefinition`.
- `WarehouseLocation`, `StockReservation`.
- `InventoryCostingConfig`, `InventoryCostLayer`, `InventoryValuationEntry`, `IdempotencyRecord`.
- `Product` ahora mapea tipo, unidad y atributos JSONB versionados.
- `StockBalance` y `StockMovement` incluyen referencias de ubicación y metadatos del movimiento.
- Repositorios Spring Data para consultar las nuevas entidades.
- El servicio de almacenes crea una ubicación principal al crear un almacén, y el servicio de inventario usa esa ubicación al crear el primer saldo.
- Los ajustes físicos rechazan productos de tipo servicio o sin control de stock.

## Compatibilidad y límites de la migración

- V1–V8 no se modifican; V9 agrega el nuevo esquema y backfill de los registros existentes.
- Para mantener compatible el contrato actual, el saldo continúa siendo único por producto y almacén durante esta entrega, aunque ya referencia una ubicación. La expansión a varios saldos por producto/ubicación requiere migrar las consultas y operaciones de inventario a IDs de ubicación en una siguiente etapa.
- Los productos existentes reciben el tipo `STOCKED_GOOD` y unidad `EA` como valores de transición. La clasificación real de servicios y bienes sin control de stock debe revisarse desde el catálogo.
- Las tablas de costeo y reserva quedan preparadas en persistencia; todavía requieren conectar todos los flujos de recepción, despacho, reserva y valoración antes de considerarlos funcionalidad operativa completa.
- La estrategia inicial se configura como promedio móvil, pero los cálculos de costo y el uso de capas FIFO deben implementarse/integrarse en las operaciones de posting antes de habilitar reportes de valoración como fuente financiera.

## Validación

No se ejecutaron pruebas, compilación Maven, migraciones contra una base de datos, CI ni comprobaciones de entorno. La revisión de compatibilidad de runtime queda reservada a la fase final acordada.
