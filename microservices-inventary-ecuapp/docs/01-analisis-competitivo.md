# 01 — Análisis competitivo y lecciones de producto

## Objetivo

Contrastar Inventary con productos consolidados para definir capacidades del backend, evitando copiar innecesariamente la complejidad funcional de un ERP completo. La revisión toma como referencia documentación oficial pública consultada el 22 de octubre de 2026. Las páginas pueden cambiar; volver a verificar antes de tomar decisiones contractuales o comerciales.

## Referentes

### Odoo Inventory / Supply Chain

La documentación de Odoo cubre almacenes y ubicaciones, tipos de operación, conteos y ajustes de inventario, reabastecimiento, reglas de reorden, lotes y series, fechas de vencimiento, rutas de recepción/entrega, movimientos y reportes. También documenta flujos operativos de recepción y despacho de varios pasos.

Fuentes:
- [Odoo 19 — Inventario](https://www.odoo.com/documentation/19.0/es_419/applications/inventory.html)
- [Odoo 19 — Supply Chain](https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp.html)
- [Odoo 19 — Reglas de reordenamiento](https://www.odoo.com/documentation/19.0/es_419/applications/inventory_and_mrp/inventory/warehouses_storage/replenishment/reordering_rules.html)

**Lección para Inventary:** modelar movimientos y ubicaciones como conceptos de primera clase; no reducir todo a una columna stock en products. Dejar trazabilidad desde la recepción hasta la salida, y separar existencia física, cantidad reservada y cantidad disponible.

### Zoho Inventory

La descripción oficial reúne gestión de órdenes de compra y venta, recepción, almacenes múltiples, transferencias, ubicaciones de almacenamiento, lotes y números de serie, ajustes, devoluciones, códigos de barras, reabastecimiento y reportes.

Fuente: [Zoho Inventory — funciones oficiales](https://www.zoho.com/us/inventory/)

**Lección para Inventary:** diseñar un contrato API coherente entre catálogo, compras, movimientos y ventas. Reportes como antigüedad del inventario, valoración y saldos por almacén se deben derivar de datos trazables, no de cálculos distintos por pantalla.

### SAP Business One

La documentación de SAP Business One describe documentos de entrada y salida de inventario, transferencias, movimientos ligados a ventas y compras y gestión de lotes; además permite consultar transacciones por lote.

Fuente: [SAP Help — Managing Batch Numbers](https://help.sap.com/docs/SAP_BUSINESS_ONE/68a2e87fb29941b5bf959a184d9c6727/1ae2446f5f434a3288a48edcf9079763.html?locale=en-US)

**Lección para Inventary:** cada movimiento debe poder rastrearse a un documento fuente y, cuando aplique, a lote o serie. Los documentos ya contabilizados no se “editan silenciosamente”: se corrigen con devolución, reversión o ajuste compensatorio autorizado.

## Matriz comparativa orientativa

| Capacidad | Referencia documentada | Decisión propuesta para Inventary |
|---|---|---|
| Catálogo de bienes y servicios | Zoho y Odoo | Incluir en MVP. Los servicios no generan movimientos de stock. |
| Variantes y atributos por categoría | Odoo documenta productos/unidades y configuración; Inventary requiere atributos dinámicos | Incluir catálogo de atributos tipados, valores validados y variantes/SKU cuando corresponda. |
| Múltiples almacenes/ubicaciones | Odoo y Zoho | Incluir almacenes y ubicaciones, con saldo independiente por ubicación. |
| Kardex y trazabilidad | SAP y Odoo | Incluir ledger de movimientos inmutable, vínculo al documento origen y auditoría. |
| Lotes, series y caducidad | Odoo, SAP, Zoho | Implementar modelo, pero activar por producto según política. La selección FEFO es necesaria cuando exista caducidad. |
| Órdenes de compra y recepción | Odoo y Zoho | Incluir pedido, recepción parcial y contabilización de cantidades efectivamente recibidas. |
| Ventas y devoluciones | Zoho, Odoo, SAP | Incluir venta, reserva/salida y devolución compensatoria con trazabilidad. |
| Alertas y reabastecimiento | Odoo y Zoho | MVP: mínimos/máximos y consulta de artículos bajo mínimo; generación automática después de validar proveedores y demanda. |
| Valoración FIFO/Promedio | El README de Inventary lo requiere | Diseñar capa Strategy; validar política contable y registros de costo antes de habilitar métodos. |
| LIFO | Requisito escrito en el README actual | No habilitar en producción por defecto. Requiere aprobación contable/legal, reglas de reporte y pruebas de valoración propias. |
| Integración de facturación fiscal | Depende del país y alcance | Fuera de MVP por defecto. Definir SRI Ecuador, ambiente, firma, autorizaciones y contingencias si se incluye. |
| RBAC y auditoría | Requisito explícito de Inventary | Incluir permisos de servidor por acción y recurso; no basta ocultar opciones en frontend. |

Esta matriz es un análisis de diseño, no una afirmación de que todos los productos tengan exactamente el mismo comportamiento o se comparen uno a uno. Consultar los productos oficiales para validar planes, licencias, precios y funciones vigentes.

## Requisitos diferenciales que conviene adoptar

1. **Ledger inmutable de inventario:** toda entrada, salida, transferencia, reserva, liberación, devolución y ajuste tiene actor, instante, origen, razón y cantidades.
2. **Documento + posting explícito:** guardar un borrador no cambia el stock; contabilizarlo dispara el movimiento una sola vez.
3. **Consistencia bajo concurrencia:** dos solicitudes simultáneas no pueden vender la misma unidad. La base de datos y la transacción deben impedir cantidades disponibles negativas.
4. **Atributos dinámicos gobernados por esquema:** JSON flexible no significa aceptar cualquier JSON. Las plantillas deben definir tipo, obligatoriedad, límites y reglas de validación.
5. **Control de acceso de mínimo privilegio:** permisos de lectura y escritura independientes y protección adicional para costos, márgenes y cancelaciones.
6. **Reversión, no edición histórica:** los documentos contabilizados solo se rectifican mediante operaciones compensatorias vinculadas.
7. **Observabilidad operacional:** correlation ID, logs estructurados sin secretos, métricas de errores y alertas sobre operaciones fallidas.

## Decisiones de alcance recomendadas

**MVP backend:** identidad/autorización, usuarios y roles, catálogo, categorías/atributos, almacenes, stock y kardex, ajustes auditados, transferencias, compras/recepciones, ventas/devoluciones, consultas/reportes operativos y bitácora de auditoría.

**Posterior o condicionado:** multicanal, fabricación/BOM, códigos de barras/RFID, integración contable y fiscal SRI, pagos, envío, pronóstico de demanda, multi-moneda, portal de proveedores y eventos de integración externos.

Los módulos posteriores pueden modificar significativamente el modelo de datos, el alcance de seguridad y el cronograma; no deben considerarse incluidos solo por aparecer como capacidades de un competidor.