> **Marcador de vigencia — 2026-10-26:** registro histórico de hito/entrega, no inventario vivo de rutas ni garantía de validación del HEAD actual. Consultar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia de APIs](25-referencia-apis.md), [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar comportamiento y permisos contra el código y pruebas del SHA que se despliega.

# P08 — Proveedores, órdenes de compra y recepciones parciales

## Alcance

P08 completa el flujo de abastecimiento interno: mantenimiento de proveedores, creación de órdenes, envío, aprobación, cancelación previa a aprobación y recepción parcial o total. No incluye facturación de proveedores, pagos ni contabilización contable.

## Proveedores

- `GET /api/v1/suppliers` y `GET /api/v1/suppliers/{id}` requieren `purchasing:read`.
- `POST /api/v1/suppliers`, `PUT /api/v1/suppliers/{id}` y `POST /api/v1/suppliers/{id}/deactivate` requieren `partners:manage`.
- El código del contacto permanece inmutable y los proveedores inactivos no pueden usarse en nuevas órdenes.
- Se conserva el modelo compartido `BusinessPartner` con tipos SUPPLIER, CUSTOMER y BOTH.

## Crear una orden de compra

```http
POST /api/v1/purchase-orders
Authorization: Bearer <JWT>
Idempotency-Key: oc-proveedor-2026-001
Content-Type: application/json

{
  "supplierId": "UUID_DEL_PROVEEDOR",
  "warehouseId": "UUID_DEL_ALMACEN",
  "notes": "Reposición mensual",
  "items": [
    {"productId": "UUID_DEL_PRODUCTO", "quantity": 20.000, "unitCost": 4.25}
  ]
}
```

El servidor calcula subtotal y total; valida proveedor y almacén activos, productos activos con control de stock, moneda homogénea, líneas sin duplicados y precisión de cantidades/importes. Guarda snapshots de SKU/nombre y cantidades ordenadas, recibidas y pendientes. La clave de idempotencia se reserva de forma atómica en PostgreSQL con el hash canónico de la solicitud: un reintento equivalente recupera la misma orden y reutilizar la clave con contenido distinto produce conflicto.

## Flujo de estados

1. `DRAFT`: orden creada; se puede enviar o cancelar.
2. `SUBMITTED`: enviada para aprobación; puede aprobarse o cancelarse.
3. `APPROVED`: se permiten recepciones.
4. `PARTIALLY_RECEIVED`: una parte de la cantidad pedida ya ingresó; se permiten nuevas recepciones.
5. `RECEIVED`: todas las líneas quedaron recibidas; no admite más recepciones.
6. `CANCELLED`: estado terminal.

Las transiciones de envío, aprobación y cancelación bloquean la fila de la orden y responden conflicto de dominio cuando el estado no lo permite. La aprobación requiere `purchasing:approve`; la gestión de órdenes y recepciones requiere `purchasing:manage`.

## Registrar una recepción parcial

```http
POST /api/v1/purchase-orders/{orderId}/receipts
Authorization: Bearer <JWT>
Idempotency-Key: recepcion-proveedor-2026-001
Content-Type: application/json

{
  "items": [
    {"purchaseOrderLineId": "UUID_DE_LINEA", "quantity": 7.000}
  ]
}
```

La recepción solo acepta líneas pertenecientes a la orden y cantidades positivas que no superen lo pendiente. La orden se bloquea y los saldos se bloquean por producto; saldo, cantidades recibidas, documento de recepción, movimientos, auditoría, Outbox e idempotencia se guardan en la misma transacción. Repetir la misma clave y contenido no vuelve a incrementar stock; cambiar el contenido produce conflicto.

Cada movimiento de entrada incluye la ubicación destino, unidad de medida, identificador de la línea de recepción y número de recepción para rastrear el ingreso desde el ledger. La recepción devuelve HTTP 201 y puede consultarse con `GET /api/v1/goods-receipts/{receiptId}`.

## Consistencia y alcance explícito

- No se añadió una migración nueva: las tablas de compras/recepciones vienen de V2 y `idempotency_record` de V9; P08 reutiliza ese esquema.
- Los cambios de inventario y documentos se revierten juntos si falla cualquier parte de la transacción.
- Las cantidades recibidas no pueden superar las ordenadas; las recepciones parciales conservan el pendiente.
- FIFO/promedio ponderado completo, devoluciones financieras, facturas y pagos a proveedores no forman parte de P08.
- No se ejecutaron pruebas, compilación, CI, arranque de aplicación ni migraciones; esas validaciones quedan reservadas para después de terminar la implementación fuente.
