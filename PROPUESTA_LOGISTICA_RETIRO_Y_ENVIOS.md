# Propuesta de logística comercial: retiro, despacho y entrega desde la bodega que tiene el stock

**Fecha:** 28 de octubre de 2026  
**Producto:** Inventary · EcuApp  
**Ámbito:** pedidos originados en React y WhatsApp/n8n, disponibilidad por almacén, preparación, retiro, despacho, entrega y transferencias entre locales.  
**Estado:** diseño funcional y técnico; no implica que la logística de pedidos al cliente esté implementada.  
**Documentos relacionados:** [Ventas V2](PROPUESTA_UX_VENTAS_V2.md) · [Motor comercial inteligente](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md).

## 1. Corrección de alcance

Tienes razón: la propuesta anterior se quedó corta. Trató entrega y retiro como opciones del checkout, pero no diseñó la operación que ocurre en el local o bodega donde realmente se encuentran las existencias. No basta con preguntar «¿entrega o retiro?». El sistema debe saber desde qué almacén se puede cumplir cada línea, en qué local puede recogerla el cliente, quién prepara el pedido, cuándo queda listo, cómo se despacha y cómo se confirma la entrega.

Inventary ya tiene gestión de almacenes, saldos por producto y almacén, disponibilidad informativa, movimientos, reservas en el flujo de pedidos WhatsApp y transferencias entre almacenes. Sin embargo, los contratos actuales de pedido no describen una operación logística completa al cliente.

Este documento propone dicha capa sin confundir capacidades existentes con futuras.

## 2. Lo que existe actualmente en el backend y lo que falta

### Capacidades verificadas

- La existencia se consulta por producto y almacén; el saldo distingue cantidades físicas, reservadas y disponibles.
- Existe GET /api/v1/inventory/availability, pero la disponibilidad observada no garantiza por sí sola que se mantenga hasta confirmar la operación.
- La API de ventas ordinarias recibe warehouseId y asocia la venta a un único almacén.
- La cotización de WhatsApp recibe warehouseId. WhatsAppCommerceService valida que cada línea tenga suficiente stock en ese almacén; la cotización guarda una sola referencia de almacén.
- Al crear un pedido de WhatsApp, el backend bloquea los saldos en orden determinista y reserva las cantidades en ese mismo almacén. La orden espera confirmación de pago; si vence o se cancela en los estados permitidos, se liberan las reservas.
- WhatsAppOrder registra un almacén, líneas, total, vencimiento y estados AWAITING_PAYMENT, CONFIRMED, CANCELLED y EXPIRED. La confirmación de pago la realiza el backoffice autorizado, que crea/vincula la venta.
- Existen transferencias internas de inventario con despacho y recepción entre almacenes, pero son un flujo de movimiento de stock y no están conectadas a preparación/entrega de un pedido del cliente.
- El modelo básico de almacén incluye código, nombre, dirección y estado activo. No constituye todavía un directorio completo de puntos de retiro con horarios, capacidad y reglas logísticas.
- La documentación del repositorio indica que no hay picking multi-bin operativo.

Referencias del código:
- [WhatsAppCommerceService.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppCommerceService.java)
- [WhatsAppDtos.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppDtos.java)
- [WhatsAppOrder.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/whatsapp/WhatsAppOrder.java)
- [SaleDtos.java](microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SaleDtos.java)
- [Referencia de APIs](microservices-inventary-ecuapp/docs/25-referencia-apis.md)

### Brechas logísticas

En los DTO/modelos del pedido de WhatsApp no aparecen un modo de cumplimiento, dirección de entrega, punto de retiro, envío/courier, seguimiento, ventanas estimadas, tareas de picking/empaque, prueba de entrega ni estados READY_FOR_PICKUP, DISPATCHED, IN_TRANSIT, DELIVERED o PICKED_UP. Tampoco hay un grupo de cumplimiento por líneas para dividir un pedido entre almacenes.

Por tanto, el sistema puede cotizar y reservar inventario para el pedido WhatsApp en un único almacén, pero no debe presentarse como un proceso de envío o retiro integralmente gestionado. La interfaz no puede fingir que el producto ya está «listo para retirar» o «en camino» sin un estado persistido que lo confirme.

### Qué significa «el almacén dueño del producto»

El modelo actual liga existencias a la combinación producto + almacén; no declara que el producto maestro tenga un propietario exclusivo. En esta propuesta, almacén de origen significa la ubicación que realmente dispone del stock asignable y que será responsable de prepararlo.

Si el negocio necesita diferenciar también al propietario legal de la mercancía —por ejemplo, consignación de terceros—, ese es otro concepto: requiere una relación explícita de propietario/consignador, reglas de autorización, liquidación y reportes. No se debe inferir propiedad legal a partir del almacén donde está guardado el artículo.

## 3. Tres formas de cumplir el pedido

El checkout debe distinguir modalidades con información verdadera y costes/tiempos calculados por el backend.

### A. Retirar en el local/bodega que tiene el stock

La mercancía ya está en la ubicación de origen. El cliente elige ese punto, confirma cantidades y total, y recibe la instrucción de acudir solo cuando el pedido pase a READY_FOR_PICKUP.

La UI muestra nombre real del local/bodega, dirección, horario, contacto, artículos disponibles en ese punto, plazo estimado de preparación e instrucciones de ingreso si aplican. No basta con «Retiro en local» sin identificar cuál.

Flujo:
1. Resolver un punto activo habilitado para retiro.
2. Confirmar que tiene disponibles las cantidades requeridas.
3. Cotizar y crear el pedido con reserva controlada.
4. Enviar una tarea a la cola de preparación de ese punto.
5. Un operador autorizado recoge, comprueba y prepara las líneas.
6. Cambiar a READY_FOR_PICKUP solo cuando todas las líneas del grupo estén completas.
7. Notificar al cliente por el canal autorizado con dirección, horario, vigencia del retiro y un código/QR seguro si se utiliza.
8. Verificar el código o identidad permitida al entregar; registrar actor y hora; cambiar a PICKED_UP.

La reserva no significa que el pedido esté preparado. El frontend y el bot deben distinguirlo.

### B. Envío desde la bodega con existencias hasta una dirección

El origen es la bodega/local que abastece el pedido; el destino es la dirección del cliente. Son datos diferentes que deben guardarse por separado.

Antes de confirmar se muestra dirección y contacto de entrega, cobertura, tarifa de envío devuelta por backend, ventana estimada, restricciones relevantes, modo de transporte y proceso ante cliente ausente o entrega fallida.

Flujo:
1. Validar la dirección y que esté dentro del área de cobertura.
2. Determinar qué almacén tiene stock disponible y puede despachar.
3. Calcular el costo y estimación de envío según reglas aprobadas.
4. Reservar las cantidades al aceptar el pedido.
5. Generar picking y empaque en el almacén de origen.
6. En despacho, registrar cantidades entregadas al transportista, fecha, responsable y referencia.
7. Actualizar tránsito mediante eventos del transportista o del operador, según la integración disponible.
8. Confirmar entrega con evidencia apropiada; si falla, registrar la excepción y siguiente acción.

En la primera fase puede permitirse una guía o referencia manual y actualización por personal autorizado. La integración automática con transportistas queda para otra fase. No se deben inventar enlaces de tracking ni decir «en camino» antes del despacho real.

### C. Retirar en un local distinto del que tiene el stock

Esta modalidad requiere mover la mercancía desde la bodega que la tiene hasta el punto de retiro elegido. No es lo mismo que retirar en el origen: el artículo aún no se encuentra en el local de recogida.

Antes de aceptar, la UI informa del traslado, tiempo adicional estimado y cargos aplicables. No se promete el retiro hasta confirmar el flujo de transferencia y recepción.

Flujo:
1. Identificar un origen con stock y un destino habilitado para retiro.
2. Mostrar «Retirar en [local destino]», junto con la llegada estimada.
3. Al aceptar, reservar stock origen y vincular una transferencia operativa al pedido.
4. Despachar el traslado con el flujo existente de transferencias.
5. Registrar recepción en destino; el estado de despacho por sí solo no demuestra que llegó.
6. Reasignar la custodia/reserva al destino de forma atómica y crear la tarea de preparación de retiro.
7. Notificar solo cuando destino confirme recepción y preparación.
8. Al entregar, registrar PICKED_UP.

Si no se puede trasladar a tiempo, ofrecer alternativas claras: recoger en origen, esperar, cambiar artículo o cancelar y liberar la reserva, según la política. No ocultar el traslado ni afirmar que el producto está en destino antes de confirmarlo.

## 4. ¿Qué pasa cuando el carrito contiene productos de distintas bodegas?

No se debe asignar todo el pedido a una bodega que solo tiene parte de la compra y descubrir el faltante al final. Tampoco se debe dividir silenciosamente el envío: el cliente podría recibir dos paquetes, pagar cargos adicionales o tener tiempos diferentes.

### Política recomendada

1. Buscar una ubicación habilitada que pueda atender el carrito completo en la modalidad solicitada.
2. Si hay varias, ordenar opciones mediante reglas configuradas: preferencia de retiro, stock real, promesa de entrega, tarifa total, distancia solo si existen datos fiables y carga operativa. Aplicar restricciones duras antes de ponderar.
3. Si ninguna puede surtir todo, considerar traslado hacia un único punto cuando sea viable y el plazo/costo puedan calcularse.
4. Como siguiente alternativa, ofrecer grupos de cumplimiento separados, mostrando los artículos de cada grupo, origen, destino, coste y tiempo.
5. Si el cliente no acepta tiempos, costes o división, dejarle modificar el carrito. No crear un split no consentido.
6. Revalidar existencias y reservarlas transaccionalmente al confirmar.

Un pedido comercial puede contener múltiples grupos de cumplimiento, aunque el cliente vea un solo resumen. Cada grupo lleva las líneas/cantidades preparadas desde un origen y con un método/destino comunes. Esto permite que un artículo se retire y otro se envíe solo si el negocio lo admite y el cliente acepta la separación.

No hace falta habilitar división de pedidos en la primera entrega si aumenta demasiado el riesgo. Una primera versión puede exigir un solo origen por pedido y explicar alternativas cuando ninguna ubicación pueda completar el carrito. La limitación debe ser intencional y transparente, no un error de disponibilidad.

## 5. UX propuesta en React

Añadir un paso claro «Cómo quieres recibir tu pedido» después de elegir productos y antes de confirmar. Las opciones solo aparecen si el backend devuelve que son elegibles.

### Retiro en local/bodega
- Lista/mapa de puntos activos con nombre, dirección, horario y distancia solo si se calcula correctamente.
- Artículos disponibles en ese punto, faltantes, plazo de preparación y si requiere traslado.
- Selección explícita del local.
- Mostrar «Disponible para retiro» solo después de READY_FOR_PICKUP.

### Entrega a domicilio
- Dirección y contacto de recepción.
- Validación de cobertura en backend.
- Costo, plazo y restricciones antes de pagar/confirmar.
- Separar «Sale de Bodega Norte» de «Se entrega en [dirección]».
- Seguimiento y excepciones según estados reales.

### Pedido con varios orígenes
- Mostrar cada grupo con artículos/cantidades, origen, método/destino, costo y estimación.
- Mostrar total combinado y pedir aceptación explícita de la división.

### Estados y mensajes
Pago y logística son dimensiones diferentes: «Pago confirmado» no significa «en preparación», y «listo para retiro» no significa «entregado». Una orden puede estar pagada y pendiente de preparación, o tener un grupo en tránsito mientras otro está listo.

Mensajes recomendados:
- «Pedido recibido; estamos validando la reserva».
- «Pago confirmado; Bodega Norte está preparando 3 artículos».
- «Listo para retirar en Local Centro. Dirección… Horario…».
- «Despachado desde Bodega Norte. Seguimiento: [referencia real si existe]».
- «Entrega no completada; selecciona una acción o contacta al equipo».

Ante error de red tras confirmar, consultar el pedido por ID/clave idempotente antes de repetir la acción. Conservar el carrito y los datos.

## 6. Experiencia operativa del local/bodega

La logística requiere una bandeja de trabajo para el personal; no puede resolverse únicamente con el checkout.

### Bandeja «Pedidos por preparar»
Filtros por ubicación, fecha prometida, estado, modalidad y prioridad. Mostrar número de pedido, hora límite, método de entrega/retiro, cantidad de líneas, dirección/punto destino y alertas. Cada operador solo puede preparar y entregar para las ubicaciones autorizadas.

### Picking y empaque
- Lista de picking según la estructura que realmente exista en el modelo.
- SKU/nombre/variante, cantidad y unidad por preparar.
- Confirmación de cada línea y gestión de faltante o daño.
- No sustituir productos sin política y consentimiento.
- Registro de quién preparó y empaquetó.
- Un faltante físico genera una excepción; no un ajuste silencioso de stock.

Como no hay picking multi-bin operativo documentado, la primera versión debe trabajar con saldos por producto/almacén y no simular precisión por estantería.

### Retiro y despacho
- Retiro: transición controlada a READY_FOR_PICKUP, notificación, verificación segura y registro de entrega.
- Despacho: comprobación del paquete, transportista o método interno, referencia real y cambio a DISPATCHED; IN_TRANSIT, DELIVERED y DELIVERY_FAILED requieren evento o actor autorizado.
- Si se permite retiro parcial, registrar cantidades entregadas por grupo sin cerrar prematuramente toda la orden.

## 7. Modelo de dominio propuesto

La nomenclatura final debe ajustarse al código actual. Separar conceptualmente:

### FulfillmentGroup (grupo de cumplimiento)
- orderId y número de grupo;
- modalidad: PICKUP_AT_ORIGIN, DELIVERY o TRANSFER_TO_PICKUP;
- sourceWarehouseId y pickupWarehouseId cuando corresponda;
- snapshot inmutable de dirección de entrega cuando aplique;
- estado, ventana prometida y cargos fijados en cotización;
- timestamps de asignación, preparación, despacho y cierre.

### FulfillmentLine
Vincula una línea de pedido y cantidad asignada a un grupo. Permite dividir una línea entre grupos si el negocio habilita fulfillment parcial.

### Shipment (envío)
Registra transportista/método, referencia de tracking real, coste estimado y real si se conoce, fecha de despacho, eventos de seguimiento y estado de excepción/entrega. No guardar un tracking inventado ni tomar la cadena de seguimiento como prueba automática de entrega.

### Pickup record
Registra punto, disponibilidad, expiración del plazo de recogida cuando exista, código/QR de un solo uso, actor que verifica y hora. Guardar un hash del código, no incluirlo en logs, y evitar que autorice el retiro de otra orden.

### Reservas y auditoría
Enlazar reservas/movimientos con línea, grupo y orden. Liberar la reserva exactamente una vez por expiración/cancelación permitida. La transición origen → tránsito → destino no puede duplicar ni destruir stock. Mantener el libro de movimientos como registro de inventario y no modificar onHand desde frontend.

### Capacidad logística de la ubicación
Evaluar configuración administrativa de:
- permite venta/fulfillment o solo almacena;
- habilitado para retiro;
- habilitado para despacho;
- horarios y días no laborables;
- zonas de cobertura/envío;
- límites de capacidad y dimensiones;
- contactos e instrucciones visibles;
- ventana/corte para preparación y transporte.

El modelo básico de almacén no contiene todas estas capacidades; hay que evitar duplicar conceptos ya existentes.

### Propietario legal del inventario, si aplica
Si Inventary maneja consignación o mercancía de varios propietarios, incorporar inventoryOwnerId o relación equivalente y propagarla a saldos, reservas, movimientos, órdenes, ventas, devoluciones y reportes. Definir autorizaciones, devoluciones y liquidación al propietario. No usar warehouseId como sustituto de ownerId: una bodega puede contener mercancía de distintos propietarios.

## 8. Contratos de API: extender, no duplicar

No introducir APIs nuevas sin compararlas con la referencia actual ni recrear lógica de cotización. Los siguientes contratos son propuestas, no endpoints existentes.

### Cotización
Ampliar la petición de cotización para incluir:
- fulfillmentMode: PICKUP_AT_ORIGIN, DELIVERY o PICKUP_AT_OTHER_LOCATION;
- pickupWarehouseId cuando el cliente quiere retirar;
- dirección de entrega o identificador de dirección autorizado;
- carrito y cantidades;
- preferencia opcional de ubicación, siempre validada por permisos y stock.

La respuesta devuelve candidatos/grupos con origen, datos públicos del punto, modalidad/destino, líneas y cantidades atendidas, stock elegible, costo de transporte, ventanas estimadas, necesidad de traslado, motivos de rechazo y versión/expiración de cotización.

Solo prometer horarios/costos que el backend pueda calcular. React y n8n presentan lo recibido sin inventar datos.

### Pedidos y comandos de fulfillment
Evaluar lectura del estado logístico por pedido y comandos autorizados para asignar/preparar, empaquetar, marcar listo para retiro, entregar al transportista, registrar tránsito/fallo/entrega, confirmar retiro y resolver excepciones. Cada comando valida la transición, permisos por ubicación e idempotencia. Crear auditoría y eventos Outbox de forma atómica.

### WhatsApp
El flujo actual POST /integrations/whatsapp/carts/{id}/quotes recibe una sola warehouseId. La evolución debe usar el mismo servicio comercial que el checkout web, con modalidad y elección de origen/destino. n8n no debe decidir la bodega por su cuenta ni mantener existencias duplicadas.

El chatbot responde separando productos disponibles por ubicación, local de retiro, pedido pendiente de preparación, pedido listo, despacho y entrega; solo usa estados confirmados por backend.

## 9. Reglas operativas de seguridad

1. La disponibilidad por ubicación se vuelve a validar al crear el pedido.
2. Reserva y asignación de líneas son atómicas; dos pedidos por las últimas unidades no pueden causar sobreventa.
3. Preparación/entrega requiere permiso para la ubicación; el cliente no puede evadir reglas eligiendo cualquier origen.
4. Al cancelar/expirar, liberar cada reserva una vez; transferencias ya despachadas requieren compensación apropiada, no borrado.
5. Cambiar de origen después de cotizar exige recalcular promesa/costo y pedir aceptación si afecta al cliente.
6. No marcar READY_FOR_PICKUP hasta que destino reciba y prepare todas las cantidades del grupo.
7. No confundir reserva, preparación, despacho, entrega, pago y documento fiscal.
8. No exponer la dirección de entrega del cliente desde la ficha pública ni poner información personal completa en logs.
9. El canal y el método de pago no deben alterar en silencio el origen real del stock.
10. El bot solo puede decir «reservado» si hay una reserva activa y vigente en el backend.

## 10. Métricas de operación y negocio

Medir:
- pedidos por modalidad y almacén de origen;
- porcentaje de carritos surtibles desde una ubicación;
- diferencia entre disponibilidad prometida y unidades encontradas durante picking;
- tiempo entre pedido confirmado, asignación, preparación, listo para retiro y entrega;
- retiros completados y no recogidos;
- entregas a tiempo/fallidas y costo real frente al estimado;
- pedidos divididos y coste adicional;
- incidencias de traslado/retrasos al punto de retiro;
- reservas expiradas, pedidos duplicados y sobreventa;
- cancelaciones/devoluciones relacionadas con promesas de entrega incorrectas;
- conversión y margen por modalidad, junto con reclamaciones y satisfacción.

No asumir que retiro siempre aumenta conversión ni que envío gratis compensa. Experimentar con grupo control y medir coste, reclamaciones, cancelaciones y satisfacción.

## 11. Plan de entrega recomendado

### Fase 1: retiro en origen y envío desde una sola bodega
- Añadir modo y destino a cotización/pedido.
- Mostrar puntos habilitados y datos reales.
- Asignar todas las líneas a un solo origen.
- Crear tareas simples de preparación.
- Implementar READY_FOR_PICKUP, PICKED_UP, DISPATCHED y estados mínimos de entrega con auditoría.
- Guardar dirección snapshot, tarifa/estimación y método.
- Preservar idempotencia y liberación de reservas.

### Fase 2: tablero de bodega y seguimiento
- Bandeja por ubicación para picking, empaque y despacho.
- Notificaciones consentidas cuando el pedido esté listo.
- Tracking real o seguimiento manual autorizado.
- Excepciones por faltante, dirección incorrecta y cliente ausente.
- Métricas operativas.

### Fase 3: retiro en otra ubicación y fulfillment dividido
- Integrar transferencias existentes con pedidos de cliente.
- Vincular transferencia a líneas y recepción.
- Grupos de cumplimiento por origen/destino.
- Múltiples paquetes/locales solo si el negocio acepta el coste y el checkout lo explica antes de confirmar.

### Fase 4: optimización de asignación
Con datos fiables, optimizar origen sujeto a restricciones duras de stock, permisos, capacidad y modalidad. Después evaluar plazo, coste y carga operativa. No calcular distancia o tiempo si faltan coordenadas, tarifas o estimaciones validadas.

## 12. Matriz de pruebas mínimas

1. Retiro cuando todo el stock está en el punto elegido.
2. Retiro en bodega cerrada o punto inactivo.
3. Envío dentro y fuera de cobertura.
4. Tarifa recalculada si cambian dirección/origen.
5. Dos pedidos concurrentes por las últimas unidades.
6. Reserva expirada y liberada una sola vez.
7. Antes de preparación nunca se muestra «listo para retiro».
8. Picking detecta faltante/daño.
9. READY_FOR_PICKUP solo tras completar grupo.
10. Código de retiro inválido, vencido o de otra orden.
11. Retiro parcial solo si política habilitada.
12. Despacho con referencia real y sin tracking.
13. Entrega fallida y reintento autorizado.
14. Cancelación antes de preparar frente a después de transferir/despachar.
15. Retiro en otro punto requiere traslado y recepción confirmada.
16. Carrito con líneas de dos bodegas: consolidación válida o split explícito con tarifas/plazos.
17. Transferencia despachada/recibida una vez; stock no se duplica.
18. Error de red al confirmar: reconsulta idempotente.
19. Web y WhatsApp muestran el mismo origen, costo, promesa y estado.
20. Operador no puede preparar/entregar desde otra ubicación no autorizada.
21. Cancelación libera reserva solo si el estado logístico lo permite.
22. Dirección, teléfono y código de retiro no se filtran a logs.
23. Propiedad legal distinta del operador logístico, si aplica, respeta permisos/reportes.
24. Pruebas móviles, teclado, lector de pantalla, carga/error y recuperación.

## 13. Fuentes de patrones operativos

Estos materiales son referencia de patrones; no demuestran que estas funciones estén disponibles en Inventary.

- Oracle Retail, Understand Buy Online Pick Up in Store: distingue retiro en ubicación con stock de retiro después de transferir mercancía al punto elegido. https://docs.oracle.com/en/industries/retail/retail-digital-commerce/latest/ccdev/understand-buy-online-pick-store.htm
- Oracle Retail Order Management, Store Pickup Orders: retiro cuando el stock está disponible en el punto elegido y notificación al cliente. https://docs.oracle.com/en/industries/retail/retail-oms-suite-cloud/24.2.401.0/romtg/c_store_pickup_orders.htm
- Oracle Retail Order Management, Ship-for-Pickup Orders: separa bodega origen, ubicación de abastecimiento y local de retiro. https://docs.oracle.com/en/industries/retail/retail-oms-suite-cloud/24.2.401.0/romtg/c_ship_for_pickup_orders.htm
- Shopify Help Center, Locations: inventario por ubicación y reglas de asignación de pedidos. https://help.shopify.com/en/manual/inventory-and-locations/locations
- Shopify Help Center, Fulfilling orders: cambio de origen para pedidos no completados cuando lo permiten las reglas. https://help.shopify.com/en/manual/fulfillment/fulfilling-orders/single-fulfillment
- Shopify Help Center, Ship and pickup in one order: ejemplo de pedido dividido entre envío y retiro, con sus restricciones de funcionalidad. https://help.shopify.com/en/manual/checkout-settings/ship-and-pickup
- Google Merchant Center, disponibilidad de retiro local: https://support.google.com/merchants/answer/14634021

## 14. Criterio de aceptación

El diseño está completo cuando cada opción de retiro/envío identifica origen y destino, la disponibilidad se valida en la ubicación elegida, costo y promesa provienen del backend, reserva y picking tienen estados persistidos y el cliente distingue pedido recibido, pago confirmado, preparación, listo para retirar, despacho y entrega.

La primera implementación no necesita todos los escenarios avanzados. Sí necesita evitar confundir existencias por almacén con un pedido logísticamente cumplido o prometer recogida/entrega sin un estado real.
