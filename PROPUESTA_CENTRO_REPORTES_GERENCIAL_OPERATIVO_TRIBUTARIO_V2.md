# Propuesta V2 — Centro de reportes gerencial, operativo, analítico y tributario para Ecuador

**Fecha de análisis:** 29 de octubre de 2026  
**Repositorio:** JhonnyMinian92/inventary · rama master  
**Estado:** propuesta estratégica ampliada; pendiente de implementación. La V2 sustituye V1 y prevalece cuando exista diferencia entre ambas.  
**Pantalla actual:** app-inventary-ecuapp/src/features/reports/ReportsCenter.jsx  
**Backend actual:** microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/*


> **V2 vigente.** Esta versión amplía y reemplaza la propuesta [V1](PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V1.md). Resuelve explícitamente la relación muchos-a-muchos entre establecimientos SRI y bodegas, define cómo evitar duplicar stock/importes al reportar, e incorpora el calendario y las reglas tributarias actualizadas verificadas a 29 de octubre de 2026. El mapa entre bodega y establecimiento es una asociación operativa; no convierte una bodega en establecimiento tributario ni selecciona automáticamente el emisor de una factura.


> Objetivo del producto: que el dueño pueda dirigir el negocio, el responsable comercial analizar ventas y rentabilidad, el jefe de bodega controlar existencias y operaciones, y el contador preparar, conciliar y revisar información antes de sus declaraciones. El sistema puede automatizar una parte importante del trabajo repetitivo, pero no debe asegurar cumplimiento tributario ni presentar/firmar declaraciones en nombre del contribuyente sin un proceso autorizado, datos completos y validación profesional.

## 1. Decisión ejecutiva

La pantalla actual es un centro básico de seis reportes. V2 la transforma en una plataforma de decisión y cierre del negocio, modelando explícitamente los ámbitos RUC/entidad legal, establecimientos SRI, bodegas, propiedad de inventario, ventas, compras, suscripciones y documentos fiscales.

1. **Dirección del negocio:** qué vendemos, qué deja margen, dónde se pierde una venta, qué capital está inmovilizado y qué requiere atención.
2. **Control comercial y de bodega:** conectar productos, ventas, compras/recepciones, devoluciones, inventario, reservas, traslados y suscripciones para explicar el rendimiento y sus causas.
3. **Preparación contable y tributaria ecuatoriana:** conciliar comprobantes internos con los documentos fiscales autorizados por el backend externo, preparar papeles de trabajo para IVA, retenciones, ATS e impuesto a la renta cuando correspondan al contribuyente, documentar diferencias y preparar archivos/datos con esquemas vigentes.
4. **Exploración flexible y trazable:** correlacionar dos, tres o cuatro dominios con medidas y dimensiones definidas, guardar reportes, programarlos y bajar del indicador al documento original sin ejecutar SQL arbitrario ni mezclar registros de forma incorrecta.

La pantalla no debe limitarse a mostrar muchas tarjetas. Debe responder tres preguntas en lenguaje de negocio:

- **¿Qué está pasando?** Resultados, evolución, incidencias y calidad de datos.
- **¿Por qué ocurre?** Desglose por producto, cliente, proveedor, canal, almacén, suscripción, periodo y estado fiscal.
- **¿Qué debo hacer ahora?** Resolver una factura pendiente, corregir una diferencia, investigar una devolución, revisar el bajo stock o preparar un cierre.

### Límite importante del objetivo “reemplazar al jefe de ventas, al jefe de bodega y al contador”

El sistema puede sustituir muchas consultas manuales, informes recurrentes, conciliaciones simples y alertas que hoy requieren horas de trabajo. Pero el repositorio inspeccionado todavía no tiene un libro contable completo, conciliación bancaria, módulos completos de pagos/cuentas por cobrar/pagar, retenciones fiscales completas ni el vínculo fiscal real de cada venta. No es correcto afirmar que con las seis APIs actuales puede elaborar todas las declaraciones o estados financieros.

La propuesta es construir primero una plataforma confiable de análisis; después añadir las fuentes contables/fiscales necesarias y comprobar cada salida contra la normativa y el dato oficial. La persona responsable de contabilidad debe poder revisar, aprobar y documentar la declaración preparada. La automatización no sustituye la responsabilidad legal, la firma ni la presentación efectiva de una declaración.

## 2. Investigación del trabajo contable que Reportes debe apoyar

Un proceso contable no es solo sumar ventas. Para revisar cifras y preparar obligaciones de un contribuyente ecuatoriano, normalmente hace falta recorrer las siguientes etapas, adaptadas al tipo de contribuyente, sus obligaciones del RUC, régimen y actividad:

1. **Recolectar y custodiar evidencia:** comprobantes de venta, retención y documentos complementarios; facturas recibidas de proveedores; notas de crédito/débito; órdenes/recepciones; pagos; movimientos bancarios; contratos/soportes; nómina y otros documentos aplicables.
2. **Validar y clasificar:** identificación de emisor/receptor, fecha, estado fiscal, moneda, bases imponibles, códigos/tarifas tributarias, retenciones, naturaleza de ingreso/gasto/activo/pasivo, centro de costo y cuenta contable.
3. **Conciliar:** documento interno contra documento fiscal autorizado/recibido; ventas contra notas de crédito/anulaciones; compras contra recepciones y documentos de proveedor; saldos de inventario contra costeo; cobros/pagos contra bancos; retenciones contra comprobantes y su declaración.
4. **Registrar contablemente:** asiento de diario con debe/haber, cuentas, fecha de devengo, soporte, centro de costo, tercero y estado de aprobación. Un documento de compra o venta por sí solo no prueba el pago bancario.
5. **Cerrar el periodo:** revisar corte, documentos pendientes, transacciones duplicadas, saldos, inventario físico/valorado, provisiones, depreciación y otros ajustes aplicables.
6. **Determinar obligaciones y preparar papeles de trabajo:** calcular cifras a declarar a partir de fuentes conciliadas, aplicar la versión de reglas y formularios correspondiente, producir diferencias explicables y una lista de datos que todavía falten.
7. **Revisar, aprobar y presentar:** la persona autorizada valida la salida, presenta por el canal oficial permitido y guarda el comprobante de presentación/pago cuando existan. Un borrador calculado por software no equivale a una declaración presentada.

La norma contable aplicable depende del tipo de entidad y del marco que le corresponda. Por ejemplo, IAS 1 describe estados financieros completos (situación financiera, resultados/otro resultado integral, cambios en patrimonio, flujos de efectivo y notas), mientras IAS 2 trata la valoración del inventario y el reconocimiento del costo cuando se vende. Estas referencias ayudan a diseñar los datos, pero la política concreta de la entidad en Ecuador debe configurarse conforme a su régimen y marco contable aplicable.

Referencias:
- IFRS Foundation, IAS 1: https://www.ifrs.org/issued-standards/list-of-standards/ias-1-presentation-of-financial-statements/
- IFRS Foundation, IAS 2 Inventories: https://www.ifrs.org/issued-standards/list-of-standards/ias-2-inventories/
- IFRS Foundation, IFRS 15 Revenue from Contracts with Customers: https://www.ifrs.org/issued-standards/list-of-standards/ifrs-15-revenue-from-contracts-with-customers/
- Superintendencia de Compañías, Valores y Seguros, normativa NIIF aplicable a compañías sujetas a su control: https://www1.supercias.gob.ec/portalscvs/Institucion/SectorSocietario.php?seccion=normativas

## 3. Investigación del SRI: cómo debe reflejarse en el producto

El sistema no debe tener un calendario fiscal fijo igual para todos. Las obligaciones dependen, entre otros factores, del RUC y datos del contribuyente, tipo de persona/entidad, régimen, estado, actividad, condición de obligado a llevar contabilidad, agente de retención, contribuyente especial, existencia de empleados, transacciones y normativa vigente.

Fuentes oficiales consultadas el 29 de octubre de 2026:

- SRI — Formularios e instructivos: https://www.sri.gob.ec/formularios-e-instructivos
- SRI — Anexos y guías: https://www.sri.gob.ec/formularios-e-instructivos1
- SRI — Facturación electrónica: https://www.sri.gob.ec/web/intersri/facturacion-electronica
- SRI — Retenciones en la fuente: https://www.sri.gob.ec/retenciones-en-la-fuente
- SRI — Reglas de anulación de comprobantes electrónicos: https://www.sri.gob.ec/detalle-noticias?idnoticia=1160&marquesina=1
- SRI — Proveedores de sistemas/servicios de facturación electrónica, Resolución NAC-DGERCGC26-00000027: https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1

### 3.1 Obligaciones y artefactos que Reportes debe poder preparar

| Artefacto/obligación | Para qué sirve el centro de reportes | Requisitos y límites |
|---|---|---|
| Declaración de IVA (formulario vigente aplicable; el portal SRI lista formatos mensuales/semestrales y guía actualizada el 15-jun-2026) | Preparar ventas/compras por clasificación fiscal, base, impuesto, crédito tributario, retenciones recibidas y ajustes según el régimen/actividad. | Debe usar líneas fiscales y comprobantes reales; un subtotal comercial no determina por sí solo la base/impuesto declarable. |
| Declaración de retenciones en la fuente de impuesto a la renta (Formulario 103, para obligados) | Conciliar pagos/acreditaciones, bases sujetas, porcentajes/códigos aplicables y comprobantes de retención emitidos. | El sistema actual no tiene el libro completo de pagos ni de retenciones; hoy no puede liquidar un Formulario 103 completo. |
| Anexo Transaccional Simplificado (ATS), cuando corresponda | Preparar el conjunto de compras, ventas, retenciones y demás operaciones exigidas por esquema/catálogo vigente, detectando documentos faltantes o duplicados. | El SRI publica ATS V1.19.0 actualizado el 23-sep-2026 para periodos fiscales septiembre 2026 en adelante; catálogo actualizado el 06-ago-2026. Se necesita versionar ficha, catálogo, XSD y validaciones por periodo. |
| Impuesto a la renta anual (formulario que corresponda a persona natural/sociedad y obligaciones) | Preparar ingresos, costos, gastos deducibles/no deducibles, activos/pasivos y conciliación tributaria cuando exista un libro contable completo. | Las ventas e inventarios no bastan: se requieren cuentas/ajustes, gastos, depreciaciones, nómina y otras fuentes aplicables. |
| RDEP/Formulario 107, si existen obligaciones laborales/de retención en relación de dependencia | Preparar registros anuales por trabajador y conciliar con retenciones mensuales según normativa vigente. | El repositorio no tiene módulo de nómina/empleados; no puede producir este anexo con los datos actuales. La página SRI lista software RDEP para periodo fiscal 2026 actualizado el 17-mar-2026. |
| Comprobantes de retención electrónicos | Controlar emitidos/recibidos, plazo, entrega, estado fiscal y relación con la transacción de origen. | El backend de facturación debe soportar estos documentos; Inventary debe guardar la referencia y estados recibidos. |
| Facturas/nota de crédito/anulaciones | Conciliar documentos internos con estado fiscal confirmado y trazabilidad del comprobante original. | La integración fiscal externa descrita en PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md sigue propuesta, no implementada. |
| Reportes societarios/estados financieros, si corresponden | Preparar saldos contables, balances y notas desde el libro contable y reglas contables aplicables. | El sistema actual no incluye libro mayor, catálogo de cuentas ni asientos generalizados. No fabricar estados financieros con solo ventas y costeo. |
| Calendario de declaraciones/pagos | Indicar fecha límite, periodo, fuente legal, estado, responsable y tareas pendientes. | Derivar obligaciones del perfil fiscal y un catálogo de reglas versionado; no asumir que todas las obligaciones aplican a todos los clientes ni codificar días fijos universales. |

**Regla de producto:** mostrar “No aplica según perfil/configuración verificada”, “Pendiente de configurar obligaciones”, “Datos insuficientes”, “En preparación”, “Pendiente de revisión”, “Aprobado”, “Presentado” y “Pago confirmado” como estados diferentes. No mostrar una declaración como “presentada” porque se exportó CSV o JSON.

La aplicación debe distinguir: cálculo preliminar, datos conciliados, borrador listo para revisión, archivo generado, archivo aceptado por el validador, envío realizado, aceptación/presentación confirmada y pago confirmado. Los nombres definitivos dependen del canal real de presentación y su respuesta.

### 3.2 Reglas tributarias versionadas

Cada regla tributaria que afecte a una preparación guarda nombre/código, autoridad, referencia normativa, URL, vigencia desde/hasta, periodo al que aplica, versión del esquema/formulario/catálogo, hash del artefacto oficial, fecha de descarga y responsable de revisión. Cada resultado conserva la versión de reglas con que se calculó.

Cuando el SRI publica un nuevo formulario, catálogo ATS, esquema XML/XSD o regla, el sistema no recalcula silenciosamente declaraciones ya preparadas. Debe detectar que una preparación utiliza una versión anterior, marcar si hay que revisar y permitir reproducir el cálculo con su versión histórica.

### 3.3 Facturación electrónica del backend externo

Contrato de referencia: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

Inventary solicitará la facturación al backend externo y guardará el número real, clave de acceso, establecimientos/punto utilizados, autorización, fecha, tipo de documento y estado devuelto. No genera XML ni numeración fiscal local. Reportes debe comparar ventas internas con documentos autorizados/rechazados/anulados, notas de crédito, documentos pendientes y cambios de estado. La venta interna no debe contarse como factura autorizada mientras el backend externo no confirme ese estado.

La devolución física, la anulación fiscal de un comprobante emitido con error/no realizada la operación, la nota de crédito por una operación válida y el reembolso de dinero son procesos distintos. El centro tributario muestra sus estados por separado y no resta dos veces una devolución por sumar la venta neta, la nota de crédito y el movimiento físico como si fueran tres devoluciones económicas.

Además, el SRI publicó la Resolución NAC-DGERCGC26-00000027 sobre proveedores de sistemas/servicios de facturación electrónica. Cuando aplique, se requiere incluir el RUC del proveedor en información adicional del comprobante según la ficha vigente y validar el registro/condiciones correspondientes. El sistema debe verificar la aplicabilidad con el responsable fiscal y el backend emisor; no introducir este dato manualmente por cada venta.

## 4. Auditoría de la pantalla de Reportes actual

### 4.1 Lo que existe hoy

La pantalla está en app-inventary-ecuapp/src/features/reports/ReportsCenter.jsx. Tiene seis pestañas: Balance de inventario, Ventas y margen, Compras y devoluciones, Kárdex, Valoración de stock y Stock bajo.

Los filtros disponibles son fecha desde/hasta en informes por periodo, almacén, producto, proveedor y zona horaria. La interfaz permite exportar CSV. El resultado se solicita al backend al enviar filtros y se muestran estados de error/permiso.

El backend está en InventoryBalanceReportController y OperationalReportService, con DTOs de resumen y detalle. Todas las rutas /api/v1/reports requieren actualmente reports:financial.

### 4.2 Hallazgos críticos

| Hallazgo comprobado | Evidencia | Consecuencia |
|---|---|---|
| La pantalla contiene seis informes fijos; no existe constructor para correlacionar dominios. | REPORT_TABS y ramas de renderizado en ReportsCenter.jsx. | No permite combinar ventas, productos, compras y suscripciones según las preguntas del usuario. |
| Ventas y compras devuelven detalle con un máximo de 500 filas/combinaciones por consulta. | OperationalReportService.MAX_PRODUCT_ROWS = 500; las respuestas incluyen productsTruncated/itemsTruncated. | Los totales agregados pueden cubrir el periodo, pero tabla y CSV no son base de un cierre completo si están truncados. El límite debe ser visible y el backend debe soportar extracción completa segura. |
| El Kárdex limita el informe a 2.000 movimientos y rechaza rangos mayores. | MAX_KARDEX_LINES = 2000. | Un almacén de alta rotación puede no cerrar un periodo largo en un solo informe. |
| La interfaz descarga todas las páginas de balance y valoración para construir resúmenes/CSV. | Loops de pageResults en runReport. | Mucha carga, latencia y memoria de navegador; los agregados deben calcularse en backend y los exports ser jobs del servidor. |
| El reporte de stock bajo devuelve todas las sugerencias de reglas activas sin paginación y la UI considera el tamaño recibido como cantidad de productos bajo mínimo. | /reports/low-stock filtra reorder.suggestions. | No incluye necesariamente productos sin regla de reposición; podría parecer que no hay riesgo cuando faltan reglas. |
| El reporte de ventas muestra ventas internas, anulaciones internas y devoluciones; no desglosa por estado real de documento fiscal. | SALES_SUMMARY_SQL y SalesReport. | No sirve por sí solo para conciliar con cifras autorizadas por el SRI. |
| El reporte presenta resúmenes monetarios en una única cantidad pese a que productos/filas guardan currency. | SalesSummary no contiene moneda y la UI usa money() con USD por defecto; SQL suma totales sin grupo por moneda. | Si hay operaciones multimoneda, métricas pueden mezclar monedas o etiquetarlas como USD. Bloqueante financiero: agrupar por moneda o impedir la mezcla. |
| Las compras reportadas son recepciones contabilizadas y devoluciones a proveedor, no compras fiscales completas ni pagos al proveedor. | PURCHASES_SQL usa goods_receipt y purchase_return; PurchasesReport no contiene facturas de proveedor, retenciones ni pagos. | “Importe recibido” no significa factura fiscal recibida, saldo por pagar ni dinero pagado. |
| Balance histórico y stock actual se presentan cerca. | DTO incluye apertura/cierre del periodo y currentStock/currentAvailable. | Debe quedar clara la fecha del cierre histórico; no atribuir movimientos posteriores al periodo. |
| El histórico de valoración inicial tiene limitaciones. | La documentación del backend indica que ciertas capas iniciales se estimaron con product.costPrice; no reconstruyen coste histórico real. | Debe mostrarse calidad/cobertura histórica y no simular coste exacto cuando no existe. |
| Kárdex requiere producto y almacén específicos. | La API /reports/kardex exige productId/warehouseId/from/to. | No existe un reporte masivo de todos los productos sin job/export dedicado. |
| El balance exige warehouseId aunque la UI permite “Todos los almacenes”. | InventoryBalanceReportService lanza error si warehouseId es null, pero ReportsCenter deja la selección vacía. | El formulario permite una selección visual que el backend rechaza. Se debe hacer consistente el filtro o crear API multi-almacén real. |
| Todas las APIs de reportes requieren reports:financial, incluidos informes operativos de stock. | @PreAuthorize de InventoryBalanceReportController. | Un jefe de bodega sin permiso financiero no puede ver informes operativos que no necesitan revelar costes. |
| La zona horaria es seleccionable incluso como Nueva York. | Opciones de ReportsCenter. | Para declaraciones/fechas fiscales Ecuador hace falta un calendario y zona aplicable explícitos; una zona arbitraria no debe cambiar totales legales del periodo. |
| No hay reportes de suscripciones, estado fiscal, retenciones, notas de crédito ni calendario de obligaciones. | No existen rutas de estas familias en la tabla /reports. | No hay vista tributaria ni correlación entre contrato, cuota, factura interna y documento fiscal externo. |
| No hay libro de asientos, plan de cuentas o conciliación bancaria en los reportes revisados. | El dominio documentado se centra en inventario, ventas, compras y reportes. | No es correcto generar balance general, flujo de caja ni impuesto a la renta completo a partir de reportes operativos. |

### 4.3 Lo que los nombres actuales deben significar

- “Ventas” = ventas comerciales internas bajo filtros y reglas explícitos.
- “Ventas netas internas” = ventas menos cancelaciones/devoluciones relacionadas según la fórmula documentada.
- “Ventas fiscales autorizadas netas” = comprobantes autorizados menos documentos correctivos/anulaciones según regla fiscal vigente, agrupados por moneda y periodo aplicable.
- “Compras recibidas” = mercancía cuya recepción fue contabilizada en Inventary.
- “Compras fiscales” = documentos de proveedor recibidos y validados, con su clasificación y estado fiscal; no es el mismo conjunto.
- “Pagado/cobrado” = transacción financiera conciliada o confirmada; no significa factura creada ni pedido confirmado.
- “Utilidad bruta” = ingresos netos comparables menos costo de ventas bajo una política de costeo validada. No es utilidad neta contable.
- “Disponible” = stock físico menos reservas en el ámbito/instante definido; no una promesa de stock futura.
- “Valor de inventario” = valoración según política de coste del stock; no precio de venta, valor de pedido ni saldo bancario.

## 5. Nueva arquitectura de la pantalla

### 5.1 Pestañas de primer nivel

1. **Centro de mando** — lectura del dueño: alertas, métricas, tendencia y decisiones.
2. **Ventas y clientes** — ventas internas, margen, canales, clientes, productos, devoluciones, suscripciones y vínculos.
3. **Compras y proveedores** — órdenes, recepciones, devoluciones a proveedor, documentos fiscales de proveedor cuando existan.
4. **Productos e inventario** — rotación, bajo stock, exceso, disponibilidad, conteos, valoración, Kárdex y diferencias.
5. **Suscripciones** — contratos, cuotas, ventas internas de suscripción, documentos fiscales asociados y fallos.
6. **Fiscal / SRI** — conciliación entre operación interna y comprobantes reales; anulación, notas de crédito, retenciones, ATS/IVA y obligaciones aplicables.
7. **Explorar datos** — builder para correlacionar dos, tres o cuatro dominios.
8. **Informes guardados y exportaciones** — consultas guardadas, programación, historial de exportación y trazabilidad.

La pantalla no debe depender de que la persona abra ocho tablas. La primera pestaña prioriza anomalías y decisiones; las pestañas especializadas conservan el detalle y se abren con filtros desde cada alerta.

### 5.2 Centro de mando del dueño: primer viewport

**Fila 1: periodo, ámbito y frescura**
- Periodo con presets Hoy, Semana, Mes, Trimestre y Personalizado.
- Comparación con periodo anterior equivalente.
- Almacén/canal/familia de producto si procede.
- Indicador de última actualización por fuente: ventas, inventario, compras y fiscal.
- Permisos explícitos; los costos no se cargan al navegador si el usuario no tiene permiso.

**Fila 2: 5–7 indicadores seleccionables y con definición**
- Ventas netas comerciales;
- utilidad bruta y margen bruto, cuando los costes sean fiables;
- documentos fiscales autorizados frente a pendientes/rechazados;
- valor del inventario por moneda;
- productos en riesgo de agotarse/stock crítico;
- compras recibidas netas;
- cuotas de suscripción/facturas fallidas, si aplica.

Cada KPI muestra valor, diferencia contra periodo comparable, fecha, moneda/fuente y acceso al desglose. No mostrar “ingreso cobrado”, “impuesto a pagar” o “utilidad neta” si la fuente actual no permite calcularlo.

**Fila 3: atención prioritaria**
- venta interna con factura fiscal pendiente o rechazada;
- documento fiscal autorizado sin vínculo interno, o viceversa;
- venta cuya cifra no concuerda con factura/notas de crédito;
- diferencias entre recepción/orden/documento del proveedor;
- retención pendiente, cuando ese flujo esté implementado;
- stock por debajo de regla, productos sin regla y cobertura estimada cuando haya histórico suficiente;
- conteo o valoración no conciliados;
- suscripción con cuota/facturación fallida;
- declaración/anexo en preparación con datos insuficientes o fecha límite próxima, solo si las obligaciones del perfil están verificadas.

Cada alerta incluye qué ocurrió, importe/cantidad y moneda, por qué importa, evidencia, calidad de datos, responsable, siguiente acción y enlace al documento. Un dato faltante no se transforma en “todo correcto”.

**Fila 4: tendencias**
- ventas netas internas, separadas de ventas fiscales autorizadas;
- margen bruto por categoría/producto/canal;
- tendencia de entradas/salidas y cobertura;
- compras recibidas y cambios de stock;
- contratos/cuotas de suscripción y su documento fiscal cuando corresponda.

**Fila 5: preguntas rápidas**
Plantillas “¿Qué producto me deja más margen?”, “¿Qué vendo pero no tengo?”, “¿Qué compré y no he vendido?”, “¿Qué factura no coincide?”, “¿Qué suscripciones facturan mal?” y “¿Qué debo revisar para el IVA de este periodo?”. Cada respuesta debe apoyarse en una plantilla con fuentes y fórmulas visibles.

### 5.3 Modo comercial y modo de bodega

**Responsable comercial:** ventas por día/canal/vendedor si se registra el actor comercial, ticket medio, unidades, descuentos, devoluciones, cancelaciones, rentabilidad, clientes, objetivos configurados y repetición/renovación cuando existan datos fiables.

**Responsable de bodega:** disponibilidad por almacén/unidad, stock bajo, reglas ausentes, exceso, entradas/salidas, traslados en tránsito, recepción frente a orden/devolución, exactitud de conteos, rotación, aging, valoración y diferencias.

**Contador/finanzas:** obligaciones y periodos fiscales aplicables, documentos pendientes/autorizados/rechazados/anulados, facturas de compras/ventas/notas de crédito, retenciones emitidas/recibidas, bases/impuestos por clasificación, ATS y excepciones, discrepancias entre libros operativos, documentos fiscales, pagos y asientos.

## 6. Constructor flexible: correlación de dos, tres o cuatro dominios

### 6.1 UX de Explorar datos

El builder guía al usuario en pasos:

1. Elegir una pregunta de negocio o un dominio de inicio.
2. Seleccionar entre 2 y 4 dominios compatibles.
3. Elegir medidas: ventas netas, unidades vendidas, coste de ventas, margen bruto, cantidad recibida, costo de recepción, cantidad devuelta, saldo disponible, valor de stock, cuotas de suscripción o estado fiscal.
4. Elegir agrupación: fecha/día/mes, producto, categoría, cliente, proveedor, almacén, canal, plan/suscripción, estado fiscal o tipo de operación.
5. Añadir filtros: intervalo, estado, almacén, canal, producto, tercero, tipo de comprobante, moneda, familia, estado fiscal y dimensiones permitidas.
6. Seleccionar visualización: tabla, serie temporal, barras, composición o comparación. No dibujar un gráfico que distorsione unidad/moneda.
7. Validar y consultar: mostrar alcance, límites y estimación de consulta.
8. Guardar/exportar: nombre, descripción, roles que pueden usarlo, programación opcional y expiración del resultado.

El usuario no elige tablas SQL, columnas internas ni escribe joins. Los nombres se presentan en lenguaje de negocio.

### 6.2 Dominios correlacionables

**Producto/catálogo:** SKU, nombre, categoría, tipo, unidad, estado y clasificación tributaria cuando exista.

**Ventas:** cabecera, líneas, fecha, cantidad, precio/descuento, canal, cliente, almacén, coste atribuible, cancelación y devolución.

**Compras/recepciones:** orden, proveedor, líneas solicitadas/recibidas, recepción contabilizada, almacén, devoluciones y coste. Los documentos fiscales de proveedor se añaden como hecho separado cuando exista el modelo.

**Inventario:** saldo por producto/almacén, reservas, movimientos, conteos, transferencias y valoración.

**Suscripciones:** contrato, plan, estado, cuotas, periodos, precio/versiones, cancelación, saleId de la venta interna y vínculo fiscal externo.

**Fiscal/SRI:** documento fiscal externo, tipo, estado real, RUC/establecimiento/punto de emisión, fecha, clave de acceso, autorización, líneas/impuestos, nota de crédito/anulación y origen interno.

**Finanzas futuras:** pagos, cobros, retenciones, cuentas por cobrar/pagar, bancos, asientos y plan de cuentas. Estos datos no pueden inferirse de ventas/recepciones.

### 6.3 Ejemplos de correlación

| Pregunta | Dominios | Medidas y dimensiones |
|---|---|---|
| ¿Qué productos vendo mucho, pero mantengo con stock insuficiente? | Productos + Ventas + Inventario | Unidades vendidas, disponible, días sin stock por SKU/almacén. |
| ¿Qué compré, cuánto recibí y qué porcentaje vendí? | Productos + Compras/recepciones + Ventas | Unidades recibidas, devoluciones y unidades vendidas; comparar solo unidades compatibles. |
| ¿Qué productos dejan margen y qué capital queda inmovilizado? | Productos + Ventas + Inventario | Margen bruto estimado, valor de stock por moneda y cobertura si existe demanda fiable. |
| ¿Qué proveedor abastece productos que se venden rápidamente? | Compras/proveedores + Productos + Ventas | Tiempo de reposición si se mide, unidades recibidas/vendidas y margen; no llamar “mejor proveedor” solo por volumen. |
| ¿Qué suscripciones internas no tienen factura fiscal autorizada? | Suscripciones + Ventas + Fiscal/SRI | Cuota/saleId frente a fiscalDocumentId/status; faltantes, duplicados y pendientes. |
| ¿Qué venta no coincide con la factura autorizada? | Ventas + líneas de producto + Fiscal/SRI | Total, líneas, impuestos, estado y diferencia por moneda. |
| ¿Qué compras recibidas no tienen factura de proveedor registrada? | Productos + Compras/recepciones + documentos fiscales proveedor | Recepción, importe, documento proveedor y estado de conciliación. Requiere nueva fuente fiscal de compras. |
| ¿Qué tipos de producto dominan ventas por canal? | Productos + Ventas + Suscripciones | Importe por tipo/canal; no sumar unidades de dimensiones diferentes. |
| ¿Cómo se mueve el capital de inventario frente a ventas? | Inventario + Compras + Ventas | Valor de stock histórico, compras recibidas, coste de ventas, moneda y fecha de corte. |
| ¿Qué devoluciones requieren revisar nota de crédito? | Ventas + Fiscal/SRI + Productos | Devolución interna, factura original, nota de crédito relacionada, estados y diferencias. |

### 6.4 Reglas para evitar correlaciones falsas

- Cada hecho declara granularidad: cabecera, línea, movimiento, cuota, documento fiscal, pago o asiento.
- No unir cabecera de venta, líneas, cuotas y movimientos sin una relación que evite multiplicar importes. Una venta con cuatro líneas no debe contar cuatro veces el total de cabecera.
- Las medidas monetarias mantienen moneda; las cantidades mantienen unidad/dimensión. Sin conversión homologada, no se suman.
- Una suscripción facturada por una venta interna no se cuenta dos veces como “venta” y “suscripción”: se distingue el hecho económico de contrato/cuota.
- Un documento fiscal y una venta interna relacionados son dos representaciones del mismo hecho, no dos ingresos a sumar.
- Devoluciones, anulaciones y notas de crédito tienen referencias y fechas distintas; la fórmula declara cuál fecha se utiliza para el reporte comercial y cuál para el fiscal.
- Las medidas de margen requieren coste de ventas fiable. Los valores desconocidos no se reemplazan por cero.
- La consulta de cuatro dominios solo se habilita si existen relaciones y permisos explícitos. Si no hay camino de correlación, el builder explica por qué.

### 6.5 Contrato semántico y consultas seguras

No aceptar SQL libre ni joins arbitrarios desde React o agentes. El backend expone un catálogo de datasets, dimensiones, medidas y relaciones permitidas, cada una con identificador estable, versión, descripción, granularidad, claves, permisos, moneda/unidad, fórmula, fecha/corte, filtros, política de nulos, fuente y frescura.

El builder envía un objeto declarativo con IDs de datasets, medidas, dimensiones y filtros. El backend valida compatibilidad, permisos, fecha, coste estimado y cardinalidad; luego ejecuta consultas parametrizadas. El API rechaza campos desconocidos, medidas financieras no autorizadas, joins no soportados y límites excesivos.

## 7. Backend: APIs y arquitectura propuestas

### 7.1 Primera fase: hacer fiables los reportes actuales

- Corregir/suspender agregados que mezclen monedas.
- Hacer consistente el filtro “Todos los almacenes” del balance.
- Evitar que el frontend descargue todas las páginas de balance/valoración.
- Añadir exportación completa en backend y estado del job.
- Reemplazar el límite fijo de detalle de 500 por paginación estable o exportación asíncrona, mostrando el límite si temporalmente se mantiene.
- Revisar sumas de ventas por moneda y planes SQL.
- Paginar/filtrar stock bajo, incorporando cobertura de reglas ausentes.
- Crear endpoints agregados que calculen en backend, no en el navegador.
- Separar permisos operativos de reports:financial; costo y margen siguen protegidos, pero conteos de bodega o listados comerciales sin coste pueden tener permisos de lectura específicos.

### 7.2 API del centro de mando

Proponer GET /api/v1/reports/command-center con periodo, comparación, almacén/canal y filtros permitidos.

Respuesta:
- generatedAt y sourceFreshness por dataset;
- periodo, zona/calendario aplicado;
- métricas agregadas y moneda;
- alertas priorizadas con tipo, severidad, estado, causa y referencias;
- tendencias acotadas;
- indicadores de integridad fiscal y operativa;
- permisos/medidas omitidas sin revelar campos restringidos;
- links y filtros para profundizar.

No devolver todas las líneas de venta/recepción para calcular tarjetas en React.

### 7.3 API de informe declarativo

Endpoints propuestos, todavía no implementados:

- GET /api/v1/reports/catalog — datasets, dimensiones, medidas, relaciones, permisos y versión.
- POST /api/v1/reports/preview — valida definición, devuelve compatibilidad, advertencias y muestra acotada.
- POST /api/v1/reports/query — ejecuta consulta permitida y devuelve filas, metadatos, unidades, monedas y trazabilidad.
- POST /api/v1/reports/exports — inicia exportación asíncrona y devuelve job ID.
- GET /api/v1/reports/exports/{jobId} — estado, archivo, expiración y errores.
- GET/POST/PUT/DELETE /api/v1/reports/saved — guardar, listar, versionar y eliminar definiciones.
- GET /api/v1/reports/obligations — obligaciones derivadas del perfil fiscal verificado y reglas vigentes.
- GET /api/v1/reports/fiscal-reconciliation — diferencias entre ventas/recepciones internas y documentos fiscales externos.
- POST /api/v1/reports/tax-workpapers — crear borrador reproducible con filtros, regla/versión, fuente y aprobación.
- GET /api/v1/reports/tax-workpapers/{id} — validaciones, errores y evidencia.
- POST /api/v1/reports/tax-workpapers/{id}/approve — aprobación por persona con permiso, con segregación cuando aplique.
- GET /api/v1/reports/tax-workpapers/{id}/artifacts — paquete/archivos de trabajo; no debe decir “presentado” sin evidencia.

El API real del backend fiscal debe integrarse mediante [el contrato de facturación electrónica SRI](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

### 7.4 Proyecciones y rendimiento

Evolución gradual:
1. Consultas parametrizadas en PostgreSQL con índices y EXPLAIN ANALYZE.
2. Vistas/proyecciones de lectura para el centro de mando y medidas de alto uso.
3. Snapshots de cierre para informes tributarios/financieros reproducibles.
4. Un read model/materialized view o almacén analítico solo cuando el volumen y las métricas lo justifiquen.

No iniciar event sourcing completo ni un data warehouse solo por crear una pantalla avanzada. Cada informe debe identificar snapshotAt o instante de consistencia. Para una declaración, usar un snapshot congelado y versionado, no filas que cambian mientras el usuario revisa.

### 7.5 Paginación y exportación

- Dashboard con agregados acotados.
- Tablas con page/size/sort en backend, tamaño máximo, sort allowlist y orden estable.
- Exports completos en backend, nunca con descarga de todas las páginas desde navegador.
- Job con creador, definición, filtros, periodo, filas, formato, estado, duración, error y expiresAt.
- CSV protegido contra formula injection al abrirlo en Excel/LibreOffice.
- Export que no incluya campos ajenos al permiso del usuario.
- Kárdex con paginación por cursor/snapshot y saldo corrido exacto, o export job; nunca truncamiento silencioso.
- El total real y cualquier límite se muestran claramente.

## 8. Modelo fiscal/contable que falta para preparar declaraciones reales

### 8.1 Documentos fiscales emitidos y notas correctivas

La propuesta fiscal separada debe persistir:
- documento fiscal externo, tipo, número/serie/secuencial devuelto;
- clave de acceso/ID fiscal, estado autorizado/rechazado/anulado/en proceso;
- RUC emisor/receptor y establecimiento/punto;
- bases, impuestos por código/tarifa, descuentos y total;
- referencias a factura original, anulación y nota de crédito;
- evidencia de autorización/fechas, errores y correlación;
- idempotency key, sourceOperationId y versión del contrato.

La misma relación debe existir para ventas de suscripción, incluyendo cuota/periodo y venta interna vinculada. El estado fiscal no se deduce de Sale.status ni del estado de devolución.

### 8.2 Documentos de proveedores y retenciones

Compras hoy distingue orden, recepción y devolución física. Para preparar impuestos hace falta otro conjunto de hechos:
- factura/documento recibido del proveedor con RUC, fecha de emisión/recepción, número/clave/ID y estado;
- líneas con base imponible, código/tarifa, valor de impuesto, descuento y clasificación;
- notas de crédito/débito del proveedor y referencias al original;
- comprobante de retención emitido por el negocio sobre una compra y su estado;
- comprobante de retención recibido del cliente cuando corresponda al negocio, base, porcentaje, valor y conciliación;
- vínculo a recepción/orden y pagos, sin asumir equivalencia;
- fecha contable, fiscal, de emisión, autorización y pago separadas.

No generar el ATS de compras usando solo la recepción física; debe entrar el documento fiscal pertinente y aplicar reglas del ATS de ese periodo.

### 8.3 Libro contable

Para obtener estados financieros y preparar impuesto a la renta de forma seria se requiere, según marco/alcance:
- plan de cuentas;
- asientos con origen, fecha de devengo, cuentas y debe/haber;
- asiento balanceado;
- submayores de clientes, proveedores, impuestos, inventario, caja/bancos, activos y pasivos;
- periodo contable abierto/cerrado, permisos de ajuste y asiento de cierre;
- conciliación de saldos de submayor contra mayor;
- trazabilidad de asiento a factura/documento/transacción;
- políticas contables/tributarias versionadas;
- ajustes manuales aprobados con motivo y evidencia.

Las operaciones actuales generan ventas, recepciones, movimientos de inventario, costo y auditoría; no equivalen a un libro mayor completo. Los informes de utilidad bruta seguirán siendo operativos hasta registrar gastos, nómina, depreciación, provisiones, financiación e impuestos.

### 8.4 Pagos, bancos, cuentas por cobrar/pagar y nómina

No calcular impuestos o flujo de caja solo con facturas. La extensión contable debe incluir o integrar:
- pagos recibidos, pagos a proveedores, anticipos, saldos y métodos;
- estados de conciliación con proveedor de pagos o extractos bancarios;
- notas de crédito, devoluciones y reembolsos reales;
- cuentas por cobrar/pagar y vencimientos;
- nómina/RDEP si existen obligaciones y el negocio desea soportarlas;
- gastos recurrentes y gastos no ligados a compras de inventario;
- activos fijos y depreciaciones si corresponden.

Si otro sistema gestiona alguno de esos componentes, importar mediante contrato con ID, fecha, estado, procedencia y deduplicación. No reconstruirlo en Inventary por obligación, pero tampoco presentar un paquete fiscal como completo con datos faltantes.

## 9. Preparación tributaria: flujo de cierre

1. **Perfil fiscal vigente:** RUC verificado, régimen, tipo de contribuyente, obligaciones activas, condiciones y fecha de verificación.
2. **Seleccionar ejercicio/periodo:** calendario, obligación y versión de regla/esquema.
3. **Cerrar fuentes:** ventas internas, documentos fiscales, compras/documentos de proveedor, notas de crédito/anulación, retenciones, pagos/ajustes disponibles.
4. **Conciliar:** operación interna contra documento fiscal, totales por base/impuesto/retención, faltantes y duplicados.
5. **Resolver excepciones:** documento no autorizado, receptor/emisor/establecimiento inválido, nota de crédito sin original, documento de proveedor ausente, clasificación sin mapear o moneda/unidad inconsistente.
6. **Generar borrador:** valores por casilla/categoría aplicable, fórmula, fuentes y supuestos.
7. **Validar esquema/formulario:** usar artefactos oficiales versionados; no diseñar el XML ATS/formulario por aproximación.
8. **Revisión profesional:** comentarios, ajustes documentados y aprobación con segregación de funciones.
9. **Exportar/presentar:** si no existe API autorizada de presentación, generar artefactos y abrir el portal oficial. No automatizar credenciales personales ni marcar presentado al generar un archivo.
10. **Registrar evidencia:** comprobante oficial, número de declaración, fecha/estado y pago cuando se aporte o una integración autorizada lo devuelva.
11. **Congelar el paquete de cierre:** filtros, snapshot, versiones, hashes, evidencia, aprobadores y resultado.
12. **Reabrir correcciones/sustitutivas:** crear nueva versión enlazada al paquete anterior, con motivo y diferencia; no sobrescribir el original.

El motor debe responder “¿qué obligación corresponde a este contribuyente en este periodo?” con fuente y fecha. No presumir que IVA, ATS, retenciones, RDEP o todos los formularios aplican a cada RUC.

## 10. Conciliación fiscal y operativa que Reportes debe priorizar

### 10.1 Conciliación de venta

Por cada venta interna:
- ¿existe solicitud de emisión?
- ¿el backend fiscal la recibió?
- ¿hay documento fiscal?
- ¿está autorizado o rechazado?
- ¿coinciden receptor, emisor, establecimiento/punto, líneas, bases, impuesto, moneda y total?
- ¿se anuló o tuvo nota de crédito?
- ¿el estado del pedido/suscripción coincide con el hecho comercial?
- ¿hay pago/reembolso confirmado o solo documento emitido?

Estados sugeridos de conciliación: MATCHED, INTERNAL_ONLY, FISCAL_ONLY, AMOUNT_MISMATCH, TAX_MISMATCH, RECEIVER_MISMATCH, ISSUER_CONTEXT_MISMATCH, DUPLICATE, PENDING, CANCELLED_MISMATCH, CREDIT_NOTE_MISMATCH, NEEDS_REVIEW. Son estados propuestos, no parte de la API actual.

### 10.2 Conciliación de compras

Por cada recepción:
- ¿existe orden?
- ¿existe documento de compra del proveedor?
- ¿coinciden líneas/valores/moneda/IVA?
- ¿hay nota de crédito del proveedor?
- ¿se han registrado retenciones que correspondan?
- ¿coincide devolución física con documento comercial/fiscal?
- ¿se registró pago/anticipo y contra qué documento?
- ¿se valoró stock conforme a política?

### 10.3 Conciliación de inventario/contabilidad

- existencia física y saldo del sistema;
- movimientos y origen documental;
- cantidad valorizada y onHand;
- libro de costo y costo de ventas;
- devolución a stock vendible versus merma/SCRAP;
- saldo inicial, movimientos del periodo y cierre;
- valor de stock versus cuenta contable de inventario cuando exista libro mayor.

No autoajustar saldo/costo para que el reporte cuadre. La diferencia abre un caso de revisión y la corrección se hace como movimiento/ajuste autorizado.

## 11. Definiciones de métricas para evitar decisiones equivocadas

| Métrica | Definición propuesta | Datos mínimos |
|---|---|---|
| Ventas comerciales brutas | Totales internos según fecha de negocio elegida, antes de cancelar/devolver; agrupados por moneda. | Sale/líneas con moneda y fecha. |
| Ventas comerciales netas | Ventas ajustadas por cancelaciones/devoluciones relacionadas según fórmula documentada. | Sale, cancellation, sales-return; no inferir fiscalidad. |
| Ventas fiscales autorizadas netas | Comprobantes autorizados menos documentos correctivos/anulaciones según regla vigente, por moneda/periodo. | Backend fiscal y relación con venta/nota de crédito. |
| Ingreso cobrado | Efectivo/transferencia/pago conciliado atribuible a una venta según política. | Entidades de pago, transacción de proveedor o banco. |
| Utilidad bruta | Ventas netas comparables menos costo de ventas bajo método y periodo coherentes. | Venta, coste valorado, devoluciones y moneda. |
| Margen bruto % | Utilidad bruta / ventas netas × 100 si ventas netas es positiva y comparable. | Mismos datos y moneda. |
| Compras recibidas netas | Valor de recepciones contabilizadas menos devoluciones a proveedor del periodo. | Goods receipts y purchase returns. |
| Compras fiscales netas | Documentos de proveedor/notas correctivas con clasificación fiscal; no solo recepción. | Documento, líneas, impuestos y estado. |
| Capital inmovilizado | Valor de inventario al costo, con segmento de exceso solo si hay política. | Valoración reconciliada y moneda. |
| Cobertura de stock | Disponible / demanda media diaria bajo ventana válida; no calculable si demanda no es fiable. | Historial de demanda, reservas, unidad base. |
| Rotación | Costo de ventas de ventana / inventario promedio valorizado de ventana. | Costo y valor en moneda consistente. |
| Diferencia fiscal pendiente | Campo/importe discrepante entre operación interna y documento asociado. | Datos internos y backend fiscal. |
| Tasa de documentos sin conciliar | Documentos internos/fiscales sin correspondencia dividido por universo elegible explícito. | Cruce por IDs/hash/clave. |

No mostrar “beneficio neto”, “flujo de caja”, “impuesto a pagar” ni “balance general” hasta tener datos/reglas necesarios. No comparar importes de monedas ni cantidades de unidades diferentes.

## 12. Permisos y agentes

| Perfil | Puede consultar | No debe recibir por defecto |
|---|---|---|
| Dueño/gerencia | Tendencias, alertas, margen y finanzas según política de negocio. | Datos personales innecesarios o XML/IDs sensibles sin permiso. |
| Comercial | Ventas, clientes dentro de alcance, productos y objetivos. | Costo/margen si no tiene autorización financiera. |
| Bodega | Stock, movimientos, recepciones, traslados y conteos. | Costo total, margen o datos fiscales innecesarios. |
| Contabilidad/finanzas | Costes, documentos tributarios, conciliaciones, workpapers y exportación. | Secretos del backend fiscal/certificados. |
| Auditor | Evidencia histórica, cambios, actor y correlación. | Modificar registros o aprobar su propia corrección sin permiso. |
| Agente IA futuro | Consultar datos acotados, explicar métricas y crear borradores de reportes/workpapers. | SQL arbitrario, secretos, autorización para presentar declaraciones o alterar cifras. |

Para agentes, ofrecer herramientas de solo lectura como getReportCatalog, queryReport, explainReportMetric, getFiscalReconciliation y getTaxWorkpaper. Pueden preparar una consulta o señalar anomalías, pero no cambian estados fiscales, presentan declaraciones ni inventan reglas. Un envío/presentación requiere backend autorizado, identidad acotada, aprobación y evidencia verificable.

Cada medida incluye fuente, definición, periodo, versión, generatedAt, freshness y permisos. La autorización se aplica antes de enviar datos a React o al modelo de IA.

## 13. Roadmap por fases

### Fase 0 — Fiabilidad y límites
1. Corregir métricas monetarias para no mezclar monedas.
2. Resolver la incoherencia del filtro de almacén obligatorio/opcional.
3. Mover agregados a backend y eliminar listAllPages para resumen/CSV.
4. Añadir paginación o exportes asíncronos para ventas/compras/Kárdex.
5. Mostrar calidad/cobertura histórica del coste.
6. Incorporar permisos operativos/financieros diferenciados.
7. Definir ventas internas vs ventas fiscales autorizadas.

### Fase 1 — Centro de mando gerencial
1. Command center con KPIs definidos, alertas por fuente y tendencias.
2. Filtros globales persistentes y comparación de periodos.
3. Drill-down de cada KPI a registros/documentos.
4. Skeleton por sección, datos antiguos, error, falta de permiso, vacío y reintento.
5. CSV/PDF/exportación básica con timestamp y filtros auditables.

### Fase 2 — Constructor de informes
1. Catálogo de datasets/dimensiones/medidas.
2. Builder de 2–4 dominios con validación de relaciones/granularidad.
3. Plantillas ventas+inventario, compras+ventas, producto+ventas+compras e indicadores de suscripción.
4. Informes guardados, permisos y versiones.
5. Jobs de exportación/programación opcional.
6. Medición de coste de consulta, latencia y cardinalidad.

### Fase 3 — Integración fiscal SRI
1. Implementar vínculo del backend fiscal externo con ventas y devoluciones.
2. Guardar número real, clave de acceso, estado, bases/impuestos y documentos correctivos.
3. Conciliar ventas internas con facturas autorizadas, notas de crédito y anulaciones.
4. Añadir estados de retenciones/documentos fiscales de proveedor según contratos.
5. Catálogo fiscal versionado, historial y referencias oficiales.

### Fase 4 — Preparación de declaraciones
1. Configurar obligaciones de cada contribuyente conforme a perfil verificado.
2. Crear workpapers con snapshots/reconciliación.
3. Preparar ATS según catálogo/versión aplicable al periodo.
4. Preparar IVA, retenciones y otros formularios solo cuando exista toda la fuente necesaria.
5. Validar archivos contra esquema oficial vigente.
6. Revisión/aprobación con segregación de funciones.
7. Registrar presentación/pago solo tras evidencia real o integración autorizada.

### Fase 5 — Base contable
1. Plan de cuentas y asientos balanceados.
2. Enlace automático a ventas, inventario, compras y documentos fiscales.
3. Cuentas por cobrar/pagar, pagos, bancos y retenciones.
4. Ajustes de cierre y nómina/activos cuando el alcance lo requiera.
5. Estados financieros a partir de libro contable reconciliado, no de KPI operativos.

### Fase 6 — Agentes y alertas proactivas
1. Agente de análisis solo lectura con explicación y evidencia.
2. Agente para preparar consultas y paquetes de cierre en borrador.
3. Aprobación humana y snapshots inmutables.
4. Alertas con tolerancias y responsables.
5. Evaluaciones de precisión, cobertura, falsos positivos y privacidad antes de incrementar autonomía.

## 14. Pruebas y criterios de aceptación

### Frontend
- La pantalla inicial es útil sin consultar seis informes manuales.
- Filtros globales no se pierden al cambiar de pestaña y se reflejan en URL/estado guardado.
- Cada KPI explica fórmula, moneda, periodo, fuente y frescura.
- Paginación/exportes no dependen de descargas de todas las páginas desde navegador.
- Se distingue venta comercial de venta fiscal autorizada.
- Los errores, permisos y datos incompletos no se representan como cero.
- Consultas de dos, tres y cuatro dominios respetan granularidad y permiten abrir la fuente.
- El builder no admite joins sin relación ni medidas de otro permiso.
- CSV no expone datos sensibles fuera del permiso y está protegido ante fórmula maliciosa.
- Fechas locales y accesibilidad son correctas en escritorio/móvil.

### Backend
- Monedas mixtas nunca se suman ni etiquetan como USD por defecto.
- Medidas monetarias incluyen currency y política para moneda base si se implementa conversión.
- Consultas parametrizadas, allowlist de dimensiones/medidas, tamaño y cardinalidad.
- Paginación estable y query plan medido con dataset realista.
- Kárdex/exportes grandes reproducibles con snapshot y trazabilidad.
- Agregaciones no multiplican importes por joins de líneas/movimientos/cuotas.
- Métricas de inventario preservan unidades de medida.
- Datos fiscales importados con idempotencia, estado y timestamps reales.
- No se etiqueta solicitud de factura como autorizada ni nota de crédito como reembolso.
- Workpapers guardan versiones y no cambian silenciosamente después de preparados.
- RUC/obligaciones se configuran por perfil; no se asume que cada contribuyente presenta cada formulario.
- Los usuarios no reciben campos/costes que no pueden consultar.
- Un error del backend fiscal deja conciliación pendiente, no correcta.

### Contabilidad/tributario
- IVA/retenciones/ATS se contrastan con fuentes oficiales vigentes para cada periodo.
- Documentos fiscales deduplicados por identificadores reales y enlazados a operación.
- Totales por formulario/categoría trazables a cada documento/línea y clasificación.
- Diferencias no se corrigen automáticamente; se presentan para revisión con evidencia.
- La declaración preparada no se marca presentada sin confirmación.
- Reglas nuevas del SRI se aplican al periodo correspondiente y preservan versiones históricas.
- Reportes de rentabilidad no se presentan como estados financieros completos cuando faltan gastos/asientos.

## 15. Indicadores de calidad del propio sistema

- Porcentaje de ventas con documento fiscal relacionado y estado definitivo.
- Ventas internas con factura pendiente/rechazada y antigüedad.
- Documentos fiscales sin operación interna y viceversa.
- Diferencias de bases/impuestos/totales por moneda y periodo.
- Líneas sin clasificación fiscal.
- Compras/recepciones sin documento de proveedor cuando aplique.
- Retenciones con referencia/estado incompleto.
- Cobertura del catálogo ATS para el periodo.
- Stock valorizado/reconciliado.
- Exportaciones fallidas, tiempo de consulta y filas procesadas.
- Informes guardados usados y errores de definición.
- Workpapers aprobados, rechazados y reabiertos.
- Tiempos para resolver diferencias por causa/área.

Las metas se definen después de observar una línea base; no afirmar precisión ni tiempo real hasta medirlos.

## 16. Fuentes internas y propuestas revisadas

Frontend:
- app-inventary-ecuapp/src/features/reports/ReportsCenter.jsx
- app-inventary-ecuapp/src/api.js
- app-inventary-ecuapp/src/App.jsx
- app-inventary-ecuapp/src/utils/csv.js

Backend:
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/InventoryBalanceReportController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/OperationalReportService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/InventoryBalanceReportService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/reports/ReportDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SalesController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SaleService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/returns/SalesReturnService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/subscriptions/SubscriptionService.java
- microservices-inventary-ecuapp/docs/25-referencia-apis.md

Propuestas relacionadas:
- PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md
- PROPUESTA_UX_VENTAS_V2.md
- PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md
- PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md
- PROPUESTA_INVENTARIO_AGENT_READY_V3.md
- PROPUESTA_UX_SUSCRIPCIONES.md
- PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md
- PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md
- PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md

## 17. Límite de esta entrega

Esta entrega crea únicamente la propuesta en Markdown. No cambia todavía componentes React, contratos REST, DTOs, SQL, migraciones, reglas tributarias, integración del backend fiscal, generación ATS ni flujos de declaración. El API externo de facturación tampoco se ha probado porque su URL, OpenAPI, autenticación y entorno de prueba no forman parte del repositorio inspeccionado.

La propuesta define una evolución por fases: primero fiabilidad y rendimiento de los reportes actuales; después centro de mando y análisis multidominio; luego integración fiscal y preparación de declaraciones; finalmente libro contable, cierre y agentes con control humano.

## 18. Revisión V2: arquitectura multidominio real, establecimientos SRI y bodegas

Esta sección es normativa para el diseño de la plataforma. Si contradice una interpretación simplificada de las secciones anteriores, prevalece este modelo más preciso.

### 18.1 Principio de modelado: relación muchos-a-muchos sin duplicar inventario

La relación entre un establecimiento fiscal y una bodega operativa es de **muchos a muchos**:

- Un establecimiento SRI puede relacionarse con varias bodegas: por ejemplo, local de ventas con bodega trasera, almacén de apoyo, centro de distribución y bodega de devoluciones.
- Una bodega puede relacionarse con varios establecimientos: por ejemplo, un almacén compartido que abastece a dos locales, una bodega central que sirve a varias sucursales, una bodega de cross-docking o una instalación física utilizada por negocios/RUC diferentes.
- Una bodega puede no relacionarse con ningún establecimiento fiscal: por ejemplo, centro logístico interno, ubicación temporal, cuarentena, mercancía en tránsito o almacén que no corresponde a un emisor.
- Un establecimiento puede existir en los registros del SRI aunque aún no tenga bodega interna relacionada.
- La asociación puede cambiar con el tiempo y debe mantener su historia.

**La relación entre una bodega y un establecimiento NO determina por sí sola**: propiedad legal del inventario, RUC emisor de una venta, punto de emisión, origen fiscal de una compra, obligación tributaria, ni asignación contable. Esas decisiones requieren atributos explícitos y reglas del dominio. Tampoco se debe copiar la cantidad almacenada de una bodega por cada establecimiento vinculado: eso duplicaría existencias y valor de inventario en los informes.

Modelo recomendado:

- TaxpayerProfile: un contribuyente/RUC y sus atributos fiscales versionados.
- SriEstablishment: establecimiento oficial vinculado a exactamente un perfil RUC de origen; conserva el número oficial como texto, estado recibido, origen y timestamps.
- Warehouse: bodega física/lógica interna con código propio, dirección operativa, ubicaciones y estado.
- WarehouseEstablishmentAssignment: puente de asociación N:M entre Warehouse y SriEstablishment, con tipo de relación, vigencia, prioridad y auditoría.
- InventoryOwner o LegalEntity: propietario de la mercancía si una organización opera varios RUC/entidades o mantiene mercancía de terceros.
- StockBalance, StockMovement, Reservation y ValuationEntry: hechos físicos/valorados que se vinculan al almacén y propietario; no se multiplican por el número de asociaciones fiscales.
- BillingContext: contexto que el backend fiscal valida para emitir: contribuyente/RUC, establecimiento, punto de emisión, numeración/configuración externa y vigencia. Inventary conserva el identificador y el snapshot realmente usado.
- Sale/Receipt/Return: referencia a la bodega del movimiento físico y, cuando corresponda, al contexto/resultado fiscal, como dimensiones separadas.

### 18.2 Esquema de la relación WarehouseEstablishmentAssignment

La tabla puente recomendada incluye como mínimo:

| Campo | Propósito |
|---|---|
| id | Identidad propia de la asociación. |
| warehouse_id | Bodega interna relacionada. |
| sri_establishment_id | Establecimiento oficial relacionado. |
| relationship_type | Significado explícito de la asociación: STOCK_SERVES, SALES_OPERATIONS, PICKUP, FULFILLMENT, RECEIVING, RETURN_ACCEPTANCE, REPORTING_SCOPE u otro valor controlado. |
| effective_from / effective_to | Vigencia temporal; el rango se valida para no crear asociaciones contradictorias. |
| active | Estado operativo de la relación, separado del estado oficial SRI y de la bodega. |
| priority | Prioridad de sugerencia dentro de un flujo/canal definido; no es autorización de emisión. |
| default_for_channel | Opcional: sugiere una bodega o contexto por canal cuando haya una regla explícita. |
| configured_by / configured_at | Auditoría de la creación/cambio. |
| reason / change_ticket | Motivo de la modificación y referencia de aprobación cuando aplique. |
| source / source_snapshot | Distingue información operativa local de datos recibidos de una fuente externa. |

No crear una columna única establishment_id en Warehouse ni una columna única warehouse_id en SriEstablishment para representar esta relación: ambas limitarían escenarios reales y forzarían duplicados.

Restricciones:
- La bodega y el establecimiento deben pertenecer al ámbito de organización/tenant autorizado.
- El establecimiento debe existir en el perfil RUC registrado, incluso si está cerrado; el estado no elimina la historia.
- No debe haber asociaciones duplicadas con el mismo tipo y periodos superpuestos sin justificación permitida.
- Si se configura un predeterminado, debe ser único solo dentro de la combinación concreta de flujo/canal/periodo en la que la regla lo necesita. No debe imponerse un único establecimiento global para toda la bodega.
- La vigencia de una asociación no puede reescribir ventas, compras, movimientos o facturas anteriores.
- Desactivar una asociación no desactiva ni elimina la bodega, el establecimiento oficial ni el stock.
- Un cambio de relación conserva versión anterior, autor, fecha, razón y operaciones afectadas.

### 18.3 Propiedad de stock y riesgo de empresas/RUC compartiendo bodega

Si las asociaciones pueden cruzar RUC diferentes, separar la ubicación física de quién es dueño de la mercancía. Una bodega física puede contener stock de dos sociedades o de un negocio propio y otro en consignación. La relación con una sucursal no demuestra propiedad.

Si el alcance de Inventary incluye varias entidades legales/tenants:
- incluir inventoryOwnerId/legalEntityId como dimensión de saldo, movimiento, reserva, valoración, compra y venta, o un modelo de inventario multi-propietario equivalente;
- restringir consultas por entidad legal antes de calcular agregados;
- no permitir que una factura del RUC A deduzca que toda la mercancía de una bodega compartida pertenece al RUC A;
- una transferencia entre propietarios no es un traslado normal de ubicación: requiere operación explícita entre entidades, soporte comercial/contable y autorización;
- si el producto todavía no implementa inventario multi-propietario, debe bloquear o advertir la configuración multi-RUC que pudiera mezclar saldos, en vez de aparentar aislamiento.

La asociación N:M puede resolver qué establecimientos se abastecen o usan una bodega; no puede reemplazar un ledger de propiedad. Para los reportes por establecimiento, el inventario físico debe contarse una sola vez por combinación real de propietario + producto + bodega + instante. Una métrica denominada “stock atribuible al establecimiento” solo se calcula cuando exista una asignación explícita de propiedad/segmentación o una regla de asignación documentada; nunca se infiere distribuyendo la existencia completa a todos los establecimientos relacionados.

### 18.4 Cómo elegir contexto fiscal sin confundirlo con la bodega

En ventas se mantienen cuatro preguntas independientes:

1. ¿Dónde se reserva/descarga la mercancía? warehouseId o selección por línea de cumplimiento.
2. ¿Qué entidad legal es parte de la venta? sellerLegalEntity/taxpayerProfile según el modelo multi-RUC.
3. ¿Desde qué establecimiento/punto se emitirá el comprobante? billingContextId, validado por el backend de facturación.
4. ¿Desde dónde se entrega/retira al cliente? fulfillment/pickup location.

La bodega puede sugerir un contexto fiscal por una regla explícita, pero el backend fiscal debe confirmar que el RUC, establecimiento, punto de emisión, numeración y configuración sean elegibles en el momento real de emisión. Si la bodega está asignada a varios establecimientos, no escoger el primero del array, el más reciente o un orden alfabético. Si hay más de una opción legal, el flujo debe resolverla por una regla configurada y visible; si persiste ambigüedad, detener la emisión y pedir selección autorizada.

Una venta que consume stock de varias bodegas no implica automáticamente una factura por bodega ni varias facturas por establecimiento. El contrato comercial/fiscal debe determinar si el pedido permite split fulfillment y cómo representa su comprobante fiscal. Por defecto, una venta comercial conserva una identidad y su contexto fiscal explícito; el backend fiscal define el documento legal apropiado, mientras Inventary detalla uno o más movimientos de stock según el cumplimiento.

### 18.5 Matriz de escenarios que debe soportar el producto

| Escenario real | Comportamiento correcto en Inventary/Reportes |
|---|---|
| Un establecimiento tiene varias bodegas | La ficha del establecimiento muestra todas las bodegas relacionadas por tipo/vigencia. El stock se mantiene separado por bodega. |
| Una bodega sirve a varios establecimientos | La ficha de la bodega lista todos los establecimientos y el motivo de cada relación. No se duplica stock al consultar por todos los establecimientos. |
| Un almacén central abastece tres locales | Los reportes distinguen existencias físicas del centro, órdenes de reabastecimiento y ventas de cada establecimiento. No atribuyen al local una existencia que sigue físicamente en el centro salvo métrica de abastecimiento explícita. |
| Una bodega compartida por dos RUC | Debe existir propietario de inventario/aislamiento legal. Sin esa dimensión, bloquear configuración o declarar explícitamente la limitación. |
| Un establecimiento está cerrado pero hay stock en una bodega relacionada | Conservar existencias, movimientos e historia. Advertir el contexto fiscal; no borrar ni desactivar automáticamente bodega/stock. |
| RUC suspendido con varias bodegas | Bloquear nuevas emisiones que no sean elegibles según la validación fiscal; permitir consultas e informes históricos. No tratar el estado como saldo cero ni borrar stock. |
| Bodega sin asociación fiscal | Permitir administración de stock; no proponerla como prueba de elegibilidad de un establecimiento emisor. |
| Establecimiento sin bodegas relacionadas | Permitir que exista en el registro SRI; avisar que no hay correspondencia operativa, sin inventar una bodega. |
| Mapeo cambia a mitad de mes | El informe histórico usa la vigencia que aplicaba al hecho en esa fecha; no re-clasificar ventas pasadas por la configuración actual. |
| Se cierra una relación por error | Crear nueva versión/rectificación auditable; no borrar la relación usada por documentos históricos. |
| Bodega con funciones de venta, retiro y devolución | Crear asociaciones por tipo de uso. Que sea pickup point no implica que facture, reciba todo tipo de devoluciones o sea dueña de stock. |
| Mercancía en tránsito | Representarla en el estado de traslado/cuenta de tránsito que corresponda, no sumarla como disponible de destino antes de recepción. |
| Ubicación de cuarentena/merma | Separar la posición lógica del stock vendible; el reporte de disponibilidad no debe contarla como disponible para venta. |
| Compra recibida en bodega compartida | Vincular la recepción a bodega y propietario/entidad legal que corresponda; la factura del proveedor sigue siendo un documento fiscal del comprador, no de todos los establecimientos que usan el almacén. |
| Devolución recibida en una bodega distinta de la salida original | Registrar lugar real de retorno y factura original por separado. Validar reglas de transferencia/propiedad y nota de crédito. |
| Venta desde una bodega asociada a varios establecimientos | Resolver billingContextId por regla explícita y comprobar con backend fiscal. Si es ambiguo o inválido, no emitir. |
| Venta preparada en bodega central y recogida en local | Mantener sourceWarehouseId, pickupWarehouseId y billingContextId como referencias separadas. |
| Pedido surtido parcialmente desde dos bodegas | Registrar cantidades por bodega y eventos de cumplimiento. La venta y documento fiscal siguen una política definida; no duplicar línea/total al combinar movimientos. |
| Productos de diferentes unidades de medida en una bodega | Agregar cantidades solo por producto/unidad compatible; mostrar valores monetarios separados por moneda. |
| Bodega desactivada con histórico | Conservarla como filtro histórico y referencia documental; impedir nuevas operaciones según política. |
| El usuario no puede ver una de las entidades vinculadas | Aplicar permisos al filtrar el conjunto y sus agregados; no filtrar la fila después de calcular un total que exponga el monto/stock oculto. |
| Asociación activa duplicada o contradictoria | Rechazar el cambio con mensaje y sugerir la relación existente; no elegir automáticamente una opción arbitraria. |

### 18.6 Dimensiones y claves del modelo analítico

El catálogo de datasets de Reportes debe declarar las claves y cardinalidades de forma explícita:

- Dimensión de entidad legal/RUC: taxpayerId/legalEntityId.
- Dimensión de establecimiento fiscal: sriEstablishmentId y código oficial como texto.
- Dimensión de bodega: warehouseId/código interno.
- Hecho de asociación: assignmentId, warehouseId, sriEstablishmentId, relationshipType, vigencia.
- Hecho de inventario: ownerId + warehouseId + productId + instante/snapshot.
- Hecho de venta y línea: saleId, saleLineId, warehouse/fulfillment refs y billingContextId independiente.
- Hecho de documento fiscal: fiscalDocumentId, documento original, referencia a la operación, establecimiento/punto realmente devueltos por facturador, estado y fecha.
- Hecho de compra/recepción/devolución: orderId, receiptId, returnId, warehouseId, supplierId, owner/legal entity y referencia fiscal de proveedor si existe.
- Hecho de cuota: subscriptionId/installmentId/saleId/fiscalDocumentId.
- Hechos contables futuros: journalEntryId/journalLineId/accountId/periodId y origen documental.

Reglas de unión:
- Unirse de hecho a hecho con claves de origen definidas, no únicamente por producto, fecha o establecimiento.
- Tratar el puente bodega-establecimiento como relación N:M; agregar una tabla de saldos después de unir el puente puede multiplicar los saldos si no hay una política de asignación.
- Para reportar ventas por establecimiento, usar el contexto fiscal efectivamente utilizado por documento/operación, no todas las asociaciones de la bodega.
- Para reportar ventas operativas atendidas por establecimiento, usar la atribución comercial explícita almacenada en la venta/pedido.
- Para reportar stock por bodega, usar balances físicos directamente; si se filtra por establecimiento solo para incluir bodegas vinculadas, deduplicar por la clave real del balance.
- No sumar el total de la factura a cada línea, ni una cabecera a cada movimiento, ni el total de cuota a cada documento fiscal asociado.
- Definir medidas "netas" de forma versionada, explicitando el tratamiento de devolución, cancelación, nota de crédito y fecha de reconocimiento.

### 18.7 Filtros y experiencia de usuario de la pantalla

El área global de filtros debe distinguir:
- periodo comercial;
- periodo fiscal según fecha de documento/estado que se esté analizando;
- entidad legal/RUC;
- establecimiento SRI;
- bodega operativa;
- tipo de asociación entre bodega y establecimiento;
- propietario legal del inventario, cuando aplique;
- producto/categoría;
- canal, cliente, proveedor y suscripción;
- estado comercial;
- estado de documento fiscal;
- moneda y unidad de medida.

Los filtros no pueden actuar como sinónimos:
- seleccionar un establecimiento no equivale a seleccionar su bodega;
- seleccionar una bodega no implica elegir el establecimiento emisor;
- seleccionar una relación de abastecimiento no cambia la propiedad del stock;
- seleccionar una factura fiscal no excluye automáticamente la devolución comercial ni el pago asociado.

Mostrar chips de ámbito activo como "RUC/entidad", "Establecimiento", "Bodega" y "Propietario de inventario". Si una selección no es compatible con otra, explicar el conflicto y permitir limpiarlo. El usuario debe poder guardar la definición del filtro con el reporte y auditar con qué ámbito se produjo.

### 18.8 Calendario y reglas del SRI: mantener evidencia y versión

La propuesta de Reportes debe tomar los artefactos oficiales desde una capa de reglas versionada, no copiar tasas, fechas o reglas en React.

Verificaciones oficiales encontradas el 29 de octubre de 2026:
- El SRI publica ATS V1.19.0 con fecha de actualización 23 de septiembre de 2026 para periodos fiscales desde septiembre de 2026, junto con catálogo ATS actualizado el 6 de agosto de 2026. Cada paquete de preparación ATS debe registrar qué versión de catálogo/esquema usó. Fuente: https://www.sri.gob.ec/formularios-e-instructivos1
- El SRI anunció mediante Resolución NAC-DGERCGC26-00000016 que, desde el 1 de junio de 2026 y para los sujetos obligados dentro del alcance de la regla con las excepciones normativas correspondientes, la declaración de IVA se considera cumplida solo si se registra junto con el pago total del impuesto. Si no se paga el total, la declaración se considera no presentada bajo esa regla. Por eso no basta con mostrar "archivo presentado" o "declaración enviada": el workpaper debe diferenciar borrador, envío, estado oficial de declaración y pago total/compensación aplicable. Fuente: https://www.sri.gob.ec/detalle-noticias?idnoticia=1286&marquesina=1
- La Resolución NAC-DGERCGC26-00000009 establece nuevos porcentajes de retención en la fuente del Impuesto a la Renta aplicables desde el 1 de marzo de 2026; el SRI también publicó nuevos agentes de retención por resolución NAC-DGERCGC26-00000010. No usar tablas de retención sin fecha de vigencia ni el estado verificado del agente. Fuente: https://www.sri.gob.ec/web/intersri/normativa-para-agentes-de-retencion-y-contribuyentes-especiales
- El calendario tributario oficial define vencimientos según obligación, contribuyente y regla aplicable, con tratamiento de feriados/fines de semana. No codificar una fecha universal para todos los contribuyentes. Fuente: https://www.sri.gob.ec/calendario-responsabilidades-tributarias
- La Resolución NAC-DGERCGC26-00000027 exige, para los proveedores comprendidos en su ámbito, registrar la actividad/establecimiento correspondiente y que los emisores incluyan el RUC del proveedor tecnológico en información adicional del comprobante. Existe discrepancia entre la noticia informativa del SRI (que expresa 60 días hábiles para el requisito del comprobante) y la disposición transitoria tercera del texto de la resolución publicado en el Registro Oficial (60 días calendario). El sistema debe registrar esta discrepancia documental y adoptar la interpretación confirmada formalmente por el responsable tributario; no ocultarla ni resolverla dentro de React. Texto de la resolución reproducido: https://uqa.com.ec/wp-content/uploads/2026/07/Newsletter-No.-030-SRI-registro-de-proveedores-de-sistemas-informaticos-o-servicios-de-facturacion-electronica-28-JULIO-2026-SRI-NAC-27.pdf y aviso: https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1

Para cada regla, almacenar autoridad, resolución/fuente, URL, versión, vigencia, periodo afectado, fecha de verificación, hash de archivo, persona que la revisó y prueba de regresión. El software no realiza automáticamente un cambio legal a partir de un texto web o una inferencia de IA.

### 18.9 Cierre de periodo y preparación tributaria con trazabilidad

El producto debe distinguir, por cada obligación y periodo:
- obligación aplicable confirmada / no aplica según perfil / pendiente de configurar;
- datos fuente incompletos;
- documentos internos pendientes de conciliar;
- documentos fiscales en proceso, autorizados, rechazados, anulados o con nota de crédito;
- borrador de cálculo;
- archivo generado;
- esquema/XML validado;
- pendiente de revisión de contador;
- aprobado por persona autorizada;
- presentado con evidencia;
- obligación cumplida conforme a la regla de declaración/pago aplicable.

Cada workpaper conserva:
- entidad legal/RUC y periodo;
- establecimiento cuando la obligación/documento requiera esa dimensión;
- filtro exacto y timestamp de corte;
- datos de origen y version/hash de snapshots;
- versión de reglas, formularios, catálogo ATS y esquema;
- fórmula por casilla/medida;
- excepciones y diferencia entre fuentes;
- comentarios, persona que preparó y persona que aprobó;
- artefacto exportado, resultado de validación y evidencia de presentación/pago, cuando exista.

No se debe mostrar “declaración presentada” al descargar un archivo. No debe deducirse “impuesto a pagar” desde ventas netas comerciales. Un cálculo incompleto se muestra como incompleto, no como cero ni como cifra definitiva.

### 18.10 API y comportamiento del backend ante ámbito N:M

Además de los endpoints de reportes de V1, se proponen estos contratos:
- GET /api/v1/organizations/{id}/taxpayers y GET /api/v1/taxpayers/{id}/establishments para el ámbito fiscal registrado.
- GET /api/v1/warehouses/{id}/establishment-assignments con filtro por tipo, estado y vigencia.
- GET /api/v1/sri-establishments/{id}/warehouse-assignments con paginación.
- POST/PUT de la relación a través de un servicio de dominio que valida vigencia, tenant, permisos y duplicados.
- GET /api/v1/reports/command-center con filtros independientes para legalEntityId, taxpayerId, establishmentId y warehouseId.
- GET /api/v1/reports/catalog que exponga qué medidas pueden agruparse por RUC/establecimiento/bodega, y qué relaciones requieren asignación de eventos o propiedad.
- POST /api/v1/reports/query que rechace joins que inflarían medidas o que no tengan reglas de atribución.
- POST /api/v1/reports/exports para exportaciones completas reproducibles con snapshot, límite y estado del job.
- GET /api/v1/reports/fiscal-reconciliation con filtros por RUC, establecimiento fiscal real, fecha/estado de documento, warehouse operativo, venta, devolución y suscripción.

En el backend, autorización y filtros de tenant deben aplicarse antes de agregar datos. No devolver al cliente los registros de otra entidad para ocultarlos luego en React. Validar consultas declarativas mediante allowlist de datasets/medidas/dimensiones, cardinalidad y presupuesto.

### 18.11 Pruebas adicionales obligatorias para la V2

1. Un establecimiento asociado a tres bodegas muestra tres relaciones y el stock real de cada bodega una sola vez.
2. Una bodega asociada a tres establecimientos no triplica stock, coste, reservas ni importes de compra/venta.
3. La asociación con dos RUC diferentes requiere owner/legalEntity segregation o bloquea el caso no soportado.
4. Un cambio de asociación el día 15 no reatribuye ventas del día 14.
5. Una factura se atribuye al establecimiento devuelto por el backend fiscal y no a todos los establecimientos relacionados con la bodega.
6. Una venta originada en una bodega con varias asociaciones no elige un emisor arbitrario; se aplica la regla autorizada o se detiene con error explicable.
7. Una orden surtida desde dos bodegas conserva detalle por fulfillment, una venta comercial coherente y un contrato fiscal explícito.
8. Una devolución recibida en otra bodega conserva referencia a venta/factura original, lugar físico de devolución y estado de la nota de crédito.
9. Un establecimiento cerrado conserva los movimientos históricos y alerta el uso fiscal nuevo; no elimina las bodegas vinculadas.
10. Un reporte por establecimiento usa ventas/atribución explícitas, no todas las bodegas de forma que duplique la misma venta.
11. Un reporte de stock filtrado por establecimiento explica si presenta stock físico de las bodegas relacionadas o stock explícitamente atribuido; no los confunde.
12. Una medida monetaria nunca se suma entre monedas sin una conversión versionada.
13. Una cantidad nunca se agrega entre unidades incompatibles.
14. El join de venta+líneas+movimientos+cuotas no multiplica ventas brutas, costos ni impuestos.
15. Un workpaper de IVA posterior al 1 de junio de 2026 no marca la obligación como cumplida con solo generar/enviar un borrador; considera el estado/pago según el régimen y las excepciones aplicables.
16. ATS de periodo septiembre 2026 usa el catálogo/programa vigente de ese periodo, sin modificar silenciosamente workpapers anteriores.
17. Cambio de porcentaje de retención se aplica según fecha/vigencia y clasificación, no como una constante global.
18. Un usuario sin permiso de la entidad A no puede inferir sus saldos a partir de un agregado donde A esté mezclada con B.

### 18.12 Orden recomendado de ejecución

**Primero (bloqueante):** eliminar mezclas de moneda; corregir el ámbito de todos los almacenes; separar métricas comerciales y fiscales; formalizar establecimiento, bodega y propietario en el catálogo analítico; añadir reglas de cardinalidad para las consultas.

**Segundo:** construir el puente N:M con vigencia/auditoría, la interfaz para gestionarlo y los filtros de reporte. La migración actual puede iniciar con asociaciones existentes sin inferir nuevas relaciones: cualquier vínculo inicial se confirma explícitamente.

**Tercero:** implementar command center e informes paginados/exportes de servidor para las fuentes actualmente completas.

**Cuarto:** integrar documentos fiscales reales, versionar reglas del SRI y preparar conciliaciones por RUC/establecimiento/documento.

**Quinto:** implementar el constructor multidominio con pruebas anti-doble-conteo y las primeras plantillas de venta-inventario-compra-suscripción.

**Sexto:** añadir libro contable, pagos/bancos, retenciones y artefactos de declaración. Mantener revisión/aprobación humana hasta validar la automatización con casos reales.

La V2 no autoriza a migrar a multi-RUC en producción sin inventario por propietario, aislamiento de permisos, pruebas de conciliación y la aceptación explícita del modelo de operación.
