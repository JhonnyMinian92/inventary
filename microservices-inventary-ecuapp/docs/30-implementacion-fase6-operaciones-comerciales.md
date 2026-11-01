# Fase 6 — Operaciones comerciales y estados financieros completos

**Rama:** `implementacion_propuestas`  
**Estado:** EN PROGRESO — no declarar completa hasta cerrar pagos, reembolsos, UX y pruebas integrales.

## Alcance revisado

La fase cubre órdenes de compra, recepciones, devoluciones a proveedores, ventas, cancelaciones/devoluciones de clientes, pagos/reembolsos y suscripciones. Los estados de inventario, dinero y documento fiscal son independientes.

## Capacidades existentes verificadas

- Órdenes de compra con estados DRAFT, SUBMITTED, APPROVED, PARTIALLY_RECEIVED, RECEIVED y CANCELLED; envío y aprobación separados, idempotencia, actor de aprobación y eventos Outbox.
- Recepciones parciales desde órdenes aprobadas, control de cantidades pendientes, valoración de inventario, movimientos, auditoría y Outbox.
- Devoluciones a proveedores originadas en recepciones contabilizadas; el backend revalida cantidades y disponibilidad. Una devolución física no crea por sí misma nota de crédito ni reembolso.
- Ventas con precios calculados/validados en backend, control transaccional de stock, coste histórico, idempotencia, auditoría y Outbox.
- Solicitud y aprobación de cancelación de venta con actores distintos; las existencias se compensan una sola vez.
- Devoluciones de clientes desde la venta original con validaciones de cantidades y disposición del inventario.
- Suscripciones con calendario de cuotas, previsualización, generación programada de ventas internas, cancelación y prepagos entendidos como facturación, no como confirmación de dinero recibido.

## Cambios de este corte

- Se agregó búsqueda server-side `q` en `GET /api/v1/purchase-orders`, buscando por número de orden y nombre de proveedor y manteniendo la paginación y el filtro por almacenes autorizados.
- Se añadió prueba de integración para la búsqueda de órdenes.
- Se extendió la búsqueda server-side a `GET /api/v1/goods-receipts` (número de recepción, orden y proveedor) y `GET /api/v1/purchase-returns` (número de devolución, recepción, orden y proveedor), manteniendo scope y paginación.
- Se añadió `GET /api/v1/goods-receipts/{id}/returnable-lines`, que calcula en servidor cantidad recibida, devuelta y restante por línea. La UI ya no descarga hasta 100 devoluciones para reconstruir elegibilidad ni confía en agregados parciales del navegador.
- La UI de devoluciones a proveedores usa búsqueda paginada para recepciones y devoluciones, y consume el saldo retornable calculado por el backend.
- Se conectó el filtro de búsqueda de la pantalla Compras al parámetro server-side y se persiste como `purchases.q` en la URL. La búsqueda no descarga el historial completo ni filtra solo la página visible.
- Se corrigió la prueba de bootstrap Flyway que aún esperaba la versión 20; la rama ya contiene 22 migraciones incrementales.
- La CI del commit que amplía la búsqueda y elegibilidad de devoluciones debe terminar en verde; las primeras ejecuciones intermedias fallaron porque los cambios de repositorio, servicio, controlador y pruebas se estaban aplicando secuencialmente. Se debe validar el SHA final.

## Pendientes que impiden cerrar la fase

### Compras
- Separar Órdenes, Recepciones y Devoluciones a proveedores en pestañas de primer nivel con estado/error/empty independiente.
- Añadir búsqueda y filtros server-side a recepciones y devoluciones; dejar de pedir hasta 100 registros para calcular elegibilidad en la UI. El servidor debe seguir siendo la autoridad contra sobredevoluciones concurrentes.
- DTOs ligeros para tablas, contadores agregados del servidor y medición de N+1.
- Permisos por acción en cada fila y estados de carga por fila, no un único bloqueo global.
- Mantener claro que el total de la orden es valor planificado, no factura del proveedor ni deuda pagada.

### Ventas y devoluciones
- Completar el flujo guiado de cotización, comprador/facturación, destinatario/fulfillment y confirmación con snapshots históricos inmutables.
- Resolver almacén habitual por usuario/caja/terminal autorizada en servidor; el modelo actual requiere que el formulario proporcione `warehouseId`.
- Presentar descuentos, impuestos/cargos y pago solo según respuestas reales del backend; no hardcodear tasas ni presentar subtotal como impuesto.
- Separar vistas de ventas y devoluciones, con filtros, paginación, permisos por acción y estados de pago/fiscal/fulfillment.
- Completar la conciliación de devoluciones con reembolso y, cuando corresponda, documento fiscal, sin equiparar esas operaciones.

### Pagos y reembolsos
- No existe todavía un libro de pagos ni entidades PaymentAttempt/PaymentTransaction/Refund persistidas ligadas a la venta y al pago original.
- Falta contrato de pago manual: métodos permitidos, evidencia, estados reportado/verificado, permisos de confirmación, aprobación independiente y conciliación.
- Reembolso debe ser una transacción compensatoria separada, idempotente, auditada y limitada al importe realmente liquidado del pago original.
- No hay proveedor/gateway de pago aprobado ni contrato de charge/refund; no se simula una autorización externa ni se considera una captura como confirmación bancaria.
- No existe todavía el modelo de factura de proveedor/cuenta por pagar; por ello no se puede mostrar deuda o pagos a proveedores a partir de órdenes y recepciones.

### Suscripciones
- Añadir búsqueda/filtros y contadores server-side que distingan programado, facturado y cobrado.
- Mejorar la bandeja de próximos cargos y errores de facturación con reintento seguro y operación trazable.
- Conservar el alcance actual: no habilitar auto-renovación, pruebas, créditos, derechos ni pasarelas no modeladas.

## Validación requerida

- CI backend y frontend en SHA final; migraciones Flyway desde base vacía y upgrade, Hibernate validation y Compose smoke test.
- Pruebas HTTP de permisos/scope, paginación/búsqueda, idempotencia, concurrencia de recepciones/devoluciones/cancelaciones y compensaciones.
- Pruebas de pagos parciales, duplicados, pago reportado no verificado, aprobación independiente y reembolso no superior al saldo liquidado una vez exista el contrato.
- Pruebas browser de las pestañas, filtros URL, formularios y responsive.
- La fase solo se cierra cuando los contratos y las evidencias anteriores existan. No se atribuye completitud a capacidades futuras ni a integraciones externas no contratadas.
