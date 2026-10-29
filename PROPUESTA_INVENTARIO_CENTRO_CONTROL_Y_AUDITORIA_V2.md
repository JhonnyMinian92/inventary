> **Documento sustituido:** esta V2 conserva la revisión anterior como histórico. La propuesta vigente, ampliada con benchmark de ERP/WMS, experiencia del dueño y arquitectura preparada para agentes, es [PROPUESTA_INVENTARIO_AGENT_READY_V3.md](PROPUESTA_INVENTARIO_AGENT_READY_V3.md).  
> No implementar el roadmap de V2 de forma aislada; utilizar V3 como referencia estratégica vigente.

# Propuesta de Inventario como centro de control, gestión y auditoría

**Versión:** 2.0  
**Fecha:** 28 de octubre de 2026  
**Repositorio:** JhonnyMinian92/inventary · rama master  
**Estado:** propuesta de UX, backend y arquitectura, pendiente de implementación.  
**Alcance:** existencias, movimientos, coste, auditoría, conteos, ajustes, traslados, reposición y sus integraciones con Productos, Compras, Ventas, Suscripciones, Pedidos WhatsApp y n8n.

> Este documento distingue lo que existe en el código actual de lo que se propone construir. Crear esta propuesta no modifica ni prueba el código fuente.

## 1. Decisión ejecutiva

Inventary debe tratar el Inventario como el centro de control de las operaciones físicas y la trazabilidad, no como una tabla de saldos seguida por un bloque de operaciones situado al final del scroll. La pantalla principal debe responder de inmediato a cinco preguntas:

1. ¿Qué stock físico existe por producto y almacén?
2. ¿Qué cantidad está disponible y cuál se encuentra reservada?
3. ¿Qué movimiento originó un cambio, en qué documento, por quién y cuándo?
4. ¿Qué tarea necesita atención: reposición, traslado en tránsito, conteo abierto, diferencia de saldo o inconsistencia de valoración?
5. ¿Qué ocurrió con el inventario cuando se registró una recepción, venta, devolución o pedido de WhatsApp?

La propuesta convierte la pantalla en un centro de control con cinco vistas de primer nivel: **Resumen, Existencias, Movimientos/Kárdex, Operaciones y Auditoría**. Cada vista tendrá filtros, paginación, estados de carga/error y permisos propios. Los formularios largos se mantienen como procesos guiados por pasos.

**Principio arquitectónico:** Spring Boot y PostgreSQL son la autoridad de stock. React presenta los resultados, pero no calcula ni persiste cambios por su cuenta. n8n coordina tareas downstream, pero no modifica los saldos directamente. Una IA puede interpretar o resumir una consulta, nunca decidir stock, coste, reservas o permisos.

## 2. Auditoría de la pantalla actual

### 2.1 Problemas de maquetación y carga

La sección actual se compone en app-inventary-ecuapp/src/App.jsx. El orden visual observado en la captura es: filtros, tarjetas de resumen, tabla de saldos, tabla de últimos movimientos y, al final, el panel Operaciones de inventario con pestañas de Traslados, Conteos físicos, Reposición y Ajustes. Esto obliga a desplazarse después de consultar dos tablas para llegar a las acciones del negocio.

| Hallazgo verificado | Evidencia en el código | Prioridad y consecuencia |
|---|---|---|
| Las operaciones están debajo de los saldos y movimientos. | App.jsx renderiza InventoryOperations después de ambas tablas. | Alta: el usuario descubre tarde las tareas que necesita ejecutar. |
| La carga global carga todas las páginas de balances; además obtiene los primeros 100 movimientos y solicita datos de compras, ventas, productos, clientes y valoración. | App.jsx, loadData; api.js | Alta: abrir una vista hace trabajo que no corresponde a esa vista y escala con el historial. |
| El indicador stockTotal suma onHand de todos los balances sin separar unidades de medida. | App.jsx, cálculo de stockTotal | Crítica: sumar metros, cajas y unidades en una sola cifra no tiene significado empresarial. Debe retirarse o cambiarse por métricas semánticamente válidas. |
| El filtrado de inventario solicita size=100 y reemplaza los balances/movimientos con la página recibida. | App.jsx, loadInventoryFilter | Alta: los filtros pueden dejar el conjunto truncado cuando hay más de 100 registros y la pantalla no da paginación visible. |
| La tabla de balances no incluye paginación ni búsqueda remota. | App.jsx, DataTable de saldos | Alta: se hace difícil localizar un SKU con un catálogo grande. |
| La tabla de últimos movimientos usa solo una muestra limitada, no un historial navegable. | App.jsx, movements.slice(0, 30) | Alta: no sirve para investigar operaciones antiguas o documentar diferencias. |
| InventoryOperations obtiene todas las páginas de traslados y conteos y carga listas completas de reglas y sugerencias. | src/features/inventory/InventoryOperations.jsx, refresh | Alta: el listado completo no es necesario para pintar una página de resultados. |
| refresh no activa loading al inicio, así que los empty states pueden aparecer antes de que llegue la respuesta. | InventoryOperations.jsx | Media/alta: una consulta lenta puede confundirse con una lista vacía. |
| La pestaña Ajustes no ofrece una tabla propia de historial; solo muestra explicación y CTA para crear uno. | InventoryOperations.jsx, tab adjustments | Alta para auditoría: se debe buscar el ajuste en una tabla global. |
| El contador de las pestañas se calcula desde los arrays cargados. | InventoryOperations.jsx | Debe cambiar cuando exista paginación: el número de filas visibles no es el total global. |
| La pantalla mezcla monitorización, historial y comandos en una sola página larga. | App.jsx e InventoryOperations.jsx | Alta: se necesita una arquitectura de información por tarea, no solo cambios de CSS. |
| InventoryOperations no recibe un mapa de permisos para ocultar acciones. | App.jsx e InventoryOperations.jsx | Alta: pueden mostrarse botones a usuarios que el backend rechazará. |
| El menú de Inventario se revela con inventory:read, pero movimientos, traslados, conteos y reposición tienen GET protegidos con inventory:manage o audit:read. | App.jsx, InventoryController, StockTransferController, InventoryCountController, ReorderController | Crítica: un usuario de lectura puede entrar a la sección y recibir 403 al consultar datos operativos. |
| La valoración se consulta con un reporte que requiere reports:financial. | InventoryBalanceReportController | Alta: no se debe depender de una API financiera para mostrar el saldo físico a un usuario sin permisos de costes. |
| Las sugerencias se calculan solo para reglas de reposición activas que existen. | ReorderService.suggestions | Importante: ausencia de sugerencias no significa que no haya productos con stock bajo; puede haber productos sin regla. |
| ReorderService.list carga todas las reglas y filtra en memoria; suggestions consulta el saldo por cada regla. | ReorderService | Alta: hay riesgo de consulta N+1 y no existe una respuesta paginada eficiente. |
| El informe de kárdex limita el resultado a 2.000 movimientos. | OperationalReportService.kardex | Debe mostrarse un límite explícito y ayudar a reducir fechas, en vez de parecer que el historial está completo. |
| El servicio de ajuste manual y InventoryCountService no escriben actualmente en OutboxEvent. | InventoryService e InventoryCountService | Hueco para notificar integraciones downstream de todos los cambios de stock de forma uniforme. |

La corrección del indicador “unidades” es inmediata: contar productos/SKU con existencia, contar productos sin disponibilidad o presentar cantidades por unidad de medida. No sumar indiscriminadamente cantidades. Del mismo modo, el inventario valorizado debe presentarse agrupado por moneda.

### 2.2 Capacidades que ya existen en el backend

El código contiene piezas de dominio que deben mostrarse y conectarse, no reemplazarse con una segunda implementación:

- StockBalance representa el saldo actual por producto y almacén, con onHand, reserved, available y versión.
- StockMovement registra tipo, producto, almacén, cantidad, coste unitario, unidad de medida, origen/destino de ubicación, referencia al documento, correlación, actor y fecha.
- InventoryCostingService implementa entradas y salidas valorizadas y trabaja con FIFO o promedio ponderado móvil.
- InventoryValuationEntry, InventoryCostLayer e InventoryCostState conservan información necesaria para explicar la valoración.
- El detalle de movimiento ya devuelve entradas de valoración.
- Existen APIs para ajustes, traslados, conteos, reposición, saldos, disponibilidad y movimientos.
- Los reportes incluyen valoración, kárdex, compras, ventas, balance de inventario y bajo stock.
- AuditService registra acciones; GET /api/v1/audit-events permite consultar actor, acción, recurso, resultado y periodo.
- Existe infraestructura transaccional de Outbox, utilizada en varios flujos, cuyo delivery es al menos una vez; los consumidores deben deduplicar.

La pantalla actual no expone bien estas capacidades y algunas listas se descargan de forma ineficiente. La propuesta debe aprovecharlas y completar los contratos que faltan.

## 3. El modelo que debe guiar toda la interfaz

Inventario debe distinguir sin ambigüedad:

- **Existencia física (onHand):** la cantidad registrada dentro del almacén.
- **Reservado (reserved):** la cantidad comprometida a operaciones abiertas.
- **Disponible (available):** cantidad física menos reserva.
- **Valoración:** valor de coste del stock bajo la política configurada; no es precio público.
- **Movimiento:** registro que explica una entrada, salida, reserva, liberación, devolución, ajuste o transferencia.
- **Documento origen:** compra, recepción, venta, devolución, traslado, conteo, ajuste o pedido que explica el movimiento.
- **Auditoría:** quién realizó la acción, resultado, motivo y correlación.
- **Evento downstream:** comunicación a una integración, que puede retrasarse sin invalidar la transacción interna.

Reglas no negociables:

1. Todo cambio físico se ejecuta en el backend dentro de una operación autorizada y transaccional.
2. La disponibilidad mostrada en pantalla es una lectura informativa; el comando final vuelve a bloquear y validar stock.
3. No se consumen saldos reservados por otra operación y no se permite stock negativo.
4. Toda cantidad se muestra con su unidad de medida. No se suman cantidades heterogéneas.
5. Los importes en diferentes monedas no se suman como si fueran una sola moneda.
6. La compra aumenta existencias al contabilizar la recepción, no al crear la orden.
7. Una cotización no es una salida física; una reserva no es lo mismo que el consumo definitivo del stock.
8. Un traslado reduce el origen al despacharse y aumenta el destino al recibirse.
9. Un conteo no puede contabilizar un delta sobre un saldo que cambió después de la captura sin resolver ese conflicto.
10. La corrección del historial se representa mediante una operación compensatoria y motivo, no borrando movimientos.
11. Un fallo de n8n, WhatsApp o notificaciones no revierte una operación interna que ya fue confirmada.

## 4. Arquitectura visual recomendada

### 4.1 Encabezado persistente y acciones

La cabecera debe contener título Inventario, texto explicativo, selector de almacén, búsqueda de producto y estado de actualización. Debe tener botones rápidos de **Nuevo traslado**, **Nuevo conteo** y **Nuevo ajuste**, además de un acceso a Reposición, según permisos.

No dar el mismo peso a todas las acciones. Mostrar las más habituales en la cabecera y agrupar acciones secundarias en un menú accesible. Las acciones pertenecen al contexto principal del módulo y no debajo de dos tablas largas.

### 4.2 Pestañas de primer nivel

- **Resumen**
- **Existencias**
- **Movimientos / Kárdex**
- **Operaciones**
- **Auditoría** (solo para quienes tienen permisos)

Las pestañas son vistas independientes con sus propios filtros, skeleton, errores, tabla y paginación. Deben conservar contexto en la URL: tab, warehouseId, q, filtros, sort, page y size. Actualizar o volver atrás no debe perder innecesariamente la vista.

### 4.3 Resumen: qué poner en el primer viewport

Orden sugerido:

**A. Contexto y acciones rápidas.** Selector de almacén, búsqueda y accesos principales.

**B. Indicadores breves.** Tres a cinco tarjetas, según permisos:
- productos con existencia física;
- productos con disponibilidad cero;
- productos por debajo del mínimo de una regla activa;
- operaciones pendientes (traslados en tránsito y conteos abiertos);
- valor de inventario agrupado por moneda, solo con permiso de costes.

No debe existir una tarjeta “Unidades totales” que agregue metros, litros, cajas y unidades sin conversión.

**C. Alertas y pendientes accionables.** Lista que explique qué ocurre, por qué importa y dónde resolverlo: bajo mínimo, conteo abierto, traslado en tránsito, conflicto de conteo o discrepancia de valoración. Si no hay plazo/ETA en el modelo, mostrar antigüedad de un traslado, no afirmar que está atrasado.

**D. Estado de existencias.** Productos sin disponibilidad, con stock crítico o con mayor brecha frente al objetivo, mostrando almacén y unidad de medida. Cada fila debe abrir la vista Existencias con filtros aplicados.

**E. Últimos movimientos.** Muestra de 8–10 entradas recientes, con producto, cantidad/unidad, almacén, referencia y hora. El enlace “Ver todos” lleva a Movimientos/Kárdex; la muestra no reemplaza el historial paginado.

**F. Consistencia de valoración.** Indicador de reconciliación por almacén/moneda para roles financieros; para roles operativos, señal de consistencia de cantidad sin revelar costes.

### 4.4 Existencias

Tabla de servidor con estas columnas:

| Columna | Contenido |
|---|---|
| Producto | Nombre, SKU, tipo de producto físico y categoría si está disponible. |
| Almacén | Código/nombre. |
| Existencia física | onHand y unidad de medida. |
| Reservado | reserved y la misma unidad. |
| Disponible | available, con indicador si es cero o bajo mínimo. |
| Estado | Con stock, sin disponibilidad, bajo mínimo, sin regla o producto inactivo. |
| Valoración | Solo con permiso financiero, por moneda y política de coste. |
| Actualizado | Momento de actualización del saldo si está disponible en el DTO. |
| Acciones | Ver movimientos, detalle de producto o regla de reposición. |

Filtros: almacén, búsqueda SKU/código/nombre, categoría/tipo si es soportado por el catálogo, solo disponibles, sin disponibilidad, bajo mínimo y estado de producto. La búsqueda debe ejecutarse en backend; no filtrar localmente solo las filas cargadas.

El detalle de un producto debe agrupar el saldo por almacén, mostrar onHand/reserved/available, regla de reposición, movimientos recientes, documentos origen y valoración si la autoridad lo permite. Productos describe qué es el artículo; Inventario describe dónde está y qué le ocurrió.

### 4.5 Movimientos / Kárdex

Debe ser una herramienta de investigación completa, no la tabla recortada del resumen.

Filtros: producto/SKU, almacén, tipo de movimiento, fechas, referencia/documento y, si se habilita en backend, actor y correlationId. Ordenación descendente por fecha, paginación real y tamaño configurable.

Columnas: fecha/hora local, movimiento legible, producto/SKU, almacén, entrada/salida con unidad, referencia de origen, actor y acción “Ver trazabilidad”. Si el saldo posterior no puede obtenerse correctamente desde el backend, no calcularlo en React sumando páginas.

El detalle del movimiento debe incluir tipo técnico y etiqueta legible, cantidad/unidad, almacén, ubicación origen/destino, referencia, sourceLineId/correlationId, actor, hora, valoración y entradas del ledger según permiso, así como eventos de auditoría vinculados. Debe poder abrir el documento origen. Si el documento antiguo ya no puede resolverse, conservar y mostrar referenceType/referenceId.

Los tipos actuales incluyen ADJUSTMENT_IN, ADJUSTMENT_OUT, SALE, SALE_CANCEL_IN, RECEIPT, RETURN_IN, RETURN_OUT, TRANSFER_IN, TRANSFER_OUT, RESERVE y RELEASE. Las etiquetas deben traducirlos sin perder el valor técnico en el detalle.

El kárdex valorizado actual requiere producto, almacén y periodo y limita el informe a 2.000 movimientos. La pantalla debe explicar que hay que reducir el rango, o en una fase posterior migrar el kárdex a paginación por cursor/snapshot manteniendo el saldo corrido exacto.

### 4.6 Operaciones: pestañas reales

Cada subpestaña debe tener su cabecera, CTA, filtros, tabla paginada, loading/error y empty state. No debe estar visualmente relegada a la parte inferior del módulo.

**Traslados**
- Columnas: número, origen, destino, estado, cantidad/líneas, fecha, antigüedad y acciones.
- Estados actuales: DRAFT, IN_TRANSIT, COMPLETED, CANCELLED y REVERSED.
- Mostrar que en tránsito el origen ya fue disminuido y el destino aún no ha sido incrementado.
- Acciones según estado y permisos: despachar, recibir, cancelar o revertir. Cancelación/reversión requieren motivo cuando el backend lo exige y generan compensaciones, no ediciones históricas.
- Filtros por estado, origen, destino, número y periodo.

**Conteos físicos**
- Columnas: número, almacén, estado, fecha, creador, líneas y diferencia total.
- Estados actuales: OPEN, POSTED y CANCELLED.
- Crear un conteo con flujo guiado y mostrar por línea esperado, contado y diferencia. Cero es una cantidad válida.
- Antes de contabilizar, resumir los ajustes previstos.
- Si el saldo cambió desde el snapshot, el backend rechaza el posting; la UI conserva el documento, identifica el conflicto y guía a refrescar/recontar, sin reintentar a ciegas.
- En una fase posterior, habilitar conteos cíclicos por almacén, categoría, zona o rotación sin fingir que hoy existe un motor completo de bins.

**Ajustes**
- Mostrar su historial propio consultando movimientos filtrados por ADJUSTMENT_IN/ADJUSTMENT_OUT y referencias aplicables.
- Columnas: fecha, producto, almacén, dirección, cantidad/unidad, motivo, usuario, referencia y coste cuando esté autorizado.
- Nuevo ajuste exige producto, almacén, dirección, cantidad positiva y motivo.
- El coste unitario solo se admite para la entrada según el contrato actual; la salida usa la política de valoración.
- La confirmación debe mostrar el impacto esperado. Si se añaden umbrales de aprobación, se implementan como regla de backend, no como una condición oculta de React.

**Reposición**
- Separar “Necesita reposición” de “Reglas configuradas”.
- Mostrar disponible, mínimo, objetivo, cantidad sugerida, plazo y proveedor preferido.
- Identificar de forma explícita productos físicos sin una regla configurada.
- “Crear orden de compra” solo debe abrir un borrador revisable; una sugerencia no crea ni envía una compra automáticamente.
- La regla mínimo/objetivo está desalineada: el frontend rechaza targetQuantity igual a minimumQuantity, mientras ReorderService solo rechaza cuando targetQuantity es menor. Elegir una sola regla y cubrirla con tests.

### 4.7 Auditoría

La vista debe responder “¿quién cambió el inventario y por qué?”, no limitarse a una tabla genérica de audit-events.

Filtros: producto, almacén, periodo, actor, tipo/referencia de documento, tipo de movimiento, acción/outcome y correlationId, con autorización apropiada. La cadena de trazabilidad debe conectar, cuando aplique:

**Documento origen → operación/transacción → movimiento → valoración → evento de auditoría → evento de integración.**

Separar una operación exitosa, intento denegado por permiso, conflicto de stock/estado y fallo downstream de notificación. No convertir un 403 en un empty state, ni mostrar costes a quien solo tiene permiso de cantidad.

## 5. Inventario como núcleo de Productos, Compras, Ventas, Suscripciones, WhatsApp y n8n

### 5.1 Productos

La propuesta de Productos busca fichas fiables, tipo de producto, unidad y categoría. Inventario debe contabilizar cantidades físicas solo para productos de tipo stockeable, usando el mismo SKU y unidad en compras y ventas. Servicio no equivale a mercancía.

Los productos inactivos conservan movimientos e historial, aunque las nuevas operaciones se restrinjan según las reglas de negocio. Inventario debe enlazar al detalle del producto, no duplicar la edición del catálogo dentro del flujo de movimientos.

Si Productos habilita variantes con stock independiente, se debe decidir el modelo antes de ofrecerlo en la UI. Actualmente el saldo y el movimiento usan productId y warehouseId; no debe simularse stock por variante a partir del nombre. Variantes, bins, lotes y series requieren definir la dimensión del saldo, el movimiento, la valoración y las reservas.

### 5.2 Compras y recepciones

- Crear o aprobar una orden no aumenta el stock.
- Contabilizar una recepción incrementa existencias y actualiza la valoración.
- Una devolución a proveedor reduce el stock según las cantidades recibidas y no devueltas que el backend permite.
- La vista de Inventario debe enlazar al documento de orden y recepción que originó el movimiento.
- El total de orden no es dinero pagado al proveedor; el flujo actual de devolución registra la salida del inventario, no una nota de crédito ni un reembolso financiero.

La pantalla debe mostrar cada recepción como un documento independiente y permitir llegar al producto y su kárdex desde la recepción.

### 5.3 Ventas y devoluciones de clientes

La cotización/lectura de disponibilidad no garantiza stock en el futuro. El backend debe bloquear y validar el saldo al registrar la venta, reserva o expedición que corresponda. La interfaz debe distinguir pedido, venta, pago y entrega; no tratar una cotización como movimiento físico.

Las cancelaciones y devoluciones de cliente vuelven a incrementar el inventario solo cuando el flujo autorizado determina que el artículo regresa al stock vendible. Si se incorporan cuarentena, daño, merma o reparación, se deben modelar como una disposición explícita; no se debe volver a vender automáticamente un artículo no apto.

### 5.4 Pedidos WhatsApp y WebView

- React y WhatsApp comparten los mismos endpoints de disponibilidad y reglas del backend.
- Reserva aumenta reserved y reduce available, pero no representa una salida física final.
- Cancelación/expiración libera la reserva una sola vez.
- La confirmación del pedido consume la reserva y/o registra la salida conforme al flujo de negocio actual, sin duplicar movimiento.
- WebView consulta el stock de backend con credencial acotada de cliente; nunca expone X-Integration-Key ni tokens de n8n.
- Inventario enlaza pedido WhatsApp, venta y movimientos para que soporte pueda reconstruir el proceso.
- n8n no confirma stock porque una IA lo haya mencionado en conversación; consulta/ejecuta el endpoint autorizado y usa la respuesta del servidor.

### 5.5 Suscripciones

La propuesta de Suscripciones actual se centra en contratos de servicios y calendario de facturación. Emitir una cuota o factura no debe modificar el inventario físico por sí solo.

Si posteriormente se ofrecen cajas, dispositivos o entregas periódicas, se debe diseñar fulfillment: cuándo reservar, cuándo descontar, qué ocurre ante falta de stock, sustitución, pausa/cancelación, reembolso y reintentos. Cada entrega debe estar ligada a la suscripción y a la cuota sin duplicar movimientos.

### 5.6 n8n e IA

n8n puede notificar niveles bajo mínimo, traslado despachado/recibido, conteos pendientes y problemas de integración. La IA puede resumir una incidencia o formular preguntas, pero no decide cantidades, coste, reserva ni permisos.

Las notificaciones deben tener enlace de regreso al documento, eventId para deduplicación, política de reintentos y estado de entrega. Si n8n o el proveedor IA deja de funcionar, las operaciones internas de inventario continúan; el fallo downstream no revierte un movimiento confirmado.

## 6. Backend: contratos que existen y cambios propuestos

### 6.1 APIs actuales que deben reutilizarse

| API | Capacidad actual | Limitación para la nueva experiencia |
|---|---|---|
| GET /api/v1/inventory/balances | Página con productId, warehouseId, onlyAvailable y ordenación permitida. | No incluye búsqueda general por texto ni filtros explícitos de categoría/bajo mínimo. |
| GET /api/v1/inventory/balances/{productId}/{warehouseId} | Saldo de un producto/almacén. | Es para detalle, no para reconstruir listados globales. |
| GET /api/v1/inventory/availability | Consulta informativa por productId o SKU y almacén; puede comprobar cantidad solicitada. | No crea reserva ni garantiza el stock al terminar la consulta. |
| GET /api/v1/inventory/movements | Página de movimientos; permite producto, almacén, tipo, referencia y fechas. | No cubre búsqueda por texto de documento ni actor. |
| GET /api/v1/inventory/movements/{id} | Detalle del movimiento y entradas de valoración. | Proponer una respuesta de trazabilidad unificada, sin cargar detalles masivos en listados. |
| POST /api/v1/inventory/adjustments | Ajustes con motivo, validación e idempotencia. | Usar movimientos filtrados para el historial; no crear una segunda tabla de ajustes sin necesidad. |
| GET /api/v1/inventory/transfers y GET /{id} | Listado paginado y detalle; filtro por estado y almacenes. | La UI descarga todas las páginas y faltan búsqueda general/periodo. |
| POST /api/v1/inventory/transfers y acciones dispatch/receive/cancel/reverse | Ciclo idempotente y compensaciones. | Mostrar estados reales y permisos. |
| GET /api/v1/inventory/counts y GET /{id} | Página por almacén/estado y detalle. | El frontend descarga historial completo. |
| POST /api/v1/inventory/counts y acciones post/cancel | Conteos con snapshot y conflicto al contabilizar si el saldo cambió. | UX debe resolver el conflicto, no reintentar ciegamente. |
| GET /api/v1/inventory/reorder-rules | Lista filtrable por almacén/activo. | Sin paginación; servicio carga todas las reglas. |
| GET /api/v1/inventory/replenishment-suggestions | Sugerencias basadas en reglas activas. | Lista completa y consultas por regla; requiere eficiencia y paginación. |
| GET /api/v1/reports/valuation | Paginación, valores por moneda, coste medio y señal de reconciliación. | Requiere reports:financial, por lo que no debe ser obligatorio para la lectura física. |
| GET /api/v1/reports/kardex | Kárdex valorizado por producto, almacén y periodo. | Límite actual de 2.000 movimientos. |
| GET /api/v1/reports/inventory-balance | Balance inicial/final, compras/ventas netas y coste/utilidad según el DTO. | Requiere reports:financial y filtros de periodo/almacén. |
| GET /api/v1/reports/low-stock | Sugerencias filtradas por almacén. | Requiere reports:financial y depende de reglas configuradas. |
| GET /api/v1/audit-events y GET /{id} | Auditoría por actor, acción, recurso, outcome y fechas. | Falta una búsqueda de dominio unificada por producto/almacén/documento. |

### 6.2 Endpoint de resumen

Proponer GET /api/v1/inventory/overview con almacén opcional. Debe devolver conteos calculados por servidor: productos con existencia, productos sin disponibilidad, bajo mínimo, transferencias pendientes, conteos abiertos, cobertura de reglas y salud de reconciliación.

Los importes deben agruparse por moneda y devolverse solo con permiso para costes. Un resumen operativo sin permiso financiero devuelve indicadores de cantidad/estado y omite información monetaria. No calcular las tarjetas descargando todas las páginas de balances.

### 6.3 Búsqueda y DTOs ligeros

Extender de forma compatible GET /inventory/balances con q (nombre/SKU/código) y filtros de categoría/stock cuando la semántica esté definida. Extender GET /inventory/movements con búsqueda de referencia/documento y actor según autorización. Traslados y conteos deberían tener búsqueda/fechas si las necesidades operativas lo requieren.

Los listados deben usar DTOs de resumen y paginación con metadata; el detalle se solicita por separado. Revisar mapeadores de traslados/conteos por posibles consultas N+1 si recorren líneas lazy. Mantener allowlist de sort, tamaño máximo, filtros parametrizados y fechas con inicio inclusivo y fin exclusivo.

Agregar sort estable con desempate por id, para que el movimiento de página no pierda/duplique filas cuando varias comparten timestamp. La UI vuelve a página 0 cuando cambia el filtro y se ajusta si una acción deja una página fuera de rango.

### 6.4 Reposición eficiente

Cambios de backend:
- paginar reglas y sugerencias;
- filtrar por almacén, producto y estado;
- unir reglas activas con saldo actual por consulta/lote y evitar un SELECT por cada regla;
- ordenar sugerencias por brecha respecto al objetivo, mostrando criterio explicable;
- devolver cobertura: reglas existentes vs productos físicos sin regla;
- no crear órdenes automáticamente; devolver datos suficientes para abrir un borrador de compra.

Alinear mínimo/objetivo: el frontend exige targetQuantity mayor que minimumQuantity, pero el servicio solo rechaza cuando es menor. Decidir si la igualdad es válida y hacer coincidir UI, API y tests.

### 6.5 Trazabilidad de movimientos y auditoría

Extender el detalle o añadir una consulta de solo lectura equivalente a GET /api/v1/inventory/movements/{id}/trace para resolver, cuando sea posible:
- documento origen y su número;
- línea origen;
- actor/fecha/correlación;
- movimiento, coste/valoración según permiso;
- eventos de auditoría por recurso/correlación;
- evento de integración, si existe.

La búsqueda por producto/almacén/periodo debe usar consultas indexables, no full scan libre sobre metadata JSON. Los movimientos antiguos cuyo documento no se pueda resolver se mantienen consultables con referenceType/referenceId. Para auditoría crítica se debe guardar contexto de dominio suficiente para evitar depender de un documento mutable.

### 6.6 Reconciliación de existencias y valoración

El informe de valoración existente compara cantidad valorizada con onHand. Hacer esa señal operativa:
- Resumen de consistencia accesible a roles autorizados sin exponer coste;
- detalle financiero para users con reports:financial;
- diferencias por producto/almacén/moneda paginadas;
- vínculo al movimiento, capa y documento que hay que investigar;
- no corregir automáticamente capas ni saldos; requiere una operación explícita y auditable.

El servicio actual impide cambiar la política de valoración cuando ya existen movimientos valorizados; esta regla debe seguir visible en la documentación y no ofrecerse como control editable del frontend.

### 6.7 Outbox y automatización

El Outbox actual es al menos una vez y el consumidor debe deduplicar por eventId. Traslados y varios flujos comerciales ya escriben eventos. En InventoryService y InventoryCountService no se observa una escritura Outbox para ajuste manual y contabilización de conteo.

Añadir eventos transaccionales cuando corresponda: ajuste contabilizado, conteo posteado con sus movimientos, reserva/liberación si aún falta esa fuente, y estados relevantes de movimientos. El payload necesita eventId, eventType/schemaVersion, occurredAt, aggregate, producto/almacén, delta/unidad, documento origen y correlación; no guardar secretos ni datos personales innecesarios.

El evento se escribe en la misma transacción que el cambio físico. Un consumidor puede recibirlo más de una vez y debe deduplicar. n8n no escribe stock directamente; si una notificación falla, el movimiento interno permanece confirmado y la entrega se reintenta de forma aislada.

### 6.8 Matriz de permisos

| Permiso | Acceso recomendado |
|---|---|
| inventory:read | Resumen no financiero, saldos, disponibilidad informativa y listados de operaciones en modo lectura. |
| inventory:manage | Crear/ejecutar ajustes, conteos, traslados y reglas de reposición. |
| reports:financial | Costes, valoración monetaria, kárdex valorizado y reportes financieros actuales. |
| audit:read | Historia de auditoría y referencias de cambios; coste condicionado a permiso financiero. |

Estado actual a corregir: el menú revela Inventario con inventory:read, pero varios GET de movimientos, traslados, conteos y reposición exigen inventory:manage o audit:read. Hay que decidir y alinear la política en backend y frontend. Ningún 403 se traduce a “no hay registros”, y ninguna pantalla muestra mutaciones a un usuario de solo lectura.

## 7. Escenarios importantes que deben quedar cubiertos

| Escenario | Resultado esperado |
|---|---|
| Miles de productos | Tabla pagina desde backend y resumen agregado; no se descargan todas las páginas. |
| Diferentes unidades | Se muestran las cantidades por SKU/unidad; no hay suma global engañosa. |
| Monedas distintas | Valoración se agrupa por moneda. |
| Usuario solo lector | Ve cantidades autorizadas, no ve acciones mutables y no recibe 403 inesperado en los GET que debería poder usar. |
| Error de red/5xx | Se conserva el contexto y se muestra reintentar; no vaciar datos fingiendo ausencia. |
| Cambio rápido de filtro | Las respuestas antiguas no pisan el resultado más reciente. |
| Stock físico pero disponible cero | Mostrar onHand y reserved separados, explicando por qué no se puede comprometer más stock. |
| Venta/traslado/ajuste concurrente | El servidor serializa o rechaza el comando sin duplicar movimiento ni permitir saldo negativo. |
| Doble reintento | La misma operación devuelve el mismo resultado idempotente; payload distinto con misma clave devuelve conflicto. |
| Recepción parcial | Stock solo cambia por la cantidad recepcionada; documento y movimiento se enlazan. |
| Pedido WhatsApp pendiente/vencido | Reserva y liberación son estados distintos de salida física. |
| Suscripción a servicio | No modifica stock físico por emitir factura. |
| Entrega física de suscripción futura | Reservar/consumir solo mediante flujo explícito de fulfillment idempotente. |
| Traslado en tránsito | Origen disminuido, destino todavía no incrementado y estado visible. |
| Reversión de traslado | Motivo, movimiento compensatorio y validación de saldo destino. |
| Conteo obsoleto | Backend devuelve conflicto; UI identifica línea, conserva documento y guía a recontar. |
| Conteo sin diferencias | Se registra documento, sin generar movimientos falsos de cantidad cero. |
| Producto sin regla de reposición | Estado “sin regla”, nunca “sin alertas, todo correcto”. |
| Ajuste manual | Motivo, usuario, referencia, cantidad y valoración apropiados quedan trazables. |
| Diferencia de valoración | Se muestra señal de discrepancia y ruta de investigación; no se autorrepara con coste de catálogo. |
| Referencia antigua no resoluble | Movimiento sigue visible con referencia técnica. |
| n8n/Kafka/IA temporalmente caídos | Operaciones internas continúan; el evento se reintenta sin duplicación. |
| Móvil/teclado | Acciones alcanzables, foco y estados anunciados, sin scroll horizontal obligatorio. |

## 8. Roadmap recomendado

### Fase 0 — Errores semánticos, carga y permisos
1. Eliminar la suma de cantidades heterogéneas.
2. Subir operaciones a nivel principal del módulo.
3. Diferenciar carga, error, permiso y lista vacía.
4. Alinear RBAC y pasar permisos a los componentes.
5. Añadir skeletons por vista y activar correctamente loading.
6. Evitar que el refresh global cargue todas las áreas.
7. Alinear regla de mínimo/objetivo y cubrirla con tests.

### Fase 1 — Arquitectura visual y paginación
1. Implementar Resumen, Existencias, Movimientos/Kárdex, Operaciones y Auditoría.
2. Paginación real en saldos, movimientos, traslados, conteos y ajustes.
3. Historial de ajustes desde movimientos.
4. Filtros y URL restaurable.
5. DTOs de listado ligeros y detalle separado.
6. Tarjetas responsive, acciones contextuales, empty/error/forbidden y skeleton.

### Fase 2 — APIs eficientes
1. Endpoint agregado de overview operativo.
2. Búsqueda por texto/filtros del servidor.
3. Paginación de reglas/sugerencias y cálculo por lote.
4. Rastro de documento desde movimiento.
5. Reconciliación por permisos.
6. Medir latencia, número de SELECT, volumen de respuesta y planes SQL antes/después.

### Fase 3 — Auditoría y eventos
1. Unificar el rastro movimiento-documento-auditoría.
2. Emitir Outbox en ajuste y conteo contabilizado y revisar todos los flujos físicos.
3. Contratos con versión, deduplicación y reintentos para n8n.
4. Panel de incidencias de conciliación.
5. Alertas configurables y medibles.

### Fase 4 — Funciones avanzadas, solo si se necesitan
Conteo cíclico, previsión de demanda, lotes/caducidad/series, multiple bins, cuarentena/merma, fulfillment físico de suscripciones y picking. Requieren decisiones de dominio y migraciones, no son simples cambios visuales.

## 9. Pruebas y criterios de aceptación

### Frontend
- Skeleton, empty, filtered-empty, error, forbidden y retry por tab.
- Primera/última página, tamaños 10/20/50, filtros que reinician página y URL que restaura estado.
- Solicitudes que responden fuera de orden; solo mostrar la respuesta de la consulta más reciente.
- Responsive en 360 px, 768 px y escritorio, foco de teclado, tabs, stepper y confirmación accesible.
- Permisos lector/gestor/auditor/financiero; controles y datos sensibles correctos.
- No sumar unidades heterogéneas ni mostrar coste con moneda incorrecta.
- Ajustes: motivo, cantidades, coste de entrada, salida valorada por política e idempotencia.
- Traslado: crear, despachar, tránsito, recibir, cancelar y reversión.
- Conteo: cero, decimales, diferencias, no diferencia, conflicto del snapshot.
- Reposición: bajo mínimo, sin regla, regla inactiva, proveedor inactivo y objetivo en el límite.
- No convertir 401/403/409/422 a estado vacío.

### Backend
- RBAC consistente para saldos, movimientos, operaciones, auditoría y coste.
- Concurrencia de venta/reserva/ajuste/transferencia y ausencia de saldo negativo.
- Idempotencia del mismo payload y conflicto por clave reutilizada con payload distinto.
- Conteo obsoleto no contabiliza movimientos parciales.
- Sugerencias y resumen correctos para bajo mínimo, cero disponible, varias unidades/monedas y producto sin regla.
- Paginación/ordenación permitida y filtros combinados de almacén/producto/fechas.
- DTOs de listado sin detalle innecesario y número de SELECT por página medido.
- Reconciliación entre saldo, capas y entradas de valoración.
- Outbox se confirma solo con el movimiento transaccional; consumidor deduplica.
- Auditoría de éxito, denegación, conflicto y reversión.
- Regresión de FIFO/promedio en recepción, venta, retorno y transferencia.

### Integración end-to-end
1. Crear producto stockeable.
2. Crear/aprobar compra sin cambiar stock.
3. Recibir parcialmente y comprobar saldo/valoración.
4. Completar recepción y verificar kárdex.
5. Vender/reservar y verificar disponible.
6. Liberar reserva o cancelar conforme al estado sin duplicar stock.
7. Registrar devolución y verificar movimiento/valoración.
8. Transferir entre almacenes, comprobar la etapa en tránsito y la recepción.
9. Contar y contabilizar una diferencia con auditoría y Outbox.
10. Seguir del documento origen a movimiento, coste y auditoría.
11. Detener n8n/relay temporalmente, confirmar que Inventary mantiene consistencia y validar deduplicación al restaurar.

### Definition of Done
- Operaciones no quedan al final de un scroll largo.
- Cinco vistas de primer nivel con sus estados y filtros.
- Ningún listado descarga el histórico completo para renderizar una página.
- No se mezclan unidades de medida ni monedas sin semántica.
- Cantidad física, reservada y disponible siempre son diferenciables.
- Cada movimiento tiene documento/ref, actor y hora; coste protegido por permisos.
- Usuarios de solo lectura no ven mutaciones ni reciben 403 engañosos.
- Ajustes y conteos tienen motivos/controles/idem y resolución de conflictos guiada.
- Compras, ventas, WhatsApp, transferencias y conteos siguen un único saldo de verdad.
- Eventos downstream no mutan inventario y no duplican efectos.
- Tests de API, UI, RBAC, concurrencia, valoración y accesibilidad pasan.
- Lo no implementado (bins, lotes, devoluciones financieras, fulfillment físico de suscripción) no se anuncia como existente.

## 10. Métricas

Medir antes y después, con datos representativos:
- tiempo a primera vista útil p50/p95;
- latencia de overview y de la primera página;
- peticiones y bytes al entrar a Inventario;
- SELECT por página y latencia de balances/movimientos/sugerencias;
- porcentaje de 403 inesperados en vistas de lectura;
- conflictos por cantidad/estado, doble envío y movimientos duplicados;
- cantidad y antigüedad de conteos abiertos;
- cobertura de reglas de reposición y sugerencias accionadas;
- diferencias de valoración pendientes y tiempo de investigación;
- tiempo para llegar desde un documento al movimiento origen;
- eventos Outbox fallidos/pendientes y latencia de notificación.

Objetivos iniciales sugeridos, no resultados medidos: primera tabla visible en menos de 1,5 s p95 en un escenario de carga representativo; una consulta paginada por lista más el overview necesario; cero descargas del historial completo para pintar una tabla; cero duplicación de movimientos tras reintentos idempotentes.

## 11. Documentos relacionados

Esta propuesta complementa y debe mantenerse alineada con:

- [UX/UI de Productos V2](PROPUESTA_UX_PRODUCTOS_V2.md)
- [UX/UI de Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Mejora integral de Suscripciones](PROPUESTA_UX_SUSCRIPCIONES.md)
- [UX/UI de Pedidos WhatsApp V2](PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md)
- [Logística de retiro y envíos](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)
- [Motor comercial inteligente](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)
- [Arquitectura n8n + backend comercial V2](PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md)
- [Propuesta integral de Compras](PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md)
- [Referencia de API](microservices-inventary-ecuapp/docs/25-referencia-apis.md)
- [Arquitectura y patrones actuales](microservices-inventary-ecuapp/docs/26-arquitectura-y-patrones-actuales.md)
- [Guía de consumo de APIs](microservices-inventary-ecuapp/docs/27-guia-consumo-apis.md)
- [Pendientes de balance y valoración](microservices-inventary-ecuapp/PENDIENTES.md)
- [Auditoría técnica pendiente](AUDITORIA_TECNICA_PENDIENTES.md)

## 12. Código inspeccionado

Frontend:
- app-inventary-ecuapp/src/App.jsx
- app-inventary-ecuapp/src/api.js
- app-inventary-ecuapp/src/features/inventory/InventoryOperations.jsx
- app-inventary-ecuapp/src/ui/Skeleton.jsx
- app-inventary-ecuapp/src/components/DataTable.jsx

Backend:
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/StockBalance.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/StockMovement.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryCostingService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryCountController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryCountService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/ReorderController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/ReorderService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/transfers/StockTransferController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/transfers/StockTransferService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/InventoryBalanceReportController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/OperationalReportService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/audit/AuditController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/audit/AuditService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxEvent.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxEventRepository.java

**Límite de esta entrega:** solo se crea este documento de propuesta en Markdown. No se modifican componentes, servicios, endpoints, permisos, migraciones de base de datos ni workflows n8n; la implementación posterior debe ejecutar y documentar las pruebas descritas.
