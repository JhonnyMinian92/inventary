# Propuesta de mejora integral del módulo Compras — Frontend y Backend

**Versión:** 2.0  
**Fecha de revisión:** 28 de octubre de 2026  
**Rama revisada:** master  
**Estado:** propuesta técnica pendiente de implementación  
**Alcance:** pantalla de Compras, órdenes de compra, recepciones de mercancía y devoluciones a proveedores.

> Esta revisión documenta cambios recomendados; no modifica todavía componentes, endpoints, migraciones ni datos de negocio. Los riesgos de consultas N+1 e índices se deducen de los patrones de código y deben confirmarse con métricas y planes de ejecución antes de optimizar la base de datos.

## 1. Objetivo

Transformar la pantalla actual en un centro de trabajo de compras que permita localizar documentos rápidamente, entender qué requiere atención y ejecutar cada operación con seguridad. Debe funcionar bien con pocos registros y con decenas de miles, mostrar estados de carga y error inequívocos, respetar permisos y no confundir el valor de una orden con facturas o pagos al proveedor.

La propuesta conserva las reglas transaccionales que ya existen en Spring Boot y lleva la paginación, filtros y estados al servidor. No propone descargar todo el historial para luego filtrarlo en el navegador.

## 2. Diagnóstico del código actual

### 2.1 Frontend

| Hallazgo comprobado | Evidencia en el código | Efecto / prioridad |
|---|---|---|
| La carga global solicita todas las páginas de órdenes, con tamaño 100, mediante listAllPages. | app-inventary-ecuapp/src/App.jsx, loadData; src/api.js, listAllPages | Alto: carga y memoria crecen con el histórico y Compras espera a que terminen peticiones de otros módulos. |
| Las órdenes y las devoluciones aparecen una debajo de otra en el mismo panel. No existe una pestaña de recepciones. | App.jsx renderiza PurchaseTable y PurchaseReturns; src/features/purchasing/ | Alto: procesos distintos compiten por atención y las recepciones son difíciles de consultar. |
| La carga de compras no tiene un error independiente visible. El manejador global agrega a su lista principal fallos de productos, categorías, almacenes y saldos, pero no el fallo del listado purchases. | App.jsx, loadData | Alto: un fallo de GET /purchase-orders puede terminar representado como “no hay órdenes”. |
| La vista de devoluciones vuelve a descargar todas las devoluciones y recepciones; además consulta todas las devoluciones de una recepción para calcular cantidades ya devueltas. | PurchaseReturns.jsx, refresh y efecto de receiptId | Alto: tráfico, latencia y coste de memoria innecesarios. La validación final del servidor debe seguir siendo la autoridad. |
| El estado vacío de devoluciones usa texto de carga y no un skeleton específico de tabla; no hay paginación visual. | PurchaseReturns.jsx | Medio/alto: una consulta lenta no tiene una representación consistente con el resto de la aplicación. |
| Las acciones de filas se deciden por el estado de la orden, no por los permisos del usuario. | PurchaseTable.jsx no recibe can() ni un mapa de permisos | Alto: un usuario puede ver “Aprobar”, “Recibir” o “Cancelar” y recibir un 403 al intentar la acción. El backend sí aplica autorización. |
| Las acciones de fila usan el loading general, por lo que una operación puede bloquear controles de otras filas/módulos. | PurchaseTable.jsx y estado global loading de App.jsx | Medio: feedback poco preciso; la interfaz no indica qué orden está procesándose. |
| La pantalla no expone los filtros y la ordenación que ya acepta la API. | PurchaseTable.jsx solo recibe rows y callbacks | Alto: buscar un documento en el histórico se vuelve manual. |
| Los importes de los formularios de compra y devolución se formatean explícitamente como USD. | PurchaseForm.jsx y PurchaseReturns.jsx | Medio: validar que la moneda mostrada corresponda siempre con la moneda real del documento; no permitir que un símbolo incorrecto induzca a error. |
| La página de Compras depende de hasLoadedData, que solo se completa después de la carga global de la aplicación. | App.jsx | Medio/alto: el skeleton inicial no representa la carga propia de la pestaña ni permite recuperarse de forma independiente. |

El skeleton global existente es útil como fallback inicial, pero no sustituye skeletons por pestaña, errores de carga específicos, estados vacíos filtrados ni paginación real.

### 2.2 Backend actual: capacidades que deben reutilizarse

Los endpoints privados están paginados y protegidos por permisos. El tamaño normal predeterminado es 20 y el máximo documentado es 100.

| Recurso | Endpoint actual | Filtros actuales | Ordenación permitida en servicio |
|---|---|---|---|
| Órdenes | GET /api/v1/purchase-orders | status, supplierId, warehouseId, from, to | orderedAt, orderNumber, status, total |
| Detalle de orden | GET /api/v1/purchase-orders/{id} | Por ID | No aplica |
| Recepciones | GET /api/v1/goods-receipts | purchaseOrderId, warehouseId, from, to | receivedAt, receiptNumber |
| Detalle de recepción | GET /api/v1/goods-receipts/{id} | Por ID | No aplica |
| Devoluciones | GET /api/v1/purchase-returns | goodsReceiptId, warehouseId, from, to | createdAt, returnNumber |
| Detalle de devolución | GET /api/v1/purchase-returns/{id} | Por ID | No aplica |

Los listados actuales devuelven DTOs con sus líneas de productos. Sin embargo, las consultas de repositorio que construyen esas páginas cargan explícitamente asociaciones de cabecera, pero no todas las líneas que luego recorren los mapeadores DTO. Ese patrón puede provocar consultas lazy adicionales por documento (riesgo N+1) y respuestas más grandes de lo que necesitan las tablas. No está cuantificado mediante profiler; se debe medir antes y después.

Los filtros por texto libre no existen en estos listados. Tampoco hay una API agregada de indicadores por estado. Estos son huecos reales si se quiere búsqueda, contadores globales y una UX paginada sin descargar cada documento.

### 2.3 Reglas de negocio actuales que la UX debe respetar

Las órdenes tienen estos estados:

- DRAFT: borrador.
- SUBMITTED: enviada para aprobación.
- APPROVED: aprobada, pendiente de recibir.
- PARTIALLY_RECEIVED: recepción parcial; conserva cantidades pendientes.
- RECEIVED: recibida por completo.
- CANCELLED: cancelada.

La transición normal es DRAFT → SUBMITTED → APPROVED → PARTIALLY_RECEIVED → RECEIVED. Una orden aprobada sin recepciones todavía puede cancelarse; después de una recepción parcial o completa, la cancelación está bloqueada. La aprobación puede exigir un usuario distinto del creador y del remitente (la configuración predeterminada exige separación). El servicio hace los controles aunque un frontend obsoleto todavía muestre una acción.

La recepción registra únicamente cantidades positivas recibidas, permite recepciones parciales, no permite superar el pendiente y actualiza inventario, valoración, movimiento, auditoría y Outbox en una transacción. La creación usa Idempotency-Key. El coste del producto y la valoración de inventario cambian al contabilizar la recepción, no al crear el borrador.

Las devoluciones se crean desde una recepción contabilizada. El backend controla que la cantidad acumulada no supere la recibida y sincroniza la devolución para evitar sobredevoluciones concurrentes. La operación disminuye existencias y registra valoración, movimiento, auditoría y evento Outbox. La pantalla no debe asumir que todo lo recibido sigue disponible físicamente: puede haberse vendido o trasladado desde entonces.

**Límite contable importante:** el flujo actual de devolución registra la salida del inventario; no representa por sí solo una nota de crédito, reembolso, factura o saldo por pagar al proveedor. La UI no debe afirmar que el dinero fue devuelto ni que la cuenta del proveedor quedó conciliada.

## 3. Experiencia objetivo

### 3.1 Encabezado y acciones

Encabezado recomendado:

- Título: **Compras**.
- Texto auxiliar: “Gestiona órdenes, registra lo recibido y consulta devoluciones a proveedores”.
- Acción primaria condicionada a purchasing:manage: **Nueva compra**.
- Acción secundaria de actualización limitada a la pestaña activa, con fecha/hora de última actualización y estado accesible.
- Si el usuario no tiene permiso para leer Compras, no debe acceder al módulo. Si puede leer pero no administrar, conserva las consultas y no ve acciones de modificación.

La acción “Nueva devolución” debe aparecer únicamente en la pestaña de devoluciones, y “Registrar recepción” debe estar ligada a una orden elegible. No colocar dos botones primarios desconectados al mismo nivel.

### 3.2 Pestañas de primer nivel

Usar pestañas accesibles de teclado con rol tablist, tab y tabpanel, estado seleccionado anunciado y foco visible:

1. **Órdenes de compra**
2. **Recepciones**
3. **Devoluciones a proveedores**

Cada pestaña mantiene sus propios filtros, resultados, paginación, skeleton y errores. La selección debe reflejarse en la URL (por ejemplo, query param tab) para que refrescar, compartir el enlace o volver atrás no pierda el contexto.

Puede haber contadores en las pestañas, siempre que provengan de agregados del backend. No calcular el total global con la página actual.

### 3.3 Indicadores útiles y honestos

En un bloque compacto, y solo si la consulta de resumen está disponible:

- Órdenes pendientes de aprobación.
- Órdenes aprobadas o parcialmente recibidas con unidades pendientes.
- Recepciones registradas durante el período elegido.
- Devoluciones registradas durante el período elegido.

Los contadores deben respetar filtros de período, proveedor y almacén cuando se apliquen. No mostrar “deuda al proveedor”, “ahorro” o “dinero devuelto” porque las APIs actuales no sustentan esas afirmaciones. Una suma de órdenes es valor planificado de compra, no necesariamente factura ni pago efectuado.

### 3.4 Pestaña Órdenes de compra

Barra de filtros:

- Búsqueda por número de orden y proveedor; el backend debe ofrecer q para no filtrar solo la página visible.
- Estado, con opción “Todos”.
- Proveedor.
- Almacén de destino.
- Fecha desde / hasta.
- Limpiar filtros y mostrar los filtros activos.
- Ordenar por fecha, número, estado o total según la lista permitida por la API.

Columnas sugeridas:

| Columna | Contenido |
|---|---|
| Documento | Número de OC; al abrir, mostrar detalle completo. |
| Estado | Etiqueta legible, consistente y con texto además del color. |
| Proveedor | Nombre, con código si ayuda a distinguir homónimos. |
| Almacén | Código y/o nombre. |
| Recepción | Unidades recibidas / ordenadas o porcentaje, y unidades pendientes. |
| Total | Importe con la moneda real del documento. |
| Fecha | Fecha local y, si procede, fecha de última actualización. |
| Acciones | Solo acción principal elegible y menú “Más”. |

No saturar cada fila con cinco botones. Priorizar según estado:

| Estado | Acción primaria | Acciones secundarias |
|---|---|---|
| DRAFT | Enviar a aprobación | Ver detalle; cancelar. Proponer editar mientras siga en borrador si se implementa el endpoint correspondiente. |
| SUBMITTED | Aprobar, solo con purchasing:approve | Ver detalle; cancelar, solo con purchasing:manage. El backend puede rechazar la autoaprobación. |
| APPROVED | Registrar recepción | Ver detalle; cancelar si aún no hay recepciones y el usuario tiene permiso. |
| PARTIALLY_RECEIVED | Registrar recepción pendiente | Ver detalle. No ofrecer cancelar. |
| RECEIVED | Ver detalle | Enlace a recepciones asociadas y, cuando aplique, nueva devolución. |
| CANCELLED | Ver detalle | Sin acciones de mutación. |

Las acciones requieren confirmación explícita cuando son destructivas o contabilizan inventario. El cuadro de confirmación debe indicar el documento, el impacto y si la operación es irreversible. El botón afectado presenta “Procesando…”; el resto de filas no debe bloquearse sin motivo.

### 3.5 Pestaña Recepciones

Debe permitir auditar qué mercancía ingresó al inventario sin abrir cada orden.

Columnas sugeridas: número de recepción, orden de compra, proveedor, almacén, fecha, líneas/unidades, total valorizado y acción Ver detalle.

Filtros: búsqueda por número de recepción u orden, proveedor, almacén y período. Cada recepción abre un detalle que incluya cantidades recibidas por línea, costo unitario registrado, unidad, total y documento de origen.

El endpoint actual permite filtrar por purchaseOrderId, warehouseId, from y to; para búsqueda general y proveedor se propone ampliar el contrato. No presentar “anular recepción” hasta diseñar un flujo backend de reversión compensatoria que conserve el historial de inventario.

### 3.6 Pestaña Devoluciones a proveedores

Columnas sugeridas: número de devolución, orden, recepción, proveedor, almacén, fecha, motivo, unidades/total al costo de inventario, estado y acción Ver detalle.

Filtros: búsqueda, proveedor, orden, almacén, período y estado cuando el modelo permita más de un estado operacional. El botón **Nueva devolución** vive aquí.

El detalle debe distinguir:
- cantidad originalmente recibida;
- cantidad ya devuelta;
- cantidad que todavía se puede devolver según las reglas;
- existencias actuales relevantes para ejecutar la salida;
- cantidad solicitada y total al coste contabilizado.

No afirmar que el total representa dinero reembolsado. Si la empresa necesita gestionar crédito o reembolso del proveedor, debe diseñarse como un flujo financiero separado.

### 3.7 Paginación y estado de URL

- Página inicial 0, tamaño por defecto 20; opciones 10, 20 y 50. Mantener el máximo del backend.
- Texto “Mostrando 21–40 de 138”, anterior/siguiente y números de página cuando sean razonables.
- Cambiar de filtro o texto de búsqueda vuelve a la página 0.
- Conservar pestaña, filtros, orden y tamaño en la URL; el botón Atrás debe restaurar el contexto.
- Si una operación reduce los resultados y deja la página fuera de rango, volver a la última página válida.
- No aplicar paginación en memoria después de descargar todo el historial. La API debe devolver content y metadatos de la página.
- Para búsquedas, introducir debounce de 300–400 ms o búsqueda al enviar el formulario, cancelar solicitudes obsoletas o ignorar sus respuestas tardías.

### 3.8 Skeletons, carga y errores

Cada pestaña debe tener estados independientes:

1. **Primera carga:** skeleton de la estructura real: filtros, filas, columna de acciones e indicadores. Evitar desplazar toda la página al terminar de cargar.
2. **Cambio de página/filtro:** mantener el contexto visible y mostrar skeleton ligero dentro de la tabla o un indicador de actualización. No borrar los resultados hasta saber si la nueva consulta se completó.
3. **Actualización manual:** bloquear solo la acción de refresh; indicar “Actualizando…” con aria-busy.
4. **Sin registros:** empty state específico con siguiente acción según permisos.
5. **Sin coincidencias por filtro:** explicar que los filtros no devolvieron resultados y ofrecer “Limpiar filtros”.
6. **Error de red/servidor:** mensaje visible dentro de la pestaña, botón Reintentar y conservación de filtros.
7. **401:** renovar/reiniciar sesión conforme al mecanismo actual.
8. **403:** “No tienes permiso para consultar/ejecutar esta acción”; nunca mostrarlo como lista vacía.
9. **409 por estado cambiado/concurrencia:** refrescar el documento y explicar qué cambió, sin perder los datos del formulario que aún puedan reutilizarse.
10. **422 por regla de negocio:** asociar el mensaje a la cantidad/línea o al campo correspondiente.
11. **Error después de timeout de una creación:** conservar la clave de idempotencia de esa operación al reintentar el mismo payload; generar una clave nueva si el usuario modifica la operación.

No renderizar el mensaje de “no hay órdenes” cuando la solicitud falló. Los toasts sirven para comunicar el resultado, pero no reemplazan el estado de error persistente de la pestaña.

### 3.9 Responsive, accesibilidad y prevención de errores

- Escritorio: tabla densa pero legible, anchuras controladas y encabezado de columnas estable.
- Móvil/tablet: tarjeta por documento con número, estado, proveedor, pendiente y acción principal; el resto se abre en detalle. No depender de un scroll horizontal largo para realizar tareas críticas.
- Contraste de estado y foco conforme a WCAG; color nunca como único indicador.
- Etiquetas visibles para filtros; errores conectados al campo; aria-live para cambios de página y acciones.
- No perder datos al cambiar de pestaña o salir accidentalmente de un formulario. Mantener el wizard de compra/recepción/devolución como página guiada, no convertir formularios extensos en modales.
- Deshabilitar doble envío mientras una operación está en curso, sin confiar únicamente en el estado del botón.
- Mostrar moneda, unidades decimales admitidas (hasta tres decimales en cantidades) y reglas de redondeo antes de confirmar.

## 4. Cambios recomendados de Backend

### 4.1 Aprovechar la paginación existente

La primera mejora no requiere un endpoint nuevo: el frontend debe llamar directamente a los listados actuales con page, size, sort y filtros. Retirar las llamadas a listAllPages para órdenes, recepciones y devoluciones en las pantallas de Compras.

La navegación paginada debe seguir siendo compatible con el formato de página que el backend ya devuelve. No crear otro formato de paginación solo para este módulo.

### 4.2 Introducir DTOs ligeros para listados

Separar el DTO de listado del DTO de detalle:

- **PurchaseOrderSummary:** id, orderNumber, status, supplierId/name, warehouseId/code, currency, total, lineCount, orderedAt, submittedAt, approvedAt, unidades ordenadas/recibidas/pendientes. No incluir items completos.
- **GoodsReceiptSummary:** id, receiptNumber, purchaseOrderId/number, supplier, warehouse, receivedAt, lineCount, total y unidades recibidas. No incluir items completos.
- **PurchaseReturnSummary:** id, returnNumber, status, purchaseOrderId/number, receiptId/number, warehouse, motivo resumido, createdAt, total y unidades devueltas. No incluir items completos.

Los endpoints de detalle existentes seguirán devolviendo líneas completas. Implementar los resúmenes mediante proyecciones, consultas agregadas o mapeos que eviten acceder en bucle a colecciones lazy. Medir queries por página y tamaño de respuesta para comprobar que el riesgo N+1 queda resuelto.

### 4.3 Búsqueda por texto y filtros

Ampliaciones propuestas, conservando los filtros existentes:

- Órdenes: añadir q para número de orden, nombre/código de proveedor y, si la experiencia lo justifica, SKU/nombre de líneas. Acordar el alcance de la búsqueda antes de implementarla.
- Recepciones: añadir q para número de recepción u orden y supplierId.
- Devoluciones: añadir q para número de devolución, orden o recepción y supplierId/purchaseOrderId.
- Mantener parámetros de fecha ISO-8601, validar from anterior a to y documentar si to es exclusivo.
- Permitir ordenar solo por propiedades de una allowlist; rechazar sort inválido con el error estable ya usado por la API.
- Escapar o tratar el texto de búsqueda como dato parametrizado; no concatenar valores en consultas SQL/JPQL.

La búsqueda por texto debe tener un límite y una estrategia de índice apropiada para el motor real. No usar un filtro local sobre una página incompleta.

### 4.4 Endpoint de resumen agregado

Propuesta: GET /api/v1/purchasing/summary?from=&to=&supplierId=&warehouseId=.

Debe devolver conteos por estado, órdenes pendientes de aprobación, órdenes con pendiente de recepción, recepciones y devoluciones del período. Los parámetros de filtro deben tener la misma semántica que las pestañas.

- Ejecutar agregados en SQL/JPQL; no cargar las entidades para contar.
- Definir si “del período” filtra por fecha de orden, recepción o devolución según cada indicador.
- No sumar conceptos de distinta moneda como si fueran una sola cantidad.
- Mantener el permiso purchasing:read.
- Si el tráfico no justifica un endpoint agregado en la primera fase, posponer las tarjetas KPI; no calcularlas descargando todo el histórico.

### 4.5 Elegibilidad de devolución y stock actual

Hoy la UI calcula lo pendiente de devolver descargando el historial de devoluciones y restándolo de la cantidad recibida. Esto aumenta el tráfico y puede ofrecer una cantidad que el usuario ya no tiene físicamente en el almacén.

Propuesta: añadir un endpoint de consulta por recepción (por ejemplo, GET /api/v1/goods-receipts/{id}/return-eligibility) que devuelva por línea cantidad recibida, acumulado previamente devuelto, pendiente por devolver y el máximo actualmente procesable teniendo en cuenta las existencias. La operación POST de devolución debe volver a comprobar todo de forma transaccional y devolver un conflicto/regla de negocio identificable si el stock cambió entre la consulta y la confirmación.

La consulta de elegibilidad es informativa, no una reserva. No confiar en la cantidad enviada por el navegador; el servicio sigue siendo autoridad. Si no hay stock suficiente porque parte se vendió o trasladó, la interfaz debe explicar el límite y no fingir que el proveedor ya fue reembolsado.

### 4.6 Edición de borradores y reversión

- **Edición de DRAFT:** actualmente existe creación de borrador y transición de estado, pero no se observa endpoint de actualización de orden. Proponer PUT /api/v1/purchase-orders/{id} solo si el negocio desea corregir una OC sin cancelarla y crear otra. Debe bloquear cualquier edición posterior a SUBMITTED, validar proveedor/almacén/productos/moneda, recalcular importes, registrar auditoría e impedir cambios concurrentes.
- **Cancelación de órdenes:** conservar las reglas actuales; no habilitar cancelar después de una recepción parcial o completa.
- **Reversión de recepción o devolución:** no añadir un botón que solo cambie el estado de la cabecera. Si el negocio lo necesita, debe ser una transacción compensatoria, con motivo obligatorio, permiso específico, movimiento de inventario y coste inverso, idempotencia, auditoría y restricciones por operaciones posteriores.
- **Finanzas de proveedor:** facturas, cuentas por pagar, notas de crédito y reembolsos no están cubiertos por el flujo actual. Mantenerlos fuera de esta mejora visual salvo que se apruebe explícitamente el alcance de un módulo financiero.

### 4.7 Índices y rendimiento SQL

La migración actual ya define, entre otros, índices para proveedor/estado/fecha de órdenes, líneas por orden, recepciones por orden/fecha, devoluciones por recepción/fecha y líneas de devolución por línea de recepción.

La nueva UX incrementará consultas por status, almacén y fecha, así que hay que revisar planes con datos representativos. Evaluar —solo después de medir— índices compuestos para orden por almacén/estado/fecha, recepciones por almacén/fecha y devoluciones por fecha/almacén. Considerar selectividad, orden de columnas, coste de escritura y consultas existentes; no crear índices indiscriminadamente. Registrar el resultado de EXPLAIN ANALYZE antes y después.

### 4.8 Concurrencia, idempotencia y contratos de error

Conservar los controles ya implementados: clave de idempotencia en creación de orden, recepción y devolución; bloqueo transaccional de órdenes; control de cantidad pendiente; segregación de aprobación; ledger de coste; auditoría y Outbox.

El frontend debe interpretar de forma diferenciada 401, 403, 404, 409, 422 y errores transitorios. No mostrar mensajes técnicos o SQL. Los errores deben tener un código estable, mensaje para usuario y, cuando corresponda, identificador de petición. En recepciones y devoluciones concurrentes, el backend vuelve a validar cantidades y stock con bloqueo dentro de la transacción.

## 5. Matriz de escenarios que deben quedar cubiertos

| Escenario | Comportamiento esperado en frontend | Garantía del backend / trabajo necesario |
|---|---|---|
| Primera entrada a Compras | Skeleton de pestaña y tabla; la navegación no espera a que carguen todas las áreas de la aplicación. | Endpoint paginado existente. |
| Cero órdenes registradas | Empty state específico y CTA Nueva compra solo para quien pueda administrar. | Página vacía válida, no error. |
| Cero resultados por filtros | Explicar que no hubo coincidencias y ofrecer limpiar filtros. | El servidor devuelve content vacío y totalElements 0. |
| API fuera de línea o 5xx | Error visible y Reintentar; conservar filtros y resultados previos cuando existan. | No convertir error en lista vacía. |
| Usuario sin permiso de lectura | No mostrar navegación al módulo y manejar 403 si se accede por URL. | purchasing:read obligatorio. |
| Usuario solo lector | Puede consultar y paginar; no ve botones de mutación. | Endpoints de mutación siguen protegidos. |
| Usuario sin permiso para aprobar | No ve Aprobar; si se intenta desde un cliente no autorizado, recibe explicación. | purchasing:approve y segregación de funciones. |
| Orden DRAFT | Ver detalle, enviar y cancelar según permiso. Edición solo tras implementar el endpoint. | Solo permite enviar/cancelar según estado. |
| Orden SUBMITTED | Mostrar pendiente de aprobación; no dejar que quien creó/envió se autoapruebe. | Backend valida el usuario aprobador. |
| Orden APPROVED | Mostrar unidades pendientes y CTA Recibir. | Recepción permitida solo en órdenes aprobadas. |
| Recepción parcial | Actualizar progreso y conservar lo pendiente. | Cantidades acumuladas; no superar lo ordenado. |
| Recepción completa | Estado RECEIVED; quitar CTA de recepción. | Estado recalculado por el servicio. |
| Línea de recepción en cero | No se envía esa línea; explicar que se registra solo lo recibido. | El POST exige al menos una línea positiva. |
| Entrega mayor a la pendiente | Error por producto y conservar el formulario para corregir. | Backend rechaza excedente. |
| Producto/almacén inactivo | Explicar por qué no puede continuarse y sugerir corregir el documento de origen si aplica. | Backend vuelve a validar antes de contabilizar. |
| Dos usuarios operan la misma orden | Si la operación quedó obsoleta, refrescar estado, informar y no duplicar movimientos. | Bloqueo e idempotencia existentes. |
| Doble clic o timeout al crear | Botón ocupado y reintento seguro de la misma operación. | Mantener la misma clave para el mismo payload. |
| Orden cancelada mientras se ve en pantalla | No insistir con una acción obsoleta; refrescar fila y explicar el estado actual. | Transición inválida rechazada. |
| Orden parcialmente recibida intenta cancelarse | Acción ausente; detalle explica que ya hubo recepción. | Backend bloquea la cancelación. |
| Recepción sin líneas elegibles para devolver | No permitir iniciar o continuar; explicar la razón. | El servidor valida recepción contabilizada. |
| Devolución parcial de una recepción | Permitir devolver solo cantidades válidas y mostrar saldo restante. | No exceder la cantidad recibida acumulada. |
| Intento de devolver lo ya devuelto | Marcar cantidad disponible como cero. | Control transaccional existente. |
| Stock menor a la cantidad retornable histórica | Mostrar cantidad máxima actual y explicar; refrescar si cambió mientras se editaba. | Añadir elegibilidad informativa y mantener validación transaccional. |
| Dos devoluciones concurrentes | Una puede completarse y la otra debe refrescar/reducir cantidades con un mensaje útil. | Pruebas existentes cubren sobredevolución concurrente. |
| Devolución contabilizada | Mostrar movimiento de salida y coste; no mostrar “reembolso hecho”. | El flujo actual actualiza inventario/valoración, no finanzas de proveedor. |
| Moneda distinta de USD, si está permitida en el catálogo | Mostrar símbolo/código real; nunca etiquetar la cifra con moneda incorrecta. | Orden exige moneda consistente entre productos. |
| Fecha final del filtro | El resultado no debe perder documentos del último día por conversión horaria. | Documentar uso de instantes y límite superior exclusivo. |
| Cambio rápido de búsqueda/página | No aceptar respuestas antiguas que reemplacen resultados nuevos. | Cancelación/identificación de solicitud frontend. |
| Más de 100 órdenes | Paginación real y conteos globales correctos; sin descargar todas las páginas. | Consultas de página en backend. |
| Página sin resultados tras un cambio de estado | Ajustar a la página válida más cercana o mostrar el estado vacío correcto. | Metadatos de página actualizados. |
| Navegación Atrás o recarga | Restaurar pestaña, filtros y paginación de la URL. | No requiere migración de negocio. |
| Móvil y teclado | Acciones disponibles, tabla convertida a tarjetas cuando sea necesario, foco visible y estados anunciados. | Pruebas visuales y de accesibilidad frontend. |

## 6. Plan de implementación recomendado

### Fase 0 — Corregir estructura de carga y fallos

1. Sacar el listado de órdenes del loadData global y cargarlo desde un módulo/pestaña de Compras.
2. Usar page/size/sort con valores por defecto 0/20 y filtros serializados en URL.
3. Separar loading, error, empty y forbidden; reparar el error de carga que hoy puede parecer “sin órdenes”.
4. Pasar permisos de lectura/gestión/aprobación a PurchaseTable y deshabilitar solo la acción en curso.
5. Reemplazar el patrón listAllPages en la lista de devoluciones y en la selección masiva de recepciones por consultas específicas.
6. Añadir skeletons por pestaña y carga incremental accesible.

### Fase 1 — Navegación operativa

1. Crear pestañas Órdenes, Recepciones y Devoluciones.
2. Añadir filtros, ordenación y paginación del servidor.
3. Separar DTOs de listado y detalle para no transportar líneas completas en cada fila.
4. Retener contexto de URL al abrir/cerrar detalles.
5. Reducir acciones por fila a una principal más un menú contextual.

### Fase 2 — API de búsqueda y resumen

1. Añadir q y filtros de proveedor a recepciones/devoluciones cuando corresponda.
2. Crear el endpoint agregado de resumen con semántica precisa para cada indicador.
3. Medir N+1, tamaño de respuestas, latencias p50/p95 y planes SQL. Corregir consultas/índices según la evidencia.
4. Publicar contratos OpenAPI y ejemplos de filtros, errores y paginación.

### Fase 3 — Reglas operativas de borde

1. Añadir endpoint de elegibilidad de devolución con cantidad ya devuelta y stock procesable.
2. Decidir con negocio si se necesita editar DRAFT; si se aprueba, implementar API, auditoría y pruebas de concurrencia.
3. Diseñar por separado cualquier reversión de recepciones/devoluciones o función financiera de proveedores.

No empezar con una reescritura total del módulo ni con un sistema de estados frontend paralelo. Las fases 0–2 entregan una mejora importante manteniendo el dominio actual.

## 7. Plan de pruebas y criterios de aceptación

### Frontend

- Paginación: inicial, siguiente, anterior, última, tamaño 10/20/50, filtros que reinician página, URL restaurable y página fuera de rango.
- Estados de carga: primera carga, cambio de filtro, refresco, fallo de red, 401, 403, 5xx, sin registros y búsqueda sin resultados.
- Carrera de solicitudes: responder en orden inverso dos búsquedas y verificar que permanece el resultado de la última.
- Permisos: lector, gestor de compras y aprobador; una acción no autorizada no debe aparecer ni poder ejecutarse desde la UI.
- Estados por fila: cada una de las seis fases de la orden y sus acciones correspondientes.
- Formularios: recepción parcial, línea omitida, cero, cantidad negativa, excedente, precisión decimal, cancelación con cambios y reintento.
- Devoluciones: recepción sin saldo retornable, parcial, total, agotada, stock insuficiente y error de concurrencia.
- Responsive: 360 px, 768 px y escritorio; sin acciones inaccesibles ni scroll horizontal obligatorio en teléfono.
- Accesibilidad: navegación por teclado de tabs, etiquetas, foco, diálogo, mensajes aria-live y contraste.

### Backend

La suite actual PurchasingAndCostingApiTest ya cubre aspectos importantes del ciclo de vida, aprobación segregada, paginación/sort permitido, recepciones parciales e idempotentes, valoración y sobredevolución concurrente. Conservarla y ampliar:

- DTO de resumen y detalle: los listados no incluyen líneas completas y la consulta no crece linealmente en número de SELECT por documento.
- Filtros combinados, límites de fecha y consultas por proveedor/almacén.
- q: coincidencias, acentos/capitalización según el contrato, entradas muy largas, caracteres especiales y ausencia de inyección.
- Resumen agregado: conteos consistentes con páginas y transiciones de estado.
- Devolución disponible: cantidad recibida, acumulada devuelta, stock insuficiente y carreras entre recepciones/devoluciones de un mismo producto.
- DRAFT update, solo si se aprueba: edición permitida en borrador, prohibida tras envío, actualización concurrente, totales/currency y auditoría.
- Seguridad: lector no puede mutar; gestor no puede aprobar salvo permiso; ninguna acción elude autorización por llamadas directas.
- Rendimiento: consultas representativas con órdenes, recepciones y devoluciones voluminosas; documentar p95 y planes de ejecución.

### Definition of Done

- Ningún listado operativo descarga todas las páginas solo para representarlas en una tabla.
- Las tres pestañas tienen skeleton, empty, filtered-empty, error, forbidden y paginación consistentes.
- El número de documentos y los contadores corresponden al filtro del servidor, no solo a la página actual.
- Las acciones visibles respetan permisos y estados; el backend sigue siendo la autoridad.
- Ninguna recepción o devolución se duplica tras doble clic/reintento; errores de concurrencia son recuperables.
- La interfaz distingue inventario contabilizado de obligaciones financieras que el sistema no registra.
- Tests frontend, API y smoke tests pasan; no hay degradación de reglas de coste, movimientos, auditoría ni Outbox.

## 8. Indicadores para verificar la mejora

Medir antes/después en datos equivalentes:

- tiempo a primera tabla útil (p50/p95);
- latencia de cada pestaña y filtro (p50/p95);
- bytes y número de respuestas descargadas al entrar en Compras;
- número de SELECT por listado/página;
- tasa de errores 401/403/409/422 y de reintentos;
- errores al registrar recepciones/devoluciones y operaciones duplicadas;
- abandono al iniciar una compra/devolución;
- proporción de búsquedas sin resultados y uso de filtros.

Metas iniciales sugeridas, a validar con el entorno real: primera página visible en menos de 1,5 s p95 en una conexión y dataset representativos; una petición paginada por refresco de la tabla (más la de resumen si está habilitado); y ninguna carga masiva del histórico para mostrar una sola página. Son objetivos de aceptación, no resultados medidos.

## 9. Referencias revisadas en el repositorio

- [App.jsx](app-inventary-ecuapp/src/App.jsx): carga global, permisos de cabecera y composición actual de Compras.
- [api.js](app-inventary-ecuapp/src/api.js): wrappers de compras, recepciones y devoluciones; listAllPages.
- [PurchaseTable.jsx](app-inventary-ecuapp/src/features/purchasing/PurchaseTable.jsx): tabla de órdenes y acciones por estado.
- [PurchaseReturns.jsx](app-inventary-ecuapp/src/features/purchasing/PurchaseReturns.jsx): listado, selección de recepción y wizard de devolución.
- [PurchaseForm.jsx](app-inventary-ecuapp/src/features/purchasing/PurchaseForm.jsx): wizard de creación de orden.
- [ReceiptForm.jsx](app-inventary-ecuapp/src/features/purchasing/ReceiptForm.jsx): cantidades de recepción parcial.
- [PurchaseOrderController.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseOrderController.java): endpoints, permisos y filtros.
- [PurchaseOrderService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseOrderService.java): estados, idempotencia, recepción, inventario y auditoría.
- [PurchaseOrderRepository.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseOrderRepository.java): consulta paginada y asociaciones cargadas.
- [PurchaseReturnController.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseReturnController.java) y [PurchaseReturnService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseReturnService.java): filtros y validaciones de devolución.
- [PurchasingAndCostingApiTest.java](microservices-inventary-ecuapp/src/test/java/com/ecuapp/inventary/api/PurchasingAndCostingApiTest.java): ciclo de vida, recepciones parciales, idempotencia, valoración y concurrencia.
- [Migración V2 de compras y recepciones](microservices-inventary-ecuapp/src/main/resources/db/migration/V2__partners_and_procurement.sql) y [Migración V4 de devoluciones](microservices-inventary-ecuapp/src/main/resources/db/migration/V4__purchase_returns_and_reorder_rules.sql): índices y restricciones actuales.

