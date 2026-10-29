
# Contrato entre Inventary y el backend de facturación electrónica SRI

**Versión:** 1.0  
**Fecha de investigación normativa/técnica:** 29 de octubre de 2026  
**Sistema que origina la solicitud:** Inventary (React + Spring Boot + PostgreSQL)  
**Sistema fiscal externo:** backend que recibe la información de la venta, construye el XML, firma, transmite al SRI y devuelve el resultado real del comprobante.  
**Estado:** contrato arquitectónico propuesto; requiere acordar el API real del backend fiscal y validar la normativa consolidada con el responsable tributario.

> **Regla central:** Inventary no genera el XML tributario, no firma, no asigna secuenciales ni simula autorización. Envía una solicitud fiscal con los datos correctos de la operación y del emisor. El backend fiscal valida, construye, firma, transmite y devuelve el resultado real. Inventary conserva el vínculo, refleja los estados recibidos y nunca afirma que una factura fue autorizada o anulada sin evidencia del resultado.

## 1. Alcance y responsabilidades

### 1.1 Inventary: operación comercial y de inventario

Inventary mantiene la venta, las líneas, el cliente seleccionado, el almacén que afectó a las existencias, el precio/cantidad comercial confirmados, los movimientos de stock, el coste, las devoluciones, los permisos y la auditoría. También:

- determina que los datos necesarios para solicitar facturación estén presentes;
- selecciona un contexto fiscal válido para el emisor o impide continuar si no existe;
- registra una intención fiscal idempotente con referencia inmutable a la venta;
- invoca inmediatamente al backend fiscal después de confirmar la operación;
- persiste la operación de integración y recibe/consulta su estado;
- presenta número de documento, estado fiscal, errores recuperables y vínculos de descarga que el servicio fiscal devuelva;
- registra y coordina solicitudes de anulación de factura o de emisión de nota de crédito según el tipo de caso, sin afirmar de antemano qué trámite legal corresponde;
- separa la devolución física de mercadería, el cambio fiscal del comprobante y el reembolso real de dinero.

### 1.2 Backend de facturación: autoridad fiscal

El backend externo:
- mantiene la configuración que le corresponde del emisor, certificado, establecimiento/punto de emisión y secuenciales;
- valida la vigencia y el estado de los datos fiscales que administra;
- valida el contexto fiscal recibido y rechaza un establecimiento o punto de emisión no habilitado/configurado;
- valida los datos del receptor, los códigos fiscales y las reglas de impuestos vigentes;
- calcula o valida el desglose tributario conforme al contrato que se acuerde;
- genera el XML según la ficha técnica vigente del SRI, firma, transmite y procesa las respuestas;
- conserva XML firmado, autorización/estado, RIDE y evidencia del intercambio;
- devuelve el número de comprobante realmente asignado, establecimiento/punto de emisión utilizados, clave de acceso, autorización, fecha, estado y referencias de consulta/descarga;
- resuelve si una petición corresponde a anulación en línea, nota de crédito u otro trámite permitido según motivo, tipo de documento, plazo y normativa consolidada;
- no afirma que una solicitud fue completada si solo la aceptó para procesamiento;
- emite callbacks/webhooks firmados o permite consultar la operación hasta un estado definitivo;
- mantiene idempotencia para evitar comprobantes duplicados cuando Inventary reintenta.

### 1.3 SRI: sistema tributario

El SRI es quien valida y autoriza/anula los documentos según sus servicios y normativa. Una respuesta HTTP 200 del backend fiscal significa únicamente lo que defina su contrato; **no basta para afirmar “Factura autorizada”**. Esa condición exige el estado fiscal explícito correspondiente y evidencia devuelta por el backend fiscal.

### 1.4 Operador y contabilidad

La persona autorizada resuelve excepciones, corrige datos maestros donde corresponda, aprueba acciones sensibles y coordina los casos que requieran análisis contable. Ninguna automatización debe convertir un caso fiscal ambiguo en una anulación automática.

## 2. Estado real del repositorio frente a la integración futura

### 2.1 Lo que ya existe

En el backend de Inventary:
- POST /api/v1/sales registra una venta interna con idempotencia.
- SaleService calcula actualmente tax = 0 y total = subtotal; el DTO actual no representa un desglose fiscal completo por tarifa/código.
- La venta se guarda como SaleStatus.COMPLETED y las líneas/movimientos se registran dentro de una transacción.
- Se emite un evento Outbox SALE_COMPLETED.
- La venta guarda cliente, nombre/teléfono capturados, almacén, canal, importes y líneas; no guarda todavía un snapshot fiscal suficiente de identificación del receptor, establecimiento/punto de emisión, clave de acceso, número fiscal ni estado de autorización.
- POST /api/v1/sales/{id}/request-cancellation crea una solicitud de cancelación interna; POST /api/v1/sales/{id}/approve-cancellation aprueba la cancelación operativa y repone stock mediante movimientos de compensación.
- POST /api/v1/sales-returns registra la devolución y su efecto sobre inventario; no se observa integración con un backend fiscal que solicite anulación o nota de crédito.
- Las cuotas de suscripción se transforman en ventas internas desde SubscriptionService y SubscriptionBillingJob; el contrato actual no tiene un vínculo con un comprobante SRI autorizado.
- La referencia actual de APIs declara expresamente que la integración fiscal SRI/impuestos completos no está implementada.

En el frontend:
- SaleForm.jsx permite seleccionar cliente o realizar venta rápida; el selector usa contactos existentes, pero no presenta una selección fiscal de emisor/establecimiento/punto de emisión.
- SalesTable.jsx y el detalle deben incorporar la vida fiscal del documento; el estado interno actual de la venta no basta para representar los estados SRI.
- Las propuestas de Ventas, Suscripciones, Pedidos WhatsApp, Almacenes y n8n reconocen que la facturación fiscal no está completa, pero deben usar este contrato como referencia común.

### 2.2 Consecuencia

No es correcto simplemente llamar al otro backend después de guardar la venta y pintar el número que llegue. El flujo actual envía importes con impuesto cero, no captura todos los datos de receptor/emisor y no conserva la referencia fiscal. Antes de declarar la integración terminada, hace falta definir los datos tributarios, obtener una cotización/validación fiscal cuando sea necesaria, mantener un estado independiente del de la venta y coordinar cancelaciones y devoluciones.

No se debe desarrollar un generador XML, firmador ni algoritmo de secuenciales en Inventary. Esas funciones pertenecen al backend fiscal.

## 3. Normativa y fuentes oficiales verificadas

La sección de facturación electrónica del SRI, consultada el 29 de octubre de 2026, identifica como ficha técnica vigente publicada el **Esquema Off-line versión 2.34, actualizado a julio de 2026**. La emisión electrónica implica generar el comprobante conforme a especificaciones técnicas, firmarlo/transmitirlo y consultar su resultado; Inventary debe versionar su contrato de integración para que una actualización de la ficha técnica no obligue a reescribir los procesos de ventas.

Fuentes:
- SRI, facturación electrónica y ficha técnica 2.34: https://www.sri.gob.ec/web/intersri/facturacion-electronica
- SRI, herramienta de comprobantes electrónicos y recursos de emisión: https://www.sri.gob.ec/herramienta-de-comprobantes-electronicos
- Resolución NAC-DGERCGC25-00000014: https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar?id=137046a6-787c-47fb-a2d7-176595d292dc&nombre=NAC-DGERCGC25-00000014.pdf
- Reforma NAC-DGERCGC25-00000017 publicada en el Registro Oficial, Tercer Suplemento No. 92, 30 de julio de 2025: https://esacc.corteconstitucional.gob.ec/storage/api/v1/10_DWL_FL/eyJjYXJwZXRhIjoicm8iLCJ1dWlkIjoiODlmZGY5NTMtMWYyNy00MTEyLTg3OTctOTU3MjUwMzA1NWIxLnBkZiJ9
- SRI, noticia sobre las normas de anulación de comprobantes: https://www.sri.gob.ec/detalle-noticias?idnoticia=1160&marquesina=1
- SRI, guía para actualización del RUC y establecimientos abiertos/cerrados: https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/960ce2a0-c184-4b6a-a66c-1aa41b43377c/Gu%C3%ADa%20para%20el%20contribuyente-Actualizaci%C3%B3n%20de%20RUC%20en%20l%C3%ADnea.pdf
- SRI, facturación física y uso de notas de crédito para devoluciones: https://www.sri.gob.ec/web/intersri/facturacion-fisica

### 3.1 Anulación: regla vigente que debe implementarse

La Resolución NAC-DGERCGC25-00000014 regula la anulación de comprobantes electrónicos emitidos con errores o cuando la operación que motivó el comprobante no ocurrió. La Reforma NAC-DGERCGC25-00000017 sustituyó la referencia de **“día 10” por “día 7 del mes siguiente”** en el artículo 3; ese último inciso entró en vigor el 1 de enero de 2026. Si la fecha coincide con descanso obligatorio o feriado nacional/local, el texto de la resolución contempla la extensión al siguiente día hábil, con las salvedades previstas en la norma. La implementación debe aplicar el texto consolidado y comprobar el calendario oficial pertinente, no una fórmula fija de días naturales.

La misma reforma cambió el artículo 5 de la resolución original: la redacción inicial incluía de forma expresa un límite de doce meses para emitir notas de crédito; la redacción reformada lo sustituyó por la regla de que las notas de crédito se emitan únicamente en los casos del Reglamento de Comprobantes de Venta y que el SRI puede verificar su uso adecuado. Por tanto:
- no codificar el día 10 como plazo vigente;
- no reutilizar de forma ciega el límite fijo de doce meses de la versión inicial;
- no interpretar la reforma como permiso general para emitir una nota de crédito por cualquier causa;
- el backend fiscal debe mantener el texto consolidado vigente y revisar sus disposiciones complementarias antes de aplicar un plazo automáticamente.

La resolución también establece que una factura con la leyenda “consumidor final” no se puede anular ni modificar mediante nota de crédito una vez emitida y transmitida al SRI, sin perjuicio de la excepción específica incorporada para anulación de oficio durante un proceso de control tributario. Inventary no debe enviar una petición de anulación comercial común para forzar un caso así; debe bloquear la acción automática y escalar al responsable tributario.

Los comprobantes de retención, notas de crédito y notas de débito que requieran anulación pueden necesitar aceptación del receptor. La reforma contempla excepciones concretas para determinados receptores con identificación/pasaporte del exterior o registrados como fallecidos; no programar estas excepciones en Inventary a partir de supuestos. El backend fiscal debe resolverlas conforme al texto vigente.

### 3.2 Anulación, devolución comercial y reembolso son cosas distintas

No diseñar un botón único “Anular todo” para estas cuatro operaciones:

1. **Cancelación operativa de la venta:** corregir una venta cuya transacción no ocurrió o que se registró por error y aplicar al inventario la compensación autorizada.
2. **Anulación fiscal del comprobante:** solicitud legal al backend fiscal para tramitar la anulación permitida del documento; requiere confirmar el resultado.
3. **Devolución de mercadería:** registra productos devueltos, cantidades, motivo y disposición (RESTOCK/SCRAP u otra política). El sistema actual genera movimientos de stock/valoración.
4. **Nota de crédito:** documento fiscal complementario que puede corresponder para aceptar devoluciones, anular una operación válida o conceder descuentos/bonificaciones según el Reglamento.

Una venta correcta que sí ocurrió y luego tiene devolución física normalmente no debe tratarse como si la operación nunca hubiera existido. El flujo debe solicitar al backend fiscal el tratamiento aplicable —por ejemplo, nota de crédito vinculada a la factura original— y no intentar siempre anular la factura original. Es el backend fiscal quien debe resolver el procedimiento permitido; Inventary envía la intención y los hechos de la devolución de forma explícita.

Una nota de crédito tampoco prueba un reembolso de dinero. El registro de un reembolso exige evidencia financiera correspondiente, separada de los movimientos de inventario y de la emisión de un documento fiscal.

### 3.3 Nueva obligación sobre proveedores de sistemas de facturación: Resolución NAC-DGERCGC26-00000027

El SRI publicó la Resolución NAC-DGERCGC26-00000027 en el **Quinto Suplemento del Registro Oficial No. 335, el 28 de julio de 2026**. Regula el registro de ciertos proveedores de sistemas o servicios de facturación electrónica. Según su artículo 2, el ámbito incluye, en los términos definidos en la norma, a desarrolladores/dueños del código fuente de sistemas para emitir, firmar, transmitir y conservar comprobantes con fines de comercialización, y a propietarios de licencias/código fuente de sistemas desarrollados por terceros para comercialización. El artículo 3 establece el registro de un establecimiento exclusivo y las actividades económicas correspondientes. La aplicabilidad al proveedor concreto debe comprobarse contra el texto oficial y su modelo comercial; no se debe asumir que todo microservicio interno es automáticamente un proveedor sujeto a ese registro.

El artículo 5 requiere que el emisor de comprobantes electrónicos incluya, en la **sección de información adicional del comprobante**, el RUC del proveedor del sistema informático o servicio de facturación electrónica, conforme a la ficha técnica. La disposición transitoria tercera concede **60 días calendario** desde la publicación para implementar ese requisito. A la fecha de esta revisión (29 de octubre de 2026), el plazo indicado ya habría transcurrido; el equipo fiscal debe comprobar el cumplimiento efectivo antes de emitir en producción.

Esto introduce un dato distinto de los anteriores:
- **RUC del emisor:** contribuyente que vende y emite la factura;
- **RUC del proveedor del sistema de facturación:** proveedor tecnológico del sistema que cae dentro del ámbito de la Resolución 26-00000027;
- **RUC del comprador/receptor:** persona/entidad a la que se emite el comprobante.

No confundir ni reemplazar uno con otro. El backend fiscal debe conocer, mantener y aplicar el RUC del proveedor cuando corresponda y colocarlo en el campo/sección que defina la ficha técnica vigente; no debe pedir a Inventary que concatene manualmente texto tributario libre. El contrato entre backends debe incluir un `billingProviderRuc` configurado por servidor o un identificador de configuración de proveedor, de manera que no pueda alterarse desde una venta individual. El servicio fiscal es responsable de verificarlo y producir el XML/representación conforme a la versión vigente de la ficha técnica.

Fuentes:
- SRI, aviso oficial de 28 de julio de 2026: https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1
- Quinto Suplemento No. 335, Registro Oficial, 28 de julio de 2026: https://www.registroficial.gob.ec/quinto-suplemento-no-335/
- Texto de NAC-DGERCGC26-00000027 (reproducción de la resolución): https://uqa.com.ec/wp-content/uploads/2026/07/Newsletter-No.-030-SRI-registro-de-proveedores-de-sistemas-informaticos-o-servicios-de-facturacion-electronica-28-JULIO-2026-SRI-NAC-27.pdf
- SRI, ficha técnica vigente consultada: https://www.sri.gob.ec/web/intersri/facturacion-electronica

## 4. Datos del emisor: RUC, establecimiento y punto de emisión

### 4.1 Identidad tributaria del emisor

El RUC del emisor, razón social, estado, régimen, obligación contable y atributos fiscales aplicables deben proceder del perfil tributario validado. El backend fiscal conserva la configuración/certificado y valida que el emisor pueda operar. Un RUC con estado SUSPENDIDO no puede tratarse en la UI como un emisor fiscal activo por tener un almacén operativo activo.

La ficha de Almacenes y Contactos puede presentar datos del RUC y establecimientos obtenidos de una fuente validada; no debe editar manualmente los valores oficiales. El perfil del cliente/receptor es diferente del perfil del contribuyente emisor.

### 4.2 Establecimiento oficial versus almacén de inventario

No son el mismo concepto:
- warehouseId: determina dónde se consultó/consumió el stock.
- billingContextId: selecciona el contexto fiscal de emisión configurado.
- establishmentCode: código oficial del establecimiento tributario, preservado como texto, normalmente con tres dígitos conforme al esquema.
- emissionPointCode: código oficial del punto de emisión, preservado como texto, normalmente con tres dígitos conforme al esquema.
- documentSequential: lo genera y administra únicamente el backend fiscal.

Un almacén de inventario puede no ser un establecimiento emisor. Un establecimiento SRI puede servir a varios almacenes internos. La relación entre ambos debe ser explícita y revisable, nunca se supone que el código interno de Warehouse equivale a 001.

El usuario puede elegir un contexto de facturación solo entre los habilitados y devueltos por una API autorizada. Si existe un almacén con una asociación fiscal preferida, puede preseleccionarse, pero la UI debe mostrar esa selección y el backend fiscal vuelve a validarla antes de emitir. No ocultar la selección de establecimiento detrás del almacén.

### 4.3 Punto de emisión, numeración y clave de acceso

La serie y secuencial deben provenir de la configuración del backend fiscal, que evita duplicados entre procesos concurrentes y comprobantes emitidos desde otros aplicativos. Inventary jamás:
- genera localmente un secuencial fiscal;
- completa un número inventado si el backend fiscal falla;
- modifica el número del documento después de recibirlo;
- deduce la clave de acceso mediante una fórmula paralela;
- muestra un RIDE/XML como autorizado antes de que el servicio fiscal reporte ese estado.

La respuesta se conserva exactamente como la devolvió el backend fiscal, con los códigos oficiales como cadenas para no perder ceros iniciales.

## 5. Datos del receptor y snapshot fiscal

El documento se debe emitir con los datos que corresponden a la operación en el momento de la venta. No basta una relación mutable con BusinessPartner.

Se requiere un snapshot asociado a la venta con:
- tipo de identificación según el catálogo soportado por el facturador y normativa vigente;
- número de identificación o modalidad de consumidor final correspondiente;
- nombre/razón social exactamente como se envió para el comprobante;
- dirección y email cuando se requieran para el documento/canal;
- fuente del dato (contacto seleccionado o datos confirmados en venta rápida);
- fecha de captura y actor/canal;
- versión/huella de los datos de la venta.

Cambiar después nombre, dirección o identificación del cliente no debe reescribir la factura ni el snapshot histórico. Para clientes con RUC, la información verificada se conserva como perfil/instantánea con fuente y fecha. No exigir RUC universalmente a todos los clientes: el tipo de identificación y los requisitos del documento determinan los campos necesarios.

El usuario debe revisar los datos de facturación antes de confirmar la emisión. Si el receptor requiere corrección después de que una factura ya se autorizó, se utiliza el procedimiento fiscal permitido; no se modifica retrospectivamente el documento interno para fingir que la factura original cambió.

## 6. Datos de la venta que debe recibir el backend fiscal

La solicitud no debe consistir solo en saleId y total. Tiene que transportar hechos necesarios y verificables de la transacción:
- ID y número interno de venta, canal, instante, almacén origen de stock y actor/origen;
- contexto del emisor seleccionado (RUC, establecimiento, punto de emisión) o ID que el backend fiscal pueda resolver sin ambigüedad;
- snapshot del receptor;
- cada línea: ID interno, SKU/código, descripción, cantidad, unidad, precio unitario, descuento, subtotal de línea y clasificación/códigos fiscales de producto cuando existan;
- moneda, totales comerciales y referencia a una prevalidación/cotización fiscal cuando aplique;
- desglose de impuestos y valores calculados por tarifa/código, o datos para que el backend fiscal calcule con autoridad;
- forma/condición de pago declarada requerida para el comprobante, sin confundirla con un pago recibido y verificado;
- referencias a promoción/descuento autorizado y notas adicionales permitidas por el contrato;
- sourceSystem, sourceOperationId, versión del esquema y clave de idempotencia.

**Brecha crítica:** el SaleService actual persiste tax = 0 y total = subtotal; el DTO no contiene un desglose fiscal. Antes de poner en producción la emisión, debe decidirse dónde se calcula la cotización fiscal real. La recomendación es que el backend de facturación exponga una prevalidación/cotización autoritativa que devuelva desglose e importe fiscal, y que Inventary guarde esa versión antes de confirmar la venta. Si se acuerda que Inventary calcula impuestos, esa responsabilidad requiere un dominio tributario versionado y mantenido según catálogo vigente; no debe surgir de campos hardcodeados en React.

No se envían tokens del SRI, certificado de firma, contraseña del contribuyente ni secretos del facturador desde el navegador. El contrato entre backends autentica el servicio de Inventary con credenciales de servicio de alcance limitado.

## 7. Contrato API recomendado entre backends

Las siguientes rutas son una propuesta para acordar con el otro backend; no existen aún en la API actual de Inventary.

### 7.1 Prevalidar antes de confirmar

**POST /api/v1/fiscal/previews**

Entrada conceptual:
- saleDraftId o identificador de intento;
- billingContextId (no un código fiscal libre y no confiable);
- receptor y líneas/snapshots;
- datos comerciales relevantes y versión de esquema.

Respuesta:
- fiscalPreviewId;
- status: VALIDATED, INVALID, NEEDS_REVIEW;
- contexto emisor resuelto, establecimiento y punto;
- snapshot/hash de input y versión de reglas;
- base imponible/impuestos/total y moneda;
- errores/advertencias tipados y campos afectados;
- vigencia de la previsualización y requisitos pendientes.

La previsualización no es una factura, no reserva un secuencial y no implica autorización SRI. El backend fiscal revalida al emitir. Si la cotización expira o cambian las líneas, precios, impuestos, receptor o contexto, la venta debe pedir una nueva prevalidación.

### 7.2 Solicitar emisión al confirmar venta

**POST /api/v1/fiscal/documents** (nombre ilustrativo).

Entrada conceptual:
- schemaVersion;
- sourceSystem = INVENTARY;
- sourceOperationId = saleId;
- sourceOperationNumber = saleNumber;
- sourceChannel (WEB, POS, WHATSAPP, SUBSCRIPTION, etc.);
- idempotencyKey único por venta/documento;
- fiscalPreviewId y hash del snapshot;
- referencia exacta al contexto emisor, establecimiento y punto de emisión;
- snapshot del receptor, líneas y datos acordados;
- forma/condición de pago declarada;
- fecha/hora del hecho de negocio.

El endpoint debe permitir reintentar el mismo comando sin generar otra factura y detectar cuando se reutiliza una clave con payload distinto. Una venta solo puede tener un documento de tipo factura principal activa, salvo un caso excepcional definido explícitamente por el dominio fiscal.

Respuesta inmediata posible:
- operationId;
- fiscalDocumentId si ya fue creado;
- status actual;
- documentType;
- documentNumber solo cuando el backend lo haya asignado;
- establishmentCode, emissionPointCode y sequential cuando existan;
- accessKey, authorizationNumber, authorizedAt;
- rideUrl/xmlUrl si están disponibles y protegidas;
- mensajes/códigos de error y retryable;
- receivedAt y correlationId.

La respuesta puede indicar PROCESSING/PENDING. Inventary no transforma eso en AUTHORIZED. El documento definitivo se actualiza por callback firmado o por consulta de estado.

### 7.3 Consultar estado/resultado

**GET /api/v1/fiscal/operations/{operationId}** o un endpoint que consulte por sourceSystem + sourceOperationId + documentType.

Se debe poder recuperar el estado después de timeout, reinicio de servicio, cierre de sesión o reintento del navegador. Antes de reemitir una solicitud cuyo resultado es ambiguo, consultar por idempotency/source ID para asegurar que el backend fiscal no la procesó ya.

### 7.4 Solicitud de cancelación/anulación fiscal

**POST /api/v1/fiscal/documents/{fiscalDocumentId}/cancellation-requests** (propuesta).

Datos requeridos:
- documento original identificado por la referencia fiscal real;
- motivo específico de error/no realización de la operación;
- actor que solicitó y actor que aprobó, si la política de Inventary requiere separación;
- evidencia y timestamp;
- referencia al ID de solicitud interna de cancelación;
- Idempotency-Key de la operación fiscal, distinta de la clave de creación de venta.

La respuesta debe distinguir como mínimo:
- solicitud recibida;
- anulación procesándose;
- pendiente de aceptación del receptor cuando aplique;
- comprobante efectivamente anulado;
- no elegible;
- rechazada por regla fiscal;
- requiere revisión manual;
- error temporal;
- estado desconocido por timeout.

Una solicitud recibida no equivale a un comprobante anulado. El backend fiscal valida elegibilidad/plazo/tipo de documento, receptor y estado anterior conforme a normativa vigente.

### 7.5 Solicitud fiscal por devolución comercial

**POST /api/v1/fiscal/documents/{originalFiscalDocumentId}/credit-notes** (propuesta) para casos en que el tratamiento correcto sea una nota de crédito; alternativamente el otro backend puede ofrecer una operación común fiscal-adjustments que seleccione el procedimiento permitido.

Debe incluir:
- factura original autorizada;
- devolución interna y sus líneas;
- cantidades/importes de cada línea devuelta;
- motivo y disposición de inventario;
- desglose/importe fiscal correspondiente al ajuste;
- referencia al cliente/receptor, establecimiento y punto que el backend fiscal exige para emitir ese documento;
- claves de idempotencia, actor, auditoría y correlación.

La nota de crédito tiene su propia identidad fiscal, estado, secuencial y clave de acceso; se vincula a la factura afectada, pero no reemplaza ni borra la factura original. Si el backend fiscal considera que el caso requiere otro trámite o no es elegible, devuelve esa decisión con un código estable y no emite un documento por defecto.

### 7.6 Callback/webhook del backend fiscal

El backend fiscal debe notificar cambios de estado o permitir consultarlos con polling. Si se utiliza webhook:
- autenticación de servicio, firma del payload o mecanismo equivalente;
- fecha de emisión, identificador único de evento, versión de esquema y correlación;
- verificación de firma, timestamp y protección anti-replay;
- deduplicación de eventos;
- persistencia del evento antes de confirmarlo;
- tolerancia a eventos duplicados/desordenados;
- confirmación solo después de almacenar el resultado;
- endpoint de redrive/reconciliación operativo.

Payload mínimo:
- eventId, eventType, schemaVersion, occurredAt, operationId, sourceSystem, sourceOperationId;
- fiscalDocumentId, tipo/estado fiscal;
- número de comprobante, códigos de establecimiento/punto/secuencial, clave de acceso, número/fecha de autorización si existen;
- errorCode/mensaje de usuario seguro y retryable;
- URL de descarga protegida si está disponible.

No enviar en los webhooks secretos, certificado ni información personal que no sea necesaria.

## 8. Estados: venta comercial y documento fiscal independientes

No ampliar el enum actual de SaleStatus para mezclar cada estado fiscal con el estado comercial. Añadir un estado/registro independiente para documento fiscal y operación fiscal.

Estados conceptuales del documento principal:
- NOT_REQUESTED;
- PREVIEW_PENDING;
- PREVIEW_INVALID;
- REQUEST_PENDING;
- PROCESSING;
- AUTHORIZED;
- REJECTED;
- RETRYABLE_ERROR;
- CANCELLATION_REQUESTED;
- CANCELLATION_PENDING_ACCEPTANCE, cuando aplique;
- CANCELLED_BY_TAX_AUTHORITY;
- CREDIT_NOTE_REQUESTED / CREDIT_NOTE_PENDING / CREDIT_NOTE_AUTHORIZED;
- NOT_ELIGIBLE;
- MANUAL_REVIEW;
- UNKNOWN_REQUIRES_RECONCILIATION.

El nombre final debe reflejar los códigos del backend fiscal. Inventary guarda por separado:
- estado de Sale y sus cancelaciones;
- estado de SalesReturn;
- estado fiscal de factura;
- estado de anulación;
- estado fiscal de nota de crédito;
- estado de pago/reembolso, solo si un sistema de pagos lo soporta.

No permitir que SaleStatus.CANCELLED se interprete como “Factura SRI ANULADA”. Tampoco que SalesReturn.status = POSTED implique que la nota de crédito fue autorizada o que el dinero se reembolsó. El listado de ventas debe mostrar ambos estados con etiquetas distintas.

## 9. Secuencia normal de venta

1. El usuario selecciona artículos, cantidades, cliente/tipo de identificación, datos necesarios de facturación y contexto de emisión válido.
2. Inventary consulta una prevalidación fiscal cuando el contrato así lo defina. El servicio valida receptor, emisor, establecimiento/punto y cálculo fiscal.
3. React muestra el desglose final de forma comprensible y requiere aceptación antes de registrar la venta.
4. Inventary crea la venta interna, captura snapshots de la operación y aplica stock/coste/auditoría en una transacción local. Dentro de esa misma transacción escribe el Outbox para emitir el documento fiscal.
5. La petición al backend fiscal se despacha inmediatamente después del commit. No se espera a la tarea nocturna ni se deja la emisión para una futura carga manual; el relay proporciona recuperación si hay un fallo.
6. El backend fiscal devuelve/actualiza el estado, número del documento/serie y datos de autorización. Inventary persiste la respuesta sin modificar los números que recibió.
7. Solo si el estado real es AUTHORIZED la UI muestra “Factura autorizada” y enlaces de comprobante/clave de acceso disponibles.
8. Si la petición queda pendiente o falla, la UI muestra “Venta registrada; factura en proceso” o “La emisión requiere atención” y genera una tarea prioritaria. No crea una segunda venta ni factura en cada reintento.
9. Al reintentar un timeout, Inventary consulta la operación por Idempotency-Key/sourceSaleId y reutiliza la misma identidad; nunca crea un nuevo documento sin saber qué pasó con el primero.

El Outbox no es permiso para postergar la obligación fiscal. Es un mecanismo de durabilidad y recuperación, no una autorización para acumular ventas sin atender. El despliegue debe procurar el primer envío inmediatamente y alertar cualquier demora. Si la indisponibilidad del backend fiscal/SRI impide cumplir el momento de transmisión, se necesita un procedimiento de contingencia autorizado por el responsable tributario; el sistema no puede inventar una autorización ni decidir que una venta quedó exenta.

## 10. Cancelación: de la solicitud interna al resultado fiscal

El flujo actual pide motivo, requiere aprobación separada y después aprueba la cancelación operativa/reposición de stock. Debe evolucionar antes de integrar facturación, porque hoy puede restaurar inventario y poner la venta en CANCELLED sin una referencia fiscal.

Flujo recomendado para una venta ya asociada a factura:
1. El usuario solicita cancelación e indica causa. La UI muestra si la venta tiene factura pendiente, autorizada o ya afectada por nota de crédito.
2. Un aprobador autorizado revisa la causa. Quien solicitó no aprueba si la política actual de segregación lo prohíbe.
3. Inventary crea una operación de cancelación con estado propio, idempotencia y evidencia, sin marcar todavía la factura como anulada.
4. El backend fiscal valida elegibilidad/plazo/tipo de documento y procesa la solicitud.
5. Si la respuesta confirma la anulación fiscal, Inventary ejecuta una compensación interna de stock exactamente una vez y finaliza el estado comercial conforme a la política acordada. Si el proceso está pendiente de aceptación, queda pendiente; no se dice “anulada”.
6. Si el servicio fiscal declara que el caso debe tratarse como nota de crédito, el flujo de cancelación se detiene y se deriva al proceso correcto, sin emitir un documento no solicitado.
7. Si se rechaza o no es elegible, se conserva el historial y se muestra el motivo; cualquier corrección requiere el flujo legal apropiado.

El orden exacto entre aceptación fiscal y compensación de stock debe definirse como una saga con estados durables. No se debe mantener una transacción PostgreSQL abierta mientras se espera una llamada HTTP externa, y tampoco ejecutar reversión física antes de definir la política ante un rechazo fiscal. Cuando el estado externo es ambiguo, primero se reconcilia; no se repite el movimiento ni la solicitud fiscal a ciegas.

## 11. Devolución física, nota de crédito y reembolso

La ruta actual POST /api/v1/sales-returns registra líneas devueltas, su disposición y los movimientos de inventario. Para productos retornables:
- valida que la venta esté completada;
- no permite devolver más unidades que las vendidas/restantes;
- registra RESTOCK o SCRAP;
- crea movimientos y coste relacionados con la venta.

Se requiere agregar una operación fiscal relacionada con la devolución:
1. La devolución interna conserva una referencia a la factura fiscal original si la venta tiene una.
2. Se crea la devolución y su efecto de stock conforme a la transacción actual, con Outbox para solicitar el documento fiscal complementario correspondiente.
3. El worker envía al backend fiscal la factura original autorizada, líneas/cantidades/importes y motivo. En el caso aplicable solicita una nota de crédito; no presume una anulación de la factura principal.
4. Inventary guarda el estado de la nota de crédito separadamente del estado de la devolución y del inventario.
5. Si el backend fiscal devuelve NEEDS_REVIEW/NOT_ELIGIBLE, la devolución física se conserva y se activa una incidencia fiscal; no se crea otro documento automáticamente.
6. El reembolso en dinero, si existe, debe tener una entidad/operación financiera propia y confirmación real. Emitir nota de crédito no equivale a devolver dinero.

No volver a reducir stock por el hecho de solicitar una nota de crédito. La devolución física ya tiene su propio movimiento. Una repetición de webhook/reintento no puede crear otra devolución, otra salida/entrada ni una segunda nota de crédito.

Si el comprobante original ya fue cancelado, la devolución está duplicada o las líneas/cantidades ya fueron afectadas por una nota de crédito, el backend fiscal debe rechazar o calcular el procedimiento según sus reglas; Inventary debe resolver la discrepancia antes de ejecutar nuevamente.

## 12. Establecimientos abiertos/cerrados y validación fiscal

La propuesta de Almacenes y Contactos define la información del contribuyente y establecimientos. Su integración con facturación debe cumplir:
- el establecimiento oficial se mantiene como dato fiscal; el almacén físico conserva su código interno;
- establecimiento abierto/cerrado y estado del contribuyente se muestran con valor original, estado normalizado y fecha/fuente de verificación;
- no se permite seleccionar un establecimiento cerrado o un RUC suspendido como emisor elegible sin que el backend fiscal confirme la habilitación conforme a su configuración y a la regla vigente;
- la validación final se hace en backend de facturación, no en React ni con el último snapshot local;
- si un establecimiento cambia a cerrado o el RUC se suspende, se bloquean nuevas emisiones afectadas según las reglas fiscales configuradas y se notifica a los administradores; no se elimina el almacén ni su inventario;
- un nuevo contexto fiscal no cambia la numeración histórica;
- el almacén origen de la venta sigue siendo el almacén que afecta al stock; el establecimiento emisor es un campo fiscal distinto, aunque una configuración los asocie como predeterminados.

El JSON de estado tributario puede venir de una consulta del SRI o de un proveedor. Antes de automatizar hay que verificar el origen/contrato real. La consulta pública de datos no debe confundirse con los servicios autenticados de actualización del RUC.

## 13. Suscripciones: la factura periódica también pasa por el backend fiscal

Hoy SubscriptionBillingJob genera cuotas convertidas en ventas internas de canal SUBSCRIPTION. Esa venta debe seguir una sola ruta de fiscalización:
- las cuotas guardan subscriptionId/installmentId y saleId;
- cada venta de suscripción solicita una factura fiscal idempotente, con la referencia de cuota o grupo consolidado;
- la tarea programada puede generar ventas internas pero no debe emitir varias facturas por reintentos;
- una facturación anticipada de varias cuotas envía exactamente la venta consolidada creada y las referencias de las cuotas cubiertas, sin crear una factura adicional por cada cuota;
- cancelar una suscripción no anula automáticamente facturas históricas: las cuotas futuras no facturadas se cancelan; un documento ya emitido utiliza el flujo fiscal correcto según el caso;
- si falla la fiscalización, la cuota conserva la relación con la venta y muestra error/pendiente, sin marcarla como pagada;
- si una venta de suscripción requiere anulación o nota de crédito, el estado del documento fiscal, la cuota y la suscripción se coordinan mediante un flujo explícito y auditable.

n8n no debe duplicar SubscriptionBillingJob ni llamar de forma independiente a la API fiscal para la misma cuota.

## 14. Pedidos WhatsApp y canales futuros

WhatsApp/WebView/POS deben crear la misma venta canónica y usar el mismo contrato fiscal; no implementar lógica fiscal diferente por canal.
- conservar consentimientos/datos del receptor confirmados;
- vincular el pedido al ID de venta y al documento fiscal;
- los estados de pedido, pago, inventario y factura son dimensiones distintas;
- el bot puede decir “solicité la factura” si el backend registró la solicitud, pero no “factura autorizada” sin ese estado;
- el WebView nunca recibe credenciales internas del backend fiscal;
- n8n puede notificar estados recibidos, pero no genera XML ni crea un segundo documento;
- los eventos tienen eventId, schemaVersion, correlationId y referencias suficientes para deduplicar;
- mensajes por WhatsApp informan el estado real del documento y usan URLs protegidas con controles de acceso.

## 15. Modelo de datos local recomendado

No guardar toda la fiscalización en columnas de la tabla sale. Propuesta de entidad separada, por ejemplo FiscalDocumentLink:
- id;
- saleId;
- documentKind (INVOICE, CREDIT_NOTE, DEBIT_NOTE u otro tipo soportado);
- externalFiscalDocumentId y externalOperationId;
- sourceOperationId (saleId o salesReturnId);
- sourceReturnId opcional;
- parentFiscalDocumentId para la relación con factura original;
- issuerTaxpayerId/billingContextId referenciado;
- establishmentCode, emissionPointCode, sequential recibidos;
- documentNumber, accessKey, authorizationNumber, authorizedAt;
- fiscalStatus, lastErrorCode, mensaje seguro de usuario, retryable;
- requestPayloadHash y responsePayloadHash;
- requestedAt, updatedAt, lastSyncedAt;
- correlationId y schemaVersion;
- URLs o referencia de almacenamiento para XML/RIDE, sin URLs públicas permanentes si contienen acceso sensible.

Entidad opcional FiscalOperation:
- solicitud de emisión, cancelación o nota de crédito;
- clave de idempotencia y hash de input;
- actor/permiso/motivo;
- estado pendiente/procesando/completado/error/manual review;
- número de intentos, nextRetryAt y último error;
- correlación y timestamps.

Los snapshots fiscales de receptor/emisor y líneas deben ser inmutables después de presentar la emisión. No guardar tokens del SRI, certificados, contraseñas ni secretos en el frontend. Limitar el acceso a XML/RIDE según rol y mantener logs sin datos personales innecesarios.

## 16. Idempotencia, Outbox y recuperación ante errores

La integración debe tolerar un timeout después de que el backend fiscal haya recibido la petición:
- clave estable de emisión derivada de saleId + documentKind + versión;
- clave separada para anulación y cada nota de crédito por devolución;
- el mismo key y payload devuelve la operación/documento original;
- el mismo key con payload distinto produce conflicto;
- consulta por sourceSaleId/operationId antes de repetir;
- Outbox creado en la transacción local que confirma la operación que necesita fiscalización;
- primer intento de envío inmediato tras commit; reintentos acotados y trazables si hay fallos transitorios;
- backoff y bandeja operativa de pendientes; no reintentos infinitos que generen spam o documentos duplicados;
- reconciliación periódica entre ventas y documentos del backend fiscal;
- redrive administrativo que no cambie la identidad/idempotency key;
- callbacks duplicados o fuera de orden deduplicados y comparados con el estado de la operación.

El Outbox no sustituye el requisito de emitir/transmitir en el momento que corresponda. Es el mecanismo de durabilidad y recuperación, no una autorización para acumular ventas sin atender. El despliegue debe monitorizar cualquier demora fiscal, definir un procedimiento de contingencia autorizado y bloquear toda señal falsa de documento autorizado.

## 17. UX y estados que deben verse claramente

En la fila y detalle de la venta, mostrar dos etiquetas separadas:

**Estado comercial:** completada, cancelación solicitada, cancelada, etc.  
**Estado fiscal:** sin solicitar, emisión solicitada, en proceso, autorizada, rechazada, anulación pendiente, anulada, nota de crédito pendiente/autorizada o requiere revisión.

En el detalle fiscal:
- emisor y RUC;
- código de establecimiento y punto de emisión;
- serie/número recibido del otro backend;
- tipo de documento;
- estado real informado por el servicio;
- clave de acceso, autorización y fecha;
- enlaces protegidos a XML/RIDE cuando existan;
- error legible y acción permitida;
- vínculo a factura original y notas de crédito relacionadas.

Textos recomendados:
- “Venta registrada; factura en proceso de emisión.”
- “Factura autorizada por el SRI” solo ante status AUTHORIZED confirmado.
- “El backend fiscal recibió la solicitud; la autorización todavía no está confirmada.”
- “Anulación fiscal solicitada; pendiente de respuesta.”
- “Devolución de mercadería registrada; nota de crédito pendiente.”
- “Documento fiscal requiere revisión; no se ha confirmado la anulación.”
- “Reembolso no registrado” si solo existe una nota de crédito y no hay operación de pago confirmada.

Acciones diferenciadas:
- reintentar consulta de estado;
- reprocesar solicitud fallida si retryable;
- solicitar anulación fiscal con motivo;
- solicitar nota de crédito relacionada con devolución;
- abrir documento original;
- copiar clave de acceso/número;
- revisar incidencia o escalar a administración.

No mostrar un botón de “Anular factura” en una devolución genérica cuando lo correcto puede ser una nota de crédito u otro tratamiento legal.

## 18. Criterios de aceptación y pruebas

### 18.1 Venta normal
- Una venta crea exactamente una operación de emisión fiscal.
- El request usa el contexto fiscal seleccionado y conserva establecimiento/punto con ceros iniciales.
- El backend devuelve el número; Inventary no lo genera.
- Un documento solo se etiqueta autorizado después de recibir el estado de autorización.
- Un timeout tras emisión no crea otro comprobante.
- Los importes/desglose de Inventary y backend fiscal concuerdan con el preview/snapshot versionado.
- Un RUC o establecimiento no elegible bloquea la emisión y muestra error con acción correctiva.

### 18.2 Anulación
- Venta sin documento fiscal: se aplica política interna; no llamar a anulación de una factura inexistente.
- Factura ya autorizada: solicitud fiscal con motivo, idempotencia y referencia exacta.
- Solicitud aceptada pero pendiente: no marcar la factura anulada ni finalizar silenciosamente la operación.
- Factura no elegible o pasada de plazo: no forzar la anulación; el backend fiscal responde el tratamiento disponible conforme a normativa vigente.
- Factura con consumidor final transmitida: bloquear anulación/nota de crédito automatizada conforme a regla vigente; la excepción de oficio del SRI no se implementa como acción comercial.
- Reintentos/callbacks duplicados no repiten la compensación de stock.
- No usar el día 10 como plazo vigente; las pruebas desde el 1 de enero de 2026 aplican la reforma que cambia el artículo 3 al día 7.
- No hardcodear la antigua regla de 12 meses de la redacción inicial sin validar el texto consolidado posterior a NAC-DGERCGC25-00000017.

### 18.3 Devolución
- Una devolución física crea una sola operación fiscal asociada a la factura original.
- Para una operación válida con retorno, el tratamiento se resuelve mediante el procedimiento legal aplicable, normalmente nota de crédito en los casos permitidos, no anulación automática de la factura original.
- La devolución de inventario y su coste se registra una sola vez.
- La nota de crédito tiene su número/clave/estado independiente y enlaza a la factura original y devolución.
- La nota de crédito no se muestra como dinero reembolsado.
- Una solicitud no elegible conserva la devolución física y activa incidencia fiscal sin crear otra nota automáticamente.

### 18.4 Otros canales
- WhatsApp, WebView, venta de mostrador y suscripciones usan la misma API de venta y el mismo contrato fiscal.
- La facturación de suscripciones es idempotente por cuota/operación consolidada.
- n8n solo notifica estados recibidos; no genera XML ni crea un segundo documento.
- Usuarios sin permiso fiscal no pueden solicitar anulación, ver datos sensibles o acceder al XML/RIDE.

### 18.5 Seguridad y pruebas de integración
- Autenticación servicio-a-servicio, allowlist de servicio si aplica y rotación de credenciales.
- Firma/verificación de callbacks y protección anti-replay.
- Timeout de conexión/lectura y circuito abierto; consulta por idempotencia al resolver resultado ambiguo.
- Escenarios externos: autorizado, en proceso, rechazo fiscal, 4xx de datos, 429, 5xx, timeout antes/después de aceptar y callback perdido.
- Registro de requestId/correlationId, hashes de payload y response, evitando XML completo en logs generales.
- Test de reconciliación entre Sale, SalesReturn y FiscalDocumentLink.
- No actualizar la versión/ficha técnica fiscal sin contrato de regresión para emisión y anulación.

## 19. Roadmap de implementación

### Fase 0 — Cerrar el contrato con el backend fiscal
1. Obtener OpenAPI/documentación real del servicio externo: autenticación, payload y enums.
2. Confirmar si ofrece prevalidación/cotización fiscal, emisión, consulta, anulación, nota de crédito, webhook y consulta por Idempotency-Key.
3. Formalizar tipos de identificación, establecimientos/puntos, medios de pago declarados, códigos fiscales de producto/impuesto y snapshots.
4. Acordar quién calcula impuestos y totales para eliminar el actual tax = 0; no implementar impuestos a ciegas.
5. Confirmar con responsable tributario la resolución consolidada 25-00000014/25-00000017 y reglas que correspondan al negocio.

### Fase 1 — Persistencia y primera emisión
1. Crear entidad/DTO de vínculo fiscal y estados separados de SaleStatus.
2. Validar/referenciar billingContextId, establecimiento y punto de emisión.
3. Agregar snapshot tributario del receptor y datos de línea.
4. Crear Outbox e invocación idempotente inmediata.
5. Guardar respuesta del backend, clave/número/estado y URLs del documento.
6. Añadir UI para estado fiscal y errores.

### Fase 2 — Cancelación y devoluciones
1. Separar cancelación operativa, anulación fiscal, devolución de stock y nota de crédito.
2. Diseñar la saga y transiciones que dependen del resultado fiscal.
3. Asegurar compensación de inventario solo una vez con la política de secuencia acordada.
4. Añadir enlaces a factura original/nota de crédito.
5. Implementar cola de revisión fiscal y alertas de plazo/estado.

### Fase 3 — Suscripciones, WhatsApp, n8n y resiliencia
1. Conectar ventas de suscripción con la ruta común sin duplicar facturas.
2. Unificar la ruta fiscal de web, POS, WhatsApp y WebView.
3. Emitir eventos de estado fiscal y notificar con n8n sin secretos.
4. Reconciliación y dashboard de documentos sin vínculo/errores.
5. Tests de fallo externo, callback, idempotencia y cambio de normativa.

## 20. Fuentes internas de Inventary y propuestas que se deben alinear

Código actual:
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SalesController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SaleService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/Sale.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SaleDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/returns/SalesReturnController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/returns/SalesReturnService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/returns/SalesReturnDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/subscriptions/SubscriptionService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/subscriptions/SubscriptionBillingJob.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/OutboxEvent.java
- microservices-inventary-ecuapp/docs/25-referencia-apis.md

Propuestas relacionadas que deben coordinarse:
- PROPUESTA_UX_VENTAS_V2.md
- PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md
- PROPUESTA_UX_SUSCRIPCIONES.md
- PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md
- PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md
- PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md
- PROPUESTA_INVENTARIO_AGENT_READY_V3.md
- PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md

**Límite:** este documento define el contrato recomendado, no una integración ya conectada. No se ha probado el backend fiscal externo porque su URL, OpenAPI, autenticación y entorno de pruebas no forman parte del código inspeccionado. El responsable tributario debe validar la interpretación de las normas consolidadas del SRI antes de llevar los controles legales a producción.
