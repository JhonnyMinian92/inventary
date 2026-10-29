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




---

# Revisión prioritaria V2.1 — coste de mensajería Meta, WhatsApp Flows y compra en menos de un minuto

**Fecha de revisión:** 28 de octubre de 2026  
**Motivo:** diseñar el recorrido de compra de modo que requiera pocas intervenciones del negocio, evite conversaciones innecesariamente largas y sea fácil de completar por usuarios con edades, experiencia digital, capacidades y preferencias distintas.  
**Importancia:** esta sección complementa y prevalece sobre cualquier parte general de este documento que trate la mensajería de WhatsApp como si todas las respuestas fueran gratuitas o como si el chatbot debiera hacer cada pregunta en un mensaje separado.

## 19. La economía real del canal: optimizar mensajes entregados, no solamente los tokens de IA

El diseño debe tratar el costo de mensajería como una métrica de producto y arquitectura. El objetivo es que el cliente compre rápido, con claridad y sin errores, reduciendo los mensajes salientes redundantes. No se debe sacrificar una confirmación económica esencial para ahorrar un mensaje ni automatizar una decisión arriesgada.

### 19.1 Aclaración sobre el modelo de cobro de Meta

El modelo moderno de WhatsApp Business Platform es **por mensaje entregado**, con tarifas que dependen del país del destinatario y de la categoría (marketing, utility, authentication y service), no un cobro universal idéntico por cada interacción de usuario. El modelo pasó a cobrar por mensaje de plantilla entregado el 1 de julio de 2025. Para octubre de 2026, fuentes de tarifas que refieren al rate card de Meta describen que los mensajes de servicio también pasan a tener tarifa por mensaje tras un tramo mensual gratuito; una página pública de precios de Meta todavía presenta texto general que indica que los mensajes de servicio no se cobran. Esta divergencia obliga a verificar la tarifa y las condiciones vigentes directamente en la tarjeta de precios de la cuenta/WABA antes de desplegar y cada vez que cambien las reglas. No asumir como contrato estable una tabla copiada en un blog.

Referencias que se deben comprobar antes de la puesta en producción:
- [Página oficial de precios de WhatsApp Business Platform](https://whatsappbusiness.com/products/platform-pricing/)
- [Política de categorías de mensajes de utilidad](https://whatsappbusiness.com/products/conversation-categories/utility/)
- [Política de mensajes de servicio](https://whatsappbusiness.com/products/conversation-categories/service/)
- [Tarifas por mensaje en Latinoamérica consultadas para octubre de 2026](https://contactship.ai/en/blog/whatsapp-business-api-pricing-latin-america-2026)
- [Tarifas de WhatsApp Business API en Ecuador/resto de Latinoamérica para octubre de 2026](https://contactship.ai/blog/precios-whatsapp-business-api-resto-latinoamerica-2026)

Como referencia presupuestaria —no como precio contractual—, las tablas secundarias que dicen basarse en el tarifario Meta vigente desde el 1 de octubre de 2026 muestran para Ecuador, agrupado en “Resto de Latinoamérica”, alrededor de USD 0,0113 por mensaje de servicio, utility o authentication y USD 0,074 por mensaje de marketing. También describen 1.000 respuestas de servicio gratuitas mensuales por número, con cobro de las siguientes. Estas cantidades deben verificarse en el WABA y la moneda de facturación real; no codificarlas como constantes en Java, React ni n8n.

Reglas prácticas:
- Meta cobra el mensaje cuando se entrega, según categoría y mercado; los envíos fallidos no equivalen automáticamente a un cargo por entrega.
- Un mensaje entrante del cliente, una respuesta saliente libre, una plantilla utility, una plantilla marketing y un mensaje de Flow no son categorías intercambiables.
- Si se utiliza un BSP/proveedor intermediario, puede añadir sus propias tarifas por mensaje, por número o por plataforma, aparte de Meta. n8n, hosting, base de datos y llamadas al modelo también tienen costos aparte.
- La ventana de servicio, los puntos de entrada gratuitos y las excepciones cambian según las políticas vigentes. Los anuncios click-to-WhatsApp tienen reglas especiales de entrada; confirmar duración y elegibilidad con el sistema actual, sin basar la economía en una ventana gratuita que no se haya validado.
- No clasificar artificialmente como utility un mensaje promocional. La categoría la determinan el contenido, el propósito y las reglas vigentes de Meta.
- Los mensajes de seguridad, entrega, pago y actualizaciones pedidas por el usuario deben ser concisos y específicos; no añadir ofertas promocionales a mensajes transaccionales.

### 19.2 Costo medible por compra terminada

Instrumentar el canal para medir el número de mensajes salientes entregados y su costo estimado, no solo el número de ejecuciones n8n. Por operación lógica, registrar como mínimo:

- conversation/message correlation, número de mensajes entrantes y salientes;
- proveedor, messageId, timestamp, categoría/plantilla, país de destino y estado final entregado/fallido;
- tarifa aplicada/versión de tarifario disponible, moneda y cargo de BSP si existe;
- mensajes de entrada a Flow, envío de Flow y respuesta de Flow, sin asumir que el resultado de Flow es gratis para el negocio o que equivale a un único mensaje facturable en cualquier circunstancia;
- llamadas de IA/tokens/costo del proveedor, separados del cobro de Meta;
- compra aceptada, pedido creado, venta vinculada, cancelación, conversión o abandono;
- motivo por el que se envió un mensaje adicional: información obligatoria, cambio de precio, aclaración de producto, error, recordatorio autorizado o seguimiento solicitado.

No guardar el contenido completo del chat para la telemetría de costos si bastan IDs y categorías. El detalle sensible queda bajo sus permisos/retención correspondientes.

Definir indicadores:
- costo Meta + proveedor por pedido aceptado;
- mensajes salientes entregados por pedido aceptado/completado;
- porcentaje de pedidos que necesitan corrección por producto/cantidad/dirección;
- tasa de completitud del Flow y abandono por pantalla/campo;
- mediana y percentil 90 del tiempo desde la primera intención accionable hasta pedido aceptado;
- porcentaje resuelto sin intervención humana, segmentado por tipo de operación;
- devoluciones/reclamaciones relacionadas con confirmación insuficiente;
- porcentaje de mensajes enviados como plantillas de marketing cuando no se produjo una compra: vigilar y evitar presión innecesaria.

**Función objetivo:** minimizar el costo total por pedido correcto y cumplible, no minimizar mensajes a cualquier precio. Una compra incorrecta, un descuento no autorizado o un envío al domicilio equivocado puede costar mucho más que un mensaje adicional bien utilizado.

## 20. Qué es WhatsApp Flows y cómo debe utilizarse

“WhatsApp Flows” es el nombre de la funcionalidad nativa de formularios y pantallas interactivas dentro de WhatsApp. No debe confundirse con Webflow, que es un producto distinto para crear sitios web. Para esta propuesta se entiende que la opción solicitada es WhatsApp Flows.

Meta describe Flows como una experiencia de pantallas y campos que el cliente completa sin salir del chat. Puede agrupar desplegables, opciones, texto, casillas y selectores de fecha. Las respuestas pueden regresar al negocio mediante webhooks y, para interacciones dinámicas, a través de un endpoint de Flow.

Referencias:
- [WhatsApp Flows — página oficial de Meta](https://whatsappbusiness.com/es-la/products/whatsapp-flows/)
- [Meta: experiencias de negocio dentro de WhatsApp](https://about.fb.com/news/2023/09/whatsapp-new-experiences-for-people-and-businesses/)
- [Referencia técnica de WhatsApp Flows y sus componentes](https://www.360dialog.com/docs/messaging/flows.md)
- [Referencia del formato de envío/recepción de Flows](https://www.twilio.com/docs/content/whatsapp-flows)

### 20.1 Qué mejora un Flow

- Agrupa varias preguntas en una interfaz estructurada, en vez de requerir una respuesta por pregunta.
- Ofrece opciones claras para retiro/envío, punto de retiro, rango/fecha, cantidades o preferencias admitidas.
- Reduce errores de escritura en campos que se pueden elegir de una lista.
- Permite mostrar un resumen antes de enviar.
- Puede pedir solamente los campos faltantes para una operación.
- Es útil para personas que no conocen los comandos del bot o no desean redactar mensajes largos.
- Puede ser más rápido en procesos estructurados y repetibles como elección de entrega o configuración de una compra.

### 20.2 Qué no hace un Flow

- No vuelve gratis los mensajes que lo abren o lo acompañan.
- No garantiza que cada cliente tarde menos de un minuto.
- No sustituye las APIs transaccionales, la cotización del backend ni la reserva.
- No debe contener una lista completa de catálogo si la selección nativa de productos/cart ya resuelve mejor esa tarea.
- No es la solución ideal para toda pregunta abierta, reclamo o excepción.
- No convierte automáticamente sus campos en consentimiento válido para una obligación económica si el resumen y el acto de confirmación no son explícitos.
- No debe recoger tarjetas bancarias ni información de pago sensible que la modalidad/canal no esté diseñado para procesar con seguridad.
- No se debe confiar en campos enviados por el cliente/Flow sin validar identificadores, importes, disponibilidad, tiempos y permisos en Inventary.

**Regla de selección:** utilizar WhatsApp Flows para datos estructurados; usar catálogo/carrito nativo para elegir productos cuando esté disponible; usar texto libre para aclaraciones excepcionales y para atención humana. La combinación adecuada reduce fricción sin forzar todos los casos en un formulario único.

## 21. Estrategia de compra en menos de un minuto

“Comprar en menos de un minuto” se define como **objetivo de experiencia para el camino sencillo**, no como garantía para todos los pedidos. El tiempo se medirá desde que el cliente expresa una intención de compra clara hasta que el backend ha aceptado el pedido y devuelve el número/estado definitivo.

Excluir del objetivo los casos que, por su propia naturaleza, requieren revisión: dirección ambigua, excepción de descuento, crédito, transferencia sin verificar, producto no identificable, stock cambiado, multiubicación, documento fiscal especial o atención humana. Estos escenarios deben resolverse de forma transparente, no apresurando al cliente a aceptar algo incorrecto.

### 21.1 El camino rápido ideal

**Caso A — El cliente ya sabe qué quiere**
1. El cliente envía SKU/nombre conocido o abre un producto del catálogo.
2. El sistema obtiene la ficha vigente de Inventary; si la intención identifica exactamente el producto, no pregunta “¿qué producto buscas?” otra vez.
3. El cliente elige cantidad en el catálogo/carrito nativo o en un Flow corto.
4. Se muestra una única opción claramente seleccionable para retiro/envío según disponibilidad y los campos realmente necesarios.
5. El backend cotiza precio/stock y calcula el total real.
6. El cliente revisa el resumen final y confirma explícitamente.
7. Inventary crea un pedido idempotente y devuelve el número.
8. n8n envía una confirmación breve con número, total, modo de recepción y siguiente paso.

El objetivo es eliminar mensajes de cortesía vacíos, preguntar una sola cosa por turno en texto libre y agrupar datos predecibles, sin eliminar la confirmación final del pedido y del importe.

### 21.2 Ruta con catálogo de WhatsApp

Si la empresa tiene conectado el catálogo de Commerce Manager a WhatsApp:
1. Ofrecer “Ver catálogo” o un mensaje de producto/lista; limitar la recomendación conversacional a pocas opciones cuando el usuario pide ayuda, en vez de enviar todo el catálogo.
2. Dejar que el cliente elija artículos y cantidades y remita el carrito nativo en una sola acción.
3. Recibir el evento de carrito/orden del canal y convertirlo en una solicitud de cotización de Inventary.
4. Volver a validar cada producto, variante, precio y stock; el carrito de Meta es una intención, no una venta ni una reserva.
5. Pedir el mínimo de datos que aún falten para cumplir el pedido y presentar una cotización de servidor.
6. Obtener aceptación explícita del total/modalidad y crear el pedido.

**Dependencia de backend:** debe existir una sincronización segura entre el catálogo maestra de Inventary y el catálogo de Meta. No mantener nombres/precios/imagenes de forma manual en dos sistemas. Inventary es fuente de verdad; Meta Commerce Manager es una proyección/publicación del catálogo. Cada elemento debe tener un mapping estable, normalmente SKU/retailer ID y el ID interno de producto/variante cuando corresponda.

La sincronización debe:
- publicar solo productos elegibles y datos aprobados;
- propagar bajas/cambios de precio/imágenes;
- preservar el vínculo SKU/variant/product;
- registrar errores de sincronización;
- tratar stock de Meta como informativo y validar el inventario real al cotizar;
- no publicar precios/costos internos ni atributos privados;
- detectar que un artículo del carrito nativo se volvió inactivo o cambió de precio.

Referencia de patrón de compra nativa:
- [WhatsApp: cómo el cliente agrega productos al carrito y envía el pedido](https://faq.whatsapp.com/5495780857144067/?cms_platform=android)
- [Mensajes de catálogo, producto y lista de productos](https://developers.cm.com/messaging/docs/whatsapp-catalogue-product-messages)

### 21.3 Ruta con WhatsApp Flow de checkout

Usar el Flow para recopilar todos los datos que puedan elegirse sin una conversación separada. Una primera versión podría tener dos o tres pantallas cortas:

**Pantalla 1 — Cómo recibirás el pedido**
- Retirar en un punto disponible o solicitar envío.
- Para retiro, elegir punto de retiro habilitado.
- Para envío, elegir o capturar dirección solo si no existe una dirección elegida/verificada previamente.
- Mostrar únicamente opciones que la API sabe servir.

**Pantalla 2 — Datos faltantes y pago**
- Presentar los datos conocidos para confirmar/corregir, no volver a pedirlos todos.
- Elegir condición de pago entre las permitidas para ese pedido. Si el backend solo soporta prepago/confirmación de pago manual, no mostrar contra entrega o crédito como si ya existieran.
- Seleccionar una fecha/ventana solo cuando el backend exponga opciones reales; no aceptar fechas libres como promesa de entrega.
- Opción explícita “Necesito ayuda” para salir del Flow y pasar a una persona.

**Pantalla 3 — Resumen/confirmación (si la integración dinámica lo permite)**
- Resumen de artículos y cantidades.
- Precio y total devueltos por Inventary, descuento/envío/impuestos aplicables.
- Punto/dirección, modalidad y condición de pago.
- Condiciones necesarias para aceptar y acción explícita de confirmar el pedido.

Después del envío, el backend debe volver a validar la cotización y la disponibilidad. Si el monto ha cambiado, no ejecutar un alta silenciosa: mostrar el cambio y requerir aceptación nueva.

Para ofrecer el total autoritativo dentro del Flow antes de confirmarlo, se necesita un endpoint de datos de Flow seguro y de baja latencia, integrado con el servicio de cotización de Inventary. No se debe confiar en que una pantalla estática con valores hardcodeados refleje precio y stock. Si ese endpoint no está disponible en la primera versión, el Flow recogerá las opciones y el backend devolverá la cotización; el cliente confirmará el resumen final mediante el mecanismo interactivo aprobado.

**No forzar tres pantallas para todos:** una compra sencilla de un único producto puede usar una confirmación/resumen más corto; pedidos complejos usan el Flow con más campos, pero solo los necesarios.

## 22. Diseño para distintas edades y experiencias digitales

No tratar la edad como predicción de capacidad. Diseñar un camino accesible y corto para todos, con una alternativa en texto para quien no quiera usar un formulario.

### 22.1 Principios de interacción universal

- Un objetivo principal por pantalla.
- Botones con verbos concretos: “Ver productos”, “Elegir retiro”, “Enviar pedido”, “Confirmar compra”.
- Frases breves, palabras de uso común y montos con moneda explícita.
- Mostrar imágenes y nombre de producto, pero nunca depender solo de la imagen para identificarlo.
- Mostrar variantes, cantidades y precios con etiquetas completas, no solo códigos.
- Limitar las alternativas simultáneas a un número que ayude a elegir; ofrecer “Ver más opciones” en vez de saturar.
- Mantener “Atrás”, “Cambiar” y “Hablar con una persona” visibles donde corresponda.
- No borrar el carrito si el cliente vuelve atrás o necesita ayuda.
- Prefill de datos ya verificados, con oportunidad de corregirlos.
- No usar jerga como “SKU”, “fulfillment”, “token” o “webhook” en el mensaje al cliente.
- No usar un temporizador de cuenta regresiva agresivo; comunicar la vigencia real de la cotización sin presión artificial.
- Confirmar el pedido y el total con claridad; no ocultar cargos en una pantalla anterior.

### 22.2 Tres perfiles de experiencia que deben probarse

| Perfil de tarea | Preferencia de interfaz | Camino recomendado |
|---|---|---|
| Compra rápida/repetida | Ya conoce producto y cantidad, quiere terminar rápido | Atajo a producto frecuente/SKU, carrito pequeño, retiro predeterminado si sigue vigente, resumen y confirmación. |
| Compra asistida | No conoce SKU, compara opciones o necesita recomendación | Texto libre asistido por búsqueda, máximo 3–5 opciones iniciales basadas en datos, detalle del producto, opción de ayuda y checkout Flow opcional. |
| Compra cuidadosa o de baja familiaridad digital | Quiere revisar detalles, no sabe usar catálogo o prefiere escribir | Instrucciones paso a paso, una pregunta por vez, Flow con etiquetas simples o alternativa en texto, resumen legible y posibilidad de regresar/consultar a una persona. |

No asumir que todos los adultos mayores necesitan un flujo lento ni que todos los jóvenes prefieren un bot autónomo. La UI debe permitir seleccionar el modo, no inferirlo ni etiquetar al usuario.

### 22.3 Accesibilidad funcional

- Compatibilidad con TalkBack/VoiceOver y lectores de pantalla en el cliente móvil.
- Navegación predecible, labels descriptivos, contraste y foco visibles.
- Evitar microtexto, códigos crípticos, opciones que solo se distinguen por color o acciones escondidas.
- Si el Flow no está disponible o falla en un dispositivo/cuenta, fallback inmediato al mismo formulario por chat con opciones y mensajes compactos.
- No dar por hecho que todos usan WhatsApp Web/desktop para completar Flows; comprobar la compatibilidad del cliente y ofrecer alternativa.
- Probar en Android/iOS de gama media/baja, distintos tamaños de letra, mala conexión y usuario que interrumpe/retoma la compra.

## 23. Estrategia de mensajes: menos mensajes, sin quitar información esencial

### 23.1 Guion de referencia para una compra simple

El sistema debe evitar enviar automáticamente “Hola”, “¿en qué puedo ayudarte?”, “perfecto”, “un momento”, “gracias”, cada uno como mensaje separado, si el cliente ya dio una instrucción clara. Concatenar en un único mensaje lo que sea útil y no resulte un párrafo difícil de leer.

Ejemplo de entrada de cliente:
“Necesito 2 unidades del cargador X, para retirar hoy.”

Respuesta agrupada del sistema:
- Producto validado y cantidad interpretada.
- Si hay stock y puntos habilitados, presentar una selección simple de retiro.
- Si hay una sola opción válida, no preguntar algo que la API ya determina.
- Generar cotización real.
- Mostrar total y condición de pago.
- Pedir confirmación del resumen.

No se deben enviar mensajes separados para declarar “buscando producto”, “encontrado”, “revisando precio”, “stock revisado” o “calculando total”. Son pasos técnicos que n8n registra internamente, no contenido para el cliente.

### 23.2 Qué preguntas agrupar y cuáles mantener separadas

**Agrupar en Flow cuando sean campos estructurados y conocidos:**
- retiro o envío;
- ubicación de retiro disponible;
- dirección/entrega cuando sea necesaria;
- preferencia de ventana real, si se soporta;
- datos faltantes de contacto/entrega;
- condición de pago que esté habilitada.

**Mantener una pregunta breve de texto cuando:**
- el producto o variante no se identifica;
- hay conflicto entre mensajes previos;
- el cliente solicita otra cosa o explica una excepción;
- el artículo exige compatibilidad no registrada;
- hay una reclamación que requiere empatía, contextualización y persona.

**No agrupar por comodidad datos sensibles o económicos** si el Flow/proveedor no es adecuado para ellos. No pedir en el mismo formulario más datos personales de los que el pedido necesita.

### 23.3 Respuesta consolidada de cotización

El mensaje/resumen de cotización debe incluir todo lo que pueda cambiar la decisión del cliente:
- nombre corto e identificador del producto, cantidad y variante;
- precio/descuento autorizado;
- tarifa de envío y cargo adicional si aplica;
- impuestos/cargos calculados por backend;
- total y moneda;
- punto/destino;
- condición de pago;
- validez de cotización y siguiente paso.

La confirmación se solicita una sola vez sobre esa versión del resumen. Si el cliente modifica una línea o modalidad, vuelve a cotizarse solo lo que cambió y se muestra el nuevo total. No reenviar el catálogo entero ni la misma dirección varias veces.

## 24. Selección de canal según caso: Flow, catálogo, texto o sitio web

| Necesidad | Opción preferida | Motivo y límite |
|---|---|---|
| Buscar un artículo exacto | Consulta del catálogo de Inventary y respuesta breve | Evita un formulario innecesario; la respuesta debe usar datos actuales. |
| Explorar productos físicamente en WhatsApp | Catálogo y carrito nativos, si la sincronización Meta está implementada | El cliente elige artículos y cantidades dentro de WhatsApp. El carrito recibido aún debe cotizarse/validarse en Inventary. |
| Elegir retiro/envío, sede o información estructurada | WhatsApp Flow | Agrupa campos en pocas pantallas y evita varios mensajes de pregunta/respuesta. |
| Repetir una compra conocida | Atajo de recompra o artículo conocido con revisión de total | Reduce pasos, pero no omite validar precio/stock/confirmación. |
| Pedir recomendación abierta | Texto + búsqueda semántica/reglas; Flow solo después de identificar opciones | No convertir una conversación exploratoria en un formulario rígido demasiado pronto. |
| Reclamo, error de pago, excepción o producto no identificable | Mensaje breve y handoff humano | Evita respuestas de IA que podrían agravar el problema o generar cargos indebidos. |
| Producto con gran cantidad de atributos técnicos | Detalle estructurado o página web accesible, más resumen WhatsApp | Un Flow no sustituye una ficha técnica larga. Enlazar solo a una URL segura y móvil. |
| Selección logística o dirección extensa | Flow con lista de ubicaciones/validación en backend | Evita texto libre ambiguo; la promesa/selección final sigue validándose en API. |

WhatsApp Flows no debe ser el único camino; el checkout debe tener fallback por chat y, si la ficha requiere mucha información, la posibilidad de abrir una web móvil. Un sitio externo puede mejorar la comparación, pero también introduce salida de WhatsApp y no garantiza menor costo; la decisión se mide.

## 25. Arquitectura técnica necesaria para Flows

### 25.1 Backend Flow endpoint

Los Flows dinámicos pueden consultar datos y enviar respuestas entre pantallas. El endpoint de datos debe ser un adaptador dedicado, seguro y de latencia predecible que aplique las reglas de negocio en Inventary.

Debe:
- validar autenticidad/cifrado del payload conforme al protocolo Flow vigente;
- resolver el contexto de la sesión y Flow token sin confiar en valores del cliente;
- devolver solo el catálogo/opciones que esa pantalla necesita;
- consultar disponibilidad/precio a servicios de dominio, no leer o modificar stock desde n8n;
- validar cada productId/variantId contra los elegibles;
- generar una cotización de servidor y un ID de versión;
- rechazar entradas inválidas y expiraciones con mensajes que permitan al cliente corregir;
- evitar operaciones no idempotentes durante eventos intermedios de navegación;
- registrar correlationId/flowId sin persistir campos personales innecesarios;
- aplicar timeout, rate limit, versionado y pruebas del formato de cifrado.

No construir lógica comercial crítica en el JSON del Flow. El JSON presenta campos y pantallas; el backend decide los resultados.

### 25.2 Papel de n8n en el Flow

n8n debe:
- enviar el mensaje/CTA que abre el Flow;
- conservar el correlativo del mensaje enviado y la relación con conversación/carrito;
- recibir el webhook de envío/completado;
- validar que la finalización corresponde al Flow/pedido esperado;
- normalizar los campos con versión de esquema;
- llamar al backend con una solicitud idempotente para guardar/actualizar la intención/cotización;
- recibir el resultado real y enviar una única confirmación/resumen final;
- derivar a una persona si el Flow fue abandonado, la API falla o los datos no se pueden validar.

Para intercambios de datos en tiempo real, no hacer que el endpoint de Flow dependa de una cadena larga de nodos n8n con LLM en el camino crítico. El endpoint dedicado de backend debe responder rápido; n8n coordina la operación asíncrona posterior.

### 25.3 Versionado y despliegue

- Versionar los JSON de Flow, templates/CTA, versiones de API y workflow n8n que consumen sus respuestas.
- Separar Flow de pruebas y Flow de producción.
- Mantener una tabla de compatibilidad: flowId/name, version, backend schema, workflow, estado de publicación y fecha de salida.
- Probar Flow antiguo con webhook tardío; si una versión queda retirada, devolver un mensaje claro y un fallback a chat, no aceptar un formulario incompatible.
- Nunca guardar tokens/keys en el JSON exportado.
- Medir tasa de apertura, finalización, abandono y errores por versión y pantalla.
- Actualizar el catálogo o tarifas siempre a través de APIs, no editando JSON del Flow con precios constantes.

## 26. Diseño de checkout con cotización viva y confirmación segura

Para una experiencia rápida no es necesario convertir cada pantalla en una conversación, pero sí separar “capturar preferencias” de “aceptar un importe concreto”.

### Fase A — Recopilar

Flow/catálogo reúne producto(s), cantidad(es) y los datos operativos mínimos. Las selecciones se consideran intención, no venta.

### Fase B — Cotizar

Backend valida:
- producto activo y elegible;
- precio vigente y moneda;
- cantidad disponible;
- descuentos autorizados;
- impuestos/cargos aplicables;
- modalidad y destino;
- coste/ventana logística, si están implementados;
- condición de pago habilitada;
- vigencia del precio.

Backend guarda la versión de cotización y devuelve el resumen. No reservar stock a largo plazo solo por abrir el Flow.

### Fase C — Aceptar

El resumen final, ya con datos del servidor, debe pedir aceptación explícita del total y condiciones. Guardar la versión de cotización, timestamp e ID verificado de la respuesta/Flow cuando el proveedor lo permita.

Si la cotización cambia entre la pantalla del Flow y la creación del pedido, el backend devuelve conflicto/cambio; se informa al cliente y se solicita nueva aceptación. No se acepta una casilla o texto generado por IA como consentimiento de un total que no se presentó al cliente.

### Fase D — Crear una sola orden

Crear el pedido con Idempotency-Key por intento lógico y relacionarlo con:
- messageId de entrada;
- flowId/version y submissionId si existen;
- quoteId/version;
- customerRef derivado de la identidad verificada;
- modalidad seleccionada;
- respuesta final del backend.

Un webhook duplicado o un timeout devuelve el mismo pedido; no crea otra orden ni otra reserva.

### Fase E — Confirmar resultado

Enviar un mensaje compacto con número de pedido, total, modo de recepción y siguiente paso. La respuesta no dice “pagado”, “listo” o “despachado” salvo que el backend tenga esos estados.

## 27. Recompra, preguntas frecuentes y compradores recurrentes

La recompra ofrece gran potencial de reducir pasos, siempre que sea segura y el artículo no haya cambiado:

- Detectar que el cliente se refiere a una compra previa por historial verificable y permisos, no por inferencia libre.
- Proponer “Repetir el pedido anterior” con resumen; nunca ejecutar automáticamente por una frase ambigua.
- Volver a consultar precio, activo, variantes y disponibilidad.
- Volver a preguntar únicamente el dato que probablemente cambió (p. ej., modalidad/dirección), prellenando lo que sigue válido.
- Mostrar nuevo total y pedir confirmación.
- Si se ha cambiado el producto, el precio, la modalidad o la dirección, abandonar el atajo y usar el flujo normal.

No usar historial de compras para enviar campañas de marketing sin consentimiento apropiado. La recompra debe ser una función iniciada/esperada por el cliente.

## 28. Ajustes al modelo de datos y telemetría para medir la experiencia rápida

Añadir o consolidar un registro de interacción que vincule:
- conversationId, wa_id/customerRef protegido y messageId;
- tipo de entrada: texto, producto, carrito, Flow, catálogo o humano;
- flowId/version/screenId y evento de submit/cancel;
- quoteId/version y duración de validez;
- orden creada y su resultado;
- mensajes de salida, categoría, delivery status y costo/tarifa estimados;
- latencia de backend y n8n;
- punto de abandono y motivo conocido;
- aclaraciones, modificaciones y handoff humano.

Mantener los eventos de producto (impresión, clic, selección) diferenciados de la compra. No inferir que se mostró una lista completa si no hay evidencia de que el mensaje llegó. Evitar almacenar texto completo y dirección en telemetría analítica cuando bastan referencias opacas.

Definir un baseline de dos semanas de uso o una prueba con usuarios simulados antes de fijar metas de reducción de costos. Si aún no hay tráfico suficiente, medir con una batería de tareas representativas y etiquetar el resultado como baseline técnico, no como evidencia comercial.

## 29. Metas y criterios de aceptación del flujo rápido

Los valores siguientes son objetivos a validar, no resultados ya medidos:

- En una compra simple de producto conocido, objetivo mediano de **menos de 60 segundos** desde la intención accionable hasta pedido aceptado por backend.
- No más de 2 mensajes salientes de negocio antes de la aceptación en la ruta rápida, salvo que se necesite una pantalla Flow/CTA independiente, haya cambio de cotización o exista una pregunta obligatoria.
- Una respuesta consolidada de cotización/resumen que presente los datos esenciales antes de aceptar.
- Cero duplicados de pedido/venta/reserva en pruebas de reintento.
- Cero pedidos creados con producto/cantidad/total distinto del resumen aceptado.
- Cero falsos mensajes de “pagado”, “listo”, “despachado” o “entregado”.
- Un camino accesible alternativo al Flow que permite completar la operación o solicitar atención humana.
- Separar abandono por elección (cliente deja de responder) de errores del sistema y de la pérdida de ventana de mensajería.
- Medir p50 y p90; no optimizar únicamente la mediana mientras un grupo de usuarios quede bloqueado.
- Medir costo de Meta y proveedor por pedido completado, separado del coste de IA/hosting.

### Escenarios que deben probarse

1. Cliente escribe el SKU/nombre exacto y cantidad.
2. Cliente escribe una descripción vaga; la IA ofrece hasta 3–5 alternativas verificadas.
3. Cliente usa catálogo nativo y manda carrito.
4. Cliente abre Flow, completa una pantalla y abandona; puede volver sin perder todo.
5. Cliente mayor o poco familiarizado con formularios prefiere responder por texto; el fallback funciona.
6. Cliente usa lector de pantalla o tamaño de fuente grande.
7. Cliente modifica cantidad en Flow y el precio cambia.
8. Un producto quedó inactivo entre abrir el catálogo y enviar el carrito.
9. Variante que el carrito actual no soporta: no se vende como producto padre por equivocación.
10. Cotización vencida justo antes de confirmar.
11. Cliente selecciona envío pero falta dirección; solo se pregunta ese dato.
12. Cliente selecciona una sede cerrada o no elegible; la API elimina esa opción y solicita alternativa.
13. Cliente escribe que va en camino, pero la preparación no está lista.
14. Pedido nocturno sin capacidad inmediata; no se promete entrega/retirada.
15. Contra entrega o pago al retirar no está soportado: no mostrarlo como opción viable.
16. Dos envíos de Flow/webhook para la misma submission; solo una operación.
17. Flow responde con un campo inesperado/versión antigua.
18. Webhook de proveedor falla tras creación; el workflow consulta el resultado antes de repetir.
19. Un mensaje de utilidad o marketing se clasifica mal; alertar/no enviar hasta resolver.
20. Precio o stock cambió después de que el cliente revisó el resumen: exige nueva aceptación.
21. Servicio Meta, BSP o IA tiene un fallo; la experiencia recurre a un camino alternativo sin duplicar.
22. Cliente desea comparar productos con explicación larga; el sistema no lo obliga a terminar en 60 segundos.
23. Cliente pide un humano durante cualquier pantalla.
24. Medir costo de mensajes y tiempo total, además de conversión y errores.

## 30. Plan de implementación actualizado por prioridad

### P0 — Medir y definir el modelo de precios de mensajería

- Confirmar el tarifario, la moneda y las categorías en la cuenta WABA que se desplegará; documentar la fecha de verificación.
- Confirmar la tarifa del proveedor/BSP, si existe.
- Añadir instrumentación de mensajes enviados/entregados/categoría y cálculo de costo estimado.
- Definir quién aprueba campañas y plantillas marketing para evitar envíos caros e innecesarios.

**Razón:** sin una base real no se puede afirmar cuánto ahorra un Flow ni estimar costo por pedido.

### P1 — Checkout rápido sin Flow complejo

- Evitar mensajes de “progreso” que no aportan información.
- Soportar la intención de producto conocido y búsqueda de máximo 3–5 alternativas útiles.
- Reutilizar carrito, cotización y pedido actuales con idempotencia.
- Consolidar resumen/aceptación.
- Añadir pruebas de timeout, producto inactivo y cambio de precio.

**Razón:** mejora la velocidad sin esperar primero una integración dinámica de Flows.

### P1 — Integración de catálogo/carrito nativo de WhatsApp (si es conveniente para el negocio)

- Diseñar el vínculo fuente de verdad Inventary → Meta Catalog, sincronización y mapping de SKU/variantes.
- Recibir el carrito del proveedor como una solicitud; volver a validar y cotizar en Inventary.
- No tomar el stock/precio cacheado por Meta como un compromiso transaccional.
- Medir mensajes, tiempo, abandono y errores frente a la ruta de texto.

**Dependencia:** cuenta WABA, catálogo Commerce Manager conectado, claves/product IDs y la integración de webhooks correspondientes.

### P1 — WhatsApp Flow de retiro/envío y datos estructurados

- Diseñar la primera versión con pocos campos y dos o tres pantallas cortas.
- Crear un endpoint dinámico seguro y de baja latencia cuando el precio/opciones deban cambiar en tiempo real.
- Enviar el Flow por el mecanismo permitido para la ventana/política vigente.
- Recibir submission, validar, cotizar y pedir/registrar aceptación de la versión real.
- Implementar fallback por texto y métrica de finalización/abandono.

**Dependencia:** Flows publicados/aprobados, protocolo de cifrado del endpoint, payloads de prueba y contrato backend de fulfillment/cotización.

### P2 — Checkout con condiciones avanzadas

Habilitar solo después de que backend soporte realmente descuentos en cotización, pagos por modalidad, crédito autorizado, direcciones, tarifas, preparación/entrega y suscripciones. No colocar opciones en el Flow antes de que exista su regla de servidor.

### P2 — Optimización continua

- Experimentar por separado con Flow versus texto y catálogo nativo versus búsqueda conversacional.
- Comparar tasa de pedido correcto/completado, tiempo p50/p90, mensajes por compra, coste, error de stock/pago, reclamaciones y accesibilidad.
- Segmentar por tarea, dispositivo y modo de entrada, no crear perfiles psicológicos.
- Mantener control de versión y rollback del Flow/plantillas/workflows.

## 31. Referencias para esta revisión

### Meta y WhatsApp
- [WhatsApp Business Platform — precios oficiales](https://whatsappbusiness.com/products/platform-pricing/)
- [WhatsApp Flows — producto oficial](https://whatsappbusiness.com/es-la/products/whatsapp-flows/)
- [Meta Newsroom — Flows para compras y servicios dentro de WhatsApp](https://about.fb.com/news/2023/09/whatsapp-new-experiences-for-people-and-businesses/)
- [WhatsApp — cómo utilizar el carrito de compra](https://faq.whatsapp.com/5495780857144067/?cms_platform=android)
- [WhatsApp Flows: especificación de referencia](https://www.360dialog.com/docs/messaging/flows.md)
- [WhatsApp Flows: envío/recepción técnica](https://www.twilio.com/docs/content/whatsapp-flows)
- [WhatsApp precios en Latinoamérica, rate card referido para octubre de 2026](https://contactship.ai/en/blog/whatsapp-business-api-pricing-latin-america-2026)
- [Precios WhatsApp API para Ecuador y el resto de Latinoamérica](https://contactship.ai/blog/precios-whatsapp-business-api-resto-latinoamerica-2026)

### Inventary
- [Arquitectura n8n/backend V2](PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md)
- [Propuesta UX/UI de Pedidos WhatsApp V2](PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md)
- [Propuesta UX/UI de Productos V2](PROPUESTA_UX_PRODUCTOS_V2.md)
- [Propuesta UX/UI de Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Propuesta de Suscripciones](PROPUESTA_UX_SUSCRIPCIONES.md)
- [Propuesta logística de retiro y envío](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)
- [Referencia actual de APIs](microservices-inventary-ecuapp/docs/25-referencia-apis.md)

## 32. Dictamen

Inventary debe diseñar WhatsApp como un canal de compra optimizado, no como un chat que hace una pregunta por cada campo. El camino de referencia es catálogo/carrito nativo cuando el cliente quiere explorar productos, Flow para capturar en pocas pantallas los datos estructurados de cumplimiento y pago permitidos, y texto/IA para las dudas que de verdad necesitan conversación. n8n coordina; el endpoint de datos de Flow y la API de Inventary entregan opciones/precios seguros; el backend valida la cotización, registra la aceptación y crea el pedido una sola vez.

El ahorro debe ser demostrable: menos mensajes salientes entregados por pedido correcto, menor costo total por venta, menor tiempo de finalización y sin aumento de errores, quejas, pedidos duplicados o pagos incorrectos. La compra en menos de un minuto se valida con usuarios y métricas reales; no se promete para excepciones, productos ambiguos ni operaciones que requieran aprobación.

Los formularios no deben convertirse en una barrera. El cliente puede usar el catálogo, elegir el Flow, responder por texto o hablar con una persona. El mejor flujo es el que consigue que el cliente complete la compra rápido y entienda exactamente lo que aceptó.





---

# Revisión V2.2 — componentes nativos de WhatsApp y compra con mínimo esfuerzo

**Fecha de revisión:** 28 de octubre de 2026. Esta sección amplía el diseño de compra rápida con un selector adaptativo de componentes; no implica que esas integraciones ya estén habilitadas en Inventary.

## 33. Aclaración: qué podría significar “WhatsApp View”

En la documentación oficial consultada no encontré una función de Meta cuyo nombre formal sea exactamente **WhatsApp View**. Si con ese nombre te refieres a mostrar una experiencia visual dentro del propio WhatsApp, las piezas documentadas relevantes son el catálogo y carrito nativos, mensajes de producto, botones, listas interactivas, WhatsApp Flows, CTA a una página web y mensajes de ubicación/contacto. Debe confirmarse el nombre técnico si “WhatsApp View” se refiere a un componente de un BSP específico.

No confundirlo con **View Once**: eso es contenido multimedia que desaparece después de abrirse, no una interfaz de compra. No utilizarlo para recibos, políticas, confirmaciones o información que el cliente debe poder volver a consultar.

Fuentes:
- [Meta — características de WhatsApp Business Platform](https://whatsappbusiness.com/products/business-platform-features/)
- [Meta — WhatsApp Flows](https://whatsappbusiness.com/products/whatsapp-flows/)
- [SDK de Cloud API — mensajes interactivos](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/messages/interactive/) (documentación del SDK archivada; validar límites contra la versión de Cloud API/BSP desplegada)
- [WhatsApp Help Center — carrito de compra](https://faq.whatsapp.com/5495780857144067/?cms_platform=android)
- [WhatsApp Business Calling API](https://whatsappbusiness.com/blog/whatsapp-business-calling-api/)
- [WhatsApp Help Center — View Once](https://faq.whatsapp.com/578442220724722/?cms_platform=android)

## 34. Biblioteca de componentes: qué usar y cuándo

La regla es **usar el componente más simple que permita completar bien la tarea**. No enviar una secuencia de textos cuando un botón o Flow recoge la elección en una acción; tampoco abrir un formulario cuando basta responder una pregunta puntual.

| Componente | Usarlo para | Beneficio | Condición/límite |
|---|---|---|---|
| Botones de respuesta | Sí/no, retirar/enviar, confirmar/cambiar, ayuda/humano | Una acción clara por toque | Los mensajes de botón admiten un conjunto pequeño de opciones; validar soporte y ventana activa |
| Lista interactiva | Elegir sede, categoría, motivo o una opción entre varias | No pedir que el cliente escriba el nombre exacto | Mostrar solo opciones del backend que realmente se puedan usar |
| Mensaje de producto único | Pregunta por un artículo exacto | La ficha nativa contiene nombre e imagen del catálogo | Requiere catálogo Meta conectado y mapping estable |
| Mensaje multi-producto | Mostrar pocas alternativas comparables | Seleccionar artículos dentro del chat con menos mensajes | Precio, variante y stock se vuelven a validar en Inventary |
| Catálogo + carrito nativos | Explorar y elegir varios productos y cantidades | El cliente prepara y envía el carrito en una acción | Requiere catálogo sincronizado; carrito enviado no equivale a venta ni reserva |
| WhatsApp Flow estático | Varios datos estructurados relativamente constantes | Agrupa campos en pocas pantallas | No debe usar precio/stock hardcodeados ni ejecutar transacciones intermedias |
| WhatsApp Flow dinámico | Opciones de sede, disponibilidad, tarifas o datos que cambian en tiempo real | Pantallas ajustadas a los resultados del backend | Requiere un endpoint de Flow rápido, cifrado/autenticado y versionado |
| CTA URL | Ficha técnica extensa, comparación, mapa o sitio de ayuda | Evita mensajes excesivamente largos | La URL debe ser móvil, HTTPS y conservar el contexto del carrito; el cambio de app puede añadir fricción |
| Solicitud o envío de ubicación | Ayudar con el destino o compartir el pin de una sede | Menos errores de escritura | Un pin no siempre es una dirección postal completa ni debe guardarse automáticamente |
| Mensaje de contacto | Compartir el contacto del local o de soporte | Facilita ayuda sin buscar el número manualmente | Enviar solo cuando aporte valor, no como mensaje redundante |
| Llamada de WhatsApp consentida | Reclamo complejo o cliente que pide hablar | Resuelve casos que por texto consumirían muchos turnos | Solo si la cuenta, el país, el BSP, el horario y el personal admiten la función |
| Plantilla de utilidad aprobada | Actualización transaccional fuera de la ventana cuando la política la exige | Comunicación consistente y autorizada | No asumir gratuidad; no mezclar con promociones |
| Plantilla de marketing | Campaña con consentimiento apropiado | Permite iniciativas comerciales autorizadas | Puede tener coste mayor y límites propios; no disfrazar marketing como utilidad |
| IA en texto/audio | Consulta abierta, búsqueda, resumen, traducción o extracción tentativa | Reduce el esfuerzo de redactar | No autoriza precio, stock, descuentos, pago o consentimiento |
| Web móvil de Inventary | Comparaciones complejas y muchas especificaciones | Permite una ficha accesible y extensa | Usarla solo cuando ayude más que la vista nativa |

Los límites de filas, botones, productos y plantillas pueden cambiar según versión de Cloud API, región y proveedor. Verificarlos antes de publicar el Flow o workflow. No codificar límites asumidos como reglas comerciales.

## 35. Selector adaptativo de interacción en n8n

Crear un subworkflow reutilizable, por ejemplo N8N-15 Interaction Planner, para escoger una única ruta de interfaz y evitar que el agente mande botones, después una lista idéntica y luego repita lo mismo en texto.

### Entrada

El selector recibe:
- intención y nivel de certeza;
- si hay producto/SKU/variante exactos;
- cantidad de productos y campos necesarios;
- si el catálogo de Meta está conectado y sincronizado;
- disponibilidad real de Flows y endpoint dinámico;
- componentes que el proveedor/BSP y el número de WhatsApp soportan;
- estado de la ventana de atención y si se requiere una plantilla permitida;
- datos ya capturados y confirmados, que no deben volverse a preguntar;
- preferencia explícita del cliente: texto, Flow, ayuda o persona;
- estado de cotización, precio, disponibilidad y modalidad devueltos por Inventary.

### Salida estructurada

- modo elegido: botones, lista, mensaje de producto, catálogo/carrito, Flow estático, Flow dinámico, texto, URL o atención humana;
- razón de selección;
- campos faltantes y ya conocidos;
- acción de backend que debe ejecutarse;
- necesidad de confirmación explícita;
- alternativa en caso de que el componente falle;
- requisito de plantilla según política vigente;
- presupuesto interno de mensajes por tarea;
- responsable/handoff si aplica.

La decisión simple de componente se resuelve con reglas de n8n, no llamando a la IA. El LLM se usa para interpretar lenguaje natural ambiguo, no para decidir la política de mensajes, calcular el costo ni resolver los permisos.

### Reglas por defecto

1. **Producto exacto y cantidad conocida:** consulta al backend, cotización inmediata y resumen compacto. No enviar una tarjeta adicional si no aporta información.
2. **Exploración de varios artículos:** catálogo/carrito nativos si están sincronizados; de lo contrario, búsqueda con pocas opciones iniciales.
3. **Dos o tres decisiones pequeñas:** botones de respuesta.
4. **Varias sedes o motivos:** lista interactiva con solo opciones elegibles del backend.
5. **Varios campos de entrega/retiro:** Flow si agrupa preguntas útiles sin obligar a completar datos irrelevantes.
6. **Opciones que cambian en tiempo real:** Flow dinámico únicamente si el endpoint está disponible y responde con la información validada.
7. **Compatibilidad desconocida o producto ambiguo:** una pregunta concreta o atención humana, no un formulario enorme.
8. **Pago disputado, reclamación o excepción comercial:** mensaje breve empático y handoff, no más automatización para evitar escuchar al cliente.
9. **Fuera de la ventana activa:** comprobar si se permite responder y si la política exige plantilla aprobada.
10. **Flow o catálogo no disponibles:** pasar al texto conservando los datos ya capturados y sin reiniciar la compra.

## 36. Flujos por escenario optimizados

### 36.1 Compra de un artículo conocido

1. Cliente: “Necesito 2 del SKU ABC123”.
2. n8n consulta catálogo/eligibilidad del backend; si el producto es inequívoco, no pregunta qué producto quiere.
3. Inventary valida precio, cantidad y modalidad/ubicación disponible.
4. Se presenta un solo resumen con producto, cantidad, total, retiro/envío disponible y vigencia.
5. Botones “Confirmar” y “Cambiar” si están soportados.
6. Al confirmar, Inventary vuelve a validar y crea el pedido idempotente.
7. n8n envía una confirmación concisa con el número y siguiente paso.

No enviar mensajes distintos para “buscando”, “consultando stock”, “calculando total” y “preparando respuesta”. Son eventos internos del workflow.

### 36.2 Explorar catálogo y comprar varias líneas

1. Mostrar el catálogo nativo o un mensaje multi-producto cuando el cliente desea explorar productos.
2. El cliente agrega productos y cantidades y envía su carrito.
3. El webhook de carrito se normaliza y se correlaciona con el remitente verificado.
4. n8n transforma las referencias públicas del catálogo en IDs internos mediante un mapping estable.
5. Inventary revalida cada línea, cantidad, precio, estado y variante; devuelve una cotización.
6. Si el precio o artículo cambió, mostrar solo las diferencias y una alternativa válida si existe.
7. Cliente confirma el total.
8. Inventary crea una orden única.

No convertir cada producto del carrito en una pregunta de texto individual. Un carrito nativo es una intención de compra, no una venta ni una reserva.

### 36.3 Retiro, entrega y selección de sede

1. Consultar al backend las modalidades elegibles para el pedido.
2. Si hay hasta tres opciones, usar botones; si hay más sedes/motivos, lista.
3. Si faltan varios campos, Flow con el mínimo necesario.
4. Flow dinámico solo para opciones actuales que dependan de sede, horario, tarifa o cobertura.
5. Backend calcula o valida tarifa y ventana.
6. Se presenta un resumen compacto con destino, costo y total; cliente confirma.

No pedir una dirección de envío cuando el cliente eligió retiro. No volver a preguntar la sede elegida. No prometer una hora que el backend no ha confirmado.

### 36.4 “Voy en camino”

- Detectar que se refiere a un pedido existente o a una nueva compra.
- Si existe pedido, vincular la prioridad a ese pedido; si no, usar el camino rápido para seleccionar/cotizar.
- Consultar el horario, la reserva y el estado de preparación.
- Solo afirmar “está listo” si la operación está marcada como lista en backend.
- De lo contrario, comunicar que se recibió la petición prioritaria y se confirmará cuando esté preparado.
- No enviar un Flow con dirección o datos de envío si pide retiro.

### 36.5 Consulta amplia o muchas preguntas

- Resolver con atributos reales del catálogo y una comparación concisa.
- Mostrar 3–5 alternativas iniciales cuando esto facilite la elección, sin limitar el catálogo global.
- Usar una ficha web o producto nativo para información larga.
- Mantener “Ver más opciones” y “Hablar con alguien”.
- No presionar para terminar en menos de un minuto cuando el cliente está explorando, no comprando.
- No inventar compatibilidad, garantía ni disponibilidad.

### 36.6 Pagos y suscripciones

- Mostrar solo condiciones de pago soportadas por backend.
- Comprobante: “recibido, pendiente de verificación”; OCR/IA puede extraer referencia, pero no certificar dinero.
- Pago al retirar, contra entrega o crédito se habilitan solo cuando Inventary los puede representar y auditar.
- WhatsApp Payments está documentado para algunos mercados concretos, como India; no asumir que está disponible para Ecuador. Elegir una pasarela compatible con Ecuador y validar callbacks.
- Para suscripciones, consultar los planes y calendario de una API de canal futura, mostrar términos y obtener una única aceptación. Los componentes UI no sustituyen el dominio de contratos, facturas y pagos.

## 37. Catálogo nativo conectado con Inventary

La experiencia nativa solo ayuda si los datos publicados en Meta reflejan lo que Inventary ofrece.

Crear un adaptador de publicación de catálogo y un workflow de sincronización separado del core de venta.

### Datos para sincronizar

- identificador estable de artículo publicado y retailer/product ID;
- productId interno y variantId cuando el backend tenga soporte completo de variante;
- título, descripción y atributos publicables;
- imagen HTTPS autorizada;
- precio y moneda publicables;
- estado de publicación/eligibilidad;
- fecha de última sincronización, versión y último error.

### Reglas

1. Publicar solo fichas activas, elegibles y con datos mínimos correctos.
2. Usar mapping estable; no emparejar solo por nombre.
3. Propagar cambios de precio, imagen y desactivaciones; evitar dos sistemas de mantenimiento manual.
4. Revalidar cada línea cuando se recibe el carrito; el precio/disponibilidad del catálogo Meta puede estar desactualizado.
5. Mantener disponibilidad global separada de “puede retirarse en este local ahora”.
6. Si el artículo se desactivó o cambió precio, mostrar la diferencia antes de aceptar la nueva cotización.
7. Monitorizar mappings ausentes, imágenes fallidas y antigüedad de sincronización.
8. Publicar por feed/API oficial o mecanismo aprobado por Meta/BSP; si no existe integración automática adecuada, usar una exportación incremental con reconciliación documentada.

La sincronización no debe crear ventas, reservar stock ni modificar la fuente comercial. Inventary sigue siendo el origen de verdad.

## 38. Flow estático, dinámico o mensaje interactivo

| Situación | Componente sugerido | Dependencia backend | No usar cuando |
|---|---|---|---|
| Confirmar/cambiar, retiro/envío | Botones | Acción/consulta autorizada | La modalidad aún no está implementada |
| Elegir entre sedes/motivos | Lista | Opciones válidas y actuales | Se necesita llenar varios campos relacionados |
| Dos campos constantes y no económicos | Flow estático | Validación al recibir el envío | Las opciones cambian frecuentemente |
| Elegir horario/sede/tarifa actual | Flow dinámico | Endpoint HTTPS seguro, cifrado/autenticado, rápido | El endpoint no puede validar los datos en tiempo real |
| Mostrar productos | Catálogo/carrito o mensaje de producto | Catálogo publicado y mapping estable | No hay catálogo sincronizado |
| Comparar muchas especificaciones | Ficha web móvil | Página pública segura | El cliente solo necesita una respuesta breve |
| Reclamo/pago no conciliado | Atención humana | Incidencia, estado y permisos | No obligar al usuario a completar formularios para ser escuchado |
| Inicio desde QR/anuncio | Deep link con contexto | Resolver contexto y volver a cotizar | No confiar en precios/descuentos escritos en URL |

La navegación de un Flow puede recopilar intención y campos, pero el alta económica final ocurre después de cotización válida y aceptación explícita. No crear órdenes en pasos intermedios de navegación.

## 39. Fallback y compatibilidad por dispositivo

- Si Flow no abre, ofrecer los mismos campos por texto; no pedir que empiece de nuevo.
- Si el catálogo no está conectado, ofrecer una lista corta de productos verificados o una URL móvil.
- Si se recibe un ID de botón desconocido, recuperar contexto y mostrar una opción válida; no ejecutar un comando arbitrario.
- Si la ubicación compartida no da una dirección postal suficiente, preguntar solo el dato restante.
- Si el cliente prefiere texto, dejar de insistir con Flow.
- Probar componentes en Android, iOS y WhatsApp Web/Desktop según el soporte real de cada uno, además de letra grande y lector de pantalla.
- Si la ventana está cerrada, seguir las reglas vigentes de plantilla y consentimiento.
- No mover stock ni confirmar pedido desde un enlace/CTA sin pasar por la API.
- Si el proveedor/BSP no soporta un componente, desactivarlo por configuración y usar el fallback.
- Conservar carrito, selección, cotización válida e historial al cambiar de interfaz.

## 40. APIs de backend adicionales para estos componentes

Los siguientes son contratos propuestos, no rutas que se afirmen implementadas hoy.

| Capacidad | Utilidad | Control obligatorio |
|---|---|---|
| Publicación de catálogo Meta | Mantener Commerce Manager alineado | mapping, versionado, retries y estado de publicación |
| Resolución de carrito nativo | Traducir retailer IDs a líneas de Inventary | revalidar ID, variante, estado, precio y cantidad |
| Catálogo de opciones de interacción | Entregar sedes, modos y horarios válidos | scope de canal, contexto y expiración |
| Endpoint de datos de Flow | Cargar opciones/cotización actual | autenticidad/cifrado, latencia, rate limit y minimización de datos |
| Recepción de submission de Flow | Conservar Flow/version y convertirlo en intención | schemaVersion, correlación e idempotencia |
| Consulta de capacidad logística | Mostrar solo opciones ejecutables | backend autoritativo |
| Telemetría de mensajes | Medir enviado/entregado/fallido, categoría y coste | messageId, versión de tarifa y retención mínima |
| Preferencia del modo de atención | Permitir texto, Flow o persona | elección explícita y modificable |
| Recompra segura | Reutilizar un pedido anterior como borrador | identidad verificada, precio nuevo, stock nuevo y nueva aceptación |

## 41. Cómo llegar a menos de un minuto sin sacrificar fiabilidad

1. Reutilizar contexto ya proporcionado; no preguntar de nuevo producto/cantidad.
2. Elegir un único componente adecuado, no encadenar botones, listas y texto que contienen la misma elección.
3. Preguntar solo datos faltantes: no pedir dirección para retiro.
4. Consultar backend en silencio y mostrar resultados reales.
5. Agrupar campos estructurados con Flow solo cuando reduce esfuerzo neto.
6. Mostrar un resumen compacto con artículos, total, cargos, modalidad y condición de pago.
7. Solicitar aceptación una vez para esa versión de cotización.
8. Crear un único pedido idempotente.
9. Enviar una confirmación final con número, total y siguiente paso real.
10. Ante cambio de precio/stock o duda relevante, aclarar y volver a confirmar.
11. Si el componente falla, continuar con el contexto preservado.
12. Si se necesita revisión humana, priorizar exactitud y confianza por encima de la métrica de 60 segundos.

Los 60 segundos son un objetivo para compras sencillas, no una promesa universal. Las excepciones se deben medir aparte.

## 42. Pruebas de aceptación adicionales

1. Botón devuelve un ID esperado y ejecuta una acción una sola vez.
2. ID de botón obsoleto o de otra conversación no autoriza una operación.
3. Lista solo muestra ubicaciones/modos válidos del backend.
4. Un retailer/product ID sin mapping no genera pedido.
5. Precio desfasado en catálogo Meta exige nueva cotización.
6. Carrito de Meta recibido dos veces crea una única operación lógica.
7. Cambio de cantidad produce total recalculado.
8. Flow estático prellena los datos conocidos y no los pregunta otra vez.
9. Flow dinámico muestra opciones actualizadas de sede/horario.
10. El cliente no puede enviar un precio o descuento inventado a través de Flow.
11. Timeout de Flow tiene fallback sin venta parcial.
12. Submission duplicada o tardía no crea otro pedido.
13. Flow antiguo se recupera con texto comprensible.
14. Dispositivo sin Flow puede terminar por texto/botones.
15. Preferencia por texto es respetada.
16. Lector de pantalla y texto grande permiten una ruta equivalente.
17. Pin de ubicación incompleto no se trata como dirección postal completa.
18. CTA web es HTTPS, móvil y conserva la referencia del carrito.
19. Producto solo se muestra como tarjeta nativa si su referencia está publicada.
20. Llamada no se inicia sin consentimiento, soporte y horario apropiados.
21. Fuera de ventana se cumple la política de plantillas, sin lista interactiva no permitida.
22. Se miden mensajes entregados, no solo nodos ejecutados.
23. Se añade el coste de BSP cuando el proveedor lo cobra.
24. La ruta rápida se prueba en los dispositivos/versión de WhatsApp soportados por la integración.
25. Ningún componente omite validar importe ni la clave de idempotencia del pedido.

## 43. Referencias adicionales

- Meta: [WhatsApp Business Platform Features](https://whatsappbusiness.com/products/business-platform-features/)
- Meta: [WhatsApp Flows](https://whatsappbusiness.com/products/whatsapp-flows/)
- Meta SDK: [Interactive messages](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/messages/interactive/)
- WhatsApp Help Center: [carrito del cliente](https://faq.whatsapp.com/5495780857144067/?cms_platform=android)
- WhatsApp Help Center: [catálogos conectados](https://faq.whatsapp.com/1057141349534704/?cms_platform=web&eea=0&locale=es_MX)
- CM.com: [catálogo y mensajes de producto](https://developers.cm.com/messaging/docs/whatsapp-catalogue-product-messages)
- Twilio: [contenido de catálogo](https://www.twilio.com/docs/content/twilio-catalog)
- WhatsApp: [Business Calling API](https://whatsappbusiness.com/blog/whatsapp-business-calling-api/)
- AWS: [Flows estáticos y dinámicos](https://docs.aws.amazon.com/social-messaging/latest/userguide/managing-flows.html)
- Inventary: [Productos V2](PROPUESTA_UX_PRODUCTOS_V2.md), [Pedidos WhatsApp V2](PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md), [referencia de APIs actuales](microservices-inventary-ecuapp/docs/25-referencia-apis.md).

## 44. Dictamen actualizado

La mejor solución no es escoger un componente único para toda compra: es aplicar una **estrategia de interacción adaptativa**.

- Catálogo/carrito para explorar y seleccionar varios productos.
- Mensaje de producto para una referencia exacta.
- Botones para decisiones pequeñas.
- Listas para alternativas estructuradas.
- Flows para varios campos de checkout.
- Flow dinámico solo cuando requiere opciones del backend en tiempo real.
- CTA web para información larga.
- Texto/IA para conversación abierta.
- Atención humana/llamada consentida para casos complejos.

n8n selecciona y coordina el componente. Inventary valida la oferta, cotización, identidad, permisos y pedido. Los costos se miden por mensajes reales entregados y por pedido correcto. La compra rápida se optimiza sin forzar una interfaz que el cliente no entiende ni quitarle información necesaria para aceptar el total.

No se ha identificado una función oficial independiente llamada “WhatsApp View” en la documentación consultada; la necesidad de una vista de compra dentro de WhatsApp se cubre mediante la combinación de componentes oficiales mencionados, previa validación de WABA, Cloud API, proveedor y dispositivos compatibles.





---

# Revisión V2.3 — WhatsApp WebView / In-App Browser para recorrer una categoría y comprar dentro de WhatsApp

**Fecha de revisión:** 28 de octubre de 2026  
**Importancia:** esta sección concreta la capacidad que el negocio describe como “WhatsApp View”. Aunque ese no parece ser el nombre formal de la función, **la capacidad sí existe**: WhatsApp Business WebView / In-App Browser (IAB). Permite que un CTA compatible abra una página web dentro de WhatsApp y que el cliente navegue por ella sin cambiar inmediatamente a Chrome o Safari. No es lo mismo que WhatsApp Flows ni que el catálogo nativo.

## 45. Confirmación de la función y sus límites

La documentación de proveedores de WhatsApp Business Platform describe el WebView/IAB: una persona pulsa un botón CTA de un mensaje compatible y la URL abre en el navegador integrado de WhatsApp. Puede explorar una página móvil, llenar datos y continuar un checkout sin cambiar de aplicación. Puede cerrar esa vista y volver al chat.

Fuentes consultadas:
- [Infobip — Webview para botones CTA](https://www.infobip.com/docs/whatsapp/manage-integration/additional-functionalities): describe la apertura dentro de WhatsApp y una condición de elegibilidad asociada al límite de mensajería.
- [WATI — In-app Browser de WhatsApp](https://support.wati.io/en/articles/12867737-meta-introduces-in-app-browser-iab-experience-for-whatsapp): indica que la experiencia se aplica a CTA de plantillas marketing/utility y a cuentas con límite de al menos 1.000 conversaciones iniciadas por el negocio al día por número.
- [Sprinklr — WebView / In-App Browser](https://www.sprinklr.com/help/articles/whatsapp-webview-support/whatsapp-business-webview-support-inapp-browser-iab/691304747e4ef1475ff48386): documenta la misma condición de elegibilidad y uso de CTA.
- [WhatsApp Flows — experiencia alternativa dentro de la app](https://www.twilio.com/docs/content/whatsapp-flows).

### 45.1 Condición de lanzamiento obligatoria

Los proveedores no describen de forma idéntica todos los tipos de mensaje compatibles: algunas guías mencionan también mensajes interactivos libres; otras describen solamente plantillas de marketing o utility. Por eso **no se debe asumir que cualquier URL de cualquier mensaje se abrirá en WebView**.

Antes de habilitar esta ruta:
1. Comprobar en WhatsApp Manager el límite y elegibilidad del WABA/número concreto.
2. Confirmar con el BSP o la integración Cloud API qué tipo de CTA abre WebView para esa cuenta.
3. Probar en staging el mismo tipo de mensaje/plantilla que se usará en producción.
4. Validar el comportamiento en Android e iOS, el retorno a la conversación y la URL de fallback.
5. Si la cuenta no es elegible o la URL abre un navegador externo, usar catálogo/carrito nativo, Flow o texto como alternativa.

Meta controla la elegibilidad y el comportamiento del navegador integrado; n8n no puede forzarlo desde un payload. Tampoco debe clasificarse un mensaje artificialmente como utility para obtener WebView: la categoría debe corresponder al propósito real y a las políticas vigentes.

## 46. Ruta propuesta para Inventary: categoría → WebView → carrito → cotización → pedido

Esta ruta es especialmente útil cuando el catálogo tiene varias categorías, variantes, imágenes, filtros y comparaciones que son incómodas en una conversación.

### Paso 1 — Entender la categoría

El cliente dice, por ejemplo, “quiero ver herramientas” o “necesito artículos de limpieza”. n8n consulta las categorías publicables del backend y responde con una lista breve de categorías válidas.

- Si existe una categoría única y clara, no hacer una pregunta redundante.
- Si hay varias categorías posibles, mostrar una lista o botones para elegir con un toque.
- Si la petición es ambigua, aclarar la categoría; no inventar categorías mediante IA.
- No crear aún una sesión de compra hasta que se haya determinado una categoría o intención suficientemente precisa.

### Paso 2 — Crear una sesión web de compra segura

Al elegir la categoría, Inventary debe crear una sesión de checkout/catálogo acotada, con:
- identificador opaco de sesión;
- categoría resuelta por Inventary;
- referencia protegida al remitente/canal y al carrito si ya existe;
- estado, expiración y correlación;
- permisos limitados a explorar catálogo, construir carrito, cotizar y enviar una compra desde esa sesión;
- ninguna credencial administrativa ni datos personales legibles dentro de la URL.

Una ruta ilustrativa sería una dirección web de sesión que abra directamente la categoría seleccionada. El token debe expirar, poder revocarse y limitarse a esa sesión. No debe permitir consultar pedidos arbitrarios ni cambiar el identificador para entrar en datos de otra persona.

**Nunca incrustar X-Integration-Key, tokens de Meta, secretos de n8n ni JWT administrativos en el JavaScript del navegador.** La web llama a una API de checkout limitada; las credenciales servidor-servidor permanecen en backend.

### Paso 3 — Abrir la categoría específica dentro de WhatsApp

n8n envía un CTA claro, por ejemplo “Ver productos de Herramientas”. Cuando la cuenta y el tipo de mensaje son elegibles, el usuario pulsa el botón y WebView abre directamente la página de esa categoría, no la página principal desde la que debería buscarla otra vez.

El cliente puede filtrar y ordenar artículos, abrir fichas e imágenes, elegir las variantes que el backend soporte y añadir varios productos al carrito.

WebView es especialmente útil para:
- catálogo amplio con múltiples categorías;
- filtros por marca, tamaño, uso o características;
- varias imágenes y fichas técnicas;
- múltiples productos en una compra;
- comparar opciones antes de decidir;
- usuarios que prefieren tocar una interfaz gráfica en lugar de escribir nombres o SKU.

### Paso 4 — Carrito web respaldado por Inventary

La web utiliza los productos, unidades, atributos, variantes y precios de Inventary. El carrito es un borrador de intención, no una venta ni una reserva indefinida.

Al añadir/modificar un artículo:
1. la página envía la selección al endpoint de checkout;
2. el backend verifica producto activo/publicable, variante vendible y cantidad;
3. el backend devuelve datos actuales permitidos;
4. la web muestra importes recalculados por servidor;
5. si el artículo ya no existe o cambió, la web lo explica y presenta alternativas válidas.

La disponibilidad mostrada al abrir la categoría es informativa. La reserva real se hace en la etapa de pedido que defina la política comercial. No reservar stock únicamente porque el cliente abrió una página.

### Paso 5 — Checkout corto y condicional

**A. Carrito:** productos, variantes, cantidades, imágenes y total provisional; permitir quitar/cambiar líneas.

**B. Cómo recibirlo:** retiro en una sede habilitada o envío solo si la modalidad ya está implementada. Si elige retiro, no pedir dirección. Si elige envío, solicitar exclusivamente los datos que falten. Las fechas/ventanas se presentan solo si backend devuelve opciones factibles.

**C. Pago:** mostrar únicamente las condiciones de pago habilitadas para ese pedido. Prepago, pago al retirar, contra entrega o crédito no se deben presentar como disponibles si el backend no puede ejecutarlos y auditarlos.

**D. Resumen y aceptación:** líneas, cantidades, descuentos autorizados, impuestos/cargos, envío, total, moneda, punto/dirección, condición de pago, validez y condiciones esenciales. CTA inequívoco como “Confirmar pedido por [total]” y alternativa “Volver a cambiar”.

No exigir un inicio de sesión completo si el diseño permite una sesión invitada segura. Sin embargo, la URL de categoría no puede ser una llave para exponer un pedido ya existente o datos personales.

### Paso 6 — Cotización viva y aceptación exacta

Antes de aceptar, el backend emite una cotización vigente con importe y expiración. Si la página estuvo abierta mucho tiempo, cambió el precio o cambió una tarifa, se muestra la diferencia y se pide nueva aceptación.

La operación final referencia quoteId y versión, modalidad y evidencia de la acción del usuario. El contrato actual de WhatsApp debe ampliarse si no conserva toda la evidencia necesaria para aceptación desde la web.

### Paso 7 — Crear el pedido una sola vez

Después de pulsar confirmar:
1. el endpoint/BFF valida sesión y cotización;
2. consulta el estado actual y valida productos, stock, total, modalidad y consentimiento;
3. crea el pedido con Idempotency-Key estable;
4. registra la reserva según política;
5. devuelve número y estado;
6. la web presenta el resultado real; n8n puede enviar al chat una confirmación breve si aporta valor y no duplica innecesariamente el mensaje.

Si la petición termina en timeout después de la creación, la web consulta el resultado de la misma operación antes de reintentar. Dos clics o reabrir el CTA no crean otro pedido.

**La compra puede completarse dentro de WebView; el registro de negocio continúa en Inventary.** El navegador nunca escribe directamente en la base de datos ni utiliza un endpoint administrativo con credencial privilegiada.

## 47. Por qué WebView puede ser mejor que un Flow

| Característica | WebView de tienda Inventary | WhatsApp Flow | Catálogo/carrito nativo |
|---|---|---|---|
| Ver una categoría completa | Excelente: página con filtros, scroll y tarjetas | Limitado a las pantallas/campos del Flow | Bueno si las categorías están configuradas en Meta |
| Filtros complejos y búsqueda | Sí, en la web propia | No es su objetivo principal | Limitado por la UI nativa |
| Muchas imágenes/fichas técnicas | Sí | Más limitado | Adecuado para el contenido de producto publicado |
| Elegir varios productos | Carrito web flexible | Flow no es un carrito comercial por sí solo | Nativo y cómodo |
| Datos de entrega estructurados | Sí | Muy bueno | Normalmente requiere otro componente |
| Control de interfaz | Alto | Limitado a componentes de Flow | Limitado al catálogo Meta |
| Depende de elegibilidad IAB | Sí, para asegurar la apertura dentro de WhatsApp | No depende de la apertura de URLs | Depende de catálogo conectado |
| Opciones dinámicas de backend | Sí, con API web/BFF | Sí, con endpoint dinámico de Flow | El carrito se recalcula en Inventary al recibirse |
| Mejor uso | Catálogo extenso y checkout web completo | Formulario breve dentro del chat | Explorar y enviar carrito nativo |

Conclusión: WebView es el mejor candidato para navegar una categoría completa y finalizar compra en una web propia, cuando la cuenta sea elegible. Flow sirve para recoger datos puntuales. Catálogo/carrito nativo sirve para selección directa si la publicación Meta está sincronizada. La arquitectura puede usar los tres.

## 48. Requisitos técnicos del checkout WebView

### 48.1 Sitio móvil
- Diseño mobile-first, carga rápida y botones grandes.
- Página inicial de la sesión abre directamente la categoría seleccionada.
- Filtros sencillos, imágenes optimizadas, fichas claras y total visible.
- Carrito persistido en backend para cerrar/reabrir la vista sin comenzar de cero.
- Estados de error, vacío, espera y alternativa humana claros.
- HTTPS, dominio propio de confianza y sin redirecciones abiertas a sitios inesperados.
- Volver al chat no cancela ni borra el carrito.
- No depender de un evento de “vista abierta” si el navegador no lo proporciona de forma fiable.

### 48.2 Seguridad de sesión
- Token opaco, limitado a una sesión y de vida corta.
- La sesión no otorga acceso a clientes/pedidos ajenos.
- Expiración y revocación seguras; límites de uso y protección ante replay.
- Validar productId, quoteId, cantidad y modalidad en cada operación.
- No guardar teléfono/dirección o datos financieros en texto legible en la URL.
- No registrar tokens completos en logs.
- Nunca exponer la clave X-Integration-Key ni cualquier credencial de proveedor.
- El retorno del navegador no prueba que se haya creado un pedido ni que se haya pagado; se consulta el resultado real de la API.

### 48.3 APIs/BFF propuestas

Las siguientes capacidades son futuras; no son rutas que se afirme que ya existen.

- Crear una sesión web acotada para remitente/categoría/carrito.
- Validar sesión y devolver contexto público permitido.
- Listar categorías y productos publicables dentro de esa sesión.
- Consultar ficha y variantes soportadas.
- Agregar, modificar y quitar líneas del carrito.
- Solicitar cotización viva con precio, descuentos permitidos, cargos y expiración.
- Confirmar pedido con aceptación y clave idempotente.
- Consultar el resultado de una operación después de un timeout.
- Solicitar ayuda humana conservando carrito/contexto.

La implementación debe seguir la convención real de Inventary, aplicar scopes/DTOs mínimos y evitar una API administrativa abierta. El BFF puede residir en Spring Boot o separarse solo si existe una razón de operación/seguridad clara.

## 49. Workflow n8n propuesto: N8N-25-WEBVIEW-CATEGORY-CHECKOUT

1. Recibir la intención y el contexto del mensaje entrante.
2. Consultar las categorías/productos publicables de Inventary.
3. Si hay varias categorías, enviar lista/botones; no crear sesión hasta que el cliente elija.
4. Validar que la selección corresponde al mensaje/conversación esperada.
5. Consultar configuración probada de elegibilidad IAB para WABA/BSP y tipo de CTA.
6. Pedir a Inventary una sesión web limitada a la categoría y contexto necesarios.
7. Enviar el CTA compatible con texto claro: “Ver productos de [categoría]”. Utilizar la plantilla aprobada que corresponda si la política/ventana lo exige.
8. El navegador abre la categoría concreta y usa la API BFF para carrito/cotización.
9. Al confirmar, backend crea pedido y publica evento/outbox.
10. n8n consume/consulta el estado real y solo envía confirmación si aporta valor.
11. Si el usuario abandona, el borrador persiste conforme a política. No enviar recordatorios promocionales sin consentimiento.
12. Si IAB no está disponible, ofrecer catálogo/carrito nativo, Flow o texto con la categoría preservada.

n8n no debe ejecutar una llamada por cada clic de filtro o cada vista de producto. La web llama directamente a la API limitada; n8n inicia la sesión, recibe eventos de negocio y coordina comunicaciones. Así se reducen latencia, ejecuciones y puntos de fallo.

## 50. Medición específica de WebView

Medir por categoría, tipo de CTA, WABA, dispositivo y versión:
- CTA enviado/entregado/clicado cuando esos datos existan;
- apertura WebView verificada, o primer request web etiquetado como aproximación;
- fallback a navegador externo, Flow o catálogo nativo;
- productos vistos/añadidos/quitaron y cantidades cambiadas;
- inicio de checkout, cotización y aceptación;
- p50/p90 desde apertura de categoría hasta pedido creado;
- abandono por sección, error de sesión o API;
- mensajes entregados por pedido completado;
- costo Meta + BSP por pedido completado;
- cambios de precio/stock entre catálogo y cotización;
- duplicados evitados, sesión expirada y errores de idempotencia;
- solicitudes de ayuda y reclamaciones por precio/destino.

No afirmar que un usuario vio la página solo porque el CTA se entregó. No acumular PII en analítica si basta con IDs opacos.

## 51. Pruebas de aceptación WebView

1. CTA abre la categoría elegida dentro de WhatsApp cuando la cuenta/tipo de mensaje son elegibles.
2. Si WebView no abre, el fallback sigue funcionando y conserva la selección.
3. Se abre exactamente la categoría seleccionada, no la página principal.
4. El cliente puede filtrar, ver detalles, cambiar cantidades y construir un carrito.
5. Ninguna clave de integración queda expuesta en código o tráfico del navegador.
6. Cada producto/variante/cantidad se revalida en backend.
7. El sistema recotiza antes de confirmar; cualquier cambio requiere nueva aceptación.
8. Dos clics en confirmar crean una sola orden/reserva.
9. El timeout consulta el resultado original, no crea otra operación.
10. Cerrar/reabrir WebView conserva la sesión segura hasta expiración.
11. Modificar token/categoría/ID no permite ver datos de otra persona.
12. CTA y plantilla respetan ventana, opt-in y categoría real.
13. n8n no afirma pedido confirmado antes de la respuesta backend.
14. Hay alternativa de texto, Flow o persona para quien no pueda/quiera usar WebView.
15. El costo se calcula a partir de mensajes reales entregados y distingue Meta/BSP/IA/hosting.
16. La ruta se prueba con Android/iOS y el WABA/proveedor reales antes de activarla en producción.

## 52. Decisión de producto actualizada

La estrategia recomendada de Inventary pasa a ser:

- **WebView/IAB:** mejor ruta para abrir una página web de la categoría elegida y completar un carrito/checkout completo en la web móvil dentro de WhatsApp, si el número y tipo de CTA son elegibles.
- **Catálogo/carrito nativo:** mejor para descubrir y seleccionar rápidamente artículos publicados en Meta.
- **WhatsApp Flow:** mejor para recoger varios campos estructurados en pocas pantallas.
- **Botones/listas:** mejor para decisiones pequeñas, categorías y ubicaciones.
- **Texto/IA:** mejor para búsqueda abierta y dudas.
- **Atención humana:** mejor para reclamaciones, pagos en disputa, excepciones y clientes que la solicitan.

n8n elige y coordina; la web/Flow llama APIs de alcance limitado; Inventary valida oferta, cotización, consentimiento, stock y pedido. La compra rápida se optimiza sin sacrificar exactitud.

---

## Addendum obligatorio — Inventary solicita la facturación; el backend fiscal la emite

Contrato canónico y estados: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

**No delegar la emisión del comprobante al workflow de n8n.** El flujo de negocio confirma la venta en Inventary y solicita inmediatamente la emisión al backend fiscal externo. Ese backend es quien calcula/valida los datos tributarios conforme al contrato, genera el XML, firma, transmite al SRI y devuelve el resultado real.

Responsabilidades:
- Inventary conserva la venta, snapshots, contexto fiscal, movimientos de stock, idempotencia y estado de la solicitud.
- El backend fiscal administra certificados, XML, firma, numeración/series, transmisión, respuestas de SRI, anulación y notas de crédito.
- n8n notifica eventos confirmados como FISCAL_DOCUMENT_AUTHORIZED, FISCAL_DOCUMENT_REJECTED, FISCAL_CANCELLATION_COMPLETED, CREDIT_NOTE_AUTHORIZED o NEEDS_FISCAL_REVIEW; esos nombres son eventos propuestos y deben acordarse con el contrato real.
- n8n no fabrica números fiscales, no modifica estados en base de datos, no intenta anular la factura por su cuenta y no vuelve a enviar un comando fiscal con una clave nueva para “solucionar” un timeout.
- Cada entrega lleva eventId, schemaVersion, sourceOperationId y correlationId. Los consumidores deduplican y consultan el estado en caso de duda.

Una venta o cuota no puede ser anunciada como fiscalmente autorizada hasta que Inventary haya persistido la respuesta confirmada del backend fiscal. Si el evento llega tarde, la notificación se retrasa o se marca pendiente; no se inventa un número de factura.

**Devoluciones y cancelaciones:** una anulación fiscal no es lo mismo que una devolución de mercadería. La venta cancelada por error/no realización puede dar lugar a una solicitud de anulación si la normativa lo permite. Una devolución de una operación válida debe enviar sus líneas al backend fiscal para que este resuelva el tratamiento permitido, normalmente una nota de crédito en los casos aplicables. El reembolso de dinero es otro proceso y no se deduce de la nota de crédito.

La Resolución NAC-DGERCGC25-00000017 sustituyó el plazo del artículo 3 por el día 7 del mes siguiente, con vigencia desde el 1 de enero de 2026, y reformó el artículo 5 de la resolución inicial. El backend fiscal, no un workflow estático, mantiene la normativa consolidada y el tratamiento de consumidor final/otras excepciones.

Pruebas n8n: evento duplicado no duplica notificación ni documento; evento fuera de orden consulta estado actual; timeout fiscal no crea factura nueva; ningún mensaje dice “autorizada” sin estado confirmado; la devolución notifica el estado de nota de crédito separado del estado de stock y del reembolso.

---

## Addendum fiscal adicional — Registro del proveedor del sistema de facturación

Contrato de referencia: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

La Resolución SRI NAC-DGERCGC26-00000027, publicada en el Quinto Suplemento del Registro Oficial No. 335 el 28 de julio de 2026, regula el registro de ciertas categorías de proveedores de sistemas/servicios de facturación electrónica. Su artículo 5 requiere, cuando el proveedor esté comprendido en el ámbito, incluir su RUC en la sección de información adicional de los comprobantes conforme a la ficha técnica; la transitoria tercera indica 60 días calendario desde publicación.

n8n no debe construir el XML ni añadir este dato manualmente. El backend fiscal debe validar la aplicabilidad, mantener la identidad fiscal del proveedor tecnológico y escribir el dato en el XML/RIDE a través del procedimiento correcto. La integración de Inventary debe poder comprobar en configuración/health check que el proveedor está configurado antes de emitir. No confundir proveedor tecnológico con contribuyente emisor o receptor. No asumir que el servicio externo está exento ni que necesariamente pertenece al artículo 2: confirmarlo con el proveedor/responsable tributario. Fuente: [SRI, aviso oficial](https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1).

---

## Revisión de n8n — Las asociaciones de bodegas no autorizan al agente a elegir emisor

Contrato de referencia: [Facturación electrónica SRI](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md) y [Centro de reportes V2](PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V2.md).

La relación bodega-establecimiento es muchos-a-muchos y debe resolverse por una herramienta de backend autorizada. n8n/LLM no puede elegir el primer registro, asumir que una bodega pertenece a un único establecimiento ni decidir el punto de emisión usando un nombre, código interno o texto del cliente.

La herramienta debe devolver opciones permitidas, tipo de vínculo, vigencia, RUC, estado actualizado y razón de la sugerencia. Si la asignación no es unívoca conforme a la política, el workflow debe pedir la selección autorizada o entregar a una persona. La API fiscal externa vuelve a validar el billingContextId antes de emitir.

Para reportes y alertas, los eventos deben incluir warehouseId, establishmentId cuando proceda, legalEntity/owner, sourceOperationId y contexto fiscal real. Los flujos deduplican por identidad del hecho y no agregan stock o ventas una vez por cada asociación. Las relaciones cambiadas no reasignan eventos históricos.

---

## Nota normativa transversal — NAC-DGERCGC26-00000027

La resolución publicada contiene, en su disposición transitoria tercera, un plazo de **60 días calendario** para implementar el requisito del RUC del proveedor de facturación en información adicional del comprobante. Sin embargo, la noticia informativa oficial del SRI describe el plazo como **60 días hábiles**. Para evitar que cada módulo interprete la regla de manera distinta, prevalece el texto normativo publicado y debe quedar registrada la discrepancia de la noticia. A la fecha de esta revisión (29 de octubre de 2026), el plazo transitorio habría transcurrido bajo ambas lecturas; verificar inmediatamente el cumplimiento con el proveedor fiscal y el responsable tributario. No modificar XML ni datos de factura desde React/n8n: la inclusión corresponde al backend fiscal conforme a la ficha técnica vigente.

Fuentes: [texto de NAC-DGERCGC26-00000027](https://uqa.com.ec/wp-content/uploads/2026/07/Newsletter-No.-030-SRI-registro-de-proveedores-de-sistemas-informaticos-o-servicios-de-facturacion-electronica-28-JULIO-2026-SRI-NAC-27.pdf) · [aviso oficial del SRI](https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1) · [contrato fiscal canónico](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).
