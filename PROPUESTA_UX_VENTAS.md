# Propuesta integral de UX/UI y flujo comercial para Ventas

**Producto:** Inventary · EcuApp  
**Base inspeccionada:** master · 23c806d6ed5d06d1ff55e5a72be405167a79150f  
**Alcance:** pantalla de ventas, proceso de cobro, datos de comprador, devolución, inventario y contratos API necesarios. Documento de propuesta; no se modificó código.

## 1. La venta real no es solo insertar una fila

Una venta de mostrador debe ayudar a la persona a tomar decisiones comerciales rápidas y correctas: encontrar los productos, identificar precios y cantidades, revisar promociones, seleccionar cómo se facturará, decidir cómo se entregará o retirará, revisar los impuestos/importes y completar la operación.

Después de completarla, el negocio debe poder encontrar la venta original, conocer qué se facturó y pagó, devolver una o varias líneas, calcular cuánto corresponde devolver, ajustar el inventario según condición de la mercancía y registrar el reembolso. Una devolución parcial no debe ser una venta borrada ni una salida arbitraria de stock.

La pantalla necesita reducir el esfuerzo de operación sin ocultar controles importantes. La interfaz debe hacer lo frecuente fácil y lo riesgoso explícito.

## 2. Qué existe hoy y por qué no alcanza para el flujo deseado

En App.jsx, Ventas renderiza SalesTable y después SalesReturns como dos bloques consecutivos en el mismo panel. El formulario SaleForm pide elegir un almacén de salida, tiene un stepper de tres etapas, permite varias líneas de producto y usa cotizaciones del backend para precio sugerido/mínimo. Es una base reutilizable, pero no corresponde todavía al flujo comercial completo que se plantea aquí.

En las API actuales:

- SaleDtos.CreateRequest exige warehouseId.
- Sale y su DTO contienen el total, subtotal, tax, canal, almacén, cliente vinculado, nombre/teléfono de cliente y artículos.
- La ruta de creación guarda tax en cero en el flujo actual.
- Los datos generales de BusinessPartner incluyen identificación, email, teléfono y dirección, pero la venta no conserva todos esos datos como una instantánea histórica de facturación.
- POST /sales-returns registra la venta de origen, líneas, cantidades, motivo y destino RESTOCK/SCRAP; no modela un reembolso monetario ni el medio de pago que devuelve el dinero.
- La cancelación de una venta está separada entre solicitud y aprobación, con requisito de actores distintos.

Estos elementos permiten parte de la operación y la trazabilidad, pero hay que distinguir venta, factura, pago, movimiento físico y reembolso. Son entidades relacionadas, no sinónimos.

## 3. Investigación de referentes de mercado

### Shopify POS: la devolución debe partir de la venta y del artículo original

Shopify POS permite localizar una orden, seleccionar los artículos que se devuelven, elegir si se reponen en inventario y procesar un reembolso. La documentación indica que el reembolso puede ser total o parcial, que el stock se repone en la ubicación asignada si se elige esa opción y que el usuario debe contar con permisos adecuados.

Aplicación para Inventary: en el detalle expandible de una venta, seleccionar el artículo original y la cantidad a devolver. Calcular el saldo todavía retornable y enseñar por separado el destino del artículo y el reembolso del dinero.

Fuentes: [Shopify POS: devolver y reembolsar](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/order-management/complete-refund-orders) · [Shopify: refunds](https://help.shopify.com/en/manual/fulfillment/managing-orders/refunding-orders).

### Lightspeed Retail: devolución enlazada al origen, con cantidad y monto

Lightspeed permite localizar una venta original por recibo, cliente o artículo y seleccionar los artículos individuales que se reembolsan. También distingue una transacción anulada antes de procesarse de un reembolso posterior a una venta.

Aplicación para Inventary: una operación ya facturada debe referenciar el documento de origen y registrar sus devoluciones. La UI debe diferenciar «anular antes de finalizar» de «emitir devolución/reembolso después de completada».

Fuente: [Lightspeed: refunding and exchanging](https://retail-support.lightspeedhq.com/hc/en-us/articles/229130768-Refunding-and-exchanging).

### Baymard Institute: reducir fricción y dudas al completar una transacción

Baymard agrupa años de investigación de usabilidad del checkout, incluidos campos de cliente/dirección, validación de errores, persistencia de datos, autocompletado, métodos de entrega, retiro y resumen de pedido. Su benchmark ha analizado cientos de sitios de alto volumen.

Aplicación para Inventary: pedir los datos en un orden lógico, evitar campos que el usuario no necesita para su caso, explicar por qué se requiere un dato, conservar la información ante errores y mantener visible un resumen total. La información tributaria aplicable no se puede eliminar por conveniencia, pero sí puede solicitarse solo cuando corresponda y validarse con las reglas correctas.

Fuente: [Baymard: Checkout Usability Research](https://baymard.com/research/checkout-usability).

## 4. Investigación científica y principios que realmente se pueden sostener

### Hick (1952): la incertidumbre de opciones influye en tiempo de reacción

La investigación original de W. E. Hick relaciona el tiempo de reacción en tareas de elección con la incertidumbre sobre la respuesta. El principio práctico no es reducir el número de productos que se pueden vender; es facilitar el acceso a la opción correcta mediante búsqueda, lectura visual, orden, filtros útiles y selección por teclado/barcode.

Fuente: Hick, «On the Rate of Gain of Information», [DOI 10.1080/17470215208416600](https://journals.sagepub.com/doi/10.1080/17470215208416600).

### Choice overload: demasiadas opciones no siempre perjudican

Scheibehenne, Greifeneder y Todd analizaron 63 condiciones de 50 experimentos, con 5.036 participantes, y encontraron un efecto promedio cercano a cero de choice overload, con heterogeneidad significativa. La conclusión para un POS no es esconder funciones de ventas; es presentar lo relevante en el momento correcto, sin obligar a recordar datos entre pantallas.

Fuente: [Journal of Consumer Research, DOI 10.1086/651235](https://doi.org/10.1086/651235).

### Defaults: las elecciones por defecto influyen, pero no deben ser invisibles

Un meta-análisis de 58 estudios y 73.675 participantes encontró un efecto promedio de los defaults, aunque con variación importante entre contextos. Eso apoya precargar el almacén autorizado, el método de entrega más frecuente o la lista de precios pertinente, siempre que el usuario pueda reconocerlos y corregirlos cuando esté permitido.

Para la asignación de almacén, la seguridad prevalece sobre la comodidad: el default lo debe resolver el servidor a partir del usuario/terminal, no confiarse a un valor arbitrario de React.

Fuente: Jachimowicz et al., «When and why defaults influence decisions», [DOI 10.1017/bpp.2018.43](https://doi.org/10.1017/bpp.2018.43).

### Riesgo percibido y abandono del carrito: evidencia de 2024

Rodrigues, Proença y Soares estudiaron el riesgo percibido relacionado con el abandono de carrito en comercio móvil a partir de 369 personas con experiencia en compras móviles. El estudio encontró una influencia significativa de los riesgos relacionados con la transacción y con el objeto comprado, y destaca la importancia de calidad de información y confianza. Esto no prueba que una interfaz concreta aumente las ventas de Inventary, pero sí justifica que el vendedor pueda comprobar claramente el producto, importe, descuento, impuesto, comprador y condiciones antes de finalizar.

Aplicación para Inventary: no ocultar cargos hasta el último paso, explicar por qué cambió un precio, mantener visibles el total y el estado de stock, y conservar el carrito ante un error técnico. El sistema debe diferenciar una petición fallida de un resultado de venta incierto.

Fuente: Rodrigues, Proença y Soares, «The influence of perceived risk on mobile shopping cart abandonment», Journal of Strategic Marketing (publicado online en 2022; volumen 2024), [DOI 10.1080/0965254X.2022.2160480](https://www.tandfonline.com/doi/full/10.1080/0965254X.2022.2160480).

### Defaults en configuradores de compra: efecto sobre las ventas

Herrmann y coautores estudiaron opciones preseleccionadas en un configurador de bicicletas y encontraron que mover un default de nivel de menor precio a mayor precio afectaba las elecciones y podía aumentar la venta del nivel seleccionado y opciones cercanas. El estudio demuestra que los defaults influyen, pero no respalda usarlos de forma opaca para empujar a la persona hacia un precio más alto.

Aplicación para Inventary: precargar solamente datos operativos justificados —almacén asignado por servidor, precio vigente autorizado, cliente seleccionado si ya fue elegido, y método de entrega habitual del contexto—. Los descuentos, precios por lista y promociones deben ser visibles; el comprador no debe recibir cargos escondidos ni se debe manipular el valor de una venta mediante defaults silenciosos.

Fuente: Herrmann et al., «The effect of default options on choice—Evidence from online product configurators», Journal of Retailing and Consumer Services 18(6), 2011, pp. 483–491, [DOI 10.1016/j.jretconser.2011.06.005](https://www.sciencedirect.com/science/article/pii/S0969698911000592).

## 5. Rediseño de la pantalla de Ventas

### 5.1 Vista principal: pestañas Ventas y Devoluciones

En lugar de poner el formulario de devoluciones como bloque al final de la pantalla:

- **Tab Ventas:** listado, filtros, estado, búsqueda por número/cliente/identificación, total, fecha, vendedor y estado de facturación/pago.
- **Tab Devoluciones:** listado de devoluciones en curso/completadas, fecha, venta original, cliente, artículos, monto, estado de inventario y estado del reembolso.

Las pestañas deben compartir navegación, pero los controles de una devolución no deben mezclarse con acciones de una venta nueva.

### 5.2 Expandir una venta con acordeón

Cada fila o tarjeta de venta debe poder expandirse sin abandonar el listado. El resumen incluiría número de venta, cliente, fecha, total, estado de venta, estado de comprobante y estado de pago.

Al expandir:

- lista de artículos con miniatura, nombre, SKU, cantidad comprada y cantidad ya devuelta;
- precio por unidad, descuentos aplicados e impuestos asociados a la línea;
- cantidad todavía retornable;
- valor reembolsable por línea, calculado con las reglas de la transacción original;
- almacén/ubicación donde se originó la salida;
- historial de devoluciones/anulaciones vinculadas;
- botones según permiso: ver comprobante, solicitar anulación, iniciar devolución, registrar reembolso, reimprimir o consultar auditoría.

Si hay muchos artículos, el acordeón debe virtualizar/paginar el detalle cuando sea necesario. Expandir varias ventas a la vez no debe perder el foco del usuario.

### 5.3 Flujo de devolución iniciado desde la venta original

1. El vendedor abre la venta original.
2. Selecciona uno o varios artículos y la cantidad que se devuelve.
3. El sistema calcula cantidad pendiente por artículo, descuentos prorrateados, base tributaria, impuestos y total reembolsable.
4. El vendedor selecciona motivo: defectuoso, mercancía equivocada, arrepentimiento, garantía u otro motivo configurado.
5. Decide el destino físico: reingresar a inventario, cuarentena/inspección, merma/descarte o ubicación autorizada. No reingresar físicamente por defecto un artículo defectuoso.
6. Selecciona el medio de reembolso. Priorizar devolución al medio original si hay integración disponible; en caso manual, registrar la entrega de dinero con medio, referencia y actor autorizado. Si no hubo pago, no mostrar una acción que diga que se reembolsó dinero.
7. La API valida cantidades y estados otra vez dentro de la transacción, registra la devolución y sus movimientos; el reembolso queda como entidad relacionada y auditable.
8. Mostrar un resultado único que distinga «mercancía registrada», «inventario actualizado», «documento fiscal emitido/pendiente» y «dinero reembolsado/pendiente».

La devolución no debe aceptar una cantidad superior a lo vendido menos lo ya devuelto. Dos usuarios devolviendo el mismo artículo de forma concurrente no pueden exceder ese límite.

### 5.4 Anulación antes de completar vs devolución posterior

No llamar a todas las operaciones «cancelar»:

- **Borrador de venta:** puede descartarse porque todavía no se completó la transacción.
- **Antes de pago/confirmación fiscal:** puede ser posible anular según el estado y reglas.
- **Venta completada sin emisión fiscal:** seguir la política de negocio para cancelación autorizada y compensación de stock.
- **Comprobante electrónico ya autorizado:** gestionar el documento fiscal correctivo que corresponda, no solo cambiar el estado de Sale.
- **Devolución parcial posterior:** conservar la venta original y crear un documento de devolución por líneas/cantidades.
- **Devolución total:** el total devuelto puede alcanzar el monto original, pero la venta y su historia no se eliminan.
- **Reembolso monetario:** es una operación financiera distinta y debe tener estado, actor, referencia de pago, medio, importe y conciliación.

El sistema ya aplica solicitud y aprobación separadas para la cancelación de venta, una práctica que debe mantenerse para operaciones sensibles. La interfaz debe explicar quién solicita y quién autoriza, sin confundir ese permiso con el reembolso.

## 6. Flujo para registrar una nueva venta: tres pasos centrados en la tarea

### Step 1 — Productos y precios

Esta debe ser la etapa principal: al entrar, el foco estará en buscar/agregar productos, no en escoger almacén o llenar datos del comprador.

**Selector visual de productos**
- tarjetas con fotografía, nombre, SKU/código de barras, precio de venta vigente y disponibilidad del almacén asignado;
- búsqueda por nombre, SKU o barcode, autocompletado con teclado y selección rápida;
- categorías/recientes/favoritos si los datos del negocio lo justifican;
- indicador claro de servicio, mercancía con stock o producto sin control de stock;
- selección de varios artículos sin cerrar la búsqueda;
- control de cantidad +/−, edición directa de cantidad y mensaje si no hay disponibilidad;
- cada línea del carrito muestra precio, cantidad, descuento aplicable y subtotal.

**Precio y subtotal dinámicos**
- al seleccionar un artículo, el backend calcula la cotización y el precio sugerido autorizado para el contexto de venta;
- cargar el precio sugerido como precio de venta propuesto y mostrar claramente que el usuario puede editarlo si tiene permiso;
- multiplicar precio por cantidad y actualizar subtotal de línea e importe de carrito en el acto;
- si cambia el precio, cantidad, promoción o producto, actualizar cálculos de forma reactiva;
- confirmar en servidor el precio, stock, descuento y reglas de costo en el comando final, no confiar en cifras de frontend.

En la UI usar «Precio sugerido» o «Precio de venta», no «costo» cuando se muestra al vendedor el importe que debe cobrarse. El coste de adquisición y margen reales son datos sensibles y solo se exponen a los roles que tienen permiso.

**Descuentos**
- código promocional;
- campaña con fechas de inicio/fin;
- lista de precios por cliente/canal;
- descuento autorizado por rol;
- liquidación/remate de últimas unidades con política explícita;
- descuento por volumen, si el negocio lo decide.

El servidor debe comprobar vigencia, combinación de promociones, límites, cantidad, stock elegible y permisos. Registrar la regla aplicada en la venta. Nunca aceptar un descuento únicamente porque el navegador envió una cifra menor.

**Almacén automático**
No mostrar «Almacén de salida» como campo de inicio rutinario. El servidor obtiene el almacén predeterminado desde la asignación del usuario, caja o terminal autenticada. La cabecera puede comunicar discretamente «Vendiendo desde: Local Centro» para que la persona sepa dónde se registrará la operación.

Si un producto no está disponible allí:
- ofrecer «Buscar en otro local» únicamente si el usuario puede operar en esa ubicación;
- mostrar disponibilidad comprobada por almacén;
- exigir selección explícita solo para ese artículo/operación;
- registrar qué almacén abasteció la venta y no asumir un traslado silencioso;
- revalidar stock durante el registro para evitar que dos usuarios vendan la última unidad.

### Step 2 — Facturación y entrega/retiro

Separar «quién compra y recibe el comprobante» de «quién recibe la mercancía».

**Comprador/facturación**
- cliente guardado o consumidor final;
- tipo de identificación y número de identificación fiscal (cédula/RUC u opción permitida);
- nombre o razón social;
- teléfono;
- email;
- dirección de facturación cuando aplique;
- autocompletar desde cliente existente;
- opción de guardar los nuevos datos con el consentimiento y permisos adecuados.

Los campos requeridos deben depender del tipo de comprobante, monto, régimen y condiciones aplicables. No implementar un umbral fijo deducido de una guía antigua; consultar la ficha técnica/regla vigente del SRI. El sistema debe validar datos según la normativa de Ecuador sin bloquear injustificadamente operaciones para las que no sean obligatorios.

**Entrega o retiro**
- retirar en local;
- entrega a domicilio;
- destinatario diferente del comprador;
- dirección de entrega independiente;
- teléfono de contacto e instrucciones necesarias;
- fecha/ventana de entrega si el negocio lo ofrece;
- costes de entrega calculados en el servidor si existen.

La dirección de facturación y la de entrega no deben compartir el mismo campo ni sobrescribirse automáticamente.

### Step 3 — Confirmación de importes y finalización

Mostrar un resumen legible y persistente:

- artículos y cantidades;
- subtotal por línea;
- precios unitarios;
- descuentos por línea y total;
- base imponible agrupada por tratamiento tributario;
- impuestos desglosados;
- entrega u otros cargos;
- total a pagar;
- importe recibido, saldo pendiente o crédito si ese flujo existe;
- comprador y tipo de comprobante;
- entrega/retiro y ubicación que abastecerá;
- promociones y sus condiciones.

Solo después de que todos los importes e información sean válidos debe aparecer el CTA principal «Completar venta» / «Registrar venta». No se debe llamar «Factura emitida» si lo único que ocurrió fue crear un registro interno. Si hay emisión electrónica, mostrar su estado real.

La confirmación debe prevenir doble envío con una clave idempotente y bloquear la acción mientras se procesa. Si hay una caída de red con resultado incierto, consultar el estado antes de repetir la venta.

## 7. Esquema de interacción y jerarquía visual

### Paso 1
Un área de búsqueda amplia y prominente, selector visual, carrito editable y resumen lateral o inferior persistente según el ancho. En desktop, el resumen no debe empujar el selector fuera de la vista; en móvil, debe compactarse en una barra de total desplegable.

### Paso 2
Formularios agrupados con etiquetas explícitas, autocompletado, campos opcionales marcados y validación inline. Mostrar la separación visual entre comprador y entrega. Evitar pedir dos veces la misma información si ya existe y está autorizada para usarla.

### Paso 3
Resumen sin campos editables que no sean necesarios. Cada importe debe tener una explicación y volver al paso correspondiente al editarlo. Mostrar errores específicos y llevar el foco al primer problema.

### Skeletons y feedback

- búsqueda inicial: skeleton de tarjetas/lista;
- consulta del precio: precio marcado como «calculando» y subtotal coherente, sin saltos;
- búsqueda de disponibilidad: indicador junto al artículo, no una pantalla bloqueada;
- confirmar: CTA con estado de envío y prevención de doble clic;
- éxito: número de venta y estado real del comprobante/pago;
- error de red: conservar carrito y datos; no limpiar formulario ni asumir que no se grabó nada;
- animaciones breves; soportar movimiento reducido y navegación por teclado.

## 8. Contratos API que deben evolucionar con el frontend

No es posible resolver esta experiencia solo con CSS. El backend necesita representar los hechos de negocio para que los importes y permisos sean confiables.

### 8.1 Contexto de venta y almacén

El usuario/terminal debe tener un almacén asignado explícitamente o heredar uno de una configuración controlada. El servidor resuelve la ubicación en sesión y solo permite overrides a roles y casos autorizados. No confiar en un warehouseId enviado por el navegador sin comparar permisos.

### 8.2 Cotización

Una API de cotización debe calcular por línea:

- precio sugerido;
- precio aplicable por lista/canal/cliente;
- descuentos válidos y su origen;
- precio y cantidad elegibles;
- impuestos por código/tarifa aplicables;
- total de línea y carrito;
- stock disponible y ubicación seleccionada;
- versión/expiración de la cotización cuando aplique.

El servidor recalcula al completar venta. Si cambia stock, promoción, tarifa o precio, el usuario debe poder revisar la diferencia antes de confirmar.

### 8.3 Venta y datos históricos

El documento de venta debe guardar snapshots de los datos utilizados para facturar, las líneas, importes, descuentos, bases e impuestos; y referencias separadas a cliente y datos de cumplimiento/entrega. Debe quedar definido qué datos son inmutables una vez que se emite el comprobante.

Si se incorpora un proveedor fiscal del SRI, el modelo debe guardar estado, clave de acceso/identificador correspondiente, respuesta/autorización, representación legible y documentos correctivos relacionados según el contrato vigente. No asumir que una venta interna ya es un comprobante electrónico autorizado.

### 8.4 Promociones y descuentos

Los descuentos se deben validar en servidor con reglas, fechas, zonas horarias, monedas, límites, combinaciones y permisos. Registrar el identificador de la regla, el valor calculado y el usuario que autorizó una excepción. La UI muestra el precio calculado, no fabrica la regla.

### 8.5 Devolución y reembolso

Mantener separadas las entidades enlazadas:

- devolución de artículos con líneas y cantidad devuelta;
- disposición del artículo y movimiento de inventario;
- cálculo de impuestos/descuentos prorrateados;
- reembolso financiero por un importe específico y a un medio de pago;
- documento fiscal correctivo si aplica;
- eventos de auditoría y estado de conciliación.

No reutilizar el mismo endpoint para «hacer que vuelva el producto al inventario» y «devolver el dinero», porque son efectos diferentes. Puede existir un flujo de UI unificado, pero las APIs deben registrar cada resultado de modo explícito y transaccional o coordinado con estados recuperables.

Todas las operaciones deben soportar idempotencia. La devolución debe comprobar la cantidad acumulada ya devuelta, con bloqueo y restricción apropiados, para impedir dobles devoluciones concurrentes.

## 9. Matriz de escenarios de negocio obligatorios

Antes de dar por terminado el rediseño, probar como mínimo:

1. Venta rápida a consumidor final de un artículo, sin elegir almacén manualmente.
2. Venta a cliente existente con identificación/datos de factura precargados.
3. Venta a cliente nuevo con dirección de facturación distinta a la de entrega.
4. Carrito de varios productos con cambio repetido de cantidad y precio, comprobando cada subtotal, total e impuesto.
5. Precio promocional vigente y código inválido, expirado o no combinable.
6. Descuento mayor al máximo del vendedor: backend rechaza con mensaje entendible.
7. Artículo sin existencias en el local asignado: consulta de otros locales permitidos y revalidación al completar.
8. Dos ventas concurrentes intentan vender las últimas unidades: una no debe sobre-vender.
9. Error de red tras confirmar: el usuario vuelve a consultar el resultado y no crea dos ventas.
10. Devolver una cantidad parcial y luego el resto del artículo.
11. Intentar devolver más de lo comprado o devolver la misma línea desde dos sesiones.
12. Devolver un artículo defectuoso sin volverlo a existencias disponibles.
13. Reembolso parcial por el medio permitido y comprobación del límite pagado original.
14. Devolución total con artículos de distintas disposiciones físicas.
15. Venta anulada antes de emisión frente a venta con comprobante electrónico ya autorizado.
16. Verificar libro de movimientos, reportes netos, estado fiscal, pago y reembolso en cada variante.
17. Probar rol sin permiso de descuento, de anulación o de reembolso.
18. Probar teclado, lector de pantalla, móvil, estados de carga/error y recuperación después de validaciones fallidas.

## 10. Métricas de éxito

Medir en tareas reales y no decidir solo por apariencia:

- mediana y percentil 90 de tiempo desde abrir Ventas hasta añadir el primer artículo y completar la venta;
- segundos para escanear/buscar un producto;
- errores de selección de almacén y operaciones que deben corregirse;
- precio editado manualmente, cotización rechazada y discrepancias entre UI y servidor;
- abandonos por paso;
- campos del comprador que provocan errores;
- tiempo para localizar una venta y iniciar devolución;
- tiempo de una devolución parcial;
- diferencia entre total reembolsable calculado y dinero efectivamente reembolsado;
- discrepancias entre stock, venta, devolución, pago e impuestos;
- tasa de ventas duplicadas/reintentos y conflictos por stock.

El objetivo de la IA y la automatización es reducir trabajo repetitivo sin quitar visibilidad al operador. No debe inventar descuentos ni datos de clientes, ni ejecutar reembolsos o anular comprobantes sin autorización explícita.

## 11. Compatibilidad fiscal en Ecuador

El SRI publica la documentación y los flujos oficiales de comprobantes electrónicos, incluidos entornos de pruebas/certificación y producción. Antes de implantar el total tributario, el modelo de documentos y los estados de anulación/notas de crédito, debe revisarse la ficha técnica y guía vigente, no hardcodear una tarifa universal ni tratar la fila de venta como comprobante fiscal.

Fuentes oficiales:
- [SRI: Facturación Electrónica](https://www.sri.gob.ec/nl-BE/facturacion-electronica)
- [SRI: información técnica y ambientes de emisión](https://www.sri.gob.ec/web/intersri/facturacion-electronica?p_l_back_url=%2Fbuscador%3Fdelta%3D20%26q%3Dcalculador)
- [SRI: formularios e instructivos, incluidas guías IVA actualizadas](https://www.sri.gob.ec/formularios-e-instructivos)

## 12. Fuentes de mercado y científicas

- [Shopify POS: devoluciones, reembolsos y reposición de stock](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/order-management/complete-refund-orders)
- [Shopify: reembolso total y parcial de órdenes](https://help.shopify.com/en/manual/fulfillment/managing-orders/refunding-orders)
- [Lightspeed: devolución enlazada a la venta original](https://retail-support.lightspeedhq.com/hc/en-us/articles/229130768-Refunding-and-exchanging)
- [Baymard Institute: investigación de usabilidad de checkout](https://baymard.com/research/checkout-usability)\n- [Rodrigues, Proença y Soares (2024): riesgo percibido y abandono de carrito móvil](https://doi.org/10.1080/0965254X.2022.2160480)\n- [Herrmann et al. (2011): efecto de defaults en un configurador de compra](https://doi.org/10.1016/j.jretconser.2011.06.005)
- [Hick (1952): tiempo de reacción y elección](https://journals.sagepub.com/doi/10.1080/17470215208416600)
- [Scheibehenne et al. (2010): meta-análisis de choice overload](https://doi.org/10.1086/651235)
- [Jachimowicz et al. (2019): meta-análisis de default effects](https://doi.org/10.1017/bpp.2018.43)
- [W3C: WCAG 2.2, accesibilidad y operabilidad](https://www.w3.org/TR/wcag/)

Estas fuentes orientan patrones de operación y usabilidad; no sustituyen pruebas de Inventary con usuarios reales ni la validación técnica/tributaria de los requisitos locales.
