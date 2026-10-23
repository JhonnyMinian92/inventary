# P07 — Inventario operativo, conteos físicos y ledger

## Alcance implementado en código fuente

P07 conecta los flujos operativos de ajustes y transferencias con el ledger inmutable y añade conteos físicos con diferencias contabilizadas de forma transaccional. La fase de validación queda pendiente: no se ejecutaron pruebas, compilación, CI, arranque ni migraciones.

## Saldos y movimientos

- StockBalance conserva el saldo operativo por producto y almacén, separando existencias físicas (onHand), reservas y disponible.
- Las mutaciones de saldo usan bloqueo pesimista y validaciones de dominio; una salida no puede dejar el saldo físico negativo ni consumir stock reservado.
- Ajustes de entrada/salida requieren Idempotency-Key, motivo, producto, almacén, dirección y cantidad positiva con hasta tres decimales.
- Los movimientos de ajuste, transferencia y conteo registran ubicaciones, unidad de medida, línea de origen cuando aplica y correlación documental.
- El historial stock_movement ya tiene triggers de solo inserción definidos por V9: correcciones posteriores deben representarse con movimientos compensatorios, nunca editar/borrar el ledger.
- P07 amplía la respuesta de movimientos para exponer metadatos de trazabilidad.

## Conteos físicos

### Crear un conteo

```http
POST /api/v1/inventory/counts
Authorization: Bearer <JWT>
Idempotency-Key: conteo-bodega-2026-001
Content-Type: application/json

{
  "warehouseId": "UUID_DEL_ALMACEN",
  "items": [
    {"productId": "UUID_PRODUCTO_A", "countedQuantity": 14.000},
    {"productId": "UUID_PRODUCTO_B", "countedQuantity": 0.000}
  ]
}
```

La operación valida almacén y productos activos con control físico, impide repetir productos y guarda para cada línea el saldo esperado capturado y la cantidad contada. La clave se reserva en idempotency_record con el ámbito INVENTORY_COUNT_CREATE; repetir la misma clave y contenido recupera el conteo, y cambiar el contenido genera conflicto.

### Contabilizar o cancelar

- POST /api/v1/inventory/counts/{id}/post contabiliza un conteo abierto.
- POST /api/v1/inventory/counts/{id}/cancel cancela un conteo abierto sin alterar existencias.
- GET /api/v1/inventory/counts y GET /api/v1/inventory/counts/{id} consultan conteos y diferencias.

Antes de aplicar cualquier diferencia, el servicio bloquea cada saldo en orden estable y compara el saldo actual con el snapshot. Si cambió desde la captura, toda la contabilización se revierte y requiere un nuevo conteo; así no se sobrescriben ventas, recepciones u otros movimientos posteriores. Las diferencias positivas y negativas crean movimientos compensatorios ADJUSTMENT_IN/ADJUSTMENT_OUT ligados al conteo y a la línea. Las salidas siguen respetando las reservas. Las líneas de conteo son inmutables en PostgreSQL; para corregir una captura se crea otro conteo.

## Transferencias entre almacenes

Las rutas existentes se mantienen:

- POST /api/v1/inventory/transfers crea el documento con clave idempotente.
- POST /api/v1/inventory/transfers/{id}/dispatch bloquea y descuenta las existencias de origen, registrando una salida por línea.
- POST /api/v1/inventory/transfers/{id}/receive acredita el destino y registra la entrada por línea.
- POST /api/v1/inventory/transfers/{id}/cancel solo permite cancelar el borrador, antes de que exista movimiento físico.

La creación usa reserva concurrente de clave en idempotency_record para evitar documentos duplicados ante reintentos concurrentes. Despacho y recepción son transaccionales, bloquean saldos por producto en orden estable y los movimientos referencian transferencia, línea, ubicación y unidad. Una recepción repetida sobre una transferencia completada devuelve el documento existente sin volver a acreditar stock.

## Seguridad y auditoría

Los endpoints de consulta requieren inventory:manage o audit:read; crear, contabilizar, cancelar, ajustar, despachar y recibir requieren inventory:manage. Cada creación, contabilización, cancelación y ajuste genera auditoría en la misma transacción.

## Migración y límites

- V11__inventory_counts_and_ledger_hardening.sql agrega tablas de conteo, constraints, índices y un trigger que prohíbe modificar/eliminar líneas de conteo.
- V9 ya protege stock_movement y inventory_valuation_entry con triggers append-only.
- El modelo conserva el contrato actual de un saldo por producto y almacén, aunque cada saldo referencia una ubicación principal. No implementa todavía múltiples saldos simultáneos por producto dentro de un mismo almacén.
- La valoración FIFO/promedio completa y la contabilización de capas de costo no se activan en este hito.
- La validación final de mapping JPA, migraciones, concurrencia, rollback y contratos HTTP se ejecutará solo cuando toda la implementación de fuente esté cerrada.
