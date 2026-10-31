# Auditoría de bugs, errores, inconsistencias y brechas funcionales

**Fecha:** 28 de octubre de 2026  
**HEAD inspeccionado:** master · 23c806d6ed5d06d1ff55e5a72be405167a79150f  
**Método:** inspección de frontend, controladores y servicios Spring Boot, DTO, configuración, migraciones, pruebas, documentación y GitHub Actions. Solo lectura: no se modificó código.

## Resumen ejecutivo

Se encontraron un fallo de CI reproducible, inconsistencias entre configuración/documentación y brechas de negocio en los flujos de venta, facturación, reembolsos y asignación de almacén. Algunas son defectos demostrados por el código; otras son funciones que todavía no están modeladas en la arquitectura actual y deben tratarse como trabajo pendiente, no como si ya existieran.

### Escala de prioridad

- **P1 — Bloqueante de despliegue o transacción real:** puede impedir una instalación limpia o no cumplir un flujo de venta/facturación que el producto necesita.
- **P2 — Importante:** puede confundir al usuario, causar resultados distintos entre capas o impedir mantenimiento seguro.
- **P3 — Mejora estructural:** reproducibilidad, rendimiento, observabilidad o documentación.
- **Por confirmar:** hay indicios concretos pero falta prueba automatizada o decisión de negocio para llamarlo bug definitivo.

## P1. Fallo confirmado de backend CI

**Hallazgo:** la ejecución del backend falla en el smoke test de Docker Compose aunque Maven verify pase.

**Evidencia:** [backend-ci, run 40809131253](https://github.com/JhonnyMinian92/inventary/actions/runs/40809131253).

El workflow entrega BOOTSTRAP_ADMIN_PASSWORD=Ci-Only-Bootstrap-Password-123!, más larga que la política común de 15 caracteres. En una base vacía, el bootstrap intenta crear un administrador y rechaza la contraseña; el contenedor reinicia y el smoke test termina en fallo.

**Impacto:** no se puede afirmar que el proceso de instalación desde cero esté validado. Un Docker local con volumen persistente y usuario ya creado puede arrancar porque el bootstrap inicial no necesita crear ese usuario otra vez.

**Corrección esperada cuando se autorice:** corregir los valores de configuración de CI y del ejemplo del backend, preservar el smoke test de base vacía y volver a ejecutar todo el workflow. Detalle: [AUDITORIA_FALLO_BACKEND_CI.md](AUDITORIA_FALLO_BACKEND_CI.md).

## P1. Venta: impuesto hardcodeado en cero

**Hallazgo confirmado en código:** la creación de la venta guarda tax como cero. SaleService crea la entidad Sale con BigDecimal.ZERO para tax; SalesApiTest valida que la venta tiene tax=0.00.

**Impacto:** si la venta se presenta como una factura con impuestos, el total no representa una liquidación tributaria real. No se debe interpretar el campo tax de la API como si ya se hubiera calculado IVA por tarifa/producto.

**Necesidad de diseño:** modelar códigos tributarios, tarifa, base imponible, exenciones/no objeto, redondeos y vigencia temporal según las reglas aplicables al comprobante. El servidor debe recalcular importes; el cliente presenta el detalle calculado y no decide la carga tributaria.

**Límite:** este hallazgo demuestra que el camino actual calcula el impuesto como cero; no pretende emitir una opinión tributaria sobre cada producto o régimen. Antes de producción se debe contrastar la normativa y ficha técnica vigente del SRI. Referencias oficiales: [Facturación Electrónica del SRI](https://www.sri.gob.ec/nl-BE/facturacion-electronica) y [esquema de emisión electrónica](https://www.sri.gob.ec/web/intersri/facturacion-electronica?p_l_back_url=%2Fbuscador%3Fdelta%3D20%26q%3Dcalculador).

## P1. Devolución de producto no equivale a reembolso de dinero

**Hallazgo confirmado:** la API POST /api/v1/sales-returns registra una devolución de artículos y exige un destino RESTOCK o SCRAP. SalesReturn y SalesReturnLine guardan cantidades, motivo, precio/totales por línea y el destino del artículo; no registran pago de reembolso, método de devolución del dinero, referencia al pago original ni estado de conciliación financiera. El árbol actual tampoco contiene un dominio de pagos.

**Impacto:** la devolución puede ajustar el inventario y reportarse como retorno, pero ese registro por sí solo no demuestra que el cliente recibió el dinero. Los informes restan los valores de las líneas devueltas; sin un libro de pagos/reembolsos se puede confundir una devolución de mercancía con una devolución monetaria ya ejecutada.

**Resultado:** brecha crítica para el flujo comercial solicitado. Debe existir una operación de reembolso separada y auditable, coordinada con el registro de devolución física. Si todavía no se integra una pasarela de pago, el sistema al menos debe registrar un reembolso manual autorizado con medio, importe, motivo, actor, fecha y referencia externa; eso no debe presentarse falsamente como un abono electrónico ejecutado.

Referencias de patrones de mercado: Shopify documenta devolución total o parcial, reposición opcional y reembolso; Lightspeed describe la devolución desde la venta original por artículo, con el importe correspondiente. [Shopify POS: devoluciones y reembolsos](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/order-management/complete-refund-orders) · [Lightspeed: refunding and exchanging](https://retail-support.lightspeedhq.com/hc/en-us/articles/229130768-Refunding-and-exchanging).

## P1. La venta exige que el cliente seleccione manualmente el almacén

**Hallazgo confirmado:** SaleDtos.CreateRequest exige warehouseId; SalesController recibe el DTO y SaleService toma ese almacén de la solicitud. AppUser solo contiene usuario, hash, rol, estado y versión de token; no contiene una asignación de almacén predeterminado. El modelo de usuario no permite derivar el almacén de venta desde el usuario autenticado.

**Impacto:** el sistema no puede cumplir actualmente el requisito «usar automáticamente el almacén autorizado para el usuario/caja». Depende de que la interfaz seleccione y envíe el almacén. En una operación rápida esta elección añade fricción y abre la posibilidad de usar una ubicación equivocada por descuido.

**Diseño esperado:** el servidor resuelve el almacén predeterminado mediante asignación explícita de usuario, terminal/caja o contexto operativo. La interfaz no debe poder imponer un almacén arbitrario. Solo si el producto no está disponible en la ubicación predeterminada se ofrece una búsqueda de otros almacenes permitidos; la sustitución debe respetar permisos, existencias concurrentes y trazabilidad.

## P1. Faltan datos de facturación como parte de la venta histórica

**Hallazgo confirmado:** BusinessPartner tiene name, taxId, email, phone y address. Sin embargo, Sale conserva de forma propia customerName y customerPhone; el DTO de venta no expone una instantánea de identificación fiscal, email, dirección de facturación y dirección de envío/retiro por separado. La venta sí puede referenciar un cliente persistente, pero eso no equivale a guardar los datos exactos usados en el documento que se emitió en el momento de vender.

**Impacto:** los datos históricos del comprobante no están completos como snapshot. Si se necesita reconstruir lo facturado tal como se emitió, depender de datos mutables del cliente es frágil. Tampoco está modelada en Sale la distinción entre dirección de facturación, dirección de entrega y retiro por el cliente.

**Diseño esperado:** snapshot inmutable de comprador/facturación en el documento de venta, y datos operativos de cumplimiento separados. El consumidor final debe ser un perfil de venta explícito que cumpla la regulación aplicable; no debe suponerse que todos los datos personales sean obligatorios para todas las transacciones ni almacenar de más.

## P1. Venta, devolución, anulación fiscal y pago aún no son una transacción comercial completa

El backend ya tiene una ruta robusta de solicitud/aprobación para cancelar una venta contabilizada: exige actores separados para la aprobación y genera movimientos compensatorios. Es una base importante de integridad.

No encontré, sin embargo, el dominio de comprobante electrónico del SRI, una nota de crédito electrónica ni un dominio de reembolsos y liquidaciones de pago. En consecuencia, cancelar una Sale y recuperar stock en el inventario no debe confundirse con revocar fiscalmente un comprobante ya autorizado ni con enviar dinero al cliente.

**Riesgo:** un usuario puede comprender «cancelar» como cancelar la operación en su totalidad —mercancía, inventario, documento tributario y dinero— mientras que el sistema actual modela principalmente el estado de venta y sus movimientos compensatorios.

**Corrección de diseño:** diferenciar anulación antes de confirmar la operación, cancelación operativa autorizada, devolución parcial, devolución total, nota de crédito/comprobante tributario y reembolso monetario. Cada resultado debe tener estado propio y vínculo de auditoría.

## P2. Diferencia entre el mínimo de precio que muestra el frontend y el que valida el backend

**Hallazgo identificado en las dos capas:**

- SaleForm.jsx solicita cotización a /api/v1/sales/pricing para todos los productos seleccionados, no solo los que controlan stock. La interfaz comprueba minimumPrice y bloquea el paso cuando el precio introducido es inferior.
- SalePricingService calcula el coste estimado con capas FIFO cuando hay stock disponible, pero para elementos sin ubicación de inventario utiliza el coste de fallback del producto.
- SaleService tiene un camino explícito para servicios y bienes sin control de existencias; en ese camino calcula el importe de venta sin el flujo de salida física/costeado de stock. La validación de no pérdida se ejecuta en el camino de inventario para productos con stock.

**Impacto potencial:** para servicios o productos sin stock, el frontend puede bloquear un precio que el camino real de venta del servidor sí acepta. Esto crea una regla distinta entre UI y API.

**Estado:** inconsistencia estática confirmada en la estructura de las dos capas; requiere test de integración para demostrar un ejemplo específico reproducible antes de estimar severidad económica.

**Corrección esperada:** el endpoint de cotización debe devolver el régimen de precio aplicable al tipo de producto y el frontend solo debe aplicar una regla que coincida con el servidor. Si la política comercial permite descuentos de servicios, deben evaluarse mediante permisos/políticas de descuento y no mediante un coste de inventario sin sentido para ese producto.

## P2. Configuración de bootstrap del backend también es inválida en el ejemplo

El archivo microservices-inventary-ecuapp/.env.example contiene LocalOnly-StartSecurePassword-123!, que supera los 15 caracteres. El ejemplo raíz utiliza Cambiar-Esta1!, que sí cabe en el límite. Esto puede producir el mismo fallo de inicialización en una instalación limpia si se usa el ejemplo del backend sin editar.

## P2. Documentación incompatible con el HEAD

- El README raíz describe Flyway V1–V15, pero el árbol actual contiene migraciones fuente hasta V18.
- El README del frontend afirma que falta package-lock.json y que el package.json no declara test; en el árbol revisado existen el lockfile y el script npm test.
- El README del frontend menciona Overview.jsx y CatalogTables.jsx como archivos actuales; no existen en el árbol. Los componentes actuales son Dashboard.jsx y ProductsPage.jsx.
- Algunas auditorías históricas contienen SHA, estados y limitaciones de snapshots anteriores. Aunque son útiles como registro, no son una fuente vigente sin validación en el HEAD actual.

**Impacto:** la documentación induce a navegar a archivos inexistentes y dificulta decidir cuál es el estado real de la entrega.

## P2. El diseño de ventas dificulta un flujo real de mostrador

En App.jsx la sección de ventas renderiza SalesTable y después SalesReturns en el mismo panel, en una secuencia vertical. La devolución queda como bloque separado al final, no vinculada visualmente a los artículos de la venta que se desea devolver.

El flujo obliga a salir mentalmente de la venta original y buscar el formulario de devolución por separado. Para operaciones parciales, selección de artículos, ajuste de cantidades y devolución de dinero, la interfaz debería partir de la venta original y mostrar cada línea con cantidad comprada, cantidad ya devuelta, saldo aún retornable, disposición del producto y valor reembolsable.

Esto es un problema de usabilidad confirmado en el layout actual y una brecha en el flujo funcional de devolución descrito arriba.

## P2. Promociones y descuentos comerciales todavía no están modelados

No se encontró un dominio explícito para códigos de promoción, reglas con rango de fechas, listas de precios por canal, remate de últimas unidades, límites de descuento por rol ni auditoría de redenciones. Sale acepta un precio unitario y el servidor valida la regla de no pérdida para artículos con stock.

Esto no es necesariamente un bug en un sistema que no ha implementado promociones; sí es una brecha frente al flujo de ventas solicitado. Los descuentos futuros necesitan cálculo server-side, vigencia verificable, límites, motivo y permisos. No se debe aceptar como autoridad un precio promocional calculado solamente en JavaScript.

## P2. No está modelado un contexto de envío/retiro separado del comprador

La venta lleva almacén y opcionalmente cliente, pero no entrega/retirada, destinatario, instrucciones, costes de entrega o estado logístico vinculados al documento. Los datos de cliente no deben forzarse a servir simultáneamente como dirección de facturación y de entrega.

## P3. Instalación frontend menos reproducible en CI que en Docker

El repositorio tiene package-lock.json y el Dockerfile del frontend usa npm ci. El workflow de frontend, en cambio, ejecuta npm install. Es mejor que CI use npm ci para validar exactamente el árbol de dependencias bloqueado por el lockfile.

El frontend está en verde en el SHA actual, así que esto es una mejora de reproducibilidad, no una causa del fallo backend.

## P3. Rate limit no distribuido

ApiRateLimitFilter utiliza caché local Caffeine por instancia. El código explica que es un rate limit de mejor esfuerzo por instancia y que el control distribuido debe delegarse a una puerta de enlace/WAF cuando haya múltiples réplicas. La limitación está documentada por el propio diseño; no debe anunciarse como rate limit global distribuido.

## P3. Pruebas de interfaz incompletas como evidencia de producto

Existen tests unitarios de router, cifrado de login y CSV, además de pruebas de integración backend. No encontré evidencia de una suite automatizada de navegador que recorra el flujo real: seleccionar productos, calcular importes e impuestos, elegir cliente, completar una venta, devolver parcialmente artículos y verificar el resultado de inventario y dinero.

No implica que el flujo falle en todos los casos; significa que los tests disponibles no acreditan todavía ese recorrido de extremo a extremo.

## P3. Productos: diferencias entre capacidades backend y experiencia visible

El backend tiene más capacidades de catálogo que las presentadas por una pantalla de búsqueda básica: atributos dinámicos, imágenes, unidades y conversiones, tipos de producto/servicio y servicios por suscripción. El UI debe evitar mezclar stock operativo con la ficha maestra de producto y ofrecer vistas/pistas de validación coherentes con los permisos financieros. Las mejoras se detallan en PROPUESTA_UX_PRODUCTOS.md.

## Orden recomendado de resolución

1. Corregir configuración de CI y demostrar instalación limpia verde.
2. Definir almacén por usuario/caja en backend y dejar de pedir una selección rutinaria al vendedor.
3. Definir el contrato fiscal y de impuestos; no conservar el impuesto cero si se espera facturación real.
4. Modelar snapshot de datos de facturación y datos de entrega/retiro.
5. Diseñar devolución parcial/total con ajuste de existencias y reembolso monetario explícito.
6. Unificar reglas de precio frontend/backend y diseñar políticas de descuento auditadas.
7. Actualizar documentación, luego hacer pruebas E2E y mejorar la reproducibilidad del CI.

## Referencias de mercado y regulatorias

- [Shopify POS: gestionar devolución, reembolso y reposición](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/order-management/complete-refund-orders)
- [Shopify: refunding orders](https://help.shopify.com/en/manual/fulfillment/managing-orders/refunding-orders)
- [Lightspeed: refunding and exchanging](https://retail-support.lightspeedhq.com/hc/en-us/articles/229130768-Refunding-and-exchanging)
- [SRI: facturación electrónica](https://www.sri.gob.ec/nl-BE/facturacion-electronica)
- [SRI: ambiente de pruebas/certificación y producción](https://www.sri.gob.ec/web/intersri/facturacion-electronica?p_l_back_url=%2Fbuscador%3Fdelta%3D20%26q%3Dcalculador)

Este documento es un registro de hallazgos estáticos. Las correcciones están propuestas, no ejecutadas.


## Seguimiento de implementación — rama `implementacion_propuestas`

> Esta auditoría fue levantada sobre un snapshot anterior de `master`; sus hallazgos no deben interpretarse como estado actual sin contrastarlos con la rama de trabajo. El seguimiento consolidado y la secuencia de ejecución están en [PLAN_EJECUCION_PROPUESTAS_INVENTARY.md](PLAN_EJECUCION_PROPUESTAS_INVENTARY.md).

| Hallazgo | Estado tras Fase 1 | Evidencia / trabajo restante |
|---|---|---|
| P1 — Contraseña bootstrap de CI demasiado larga | Corregido en workflow backend | Se usa una contraseña CI corta y se mantiene el smoke test de base vacía; falta confirmar el run verde del SHA final. |
| P2 — Contraseña inválida en `.env.example` del backend | Corregido | `BOOTSTRAP_ADMIN_PASSWORD=LocalOnly-123!`; solo para desarrollo local. |
| P2 — Precio mínimo aplicado en frontend a servicios/no stock | Corregido en backend y frontend | El quote devuelve precio de catálogo y mínimo cero para tipos sin stock; se añadió prueba API. Falta ejecutar Maven verify y la suite frontend. |
| P3 — CI frontend usa instalación no reproducible | Corregido | Workflow usa `npm ci`, `npm test` y `npm run build`; falta evidencia de ejecución verde. |
| Artefactos generados Maven versionados | Corregido | Retirados los 19 archivos de la ruta literal `${project.build.directory}` y añadida exclusión a Git. |
| Documentación raíz/frontend desactualizada | Parcialmente corregido | READMEs actualizados y roadmap maestro añadido. Las referencias históricas de esta auditoría siguen describiendo el snapshot original. |

Los demás hallazgos de P1/P2 —impuestos y contrato fiscal, snapshots de facturación, asignación de almacén por usuario/caja, devolución monetaria, separación entre cancelación comercial y fiscal, descuentos y logística— permanecen pendientes y se distribuyen en las fases 3, 4 y 5 del plan maestro. No se consideran resueltos por los cambios de estabilización.


## Adenda de trazabilidad — consolidación completa de propuestas · 2026-10-30

El estado de los hallazgos y las brechas de producto se ejecutará conforme al plan y a la matriz que consolidan los 22 documentos raíz:
- [Plan maestro de implementación](PLAN_EJECUCION_PROPUESTAS_INVENTARY.md)
- [Matriz de trazabilidad de propuestas](MATRIZ_TRAZABILIDAD_PROPUESTAS_INVENTARY.md)

Los defectos corregidos en Fase 1 incluyen la regla de precio mínimo aplicada indebidamente a SERVICE/NON_STOCKED_GOOD, la configuración de bootstrap y la reproducibilidad de CI; se añadieron pruebas de regresión y se validaron los workflows. Impuestos/SRI, snapshots fiscales completos, ámbitos de usuario/caja, pagos/reembolsos, descuentos, fulfillment y pruebas E2E permanecen asignados a fases posteriores, no se declaran resueltos por esta Fase 1.
