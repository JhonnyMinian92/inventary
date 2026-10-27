> **Adenda de estado — 2026-10-26:** el código actual incluye costeo FIFO/promedio y migración V14; las capas iniciales para existencias anteriores usan product.costPrice como estimación, no reconstrucción de costo histórico. Las variantes no tienen inventario/costo separado y no hay picking multi-bin operativo. Las existencias cambian mediante comandos que registran movimientos, no mediante un CRUD de stock. Ver [24](24-estado-actual-repositorio.md), [25](25-referencia-apis.md) y [26](26-arquitectura-y-patrones-actuales.md).

# 04 — Modelo de dominio, persistencia y reglas inquebrantables

## 1. Entidades principales

Los nombres son propuestos; el modelo relacional final se confirma con el negocio y se implementa mediante migraciones.

| Entidad | Campos clave | Observaciones |
|---|---|---|
| Organization / Company | id, name, status | Obligatoria si se confirma multiempresa; cada consulta verifica su ámbito. |
| User | id, subject/username, status, display data | Las credenciales preferiblemente pertenecen al IdP. |
| Role / Permission | role, permission, assignments | Permisos granulares y versionados; mínimo privilegio. |
| Category | id, code, name, status | Define políticas de atributos y defaults permitidos. |
| AttributeDefinition | key, type, required, constraints, options | Esquema para validar JSONB; tipos versionables. |
| Product | id, sku, name, kind, categoryId, unitId, status, attributes | SKU único por ámbito. kind diferencia bienes con stock, bienes sin control de stock y servicios, según decisión comercial. |
| ProductVariant | id, productId, sku, optionValues | Una variante que se vende/stockea como SKU independiente. |
| UnitOfMeasure | id, code, precision, dimension | Conversiones explícitas y sin mezcla de dimensiones incompatibles. |
| Warehouse / Location | ids, code, parent, type, status | Ubicación virtual para tránsito, cuarentena o merma cuando corresponda. |
| Supplier / Customer | id, code, status, contact data | Datos personales mínimos y permisos restringidos. |
| PurchaseOrder / PurchaseLine | id, status, supplier, lines, totals | Un pedido puede recibirse parcialmente. |
| GoodsReceipt / ReceiptLine | id, sourceOrder, quantities, status | Contabilizar solo cantidad recibida y aceptada. |
| SalesOrder / SalesLine | id, status, customer, prices | Snapshot de precio/descr. en el documento para preservar historial. |
| SalesReturn / ReturnLine | originalDocument, approved qty, reason | No puede exceder cantidad retornable. |
| StockBalance | productVariant, location, onHand, reserved, version | Proyección/índice de consulta; no es el historial. |
| StockMovement | id, product, from/to location, qty, type, source, actor, occurredAt | Ledger inmutable; toda contabilización genera uno o varios movimientos. |
| StockReservation | id, orderLine, location, qty, status, expiry | Distingue reserva de salida física; política de expiración definida. |
| CostLayer / ValuationEntry | sourceMovement, qty remaining, unitCost, method | Necesario para FIFO; promedio requiere valor acumulado y cantidades consistentes. |
| IdempotencyRecord | scope, key, requestHash, status, response | Protege posting contra reintentos de red. |
| AuditEvent | actor, action, resource, outcome, time, correlationId, metadata | Append-only por política; no registrar secretos ni contenido innecesario. |
| OutboxEvent | id, type, aggregateId, payload, status, attempts | Eventos que solo se publican después del commit. |

## 2. Relaciones relevantes

~~~mermaid
erDiagram
  CATEGORY ||--o{ PRODUCT : groups
  PRODUCT ||--o{ PRODUCT_VARIANT : has
  WAREHOUSE ||--o{ LOCATION : contains
  PRODUCT_VARIANT ||--o{ STOCK_BALANCE : held_as
  LOCATION ||--o{ STOCK_BALANCE : stores
  PRODUCT_VARIANT ||--o{ STOCK_MOVEMENT : moves
  PURCHASE_ORDER ||--o{ PURCHASE_LINE : contains
  PURCHASE_ORDER ||--o{ GOODS_RECEIPT : fulfilled_by
  SALES_ORDER ||--o{ SALES_LINE : contains
  SALES_ORDER ||--o{ STOCK_RESERVATION : reserves
  SALES_ORDER ||--o{ SALES_RETURN : reversed_by
  STOCK_MOVEMENT }o--|| AUDIT_EVENT : traced_by
~~~

Los diagramas son conceptuales; un movimiento normalmente se vincula con el documento de origen y el evento de auditoría por claves explícitas o identificadores de correlación, no necesariamente con una relación directa uno a uno.

## 3. Libro de movimientos y saldo

**Fuente histórica:** StockMovement inmutable.  
**Vista de operación:** StockBalance actualizada transaccionalmente y reconstruible/reconciliable desde el ledger.

Cada movimiento debe registrar como mínimo:

- tipo (RECEIPT, ISSUE, TRANSFER_OUT, TRANSFER_IN, RESERVE, RELEASE, RETURN_IN, RETURN_OUT, ADJUSTMENT, SCRAP u otros autorizados);
- SKU/variante, ubicación origen/destino cuando aplique, cantidad, unidad;
- documento y línea de origen; motivo/nota para ajustes;
- actor del sistema y usuario, instante UTC, correlation ID y clave idempotente;
- precio/costo de valoración cuando aplique, moneda y política de cálculo;
- estado contabilizado. Los borradores se guardan aparte y no generan movimientos definitivos.

Un traslado entre ubicaciones crea salidas/entradas vinculadas dentro de la misma transacción; transferencia a otra bodega no debe sumar stock dos veces. Para tránsito prolongado, modelar una ubicación virtual EN_TRÁNSITO con estado explícito.

## 4. Invariantes críticos

1. Cantidad de movimientos positivos; el tipo expresa dirección. Si el caso exige delta firmado, normalizar y definir una única convención.
2. Servicio sin control de stock no puede reservar ni emitir movimiento físico.
3. onHand >= 0 y reserved >= 0; además reserved <= onHand si reserva se representa dentro de existencia física.
4. available = onHand - reserved no puede resultar negativo.
5. El SKU/código de barras tiene unicidad dentro del ámbito aprobado.
6. No contabilizar dos veces un mismo documento o línea de recepción/despacho.
7. Una devolución requiere documento origen y cantidad retornable disponible; una nueva petición duplicada debe ser idempotente.
8. Documento posteado no se modifica en precio, cantidades, actor u hora original; una corrección crea nueva transacción compensatoria.
9. Cambio de categoría/atributos no puede invalidar silenciosamente instancias ya registradas; se debe versionar plantilla o validar migración.
10. Conversión de unidad solo se permite dentro de dimensión compatible y con regla decimal definida.
11. Productos inactivos no aparecen en nuevos documentos, pero permanecen visibles al consultar historial.
12. El usuario no puede seleccionar un warehouse/organization fuera de su permiso aunque conozca el ID.

## 5. Concurrencia y bloqueo

Para reservar o despachar stock:

1. Validar actor, documento, estado, almacén y cantidades.
2. Tomar una clave idempotente o identificar la operación ya procesada.
3. Dentro de transacción, reservar con bloqueo o actualización condicional, por ejemplo conceptualmente: UPDATE con condición on_hand - reserved >= quantity.
4. Comprobar número de filas afectadas; si es cero, responder conflicto de inventario y no continuar.
5. Crear reservas/movimientos pertinentes, actualizar documento y outbox/auditoría.
6. Commit; solo entonces responder como operación completada.

Una anotación @Version sola no resuelve todo: se requiere un cambio atómico que impida que el balance cruce cero. El mecanismo final se elegirá según SQL y esquema implementado. No se debe calcular disponibilidad en memoria y confiar en ese resultado para una venta concurrente.

## 6. Políticas de valoración

Implementar una interfaz CostingStrategy para poder seleccionar una política versionada:

- FIFO: las unidades salientes consumen capas de costo en orden cronológico definido.
- Promedio ponderado: documentar si se usa promedio periódico o móvil; no llamarlos igual ni mezclar su fórmula.
- LIFO: requisito indicado en README, pero **deshabilitado por defecto** hasta revisión contable/legal y especificación explícita de reportes. Debe ser una estrategia separada, no una etiqueta que todavía no tenga implementación real.

Cada entrada debe preservar el costo unitario de origen y moneda; definir tratamiento de gastos de importación, descuentos, impuestos, devoluciones y redondeo antes de habilitar valoración financiera. La valoración de inventario y el precio comercial son conceptos separados.

## 7. Atributos dinámicos

El JSON de atributos de un producto/variante necesita un AttributeDefinition que indique tipo, requerido, formato, límites, opciones y versión. Validar servidor antes de persistir.

Recomendaciones:
- Usar JSONB para atributos heterogéneos de catálogo; indexar solo consultas justificadas.
- Mantener SKU, precio, estado, unidad y datos críticos fuera de atributos arbitrarios.
- Guardar el schemaVersion aplicable, o conservar la versión de plantilla usada.
- Proteger tamaño y profundidad máxima del JSON.
- No construir SQL usando una clave de atributo sin allowlist.
- Fecha de caducidad usada por FEFO requiere formato/semántica formal y, si afecta stock por lote, se modela en la entidad de lote.

## 8. Estados propuestos

- Maestro: ACTIVE, INACTIVE.
- Documento comercial: DRAFT -> CONFIRMED -> PARTIALLY_FULFILLED -> FULFILLED; CANCELLED solo mientras las reglas de posting y aprobación lo permitan.
- Posting: operación explícita/idempotente, registra actor y resultado, no repite efectos.
- Devolución: DRAFT -> APPROVED si aplica -> POSTED.
- Transferencia: DRAFT -> APPROVED opcional -> IN_TRANSIT opcional -> RECEIVED o CANCELLED si todavía es seguro.

Los nombres definitivos deben normalizarse en enums y OpenAPI. Las transiciones no se aceptan por PATCH genérico de estado.

## 9. Retención y eliminación

- No borrar físicamente movimientos, documentos contabilizados ni eventos de auditoría por endpoints de negocio.
- Aplicar inactivación lógica en maestros cuando haya dependencias.
- Solo procesos de privacidad/retención aprobados pueden purgar PII elegible; preservar integridad legal/contable y registrar el proceso.
- No dar al rol técnico una vía de edición de datos comerciales o auditoría por el hecho de administrar infraestructura.