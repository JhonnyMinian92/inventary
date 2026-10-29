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




---

# Revisión UX prioritaria — acordeón operativo por pedido y preparación proactiva del vendedor

**Fecha de revisión:** 28 de octubre de 2026  
**Motivo:** detallar el comportamiento visual y la información que debe ver el dueño del negocio al desplegar cada pedido, para que pueda concretar la venta, detectar datos faltantes y anticipar objeciones o incidencias.  
**Precedencia:** esta ampliación concreta y prevalece sobre las partes generales de la sección 7 que no describan con suficiente precisión el acordeón de pedido.

## 17. Decisión de interfaz: cada fila de pedido se despliega en acordeón

La vista principal conservará la bandeja y los filtros, pero **cada pedido debe ser una fila expandible que despliegue su ficha completa sin sacar al usuario del contexto de la lista**. El dueño no debería abrir cinco modales ni navegar a distintas pantallas para descubrir si falta la dirección, si el cliente aprobó el total o si hay un compromiso que se prometió cumplir.

El patrón recomendado es una tabla con acordeón asociado a cada pedido:

- **Fila compacta cerrada:** resume identidad, importe, modalidad, estado de pago, estado operativo, prioridad y siguiente acción.
- **Fila expandida:** ocupa todo el ancho útil de la tabla y muestra un panel de información con secciones claramente agrupadas.
- **Subsecciones internas:** se pueden contraer de manera independiente; las alertas que bloquean la venta no se deben poder ocultar por defecto.
- **Acciones:** el dueño puede continuar el trabajo desde el acordeón. Las operaciones largas (editar destino, aprobar crédito, resolver una excepción o completar datos complejos) abren una página o un stepper enfocado, no un modal extenso.
- **Navegación:** desplegar el pedido no debe activar accidentalmente el enlace a detalle ni ejecutar una acción.

Por defecto se recomienda mantener **un pedido expandido a la vez** para evitar que una lista con varias órdenes se vuelva interminable. Si la investigación con usuarios demuestra que necesitan comparar pedidos lado a lado, se puede habilitar conservar más de uno abierto, siempre con un control claro de colapsar todos. Recordar el pedido abierto cuando se abre su detalle y se regresa a la bandeja, siempre que la orden siga en la página actual.

### 17.1 Maqueta textual de la fila cerrada

La fila debe responder, sin desplegar, estas preguntas: quién es, qué compró, cuánto vale, cómo espera recibirlo, qué ocurre con el dinero y qué debe hacer ahora el operador.

| Zona | Contenido recomendado |
|---|---|
| Pedido | Número corto, fecha/hora local, edad del pedido y marca “Nuevo mensaje” si hay actividad reciente relevante. |
| Cliente | Nombre que proporcionó, si está disponible, más WhatsApp parcialmente enmascarado según permisos. Si no hay nombre verificado, usar una etiqueta neutra como “Cliente sin nombre”. |
| Necesidad | Resumen de una línea: productos y cantidades principales, más contador de artículos restantes. |
| Cumplimiento | Retiro / envío, local/origen, fecha o ventana solicitada si fue capturada. No inferir dirección ni prometer una hora sin respaldo. |
| Importe | Total cotizado, moneda y un indicador si existe descuento o coste de envío. El desglose aparece al expandir. |
| Estados | Badge de pedido y badges independientes de pago y cumplimiento. No usar una sola etiqueta “Confirmado” para significados distintos. |
| Atención | Prioridad explícita, operador responsable o “Sin asignar”, y tiempo desde el último mensaje entrante. |
| Próximo paso | CTA principal como “Completar datos”, “Revisar total”, “Aceptar pedido”, “Preparar”, “Registrar cobro” o “Resolver incidencia”. |

La fila cerrada debe señalar si hay un impedimento, pero no mostrar un semáforo sin explicación. Ejemplos: “Falta confirmar el local de retiro”, “Total enviado; falta aceptación del cliente”, “Comprobante por verificar”, “Cliente pide retiro urgente; horario/capacidad sin confirmar”.

### 17.2 Comportamiento exacto del acordeón

1. El control de despliegue será un botón con icono y texto accesible, por ejemplo “Ver detalles del pedido WA-…”, con aria-expanded y aria-controls.
2. Clicar el número de pedido o el botón “Ver detalle” puede navegar a la ruta del pedido; clicar el control de acordeón solo despliega o colapsa la fila.
3. Si llega un mensaje nuevo para el pedido expandido, la UI puede actualizar un indicador “Nuevo mensaje” sin saltar el scroll ni reemplazar los datos que el usuario está editando.
4. Antes de ejecutar una acción, actualizar o consultar el estado real del backend. Si el estado cambió en otra sesión, mostrar el conflicto y las acciones vigentes.
5. Conservar filtros, página, búsqueda y acordeón abierto al volver de un stepper o de la ruta de detalle.
6. Mostrar skeletons específicos al expandir; una falla al cargar el detalle no debe vaciar la bandeja ni ocultar la fila.
7. Permitir copiar el número del pedido y, según permisos, el teléfono/dato de contacto; confirmar visualmente el copiado y no registrar secretos en analytics.
8. Si existen muchos artículos, mostrar los primeros y una acción “Ver todos los artículos” dentro de la sección, no ensanchar la fila interminablemente.
9. Las secciones largas deben poder contraerse, pero la alerta de bloqueo, total vigente y próxima acción permanecen visibles al inicio.
10. El comportamiento debe funcionar con teclado: foco visible, Enter/Espacio para desplegar, orden de tabulación lógico y nombres accesibles.

## 18. Contenido completo del pedido expandido

La ficha expandida es la zona principal para reunir **todo lo que se capturó de la conversación, lo que se validó por el backend, lo que el cliente aceptó y lo que todavía falta**. No se deben mezclar datos confirmados, deducciones de IA y campos pendientes como si tuvieran el mismo nivel de certeza.

### 18.1 Barra superior fija de decisión

Al desplegar, mostrar una banda de resumen con:

- Número de pedido y antigüedad.
- Nombre y WhatsApp parcial/visible según permiso.
- Total de la última cotización vigente y su expiración.
- Estado comercial/operativo, estado de pago y estado de cumplimiento.
- Prioridad y responsable de la conversación.
- Indicador de preparación comercial:
  - **Listo para continuar:** no hay bloqueos conocidos para la acción siguiente.
  - **Falta información:** detalla los campos exactos que debe pedir el operador.
  - **Requiere aprobación:** indica qué decisión y qué rol hacen falta.
  - **Conflicto detectado:** por ejemplo precio vencido, stock cambiado, pago no conciliado o dirección contradictoria.
- CTA de la siguiente acción permitida y un botón secundario “Ver cronología”.

Nunca mostrar un “porcentaje de probabilidad de cerrar la venta” ni etiquetar al cliente como problemático si no existe una métrica validada y legítima. El indicador se calcula a partir de reglas operativas verificables, no de una valoración psicológica de la persona.

### 18.2 Sección A — Cliente e identidad capturada

Debe mostrar todos los datos efectivamente obtenidos que sean pertinentes para ejecutar el pedido:

- Nombre de perfil de WhatsApp, si lo proporciona el canal.
- Nombre indicado por la persona y, separado, nombre de facturación si aplica.
- Identificador de contacto verificado por WhatsApp y teléfono completo solo para roles autorizados; por defecto se muestra parcialmente enmascarado.
- País/código de país, idioma declarado y canal/origen de conversación.
- Correo, identificación tributaria o datos de facturación únicamente si fueron necesarios, obtenidos y autorizados para la operación.
- Historial de compras y pedidos anteriores, si el usuario tiene permiso y el contacto está asociado con certeza; las coincidencias posibles deben aparecer como “posible duplicado” y no fusionarse automáticamente.
- Nombre del destinatario si difiere del comprador.
- Persona autorizada para retirar, si aplica; estado de autorización (pendiente/verificado), sin mostrar códigos de recogida activos a todo el personal.
- Preferencias declaradas relevantes para el servicio, como horario preferido o instrucción de entrega, indicando cuándo y en qué contexto se registraron.
- Consentimientos independientes: actualización del pedido, promociones, recomendaciones, fecha/origen y revocación cuando exista.
- Campo de nota interna con autor y hora, no visible para el cliente.

Para cada campo, mostrar su procedencia y verificación:
- **Capturado del cliente:** el cliente lo expresó en la conversación.
- **Confirmado:** la persona aceptó explícitamente el dato/resumen.
- **Validado por sistema:** el backend, proveedor o regla comprobó el dato.
- **Propuesto por IA:** extracción que aún requiere revisión/confirmación.
- **Pendiente / ausente / contradictorio:** falta completar o hay fuentes en conflicto.

No inferir edad, ingresos, personalidad, vulnerabilidad ni “dificultad” del cliente a partir de la forma de escribir. Guardar preferencias solo cuando sean útiles, proporcionadas y explicables. Una queja o una solicitud exigente debe registrarse como un hecho específico de servicio, no como un juicio sobre la persona.

### 18.3 Sección B — Conversación y contexto comercial

Esta sección debe preparar al vendedor para retomar la conversación sin pedir al cliente que repita lo ya explicado.

Debe incluir:

- Último mensaje entrante en texto legible y fecha/hora.
- Vista de conversación con mensajes entrantes y salientes en orden cronológico, remitente, hora y estado de entrega/lectura cuando la API lo proporcione.
- Archivos adjuntos recibidos: imagen/audio/documento, vista previa segura, fecha, tipo y enlace al mensaje original. Los archivos sensibles se restringen por rol y retención.
- Transcripción de audio si existe; distinguir “transcripción automática” de mensaje de texto enviado por el usuario.
- Resumen de necesidad expresada: qué busca, cantidad, modelo/variante y uso descrito por la persona, incluyendo dudas aún abiertas.
- Objeciones textuales o preguntas importantes: precio, calidad, garantía, disponibilidad, plazo, cambio, forma de pago, envío o confianza. Mostrar la frase o mensaje de origen y no solo un resumen abstracto.
- Compromisos ya comunicados por la empresa: precio citado, fecha/ventana prometida, recogida, envío o devolución de llamada, con quién y cuándo se comunicó.
- Preguntas que el cliente hizo y siguen sin respuesta.
- Responsable actual: persona asignada, bot/IA o sin asignar. Estado de handoff y si las respuestas automáticas están pausadas.
- Acciones de automatización: workflow, última etapa completada, última respuesta de API y error accionable. No mostrar credenciales ni logs técnicos excesivos.
- Botón “Tomar conversación”, “Solicitar ayuda” o “Reanudar automatización” según permisos/estado.
- Acción de respuesta rápida basada en plantillas revisadas, editables antes del envío; no enviar un texto sugerido por IA sin mostrar el contenido y el destinatario.

El resumen de IA debe llevar etiqueta “Borrador generado por IA” y enlaces a los mensajes que lo sustentan. Si no existe información suficiente, debe indicar “No identificado” en lugar de completar con una suposición. La conversación original sigue siendo la referencia para resolver conflictos.

### 18.4 Sección C — Pedido, artículos y cotización

Mostrar el detalle completo de la cotización/pedido y su historial:

- ID y número de carrito, cotización y pedido, con vínculo entre ellos.
- Cada línea: imagen/miniatura si existe, SKU, nombre, variante, unidad de medida, cantidad, precio unitario snapshot, subtotal y disponibilidad pertinente.
- Cantidad solicitada frente a cantidad confirmada, preparada, despachada y entregada, si las fases correspondientes existen.
- Producto sustituto solicitado/ofrecido y aceptación expresa, si existe.
- Precio previo y precio actual solo cuando la diferencia sea útil; no reemplazar el snapshot aceptado con el precio actual del catálogo.
- Descuento por línea o pedido, código/regla, motivo, elegibilidad, valor, actor que lo autorizó y vigencia.
- Subtotal, impuestos calculados por backend, coste de envío, descuentos, anticipo, saldo y total final.
- Moneda, versión de la cotización, fecha/hora de emisión y expiración.
- Qué total se mostró al cliente, cuándo se mostró y qué mensaje/respuesta registró su aceptación.
- Si el precio/stock cambió después de cotizar: diferencia exacta, cotización anterior, nueva cotización y aceptación pendiente.
- Notas de regalo, empaque o requisitos especiales expresados, únicamente si la operación puede cumplirlos.

Mostrar una banda explícita de “Importe que aceptó el cliente” y compararla con el importe vigente de backend. Si no hay confirmación de ese total, no permitir aceptar el pedido silenciosamente. Los cálculos de precios, descuentos, tasas y cargos siempre provienen del backend. El agente no puede editarlos directamente.

### 18.5 Sección D — Pago, cobros y saldo

Mostrar por separado la condición de pago y el estado financiero real:

- Condición acordada: prepago, pagar al retirar, contra entrega, crédito pospago o esquema de anticipo, si se implementan y autorizan.
- Estado actual: sin pagar, comprobante enviado, pendiente de verificación, parcialmente pagado, pagado, pago fallido, reembolso pendiente o reembolsado.
- Método seleccionado/declarado, importe esperado, importe recibido, anticipo, saldo pendiente, moneda y vencimiento del saldo.
- Referencia de pago y archivo de comprobante, con marca “no verificado” si corresponde.
- Actor que verificó el pago, fecha/hora, referencia de conciliación y fuente de verificación.
- Evento del gateway o conciliación si aplica, con estado de validación.
- Historial de pagos parciales y reembolsos como movimientos independientes.
- Para crédito: aprobación, responsable, límite aplicable y fecha de vencimiento, solo a usuarios con permiso financiero.
- CTA contextual: “Revisar comprobante”, “Registrar dinero recibido”, “Registrar anticipo”, “Ver saldo por cobrar” o “Revisar evento del proveedor”.

La UI debe impedir que un operador confunda “el cliente dice que transfirió” con “el pago está conciliado”. Un agente IA puede extraer referencia/monto y marcar el caso para revisión; no puede certificar que el dinero llegó.

### 18.6 Sección E — Retiro, envío y cumplimiento

Debe reunir las instrucciones que normalmente obligan a volver al chat para buscar detalles:

**Para retiro**
- Ubicación exacta, dirección del punto de retiro y horario validado.
- Instrucciones de acceso/parqueo o referencias útiles que el negocio haya registrado.
- Fecha/hora solicitada y estado de confirmación de esa ventana.
- Estado de preparación, persona que prepara, conteo por línea y faltantes.
- Código de retiro: estado y expiración; valor secreto solamente al rol autorizado, nunca en la tabla general.
- Comprador y receptor autorizado si son diferentes, estado de autorización y verificación efectuada.
- Hora en que se marcó listo, hora de notificación al cliente y fecha/hora de recogida.
- Resultado de la entrega: recogido, no se presentó, código inválido, tercero no autorizado o incidente.

**Para envío**
- Dirección de entrega estructurada, referencia, zona/cobertura y persona destinataria, con protección por rol.
- Tarifa/condición de envío, estimación y fuente de la promesa.
- Origen, picking/embalaje y lista de verificación.
- Transportista, guía/seguimiento, hora de despacho y eventos recibidos.
- Intentos de entrega, motivo de fallo, próxima acción, cargo adicional si existe y aceptación de ese cargo.
- Evidencia de entrega permitida, receptor y fecha/hora.
- Restricciones o instrucciones declaradas por el cliente, como horario de recepción o llamada previa; separar las instrucciones recibidas de las restricciones confirmadas por el transportista.

No mostrar “entrega hoy” sin una promesa respaldada. Si el dato de transporte no existe, utilizar un estado honesto y una tarea pendiente, en vez de inventar un seguimiento.

### 18.7 Sección F — Riesgos operativos, datos faltantes y plan de resolución

Este bloque debe ser la ventaja principal para el dueño: no solo muestra qué datos existen, sino qué podría impedir cerrar o cumplir el pedido.

La alerta debe ser concreta, accionable y basada en reglas. Ejemplos:

| Señal detectada | Qué mostrar | Próxima acción sugerida |
|---|---|---|
| Falta un dato crítico | “Falta confirmar el número de casa antes de cotizar el envío.” | Preguntar solo ese dato y conservar el resto. |
| Dos datos se contradicen | “En el mensaje inicial pidió retiro; después proporcionó una dirección de entrega.” | Pedir que elija una modalidad. |
| Total no aceptado | “El total cambió de $X a $Y después de añadir envío.” | Enviar el nuevo resumen y registrar aceptación. |
| Stock incierto/agotado | “La disponibilidad consultada cambió; aún no hay reserva.” | Revalidar stock y ofrecer alternativas válidas. |
| Comprobante sin verificar | “Se adjuntó una captura; no existe conciliación registrada.” | Asignar a una persona autorizada. |
| Cliente va en camino | “Solicita retiro prioritario, pero aún no está marcado listo.” | Verificar stock, horario y capacidad de preparación. |
| Pedido fuera de horario | “Recibido a las 23:40; la tienda está cerrada.” | Informar siguiente horario y definir reserva según política. |
| Entrega por tercero | “La persona que retirará no está autorizada todavía.” | Confirmar autorización por canal verificado. |
| Crédito solicitado | “No existe aprobación de pago pospago vinculada.” | Retener esa condición y solicitar aprobación financiera. |
| Promesa previa sin estado | “Se comunicó entrega para mañana; no hay despacho/transportista registrado.” | Revisar la promesa y corregir comunicación si no puede cumplirse. |
| Cliente reclama o pide persona | “Solicitud de atención humana pendiente.” | Pausar bot, asignar responsable y conservar el contexto. |
| Error n8n/API | “No se obtuvo confirmación final de la API; resultado incierto.” | Consultar por la misma clave antes de reintentar. |

No presentar un “riesgo de cliente” ni asignar adjetivos como difícil, molesto o problemático. Deben mostrarse hechos verificables y compromisos incumplidos o pendientes. Esto ayuda a atender bien incluso un cliente que exige exactitud o expresa frustración, sin perfilarlo psicológicamente.

El bloque incluye:
- datos críticos completos, incompletos, no verificados o en conflicto;
- errores/advertencias con gravedad y fecha;
- próxima acción sugerida, responsable y fecha límite;
- compromisos ya hechos al cliente;
- planes de contingencia autorizados, por ejemplo alternativa de stock, nueva ventana, reembolso o cambio de modalidad, sin ejecutarlos automáticamente;
- explicación de por qué una acción está bloqueada;
- botón para resolver o asignar cada incidencia y registrar el resultado.

### 18.8 Sección G — Historial, auditoría y documentos vinculados

Una cronología ordenada por hora local y con zona horaria explícita debe listar:

- mensaje entrante/saliente y su estado de envío, cuando esté disponible;
- carrito, cotización, confirmación del cliente y creación del pedido;
- reserva, ajuste o liberación de stock y sus referencias;
- cambio de modalidad, dirección, cantidad o condición;
- descuento propuesto, aprobado o rechazado;
- pago recibido, conciliación y reembolso, separados;
- inicio/final de preparación, despacho, intento de entrega, retiro o entrega;
- cambios de responsable, pausas/reanudaciones de IA y transferencias a persona;
- ejecuciones n8n con estado y correlación, sin datos secretos;
- vínculo a la venta, devolución, pago y documento generado cuando existan.

Los comentarios internos deben etiquetarse explícitamente “Solo equipo”; nunca se envían a WhatsApp por accidente. Las notas importantes requieren autor y hora. No borrar la evidencia original cuando se corrija un dato; guardar una corrección auditable con su procedencia.

## 19. Experiencia proactiva para concretar la venta y resolver excepciones

### 19.1 “Qué falta para poder continuar”

El sistema debe construir un checklist dinámico de requisitos por escenario y mostrar únicamente los bloqueos relevantes. Ejemplo para envío contra entrega:

- artículos y cantidades aceptados;
- cotización vigente y total aceptado;
- cobertura y tarifa de envío confirmadas;
- dirección/destinatario verificados;
- condición contra entrega permitida;
- almacén y reserva válidos;
- datos para despacho completos.

Cada paso tendrá estado: completo, pendiente, requiere confirmación o requiere aprobación. El dueño ve de inmediato qué debe preguntar y no necesita memorizar la política.

Para retiro urgente, el checklist es distinto: punto de retiro abierto, stock/reserva, capacidad de preparación, pedido preparado, notificación enviada y condiciones de pago conocidas.

### 19.2 “Qué ya prometimos”

Guardar y mostrar compromisos explícitos: monto, descuento, fecha/ventana, ubicación, envío, garantía o devolución comunicados. Cada compromiso debe enlazar al mensaje o evento que lo originó. Si el negocio cambia una promesa, el sistema debe avisar antes de enviar la nueva respuesta.

No dejar que el agente escriba “sí, llegamos hoy” si no existe cobertura/fecha confirmada. Las plantillas de respuestas deben distinguir hechos confirmados, estimaciones y propuestas.

### 19.3 Respuestas sugeridas para objeciones

El panel puede ofrecer plantillas editables y contextualizadas, siempre basadas en información autorizada:

- Precio: desglose real, descuento autorizado y total final.
- Disponibilidad: stock/reserva actual y alternativas existentes.
- Tiempo: horario y estimación validada, o explicación honesta de que se debe confirmar.
- Calidad/compatibilidad: especificaciones del catálogo, no afirmaciones inventadas.
- Garantía/devolución: política vigente aprobada por negocio.
- Pago: métodos y condiciones realmente soportados.
- Entrega/retiro: instrucciones confirmadas, no promesas hipotéticas.
- Retraso o error: reconocer el problema, indicar la siguiente acción y un responsable.
- Solicitud insistente: resumir la petición con respeto y ofrecer un camino claro; no responder de forma defensiva.

Las sugerencias pueden recibir una etiqueta de fuente como “Catálogo”, “Política de devolución”, “Cotización vigente” o “Estado de logística”. No deben enviarse automáticamente en casos económicos, reclamos, cambios de precio, datos contradictorios o baja confianza. El operador puede editar y aprobar la respuesta.

### 19.4 Prevención de que el cliente tenga que repetir información

Antes de solicitar un dato, comprobar si ya está capturado, validado o en la conversación. Si se necesita confirmación, formular una pregunta específica y resumir solo los datos relevantes.

Ejemplo: “Tengo anotado retiro en la sede Centro para hoy. ¿Confirmas esa sede, o prefieres envío a la dirección que compartiste?” Esto evita asumir cuál de dos mensajes contradictorios debe prevalecer.

### 19.5 Clientes exigentes, reclamos y recuperación del servicio

No asumir mala intención por mensajes repetidos, mayúsculas o tono enfático. Preparar al operador para atender situaciones exigentes por hechos:

- quejas ya expresadas y su mensaje de origen;
- compromisos de la empresa que vencieron;
- información que el cliente corrigió más de una vez;
- intentos de contacto sin respuesta;
- artículos, pagos o entrega con incidencia;
- solicitud explícita de hablar con un responsable;
- solución ofrecida, aceptada y pendiente.

Al abrir el acordeón, si existe un compromiso vencido, mostrar primero una tarjeta de servicio: qué se prometió, qué ocurrió, qué falta hacer y quién es responsable. Ofrecer el procedimiento autorizado (revisión, reemplazo, cancelación, reembolso o escalamiento) sin conceder automáticamente compensaciones económicas.

Evitar lenguaje como “cliente problemático” o etiquetas inferidas de personalidad. Clasificar el incidente (retraso, diferencia de precio, pedido incompleto, comunicación fallida, calidad, pago, etc.) y priorizar por impacto y plazo, no por una valoración subjetiva de la persona.

## 20. Estados de información: capturado, confirmado, verificado y pendiente

El frontend necesita poder diferenciar los niveles de certeza de cada dato. Proponer estos metadatos para los campos importantes:

- valor actual;
- tipo de dato y quién lo necesita;
- fuente (mensaje entrante, dato de cliente existente, formulario del vendedor, proveedor, API de Inventary, IA);
- fecha/hora y mensaje/evento original;
- estado de verificación;
- si el cliente lo confirmó;
- quién lo corrigió y motivo;
- si es dato obligatorio para una acción específica.

Estados visibles recomendados: **Capturado**, **Confirmado por cliente**, **Validado por sistema**, **Propuesto por IA**, **Pendiente** y **Contradictorio**. No requiere mostrar todos los metadatos todo el tiempo: en el acordeón se enseña una etiqueta y se puede ver la procedencia al abrir el detalle del campo.

La IA puede extraer un borrador, pero los campos que influyen en la obligación económica —producto/variante, cantidad, total/descuento, modalidad/dirección, condición de pago y autorización de tercero— deben confirmarse según su importancia antes de comprometer el pedido. No confiar en texto generado por IA como consentimiento.

## 21. Especificación responsive, accesibilidad y rendimiento

### Escritorio

- Mantener filtros/colas arriba y tabla debajo.
- Acordeón a todo el ancho de la tabla, con panel interior de dos columnas: operación/comercial en una, cliente/conversación y contexto en otra cuando el ancho lo permita.
- En el borde superior del panel, conservar visible el resumen, el bloqueo principal y la siguiente acción.
- No fijar paneles laterales muy anchos que compriman la lista y hagan ilegibles los datos.

### Tablet

- El acordeón pasa a una columna principal con subpaneles/mini tarjetas.
- Las acciones prioritarias se mantienen visibles; las secundarias se agrupan en un menú “Más acciones”.
- No se deben requerir hover para descubrir información importante.

### Móvil

- Las filas se transforman en tarjetas expandibles.
- El resumen superior muestra pedido, cliente, total, modalidad, estados y acción próxima.
- Secciones internas apiladas; las acciones críticas pueden ubicarse en una barra inferior, respetando el área segura del dispositivo.
- Ninguna tabla o formulario obliga a hacer scroll horizontal para ejecutar la operación.
- Los detalles sensibles permanecen ocultos hasta que el rol autorizado los solicite.

### Accesibilidad

- Contraste suficiente, foco visible, estructura de encabezados clara, etiquetas accesibles y mensajes de error asociados al campo.
- Botón de acordeón con aria-expanded/controls y soporte teclado.
- Los badges usan texto además de color.
- No mover automáticamente el foco al llegar un mensaje nuevo.
- Para una acción exitosa, anunciar en una región accesible el resultado que realmente devolvió el backend.
- Los controles se dimensionan para uso táctil.

### Rendimiento y estados de interfaz

- Cargar de forma diferida los datos de conversación y adjuntos cuando se expande un pedido; no descargar todas las transcripciones al montar la lista.
- Enmascarar datos por rol en servidor, no solo con CSS.
- Paginación, búsqueda con debounce, filtros mantenidos y expansión sin volver a consultar toda la tabla innecesariamente.
- Evitar que una notificación frecuente cierre el acordeón o reemplace campos editados.
- Incluir estados separados de carga, error de detalle, sin conversación registrada, permiso insuficiente, dato no disponible y sincronización atrasada.
- Si falla el servicio de conversación, el propietario debe seguir viendo la cotización, los datos de inventario y el estado del pedido; mostrar la sección de conversación como degradada, no declarar vacío todo el pedido.

## 22. Datos y contratos necesarios para que el acordeón sea real

La UI solo puede mostrar la información que el backend conserva y expone. Hoy el DTO de pedido no contiene un historial de mensajes ni la mayoría de los campos de fulfillment descritos. Por tanto, el desarrollo debe incluir un contrato de detalle ampliado con secciones o recursos relacionados, no fingir que basta con rediseñar React.

Como mínimo, el detalle ampliado debe proporcionar:

1. **Resumen:** estados independientes, siguiente acción permitida, bloqueos, fecha de última actividad y cotización/venta vinculadas.
2. **Cliente:** identidad/canales verificados, datos de facturación o entrega permitidos, autorización de tercero, preferencias declaradas y consentimientos.
3. **Conversación:** mensajes/eventos autorizados, adjuntos, ID externo del mensaje, estado de envío, resumen de IA con fuentes y asignación.
4. **Pedido/cotización:** líneas, snapshots, importes, descuentos, impuestos, envío, expiración y aceptación del cliente.
5. **Pago:** condición, estado, transacciones/anticipos, referencia, verificación, saldo, vencimiento y reembolsos relacionados.
6. **Cumplimiento:** origen, destino, ubicación de retiro, preparación, asignación, tracking, intentos, destinatario y prueba de entrega.
7. **Incidencias/compromisos:** tipo, gravedad, origen, texto relacionado, responsable, fecha límite y resolución.
8. **Cronología:** evento, actor, fuente, fecha y correlación.
9. **Integración:** workflow/correlation ID, estado del evento, último intento, error recuperable y estado de notificación, sin credenciales.

Se recomienda una respuesta paginada para la lista con un resumen ligero, y un endpoint de detalle autorizado al abrir el acordeón. El backend debe controlar acceso por campo y rol. El payload de la bandeja no debe incluir transcripciones largas, archivos, documentos o códigos de retiro para todos los pedidos.

Algunas entidades o campos pueden requerir migraciones y nuevos servicios. No se debe exponer directamente el historial completo del proveedor WhatsApp a toda persona con permiso genérico de lectura de ventas.

## 23. Pruebas de aceptación adicionales del acordeón

Sumar estas pruebas a la matriz del documento:

1. Cada fila se expande por teclado y ratón, sin navegar accidentalmente; aria-expanded cambia correctamente.
2. La tabla mantiene filtros/paginación al desplegar, colapsar y regresar de una acción.
3. Una orden sin historial de conversación muestra “No hay mensajes sincronizados” en vez de inventar actividad.
4. Datos extraídos por IA se muestran como borrador y no se confunden con datos confirmados.
5. Un teléfono, una dirección o un código sensible se ocultan a un rol que no tenga autorización, también en las respuestas de API.
6. Una contradicción entre retiro y envío crea un bloqueo accionable y pide una elección explícita.
7. Una promesa anterior vencida se resalta y vincula al mensaje que la originó.
8. Un total diferente al aceptado impide cerrar la venta sin nueva aceptación.
9. Un comprobante adjunto pero sin conciliación permanece pendiente y no habilita una etiqueta de pago confirmado.
10. En contra entrega, la fila puede estar “En entrega” y “Por cobrar” simultáneamente, con estados independientes.
11. Pedido nocturno muestra horario y próxima acción reales; no promete despacho inmediato sin capacidad.
12. Retiro por tercero oculta el código de recogida en listas y exige validación de autorización.
13. Al recibir un nuevo mensaje, el panel actualiza la actividad sin mover el scroll ni borrar la edición.
14. Error de n8n/API explica el resultado incierto y ofrece reconsulta/reintento idempotente; no crea un pedido adicional.
15. Respuesta de IA sugerida cita su fuente, permite editar y exige aprobación según el tipo de acción.
16. Solicitud explícita de persona pausa el bot y conserva el contexto y la responsabilidad.
17. El bloque “Qué falta para continuar” cambia dinámicamente al completar un dato y no sigue mostrando una alerta resuelta.
18. Acciones de negocio desde el acordeón comprueban el estado más reciente en backend y tratan conflictos concurrentes.
19. En móvil, los datos clave y CTA caben en una tarjeta accesible, sin scroll horizontal.
20. Los campos ausentes, contradichos, vencidos y no sincronizados se distinguen claramente de un valor vacío normal.

## 24. Referencias externas específicas para el patrón de bandeja y acordeón

- [Shopify: ver y gestionar la información del pedido](https://help.shopify.com/es/manual/fulfillment/managing-orders/managing-order-details) — muestra productos/importes, cronología, notas internas, adjuntos y enlaces a registros relacionados; los valores del pedido permanecen como snapshot.
- [Shopify: gestionar pedidos](https://help.shopify.com/en/manual/fulfillment/managing-orders) — separa captura de pago y cumplimiento, con búsqueda/filtros, preparación, modificaciones, devoluciones y análisis de riesgo.
- [HubSpot: diseño de la página de registro y cronología de actividad](https://knowledge.hubspot.com/records/work-with-records) — organiza propiedades, actividad y registros relacionados en secciones/cards expandibles en lugar de colocar toda la información en una sola tabla.
- [Respond.io: bandeja compartida y panel de detalles de contacto](https://respond.io/help/inbox/getting-started-with-inbox) — referencia patrones de bandejas, asignación, datos de contacto, actividades internas y adjuntos.
- [Respond.io: detalles y campos del contacto](https://respond.io/help/contacts/contact-details) — referencia campos personalizados, canales, etiquetas y asignación.
- [Política oficial de mensajes de WhatsApp Business](https://business.whatsapp.com/policy/preview?lang=es_LA) — consentimiento explícito, protección de datos y comunicaciones esperadas.

Estos sistemas inspiran la organización y la interacción; no implican que Inventary ya tenga sincronización de conversación, adjuntos, asignación de agente, timeline completo ni todos los estados descritos.

## 25. Criterio final de aceptación del diseño

El dueño del negocio debe poder desplegar cualquier pedido y, en menos de un minuto y sin buscar el chat manualmente:

- entender lo que pidió el cliente y por qué;
- ver todo lo capturado con su procedencia y grado de certeza;
- distinguir lo que ya confirmó el cliente y la empresa de lo que solo sugirió una IA;
- conocer el total vigente que el cliente aceptó, la condición de pago y cualquier saldo;
- comprobar dónde y cómo se prepara/entrega el pedido;
- detectar datos faltantes, contradicciones, compromisos vencidos y problemas de integración;
- conocer la siguiente acción segura, el responsable y lo que bloquea la operación;
- responder al cliente con datos de una fuente real y registrar esa comunicación;
- conservar una trazabilidad auditable hasta la venta, el inventario, el pago y el cumplimiento.

La pantalla no debe intentar predecir ni controlar la personalidad del cliente. Debe **preparar al negocio con evidencia, contexto, políticas, alternativas permitidas y acciones concretas**, de modo que incluso una conversación difícil se atienda con claridad y sin promesas o cargos inventados.

---

## Adenda obligatoria — Facturación SRI para pedidos WhatsApp y WebView

Contrato común: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

Los pedidos de WhatsApp, WebView y web deben utilizar la misma creación de venta y el mismo flujo de facturación fiscal; no se implementa un generador fiscal separado dentro del bot o de n8n.

- El checkout debe capturar/confirmar los datos de receptor que necesite el tipo de identificación y el documento, y el contexto fiscal de emisión válido. No debe confundir el local de retiro/almacén origen con el establecimiento que emite la factura.
- La venta debe transmitir al backend fiscal la venta confirmada, sus líneas, snapshot del receptor, contexto fiscal, referencia idempotente y la prevalidación/desglose tributario acordado.
- Inventary no construye el XML, no firma ni genera secuenciales. El backend fiscal devuelve el número real, la clave de acceso, el establecimiento/punto usados y el estado. El bot no informa «factura autorizada» hasta que el estado recibido lo confirme.
- Un pedido, una venta, una reserva de stock, un pago y una factura son estados distintos. La aceptación del pedido no demuestra que el pago llegó ni que la factura fue autorizada.
- Los mensajes pueden comunicar «solicitud de factura registrada/en proceso» cuando ese sea el resultado; no deben adjuntar un enlace sin permiso o mostrar un número inventado.
- Los reintentos usan el mismo Idempotency-Key/operationId y consultan el estado existente antes de crear otra solicitud. Los callbacks del backend fiscal se deduplican.
- Una cancelación antes de que exista factura fiscal no es igual que una solicitud de anulación de una factura autorizada. Una devolución válida de artículos normalmente requiere tramitar el documento fiscal correctivo aplicable, como una nota de crédito, no anular automáticamente la factura original.
- n8n puede notificar estados fiscales confirmados, errores y tareas que requieren a una persona; no genera XML, firma, emite secuenciales, ni escribe directamente estados fiscales o movimientos de inventario.
- El bot explica el estado real de la devolución de mercadería, del documento fiscal y del reembolso por separado.

La resolución NAC-DGERCGC25-00000017 cambia al día 7 del mes siguiente el plazo del artículo 3 de la resolución de anulación, con vigencia desde el 1 de enero de 2026. Las facturas transmitidas como consumidor final tienen restricciones especiales. El flujo automatizado debe usar la regla consolidada del backend fiscal, no una fórmula o texto desactualizado en el bot.

Pruebas adicionales: pedido WhatsApp/WebView que termina con factura autorizada; emisión pendiente/rechazada; timeout y reintento sin duplicación; establecimiento cerrado/RUC no elegible; devolución parcial con nota de crédito vinculada; notificación que no se envía como autorizada antes del callback/consulta definitivo.

---

## Adenda adicional — Cumplimiento de proveedor fiscal en el checkout

Contrato fiscal canónico: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

El checkout por web, WhatsApp y WebView debe usar la misma configuración fiscal central. No debe solicitar al cliente ni al operador que introduzcan manualmente el RUC del proveedor tecnológico. Cuando aplique la Resolución NAC-DGERCGC26-00000027, el backend fiscal debe insertar el RUC del proveedor del sistema de facturación en la sección de información adicional del comprobante según la ficha técnica vigente. La norma fue publicada en el Registro Oficial No. 335 el 28 de julio de 2026 y fija un plazo de 60 días calendario para implementar ese requisito; debe comprobarse el cumplimiento actual.

El RUC del proveedor es distinto del RUC emisor, del receptor y del número de establecimiento. La aplicación conversacional no construye XML, no agrega esta información al texto comercial y no debe afirmar que la factura cumple si el backend fiscal no confirma el resultado. Fuente: [SRI, norma sobre proveedores de facturación](https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1).

---

## Nota normativa transversal — NAC-DGERCGC26-00000027

La resolución publicada contiene, en su disposición transitoria tercera, un plazo de **60 días calendario** para implementar el requisito del RUC del proveedor de facturación en información adicional del comprobante. Sin embargo, la noticia informativa oficial del SRI describe el plazo como **60 días hábiles**. Para evitar que cada módulo interprete la regla de manera distinta, prevalece el texto normativo publicado y debe quedar registrada la discrepancia de la noticia. A la fecha de esta revisión (29 de octubre de 2026), el plazo transitorio habría transcurrido bajo ambas lecturas; verificar inmediatamente el cumplimiento con el proveedor fiscal y el responsable tributario. No modificar XML ni datos de factura desde React/n8n: la inclusión corresponde al backend fiscal conforme a la ficha técnica vigente.

Fuentes: [texto de NAC-DGERCGC26-00000027](https://uqa.com.ec/wp-content/uploads/2026/07/Newsletter-No.-030-SRI-registro-de-proveedores-de-sistemas-informaticos-o-servicios-de-facturacion-electronica-28-JULIO-2026-SRI-NAC-27.pdf) · [aviso oficial del SRI](https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1) · [contrato fiscal canónico](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).
