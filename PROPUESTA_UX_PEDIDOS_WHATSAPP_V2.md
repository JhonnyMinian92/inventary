# Propuesta UX/UI y arquitectura funcional: Pedidos WhatsApp V2

**Fecha:** 28 de octubre de 2026  
**Repositorio:** JhonnyMinian92/inventary  
**Estado:** propuesta funcional y técnica basada en el código versionado y en investigación externa. Los campos, estados, endpoints y flujos marcados como futuros no se consideran implementados.  
**Alcance:** pantalla React Pedidos WhatsApp, integración con Ventas e Inventario, WhatsApp Business Platform, n8n y agente IA opcional con OpenAI o Gemini.

Documentos relacionados:
- [UX/UI de Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Logística: retiro, despacho y entrega](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)
- [Motor comercial inteligente](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)
- [Arquitectura conversacional y n8n](microservices-inventary-ecuapp/docs/09-webflux-n8n-whatsapp-openai.md)

---

## 1. Decisión ejecutiva

La pantalla **Pedidos WhatsApp no debe ser solo una tabla de pedidos por cobrar**. Debe convertirse en una bandeja de trabajo que permita recibir, validar, confirmar, preparar, cobrar, despachar y cerrar pedidos originados en WhatsApp, manteniendo la trazabilidad hasta la venta de Inventary.

La arquitectura recomendada divide responsabilidades:

1. **WhatsApp Business Platform:** transporta mensajes y eventos entrantes/salientes.
2. **n8n:** orquesta conversación, automatiza los pasos permitidos, llama las APIs de Inventary, reintenta y deriva incidencias.
3. **Agente IA opcional:** interpreta mensajes, extrae datos estructurados, consulta herramientas autorizadas y redacta respuestas. No es fuente de verdad para precio, inventario, descuento, pago, permisos o estado.
4. **Inventary:** decide precio, cotización, stock, reserva, autorización, venta, registro de pago, auditoría y transiciones.

**Regla central:** pedido recibido, venta registrada, dinero recibido y mercancía entregada son hechos diferentes. La UI y el backend deben representarlos de manera independiente.

La propuesta contempla clientes que van en camino, pedidos nocturnos, envío a domicilio, retiro en origen, retiro por terceros, descuentos, prepago, pago al retirar, contra entrega, crédito pospago, cambios, cancelaciones, reservas vencidas, falta de stock y atención humana. Las opciones que todavía no soporta el backend solo deben habilitarse cuando se implementen y prueben los procesos correspondientes.

## 2. Auditoría del estado actual

### 2.1 Frontend

La pantalla actual está implementada en [WhatsAppOrdersPage.jsx](app-inventary-ecuapp/src/features/whatsapp/WhatsAppOrdersPage.jsx) y coincide con la captura analizada. Hoy muestra filtros **Por cobrar**, **Confirmados**, **Expirados**, **Cancelados** y **Todos**; búsqueda por número de WhatsApp; una tabla de pedido, cliente, almacén, artículos, total, vencimiento/creación y estado; y un panel lateral con líneas, importes, fechas y referencia del pago.

La acción **Confirmar pago** exige método y referencia. Al completarse, informa que el pedido ya es una venta. La bandeja resuelve el seguimiento básico de cotización/pedido y el registro de pago, pero no distingue preparación, listo para retiro, despacho, tránsito, entrega, destinatario tercero, prioridad urgente, horario comercial, trabajo humano pendiente ni pago todavía no exigible.

### 2.2 APIs existentes y lógica transaccional

El controlador [WhatsAppIntegrationController.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppIntegrationController.java) implementa catálogo/detalle público, crear/consultar carrito, modificar líneas, cotizar por almacén, consultar cotización, crear pedido idempotente, consultar pedido y cancelar cuando el estado lo permite. La autenticación de integración actual usa el header X-Integration-Key.

El backoffice [WhatsAppOrderBackofficeController.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppOrderBackofficeController.java) ofrece listado filtrable, detalle y confirmación de pago protegida por sales:manage.

[WhatsAppCommerceService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppCommerceService.java) valida productos, cotización, cantidad y disponibilidad; bloquea saldos al confirmar un pedido; reserva inventario de forma transaccional; evita repetición mediante Idempotency-Key y libera reservas al cancelar o expirar. Hay eventos Outbox y auditoría para crear, cancelar y expirar.

[WhatsAppOrderBackofficeService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppOrderBackofficeService.java) implementa confirmación idempotente: consume la reserva, crea una Sale con canal WHATSAPP, registra la salida de inventario y vincula la venta al pedido. El test [WhatsAppApiTest.java](microservices-inventary-ecuapp/src/test/java/com/ecuapp/inventary/api/WhatsAppApiTest.java) prueba la creación de pedidos, las reservas, la confirmación, los permisos, la venta, la cancelación y la expiración.

Por tanto, no se recomienda crear una segunda API de ventas ni duplicar inventario en n8n. Se debe extender el dominio existente, reutilizando sus transacciones, movimientos, costeo, idempotencia, auditoría y Outbox.

### 2.3 Brechas confirmadas por el código

1. El estado actual del pedido solo admite AWAITING_PAYMENT, CONFIRMED, CANCELLED y EXPIRED. No incluye revisión comercial, preparación, listo para retiro, despacho, tránsito, entrega o recogida.
2. El pedido solo referencia un almacén. No registra modalidad de cumplimiento, dirección/destino, punto de retiro distinto, destinatario tercero, transportista, guía, ventana solicitada, código de recogida ni prueba de entrega.
3. La cotización recibe warehouseId. El contrato actual no contempla modalidad, destino, coste de envío, descuentos ni promoción. En el flujo actual, la cotización registra impuesto en cero y total igual al subtotal; no debe mostrarse como un cálculo fiscal completo.
4. La reserva del pedido vence en 20 minutos por defecto y la configuración limita la vigencia a un máximo de cuatro horas. La expiración libera existencias. No es una política adecuada, sin ajustes, para pedidos nocturnos, pago al retirar, contra entrega o preparación prolongada.
5. Confirmar pago consume la reserva, crea la venta y registra la salida de stock. No se puede usar esa acción para confirmar un pedido contra entrega o pospago que todavía no ha cobrado.
6. El pedido no declara una condición de pago antes de crearse. Método de pago (efectivo/transferencia/tarjeta) no es lo mismo que condición (prepago, pagar al retirar, contra entrega, crédito).
7. El DTO de creación conserva una confirmación binaria consentConfirmed, pero el modelo del pedido no vincula la confirmación al ID concreto del mensaje verificado de WhatsApp.
8. No existe en el detalle una cronología integral de conversación, responsable bot/persona, eventos de entrega de mensajes, estado de ejecución n8n, preparación, entrega y pagos parciales.
9. La referencia de WhatsApp del cliente no reemplaza un modelo completo de cliente, destinatario, autorización, privacidad y retención de datos.

Estas son brechas de alcance; no invalidan la base transaccional existente.

## 3. Investigación de sistemas y patrones comparables

La revisión abarcó los contratos actuales del repositorio, sus propuestas de Ventas y Logística, workflows de n8n, el modelo de fulfillment de Shopify, la política oficial de WhatsApp y una guía operacional de agentes conversacionales.

### 3.1 Patrones observados en n8n

Las plantillas comunitarias consultadas muestran estos patrones aprovechables:

- [Pedidos, FAQ y tracking con GPT-4o](https://n8n.io/workflows/16175-handle-whatsapp-orders-faqs-and-tracking-with-gpt-4o-pinecone-and-sheets/): clasifica intención, separa búsqueda/pedido/seguimiento y deriva soporte humano.
- [WhatsApp Cloud API, conversación y Data Tables](https://n8n.io/workflows/20729-handle-whatsapp-customer-chats-with-openai-and-data-tables/): deduplica mensajes por ID, filtra eventos de entrega/lectura, trata audio/imagen/documentos, mantiene contexto y alerta sobre fallos.
- [Pedidos y entregas de restaurante con WhatsApp, GPT y Supabase](https://n8n.io/workflows/7298-restaurant-order-and-delivery-system-for-whatsapp-with-gpt-4o-and-supabase/): combina mensajes multimedia, pedido, cobro y comunicación operativa.

Son patrones útiles, no arquitecturas transaccionales que deban copiarse literalmente. Algunas plantillas escriben el catálogo y las órdenes en hojas de cálculo o servicios externos. En Inventary, la orden y el inventario deben mantenerse en la API de negocio; no deben duplicarse en Sheets, la memoria del LLM ni una base separada de n8n.

### 3.2 Shopify: cumplimiento por ubicación

Shopify representa el trabajo de surtir un pedido con grupos de cumplimiento ligados a una ubicación, contempla más de un grupo por pedido y separa preparación/empaque de despacho y seguimiento. En retiro local se informa la ubicación, el horario, las instrucciones y una estimación de preparación.

Aplicación a Inventary: confirmar un pedido no equivale a preparar la mercancía; el almacén que tiene stock no siempre es el punto de retiro; y un pedido que incluya varios orígenes puede requerir varios grupos de cumplimiento. La propuesta debe coordinarse con [PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md).

Referencias:
- [Shopify: FulfillmentOrder y ciclo operativo](https://shopify.dev/docs/api/admin-graphql/latest/objects/fulfillmentorder)
- [Shopify: retiro local, horario e instrucciones](https://shopify.dev/docs/api/checkout-ui-extensions/latest/targets/checkout/local-pickup)
- [Shopify: grupos de cumplimiento y pedidos divididos](https://shopify.dev/docs/apps/build/checkout/split-carts)

### 3.3 WhatsApp Business

La política oficial consultada exige consentimiento para comunicaciones posteriores, respeto a las bajas, plantillas aprobadas para iniciar mensajes de empresa y vías de escalamiento humano. La ventana de respuesta de 24 horas desde el último mensaje del usuario y las reglas de plantillas deben comprobarse nuevamente antes del despliegue, porque las políticas pueden cambiar. La política recomienda consentimiento separado para categorías como actualizaciones de pedidos y ofertas.

Referencia: [Política oficial de mensajes de WhatsApp Business](https://business.whatsapp.com/policy/preview?lang=es_LA).

### 3.4 Agentes de IA y handoff

La guía de [Bird para agentes de WhatsApp](https://bird.com/docs/guides/whatsapp/ai-agents) recomienda limitar las acciones a herramientas autorizadas, solicitar confirmación cuando corresponda, conservar un identificador para impedir operaciones duplicadas y transferir contexto verificado a una persona cuando la petición sea ambigua, el cliente lo solicite o una acción falle.

## 4. Principios de diseño no negociables

1. Inventary determina los estados definitivos, precios, impuestos, promociones, disponibilidad, reservas y ventas.
2. Pedido, pago y cumplimiento son dimensiones separadas, no un solo estado.
3. El texto de una conversación no prueba que el pago se haya recibido ni que el pedido se haya entregado.
4. Toda operación económica o de stock es idempotente.
5. Toda excepción conserva motivo, actor, fecha, estado anterior y resultado.
6. La IA propone y redacta; el backend valida y ejecuta.
7. “Voy en camino” prioriza solo si hay stock, horario y capacidad reales.
8. El total completo se ve antes de confirmar: artículos, descuentos, impuestos aplicables, entrega, total, modalidad y condición de pago.
9. Los formularios extensos van en página o stepper, no en un modal largo.
10. Debe haber un camino fácil a la atención humana.

## 5. Modelo funcional objetivo

### 5.1 Estado operativo del pedido

El estado principal responde: ¿qué ocurre con este pedido y cuál es la siguiente acción operativa?

| Estado interno propuesto | Etiqueta visible | Significado |
|---|---|---|
| PENDING_REVIEW | Por revisar | Recibido, pero necesita validación por horario, stock, excepción o condición comercial. |
| CONFIRMED | Confirmado | Inventary aceptó el pedido y aplicó la política de reserva. No significa necesariamente pagado. |
| PREPARING | En preparación | Personal autorizado inició picking/empaque. |
| READY_FOR_PICKUP | Listo para retirar | Preparación verificada y mecanismo de retiro disponible. |
| DISPATCHED | Despachado | Salió del origen; consta quién o qué transportista lo recibió. |
| IN_TRANSIT | En camino | Hay evidencia logística que respalda el estado. |
| DELIVERED | Entregado | Entrega verificada y auditada. |
| PICKED_UP | Retirado | Receptor autorizado verificado en el punto de retiro. |
| CANCEL_REQUESTED | Cancelación solicitada | Debe comprobarse si aún se puede cancelar o requiere excepción. |
| CANCELLED | Cancelado | Aplicadas las compensaciones de inventario/finanzas que correspondan. |
| REJECTED | No aceptado | El negocio no puede cumplirlo; se informa el motivo. |
| EXPIRED | Vencido | Venció un compromiso definido y se liberó la reserva si corresponde. |

Es un modelo futuro, no una afirmación de estados implementados. Las transiciones deben ser comandos del backend con precondiciones, permisos y auditoría. Un retiro puede pasar de PREPARING a READY_FOR_PICKUP y luego PICKED_UP; un envío usará DISPATCHED, IN_TRANSIT y DELIVERED. No todos los pedidos necesitan todos los estados.

### 5.2 Condición de pago y estado de pago

La **condición de pago** define cuándo se debe pagar:

- PREPAID: pagar antes de confirmar/preparar según la política.
- PAY_ON_PICKUP: cobrar al retirar.
- CASH_ON_DELIVERY: cobrar en la entrega.
- POSTPAID: crédito/plazo autorizado, con límite y vencimiento.
- DEPOSIT_THEN_BALANCE: anticipo y saldo posterior, si el negocio lo habilita.

El **estado de pago** registra hechos:

- UNPAID
- PROOF_SUBMITTED o PENDING_VERIFICATION
- PARTIALLY_PAID
- PAID
- PAYMENT_FAILED
- REFUND_PENDING
- PARTIALLY_REFUNDED
- REFUNDED

El **método de pago** describe el instrumento: efectivo, transferencia, depósito, tarjeta/gateway u otro método configurado. No sustituye la condición.

Reglas esenciales:
- Subir una captura no marca un pedido como pagado.
- OCR/IA puede extraer una referencia y dejarla pendiente, pero no validar por sí sola que el dinero llegó.
- Un pago online solo se marca pagado tras un callback verificado del proveedor o una conciliación autorizada.
- En contra entrega, el pedido puede estar despachado y continuar sin pagar; el cobro se registra cuando efectivamente se recibe.
- Crédito pospago requiere autorización, saldo y vencimiento; no equivale a “falló el pago”.
- Reembolso y devolución son procesos diferentes.

El flujo actual de confirmación de pago no cubre todos estos casos correctamente sin ampliar el dominio financiero y separar cobro, venta y cumplimiento.

### 5.3 Estado de cumplimiento

Como mínimo, cada pedido determina:

- PICKUP: retiro en local habilitado.
- DELIVERY: entrega a domicilio/destino.
- PICKUP_OTHER_LOCATION: retirar en otro local, solo cuando exista traslado y recepción confirmada.
- Más adelante, grupos de cumplimiento por origen/destino si se soportan envíos parciales.

Los datos de cumplimiento separan almacén origen, punto de retiro, destinatario, dirección, zona de cobertura, tarifa, promesa, responsable de preparación, transportista, tracking, prueba de entrega y receptor tercero. No reutilizar un campo de dirección para significar almacén, facturación y destino.

### 5.4 Prioridad y programación

- STANDARD: cola normal.
- EXPEDITED: petición de prioridad sujeta a capacidad real.
- SCHEDULED: fecha/hora futura cuando exista agenda operativa.

“Voy en camino” genera prioridad solicitada, no una promesa automática. Se debe validar stock, horario, cola y tiempo de preparación. Si faltan datos, responder “solicitud prioritaria; te confirmaremos cuando esté listo”, no inventar una hora exacta.

Fuera del horario, el pedido puede recibirse automáticamente, pero debe quedar por revisar o mostrar el próximo horario real. No afirmar que está confirmado/listo si el local está cerrado o requiere confirmación humana.

## 6. Matriz de escenarios comerciales

| Escenario | Experiencia y datos | Operación | Control principal |
|---|---|---|---|
| Cliente va en camino | Retiro, ubicación, horario e indicación de esperar aviso de listo. | Priorizar y empezar preparación. | No prometer disponibilidad inmediata sin capacidad real. |
| Pedido nocturno | Confirmación de recepción y expectativa del próximo horario. | Revisar y aceptar al abrir. | Política específica de reserva/expiración; no falsa promesa. |
| Retiro en origen | Punto que prepara y entrega, horarios e instrucciones. | Preparar, contar, marcar listo y verificar retiro. | Solo locales habilitados como punto de atención. |
| Envío a domicilio | Destino, zona, destinatario, coste y estimación. | Preparar, despachar y registrar tracking/entrega. | Cobertura y tarifa calculadas en servidor; no marcar entrega sin evidencia. |
| Retiro por tercera persona | Nombre autorizado y dato mínimo de contacto. | Verificar autorización/código al entregar. | Código aleatorio, de un solo uso, vencimiento, límites de intentos y auditoría. |
| Prepago gateway | Total claro y retorno al mismo pedido tras confirmar pago. | Conciliar y continuar preparación. | Callback firmado/verificado e idempotente. |
| Transferencia/depósito | Puede enviar comprobante y recibe “pendiente de verificación”. | Verificar en canal autorizado. | OCR/IA no prueba recepción de fondos. |
| Pago al retirar | Pedido puede prepararse y sigue con pago pendiente. | Cobrar y registrar método/referencia al retirar. | No marcar pagado anticipadamente. |
| Contra entrega | Total final, envío y condición visibles. | Despachar, registrar intento de entrega y cobro al recibir dinero. | Puede estar en tránsito sin estar pagado. |
| Crédito pospago | Solo para cliente/condición autorizados; plazo visible. | Aprobar crédito, registrar saldo y conciliar deuda. | Límite, vencimiento, rol y auditoría. |
| Descuento | Importe, condición y total recalculado visibles. | Aplicar regla o pedir aprobación. | Regla/permisos en backend; IA no inventa descuentos. |
| Precio/stock cambió | Se explica la diferencia y se solicita nueva aceptación. | Recalcular la cotización. | No aceptar en silencio un importe distinto. |
| Stock insuficiente | Alternativas verificadas o cancelación. | Cambiar origen/producto/cantidad tras aceptación. | Validación transaccional; no inventar existencia. |
| Cancelación/devolución | Explicar si ya se preparó/despachó y qué pasa con el dinero. | Aplicar la acción permitida, compensaciones y reembolso separado. | No borrar movimientos ni duplicar reintegros. |
| Audio/imagen ambiguos | Resumen estructurado que el cliente confirma. | Derivar cuando persista la duda. | No asumir variante, cantidad, dirección ni total. |
| IA/API fuera de servicio | Aviso honesto y traspaso a bandeja. | Operador continúa con el estado real. | Un mensaje enviado no prueba éxito de negocio. |

### 6.1 Política de reserva por escenario

La vigencia actual por defecto de 20 minutos no debe aplicarse indiscriminadamente.

- Una cotización tiene una validez explícita, pero no reserva stock.
- Pedido pendiente de pago inmediato: reserva limitada con vencimiento comunicado.
- Pedido aceptado para retiro/envío: reserva ligada al SLA de preparación.
- Pedido nocturno: reserva coherente con el horario y la próxima revisión.
- Pago al retirar o contra entrega: reservar durante el compromiso operativo permitido, no solo 20 minutos si el personal lo aceptó y la preparación tarda más.
- Cancelar/expirar libera la reserva una sola vez, produce evento y notifica según política.
- Nunca mantener reservas indefinidas: alertas, límite y recuperación controlada.

## 7. Rediseño de la pantalla

### 7.1 Organización por tareas

Mantener los componentes visuales de Inventary: navegación oscura, superficies claras, tipografía legible y badges discretos. Convertir la pantalla en una bandeja de trabajo con colas:

- **Por revisar:** nuevos, nocturnos, excepciones, falta de datos o aprobación.
- **Por preparar:** aceptados pero no preparados.
- **Listos para retirar:** preparación verificada y esperando al cliente/tercero.
- **En entrega:** despachados, en tránsito o con intento pendiente.
- **Por cobrar:** saldo pendiente según condición, aunque el pedido esté en preparación o tránsito.
- **Incidencias:** falta de stock, precio cambiado, entrega fallida, pago sin conciliar, error n8n o solicitud humana.
- **Completados:** retirados/entregados y conciliados conforme a política.
- **Todos:** consulta histórica.

Las colas son vistas operativas que pueden solaparse: contra entrega puede estar en “En entrega” y “Por cobrar”. No deben confundirse con estados mutuamente excluyentes.

Filtros combinables: número de pedido, cliente/teléfono parcial, estado operativo, estado/condición de pago, cumplimiento, almacén, prioridad, asignado a, fechas y origen de atención. Conservar los filtros al volver del detalle.

### 7.2 Tabla principal

Columnas prioritarias:

1. **Pedido/hora:** número, antigüedad y nueva actividad si existe.
2. **Cliente:** nombre disponible o referencia enmascarada.
3. **Entrega:** retiro/envío, ubicación y compromiso horario si está respaldado.
4. **Artículos:** número de líneas y cantidad total.
5. **Total:** desglose en detalle; descuento/envío siempre visibles antes de confirmar.
6. **Pago:** pagado, pendiente, comprobante por revisar, contra entrega o crédito.
7. **Siguiente acción/estado:** “Revisar”, “Preparar”, “Marcar listo”, “Registrar cobro”, “Abrir incidencia”.

En móvil, transformar cada fila en una tarjeta concisa con pedido, cliente, total, modalidad, dos estados y una acción principal. No esconder pago en tooltips ni saturar la tabla con una columna para cada campo.

### 7.3 Indicadores de cabecera

Como máximo, cuatro métricas que exijan acción: **Por revisar**, **Por preparar**, **Retiros por vencer** e **Incidencias abiertas**. Los contadores provienen del backend y enlazan a su filtro. La última sincronización y los errores recuperables deben ser visibles sin recargar toda la página.

### 7.4 Detalle como página, no drawer infinito

La ruta /pedidos-whatsapp/:id ya existe. Convertir el detalle extenso en una página dedicada; reservar el drawer para consulta rápida. Para formularios con varias secciones, usar stepper o página completa.

**Cabecera:** número, cliente, hora, origen, estado operativo, estado de pago, estado de cumplimiento, siguiente acción y enlace a venta relacionada.

**Resumen comercial:** líneas, SKU/variante, cantidades, precio snapshot, descuento, impuestos aplicables, envío, total y validez de cotización. Un cambio debe resaltarse y solicitar aceptación.

**Cliente/conversación:** último mensaje, intención estructurada, comunicación útil, asignado a bot/persona, botón para tomar conversación o volver a automatización. Restringir transcripciones y datos personales por rol.

**Pago:** condición, método, importe recibido, saldo/anticipo, referencia y verificación. Acciones diferentes: “Revisar comprobante”, “Registrar pago recibido”, “Registrar anticipo”, “Ver cuenta por cobrar”. El pago pendiente no siempre bloquea preparación.

**Cumplimiento:** origen, retiro/destino, estimación, checklist por línea, incidencias, transportista, tracking y evidencia permitida. Código de retiro disponible únicamente a quien lo necesita; no en la bandeja general.

**Cronología:** cambios, reservas/liberaciones, mensajes, reintentos, confirmación humana, venta, pagos y entrega. Distinguir quién ejecutó la acción: persona, sistema o proveedor.

### 7.5 Acciones contextuales

- Aceptar pedido: validar cotización, stock, consentimiento y reserva.
- Empezar preparación: registrar responsable y hora.
- Marcar listo: solo después de conteo/verificación y punto de retiro habilitado.
- Despachar: exigir origen, responsable/transportista y los datos necesarios.
- Registrar pago: importe, método, referencia y evidencia que realmente aplique.
- Entregar/retirar: validar receptor y condiciones.
- Cancelar: explicar qué se liberará y si hay aprobación, devolución o reembolso separado.

El backend devuelve las acciones válidas según estado, permisos y ubicación. React no debe inventar transiciones ni confiar en el texto del cliente.

## 8. Integración con Ventas e Inventario

Esta alineación es la parte crítica del rediseño.

### 8.1 Pedido, venta y pago

Cada pedido conserva una referencia estable a su cotización y, si corresponde, una única venta de Inventary. La bandeja debe mostrar “Ver venta” cuando esa venta exista; Ventas debe filtrar canal WHATSAPP y enlazar el pedido.

n8n no crea ventas directamente ni genera movimientos de stock por separado. La operación debe reutilizar el caso de uso transaccional de Inventary o un comando nuevo que use sus servicios, reservas, costeo, auditoría y Outbox.

La implementación actual crea la venta y consume la reserva al confirmar pago. Eso cubre algunos pedidos prepagados, pero no basta para contra entrega, pago al retirar y crédito pospago. Antes de habilitarlos, el dominio debe permitir que la venta/cumplimiento siga la política comercial aprobada mientras la situación financiera permanece pendiente. Ventas deberá representar saldo por cobrar sin simular que hubo dinero recibido. El evento exacto de registro de venta y documento fiscal requiere decisión contable/local; esta propuesta no supone que la integración fiscal electrónica esté implementada.

**Invariante:** un pedido no puede crear dos ventas ni dos salidas de inventario por webhooks repetidos, doble clic o reintentos.

### 8.2 Reserva y salida de inventario

- Cotizar consulta stock; no lo reserva.
- Aceptar pedido reserva de forma transaccional.
- Reserva se consume/libera una sola vez según la política de entrega, cancelación o expiración.
- Salida física queda asociada a la operación de venta/movimiento aprobada.
- Disponibilidad informativa no se presenta como stock reservado.
- Falta durante preparación crea incidencia y nueva cotización; no cambia cantidad silenciosamente.

La bandeja debe coordinarse con la propuesta logística existente para picking, empaque, origen, traslado, despacho y varios almacenes.

### 8.3 Descuentos

El descuento debe ser parte de la cotización de servidor con regla, valor, elegibilidad, vigencia, acumulación y permiso.

- La IA informa promociones existentes; no inventa descuentos ni usa margen interno.
- Excepciones requieren permiso y, si procede, aprobación de otra persona.
- Se guarda quién autorizó y por qué.
- La aceptación del cliente queda vinculada al importe exacto.
- Cualquier cambio de precio/descuento exige recotizar y volver a aceptar.
- Ventas manuales y WhatsApp deben obedecer la misma política, aunque los casos de uso y endpoints sean distintos.

### 8.4 Suscripciones y servicios

Los servicios de suscripción deben ir por el dominio de Suscripciones si el backend así lo define, no por una venta común. El agente puede explicar el plan y obtener confirmación, pero debe llamar el caso de uso de contratación y asociar el contrato/factura con el pedido conversacional. No combinar suscripción y mercancía en una sola transacción hasta tener coordinación de backend probada.

## 9. APIs/contratos por completar

Las capacidades de esta tabla son propuestas, no endpoints confirmados como existentes. Deben adaptarse a las convenciones actuales y versionarse en OpenAPI.

| Capacidad | Resultado esperado | Controles |
|---|---|---|
| Cotización comercial completa | Modalidad, destino, tarifa, descuento autorizado, impuestos y total final. | Recalcular en servidor, quoteId, snapshot, expiración y diferencia de precio. |
| Aceptar pedido | Pedido durable desde cotización activa con consentimiento correlacionado. | Idempotency-Key, validación y reserva transaccional. |
| Datos de cumplimiento | Origen, retiro/destino, destinatario, ventana y logística. | Cobertura, ubicación habilitada y privacidad. |
| Comandos de estado | Aceptar, empezar preparación, listo, despachar, entregar, retirar, rechazar/cancelar. | Máquina de estados, roles, ubicación y auditoría. |
| Registrar pago/anticipo | Dinero recibido o evento del proveedor verificado. | Monto, moneda, referencia, fecha, actor, conciliación e idempotencia. |
| Acciones permitidas | Comandos válidos para estado, actor y ubicación. | El servidor aplica permisos. |
| Historial de mensajes | IDs externos, estado de envío y correlación con pedido. | Deduplicación y retención mínima. |
| Consulta/comandos n8n | Acceso limitado a pedidos y acciones del canal. | Identidad técnica, scopes, límites, auditoría y sin SQL. |
| Eventos de cambio | Outbox para n8n y notificaciones. | Consumidor idempotente, correlación, reintentos y reconciliación. |

### 9.1 Datos de dominio recomendados

Mantener separados, por entidad relacionada cuando aplique:

- Identificadores de pedido, cotización, cliente verificado, consentimiento y mensaje de WhatsApp relacionado.
- Estado comercial, condición/estado de pago, estado de cumplimiento y prioridad.
- Almacén origen y punto de retiro, que pueden ser distintos.
- Destino/dirección, destinatario y tercero autorizado protegidos por permisos.
- Líneas con cantidades, precio/descuento/impuesto snapshot y referencias a reservas/movimientos.
- Subtotal, descuento, impuestos, envío, total, anticipo, pagos recibidos y saldo, calculados en servidor.
- SaleId y referencias de pago cuando existan.
- Fecha aceptada, expiración/SLA, ventana de cumplimiento y cronología.
- Correlación con proveedor de WhatsApp, ejecución n8n y evento Outbox, sin confiar en IDs generados libremente por el LLM.

No hace falta guardar todo en una tabla principal: dirección, pagos, terceros, grupos de cumplimiento y mensajes pueden residir en entidades relacionadas con permisos/retención adecuados.

### 9.2 Eventos propuestos para n8n

Ampliar los eventos actuales con eventos durables y versionados, como:
- WHATSAPP_ORDER_RECEIVED / ACCEPTED / REJECTED
- WHATSAPP_PAYMENT_PENDING_VERIFICATION / PAYMENT_RECORDED / PAYMENT_FAILED
- WHATSAPP_ORDER_PREPARATION_STARTED / READY_FOR_PICKUP
- WHATSAPP_ORDER_DISPATCHED / DELIVERED / PICKED_UP
- WHATSAPP_ORDER_INCIDENT / CANCELLED / EXPIRED

Cada evento debe incluir eventId, eventType, schemaVersion, occurredAt, orderId, orderNumber, correlación y datos mínimos. No enviar costos, margen ni datos privados innecesarios al LLM.

## 10. Arquitectura WhatsApp + n8n + agente IA opcional

### 10.1 Entrada y deduplicación

1. Recibir el webhook por HTTPS y validar autenticidad conforme al mecanismo vigente de WhatsApp.
2. Normalizar el evento; separar mensajes de cliente de acuses de entrega/lectura.
3. Deduplicar por el ID de mensaje del proveedor; un mensaje repetido recupera el resultado anterior y no crea otra orden.
4. Asociar a una referencia de cliente verificada y a una correlación estable.
5. Procesar texto/audio/imagen/documento solo si está permitido; limitar tamaño, MIME, caducidad de medios y retención.
6. Clasificar intención mediante reglas y opcionalmente IA: catálogo, cotización, compra, tracking, pago, retiro/envío, cancelación, devolución, queja o persona.
7. Llamar solo herramientas autorizadas de Inventary.
8. Consultar el resultado definitivo y redactar una respuesta fiel. Un mensaje enviado nunca prueba éxito transaccional.

### 10.2 Flujo de compra

1. Entender producto, variante, cantidad, modalidad y condición solicitadas.
2. Buscar en catálogo autorizado de Inventary.
3. Si falta certeza, ofrecer pocas alternativas y pedir elección.
4. Crear/reutilizar carrito backend y solicitar cotización oficial.
5. Mostrar resumen con líneas, descuento, envío, total, condición de pago, destino/punto y validez.
6. Recoger confirmación explícita correlacionada con el mensaje verificado.
7. Crear pedido por API idempotente y consultar el resultado.
8. Enviar número de pedido solo cuando el backend confirme que existe.
9. Enrutar al flujo de revisión, preparación, retiro, envío o pago según el estado real.

No permitir confirmación implícita cuando existan dirección ambigua, variante incierta, descuento excepcional, crédito no autorizado, datos obligatorios faltantes o total diferente.

### 10.3 Qué puede y no puede hacer la IA

**Puede:** interpretar intención, extraer datos tentativos, preguntar campos faltantes, buscar catálogo, solicitar cotización, resumir una conversación, redactar mensajes y proponer siguiente acción.

**No puede:** consultar/modificar la base directamente; asignar precio, descuento, stock, tarifa o impuesto sin API; declarar pagado por leer una captura; declarar entregado por el texto del cliente; aprobar crédito o excepciones sin permiso; exponer costos/margen/credenciales ni datos que el rol no necesita.

Crear una capa de proveedor intercambiable OpenAI/Gemini. Si falla el modelo, deben seguir funcionando las operaciones deterministas y la atención humana; la compra no puede depender de que el LLM esté disponible.

### 10.4 Transferencia humana

Transferir cuando el cliente lo pida, haya duda sobre producto/cantidad/destino/pago, exista disputa, reclamación, descuento excepcional, dato sensible, API fallida o estado inesperado.

El handoff debe llevar intención, datos confirmados, quoteId/orderId, mensajes intentados y motivo de derivación. Registrar quién tiene la conversación en ese momento para que bot y persona no contesten a la vez. La persona puede pausar y reanudar automatización explícitamente.

### 10.5 Reintentos y recuperación

- Webhook duplicado: no duplica orden ni mensaje final.
- Red falla tras crear pedido: reutilizar la misma Idempotency-Key y consultar estado.
- Mensaje saliente falla: reintento limitado, estado pendiente y alerta.
- n8n se reinicia: reanudar eventos Outbox pendientes y reconciliar órdenes no notificadas.
- LLM falla o devuelve formato inválido: fallback determinista y opción humana.
- Operación financiera falla: consultar y conciliar antes de intentar de nuevo; no cobrar a ciegas.
- Alertar sobre cola de eventos, reintentos agotados, órdenes sin notificación y estados incompatibles.

## 11. Seguridad y permisos

### 11.1 RBAC por tarea

Evaluar permisos diferenciados para: leer pedidos, ver datos personales/conversación, aceptar/rechazar, preparar, marcar listo, despachar/entregar, verificar pagos, aplicar/aprobar descuentos, aprobar crédito, cancelar/reembolsar y administrar integraciones.

El personal de bodega necesita líneas/cantidades/origen y datos de cumplimiento, pero no necesariamente historial de conversación, margen o comprobantes de pago. El backend decide el permiso; React solo refleja la autorización.

### 11.2 Credenciales e integración

La integración actual usa X-Integration-Key. Guardarla en credenciales protegidas de n8n, con rotación, límites y auditoría. No incluirla en prompt, repositorio, logs ni exportación pública de workflow. Para evolucionar, evaluar tokens de máquina/OAuth2 con scopes y expiración. No dar acceso SQL directo desde n8n.

### 11.3 Consentimiento y privacidad

Separar consentimientos para actualizaciones operativas, promociones y recomendaciones. Respetar bajas y usar plantillas aprobadas cuando lo exija la ventana/política vigente. Evitar PII completa en analítica y payloads del LLM; proteger direcciones, códigos de retiro, comprobantes y tracking. Un orderId no debe ser suficiente para exponer información privada. No usar número telefónico como única autorización para acciones económicas de alto impacto.

Toda operación económica guarda actor, origen (persona/bot/sistema), estado anterior/nuevo, fecha del servidor, motivo, referencia y correlación. Verificación de pago, descuento excepcional, crédito, entrega a tercero y devolución deben poder auditarse.

## 12. Fases de implementación

### Fase 0 — Reglas, contratos y pruebas (bloqueante)

Decidir antes de codificar:
- cuándo se registra venta por condición de pago;
- cómo registrar pago recibido, deuda, anticipo y crédito;
- cuándo consumir reserva y hacer salida de stock;
- reserva/expiración por modalidad y horario;
- horarios, capacidad, retiro urgente y envío;
- permisos de descuento, pago, crédito y cancelación;
- política fiscal/contable para venta/documento.

Entregables: máquina de estados, modelo de datos, OpenAPI, reglas, permisos y matriz de tests. No habilitar COD/pospago solo agregando un selector React.

### Fase 1 — Backoffice y cumplimiento desde un origen

- Añadir estado operativo, estado/condición de pago y cumplimiento separados.
- Separar confirmación de pedido de pago y de venta según política.
- Agregar destino, punto de retiro, prioridad y datos mínimos de entrega.
- Mantener transacciones e idempotencia para stock, venta y pago.
- Crear bandeja, filtros, página de detalle, cronología y enlace a Ventas.
- Implementar preparación, listo para retiro, despacho y cierre.
- Habilitar prepago, pago al retirar y contra entrega solo después de probar el modelo financiero.
- Agregar descuentos de servidor y aprobación por roles.

**Salida de fase:** una sola operación por pedido, stock coherente, venta enlazada cuando corresponda y estados honestos de pago y cumplimiento.

### Fase 2 — Operación avanzada

Retiro por terceros, códigos de uso único, horarios por local, pedidos fuera de horario, prioridades, SLA de reserva, comprobantes pendientes, pagos parciales autorizados, crédito pospago, transportista, tracking, intentos y evidencia de entrega.

### Fase 3 — Varias ubicaciones y operación compleja

Transferencia a punto de retiro distinto, grupos de cumplimiento, envíos parciales, líneas de varias bodegas y asignación por disponibilidad, horario, coste y capacidad. No ofrecer retiro en otra sede antes de tener traslado y recepción vinculados al pedido.

### Fase 4 — IA gradual

1. Automatización determinista sin IA para recepción, estado y handoff.
2. Copiloto que resume mensajes y redacta para aprobación humana.
3. Autonomía acotada para tareas de bajo riesgo con herramientas y confirmación explícita.
4. Evaluación por correcciones humanas, pedidos incorrectos/duplicados, handoffs, latencia, coste por pedido y reclamaciones; activar con feature flag y rollback.

Cambiar proveedor entre OpenAI y Gemini no debe cambiar el modelo comercial ni los contratos de Inventary.

## 13. Matriz de aceptación mínima

Probar backend y UI; verificar respuesta HTTP, saldos, movimientos, venta vinculada, pagos, Outbox y auditoría:

1. Pedido de uno o varios artículos y cantidades válidas.
2. Webhook repetido crea una sola orden y una sola respuesta final.
3. Error de red tras crear pedido recupera el mismo registro/reserva.
4. Stock cambia en paralelo: sin sobreventa.
5. Cotización expirada o precio cambiado: re-cotizar y volver a aceptar.
6. Descuento sin permiso o que exige aprobación: backend lo rechaza o deja pendiente.
7. Prepago pagado, comprobante sin verificar, callback duplicado y pago fallido.
8. Pago al retirar: preparación con pago pendiente y cobro posterior una sola vez.
9. Contra entrega: despacho pendiente de cobro, cobro real en entrega e intento fallido.
10. Crédito aprobado/rechazado, saldo, vencimiento y límite excedido.
11. Pedido de madrugada: recepción, reserva y expectativa de próxima atención correctas.
12. Retiro urgente con capacidad y sin capacidad inmediata.
13. Tercero autorizado: código válido una vez, expirado, intentos fallidos y tercero no autorizado.
14. Envío sin cobertura, dirección incompleta, cambio de tarifa y entrega fallida.
15. Faltante/daño durante preparación y aceptación de una nueva cotización.
16. Cancelación en revisión, preparación, despacho y después de entrega.
17. Reserva liberada una sola vez; ninguna reserva huérfana ni sobreliberación.
18. Un pedido crea una sola venta y un solo movimiento de salida.
19. Coherencia entre bandeja WhatsApp, pedido, Ventas, Inventario, pagos y auditoría.
20. Permisos: bodega no verifica dinero; auditor no ejecuta acciones no autorizadas.
21. Baja promocional y ventana cerrada: no enviar mensaje indebido; plantilla cuando corresponda.
22. Caída de IA: la bandeja y el flujo humano continúan.
23. Producto/cantidad/descuento inventado por IA: se rechaza o se pide confirmación.
24. Eventos de lectura/entrega no disparan otra compra.
25. Teclado, lector de pantalla, móvil, errores accionables y detalle sin modal largo.

## 14. Métricas de operación

Medir por ubicación, condición de pago, modalidad y canal:
- pedidos recibidos/aceptados/rechazados/expirados/cancelados;
- duplicados, fallos de idempotencia y stockouts;
- tiempo a primera respuesta, revisión y aceptación;
- tiempo de preparación y retiros no completados;
- entrega a tiempo, intentos fallidos e incidencias de transportista;
- pagos pendientes, tiempo de conciliación, contra entrega no cobrada, deuda vencida y reembolsos pendientes;
- discrepancias cotización/pedido/venta/pago;
- reservas expiradas, faltantes y anulaciones;
- transferencias a humano, correcciones del LLM y éxito por tarea;
- coste de IA/mensajería por pedido completado;
- quejas, bajas, bloqueos y notificaciones fallidas;
- margen neto y coste de descuentos/entrega cuando haya datos fiables.

No optimizar solo cantidad de pedidos o rapidez del bot. Las guardas principales son errores de dinero/stock, duplicados, incumplimientos, reclamaciones, margen y política de WhatsApp.

## 15. Decisiones pendientes del negocio

1. ¿En qué evento se registra la venta para cada modalidad: aceptación, despacho, entrega o retiro?
2. ¿Qué pagos están realmente disponibles y cuáles requieren integración futura?
3. ¿Qué reserva corresponde a prepago, pago al retirar, contra entrega, crédito y pedidos nocturnos?
4. ¿Qué almacenes son también locales de retiro y cuáles solo abastecen envíos?
5. ¿Cuáles son horarios, capacidad de preparación, zonas, tarifas y servicios de transporte?
6. ¿Quién puede autorizar y verificar a terceros?
7. ¿Qué roles aplican descuentos, confirman pago, aprueban crédito y cancelan/reembolsan?
8. ¿Qué política fiscal/contable define el momento de registro y documento?
9. ¿Qué proveedor de WhatsApp, pasarela y transportista se usará?
10. ¿Qué categorías de consentimiento, plantillas y retención se configurarán?

No deben inventarse estas reglas dentro de React ni de un prompt.

## 16. Referencias de código y documentación

### Inventary
- [WhatsAppOrdersPage.jsx](app-inventary-ecuapp/src/features/whatsapp/WhatsAppOrdersPage.jsx)
- [WhatsAppIntegrationController.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppIntegrationController.java)
- [WhatsAppCommerceService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppCommerceService.java)
- [WhatsAppOrderBackofficeController.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppOrderBackofficeController.java)
- [WhatsAppOrderBackofficeService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppOrderBackofficeService.java)
- [WhatsAppDtos.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppDtos.java)
- [WhatsAppOrder.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppOrder.java)
- [WhatsAppApiTest.java](microservices-inventary-ecuapp/src/test/java/com/ecuapp/inventary/api/WhatsAppApiTest.java)
- [Integración n8n/WhatsApp/OpenAI](microservices-inventary-ecuapp/docs/09-webflux-n8n-whatsapp-openai.md)
- [Ventas V2](PROPUESTA_UX_VENTAS_V2.md)
- [Logística de retiro y envío](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md)

### Sistemas y políticas comparables
- n8n: [pedidos, FAQ y tracking con GPT-4o](https://n8n.io/workflows/16175-handle-whatsapp-orders-faqs-and-tracking-with-gpt-4o-pinecone-and-sheets/)
- n8n: [WhatsApp Cloud API y deduplicación](https://n8n.io/workflows/20729-handle-whatsapp-customer-chats-with-openai-and-data-tables/)
- n8n: [pedidos y entregas de restaurante](https://n8n.io/workflows/7298-restaurant-order-and-delivery-system-for-whatsapp-with-gpt-4o-and-supabase/)
- Shopify: [FulfillmentOrder](https://shopify.dev/docs/api/admin-graphql/latest/objects/fulfillmentorder)
- Shopify: [retiro local](https://shopify.dev/docs/api/checkout-ui-extensions/latest/targets/checkout/local-pickup)
- Shopify: [grupos de cumplimiento](https://shopify.dev/docs/apps/build/checkout/split-carts)
- WhatsApp Business: [política oficial](https://business.whatsapp.com/policy/preview?lang=es_LA)
- Bird: [agentes de WhatsApp y transferencia humana](https://bird.com/docs/guides/whatsapp/ai-agents)

Estas referencias describen patrones y políticas, no funcionalidades ya desplegadas en Inventary. Cada capacidad futura requiere contrato, migración, permisos, pruebas y operación reales antes de activarse.
