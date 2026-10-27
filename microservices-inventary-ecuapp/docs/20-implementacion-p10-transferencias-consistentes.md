> **Marcador de vigencia — 2026-10-26:** registro histórico de hito/entrega, no inventario vivo de rutas ni garantía de validación del HEAD actual. Consultar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia de APIs](25-referencia-apis.md), [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar comportamiento y permisos contra el código y pruebas del SHA que se despliega.

# P10 — Transferencias consistentes y operaciones compensatorias

## Alcance

P10 completa el flujo entre almacenes con operaciones de estado idempotentes, movimientos de inventario inmutables, compensaciones trazables y control concurrente para que una transferencia no cree ni pierda unidades por reintentos o fallos parciales.

## API

Base path: `/api/v1/inventory/transfers`. Los comandos requieren `inventory:manage` y un header `Idempotency-Key` único por operación:

- `POST /`: crear borrador de transferencia entre almacenes activos y distintos.
- `POST /{id}/dispatch`: descontar el origen y registrar `TRANSFER_OUT` en una transacción.
- `POST /{id}/receive`: acreditar el destino y registrar `TRANSFER_IN` en una transacción.
- `POST /{id}/cancel`: cancelar un borrador o una transferencia en tránsito; si ya se despachó, repone el origen con un movimiento compensatorio.
- `POST /{id}/reverse`: revertir una transferencia completada, debitando el destino y reponiendo el origen atómicamente. Requiere un motivo.
- `GET /` y `GET /{id}`: consultar transferencias.

Cancelación y reversión reciben JSON `{"reason":"..." }`; el motivo es obligatorio y tiene máximo 1000 caracteres. La reversión no borra ni modifica movimientos históricos: crea dos movimientos compensatorios vinculados a la transferencia y línea originales.

## Consistencia e idempotencia

- Cada transición reserva `idempotency_record` mediante inserción concurrente `ON CONFLICT DO NOTHING`; repetir clave/contenido reproduce la transferencia y cambiar el contenido produce conflicto.
- La creación usa un hash canónico independiente del orden de las líneas y mantiene compatibilidad de reintento con hashes JSON históricos.
- Las líneas se procesan en orden estable de producto. Las reversiones bloquean ambos saldos en orden estable de almacén para reducir interbloqueos.
- La creación concurrente de ubicación principal y saldo de destino usa inserción idempotente; no se crean saldos duplicados para el mismo producto/almacén.
- `StockBalance.decrease` impide stock negativo y consumo de reservas. Un error revierte el cambio de estado, saldos, ledger, auditoría, Outbox e idempotencia juntos.
- Los eventos Outbox se escriben en la misma transacción para creación, despacho, recepción, cancelación y reversión.
- El estado `REVERSED` y los metadatos de cancelación/reversión permiten identificar claramente la compensación y su actor/motivo.

La migración incremental `V13__transfer_compensation_and_consistency.sql` agrega metadatos y restricciones del estado terminal; rellena las cancelaciones históricas sin modificar migraciones ya aplicadas.

## Validación aplazada

Se actualizó el código fuente del test de integración para despacho/recepción, cancelación compensatoria en tránsito y reversión de una transferencia completada. No se ejecutaron pruebas, compilación/build, CI, arranque, migraciones ni comprobaciones de entorno. La validación se hará únicamente después de completar toda la implementación fuente.
