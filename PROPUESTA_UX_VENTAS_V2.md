# Propuesta UX/UI de Ventas — versión revisada para React y WhatsApp

> **Ampliación logística obligatoria:** la experiencia de retiro/envío y la preparación desde la bodega que tiene el stock están detalladas en [PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md). Esta capa es distinta del simple campo de entrega del checkout.


**Fecha:** 28 de octubre de 2026  
**Estado:** propuesta de UX, contratos y experimentación; no implica que todos los endpoints, pagos, facturación electrónica ni modelos estén implementados.  
**Relacionados:** [PROPUESTA_UX_VENTAS.md](PROPUESTA_UX_VENTAS.md) · [PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md).

## 1. Objetivo

Registrar una venta sin errores, explicar el importe total antes de confirmar, resolver incertidumbres que bloquean la compra y mantener la misma lógica de negocio en React y WhatsApp. El objetivo comercial es aumentar compras útiles y margen sostenible, no inducir compras mediante presión, inventar urgencia ni maximizar clics aislados.

Las reglas de precio, stock, promoción, permisos, cotización y pedido pertenecen al backend. React presenta y confirma resultados; el chatbot solicita las mismas operaciones REST y conserva el identificador del carrito/cotización. Ningún canal debe calcular un precio diferente o aceptar un descuento que el servidor rechazaría.

## 2. Pantalla principal: Ventas y Devoluciones separadas

La pantalla de Ventas debe tener vistas claramente separadas:
- **Ventas:** búsqueda por número, cliente, fecha, vendedor y estado, con total, pago y estado fiscal diferenciados.
- **Devoluciones:** devoluciones en curso/completadas, venta original, artículos/cantidades, disposición del inventario y estado de reembolso.
- **Nueva venta:** flujo guiado independiente del listado.

No colocar el formulario de devolución como bloque al final de la tabla de ventas. El listado puede abrir una fila expandible con líneas, cantidades, descuentos, estados y acciones permitidas.

## 3. Crear venta mediante stepper

Si el formulario contiene varios conjuntos de datos, usar pasos y una vista final de confirmación, no una modal extensa. Permitir volver a pasos anteriores sin perder el carrito.

### Paso 1 — Productos y cotización

- Buscador por nombre, SKU o código de barras, selección repetida sin cerrar el selector.
- Carrito editable con cantidad, precio por unidad autorizado, descuento vigente y subtotal por línea.
- Tipo de artículo claramente indicado: mercancía con stock o servicio.
- Disponibilidad calculada por backend y ubicación de abastecimiento comunicada desde el inicio.
- Precio total visible y actualizable por una cotización del servidor.
- Acciones de comparar alternativas o ver detalles sin perder el contexto.

La ubicación habitual debe resolverse por usuario/caja/terminal autorizada en el servidor. No pedir elegir almacén en cada venta rutinaria. Si falta stock, presentar alternativas de ubicaciones permitidas o productos compatibles; cualquier cambio de origen debe mostrarse y ser validado por backend.

Si cambia cantidad, artículo o promoción, solicitar una nueva cotización. Mostrar expiración/versión y diferencias importantes. El frontend puede animar la actualización, pero no calcular el importe final de manera independiente.

### Paso 2 — Comprador y entrega

Separar datos de comprador/facturación de destinatario/entrega. Precargar solo datos existentes y autorizados para el uso actual; no forzar al usuario a registrar un cliente para una operación que legalmente no lo requiere. Validar identificación y campos tributarios de acuerdo con reglas oficiales vigentes y el tipo de documento, no con una tarifa o umbral hardcodeado.

Opciones posibles: retiro en local, entrega a domicilio, destinatario diferente, dirección de envío independiente, ventana de entrega si el negocio la soporta y cargos de entrega devueltos por backend. La UI debe indicar qué campos son obligatorios, por qué se piden y cuáles son opcionales.

### Paso 3 — Revisar importes y confirmar

Mostrar:
- artículos y cantidades;
- precios por unidad y subtotales;
- descuentos, códigos y condiciones;
- bases e impuestos desglosados según la respuesta del backend;
- cargos de entrega;
- total y estado de cotización;
- comprador, comprobante previsto y entrega/retiro;
- ubicación que abastecerá la venta;
- estado real de pago y documento fiscal.

El CTA final debe reflejar la operación real: «Registrar venta» si se crea un registro interno, «Confirmar pedido» si aún está pendiente, y «Factura emitida» solo si el sistema obtuvo la autorización correspondiente. No confundir un pedido con dinero recibido ni una devolución de inventario con reembolso financiero.

Usar una clave idempotente para confirmar. Si la red falla después del envío, consultar el estado del pedido antes de repetirlo; conservar el carrito y los datos introducidos.

## 4. Atención contextual: ayudar a resolver la duda real

La evidencia de abandono apunta a incertidumbre sobre transacción y producto, pero no existe una ecuación universal que explique cada carrito. El diseño debe contestar dudas concretas:
- precio final, descuentos y cargos;
- disponibilidad y lugar de entrega;
- compatibilidad y especificaciones documentadas;
- garantía y política de devolución;
- método de pago realmente soportado;
- seguridad del proceso y próximo paso.

La interfaz puede reconocer la etapa operativa observada —descubrimiento, comparación, consulta de precio/stock, entrega, confirmación, posventa— y presentar la ayuda correspondiente. No debe describir al cliente como impulsivo, inseguro o psicológicamente vulnerable, ni usar esas etiquetas para cambiarle el precio.

## 5. Reglas comerciales y descuentos

Las promociones se implementan como reglas verificadas por servidor: fechas y zona horaria, productos elegibles, cantidad, lista de precios/canal autorizado, combinaciones, límites, permisos y trazabilidad. Los códigos vencidos o no combinables deben explicar el motivo y conservar el carrito.

No conceder automáticamente descuento solo porque la persona parece no comprar. Una alta probabilidad de compra no significa que una oferta cause esa compra; la promoción puede reducir margen sin producir ventas incrementales. Si se prueba una oferta, usar control aleatorizado y medir margen neto incremental, coste de descuento y devoluciones, no solo conversión.

Precios por cliente o contrato deben ser reglas explícitas y auditables. No se recomienda variar individualmente el precio a partir de la propensión a pagar, inferencias psicológicas o vulnerabilidad. Evitar contadores de tiempo falsos, disponibilidad ficticia y cargos que aparecen tarde.

## 6. Modelo de intención y abandono: por fases

### Fase inicial: reglas deterministas

Antes de ML, registrar los pasos del embudo y los errores reales: búsqueda sin resultado, carrito creado, cotización visible, coste de entrega visible, validación fallida, cotización vencida y pedido completado. Dar respuesta operativa contextual y medir la mejora.

### Fase posterior: estimación calibrada de compra

Con suficiente historial autorizado y eventos fiables, probar una regresión logística interpretable para estimar la probabilidad de compra en un horizonte definido. Entrenar solo con señales observables; separar datos temporalmente para evitar fuga. Evaluar calibración y Brier score además de AUC. La estimación no es un diagnóstico ni debe presentarse como certeza.

### Abandono

Definir abandono como un carrito válido que no termina en pedido tras un periodo coherente con la categoría y canal. Diferenciar compra retrasada, carrito aún activo, fallo técnico, agotado y abandono confirmado por regla temporal. No etiquetar silencio inmediato como desinterés.

La acción preferente es resolver la fricción en la sesión. No iniciar mensajes promocionales automáticos en WhatsApp sin el consentimiento explícito para esa categoría. Si se implementa recuperación de carrito, conservar contexto y cotización, verificar que sigue vigente, no enviar mensajes duplicados y respetar baja/frecuencia.

### Promociones incrementales

Cuando exista volumen adecuado, estimar uplift usando un grupo de control:

Uplift(x) = P(compra | oferta, x) − P(compra | control, x)

Solo un experimento bien diseñado permite aproximar el efecto causal en la población probada. No entrenar un modelo de uplift con conversiones observacionales sesgadas como si probaran causalidad.

## 7. WhatsApp: misma cotización, continuidad y límites claros

El README del repositorio documenta el flujo actual de integración: catálogo → POST /carts → PUT /carts/{id}/items/{productId} → POST /carts/{id}/quotes → POST /orders; cotización y pedido usan Idempotency-Key y el pedido documenta consentConfirmed. El pago/registro de venta se completa después mediante el proceso de backoffice indicado en el README. Reutilizar ese flujo antes de crear rutas paralelas.

El flujo conversacional propuesto:

1. Entender la solicitud expresada: buscar producto, comparar, consultar precio/stock, entrega, continuar carrito, consultar pedido o iniciar devolución.
2. Si la petición es ambigua, pedir aclaración breve y ofrecer escalamiento humano.
3. Recuperar/crear el carrito en backend y devolver opciones basadas en el catálogo actual.
4. Cotizar a través de la misma API que usa la web; mostrar desglose, validez y condiciones.
5. Confirmar pedido con clave idempotente y devolver su estado real.
6. En problemas de pago, factura, devolución o reembolso, explicar el estado y transferir a una persona cuando la automatización no esté autorizada o tenga baja confianza.

El estado transaccional debe residir en el backend, no solo en la ejecución de n8n. La cotización y el pedido deben poder correlacionarse con la sesión y canal sin duplicar la orden.

### Consentimiento y atención

La política de WhatsApp Business exige consentimiento explícito para contactar al cliente, respeto de la baja, plantillas aprobadas para mensajes iniciados por la empresa y atención dentro de la ventana de servicio correspondiente. La política consultada establece 24 horas desde el último mensaje del usuario para responder sin plantilla; comprobar la documentación vigente al implementar porque las reglas pueden cambiar.

Separar consentimientos para actualizaciones del pedido, ofertas y recomendaciones. El consentimiento para recibir novedades operativas no debe activar marketing. No pedir por chat números completos de tarjetas, cuentas o identificadores personales confidenciales. Para datos que deban tratarse de forma segura, usar una página autenticada. Ofrecer escalamiento humano claro y directo.

Fuente oficial: https://business.whatsapp.com/policy/preview?lang=es_LA

## 8. Arquitectura de API y contratos

La API del backend es fuente de verdad para catálogo, permisos, precio, promociones, disponibilidad, cotización, pedido, consentimiento y estado final. Las rutas que ya existen deben ampliarse de forma compatible; la tabla siguiente contiene posibilidades futuras, no endpoints confirmados como implementados.

| Capacidad por definir/completar | Contrato esperado |
|---|---|
| Cotización | líneas y versiones, precio, descuentos, stock elegible, impuestos/cargos aplicables, total, expiresAt y códigos de diferencia |
| Precio/promoción | razón legible, regla aplicada, vigencia y permiso requerido |
| Pedido | clave idempotente, referencia a cotización y estado durable consultable |
| Contexto de almacén | opción predeterminada resuelta por servidor y alternativas autorizadas |
| Consentimiento | categorías separadas, fecha, origen, revocación y trazabilidad |
| Eventos de embudo | esquema versionado, canal, correlación, deduplicación y privacidad |
| Recomendaciones | resultados elegibles, razón verificable y placement/origen |

La recomendación de una UI no sustituye una cotización. El servidor vuelve a validar disponibilidad, precios y promociones al crear el pedido. Si el total cambia de forma relevante, debe permitir revisar y aceptar el nuevo valor.

## 9. Devolución, anulación y reembolso son procesos distintos

- Borrador: se puede descartar si nunca se completó.
- Cancelación operativa: compensa una venta según su estado y permisos.
- Documento fiscal autorizado: requiere el flujo fiscal correctivo aplicable; cambiar el estado interno no lo anula.
- Devolución física: define líneas, cantidades, motivo y disposición de inventario.
- Reembolso monetario: movimiento financiero con importe, método, referencia, estado, actor y conciliación.

El estado actual documentado no muestra una integración completa de facturación electrónica SRI ni un dominio financiero de pago/reembolso; las propuestas no deben dar esas capacidades por entregadas. Mantener relacionados los hechos, no fusionarlos en una sola acción ambigua.

Toda devolución debe impedir que la cantidad total devuelta supere lo adquirido, incluso con solicitudes concurrentes. Un artículo defectuoso no debe volver automáticamente al stock disponible. La UI debe distinguir «mercancía registrada», «inventario actualizado», «reembolso pendiente/realizado» y «documento fiscal pendiente/autorizado».

## 10. Instrumentación y evaluación estadística

Definir eventos uniformes entre web y WhatsApp: búsqueda, impresión visible, producto seleccionado, artículo añadido/eliminado, cotización creada/vencida/cambiada, coste visible, error de validación, pedido creado/completado, devolución, estado de reembolso, consentimiento y escalamiento humano. No recopilar transcripciones completas como telemetría si basta una intención estructurada.

Antes de un experimento:
- registrar hipótesis, población, unidad de aleatorización, métrica primaria, métricas de guarda y duración;
- fijar el tamaño de muestra/potencia cuando sea viable;
- mantener variante estable entre canales para el mismo usuario/sesión cuando sea apropiado;
- comprobar la integridad del registro y sample ratio mismatch;
- definir antes cómo se interpretan múltiples métricas y cuándo detener por daño;
- no cerrar la prueba únicamente porque una lectura temprana muestre un resultado favorable.

### Métrica primaria sugerida

Elegir una métrica principal por experimento: conversión por sesión elegible o margen de contribución neto por sesión elegible cuando descuentos, costes y devoluciones estén suficientemente medidos.

### Guardas

Devoluciones, cancelaciones, margen, coste de descuentos, quejas, bajas/bloqueos en WhatsApp, pedidos duplicados, stockouts, latencia, errores, cotización/pedido discrepantes y discrepancias entre web y chatbot. El CTR es diagnóstico, no el objetivo final.

## 11. Matriz de aceptación

1. Venta de un artículo y venta de varios artículos con cantidades editables.
2. Precio/promoción cambian entre cotización y confirmación: mostrar diferencia y pedir confirmación.
3. Usuario sin permiso intenta aplicar un descuento.
4. Stock cambia mientras se confirma la venta.
5. Error de red tras enviar pedido: reconsulta sin duplicación.
6. Cliente existente, cliente nuevo y consumidor final con datos requeridos según la operación.
7. Entrega distinta del domicilio de facturación.
8. Carrito en web que continúa desde WhatsApp con la misma cotización válida.
9. Usuario revoca consentimiento y el sistema detiene esa categoría de mensajes.
10. Mensaje saliente fuera de ventana de atención que requiere plantilla aprobada.
11. Usuario pide atención humana o el bot no entiende.
12. Devolución parcial y total con control de cantidades acumuladas.
13. Reembolso pendiente frente a reembolso realizado y conciliado.
14. Venta cancelada antes de documento fiscal frente a documento autorizado.
15. Prueba móvil, teclado, lector de pantalla, estados de carga/error y recuperación de formulario.
16. Experimento con pérdida de telemetría o SRM: no se declara ganador hasta investigar.

## 12. Bibliografía

- Riesgo percibido y abandono de carrito: metaanálisis contextual de 2026. https://www.mdpi.com/0718-1876/21/8/265
- Choice overload: metaanálisis de 50 experimentos y heterogeneidad de contexto. https://doi.org/10.1086/651235
- Efecto de defaults en configuradores de compra: https://doi.org/10.1016/j.jretconser.2011.06.005
- Experimentación online confiable y métricas de guarda: https://www.microsoft.com/en-us/research/articles/patterns-of-trustworthy-experimentation-during-experiment-stage/
- Sample ratio mismatch y diagnóstico de calidad: https://www.microsoft.com/en-us/research/publication/diagnosing-sample-ratio-mismatch-in-online-controlled-experiments-a-taxonomy-and-rules-of-thumb-for-practitioners/
- Política oficial de WhatsApp Business: https://business.whatsapp.com/policy/preview?lang=es_LA
- Motor comercial común, algoritmos, arquitectura y privacidad: [PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)

Las fuentes orientan hipótesis y guardas; los resultados deberán validarse con los datos reales de Inventary y pruebas con compradores. No se deben presentar funcionalidades futuras como ya implementadas.


---

## Ampliación de alcance: despacho, retiro y preparación en la ubicación de origen

La opción de entrega del paso 2 no es suficiente por sí sola. Debe existir un paso explícito de cumplimiento, condicionado a las opciones que el backend pueda ejecutar:

- **Retirar en origen:** la bodega/local que tiene las existencias prepara las líneas; el cliente solo recibe el aviso de listo después de la confirmación del personal autorizado.
- **Enviar desde origen:** la bodega prepara, empaqueta y entrega al transportista o despacho interno; el destinatario y la dirección se guardan independientemente de la bodega.
- **Retirar en otro local:** solo se ofrece si el negocio soporta una transferencia hacia ese local; la recepción del traslado debe quedar confirmada antes de anunciar que está listo.

El sistema actual de pedidos WhatsApp cotiza con un solo warehouseId y reserva las líneas en ese almacén. Hay una API de transferencia de inventario, pero eso no equivale a tener preparado el pedido para cliente, registrar el despacho del transportista o confirmar un retiro. La propuesta completa describe estados, contratos y fases.

**Estados independientes:** pago, inventario reservado, preparación, listo para retiro, despacho, tránsito, entrega y recogida. No mostrar «listo para retirar» cuando solo existe una reserva ni «entregado» porque se confirmó un pago.

**Carrito con stock en varias bodegas:** primero buscar un origen común autorizado. Si no existe, ofrecer traslado o cumplimiento dividido únicamente cuando esté soportado y se muestre de antemano qué artículos salen de cada ubicación, sus costes y plazos. Nunca crear envíos divididos de forma silenciosa.

**UX operativa adicional:** bandeja de pedidos por preparar para el personal de cada local, picking/empaque con confirmación por línea, incidencias de faltante/daño, despacho, validación segura del código de retiro y prueba de entrega. Todas las acciones deben ser autorizadas por ubicación y auditables.

Este es alcance nuevo propuesto, no una afirmación de que el sistema actual ya tenga esos endpoints, estados o integraciones.


---

## Integración con Suscripciones: misma entrada comercial, comando de dominio distinto

La pantalla actual de Ventas y el backend excluyen deliberadamente servicios de tipo SUBSCRIPTION del formulario ordinario: SaleForm.jsx no los incluye en su selector y SalesController rechaza su creación mediante POST /sales cuando el canal es SUBSCRIPTION. El alta pertenece a POST /subscriptions, que crea el contrato y genera las facturas correspondientes.

Por tanto, el rediseño de Ventas debe ofrecer dos entradas claras: **Venta única** y **Contratar suscripción**. La segunda reutiliza la selección de producto/cliente, el stepper y el resumen visual, pero confirma el plan mediante el caso de uso de suscripciones, no enviándolo como una venta convencional.

La tabla de Ventas debe identificar las facturas cuyo canal es SUBSCRIPTION y enlazarlas con el contrato de origen. Una anulación genérica de una de esas facturas no puede dejar la cuota como INVOICED ni el contrato como ACTIVE sin sincronización. Hasta que haya pagos reales integrados, una factura creada no se presenta como dinero cobrado. El detalle de las brechas, estados, APIs y pruebas está en [PROPUESTA_UX_SUSCRIPCIONES.md](PROPUESTA_UX_SUSCRIPCIONES.md).

Para la primera fase, no mezclar en una sola llamada los artículos de venta única y el contrato recurrente. Si se necesita comprar ambos, se deben confirmar como operaciones diferenciadas y relacionadas; una experiencia de pedido unificado requiere coordinación atómica en backend y debe implementarse como una fase posterior.

**Pruebas de aceptación compartidas:** un plan no debe entrar en POST /sales; contratarlo desde Ventas crea un solo contrato y su primera factura según el calendario; esa factura enlaza al contrato; cancelación/anulación mantiene consistentes cuota, factura y suscripción; y los estados de contratación, facturación, pago y acceso no se confunden.
