> **Marcador de vigencia — 2026-10-26:** este archivo se conserva como registro histórico de decisión/hito, no como inventario vivo de endpoints ni prueba de que el HEAD actual pasó validación. Para estado vigente, usar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia API actual](25-referencia-apis.md), [26 — Arquitectura/patrones](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar cada ruta y comportamiento en el código del SHA desplegado.

# P06 — Ventas con descuento atómico de stock

## Alcance implementado en código fuente

P06 refuerza el comando de creación de ventas sobre PostgreSQL. No se ejecutaron pruebas, compilación, arranque, migraciones ni CI.

### Precio y total calculados en servidor

- La solicitud solo acepta almacén, cliente, canal, datos de contacto y producto/cantidad por línea.
- El precio unitario se obtiene de `product.sale_price`; el cliente no puede suministrar precio unitario, subtotal, impuesto ni total.
- Cantidades se normalizan a tres decimales y precios/importes a dos, usando `BigDecimal` y redondeo explícito.
- La venta no mezcla monedas distintas; solo acepta productos activos de categorías activas y productos con control físico de stock.
- No se repite el mismo producto en dos líneas: se exige consolidar la cantidad antes de enviar la venta.

### Atomicidad y concurrencia

La creación se ejecuta dentro de una transacción PostgreSQL que incluye:
1. Registro de idempotencia.
2. Bloqueo pesimista y descuento de cada saldo.
3. Cabecera y líneas de la venta.
4. Movimientos de inventario por línea, con referencia al documento y a la línea origen, ubicación de salida, unidad de medida y correlación.
5. Evento Outbox y auditoría.
6. Marcado del registro de idempotencia como completado.

Las líneas se procesan por UUID de producto en orden estable para reducir deadlocks. El saldo aplica `available = on_hand - reserved`; si cualquier producto no tiene saldo o no alcanza la cantidad disponible, se lanza un error de dominio y la transacción revierte los cambios de todos los productos, la venta, movimientos, Outbox, auditoría e idempotencia.

### Idempotencia concurrente

- El cliente debe enviar `Idempotency-Key` (hasta 120 caracteres).
- La clave se reserva mediante `INSERT ... ON CONFLICT DO NOTHING` contra la restricción única `(scope, idempotency_key)` de `idempotency_record`. PostgreSQL serializa solicitudes concurrentes con la misma clave.
- Se compara el hash SHA-256 de una solicitud canónica: líneas ordenadas por producto, cantidades normalizadas, almacén, canal y datos del cliente.
- Repetir la misma clave y el mismo contenido devuelve la venta existente sin descontar stock otra vez.
- Reutilizar una clave con un contenido diferente responde con conflicto.
- El registro de idempotencia se completa en la misma transacción que la venta; un fallo hace rollback y libera la clave para reintentar.

### Límites conocidos

- El precio usa el precio de venta vigente del catálogo; listas de precios, promociones, descuentos comerciales e impuestos configurables no forman parte de esta tarea.
- La política de costo de salida/valoración FIFO o promedio no se integra en P06; el movimiento conserva el costo de producto existente y queda preparado para valoración posterior.
- Se mantiene la restricción actual de un saldo por producto y almacén; el movimiento registra la ubicación de origen.
- La validación final con PostgreSQL, pruebas de concurrencia, build y CI queda pendiente por instrucción del usuario.

## Criterios de aceptación — estado de implementación

- [x] Precio y total calculados en servidor; no se aceptan importes del cliente.
- [x] Venta, líneas, saldo y movimientos se escriben dentro de una transacción PostgreSQL.
- [x] Stock insuficiente provoca rollback de toda la operación.
- [x] Creación idempotente con manejo de solicitudes concurrentes y detección de reutilización de clave.
- [x] Movimientos vinculados a la venta y a la línea origen.
- [ ] Pruebas, compilación, migración y CI: reservadas para la fase final.
