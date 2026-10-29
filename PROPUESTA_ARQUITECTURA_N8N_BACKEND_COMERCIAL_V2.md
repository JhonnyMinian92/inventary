# Propuesta de arquitectura e implementación n8n + backend para el motor comercial de Inventary V2

**Fecha:** 28 de octubre de 2026  
**Repositorio:** JhonnyMinian92/inventary  
**Rama objetivo:** master  
**Estado:** propuesta técnica basada en las propuestas funcionales y el código inspeccionado. No significa que los workflows ni los endpoints futuros estén implementados.

## 1. Objetivo y decisión ejecutiva

Esta propuesta alinea cuatro áreas que no se deben desarrollar de forma aislada:

1. **Productos y catálogo:** fichas confiables que pueden consultar el cliente, Ventas y el agente conversacional.
2. **Ventas:** cotización, aceptación, registro de venta, inventario, cancelaciones y devoluciones.
3. **Suscripciones:** plan, contratación, calendario, facturación, cancelación y, más adelante, cobros y beneficios.
4. **Pedidos WhatsApp:** conversación, carrito, cotización, pedido, retiro/envío, pago, preparación y atención humana.

La decisión arquitectónica es clara:

- **Inventary (Spring Boot + PostgreSQL)** es la fuente de verdad de datos, reglas comerciales, permisos, transacciones, inventario, precios, cotizaciones, pedidos, contratos, ventas, facturación interna, pagos registrados, auditoría y eventos durables.
- **n8n** orquesta workflows, adapta WhatsApp a las APIs de Inventary, realiza tareas externas, coordina notificaciones, maneja reintentos de comunicación y deriva incidencias.
- **OpenAI o Gemini, opcionalmente**, interpreta lenguaje natural, extrae datos tentativos, sugiere preguntas y redacta respuestas. El modelo no es la fuente de verdad ni ejecuta transacciones directamente.
- **React** presenta las interfaces internas para Productos, Ventas, Suscripciones y Pedidos WhatsApp.

**No se recomienda implementar primero un agente autónomo.** Primero se deben formalizar los contratos y las reglas de backend; después los workflows deterministas; finalmente, la IA como capa opcional y controlada. La compra debe continuar funcionando aunque n8n o el proveedor de IA se detengan.

## 2. Estado verificado del repositorio

### 2.1 Qué existe actualmente

El README, Docker Compose y la referencia de APIs documentan lo siguiente:

- Docker Compose incluye un contenedor n8n con volumen persistente y clave de cifrado.
- La URL interna documentada para conectar n8n con la API es **http://api:8080/api/v1/integrations/whatsapp**. Dentro de n8n, localhost apunta al propio contenedor n8n, no al backend.
- La integración actual utiliza el header **X-Integration-Key**, guardado como credencial Header Auth de n8n.
- Las APIs de canal permiten catálogo público, carrito, cotización por almacén, pedido idempotente, consulta de un pedido conocido y cancelación en estados permitidos.
- El backend tiene catálogo de productos, categorías, atributos dinámicos, unidades, variantes e imágenes; las escrituras administrativas están protegidas por permisos de personal.
- Ventas dispone de creación transaccional, costeo/movimientos de inventario, cancelación con solicitud y aprobación, y devoluciones.
- Suscripciones dispone de opciones, vista previa, creación idempotente, detalle, previsualización de cancelación, cancelación, prepago y un trabajo programado del backend para facturar cuotas cuya fecha ha llegado.
- El backend genera registros Outbox para eventos de negocio y tiene un relay de Kafka opcional. Kafka está deshabilitado en la configuración principal de Compose; no se debe asumir que n8n recibe los eventos automáticamente.
- Las propuestas funcionales de Ventas, Productos, Suscripciones, Pedidos WhatsApp, Motor Comercial y Logística están documentadas en la rama principal.

En el repositorio inspeccionado **no encontré workflows n8n exportados y versionados listos para importar**. Que Docker Compose levante n8n no demuestra que los workflows de recepción, compra, notificaciones, suscripciones, errores y transferencia humana estén creados, importados, activos o probados.

### 2.2 Brechas que afectan el alcance

- La integración WhatsApp actual no tiene una operación de contratación de suscripción.
- Las rutas actuales de Suscripciones están protegidas para sesiones autenticadas/roles de personal. No son una API limitada para la credencial técnica de WhatsApp.
- El pedido WhatsApp tiene un ciclo corto de estados: pendiente de pago, confirmado, cancelado y expirado. No contiene la operación completa de preparación, retiro, despacho, tránsito o entrega.
- La cotización de WhatsApp usa un solo almacén. No contempla todavía una modalidad de cumplimiento, tarifa de envío y reglas de descuento del escenario objetivo.
- El contrato de carrito WhatsApp utiliza productId. Aunque existen APIs de variantes en el catálogo, no hay un flujo completo de variante independiente en carrito/cotización/pedido.
- La cotización actual no equivale a una cotización fiscal completa; el flujo inspeccionado registra impuesto en cero.
- La reserva de un pedido vence a los 20 minutos por defecto. No es una política universal adecuada para pedidos nocturnos, pago al retirar, contra entrega o preparación prolongada.
- La confirmación de pago del backoffice consume la reserva, crea la venta y registra la salida de inventario. No debe usarse como aceptación genérica de un pedido que todavía no ha sido cobrado.
- No hay pasarela de pago ni reembolsos automáticos integrados. Recibir una referencia o imagen de comprobante no demuestra que el dinero llegó.
- Un evento Outbox no equivale por sí solo a una notificación a n8n. Se requiere explícitamente un puente de eventos o un consumidor desplegado.

Fuentes internas:
- [README y entorno Docker](README.md)
- [Referencia actual de APIs](microservices-inventary-ecuapp/docs/25-referencia-apis.md)
- [Arquitectura WhatsApp/n8n](microservices-inventary-ecuapp/docs/09-webflux-n8n-whatsapp-openai.md)
- [Propuesta de Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Propuesta de Productos V2](PROPUESTA_UX_PRODUCTOS_V2.md)
- [Propuesta de Suscripciones](PROPUESTA_UX_SUSCRIPCIONES.md)
- [Propuesta de Pedidos WhatsApp V2](PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md)
- [Motor Comercial Inteligente](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)
- [Logística de retiro y envío](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)

## 3. Contraste de las cuatro propuestas

| Área | Necesidad del negocio | Responsabilidad de Inventary/backend | Responsabilidad de n8n | Uso opcional de IA |
|---|---|---|---|---|
| Productos | Ficha exacta, atributos, SKU/variantes, imagen, precio y disponibilidad fiable. | Validar/publicar ficha y entregar datos públicos permitidos. Separar datos comerciales de costos y márgenes. | Consultar el catálogo vigente y responder por WhatsApp. Enviar tareas de revisión cuando exista un evento autorizado. | Interpretar búsquedas y proponer descripción/atributos en borrador. Nunca inventar especificaciones ni publicar cambios sin aprobación. |
| Ventas | Confirmar artículos y total, registrar la venta una sola vez y mantener inventario/costeo coherentes. | Precio, cotización, descuentos, permisos, reserva/salida de stock, venta, cancelación y devolución transaccional. | Guiar al cliente, recopilar datos, llamar APIs, obtener aceptación explícita y coordinar mensajes. No crear ventas por SQL. | Extraer producto/cantidad/variante, responder dudas con datos de API y pedir aclaración cuando falte información. |
| Suscripciones | Explicar planes, frecuencia, plazo, total/calendario, contratar y consultar estado/cancelación. | Plan/precio autorizado, snapshot de condiciones, contrato, calendario, cuotas/facturas, cancelación y errores de facturación. Integrar pagos reales aparte. | Mostrar planes, solicitar preview y consentimiento, contratar/consultar mediante APIs específicas que aún faltan y comunicar eventos. | Explicar términos devueltos por la API. No inventar precios, modificar términos ni decir que una factura está cobrada. |
| Pedidos WhatsApp | Conversación sin repetir datos, pedido correcto, preparación, retiro/envío, pagos, incidencias y atención humana. | Persistir pedido, cotización, reserva, estados, fulfillment, pago/venta y auditoría. Validar las transiciones. | Recibir y normalizar webhooks, orquestar carrito/cotización, enviar mensajes, reintentar y coordinar handoff. | Entender lenguaje natural, resumir, proponer preguntas y redactar. Las acciones económicas siguen limitadas por backend, permisos y confirmación. |

Las áreas comparten catálogo, precios, cotización y políticas; no deben fusionar sus dominios:

- Venta única no es una contratación de suscripción.
- Pedido recibido no equivale a venta registrada.
- Factura emitida no equivale a dinero recibido.
- Disponibilidad consultada no equivale a reserva.
- Reserva no equivale a preparación.
- Mensaje enviado no equivale a mensaje entregado.
- Interpretación de IA no equivale a consentimiento del cliente.

## 4. Límites precisos entre backend, n8n y la IA

### 4.1 Obligaciones del backend

Inventary debe ser autoridad única para:

- estado de publicación y elegibilidad de productos;
- precio final, cantidad, moneda, descuento, impuesto y tarifa logística;
- cotizaciones y caducidad;
- asociación/validación del cliente, consentimiento, roles y permisos;
- creación idempotente de pedido/contrato/venta;
- reserva, consumo y liberación de inventario;
- estados operativos, comerciales, financieros y de fulfillment;
- calendarios de suscripción y emisión de cuotas/facturas;
- registro de dinero recibido, pagos pendientes, anticipos, deuda y reembolsos;
- origen/destino, preparación, despacho, retiro y entrega;
- movimientos, costeo, auditoría, referencias entre registros y eventos durables.

**Por qué:** esas operaciones afectan dinero, stock o compromisos contractuales y necesitan transacciones, control de concurrencia, permisos y registro idempotente. Un workflow puede fallar o ejecutarse dos veces; la integridad de la compra no puede depender de una ejecución de n8n.

### 4.2 Funciones adecuadas de n8n

n8n debe encargarse de:

- recepción de webhooks y normalización de eventos;
- deduplicación y enrutamiento de mensajes;
- coordinación de llamadas HTTP a APIs autorizadas;
- plantillas, mensajes, medios y estados de entrega que soporte el proveedor;
- recordatorios, horarios y tareas de comunicación;
- integración con proveedores externos elegidos;
- workflows con aprobación humana;
- errores de nodos, alertas, reintentos y trazabilidad técnica;
- resumen de conversación, clasificación de intención y borradores con IA;
- coordinación de la atención humana;
- notificación de eventos verificados del backend.

n8n puede iniciar un proceso por tiempo o evento, pero no sustituye el trabajo programado de facturación de suscripciones del backend. Tampoco decide unilateralmente si una cuota está vencida.

### 4.3 Lo que no debe hacer n8n

- No conectarse directamente a PostgreSQL para crear/editar productos, reservas, ventas, contratos o saldos.
- No almacenar la verdad comercial solamente en n8n Data Tables, hojas de cálculo, memoria del agente o prompts.
- No aceptar precio, stock, descuentos, impuestos, permisos o estado final generados por el modelo.
- No usar una sesión administrativa permanente para resolver todo el canal.
- No crear otra venta si ya existe una venta asociada al pedido.
- No marcar un pago como recibido leyendo una captura, transcripción o mensaje.
- No afirmar “listo para retirar”, “despachado” o “entregado” sin una transición real.
- No reintentar una transacción económica con clave nueva cuando el resultado anterior es incierto.
- No enviar costos internos, márgenes, credenciales o PII innecesaria al LLM.

### 4.4 Regla de ejecución de IA

El modelo propone una herramienta y argumentos; el workflow valida el esquema y el contexto; la API verifica permisos y ejecuta las reglas; n8n comprueba la respuesta real; solo después se redacta la respuesta para el cliente.

OpenAI y Gemini documentan function calling como un proceso donde el modelo solicita la llamada y la aplicación la ejecuta y devuelve el resultado. Las herramientas deben ser pequeñas, predecibles y limitadas por acción; no se debe entregar al LLM un cliente HTTP genérico con acceso irrestricto al backend.

Referencias:
- [OpenAI — Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [Gemini — Function calling](https://ai.google.dev/gemini-api/docs/function-calling)

## 5. Workflows n8n que se deben implementar

Los nombres siguientes son una convención propuesta. Los workflows deben versionarse en JSON dentro del repositorio; los secretos deben permanecer fuera de los archivos exportados.

### N8N-00 — Ingreso, normalización y deduplicación

**Objetivo:** transformar cada webhook en un evento confiable e impedir que un reintento cree otro pedido.

**Flujo:**
1. Recibir el webhook por el trigger de WhatsApp/HTTPS elegido.
2. Verificar origen/cuenta y aplicar las comprobaciones de autenticidad requeridas por la integración.
3. Diferenciar mensajes de usuario de acuses de lectura/entrega y otros callbacks.
4. Obtener el ID único del mensaje externo, número/canal y timestamp.
5. Deduplicar por el ID del proveedor; una repetición recupera el resultado previo, no inicia una nueva operación comercial.
6. Normalizar texto y adjuntos; validar tipo/tamaño y controlar fallos de descarga.
7. Asignar correlationId y registrar la etapa técnica mínima.
8. Enrutar a intención o contingencia.

**Implementación y razón:** una inbox persistente o un endpoint del backend debe ayudar con estados recibido/procesando/completado/fallido para recuperación robusta. n8n puede conservar referencias técnicas, pero el pedido sigue protegido por idempotencia del backend. Marcar “procesado” antes de persistir el resultado puede perder pedidos si luego falla el workflow.

**Pruebas:** mensaje duplicado, evento de entrega en vez de mensaje, archivo inválido, reinicio de n8n, webhook no auténtico y error de red a mitad del flujo.

### N8N-10 — Clasificación de intención y selección de herramienta

**Intenciones iniciales:** búsqueda/detalle de producto, precio/disponibilidad, editar carrito, cotización, confirmar pedido, consultar estado, retiro/envío, pregunta de pago, comprobante, información/contratación de suscripción, cancelación/devolución, queja, atención humana o intención desconocida.

Usar reglas deterministas para saludos, bajas, comandos evidentes y consultas de estado; usar IA opcional para lenguaje natural o medios permitidos. Si existe ambigüedad en producto, cantidad, variante, dirección, almacén, total o condición de pago, preguntar en lugar de adivinar.

**Salida estructurada propuesta:**
- intent;
- datos extraídos y nivel de certeza;
- campos faltantes;
- messageId de origen;
- herramientas permitidas;
- requiresCustomerConfirmation;
- requiresHumanReview;
- motivo de incertidumbre.

El modelo no debe generar un orderId arbitrario ni herramientas fuera de la lista permitida. Los IDs existentes se obtienen del contexto/API, no se vuelven a inventar.

### N8N-20 — Catálogo y respuesta de Productos

**Endpoints actuales para el canal:**
- GET **/api/v1/integrations/whatsapp/catalog/products**
- GET **/api/v1/integrations/whatsapp/catalog/products/{identifier}**

**Flujo:**
1. Normalizar la consulta y, si es útil, extraer atributos solicitados.
2. Buscar en el catálogo actual, con paginación limitada.
3. Responder con pocas opciones y solo campos publicados: nombre, especificaciones conocidas, precio, moneda e imagen autorizada.
4. Ofrecer alternativas únicamente cuando atributos reales permitan compararlas.
5. Si el catálogo no puede responder compatibilidad, garantía, disponibilidad logística o plazo, indicar que falta confirmar o escalar a una persona.

**Dependencia de Productos:** la calidad de respuesta depende de fichas completas, atributos por categoría, imagen, unidad y variantes mantenidas en la pantalla Productos. El agente no puede publicar una ficha ni activar un producto por su cuenta.

**Brecha de variantes:** el catálogo tiene APIs de variantes, pero el contrato actual del carrito WhatsApp utiliza productId y no modela explícitamente una variante en carrito/cotización/pedido. Antes de vender variantes distintas, ampliar esos contratos en backend y probar precio/disponibilidad de cada selección.

**Por qué:** un buen prompt no corrige datos de producto incompletos. Inventary debe conservar la verdad y n8n solo presentarla.

### N8N-30 — Carrito, cotización y pedido

**Endpoints actuales:**
- POST **/api/v1/integrations/whatsapp/carts**
- GET **/api/v1/integrations/whatsapp/carts/{cartId}**
- PUT **/api/v1/integrations/whatsapp/carts/{cartId}/items/{productId}**
- DELETE **/api/v1/integrations/whatsapp/carts/{cartId}/items/{productId}**
- POST **/api/v1/integrations/whatsapp/carts/{cartId}/quotes**
- GET **/api/v1/integrations/whatsapp/quotes/{quoteId}**
- POST **/api/v1/integrations/whatsapp/orders**
- GET **/api/v1/integrations/whatsapp/orders/{orderId}**
- POST **/api/v1/integrations/whatsapp/orders/{orderId}/cancel**

**Flujo objetivo con APIs actuales:**
1. Crear/recuperar carrito ligado a identidad del canal verificada, no a un teléfono libremente escrito.
2. Añadir, cambiar o quitar líneas con la API.
3. Elegir un warehouseId válido; hoy la cotización exige un almacén.
4. Solicitar cotización con Idempotency-Key única para esa operación.
5. Mostrar artículos, cantidades, total y vencimiento recibidos del backend.
6. Obtener confirmación explícita de ese contenido y ese total.
7. Crear pedido con consentConfirmed y clave idempotente; vincular messageId/versión de cotización cuando el backend exponga esos campos.
8. Comprobar respuesta real de API antes de comunicar el número del pedido.
9. Si vence la cotización, cambia el precio o falta stock, recotizar y volver a pedir aceptación.
10. Ante timeout con resultado incierto, consultar/reintentar la misma operación con la misma clave; no abrir otra orden.

**Qué se puede lograr hoy:** catálogo → carrito → cotización por un almacén → pedido con reserva → cancelación/expiración en estados permitidos.

**Qué requiere backend:** descuento en cotización WhatsApp, envío/retiro, tarifas, logística completa, pago online, contra entrega, pagar al retirar, crédito, variantes reales, preparación/despacho/entrega y contratación de suscripciones.

### N8N-40 — Consulta de pedido y posventa

La API permite consultar un pedido conocido, pero el workflow debe ligarlo a una identidad de cliente verificada y consultar el estado real antes de responder.

**Brecha por cerrar:** customerRef es opcional en la ruta actual de consulta. En un flujo hacia el cliente, un secreto técnico no equivale a identidad del comprador. Añadir verificación de titular/cliente en servidor y un DTO limitado, evitando que un orderId exponga información de otra persona.

Para retrasos, faltantes, pagos, cambios, cancelación o reclamaciones, el workflow debe crear/enrutar una incidencia persistente o entregar el caso al operador. No prometer reembolso, sustitución ni fecha de entrega sin una decisión registrada.

### N8N-50 — Notificaciones por eventos de negocio

**Objetivo:** notificar solo después de que Inventary registre un cambio real.

Eventos deseados: pedido aceptado/rechazado, comprobante pendiente, pago conciliado/fallido, preparación iniciada, listo para retirar, despachado, en tránsito, entregado/retirado, incidencia, cancelación y expiración.

**Brecha actual:** Inventary crea eventos Outbox y tiene relay Kafka opcional; Compose desactiva Kafka y no se debe considerar que n8n consume esos eventos. Para notificaciones posteriores a la creación del pedido, implementar un puente de eventos.

**Recomendación:**
1. Crear la fila Outbox dentro de la transacción de negocio.
2. Entregar eventos a un webhook privado de n8n o a un broker/consumidor desplegado explícitamente.
3. Evitar mantener la transacción de la venta abierta durante llamadas de red; reclamar eventos con lease y registrar el resultado en transacciones cortas.
4. Incluir eventId, eventType, schemaVersion, occurredAt, aggregateId, correlationId y payload mínimo.
5. Deduplicar por eventId en n8n y mantener una clave lógica para la notificación.
6. Si falla WhatsApp, dejar notificación pendiente/fallida y reintentar; no revertir la venta ni cambiar fulfillment.
7. Proteger webhook/red con secreto o firma y no poner tokens en el evento.

Como transición se puede hacer polling limitado de una API autorizada de estados de pedido conocido; no consultar todas las órdenes a alta frecuencia ni acceder a tablas SQL desde n8n.

### N8N-60 — Handoff a atención humana

Transferir cuando el cliente lo pida, exista duda de producto/cantidad/destino/pago, haya reclamación o discrepancia, se solicite un descuento/crédito no autorizado, se repita un fallo de API o el modelo no tenga certeza suficiente.

El handoff debe incluir:
- conversationId/messageId y correlationId;
- intención y resumen objetivo;
- datos confirmados;
- carrito, quoteId, orderId o subscriptionId relacionados;
- total mostrado y estado real de API;
- pasos intentados, errores y motivo de derivación;
- responsable actual y estado de pausa del bot.

Mientras atiende una persona se deben pausar las respuestas automáticas que puedan contradecirla. Reanudar requiere acción explícita. La bandeja Pedidos WhatsApp debe obtener esta información de una API/servicio persistente, no reconstruirla únicamente a partir de ejecuciones de n8n.

### N8N-70 — Contratación de Suscripciones por WhatsApp (futuro)

**No llamar directamente el endpoint administrativo actual.** Las rutas /api/v1/subscriptions requieren autenticación/autoridades de personal; la integración WhatsApp no ofrece un endpoint de canal para contratación.

**Primero en backend:**
1. Endpoint limitado de planes publicables/condiciones.
2. Preview de frecuencia, plazo, calendario, total y fecha de inicio.
3. Identidad segura y resolución del cliente; no aceptar customerId arbitrario generado por el LLM.
4. Contratación por canal con Idempotency-Key, plan autorizado y consentimiento ligado a mensaje verificado.
5. Snapshot de oferta/términos y relación con canal/messageId.
6. Consulta de estado del propio contrato y solicitud de cancelación bajo las mismas reglas de negocio.
7. Eventos de factura generada, error de facturación, término y cancelación.
8. Permisos/rate limit específicos para la credencial de integración.

**Después, workflow:**
1. Consultar planes vigentes y términos oficiales.
2. Preguntar solo campos faltantes.
3. Presentar frecuencia, plazo, precio, total/calendario, condición de facturación/cancelación y siguiente paso.
4. Recoger confirmación explícita y guardar el messageId verificado.
5. Crear contrato con clave idempotente.
6. Consultar respuesta definitiva y comunicar el ID/estado.
7. Consultas futuras limitadas al cliente autorizado.

**Controles de precio:** el endpoint actual de creación permite sobrescribir frecuencia, plazo y monthlyPrice; la API del canal debe prohibir valores fuera del plan autorizado o exigir permiso explícito. El precio sugerido por IA no es autoritativo. El método prepay genera una factura/venta consolidada de cuotas pendientes, no demuestra que el cliente haya pagado. El job programado de facturación ya pertenece al backend; no duplicarlo con Schedule Trigger en n8n.

### N8N-80 — Revisión de calidad de productos (futuro)

n8n puede crear una tarea de revisión si un evento autorizado indica que falta una imagen, atributo, unidad o descripción útil. IA puede proponer redacción/normalización de un atributo, siempre en borrador y con la fuente de origen.

No usar la clave de WhatsApp para editar o activar productos: las escrituras actuales requieren catalog:manage. Los cambios de producto se mantienen en la interfaz/autorización de administración. No inventar compatibilidad, certificación, garantía o especificaciones.

### N8N-90 — Errores, alertas y recuperación

Configurar Error Workflow con Error Trigger y salidas de error para las llamadas HTTP/IA/proveedor relevantes.

Alertas mínimas:
- caída de backend, proveedor WhatsApp, IA o logística;
- webhook inválido;
- intentos repetidos o posibles mensajes duplicados;
- creación de pedido con resultado incierto por timeout;
- mensaje saliente pendiente/fallido;
- evento Outbox atrasado o FAILED;
- reintentos agotados y credencial inválida;
- intento de acción económica sin consentimiento/permiso.

Reintentar fallos transitorios con límites; no reintentar 400/403 indefinidamente. En acciones económicas, usar la misma clave/cuerpo y consultar estado antes de repetir.

n8n documenta Error Workflows y reintentos, pero repetir una acción externa puede volver a enviar un mensaje o duplicar un efecto si el sistema destino no es idempotente. Por eso n8n y el backend necesitan controles complementarios.
Referencias:
- [n8n — manejo de errores](https://docs.n8n.io/flow-logic/error-handling/)
- [n8n — ejecuciones y reintentos](https://docs.n8n.io/workflows/executions/)

## 6. Cambios necesarios en backend y por qué

### BE-P0 — Integridad, identidad y contratos de canal

1. Definir scopes técnicos: catálogo, carrito, pedido, consulta, cancelación, suscripciones, notificaciones. No entregar privilegios admin genéricos.
2. Persistir o verificar IDs externos de mensajes, customerRef de canal y consentimiento de la confirmación.
3. Endurecer la consulta de pedido, verificando asociación a cliente en servidor.
4. Garantizar idempotencia tras timeout con misma clave/cuerpo, no clave nueva.
5. Conservar la versión/importe de la cotización y la aceptación del cliente.
6. Implementar puente Outbox a n8n o broker con observabilidad y deduplicación.
7. Ampliar pruebas HTTP, permisos, concurrencia, duplicados, expiración y acceso entre clientes.

**Por qué:** evita pedidos duplicados, exposición de datos y compromisos comerciales inconsistentes.

### BE-P1 — Cotización común, Ventas y pedidos

1. Definir un servicio común de cotización reutilizable desde venta manual y canal WhatsApp, con líneas, precio snapshot, descuentos autorizados, moneda, impuestos aplicables, expiración y cambios.
2. Mantener distintos casos de uso/endpoints, pero no duplicar la lógica comercial en n8n.
3. Separar estado operativo, estado/condición de pago y estado de fulfillment.
4. Añadir modalidad PICKUP/DELIVERY, destino, tarifa y plazo en backend; habilitar solo las opciones ejecutables.
5. Implementar aceptación, preparación, listo, despacho, entrega/retirada y cancelación como comandos auditables.
6. Representar saldo, anticipo, pago recibido y reembolso como hechos separados.
7. Agregar descuento a cotización con regla/permiso y aceptación explícita.
8. Enlazar orderId con saleId, y hacerlo visible en ambas pantallas.
9. Ampliar catálogo/carrito/cotización para variantes si el negocio lo necesita.
10. Revisar cálculo fiscal/documentos antes de afirmar que hay facturación fiscal completa.

**Por qué:** el contrato actual de WhatsApp no cubre importes y cumplimiento completos y el pedido debe alinearse con las reglas de la venta manual.

### BE-P1 — API de Suscripciones por canal

1. Publicar planes elegibles sin datos internos.
2. Añadir vista previa de términos/calendario.
3. Añadir create por canal con idempotencia, identidad segura y consentimiento verificable.
4. Validar que precio, frecuencia y plazo pertenecen al plan autorizado.
5. Guardar snapshot de términos y relación con mensaje/canal.
6. Publicar eventos de cuota/factura/error/cancelación.
7. Mantener separados factura, pago real y habilitación de beneficio.
8. Proteger cancelación, prepago y descuento con las reglas/permisos de dominio.

**Por qué:** conectar n8n directamente al endpoint administrativo existente no es una integración segura ni alineada con el flujo comercial propuesto.

### BE-P2 — Operación avanzada

- Retiro por tercero con autorización/código de un solo uso.
- Horarios y capacidad de local, prioridad urgente y política de pedidos nocturnos.
- Multiubicación, transferencias y grupos de fulfillment.
- Pasarela, callbacks autenticados, conciliación, anticipos, reembolsos y contra entrega.
- Cuenta por cobrar/crédito pospago con límites y vencimientos.
- Seguimiento/prueba de entrega con proveedor elegido.
- API de historial de conversación/asignación con retención y permisos.
- Puente Outbox y redrive robusto.

No mostrar un selector en React ni agregar una rama de workflow hasta que el contrato de backend y sus pruebas existan.

## 7. Matriz actual vs. futuro

| Capacidad | Estado actual | ¿n8n puede hacerlo hoy? | Qué falta para el objetivo |
|---|---|---|---|
| Consultar catálogo público | Sí | Sí, APIs del canal | Mejorar atributos/filtros/variantes según necesidades. |
| Crear/editar producto | Sí, API autenticada de personal | No con la clave WhatsApp; no conviene editar autónomamente | Flujo de borradores/aprobación si se decide. |
| Carrito por producto | Sí | Sí | Variante explícita si se requiere vender opciones independientes. |
| Cotización por almacén | Sí | Sí | Descuentos, envío, impuestos completos y fulfillment. |
| Pedido con reserva y consentimiento binario | Sí | Sí, con claves idempotentes | Evidencia de mensaje, snapshot y estados completos. |
| Consulta de pedido conocido | Sí | Sí, después de verificar el titular | Consulta segura y DTO limitado. |
| Confirmar pago/crear venta | Sí desde backoffice JWT y sales:manage | No con la credencial técnica actual | Separar pago, venta y fulfillment; integrar proveedor si hace falta. |
| Prepago por pasarela | No | No | Pasarela, callbacks, conciliación y APIs. |
| Comprobante pendiente de verificación | No como ciclo completo | Puede recibirlo/clasificarlo, no validarlo financieramente | Entidad/estado de comprobante y verificación autorizada. |
| Pago al retirar/contra entrega | No como flujo completo | Puede comunicarse después de implementar el dominio | Estado/saldo, entrega y cobro separados. |
| Descuento en cotización WhatsApp | No está en el contrato actual | No debe calcularlo n8n | Regla backend, permiso, preview y aceptación. |
| Retiro/envío/tercero/tracking | No en pedido actual | No end-to-end | Dominio y comandos de fulfillment. |
| Notificación por cambio de estado | Puente a n8n no demostrado | Puede enviar durante una ejecución activa | Integración Outbox-eventos-notificación o polling autorizado. |
| Contratar suscripción por WhatsApp | No | No | API de canal para planes/preview/create/consulta/cancelación. |
| Facturar cuotas por calendario | Sí, job del backend | No debe duplicarse en n8n | n8n comunica eventos, no crea cuotas. |
| Cobro/reembolso automático de suscripción | No | No | Proveedor y APIs de pago/conciliación. |
| IA de interpretación/resumen | Opcional, externo | Sí, como capa no autoritativa | Herramientas limitadas, evaluación, privacidad y handoff. |
| IA cambia y publica producto | No | No con credencial actual | Borrador y aprobación autenticada. |
| Preparar, despachar y entregar pedido | No en modelo actual | No end-to-end | Estados, permisos y endpoints logísticos. |

## 8. Contratos sugeridos para extender la API

Los siguientes conceptos son **propuestas, no endpoints actuales**. Deben aprobarse, adaptarse al código existente y documentarse en OpenAPI antes de construir workflows dependientes.

### 8.1 Catálogo y variantes

- Búsqueda pública limitada por página/categoría/atributos.
- Detalle público con atributos publicados, imágenes autorizadas y variante identificable.
- Disponibilidad con significado claro: informativa o reservable, nunca ambas de forma ambigua.
- Vista pública de planes de servicio/suscripción elegibles.
- Razones de inelegibilidad sin exponer costos/márgenes.

### 8.2 Cotización comercial reusable

La respuesta debe incluir:
- quoteId, quoteVersion, status, issuedAt y expiresAt;
- líneas, cantidades y precio snapshot;
- moneda, subtotal y descuentos/reglas;
- impuestos/cargos calculados por backend;
- envío, modalidad, origen/destino y promesa si esos procesos están implementados;
- total final y códigos de bloqueo/cambio;
- importe exacto y versión que el cliente debe aceptar.

### 8.3 Pedido y fulfillment

El pedido necesita estado comercial, condición/estado de pago y fulfillment por separado. Las operaciones deben ser comandos auditables (aceptar, preparar, listo, despachar, entregar, recoger, cancelar) con Idempotency-Key, precondiciones y verificación de estado actual. Si otro actor cambió el pedido, devolver conflicto y permitir recargar.

### 8.4 Suscripciones por canal

- Listar planes y términos autorizados.
- Previsualizar calendario e importe.
- Contratar con clave idempotente, cliente vinculado y consentimiento verificado.
- Consultar contrato/calendario solo por titular autorizado.
- Solicitar cancelación con motivo y reglas/preview actuales.
- Consultar eventos de facturación y errores.
- No permitir cambios de precio/frecuencia/plazo fuera de oferta autorizada.

### 8.5 Eventos para n8n

Cada evento necesita eventId estable, eventType, schemaVersion, occurredAt, aggregateId, correlationId y payload mínimo. Si la entrega es al menos una vez, n8n debe deduplicar por eventId; no se debe afirmar exactly-once. El puente debe registrar entrega, fallo, reintentos y redrive.

## 9. Idempotencia y tratamiento de errores

### 9.1 Clave de idempotencia

- Una clave por acción lógica: cotizar, crear pedido, contratar suscripción, cancelar o cambiar fulfillment.
- Ante timeout ambiguo, reintentar con la misma clave y mismo cuerpo.
- Si el cliente cambia el pedido, crear nueva cotización/operación después de nueva aceptación.
- No compartir clave entre carrito, cotización, pedido, pago y contrato.
- El ID del mensaje no sustituye a la clave de negocio: un mensaje puede causar una consulta y luego una operación comercial distinta.
- Tras reiniciar n8n, consultar el resultado desde backend/inbox persistente antes de crear un nuevo registro.

### 9.2 Errores por categoría

- 400/422: datos incompletos/inválidos; explicar y pedir corrección.
- 401/403: credencial/permisos; alertar, no reintentar indefinidamente.
- 404: recurso no válido/no asociado a ese cliente; no revelar datos ajenos.
- 409: cambió el estado/cotización; recuperar estado, recotizar y pedir nueva aceptación.
- 429/5xx/timeout: reintento limitado con espera. Si el resultado económico es incierto, consultar antes de repetir.
- Error de WhatsApp/IA: dejar la transacción intacta y activar fallback/handoff.

No convertir los fallos de negocio a HTTP 200 con una cadena de error, porque se perdería la distinción entre éxito y fallo.

## 10. Arquitectura de extremo a extremo

~~~mermaid
flowchart TD
    C[Cliente] <--> W[WhatsApp Business Platform]
    W --> N0[n8n: verificar, normalizar, deduplicar]
    N0 --> N1[Clasificar intención]
    N1 --> N2[n8n: herramientas permitidas]
    N1 --> NS[n8n: contratación de suscripción futura]
    N1 --> NH[n8n: consulta o handoff humano]
    N2 --> API[Inventary REST API]
    NS --> API
    NH --> API
    API --> DB[(PostgreSQL)]
    API --> STOCK[Reservas, movimientos y costeo]
    API --> SALES[Ventas]
    API --> SUB[Suscripciones]
    API --> OB[Outbox transaccional]
    OB --> RELAY[Puente seguro de eventos]
    RELAY --> N3[n8n: notificar/actualizar]
    N3 --> W
    N1 -. opcional .-> AI[OpenAI o Gemini]
    AI -. intención/datos propuestos .-> N1
    STAFF[React: Productos, Ventas, Suscripciones, Pedidos] <--> API
    STAFF <--> NH
~~~

La IA puede desaparecer de esta arquitectura sin detener el proceso comercial básico. La API ejecuta la transacción; los mensajes salientes se basan en la respuesta/estado definitivo.

## 11. Configuración, despliegue y versionado de n8n

### 11.1 Entornos

Separar desarrollo, pruebas y producción. Desarrollo debe usar un número/cuenta de WhatsApp de prueba y clientes/productos de prueba. El archivo Compose actual tiene una URL de webhook por defecto localhost, válida solo para pruebas locales; la integración real necesita HTTPS accesible para el proveedor, dominio/túnel y proxy correctamente configurado.

No exponer n8n sin controles de despliegue, autenticación, red y proxy. El backend puede permanecer en red privada; no exponer PostgreSQL ni toda la API a Internet para resolver el webhook.

### 11.2 Credenciales

- **N8N_ENCRYPTION_KEY:** secreta y estable para cifrar las credenciales guardadas; proteger en el entorno de despliegue.
- **WHATSAPP_INTEGRATION_API_KEY:** credencial de máquina actual para X-Integration-Key; almacenarla en el credential store de n8n.
- Credenciales de WhatsApp y de IA: gestionar como credenciales, no como expresiones visibles ni datos de prompt.
- URL de API: usar hostname interno y rutas documentadas.
- Configurar timeouts, retención de ejecuciones, límites de adjuntos y concurrencia.
- Evitar tokens, cabeceras y transcripciones completas en logs/alertas.

La clave actual es una credencial técnica, no una autorización del cliente final. Para contratos/pagos/suscripciones deberán existir scopes y restricciones explícitos.

### 11.3 Versionado de workflows

Crear carpeta propuesta **n8n/workflows/** con JSON exportado y saneado, README por workflow (objetivo, trigger, dependencias, entradas/salidas, credenciales nombradas), payloads ficticios de pruebas, versión de esquema API, instrucciones de importación/activación/rollback y responsable.

No guardar secretos o ejecuciones con datos personales. Si la edición de n8n no incluye Source Control/Environments, usar exportación manual saneada y Git con revisión de cambios. Separar desarrollo de producción y publicar cambios explícitamente.

Referencia: [n8n — Source Control y environments](https://docs.n8n.io/source-control-environments/create-environments/).

### 11.4 Logs y recuperación

- Guardar correlationId, messageId y IDs de operación sin tokens ni PII innecesaria.
- Configurar Error Workflow global y ramas de error.
- Reintentar fallos transitorios con límites.
- No repetir 400/403 indefinidamente.
- Para operación económica, dejar que la idempotencia del backend gobierne reintentos.
- Documentar diagnóstico/redrive manual.
- Medir eventos Outbox pendientes/FAILED, edad del evento más antiguo y notificaciones fallidas.

Referencias:
- [n8n — manejo de errores](https://docs.n8n.io/flow-logic/error-handling/)
- [n8n — ejecuciones y reintentos](https://docs.n8n.io/workflows/executions/)
- [Documentación oficial n8n](https://docs.n8n.io/)

## 12. Seguridad y límites de IA

### 12.1 Herramientas limitadas por tarea

Herramientas futuras posibles:
- search_products;
- get_product_details;
- get_cart;
- set_cart_item;
- quote_cart;
- submit_customer_confirmed_order;
- get_customer_order_status;
- get_subscription_offer;
- preview_subscription;
- create_customer_confirmed_subscription;
- request_human_handoff.

No mostrar herramientas de contratación/cancelación a una simple consulta informativa sin confirmación. El workflow comprueba contexto, identidad, estado y permisos. Descuento excepcional, crédito, pago, cancelación y aceptación contractual requieren autorización/confirmación específica.

### 12.2 Privacidad y consentimiento

- Minimizar PII en el prompt.
- No enviar costos, márgenes o credenciales al modelo.
- Separar consentimiento operativo de promociones.
- Respetar baja, ventana de atención y plantillas vigentes de WhatsApp.
- Proteger direcciones, documentos, códigos de retiro y transcripciones con acceso/retención adecuados.
- No usar número de teléfono como única autorización para una acción económica de alto impacto.
- Conservar fuente de los datos extraídos y permitir corrección humana.

### 12.3 Selección del proveedor IA

Crear un adaptador de proveedor intercambiable. Las herramientas y reglas viven en n8n/API, no en prompts irrepetibles de cada modelo.

Comenzar en modo sombra/copiloto. Medir intención correcta, campos corregidos, errores por producto/cantidad, duplicados, handoff, latencia, coste y quejas. Solo después habilitar autonomía limitada, con feature flag, permisos y rollback.

## 13. Plan de implementación por fases

### Fase 0 — Entorno reproducible

**Implementar:** carpeta n8n versionada, credenciales, número de prueba, webhooks, acceso interno a API, matrices de permisos, versión de n8n/backend y proceso de importación.

**Por qué:** hoy existe el contenedor, pero falta evidencia versionada de workflows listos para importar.

**Terminado cuando:** instalación limpia puede importar workflows, usar credenciales locales y recibir un webhook de prueba sin exponer secretos.

### Fase 1 — WhatsApp determinista

**n8n:** N8N-00, N8N-10, N8N-20, N8N-30, N8N-90.

**Backend:** endurecer consulta de titular, evidencia del mensaje, snapshots de cotización y aceptación, idempotencia tras timeout y pruebas de webhook duplicado.

**Terminado cuando:** una compra produce una sola orden/reserva, la respuesta corresponde al estado real y están probados duplicados, expiración, cambio de precio, agotado y fallo de API.

### Fase 2 — Eventos, notificaciones y handoff

Implementar puente Outbox a n8n/broker, N8N-50/N8N-60, eventos versionados y alertas. El backend debe corregir/validar el relay y no mantener transacciones de negocio abiertas durante llamadas de red.

**Terminado cuando:** un cambio real genera máximo una notificación lógica, un fallo de WhatsApp no revierte la venta y los eventos pendientes se pueden recuperar sin duplicados.

### Fase 3 — Alinear cotización, Ventas y Productos

Backend: cotización reusable, descuentos, costos/cargos fiscales/logísticos que se aprueben, variantes y enlaces orderId-saleId. n8n: llamar al contrato común, no replicar cálculo; mantener escrituras de catálogo en flujo autenticado.

**Terminado cuando:** web y WhatsApp reflejan importes coherentes y se detectan cambios de precio/stock.

### Fase 4 — Fulfillment y condiciones de pago

Backend: estados separados; preparación, listo, despacho, entrega/retirada; destino/tarifa; reservas/SLA; terceros; pagos por modalidad. n8n: notificar eventos reales y capturar solicitudes, pero no marcar estados sin API.

**Terminado cuando:** se prueba pago pendiente durante tránsito, tercero autorizado, entrega fallida, cancelación y liberación exacta de stock.

### Fase 5 — Suscripciones por WhatsApp

Backend primero: endpoints de planes/preview/create/consulta/cancelación, snapshot, consentimiento, idempotencia y eventos. Después N8N-70. El job de facturación sigue siendo del backend; pago real se integra por separado.

**Terminado cuando:** un consentimiento crea máximo un contrato, la factura está vinculada, los importes coinciden y nunca se afirma pago sin evidencia financiera.

### Fase 6 — IA gradual

1. Modo sombra, sin enviar ni mutar negocio.
2. Copiloto con aprobación humana.
3. Herramientas de lectura.
4. Escrituras de bajo riesgo con confirmación explícita.
5. Acciones económicas de alto impacto solo con controles y autorización adecuados.

**Terminado cuando:** hay evaluación con mensajes anonimizados/sintéticos, guardas de calidad, rollback y permisos limitados.

## 14. Matriz de pruebas backend + n8n + React

Todas las pruebas importantes verifican HTTP y datos persistidos, no solo el color verde de un workflow.

### Productos
1. Producto inactivo/no publicado no se ofrece.
2. Imagen local no accesible no se presenta como imagen pública.
3. Atributo desconocido no se inventa.
4. Variante no soportada provoca aclaración, no la compra accidental del producto padre.
5. Precio cambiado requiere nueva cotización.

### Ventas y pedidos
6. Webhook duplicado no crea una orden duplicada.
7. Misma clave/cuerpo de cotización o pedido devuelve el mismo resultado.
8. Misma clave con cuerpo diferente devuelve conflicto.
9. Timeout tras crear pedido se recupera con misma clave.
10. Stock concurrente no se sobrevende.
11. Sin aceptación del total no se crea compromiso.
12. Cotización vencida requiere nueva aceptación.
13. n8n no llama la API de ventas con la clave WhatsApp ni crea venta adicional.
14. Pago no verificado permanece pendiente.
15. Pedido/venta tienen una sola salida de stock.

### Suscripciones
16. No usar endpoint de personal con clave genérica de máquina.
17. Plan no elegible no se contrata.
18. Precio/frecuencia/plazo manipulado por IA se rechaza o no se acepta.
19. Consentimiento repetido no duplica contrato ni primera factura.
20. Preview y creación coinciden; cualquier diferencia requiere nueva aceptación.
21. Factura no se describe como pago recibido.
22. n8n no duplica el calendario programado de facturación.
23. Cancelación/descuento requieren las reglas y permisos correspondientes.

### Seguridad y recuperación
24. Webhook no auténtico se rechaza.
25. Eventos de lectura/entrega no inician otra venta.
26. Credencial ausente/incorrecta se rechaza.
27. Cliente no puede consultar pedido de otra persona.
28. Respuesta malformada de IA no invoca una herramienta.
29. Herramienta fuera de allow-list se rechaza.
30. Caída de IA activa fallback humano.
31. Error Workflow no expone secreto ni PII innecesaria.
32. Error de mensajería no revierte la operación ni duplica la notificación lógica.
33. Outbox pendiente/FAILED genera alerta y redrive controlado.
34. Workflows productivos están exportados, versionados, probados y con responsable.
35. Cambios de workflow se validan en pruebas antes de publicar en producción.

## 15. Métricas de operación

**Backend:** órdenes/contratos creados, duplicados evitados, cotizaciones cambiadas, reservas liberadas, discrepancias cotización-pedido-venta-pago, eventos Outbox pendientes/FAILED, errores por endpoint, tiempos de API, cuotas y errores de suscripción.

**n8n:** mensajes entrantes válidos/duplicados, ejecuciones fallidas, reintentos, latencia de respuesta, errores de API/proveedor, notificaciones pendientes/fallidas, derivaciones humanas, campos corregidos por humanos y coste de IA por pedido completado.

**Negocio:** pedidos completados, cancelaciones y motivos, tiempos de preparación/entrega cuando existan, pagos pendientes y conciliación, contra entrega no cobrada, saldo vencido, descuentos, margen y reclamaciones.

No optimizar solo rapidez del chatbot o número de mensajes respondidos. Las guardas principales son integridad de stock/dinero, ausencia de duplicados, cumplimiento de compromisos y protección de datos.

## 16. Decisiones de negocio antes de habilitar funciones avanzadas

1. Momento exacto de registro de venta para cada condición de pago.
2. Qué medios de pago están disponibles hoy y cuáles requieren integración externa.
3. Política de reserva para prepago, retiro, contra entrega, crédito y pedidos nocturnos.
4. Ubicaciones habilitadas para retiro, horarios y capacidad.
5. Zona, tarifa, plazo y proveedor de envíos.
6. Roles permitidos para descuentos, pagos, crédito, cancelaciones y reembolsos.
7. Relación entre factura de suscripción, pago y beneficio/acceso.
8. Datos del cliente necesarios, finalidad, permisos y retención.
9. Consentimiento para actualizaciones operativas frente a promociones.
10. Proveedor/modelo IA habilitado, datos que recibe, presupuesto y fallback.
11. Responsable de cada workflow y receptor de alertas.

n8n no debe decidir estas reglas de manera accidental al configurar ramas de un workflow. Deben quedar reflejadas en backend, permisos y configuración comercial.

## 17. Criterios de aceptación global

La alineación se considera completa cuando:

- Productos es la fuente autorizada de los datos publicables.
- Ventas y WhatsApp usan la misma lógica de precios/stock sin crear ventas duplicadas.
- Suscripciones usa el caso de uso de suscripción, con condiciones versionadas y vinculadas a factura.
- Pedido, pago, reserva, preparación y entrega tienen estados diferenciados.
- n8n puede detenerse y recuperarse sin perder la verdad transaccional ni duplicar operaciones.
- Los cambios reales del backend llegan a n8n mediante un puente probado y observable.
- La IA puede desactivarse sin detener el canal de venta o la atención humana.
- Las credenciales y scopes limitan n8n a lo que necesita.
- Workflows versionados tienen entradas/salidas, errores, dueño, rollback y pruebas.
- Los checks y pruebas pasan en el SHA exacto que se despliega.

## 18. Referencias

### Documentación interna
- [README / Docker Compose](README.md)
- [Referencia de APIs actuales](microservices-inventary-ecuapp/docs/25-referencia-apis.md)
- [Arquitectura n8n/WhatsApp](microservices-inventary-ecuapp/docs/09-webflux-n8n-whatsapp-openai.md)
- [Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Productos V2](PROPUESTA_UX_PRODUCTOS_V2.md)
- [Suscripciones](PROPUESTA_UX_SUSCRIPCIONES.md)
- [Pedidos WhatsApp V2](PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md)
- [Motor Comercial Inteligente](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)
- [Logística de retiro/envíos](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)
- [Guía de consumo de APIs](microservices-inventary-ecuapp/docs/27-guia-consumo-apis.md)

### Fuentes externas oficiales
- [n8n Docs](https://docs.n8n.io/)
- [n8n: error handling](https://docs.n8n.io/flow-logic/error-handling/)
- [n8n: ejecuciones y reintentos](https://docs.n8n.io/workflows/executions/)
- [n8n: Source Control/environments](https://docs.n8n.io/source-control-environments/create-environments/)
- [WhatsApp Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api)
- [Nodo WhatsApp Business Cloud de n8n](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.whatsapp)
- [OpenAI: Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [Gemini: Function calling](https://ai.google.dev/gemini-api/docs/function-calling)

Las fuentes externas describen patrones de producto y herramientas; no prueban que esas integraciones estén desplegadas en Inventary. Toda capacidad futura exige contratos, permisos, pruebas y despliegue real antes de anunciarse como disponible.
