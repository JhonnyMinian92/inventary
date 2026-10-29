# Inventario como centro de mando del negocio y plataforma preparada para agentes

**Versión:** 3.0 — revisión estratégica y técnica  
**Fecha de investigación:** 28 de octubre de 2026  
**Repositorio:** JhonnyMinian92/inventary · rama master  
**Estado:** propuesta de arquitectura, producto, frontend y backend; pendiente de implementación.  
**Reemplaza:** PROPUESTA_INVENTARIO_CENTRO_CONTROL_Y_AUDITORIA_V2.md

## 0. La decisión que cambia la dirección del producto

Inventario no debe ser una página técnica para que el empleado consulte cantidades. Debe ser la **pantalla preferida del dueño del negocio**: el lugar donde comprende qué tiene, dónde está, qué parte puede vender, cuánto capital tiene inmovilizado, qué podría agotarse, qué compras o traslados necesitan atención, dónde hay diferencias y qué decisión conviene tomar a continuación.

También debe ser la pantalla de trabajo del personal del almacén, sin confundir sus necesidades con las del dueño. El dueño necesita síntesis y decisiones; el operador necesita exactitud, velocidad y acciones de proceso; el auditor necesita evidencia; un agente futuro necesita datos legibles por máquina y herramientas con contratos explícitos y permisos.

La propuesta se basa en cuatro ideas:

1. **Control operativo + inteligencia empresarial:** los saldos, movimientos, costes, reservas, compras, ventas y tareas pendientes se interpretan en un mismo contexto, sin mezclar conceptos contables.
2. **Una sola fuente de verdad:** el dominio actual de Spring Boot/PostgreSQL conserva las reglas y ejecuta los comandos. React, n8n y los agentes no calculan ni cambian stock por fuera de la API.
3. **Vista de dueño orientada a excepciones:** en lugar de llenar la pantalla de KPI decorativos, priorizar decisiones por impacto, evidencia, tiempo y siguiente acción.
4. **Agent-ready desde el diseño:** APIs de lectura consistentes, comandos tipados, previsualización, propuestas persistidas, políticas de autorización, aprobación humana e idempotencia. El acceso por MCP puede añadirse como adaptador futuro; no debe inventarse una API alternativa de negocio para cada agente.

Esta revisión es más ambiciosa que la V2: considera benchmark de ERP/WMS, experiencia del dueño, modelo de datos, lecturas de alto rendimiento, herramientas para agentes, seguridad de ejecución, medición económica, previsión, trazabilidad y un plan gradual para no desestabilizar las reglas de stock existentes.

## 1. Benchmark: patrones de sistemas de inventario avanzados

No se propone copiar una interfaz de un proveedor ni asumir que las funcionalidades documentadas ya existen en Inventary. La comparación sirve para identificar patrones maduros y decidir cuáles adoptar de forma proporcional al negocio.

| Sistema / referencia | Patrón documentado | Qué debe aprender Inventary | Límite de adaptación |
|---|---|---|---|
| Odoo Inventory 20.0 | Reposición por reglas, rutas MTO y planificación; conteo físico con diferencia explícita; trazabilidad por lotes/series y valoración del movimiento en FIFO/AVCO. | Un inventario avanzado combina el saldo actual con flujos operativos, trazabilidad y reposición, no solo una tabla. | Inventary aún no tiene lote/serie/caducidad ni todas las rutas de almacén de un WMS. No simularlos en UI. |
| Microsoft Dynamics 365 Inventory Visibility | Capa de visibilidad con publicación y consulta de on-hand, eventos de reserva y desreserva, asignación/consumo y consulta rápida entre fuentes/canales. | La disponibilidad compartida para POS, web y WhatsApp necesita un contrato único con semántica de reservas y reglas de integración. | No hace falta construir ahora otro microservicio de visibilidad independiente; el backend actual puede evolucionar sus contratos y proyecciones. |
| Shopify inventory and locations | Ubicaciones, cantidades vendibles, ajustes, conteos, transferencias e historial del motivo de cambio. | La experiencia cotidiana debe encontrar fácil la tarea “ver stock por ubicación”, corregir con motivo y seguir el historial. | Shopify es una experiencia comercial simplificada; Inventary también necesita valoración, controles de almacén y auditoría. |
| Oracle NetSuite | Diferencia entre transferencia simple y orden de transferencia con etapas de despacho/tránsito/recepción; conteo y ajuste explícitos. | Una transferencia no es un movimiento instantáneo indivisible cuando importa rastrear mercancía en tránsito. | El flujo actual de Inventary ya modela tránsito; la propuesta mejora visibilidad, métricas y alertas. |
| Microsoft Dynamics 365 Warehouse Management | Trabajo de almacén dirigido, operaciones móviles con confirmaciones por proceso y conteo de ciclo. | Separar el tablero del dueño del modo de ejecución física para el operador; preparar luego un modo móvil de escaneo. | Picking por ubicación, RF/escáner, trabajo y labor standards no están implementados y requieren modelo propio. |
| Arquitectura de agentes: MCP y OpenAI Agents SDK | Herramientas con contratos de entrada/salida, guardrails, aprobación humana para efectos laterales, trazas de llamadas y evaluaciones. | Un agente necesita herramientas de dominio acotadas, trazabilidad de extremo a extremo y aprobación en acciones sensibles. | No se debe entregar un acceso genérico a la base de datos ni permitir al modelo ejecutar cualquier endpoint. |

### Fuentes primarias para el benchmark

- Odoo, inventario, rutas, unidades, conteos y trazabilidad: https://www.odoo.com/documentation/20.0/es/applications/inventory_and_mrp/inventory.html
- Odoo, valoración de movimientos y efecto de entradas/salidas: https://www.odoo.com/documentation/20.0/es/applications/inventory_and_mrp/inventory/inventory_valuation/operations_valuation.html
- Odoo, ajuste físico, cantidad contada y diferencia: https://www.odoo.com/documentation/20.0/applications/inventory_and_mrp/inventory/management/inventory_adjustments.html
- Microsoft, API de visibilidad, consultas de disponibilidad y eventos de reserva/desreserva: https://learn.microsoft.com/es-es/dynamics365/supply-chain/inventory/inventory-visibility-api
- Microsoft, resumen de Inventory Visibility: https://learn.microsoft.com/en-us/dynamics365/supply-chain/inventory/inventory-visibility
- Shopify, inventario y ubicaciones: https://help.shopify.com/en/manual/inventory-and-locations
- Oracle NetSuite, inventario, ajustes, transferencias y conteos: https://docs.oracle.com/en/cloud/saas/netsuite-quickstart/help/book_7144014321.html
- Oracle NetSuite, órdenes de transferencia y mercancía en tránsito: https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_N2308766.html
- Microsoft, políticas de trabajo en almacén: https://learn.microsoft.com/en-us/dynamics365/supply-chain/warehousing/warehouse-work-policies
- OpenAI, agentes, herramientas, guardrails, aprobación y trazas: https://developers.openai.com/api/docs/guides/agents/sdk y https://developers.openai.com/api/docs/guides/agents/guardrails-approvals
- MCP, anotaciones de herramienta y vocabulario de riesgo: https://blog.modelcontextprotocol.io/posts/2026-03-16-tool-annotations/
- A2A, interoperabilidad entre sistemas de agentes: https://a2a-protocol.org/dev/specification/
- OWASP, Top 10 de riesgos de aplicaciones agentic 2026: https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/
- AWS, patrón Outbox transaccional e idempotencia de consumidores: https://docs.aws.amazon.com/es_es/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- Microsoft, advertencias y complejidad de event sourcing: https://learn.microsoft.com/es-es/azure/architecture/patterns/event-sourcing

Conclusión del benchmark: la ventaja competitiva no es poner un chatbot encima de la tabla actual. Es construir un modelo operativo con datos consistentes, una vista de dueño útil y contratos de acción que puedan ser utilizados con seguridad por personas, automatizaciones y agentes.

## 2. Estado real del repositorio: qué hay y qué falta

### 2.1 Componentes existentes comprobados

En el frontend:
- app-inventary-ecuapp/src/App.jsx compone la pantalla de Inventario, filtros, KPI básicos, saldos, movimientos y InventoryOperations.
- src/features/inventory/InventoryOperations.jsx tiene pestañas internas para traslados, conteos, reposición y ajustes, y formularios guiados para crear operaciones.
- src/api.js ofrece los wrappers REST para balances, movimientos, valoración, transferencias, conteos y reposición.
- Existe un componente SkeletonTable, pero no se utiliza una experiencia de carga por vista bien aislada en el módulo.
- El listado de balances descarga todas las páginas en loadData; el historial de movimientos se limita a una muestra de la primera página, y los listados de operaciones también cargan todas las páginas. La interfaz no aprovecha bien los endpoints pageable.
- El indicador “Unidades” suma onHand de diferentes productos sin agrupar por unidad de medida: no es un indicador válido.
- La página actual necesita valoración protegida por reports:financial en su carga global, aunque el usuario puede tener permiso para consultar existencias sin poder leer costes.
- Las acciones operativas no reciben una matriz completa de permisos de UI, y varias vistas de lectura requieren inventory:manage o audit:read aunque el menú expone Inventario con inventory:read. Los controladores deben ser revisados y alineados para que 403 no parezca una lista vacía.

En el backend:
- StockBalance/stock_balance: saldo actual por producto y almacén, onHand, reserved, versión, ubicación y timestamp.
- StockMovement/stock_movement: producto, almacén, tipo, cantidad, coste unitario, referencia origen, usuario, fecha, unidad de medida, correlación y ubicaciones.
- InventoryCostingService: capas y valoración FIFO o promedio ponderado; InventoryValuationEntry permite explicar coste de movimientos.
- InventoryController: saldos, disponibilidad informativa, movimientos, detalle de movimiento y ajustes.
- StockTransferController/Service: traslado en estados DRAFT, IN_TRANSIT, COMPLETED, CANCELLED y REVERSED; despacho, recepción, cancelación y reversión.
- InventoryCountController/Service: conteo con snapshot expectedQuantity, countedQuantity y difference; al contabilizar detecta cambios de saldo desde la captura.
- ReorderController/Service: reglas de reposición y sugerencias por regla de producto/almacén, con mínimo, objetivo, lead time y proveedor preferido.
- Reportes: valoración, kárdex, compras, ventas, balance histórico y bajo stock.
- AuditService y endpoints de audit-events, y una tabla Outbox transaccional para eventos en varios flujos.
- Flujos de compras, ventas, devoluciones y pedidos WhatsApp tocan el inventario desde sus respectivos servicios. El objetivo debe ser un único libro de stock, no tablas paralelas por canal.

### 2.2 Límites actuales que la propuesta no debe ocultar

1. Saldo por producto y almacén: la clave actual no permite afirmar que existe disponibilidad independiente por lote/serie/bin. Aunque existen referencias a ubicación de origen y destino, la experiencia actual no es un WMS con múltiples ubicaciones de picking.
2. Reservas: existe saldo reservado y el flujo de WhatsApp utiliza reservas. No todos los flujos de ventas convencionales tienen un ciclo universal de reserva/fulfillment.
3. Reposición: las sugerencias dependen de reglas activas que ya existen. Un producto sin regla no es automáticamente “sin riesgo”.
4. Previsión: no existe todavía un modelo validado de demanda futura, días de cobertura, estacionalidad o stock muerto. Estas métricas necesitan contratos, historial y validación, no una fórmula de interfaz improvisada.
5. Entradas previstas: las órdenes y recepciones existen, y las transferencias registran tránsito, pero no hay una capa universal de disponibilidad proyectada para todos los canales.
6. Costes: valoración financiera depende del permiso actual. No deben exponerse costes a usuarios que solo necesitan cantidades.
7. Eventos: el Outbox está, pero no todas las operaciones de ajuste y conteo parecen emitir eventos transaccionales. Se debe cerrar esa cobertura.
8. Variantes, lotes, series, caducidad, cuarentena, daño, picking y consignación necesitan decisiones de modelo antes de ofrecerlos como funcionalidad.

## 3. La pantalla preferida del dueño: un centro de mando, no un dashboard decorativo

La pantalla inicial de Inventario debe optimizar una pregunta: **“¿Qué debo atender hoy para proteger ventas, caja y control de stock?”** La respuesta necesita contexto, evidencia y acciones concretas. El dueño no debe ver una colección de indicadores sin explicación ni tener que abrir cinco módulos para entender un problema.

### 3.1 Diseño de primer viewport

**Franja 1 — Identidad y contexto**
- Título Inventario y un resumen dinámico del período: “Vista general de todos los almacenes · actualizado hace 2 min”.
- Selector de almacén, intervalo temporal y comparación, con filtros persistentes.
- Barra de búsqueda global por SKU, nombre o documento.
- Accesos rápidos: Nueva compra (abrir borrador), Nuevo traslado, Conteo físico y Ajuste. Los accesos solo se muestran con el permiso correspondiente; el ajuste y la contabilización no deben aparecer como atajos sin contexto.

**Franja 2 — Lo que importa hoy (lista de decisión)**
Un panel de trabajo prioritario con máximo 5–7 alertas ordenadas por impacto y confianza. Cada fila explica:
- qué sucede;
- por qué importa;
- qué dato lo demuestra;
- impacto estimado, cuando sea calculable;
- siguiente acción recomendada;
- fecha/hora del dato;
- vínculo a la consulta o documento origen.

Ejemplos: “3 SKU por debajo de mínimo en Quito”, “1 transferencia lleva 2 días en tránsito”, “Conteo rechazado porque cambió el saldo”, “Artículo con stock físico, pero sin disponibilidad por reservas”, “Recepción no contabilizada” solo cuando exista una operación que realmente esté en ese estado. No declarar atrasos cuando no hay SLA o fecha esperada.

**Franja 3 — Indicadores del negocio, pequeños pero útiles**
- **Valor del inventario** por moneda y método, solo con permiso financiero.
- **Capital inmovilizado / riesgo de exceso**, solo después de implementar cobertura/demanda y criterios validados.
- **Stockout risk / ventas que podrían bloquearse**, basado primero en reglas deterministas y después en una previsión validada.
- **Disponibilidad comprometible**, indicando el ámbito del almacén y el carácter temporal de la lectura.
- **Tareas abiertas de inventario**: conteos, traslados en tránsito y ajustes pendientes.

No mostrar KPI sin definición y sin fuente. Cada tarjeta incluye tooltip o “Cómo se calcula” con filtros, fecha de cálculo y link al detalle. Si la métrica no existe aún, no se dibuja como cero; se omite o se marca explícitamente como futura.

**Franja 4 — Tendencia y riesgo**
Dos visualizaciones sencillas:
1. Entradas frente a salidas, con entradas agrupadas por recepción/retorno/transferencia/ajuste y salidas por venta/devolución a proveedor/transferencia/ajuste, usando un signo y una leyenda claros.
2. Tendencia de valor de inventario y de coste de ventas, comparando períodos homogéneos y moneda compatible.

Las gráficas no deben inferir causalidad. Un pico de salidas puede deberse a una venta grande, transferencia o corrección; la tabla de movimientos sirve para explicarlo.

**Franja 5 — Atención y navegación profunda**
- Top productos con falta de disponibilidad o bajo mínimo, separados por almacén.
- Sugerencias de reposición con razón, cantidad sugerida y proveedor.
- Últimos 8–10 movimientos, con “Ver todos”.
- Enlaces al kárdex y a la reconciliación, siempre sujetos a permisos.

La información detallada no se pierde: pasa a Existencias, Movimientos, Operaciones y Auditoría. El resumen solo ordena las decisiones y permite profundizar.

### 3.2 Vista compacta para el dueño y modo de ejecución para el operador

No diseñar una única página saturada para todos los roles. La misma ruta puede ofrecer una vista de resumen por defecto y, desde ahí, acceder a un **modo de trabajo** con tablas y acciones.

- **Dueño/gerencia:** impacto en ventas, caja, stockouts, exceso, valor valorizado, prioridades y aprobación de recomendaciones.
- **Encargado de inventario:** existencias, reservas, recepción, conteos, traslados, ajustes y filtros operativos.
- **Almacén:** tareas concretas y formularios de captura rápida, especialmente en móvil; los futuros flujos de escaneo se incorporan solo después de modelar ubicaciones y unidades logísticas.
- **Finanzas:** valoración, reconciliación de cantidad/valor, coste y efecto de ajustes, con el permiso actual.
- **Auditor:** búsqueda por documento, usuario, período, movimiento y motivo; lectura de evidencia inmutable.
- **Agente:** consultas y propuestas dentro del alcance del usuario que lo invoca; las acciones de riesgo pasan por aprobación y política.

La preferencia del dueño se logra por la calidad de las decisiones, la confianza en las cifras y la claridad de qué hacer, no por incluir más tarjetas.

## 4. Navegación del módulo

### 4.1 Primer nivel

1. **Centro de mando** — alertas y decisiones del negocio.
2. **Existencias** — estado actual por producto/almacén.
3. **Movimientos / Kárdex** — historia y trazabilidad.
4. **Operaciones** — traslados, conteos, ajustes, reposición.
5. **Integridad y auditoría** — discrepancias, movimientos y eventos, según permiso.

Cada vista tiene sus propios estados de carga, error, forbidden, resultados vacíos, filtros y paginación; la navegación no descarga todos los datos al entrar. Guardar tab, warehouse, período, búsqueda, filtros, sort y page en la URL. El navegador Atrás/Adelante debe restaurar el contexto.

### 4.2 Operaciones por separado

Subpestañas:
- Traslados: origen/destino, estado, líneas, antigüedad, despacho/recepción/cancelación/reversión.
- Conteos físicos: esperado, contado, diferencia, snapshot, creador, fecha, conflicto y contabilización.
- Ajustes: historial propio basado en movimientos de ajuste, motivo, unidad, actor y referencia.
- Reposición: sugerencias separadas de reglas configuradas y productos sin regla.

La pantalla no debe colocar estos flujos debajo de la tabla de existencias. Una acción iniciada desde alertas debe abrir la vista correspondiente con el almacén/producto/documento ya seleccionado.

### 4.3 Estados de carga y error

Cada tabla y tarjeta necesita los estados siguientes:
- skeleton de la estructura real en la primera carga;
- actualización de la vista que mantiene los datos anteriores hasta recibir la respuesta;
- empty real;
- empty por filtros, con limpiar filtros;
- error recuperable con Reintentar;
- forbidden explícito;
- conflicto de estado/datos (409) con refresco guiado;
- validación de negocio (422) cerca del campo;
- conexión perdida/offline con fecha de la última lectura confirmada.

Nunca interpretar 403, 5xx, timeout o rechazo de parsing como inventario vacío. El refresco de una tabla no debe recargar ventas, compras, catálogos, todas las existencias y cada operación no relacionada.

## 5. Semántica de stock: un contrato común para persona, web, WhatsApp y agente

El frontend necesita presentar estos conceptos sin intercambiarlos:

- **On-hand / físico:** stock registrado dentro del almacén.
- **Reservado:** cantidad comprometida por operaciones abiertas.
- **Disponible actual:** onHand − reserved.
- **Entrante previsto:** mercancía respaldada por una orden/transferencia en un estado elegible y una fecha estimada fiable; no está físicamente en el almacén.
- **En tránsito:** cantidad despachada de un origen que aún no ha sido recibida en el destino.
- **Bloqueado/cuarentena/dañado:** futura dimensión que solo debe habilitarse si se implementa el modelo de disposición/calidad.
- **Proyección al horizonte:** disponible + entradas elegibles antes del horizonte − demanda prevista/comprometida; indicador estimado, no saldo actual.
- **Valor de stock:** valor según la política de coste y moneda, no precio de venta ni valor de órdenes de compra.

### 5.1 La disponibilidad requiere contexto

Una cifra de disponibilidad se debe leer como “disponible en el almacén X, consultado a las HH:MM”. No implica que el mismo saldo esté disponible después de otra operación. El backend debe volver a bloquear y comprobar el saldo al crear venta/reserva/traslado/ajuste.

Un concepto ATP (Available to Promise) puede incorporarse en el futuro, pero necesita definir qué órdenes, reservas, fechas de llegada, asignaciones y políticas se incluyen. No se sumará simplemente toda compra aprobada a la existencia física.

### 5.2 Unidad de medida y moneda

El KPI actual que suma onHand de todos los productos es incorrecto cuando hay unidades heterogéneas. Sustituirlo por conteos de SKU/productos y cantidades por unidad compatible. Si se desea una cantidad agregada, debe agruparse por unidad/dimensión convertida con una conversión oficial y válida del backend.

Todo importe se agrupa por moneda. Nunca mostrar un único total mezclando currencies sin conversión oficial con tipo de cambio, fecha y fuente documentados.

## 6. Evolución backend: capacidades concretas

### 6.1 API agregada para el centro de mando

Proponer GET /api/v1/inventory/command-center?warehouseId=&from=&to=.

Contrato sugerido:
- generatedAt y dataFreshness;
- warehouse scope y period scope;
- contadores operativos: productos con stock, sin disponibilidad, bajo regla mínima, sin regla, conteos abiertos, traslados en tránsito;
- métricas monetarias agrupadas por currency, solo si hay autorización financiera;
- lista paginada de alertas prioritarias con id, type, severity, confidence/source, businessImpact, reasonCode, evidence, generatedAt, currentState, recommendedAction y deeplink;
- resumen de tendencias, no un histórico ilimitado;
- indicador de calidad/reconciliación que distinga “no calculado”, “sin permiso”, “correcto” y “con diferencias”.

Las tarjetas se computan en SQL/servicios backend. No sumar arrays descargados en el navegador ni devolver detalles de movimientos innecesarios en el resumen.

### 6.2 API de existencias con query y vistas

Extender GET /api/v1/inventory/balances de forma compatible:
- q para SKU/nombre/código;
- warehouseId, productId, onlyAvailable;
- categoryId/productKind y filtros de estado solo cuando se definan contractualmente;
- sort allowlist estable;
- page/size con máximo definido;
- DTO de lista con unidad de medida, onHand/reserved/available/version/updatedAt y nombre/SKU/almacén.
- coste fuera de la proyección básica o protegido según el permiso.

Incluir un detalle de posición de stock que reúna, bajo permiso:
- balances por almacén;
- reserva y su documento origen cuando exista;
- recepciones/órdenes con saldo pendiente;
- movimientos recientes y link al kárdex;
- regla de reposición;
- capas/valoración y señal de conciliación para roles autorizados.

### 6.3 API de movimientos y trace

Extender GET /api/v1/inventory/movements con texto de referencia/documento, correlationId y actor solo para usuarios autorizados. Mantener filtros existentes de producto, almacén, type, referenceType, referenceId, from y to.

Añadir una lectura unificada equivalente a GET /api/v1/inventory/movements/{id}/trace que muestre:
- movimiento y línea de origen;
- referencia/documento legible cuando resoluble;
- actor, fecha, correlación y auditoría vinculada;
- valoración detallada únicamente con permiso financiero;
- eventos downstream relacionados cuando existan.

Los listados usan DTO ligero; detalle y trazabilidad son consultas separadas. La búsqueda debe ser parametrizada, acotada e indexable. No buscar texto arbitrario recorriendo metadata JSON sin un plan de datos.

### 6.4 KPI de negocio y métricas que no existen aún

| Métrica para dueño | Estado | Trabajo necesario |
|---|---|---|
| Valor de inventario por moneda | Existe vía reportes protegidos | Separarlo del resumen operacional y servirlo solo a roles autorizados; asegurar timestamp y reconciliación. |
| Productos sin disponibilidad / bajo mínimo | Parcialmente posible con balances y reglas | Agregación de servidor; distinguir regla ausente de bajo mínimo. |
| Compras/recepciones netas por periodo | Hay reportes operativos | Definir si “compra” significa orden, recepción contabilizada o devolución; mostrar conceptos separados. |
| Cobertura estimada en días | No implementada como modelo validado | Historial de demanda neta, ventana configurable, tratamiento de días sin stock y entradas previstas. |
| Stock muerto/lento/exceso | No implementado con definición consistente | Ventana sin venta, cantidades, coste, ageing, umbral específico por negocio; excluir productos nuevos/estacionales cuando corresponda. |
| Riesgo de quiebre | No implementado de extremo a extremo | Forecast validado, plazo de proveedor y stock comprometido/entrante por fecha. |
| Capital atado en exceso | No implementado como métrica de negocio | Inventario valorizado más política de exceso; monedas separadas, sin confundir compras pendientes con capital ya pagado. |
| Margen perdido por stockout | No debe estimarse como dinero exacto aún | Evidencia de demanda no satisfecha y modelo de margen, no multiplicar ventas pasadas por días de stock cero sin supuestos. |
| Tiempo real | No garantizado por todas las vistas | Definir freshness SLA, publicar eventos y mostrar última actualización; no presentar datos asíncronos como tiempo real sin evidencia. |

### 6.5 Modelo de alertas y recomendaciones explicables

Crear una lectura central de alerta operativa con:
- alertId, ruleId, severity, status (OPEN, ACKNOWLEDGED, RESOLVED, DISMISSED);
- entityType/entityId y warehouseId;
- reasonCode estable;
- evidenceRefs a balance/movement/document;
- sourceDataAt y generatedAt;
- estimatedImpact con moneda/unidad y tipo de cálculo;
- recommendedAction y link al flujo de trabajo;
- acknowledgedBy/At y resolution metadata.

No tratar una alerta como verdad irreversible. La alerta puede quedar obsoleta después de una recepción/venta; al abrirla debe refrescar la evidencia. La resolución de una alerta no edita el stock. Las reglas de riesgo deterministas son la primera fase; previsiones probabilísticas se incorporan después de backtesting.

### 6.6 Reposición: de umbrales manuales a planificación validada

El modelo actual de reglas tiene mínimo, objetivo, plazo de entrega y proveedor preferido, y sugiere target minus available. Evolucionar por fases:
1. Regla fija actual, explicada y eficiente.
2. Añadir sugerencia que considere entradas confirmadas antes de una fecha elegible, pedidos pendientes y reservas, con política explícita.
3. Añadir ventas netas/retornos, demanda media y volatilidad solo tras una historia suficiente.
4. Añadir forecast con horizonte, intervalo de confianza, sesgo/MAPE y backtesting por SKU/almacén.
5. Permitir que el agente prepare una orden como borrador con razón y datos fuente, nunca enviar/autorizar automáticamente sin políticas y permiso.

Revisar la regla existente: frontend rechaza targetQuantity igual a minimumQuantity, pero ReorderService solo rechaza target menor; escoger un contrato único y testear la igualdad.

### 6.7 Almacén real: qué no se puede improvisar

El saldo actual tiene una unicidad por producto/almacén y una ubicación en el balance. La UI no debe anunciar disponibilidad por estante, lote o variante sin ampliar el dominio.

Si el negocio requiere trazabilidad avanzada, definir de forma explícita:
- TrackingMode por producto: NONE, LOT, SERIAL o EXPIRY según aplique.
- Stock dimension: producto/variante, almacén, ubicación/bin, lote/serie y estado de calidad.
- Unidad de medida base y conversiones permitidas.
- Stock ledger y saldo proyectado por la dimensión que realmente identifica el stock.
- Reserva por la misma dimensión, picking/put-away, recepción y salida.
- Reglas de trazabilidad desde recepción/proveedor hasta venta/cliente para recalls.
- Migración de balances actuales a MAIN por warehouse, sin inventar lotes previos.

No es obligatorio implementar lotes/series para el primer rediseño. Sí es obligatorio no crear deuda de UI fingiendo que la dimensión ya existe.

## 7. Arquitectura para agentes: tool-ready antes que chatbot

### 7.1 Principio

Un agente no debe navegar como si fuera un usuario y hacer clic en botones al azar cuando puede llamar herramientas de negocio tipadas. La interfaz visual sigue siendo para el dueño y los empleados; las herramientas proporcionan a agentes una forma explícita y segura de consultar evidencia y proponer operaciones.

No empezar con muchos microservicios ni un enjambre de agentes. Comenzar con contratos REST claros, una capa de aplicación de comandos/consultas y un catálogo de herramientas de dominio. Un adaptador MCP puede envolver esos servicios cuando el runtime de agentes esté decidido. A2A solo tendría sentido si agentes independientes de otros equipos/proveedores necesitan colaborar como sistemas separados; no es una dependencia para la primera versión.

### 7.2 Herramientas iniciales de solo lectura

Las herramientas deben ser cortas, tipadas, deterministas y devolver estructuras útiles sin secretos:

| Tool futura | Responsabilidad | Clase de riesgo |
|---|---|---|
| inventory.get_command_center | KPIs, alertas, actualización y ámbito | Lectura |
| inventory.search_stock | Buscar SKU por almacén con onHand/reserved/available y unidad | Lectura |
| inventory.get_position | Componer balances, regla de reposición, recepciones y traza reciente | Lectura |
| inventory.get_movement_trace | Encontrar movimiento y documento/auditoría origen | Lectura, posiblemente coste restringido |
| inventory.get_kardex | Consultar historial por producto, almacén y periodo | Lectura |
| inventory.explain_variance | Consultar diferencia de conteo o reconciliación con evidencia disponible | Lectura |
| inventory.get_replenishment_plan | Explicar productos bajo mínimo y cálculo de cantidad sugerida | Lectura/planificación |
| inventory.get_transfer_status | Consultar mercancía en tránsito y su antigüedad | Lectura |
| inventory.get_data_freshness | Confirmar fecha de cálculo, proyección y fuente | Lectura |

No añadir una herramienta genérica run_sql, direct_database, arbitrary_http o “execute any endpoint”. No exponer credenciales internas, JWT de servicio, X-Integration-Key, tokens de Meta, secretos n8n o claves de idempotencia almacenadas.

### 7.3 Herramientas de preparación, mutación y aprobación

Separar explícitamente consulta, propuesta y ejecución:

1. **preview/recommend:** calcula una opción, muestra inputs, reglas, impacto y supuestos; no escribe.
2. **create_proposal:** guarda una propuesta tipada con estado DRAFT/PENDING_APPROVAL; no ejecuta el movimiento.
3. **approve_proposal:** una persona autorizada acepta esa propuesta exacta; la aprobación queda ligada a hash de los argumentos y snapshot usado.
4. **execute_approved_proposal:** el servidor vuelve a validar stock, estado del documento, permisos, límites, idempotencia y precondiciones antes de ejecutar.
5. **get_operation_status:** permite recuperar un resultado después de timeout sin repetir el efecto.

Ejemplos de comandos futuros:
- inventory.preview_replenishment / inventory.create_purchase_draft;
- inventory.preview_transfer / inventory.create_transfer_draft;
- inventory.preview_adjustment / inventory.create_adjustment_proposal;
- inventory.preview_count_posting / inventory.create_count_proposal.

Los nombres son propuestos, no endpoints implementados. El agente puede preparar un borrador o sugerir una decisión, pero el rol/política determina si puede ejecutarla. Ajustes, devoluciones físicas, cancelaciones, reversión de movimientos y contabilizaciones de alto impacto necesitan aprobación humana o una política aprobada explícita.

### 7.4 Modelo persistido de una propuesta del agente

Antes de permitir agentes que escriban, definir una entidad de propuesta de acción con un mínimo de:
- proposalId impredecible;
- actor humano y agentId/runtime;
- acción tipada y versión de esquema;
- argumentos normalizados y su hash;
- almacén y referencias de documento afectados;
- evidencia/snapshot IDs y versión del saldo cuando esté disponible;
- impacto esperado de cantidad y coste, respetando permisos;
- regla de política evaluada y resultado;
- status (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, EXECUTING, COMPLETED, STALE, FAILED, EXPIRED);
- createdAt, expiresAt, approvedBy/At, executedAt;
- idempotency scope/key y execution/result reference;
- requestId/correlationId y enlaces a auditoría.

Una aprobación no es un token permanente. Si cambian los argumentos, la versión del balance, la cantidad disponible o el documento, la propuesta queda STALE y requiere una nueva previsualización. La ejecución vuelve a validar todo dentro del servicio de dominio.

El diseño no debe afirmar que la propuesta se convierte en “transacción exactamente una vez” por usar un agente. La garantía se construye con transacción de backend, idempotencia, comparación de precondiciones y deduplicación de eventos.

### 7.5 Niveles de autonomía

| Nivel | Qué puede hacer el agente | Ejemplo |
|---|---|---|
| A0 — Observar | Consultar y explicar datos autorizados. | “¿Qué productos están debajo del mínimo?” |
| A1 — Recomendar | Sugerir acción y explicar evidencia; no escribe. | “Sugiero pedir 20 unidades porque el disponible es 3 y el objetivo configurado es 23.” |
| A2 — Preparar | Crear borrador/propuesta sin modificar stock ni enviar documentos. | Preparar una orden de compra, traslado o conteo revisable. |
| A3 — Ejecutar tras aprobación | Ejecutar una propuesta exacta aprobada y vigente. | Aplicar un traslado ya aprobado por un gestor. |
| A4 — Autonomía acotada | Ejecutar acciones repetibles de bajo riesgo dentro de presupuesto/almacén/productos permitidos, con límites y kill switch. | En el futuro, crear una notificación o borrador de reposición; no aprobar pagos ni ajustes de stock sin política. |

No habilitar A4 hasta tener métricas, tests adversariales, auditoría, límites por usuario/almacén/coste, control de cambios de política y rollback operativo.

### 7.6 Autorización para humanos y agentes

- Autorización backend en cada llamada, sin confiar en el texto del modelo, tool annotations ni filtros del frontend.
- Scope explícito: organización cuando exista soporte multiempresa, almacenes, clases de operaciones, producto y permiso financiero.
- Agente actúa con el contexto de autorización del usuario invocador o una identidad de servicio de alcance muy limitado y atribuible; jamás como superadmin implícito.
- Las herramientas de lectura y escritura tienen scopes distintos.
- Limitar cantidad, valor, frecuencia, número de operaciones en una corrida, horario y almacenes.
- Confirmaciones se vinculan a la propuesta exacta; no aprobar argumentos recibidos después.
- Los datos de productos, notas, motivos y documentos recuperados son contenido no confiable; no se tratan como instrucciones para el agente.
- Todas las llamadas de tools tienen log, input schema version, resultado, duración, policy decision, correlationId y acción de usuario.
- Los errores y fallos de tool se prueban como parte del flujo, no se ocultan con reintentos infinitos.
- Se necesita interruptor de desactivación global y por acción/almacén/agentId.
- Costes y métricas financieras se filtran en el servidor antes de llegar al contexto del modelo.

OWASP incluye la agencia excesiva y el abuso de herramientas/identidad como riesgos importantes de sistemas agentic. La aplicación debe limitar funcionalidad, permisos y autonomía en el propio runtime de herramientas; no basta con pedir en el prompt que el agente se comporte de forma segura.

### 7.7 MCP, OpenAPI, A2A y n8n: decisión arquitectónica

- **REST/OpenAPI:** contrato primario de negocio para React, n8n y futuras integraciones.
- **MCP (futuro):** adaptador para descubrir/callar recursos y herramientas de Inventary desde runtimes que lo soporten. Cada tool llama servicios de aplicación ya existentes y no contiene reglas duplicadas.
- **A2A (futuro y opcional):** interoperabilidad entre agentes independientes cuando de verdad exista esa necesidad; no reemplaza autorización ni contratos de dominio.
- **n8n:** orquestación de workflows, notificaciones y tareas externas deterministas. No es almacén de estado de inventario ni autoridad de transacciones.
- **Agente LLM:** interpreta petición, consulta tools, explica evidencia y propone acciones. No inventa stock ni ejecuta SQL.

Las anotaciones MCP de “read-only”, “destructive” o “idempotent” son pistas de riesgo, no controles de seguridad por sí mismas. Las reglas de autorización y aprobación deben imponerse en backend.

## 8. APIs de negocio actuales y cambios necesarios

### 8.1 Contratos existentes a reutilizar

| Endpoint actual | Capacidades existentes | Mejora necesaria |
|---|---|---|
| GET /api/v1/inventory/balances | Página con productId, warehouseId, onlyAvailable y sort permitido. | q, unidad, updatedAt y filtros explícitos según dominio; llamada paginada. |
| GET /api/v1/inventory/balances/{productId}/{warehouseId} | Posición actual puntual. | Detalle ampliado compuesto sin cargar el historial completo. |
| GET /api/v1/inventory/availability | Consulta informativa por producto/SKU y almacén. | Definir frescura y separar disponibilidad de una reserva firme o de ATP futuro. |
| GET /api/v1/inventory/movements | Página filtrada por producto, almacén, tipo, referencia y fechas. | Búsqueda por documento, actor/correlación según permisos; DTO ligero y traza. |
| GET /api/v1/inventory/movements/{id} | Movimiento y datos de valoración. | Unir documento/origen/auditoría en endpoint trace separado. |
| POST /api/v1/inventory/adjustments | Ajuste justificado e idempotente. | Flujo preview/propuesta/aprobación para agentes y ajustes de mayor riesgo. |
| GET /api/v1/inventory/transfers | Página por estado, origen y destino. | Query, fecha, conteos globales, resumen ligero y filtro de tareas pendientes. |
| GET /api/v1/inventory/counts | Página por almacén/estado. | Fecha/creador y DTO resumen; evitar bajar todas las líneas para la tabla. |
| GET /api/v1/inventory/reorder-rules | Lista no paginada con filtros acotados. | Paginación y cálculo eficiente en backend. |
| GET /api/v1/inventory/replenishment-suggestions | Lista calculada por reglas activas existentes. | Paginación, cobertura de reglas y futuro cálculo de demanda/entradas. |
| GET /api/v1/reports/valuation | Coste, método y reconciliación con permiso financiero. | Separar lectura física de permisos financieros y exposición agentic. |
| GET /api/v1/reports/kardex | Kárdex por producto/almacén/período con máximo actual de 2.000 movimientos. | UX explícita del límite; evaluar cursor para alta rotación. |
| GET /api/v1/audit-events | Auditoría por actor/acción/recurso/outcome/periodo. | Proyección de traza de inventario por producto, almacén, movimiento y referencia. |

### 8.2 DTOs y consultas

- Diseñar DTOs de lista separados del detalle: el resumen no serializa todas las líneas de un documento o entradas de valoración.
- Medir SELECT por página y corregir N+1 en mapeadores que recorren relaciones lazy.
- Usar filtros parametrizados y allowlist de sort; fechas con intervalo [from, to) estable.
- Mantener desempate por ID en sort para que varias filas con el mismo timestamp no salten al cambiar de página.
- Definir tope de page size y respuesta consistente con content, totalElements, totalPages, page y size.
- Para overview y alertas, ejecutar agregaciones en backend; nunca sumar páginas desde React para fabricar contadores globales.
- Añadir requestId/correlationId en respuestas de error y herramientas de agente, evitando que mensajes técnicos lleguen al usuario final.
- Los datos financieros se devuelven solo a roles con permiso: no basta con ocultar una columna después de recibir el JSON.

## 9. Read models y eventos: frescura sin reconstruir todo el sistema

No se recomienda migrar de inmediato el proyecto a event sourcing completo. El backend ya utiliza saldos, movimientos, capas/entradas de valoración, auditoría y Outbox. Microsoft advierte que event sourcing mejora algunos escenarios de auditoría, pero aumenta la complejidad de escrituras, concurrencia, evolución de esquemas y proyecciones. Para el estado actual, conviene un **CQRS ligero**: comandos transaccionales en el dominio actual y lecturas agregadas optimizadas para la experiencia del dueño/agent tools.

### 9.1 Vista de lectura

Cuando las consultas agregadas pesen demasiado, crear una proyección en PostgreSQL que pueda recalcularse y cuya frescura quede visible. Definir:
- source sequence/event ID;
- lastCalculatedAt y watermark procesado;
- número de errores pendientes;
- job de reconstrucción y reconciliación;
- lectura de respaldo desde datos autoritativos cuando la proyección esté obsoleta;
- reglas de invalidación al cambiar producto, almacén, coste, reserva, recepción o venta.

No usar caché con TTL silencioso como fuente de decisiones de compra o de reserva. La recomendación puede usar una proyección algo antigua si lo dice; la ejecución se revalida contra el dominio transaccional.

### 9.2 Outbox

El Outbox transaccional existente debe cubrir todas las operaciones relevantes que cambian inventario, incluidos ajuste manual y conteo contabilizado si no lo cubren ya. El evento se inserta en la misma transacción que confirma el cambio. Los consumidores son al menos una vez; deben deduplicar y preservar el orden por agregado cuando sea necesario.

Eventos propuestos:
- InventoryAdjustmentPosted;
- InventoryCountPosted;
- StockTransferCreated, Dispatched, Received, Cancelled, Reversed;
- GoodsReceiptPosted y SupplierReturnPosted;
- SaleStockConsumed, SaleStockRestored;
- StockReservationCreated, Released, Consumed;
- InventoryReconciliationDetected/Resolved;
- ReplenishmentRiskRaised/Resolved.

La lista debe corresponder a eventos de negocio reales, no a cada GET de disponibilidad. Evitar eventos de “stock actualizado” duplicados si un solo comando produce varios movimientos: el envelope puede tener una operación agregadora y los movimientos individuales enlazados.

Envelope mínimo: eventId, eventType, schemaVersion, occurredAt, aggregateType/aggregateId, operationId, actorId/agentId opcional, product/warehouse refs, delta/unidad cuando aplique, documentRef, correlationId. No incluir secretos ni PII innecesaria.

## 10. Forecasting y decisiones de negocio: avanzar sin inventar inteligencia

La previsión no debe empezar por un LLM. Primero se necesita una base cuantitativa que pueda probarse y explicarse.

### 10.1 Camino de madurez

**Nivel 1 — Reglas deterministas (primera etapa)**
- mínimo/objetivo existente;
- cantidad disponible;
- recepciones/transferencias previstas con fechas confiables;
- alertas por falta de regla, stock bajo o reserva elevada.

**Nivel 2 — Indicadores históricos**
- ventas netas y devoluciones por SKU/almacén;
- días con stock cero, días sin operación, fecha de última venta;
- rotación y consumo promedio usando una ventana configurable;
- inventario con poca salida y exceso, siempre con coste y moneda si se calcula valor.

**Nivel 3 — Pronóstico evaluado**
- demanda por producto/almacén y horizonte;
- estacionalidad según histórico suficiente;
- lead time histórico real de proveedor en lugar de solo el leadTimeDays manual;
- safety stock / reorder point según variabilidad y nivel de servicio elegido;
- forecast bias, error de predicción y backtesting frente a una línea base;
- intervalos de confianza, fecha de cálculo y explicación.

**Nivel 4 — Agente de planificación**
- consulta el forecast y los compromisos;
- explica por qué propone una cantidad;
- prepara una o varias órdenes de compra o transferencias como borradores;
- presenta efecto estimado sobre stockouts y exceso;
- solicita aprobación y ejecuta solo una propuesta vigente dentro de la política.

### 10.2 Métricas para el dueño

Las métricas candidatas y su fórmula deben acordarse antes de mostrarlas:
- **Días de cobertura:** available / demanda media diaria bajo una ventana válida; no calculable si la demanda estimada es nula o no fiable.
- **Riesgo de quiebre:** probabilidad o regla de stockout antes de llegada, no una certeza si depende de un forecast.
- **Exceso:** onHand/valor por encima del máximo/objetivo con plazo y contexto del producto.
- **Stock lento o muerto:** reglas explícitas por días sin salida y categoría; productos nuevos/estacionales requieren tratamiento separado.
- **Capital inmovilizado:** importe de coste con moneda específica y definición de “exceso”; no confundirlo con pagos a proveedores.
- **Eficiencia de inventario:** tasa de quiebres, exactitud de conteo, tiempo de ciclo y diferencia de valoración.

Si la métrica no dispone de datos, mostrar “No calculable todavía / falta histórico / falta regla”, no un cero verde. Cada indicador necesita owner, definición, fuente, actualización, tests y política de permiso.

## 11. Seguridad y gobierno de agentes

La seguridad no puede basarse en instrucciones de prompt. La especificación de MCP y los frameworks de agentes permiten tools, pero sus anotaciones y guardrails no reemplazan la autorización backend. OWASP documenta riesgos de abuso de tools, privilegios excesivos, inyección indirecta, cascadas de errores y agencia excesiva.

Controles obligatorios antes de permitir mutaciones:
- autorización backend en cada llamada;
- herramienta con esquema cerrado y validación de argumentos;
- scopes distintos para leer stock, ver costes, preparar documentos y ejecutar movimientos;
- límite de cantidad/valor, almacén y número de operaciones por ejecución;
- aprobación humana ligada al hash de la propuesta exacta;
- revalidación de stock/estado/precondiciones al ejecutar;
- Idempotency-Key de ámbito de operación y deduplicación de eventos;
- rate limit y presupuesto de tokens/calls;
- registro de la entrada de usuario, tool, argumentos normalizados, decisión de policy, resultado, duración y correlación, aplicando minimización de datos;
- prueba contra indirect prompt injection en nombres, descripciones, motivos y documentos;
- gestión de secretos fuera de contexto y de la salida del modelo;
- kill switch por agente/acción y parada global;
- evaluaciones offline y red-team antes de desplegar autonomía;
- retención/privacidad acordes al dato de negocio y a la configuración del proveedor de IA.

### Matriz de autonomía sugerida

| Nivel | Capacidad | Ejemplo de inventario |
|---|---|---|
| A0 | Leer | Consultar saldos, operaciones y traza autorizada. |
| A1 | Recomendar | Explicar riesgo y proponer reposición con fuente de evidencia. |
| A2 | Preparar | Guardar borrador de orden/traslado/conteo; no mueve stock. |
| A3 | Ejecutar tras aprobación | Ejecutar el comando exacto aprobado con condiciones vigentes. |
| A4 | Ejecutar automáticamente bajo límites | Solo acciones repetibles de riesgo bajo, con política explícita, cuota, alertas y kill switch. No es objetivo de las primeras fases. |

El dueño debe poder revisar: qué pidió, qué datos consultó el agente, qué propuso, impacto previsto, qué política se aplicó, quién aprobó y qué resultado real tuvo. Una respuesta conversacional sin esta trazabilidad no es suficiente para operaciones de inventario.

## 12. Escenarios funcionales que deben diseñarse y probarse

| Escenario | Conducta esperada |
|---|---|
| El dueño abre Inventario | Primer viewport útil con alertas y contexto; no descarga todos los balances/históricos. |
| Múltiples unidades de medida | No existe total engañoso de “unidades”; cada cantidad muestra unidad. |
| Varias monedas | Valoración agrupada por currency. |
| Usuario operativo sin permiso de costes | Ve cantidades; costes y valoración sensibles no llegan al navegador/modelo. |
| GET devuelve 403 | Estado de permiso explícito, nunca “sin registros”. |
| Consulta tarda/falla | Skeleton/refresh/error y Reintentar sin borrar filtros ni datos anteriores. |
| Miles de SKUs | Búsqueda y paginación del servidor; KPI agregado en SQL. |
| La disponibilidad cambió después de una consulta | El comando vuelve a validar y el frontend muestra conflicto recuperable. |
| Dos ventas/operaciones simultáneas | No se supera el saldo disponible ni se duplica el movimiento. |
| Orden de compra aprobada pero aún no recibida | No se presenta como stock físico; se muestra como entrada futura solo si el contrato lo respalda. |
| Traslado en tránsito | Salida en origen y pendiente de recepción en destino visibles por separado. |
| Reserva WhatsApp expira | La liberación ocurre una sola vez y se conserva la referencia al pedido. |
| Suscripción es solo servicio | Cobro no crea stock físico. |
| Conteo se vuelve obsoleto | Conflicto con SKU identificado; no contabilizar delta del snapshot antiguo. |
| Ajuste de salida excede disponible por reserva | Backend rechaza, mantiene cantidades y da causa legible. |
| Producto sin regla | Etiqueta “sin regla de reposición”; no declarar que está bien. |
| Un agente quiere crear una orden | Lee evidencia, prepara borrador y deja revisar cantidad/proveedor antes del envío. |
| Agente intenta ajustar stock | Debe presentar propuesta; requiere permiso, política y aprobación definida. |
| Datos recuperados contienen instrucciones maliciosas | Se tratan como contenido no confiable, no como una instrucción del agente. |
| Aprobación y saldo cambian antes de ejecutar | Propuesta STALE y nueva revisión; no ejecutar con precondiciones obsoletas. |
| Tool retorna timeout después de confirmar | Consultar estado por operationId/idempotency y no repetir side effect. |
| Doble evento de Outbox | Consumidor deduplica por eventId. |
| Proyección de overview está atrasada | Se muestra dataFreshness y se bloquea cualquier decisión no segura; el comando final usa datos transaccionales. |
| Documento origen no resuelve | Mostrar referenceType/Id y evidencia disponible; nunca eliminar movimiento histórico. |
| Modo teléfono del almacén | Tareas rápidas, botones grandes, lector/entrada manual y pasos confirmados; sin tabla ancha para operar. |

## 13. Plan por fases y dependencias

### Fase 0 — Corregir lo incorrecto antes de añadir IA
- Eliminar/renombrar KPI de suma de cantidades heterogéneas.
- Alinear permisos GET con inventory:read, audit:read y reports:financial.
- Separar las cargas por pestaña y dejar de usar listAllPages para tablas.
- Añadir skeletons, forbidden/empty/error y paginación visible.
- Mover operaciones a la navegación principal.
- Alinear target/minimum y añadir pruebas de contrato.

**Salida:** Inventario deja de engañar con datos agregados, falla de forma comprensible y escala mejor.

### Fase 1 — Centro de mando del dueño
- Implementar vistas de primer nivel y el nuevo orden visual.
- Endpoint command-center con agregados deterministas y alertas trazables.
- Últimos movimientos como muestra pequeña enlazada al kárdex.
- Indicadores con fórmula, timestamp, unidad, moneda y permiso.
- Acciones rápidas con deep links a operaciones.
- UX responsive y prueba con el dueño usando casos reales.

**Salida:** se puede decidir y actuar desde una única pantalla sin perder contexto.

### Fase 2 — Dominio consultable y trazabilidad completa
- Search/q, filtros, DTOs ligeros y paginación de servidor.
- Trace de movimiento a documento/auditoría.
- Historial de ajustes y estados operativos completos.
- Reconciliación de saldo/valoración por permiso.
- Métricas de performance y corrección de N+1.

**Salida:** cada cambio de stock se puede explicar y rastrear.

### Fase 3 — Contratos agent-ready sin escritura autónoma
- OpenAPI estable, schemas/versiones, errores normalizados y operationId.
- Tool catalog de solo lectura, función a función, con scopes y ejemplos.
- Logs/trazas de tool call y link al dato fuente.
- Pruebas de evaluación y ataques de prompt injection.
- Adaptador MCP read-only opcional; REST sigue como API primaria.

**Salida:** un agente puede contestar usando datos autorizados sin tocar stock.

### Fase 4 — Propuestas y aprobación humana
- Persistir AgentActionProposal (o entidad equivalente).
- Previsualización para compras, traslados, ajustes y conteos.
- Aprobación ligada a snapshot/hash, expiración y actor.
- Revalidación y ejecución idempotente con status consultable.
- UI “Revisiones propuestas por automatización/agente”.

**Salida:** agentes preparan el trabajo; las personas autorizadas mantienen control.

### Fase 5 — Planificación y agentes acotados
- Historial de demanda/lead-time y forecasts con backtesting.
- Exceso, stock lento, cobertura, riesgo de quiebre y coste de stockout.
- Reposición asistida con borradores de compra/transferencia.
- Autonomía limitada en tareas de bajo riesgo tras métricas y política de aprobación.
- Evaluar A2A solo si existe más de un sistema de agentes con necesidad real de cooperación.

**Salida:** el sistema no solo informa el stock actual; ayuda a anticipar y ejecutar decisiones bajo control.

### Fase 6 — WMS avanzado si la operación lo justifica
- Ubicaciones múltiples, bins, lotes, series, caducidad, cuarentena/merma, escaneo y picking.
- Cambiar clave de saldo y de movimientos si el dominio lo requiere, con migración de MAIN existente.
- Mobile workflow para recepción, put-away, conteo cíclico, picking y envío.
- Métricas de exactitud por ubicación y tiempos de proceso.

No implementar esta fase por imitación visual: solo si el negocio necesita trazabilidad o ejecución física avanzada.

## 14. Pruebas, métricas y Definition of Done

### Frontend
- Resumen, Existencias, Movimientos, Operaciones, Auditoría, skeleton, refresh, error, forbidden, empty y filtros.
- Paginación y URL restoration; solicitudes que terminan fuera de orden.
- Responsive 360 px, 768 px y escritorio; teclado, foco y anuncios de estado.
- Roles de lectura, operación, finanzas y auditoría.
- KPI correctos para unidades/monedas; no usar números de la página actual como totales.
- Alerta que se vuelve obsoleta y abre un documento, con refresh de evidencia.
- Formularios de ajuste, conteo y traslado con confirmación, conflicto y reintento.
- Consola de propuestas con hash/impacto/permiso y estado stale/rejected/executed.

### Backend
- RBAC en cada endpoint; no dependencia de filtros del frontend.
- Filtros y sort allowlist, size límite, estabilidad en paginación y DTO de resumen.
- Agregados correctos y eficientes, por almacén, unidad, periodo y currency.
- Concurrencia, bloqueo de saldos y cero stock negativo/consumo de reservas ajenas.
- Idempotencia con mismo payload, conflicto con payload distinto y recovery tras timeout.
- Conteos con snapshot obsoleto y atomicidad de posting.
- FIFO/promedio ponderado en recepción, venta, retorno, ajuste y transferencias.
- Reconciliación del saldo y cantidad valorizada.
- Outbox en la misma transacción; duplicate delivery no produce side effect duplicado.
- Tests de herramienta de agente: argumento inválido, permiso denegado, usuario de otro ámbito, recurso no encontrado, prompt injection, rate limit, timeout y propuesta obsoleta.
- Aprobación del agente ligada a los argumentos exactos y revalidación en ejecución.
- Ningún coste llega a una tool sin scope financiero.
- Telemetría de cada herramienta con actor, policy, operationId y request/correlationId.

### Métricas del dueño y operación
- Tiempo a primera vista útil p50/p95.
- Tiempo de resolución de alertas.
- Stockout rate y duración de falta de disponibilidad.
- Cobertura de reglas de reposición.
- Exactitud del conteo: porcentaje de líneas exactas y variación absoluta.
- Tiempo desde recepción hasta disponibilidad/put-away cuando ese flujo exista.
- Antigüedad de transferencias en tránsito.
- Diferencias pendientes de valoración y tiempo de resolución.
- Consultas y bytes por entrada al módulo.
- Número de ejecuciones fallidas/duplicadas de comandos.
- Porcentaje de recomendaciones aceptadas y razones de rechazo.
- Calidad del forecast por SKU y horizonte frente a línea base.
- Acciones de agentes aprobadas, rechazadas, expiradas y stale; incidentes de seguridad y kill switches.

### Criterios de aceptación finales
- El primer viewport prioriza preguntas/acciones de dueño, no una tabla larga y no un simple mosaico de KPI.
- Existencias, reservas, disponibilidad y entradas previstas no se confunden.
- Todas las cifras tienen alcance, unidad/moneda, fecha de frescura y fórmula accesible.
- No se descargan colecciones completas en el browser para construir listados o KPI globales.
- Cada movimiento enlaza al documento y conserva su evidencia.
- La API de stock opera de igual manera desde React, n8n y un futuro agente autorizado.
- Agentes no pueden escribir mediante SQL ni usar privilegios implícitos.
- Acciones con riesgo generan propuestas tipadas, políticas, aprobación exacta, revalidación e idempotencia.
- Si falta data, regla o permiso, la UI lo comunica explícitamente; nunca lo dibuja como cero o estado sano.
- Operaciones internas siguen siendo coherentes aunque fallen proveedores de IA, n8n o entrega de eventos.
- Las pruebas de regresión de coste, reservas, compras, ventas, devoluciones y transferencias continúan pasando.

## 15. Referencias internas del repositorio

- app-inventary-ecuapp/src/App.jsx
- app-inventary-ecuapp/src/api.js
- app-inventary-ecuapp/src/features/inventory/InventoryOperations.jsx
- app-inventary-ecuapp/src/ui/Skeleton.jsx
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/StockBalance.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/StockMovement.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryCostingService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryCountService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/ReorderService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/transfers/StockTransferService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/InventoryBalanceReportController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/OperationalReportService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/audit/AuditService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxEvent.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxEventRepository.java

Documentos de dirección:
- PROPUESTA_UX_PRODUCTOS_V2.md
- PROPUESTA_UX_VENTAS_V2.md
- PROPUESTA_UX_SUSCRIPCIONES.md
- PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md
- PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md
- PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md
- PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md
- PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md
- PROPUESTA_INVENTARIO_CENTRO_CONTROL_Y_AUDITORIA_V2.md
- PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md: datos SRI, separación de almacén físico/establecimiento tributario, CRUD de proveedores/clientes y unidades.

## 16. Límite de esta entrega

Este commit crea la propuesta V3. No implementa todavía el rediseño, los endpoints, la proyección del command center, la persistencia de propuestas de agente, el adaptador MCP, las migraciones de lotes/ubicaciones ni los workflows n8n. Todo elemento nuevo se considera propuesto hasta que exista código y pruebas de aceptación.

---

## Addendum fiscal — El inventario debe enlazarse con el estado real de facturación

Contrato canónico: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

La venta, el movimiento de salida del inventario, la factura fiscal, la anulación fiscal, la devolución de mercadería, la nota de crédito y el reembolso no son una única transacción en estado. Inventario debe mantener sus movimientos idempotentes y permitir seguir el documento origen, pero mostrar por separado el estado fiscal confirmado por el backend externo.

Para el modo dueño/agente:
- el command center puede alertar de ventas con fiscalización pendiente/rechazada, establecimiento emisor no elegible, anulación solicitada pendiente o nota de crédito de devolución por resolver;
- esas alertas se basan en estados/eventos confirmados, no en una inferencia del LLM;
- los datos de establecimiento/punto de emisión se obtienen de la configuración fiscal válida; warehouseId sigue representando dónde está el stock;
- una herramienta de agente solo puede consultar estado y preparar una propuesta. No puede emitir, anular ni generar un secuencial por SQL o por acciones arbitrarias.
- la herramienta fiscal oculta datos sensibles según permisos y devuelve código/estado, evidencia, timestamp, documento origen y correlationId.

La cancelación operativa no debe presentarse como factura anulada. La devolución física no equivale a nota de crédito ni reembolso. La resolución NAC-DGERCGC25-00000017 modifica al día 7 del mes siguiente el plazo de anulación del artículo 3 desde el 1 de enero de 2026; el backend fiscal aplica las reglas consolidadas y las restricciones de consumidor final. No duplicar reglas fiscales en el frontend ni en un agente.

---

## Addendum fiscal adicional — Proveedor tecnológico SRI y emisiones agent-ready

Contrato fiscal canónico: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

Si el sistema de facturación cae dentro del ámbito de la Resolución NAC-DGERCGC26-00000027, el RUC de su proveedor debe aparecer en información adicional del comprobante conforme a la ficha técnica vigente. El backend fiscal gestiona y valida esa configuración central. Los agentes no pueden escoger o cambiar ese RUC en una venta, ni componer textos/atributos fiscales directamente.

El centro de mando puede señalar un fallo de configuración fiscal, una factura rechazada o una emisión en proceso, pero solo con evidencia del backend. Antes de permitir que un agente prepare solicitudes de emisión, comprobar con el proveedor y el responsable tributario el registro que exige la norma y la presencia del RUC tecnológico en la ficha/documento autorizado. Fuente: [SRI, resolución 26-00000027](https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1).

---

## Revisión V3.1 — Bodegas relacionadas con varios establecimientos

El dominio de inventario adopta una relación **muchos-a-muchos** entre Warehouse y SriEstablishment a través de WarehouseEstablishmentAssignment. Un establecimiento puede operar con varias bodegas y una bodega puede servir a varios establecimientos; además, las bodegas internas pueden existir sin asociación fiscal.

La asociación se tipa y mantiene por vigencia: STOCK_SERVES, SALES_OPERATIONS, PICKUP, FULFILLMENT, RECEIVING, RETURN_ACCEPTANCE y REPORTING_SCOPE son ejemplos que requieren un contrato explícito. No usar un booleano ni un único establishmentId en Warehouse para expresar todas las relaciones.

**La asociación no es propiedad del stock ni una autorización fiscal.** Saldos, reservas, movimientos y valoración se cuentan por su llave física real de inventario. Una bodega vinculada a tres establecimientos no triplica unidades, coste, valor ni disponibilidad. Un informe de inventario filtrado por establecimiento debe explicar que está mostrando stock físico de las bodegas vinculadas, y deduplicar cada balance por su clave real. Una cifra de stock atribuida a un establecimiento requiere una política explícita de propiedad/asignación, no el puente operacional.

Cuando una ubicación compartida dé servicio a distintos RUC/entidades legales, el modelo debe incluir owner/legalEntity en saldo, movimiento, reserva, compra, venta y valoración, o bloquear ese escenario hasta ofrecer aislamiento. La ubicación compartida no autoriza mezclar la propiedad de mercancía ni los permisos financieros.

El centro de mando puede presentar:
- incidencias de establecimientos cerrados/Suspendido relacionados con una bodega sin desactivar la bodega ni borrar su historia;
- stock físico por bodega y un indicador separado de establecimientos atendidos;
- traslados y reposición entre bodegas;
- riesgo de abastecimiento por establecimiento solo si la atribución y la demanda están definidas;
- discrepancias entre el contexto fiscal de una venta y el establecimiento realmente devuelto por el backend fiscal.

Para venta, retiro, recepción y devolución, mantener warehouseId y billingContextId por separado. Si una bodega sirve a varios establecimientos, no seleccionar arbitrariamente el primer establecimiento al emitir; la regla de negocio explícita propone el contexto y el backend fiscal valida el resultado.

Pruebas críticas: N bodegas por establecimiento; N establecimientos por bodega; una bodega compartida por RUC distintos; cambio de asociación dentro de periodo; establecimiento cerrado con stock histórico; pedido multi-bodega; devolución recibida en bodega diferente; agregación que nunca duplica saldos.
