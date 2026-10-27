> **Marcador de vigencia — 2026-10-26:** registro histórico de hito/entrega, no inventario vivo de rutas ni garantía de validación del HEAD actual. Consultar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia de APIs](25-referencia-apis.md), [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar comportamiento y permisos contra el código y pruebas del SHA que se despliega.

# P09 — Devoluciones trazables y anulación aprobada

## Alcance

P09 endurece las devoluciones de venta/proveedor y sustituye la anulación inmediata por un flujo de solicitud y aprobación con segregación de funciones. No incluye notas de crédito fiscales, pagos, cuentas por pagar ni contabilización financiera.

## Devoluciones de venta

- `POST /api/v1/sales-returns` requiere `sales:manage` y `Idempotency-Key`; `GET /api/v1/sales-returns/{id}` requiere `sales:read`.
- Solo admite ventas completadas, líneas pertenecientes a la venta y cantidades positivas con precisión máxima de tres decimales.
- La cantidad devuelta acumulada de cada línea no puede exceder la cantidad vendida. El bloqueo de la venta serializa devoluciones concurrentes para el mismo documento.
- RESTOCK incrementa el saldo; SCRAP deja el saldo vendible intacto mediante movimientos compensados de entrada/salida.
- Los movimientos incluyen referencia documental, línea de devolución, ubicación y unidad de medida.

## Devoluciones a proveedor

- `POST /api/v1/purchase-returns` requiere `purchasing:manage` y `Idempotency-Key`; `GET /api/v1/purchase-returns/{id}` requiere `purchasing:read`.
- Solo admite líneas de una recepción contabilizada y cantidades acumuladas que no superen lo recibido.
- La orden de compra se bloquea para serializar devoluciones relacionadas y coordinarse con recepciones.
- Se descuenta stock bajo bloqueo y cada movimiento referencia la recepción/devolución, línea fuente, ubicación y unidad.

## Idempotencia y transacción

Ambos flujos reservan la clave con `idempotency_record` mediante `INSERT ... ON CONFLICT DO NOTHING`. El hash canónico no depende del orden de las líneas. Una repetición equivalente devuelve el recurso original; una clave con contenido diferente produce HTTP 409. Documento, líneas, saldo, ledger, auditoría, Outbox e idempotencia se confirman en una sola transacción. Se mantiene compatibilidad de lectura para claves creadas antes de P09.

## Solicitud y aprobación de anulación

1. `POST /api/v1/sales/{id}/request-cancellation` requiere `sales:manage` y un motivo. Cambia la venta a `CANCELLATION_REQUESTED` sin modificar stock.
2. `POST /api/v1/sales/{id}/approve-cancellation` requiere `sales:cancel` y una clave idempotente.
3. El solicitante no puede aprobar su propia solicitud. La aprobación bloquea la venta y los saldos en orden estable, repone el stock una sola vez y registra movimientos, auditoría y Outbox en la misma transacción.
4. No se puede solicitar la anulación de una venta que ya tenga devoluciones, ni aprobar una venta sin solicitud pendiente.

La migración incremental `V12__approved_sale_cancellations.sql` conserva ventas existentes y agrega metadatos de solicitud/aprobación y restricciones de consistencia. Las cancelaciones históricas se rellenan con metadatos de compatibilidad antes de imponer la nueva restricción; no se modifican migraciones históricas.

## Validación aplazada

No se ejecutaron pruebas, compilación/build, CI, arranque, migraciones ni comprobaciones de entorno. Estas tareas se harán únicamente después de cerrar toda la implementación fuente. Esta entrega no afirma que compile ni que funcione en runtime.