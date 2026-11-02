# Fase 6 — Operaciones comerciales y estados financieros

**Código auditado:** dc028a346ccf7b94cfbb60b4fb6cd5493518b481 · **Estado:** EN PROGRESO.

## Capacidades presentes

- Órdenes de compra con estados, envío/aprobación/cancelación, actor y eventos Outbox.
- Recepciones parciales desde órdenes aprobadas; stock y valoración cambian al contabilizar recepción.
- Búsqueda server-side por número/proveedor para órdenes, recepciones y devoluciones.
- Endpoint server-side GET /goods-receipts/{id}/returnable-lines para cantidad recibida, devuelta y restante.
- UI de Compras con pestañas Órdenes, Recepciones y Devoluciones a proveedores. Los listados de recepciones y devoluciones paginan y buscan; las devoluciones vuelven a consultar la elegibilidad al servidor.
- Ventas transaccionales con stock/costeo e idempotencia; solicitud y aprobación separada de cancelaciones.
- Devolución física desde venta con decisión de destino.
- UI de Ventas con pestañas Ventas, Devoluciones y Reembolsos.
- Suscripciones con cuotas, búsqueda y filtros de estado/almacén/fecha/error, además de facturación interna.
- Backoffice WhatsApp con bandeja, work-summary, detalle y confirmación manual idempotente de pago.

## Distinciones que el sistema conserva

- Orden de compra no es recepción; la recepción es el movimiento de stock.
- Devolución física a proveedor no es factura/nota de crédito ni pago a proveedor.
- Venta/cancelación no es factura fiscal autorizada.
- Devolución de artículos no es reembolso de dinero.
- Facturación de una cuota de suscripción no prueba cobro externo.
- Pago confirmado en WhatsApp significa que un usuario autorizado registró verificación manual; no hay gateway.

## Brechas que mantienen la fase abierta

### Compras
- Mejorar el detalle de recepción y sustituir alertas simples por vistas/detalles accesibles donde corresponda.
- Completar indicadores de flujo y contadores server-side independientes por pestaña.
- DTOs ligeros, revisión N+1 y estados/errores por fila.
- Modelo de factura de proveedor y cuentas por pagar si el producto necesita deuda y liquidación de proveedores.

### Ventas y devoluciones
- Snapshot histórico completo de comprador/facturación y fulfillment.
- Contexto operativo de almacén/caja resuelto en servidor sin permitir almacén arbitrario.
- Descuentos/impuestos/cargos calculados por backend y desglosados por línea.
- Conciliar devolución física, documento fiscal y reembolso como operaciones diferentes.
- E2E por roles, estados y concurrencia.

### Pagos y reembolsos
- No hay libro de pagos/PaymentAttempt/PaymentTransaction con importe realmente liquidado y fuente bancaria.
- No se limita de forma completa el acumulado de reembolsos al importe efectivamente cobrado menos lo ya devuelto.
- No hay gateway/contrato charge/refund ni confirmación externa firmada.
- No hay conciliación bancaria.
- **Bloqueo P0:** RefundController no define @PreAuthorize y SecurityConfig usa permitAll para /api/v1/refunds/**. La operación no es segura para datos reales.
- Falta segregación de funciones verificable y auditoría íntegra de transiciones, además de idempotencia/replay correcto.

### Suscripciones
- Distinguir programado, facturado y cobrado de forma explícita.
- Reintentos trazables de facturación y estados de error/conciliación.
- No anunciar auto-renovación, pasarelas, créditos o entitlements no implementados.

## Validación de cierre

- Pruebas HTTP/RBAC/scopes de compras, recepciones, ventas, retornos y reembolsos.
- Pruebas concurrentes para doble recepción, sobredevolución, sobreventa y doble compensación.
- Pruebas browser de pestañas, filtros, formularios, URLs, responsive y estados vacíos/error.
- Contrato financiero aprobado, ledger de pagos y conciliación antes de anunciar reembolsos como capacidad completa.

**Conclusión:** se mejoró sustancialmente la UX de compras/ventas y la búsqueda server-side, pero el ciclo financiero completo y la seguridad de reembolsos siguen pendientes.