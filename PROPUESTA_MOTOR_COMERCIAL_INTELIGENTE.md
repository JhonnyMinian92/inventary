# Propuesta de motor comercial inteligente para Inventary

> **Dependencia logística:** las recomendaciones deben respetar el origen real del stock y las opciones de recepción. Ver [PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md) para retiro en bodega/local, envío desde origen, retiro tras traslado y preparación del pedido.


**Fecha de investigación:** 28 de octubre de 2026  
**Producto:** Inventary · EcuApp  
**Ámbito:** backend Spring Boot, frontend React y canal de ventas por WhatsApp/n8n.  
**Estado:** propuesta de arquitectura, datos, algoritmos y experimentación; no implica que las APIs o los modelos descritos ya estén implementados.

## 1. Resumen ejecutivo

Inventary puede mejorar las ventas combinando cuatro capacidades: catálogo fiable, cotización transparente, ayuda contextual y experimentación rigurosa. La recomendación central es construir un **motor comercial dentro del monolito modular existente**, no crear un microservicio de IA prematuramente. La API del backend será la fuente de verdad para catálogo, disponibilidad, precios, promociones, cotizaciones, pedidos, consentimiento y reglas; React y WhatsApp serán canales que presentan esas mismas decisiones.

La primera versión no necesita un modelo grande ni inferir la personalidad del comprador. Necesita instrumentar el recorrido con eventos fiables, corregir datos de producto, hacer cotizaciones coherentes y medir las fricciones. Los modelos estadísticos solo se habilitan cuando existen datos suficientes, resultados etiquetados y un sistema de evaluación que permita detectar daños además de mejoras.

### Principios no negociables

1. Optimizar la compra útil y sostenible, no únicamente clics, facturación bruta o cantidad de mensajes.
2. No inferir diagnósticos psicológicos, vulnerabilidad emocional, situación financiera u otros rasgos sensibles para persuadir.
3. No utilizar probabilidades de compra para cobrar un precio individual más alto ni ocultar mejores opciones.
4. No inventar escasez, reseñas, descuentos, compatibilidad, datos técnicos ni beneficios.
5. No enviar promociones por WhatsApp sin el consentimiento que corresponda a esa categoría de mensajes.
6. Explicar qué se recomienda y por qué; conservar opciones de comparar, volver atrás, rechazar y hablar con una persona.
7. Los precios, impuestos, stock, descuentos y permisos se recalculan y validan en el servidor al cotizar y al confirmar.
8. Cada decisión automatizada debe tener una métrica de éxito, guardas de seguridad, registro de versión y mecanismo de desactivación.

## 2. Qué demuestra la investigación y qué no demuestra

### 2.1 Número de opciones y esfuerzo de decisión

Scheibehenne, Greifeneder y Todd revisaron 63 condiciones de 50 experimentos, con 5.036 participantes, y encontraron un efecto promedio de sobrecarga de opciones prácticamente nulo, aunque con variabilidad considerable entre estudios. Chernev y coautores identificaron moderadores como complejidad del conjunto, dificultad de la tarea, incertidumbre de preferencias y objetivo de decisión.

**Implicación:** no se debe aplicar la regla simplista «cuantas menos opciones, más ventas». Se debe organizar el catálogo para que el comprador encuentre, compare y entienda las alternativas según la tarea. Los filtros ayudan cuando reducen búsqueda; perjudican si esconden atributos necesarios o introducen decisiones técnicas innecesarias.

Fuentes:
- Scheibehenne, Greifeneder y Todd (2010), *Can There Ever Be Too Many Options?*, Journal of Consumer Research. https://doi.org/10.1086/651235
- Chernev, Böckenholt y Goodman (2015), *Choice overload: A conceptual review and meta-analysis*, Journal of Consumer Psychology. https://doi.org/10.1016/j.jcps.2014.08.002

### 2.2 Personalización y diversidad de recomendaciones

Un experimento de campo aleatorizado sobre sistemas de recomendación concluyó que el efecto en la diversidad de ventas puede aumentar o disminuir según el diseño; ciertos sistemas también pueden homogeneizar lo que compra cada persona. La personalización no es automáticamente mejor y puede encerrar al cliente en artículos populares o repetir siempre las mismas opciones.

**Implicación:** además de relevancia y conversión, medir diversidad, cobertura de catálogo, repetición de recomendaciones y exposición de productos nuevos que sí cumplen los requisitos. Mantener una parte de resultados exploratorios, sin sacrificar disponibilidad ni pertinencia.

Fuente: *Economic corollaries of personalized recommendations*, Journal of Retailing and Consumer Services (2022). https://doi.org/10.1016/j.jretconser.2022.103003

### 2.3 El clic no equivale a preferencia

Los datos implícitos están sesgados: un producto puede recibir más clics porque aparece en una posición más visible, no porque sea más relevante. El trabajo de Qin y coautores en ACM SIGKDD 2020 estudia estimación de propensión y métodos contrafactuales para corregir sesgo de exposición/posición.

**Implicación:** registrar impresiones realmente visibles, posición, ubicación del módulo, variante experimental y canal, además de clics, carritos y compras. No entrenar un recomendador solo con clics sin registrar qué productos tuvo oportunidad real de ver la persona. La evaluación offline debe documentar el sesgo que sigue presente.

Fuente: Qin et al. (2020), *Attribute-based Propensity for Unbiased Learning in Recommender Systems*. https://research.google/pubs/attribute-based-propensity-for-unbiased-learning-in-recommender-systems-algorithm-and-case-studies/

### 2.4 Abandono y riesgo percibido

Un metaanálisis publicado en 2026 resume 153 tamaños de efecto procedentes de 60 estudios independientes y 49 artículos. Reporta una asociación positiva entre riesgo percibido y abandono de carrito (r = 0,244; IC 95 %: 0,191–0,296) y subraya que varía según el contexto. Es evidencia de asociación agregada, no una ecuación universal que predice cada cliente.

**Implicación:** priorizar incertidumbres que el producto puede resolver: coste total, plazo, disponibilidad, condiciones de devolución, seguridad de pago, compatibilidad, garantía y errores del flujo. Identificar el motivo mediante eventos observables o una pregunta opcional; no afirmar que el modelo conoce el estado emocional del usuario.

Fuente: *When Does Perceived Risk Drive Shopping Cart Abandonment? A Meta-Analysis of Contextual Heterogeneity in E-Commerce* (2026). https://www.mdpi.com/0718-1876/21/8/265

### 2.5 Experimentación online y calidad estadística

Microsoft Research recomienda métricas predefinidas, medición holística, métricas de guarda y controles de calidad de datos. Una discrepancia entre proporciones asignadas a variantes (sample ratio mismatch, SRM) puede invalidar una conclusión. Mirar resultados repetidamente y detener la prueba cuando «ya parece ganar» también eleva el riesgo de falsos positivos.

Fuentes:
- Microsoft Research, *Patterns of Trustworthy Experimentation: During-Experiment Stage*. https://www.microsoft.com/en-us/research/articles/patterns-of-trustworthy-experimentation-during-experiment-stage/
- Fabijan et al. (2019), *Diagnosing Sample Ratio Mismatch in Online Controlled Experiments*. https://www.microsoft.com/en-us/research/publication/diagnosing-sample-ratio-mismatch-in-online-controlled-experiments-a-taxonomy-and-rules-of-thumb-for-practitioners/
- Dmitriev et al. (2017), *A Dirty Dozen: Twelve Common Metric Interpretation Pitfalls in Online Controlled Experiments*. https://doi.org/10.1145/3097983.3098024

### 2.6 WhatsApp y consentimiento

La política vigente de WhatsApp Business exige consentimiento explícito para contactar a las personas por WhatsApp, respeto de las solicitudes de baja y plantillas aprobadas para iniciar conversaciones en la plataforma. La política actual describe una ventana de atención de 24 horas desde el último mensaje del usuario para respuestas sin plantilla; fuera de ella rigen las plantillas aprobadas y las condiciones aplicables. También exige vías oportunas de escalamiento desde automatizaciones.

**Implicación:** el chatbot no es un canal libre para perseguir carritos. Separar el consentimiento para actualizaciones operativas del pedido, ofertas y recomendaciones; no asumir que entregar un número de teléfono equivale a aceptar marketing. Verificar esta política y los límites técnicos de Cloud API al implementar, porque Meta puede actualizarlos.

Fuente oficial: https://business.whatsapp.com/policy/preview?lang=es_LA

### 2.7 Protección de datos en Ecuador

La Superintendencia de Protección de Datos Personales publica guías para gestión de riesgos y evaluación de impacto. Su normativa y criterios contemplan evaluación previa en determinados tratamientos de alto riesgo, por ejemplo ciertos casos de perfilamiento automatizado que afecten derechos o libertades, y tratamientos a gran escala. No debe afirmarse que cualquier recomendador siempre obliga a una evaluación de impacto, ni asumir que ningún modelo la requiere: la calificación depende de su diseño, escala, finalidad y efectos.

**Implicación:** evaluar antes de recopilar datos, limitar finalidades, establecer conservación, seguridad, transparencia y derechos, y solicitar revisión jurídica/de protección de datos si se va a escalar el perfilamiento. El diseño por defecto debe recopilar solo lo necesario.

Fuentes oficiales:
- SPDP, *Guía de Gestión de Riesgos y Evaluación de Impacto del Tratamiento de Datos Personales* (2025). https://www.spdp.gob.ec/docs/guias/2025-2/guia-de-gestion-de-riesgos-y-evaluacion-de-impacto-del-tratamiento-de-datos-personales/
- SPDP, *Guía de Protección de Datos Personales Desde el Diseño y por Defecto*. https://www.spdp.gob.ec/docs/guias/2025-2/guia-de-proteccion-de-datos-personales-desde-el-diseno-y-por-defecto/
- SPDP, resolución y criterios publicados. https://www.spdp.gob.ec/resoluciones/

## 3. «Perfil psicológico» frente a señales comerciales observables

No recomiendo diagnosticar personalidad, impulsividad, inseguridad, vulnerabilidad o nivel socioeconómico a partir de clics, mensajes o compras. Esas inferencias pueden ser erróneas, invasivas y no son necesarias para mejorar la experiencia. En su lugar, representar estados de tarea y preferencias expresadas.

| Señal admisible, cuando existe base legal y finalidad clara | Uso útil |
|---|---|
| Búsqueda escrita por la persona | Comprender la solicitud y filtrar catálogo |
| Atributos y presupuesto declarados explícitamente | Comparar productos que cumplen los requisitos |
| Producto y categoría vistos, con exposición medida | Evitar repetir opciones irrelevantes y medir búsqueda |
| Cambio de cantidad, error de formulario o cotización vencida | Corregir fricción concreta |
| Duda expresada sobre entrega, garantía o compatibilidad | Mostrar información comprobada correspondiente |
| Recencia y frecuencia de compras, con transparencia y propósito | Atención posventa o reposición relevante |
| Canal elegido y consentimiento por categoría | Respetar cómo y qué comunicaciones acepta |

No usar para el motor comercial: salud, religión, orientación sexual, etnia, vulnerabilidad emocional, inferencias psicológicas clínicas o señales de dificultad financiera. No convertir una etiqueta operacional como «está comparando opciones» en una afirmación sobre su personalidad.

## 4. Arquitectura propuesta: un motor, varios canales

### 4.1 Fuente de verdad

Mantener al backend Spring Boot como autoridad de:

- Catálogo, atributos, variantes, unidades e imágenes publicadas.
- Disponibilidad por almacén y reglas de reserva.
- Precio aplicable, lista de precios, autorizaciones, descuentos y promociones.
- Impuestos y resumen de cotización, según reglas vigentes.
- Carrito, cotización con versión/expiración, pedido e idempotencia.
- Identidad, permisos, consentimiento, historial necesario y auditoría.
- Elegibilidad de recomendaciones y razón entendible de cada resultado.

React no recalcula reglas comerciales ni inventa precios a partir de campos visibles. WhatsApp/n8n tampoco mantiene una segunda versión independiente del catálogo o de las promociones: ambos consumen los mismos contratos de API y la misma cotización.

### 4.2 Arquitectura incremental

**Primero:** conservar el monolito modular actual y definir interfaces de dominio internas: calidad de catálogo, búsqueda, recomendaciones, cotización, promociones, señales/eventos, consentimiento y experimentación. No añadir un servicio ML ni un broker como requisito de la primera fase.

**Después:** si la carga y las necesidades reales justifican separación, extraer componentes con contratos estables. No confundir la presencia de Kafka opcional o del patrón Outbox con una plataforma analítica completa: según el estado documentado del proyecto, el consumidor existente se centra en invalidar caché y no equivale a un almacén de eventos comerciales listo para entrenar modelos.

### 4.3 Flujo común

1. El canal solicita productos o expresa una necesidad.
2. El backend devuelve productos elegibles y motivos de recomendación.
3. El canal crea/actualiza el carrito mediante API.
4. El backend genera una cotización con líneas, precios, descuentos, disponibilidad, cargos, impuestos aplicables, total, versión y vencimiento.
5. Si el precio/stock/promoción cambia, la API devuelve una diferencia explicable y solicita que la persona confirme la nueva cotización.
6. El pedido se registra con una clave de idempotencia y un identificador de correlación.
7. El backoffice completa el proceso operativo aplicable; no se etiqueta como pago confirmado ni factura fiscal autorizada si ese evento aún no ocurrió.
8. Se registran eventos de resultado necesarios y autorizados para evaluación.

### 4.4 APIs existentes frente a APIs propuestas

El README actual ya documenta el flujo de WhatsApp: catálogo, creación de carrito mediante POST a /carts, edición mediante PUT a /carts/{id}/items/{productId}, cotización mediante POST a /carts/{id}/quotes y creación de pedido mediante POST a /orders con Idempotency-Key y consentimiento confirmado. El pago se confirma en backoffice mediante las rutas documentadas de pedidos WhatsApp. Esto se debe reutilizar, no duplicar.

Las siguientes son **propuestas, no endpoints confirmados como implementados**:

| Contrato candidato | Responsabilidad |
|---|---|
| GET /api/v1/recommendations con placement, productId o cartId | Obtener candidatos elegibles, motivo y versión del ranking |
| POST /api/v1/commercial-events | Ingerir eventos de producto/carrito con esquema versionado e idempotencia |
| GET /api/v1/carts/{id}/recovery-summary | Retomar un carrito del mismo cliente cuando exista autenticación/consentimiento apropiado |
| GET/PUT de preferencias y consentimientos por categoría | Consultar y cambiar permisos para comunicaciones, con auditoría |
| APIs administrativas de experimentos | Configurar variante, unidad de aleatorización, fechas, hipótesis, métricas y guardas |
| API de explicación de cotización | Mostrar líneas, promociones, restricciones y diferencias entre versiones |

Antes de implementar rutas nuevas, comparar con el catálogo de APIs actual y extender lo existente en lugar de crear duplicados.

### 4.5 Contrato de recomendación

Cada producto recomendado debería incluir identificador, versión de catálogo, disponibilidad o condición comercial, información suficiente para representarlo, razón legible y acciones permitidas. Ejemplos de razones honestas: «coincide con la medida que buscaste», «compatible con el modelo que elegiste», «está disponible en este local» o «complementa el artículo del carrito». No presentar puntuaciones internas como si fueran certezas psicológicas.

El servidor aplica primero filtros obligatorios: publicado, permisos, elegibilidad, política comercial, disponibilidad, restricciones y precio válido. Solo después ordena candidatos. Un producto fuera de stock puede mostrarse como alternativa claramente marcada o con aviso de reposición; no fingir disponibilidad.

## 5. Modelos estadísticos por etapas

### Fase 0 — Reglas y medición, sin aprendizaje automático

Crear una taxonomía de categorías y atributos útil; corregir información faltante; definir eventos; garantizar que cotización y pedido sean coherentes; medir búsqueda, errores, stockouts y abandono por paso. Usar reglas de negocio transparentes y recomendaciones por contenido de producto cuando haya atributos fiables.

No entrenar modelos hasta resolver trazabilidad de exposición, etiquetas del resultado, duplicados, consentimiento y retención de datos.

### Fase 1 — Búsqueda, pertinencia y calidad de catálogo

Un ranking inicial combina coincidencia explícita de consulta/atributos, categoría y compatibilidad declarada, calidad de la ficha, disponibilidad y diversidad. Los pesos se versionan y prueban, no se presentan como valores científicos universales. El margen contribuye como restricción económica o desempate acotado; no debe desplazar un producto claramente irrelevante ni ser la única variable objetivo.

Para la ficha de producto se puede calcular una puntuación de completitud:

C_producto = suma(w_j × indicador_campo_j_completo) / suma(w_j)

Los pesos dependen de categoría y canal. Esta es una métrica de calidad del dato, no una probabilidad de venta ni un «puntaje psicológico». Debe explicar campos faltantes y distinguir requisitos obligatorios de campos recomendados.

### Fase 2 — Probabilidad de compra como probabilidad, no como diagnóstico

Con datos suficientes y consentimiento/finalidad válidos, probar primero una regresión logística interpretable como línea base:

P(compra en el horizonte H | señales observables) = 1 / (1 + exp(−(β₀ + βᵀx)))

Las señales posibles incluyen consulta explícita, categoría/atributos seleccionados, vista real de producto, cambios de carrito, disponibilidad, precio total ya visible, error técnico o paso alcanzado. Debe definirse H por ciclo de compra y canal. La probabilidad se calibra y se evalúa fuera del periodo de entrenamiento, separando datos en el tiempo para evitar fuga de información.

No utilizar solo AUC: revisar calibración, Brier score, log loss, precisión/recobrado si hay desbalance, cobertura y utilidad de decisión. Un modelo con buen ranking pero probabilidades mal calibradas no debe decidir descuentos ni mensajes automáticamente.

### Fase 3 — Diagnóstico de abandono por fricción

Definir una etiqueta reproducible: carrito válido iniciado y sin pedido completado tras un horizonte apropiado a la categoría/canal. Distinguir carrito activo, compra retrasada, error técnico, stock no disponible y abandono confirmado por regla temporal. No tratar la ausencia inmediata de compra como desinterés definitivo.

El modelo debe ayudar a explicar la fricción probable —por ejemplo, coste total inesperado, plazo no visible o fallo en el paso de confirmación— y a priorizar mejoras del producto. La respuesta preferente dentro de la sesión es resolver la fricción: mostrar coste, disponibilidad, política de devolución, alternativas o ayuda humana. No enviar mensajes proactivos fuera del canal sin consentimiento específico.

### Fase 4 — Medir el efecto incremental de las promociones

Una alta probabilidad de compra no implica que la oferta vaya a causar una compra: algunos clientes habrían comprado de todas formas. Para aprender a quién ayuda una promoción, usar experimentos aleatorizados y estimar, cuando el tamaño de muestra lo permita:

Uplift(x) = P(compra | oferta, x) − P(compra | control, x)

La decisión económica debe considerar margen de contribución incremental, coste del descuento, costes de contacto y devoluciones; no solo conversión. Mantener un grupo de control elegible, respetar el consentimiento, limitar frecuencia y registrar quién fue realmente expuesto. Nunca ofrecer un descuento falso, ocultar una oferta disponible para inducir una compra más cara ni cambiar precios individuales por vulnerabilidad o propensión.

No desplegar uplift modelling con datos observacionales sin un diseño causal defendible. La selección previa de clientes para recibir ofertas produce sesgo; una correlación no prueba que la promoción causó una venta.

### Fase 5 — Recomendación híbrida con evaluación contrafactual

Cuando exista suficiente volumen y calidad, comparar:
- contenido de producto y compatibilidad;
- popularidad reciente ajustada por exposición y disponibilidad;
- complementos y sustitutos basados en compras reales;
- modelos colaborativos si hay suficientes usuarios/productos y una finalidad aprobada.

Registrar impresión, posición, clic, add-to-cart, cotización y compra. Evitar entrenar únicamente con clics; los productos no mostrados no pueden interpretarse como rechazados. La evaluación con propensiones/IPW requiere supuestos y propensiones estimables; informar límites y usar pruebas online controladas antes de desplegar.

## 6. Taxonomía de eventos mínima

Los eventos se deben definir, versionar y validar con backend antes de instrumentar UI. Ejemplos:

- product_impression: producto realmente visible, posición y espacio de colocación.
- search_submitted: consulta necesaria para mejorar búsqueda, con política de retención y sanitización.
- product_opened y product_compared: acción observable.
- cart_created, cart_item_added, cart_quantity_changed y cart_item_removed.
- quote_created, quote_expired y quote_price_changed.
- checkout_step_viewed y checkout_validation_failed, con código de error y sin copiar datos personales al log.
- promotion_eligible, promotion_shown y promotion_applied.
- order_created, order_completed y payment_state_changed, con estado de negocio definitivo.
- return_created, refund_state_changed y cancellation_requested.
- whatsapp_consent_granted/revoked por categoría, bot_handoff y customer_request_resolved.

Cada evento tiene un ID idempotente, eventType y eventSchemaVersion, hora del servidor, canal, identificador seudónimo de sesión/cliente cuando proceda, contexto mínimo, correlación y variante experimental. No registrar números completos de identificación, tarjetas, credenciales ni transcripciones enteras en analytics. Los errores y eventos deben poder rastrearse sin sobrecolectar datos.

## 7. Experimentos A/B: cómo demostrar mejoras

Antes de cada prueba, documentar hipótesis falsable, cambio aislado, población elegible, unidad de aleatorización, métrica primaria, guardas, duración y tamaño de muestra estimado. Asignar una persona/sesión establemente para evitar que cambie entre variantes; mantener la misma asignación entre web y WhatsApp cuando sea posible y legalmente válido.

### Métrica primaria recomendada

Elegir una sola para cada experimento, según su objetivo:
- compras completadas por sesión elegible; o
- margen de contribución neto incremental por sesión elegible, cuando costes y devoluciones se midan de forma fiable.

No declarar ganador por clics o facturación bruta si aumentan descuentos, devoluciones o reclamaciones. Esperar el horizonte de compra/devoluciones definido antes de cerrar conclusiones.

### Métricas de diagnóstico

Conversión por paso, búsqueda sin resultado, tiempo para encontrar un producto, add-to-cart, cotizaciones vencidas, cambios de precio rechazados, disponibilidad fallida, uso de filtros, consultas al asesor y razón de devolución.

### Guardas obligatorias

Tasa de errores, latencia, stockouts, venta duplicada, devolución y cancelación, margen, coste de promoción, reclamaciones, baja/bloqueo de WhatsApp, mensajes rechazados por política, diferencia entre cotización y orden y brechas importantes entre canales.

Comprobar sample ratio mismatch y calidad/integridad de eventos antes de interpretar el resultado. Si se detecta SRM, telemetría perdida o una degradación material, pausar la prueba e investigar. Predefinir cómo se corrigen múltiples comparaciones y evitar detener anticipadamente la prueba solo por observar un resultado favorable.

## 8. Frontend React: adaptarse al contrato de backend

El frontend representa las decisiones devueltas por API, no replica la lógica. Cada pantalla debe soportar explícitamente:
- carga, error recuperable, sin resultados, agotado, restricción de permiso y éxito;
- disponibilidad y precio con marca temporal/estado de cotización cuando corresponda;
- explicación corta de recomendaciones, con opción de ver más alternativas;
- carrito persistente ante fallo de red, reintento idempotente y reconciliación del pedido;
- teclado, lector de pantalla, móvil y mensajes de error enfocados en la acción correctiva.

No mostrar puntuaciones de modelos, falsos niveles de certeza ni «probabilidad de que compres». Mostrar solo explicaciones de negocio que el backend pueda respaldar. El backend puede devolver códigos estables de decisión; el frontend asigna texto accesible y traducciones sin cambiar el significado de la regla.

## 9. WhatsApp: el mismo motor comercial y una salida humana

### Entender el mensaje sin perfilar psicológicamente

Clasificar la petición operativa expresada: descubrir productos, comparar características, consultar precio, disponibilidad, entrega, garantía, continuar cotización, estado de pedido o devolución. La clasificación debe tener umbral de confianza y permitir preguntar «¿Buscas X o Y?» cuando no entienda. No debe diagnosticar personalidad o emociones, ni inferir preferencias sensibles de estilo de escritura.

### Estado, carrito y continuidad entre canales

Guardar el estado transaccional en backend, no únicamente en la memoria del workflow de n8n. Una misma persona debe poder continuar una cotización en web con autenticación/enlace seguro, sin que el chatbot cree otro precio o duplique la orden. Las claves de idempotencia y el estado final pertenecen al backend.

### Consentimiento y seguridad de la conversación

- Mantener consentimientos separados para actualizaciones del pedido, promociones y recomendaciones.
- Respetar baja/cancelación inmediatamente, registrar momento y origen y detener la categoría correspondiente.
- Fuera de la ventana de atención, usar solo plantillas aprobadas cuando la política vigente lo exija.
- Ofrecer transferencia a un agente humano claramente visible y rápida ante petición explícita, baja confianza, reclamación, discrepancia de dinero o excepción.
- No pedir números completos de tarjeta, cuentas bancarias ni identificadores personales confidenciales por el chat; dirigir el proceso que requiera datos sensibles a un flujo seguro y autorizado.
- No enviar contenido de un cliente a otro ni reutilizar información de la conversación para fines incompatibles con las reglas de WhatsApp y la ley aplicable.

Fuente: política oficial de WhatsApp Business. https://business.whatsapp.com/policy/preview?lang=es_LA

## 10. Modelo de negocio y datos: condiciones antes de activar ML

Antes de entrenar cualquier modelo, responder y documentar:

1. ¿Cuál es el resultado objetivo y el horizonte temporal?
2. ¿Qué eventos están completos y se registran de forma consistente entre canales?
3. ¿Se conoce la exposición real de cada producto/promoción?
4. ¿El resultado se produce después de la señal y no hay fuga de datos?
5. ¿Hay suficientes ejemplos positivos y negativos por categoría?
6. ¿Qué sesgos de selección, posición, stock, estacionalidad y vendedor existen?
7. ¿Cuál es la métrica fuera de muestra y su intervalo de incertidumbre?
8. ¿Qué cambio operativo se permite que tome el modelo y qué debe aprobar una persona?
9. ¿Cuál es la finalidad, base legal, conservación, acceso y mecanismo de oposición/baja aplicable?
10. ¿Cómo se apaga el modelo y se vuelve a reglas deterministas si falla?

Con poco volumen, empezar con reglas, estadísticas agregadas y estimaciones suavizadas. No tratar una variación por dos o tres ventas como evidencia. Una probabilidad sin calibración, horizonte o evaluación no debe mostrarse como certeza.

## 11. Métricas ejecutivas

Un tablero de negocio puede medir semanalmente:
- conversión y margen neto por canal/cohorte, después de descuentos y devoluciones;
- porcentaje de búsquedas sin resultado y tiempo para encontrar el artículo;
- carritos creados, cotizados, recuperados en sesión y completados;
- abandono por paso y por fricción observada;
- productos sin foto/atributos claves, cobertura del ranking y diversidad de catálogo;
- exactitud de disponibilidad, cotizaciones vencidas y discrepancias entre canales;
- devoluciones, cancelaciones, quejas y contacto con soporte por venta;
- rendimiento y coste incremental de promociones, con grupo de control;
- opt-ins, bajas, bloqueos y quejas de WhatsApp;
- calidad de datos, SRM, latencia y fallos de API.

Segmentar por canal, categoría y dispositivo cuando sea útil y proporcional. No convertir métricas agregadas en etiquetas psicológicas individuales.

## 12. Plan de implantación priorizado

### Etapa A — Cimientos, sin ML
1. Inventariar y probar contratos de catálogo, precio, carrito, cotización, pedido e integración WhatsApp actuales.
2. Alinear el cálculo de precios del frontend y la API; el backend recalcula al confirmar.
3. Añadir/normalizar códigos de error, idempotencia y trazabilidad entre cotización y pedido.
4. Definir taxonomía de productos y reglas de completitud por categoría.
5. Diseñar eventos de negocio, consentimiento, retención y control de acceso.
6. Verificar el flujo de WhatsApp y los mecanismos de baja/escalamiento según la política vigente.

### Etapa B — Relevancia y diagnóstico
1. Mejorar búsqueda, filtros y comparación.
2. Implementar recomendaciones deterministas por atributos, compatibilidad y complementos.
3. Medir impresiones reales, posiciones y resultados de cada canal.
4. Crear tablero de embudo con métricas primarias y guardas.
5. Ejecutar pruebas moderadas de usabilidad con vendedores y compradores.

### Etapa C — Experimentos controlados
1. A/B de un único cambio visual o de búsqueda por vez.
2. Probar mensajes de información útil, no técnicas de presión.
3. Medir efectos en conversión, margen neto, devoluciones, quejas y baja de WhatsApp.
4. Corregir calidad de datos/SRM antes de decidir despliegues.

### Etapa D — Modelos supervisados
1. Establecer baseline logística y evaluación temporal.
2. Calibrar probabilidades, medir deriva y cobertura.
3. Aprender uplift de promociones solo con diseño causal suficiente.
4. Evaluar diversidad, stock, margen, privacidad y efectos no deseados.
5. Activar gradual con feature flag, revisión y rollback.

### Criterio de aceptación global

No se aprueba un modelo porque tenga AUC alto o porque aumente clics. Se aprueba si mejora la métrica principal frente a control con una incertidumbre razonable, mantiene todas las guardas dentro de límites, sus decisiones pueden explicarse, los datos son válidos y existen consentimiento, auditoría y rollback.

## 13. Bibliografía y políticas de referencia

- Scheibehenne, B., Greifeneder, R., & Todd, P. M. (2010). *Can There Ever Be Too Many Options? A Meta-Analytic Review of Choice Overload*. https://doi.org/10.1086/651235
- Chernev, A., Böckenholt, U., & Goodman, J. (2015). *Choice overload: A conceptual review and meta-analysis*. https://doi.org/10.1016/j.jcps.2014.08.002
- Qin, Z. et al. (2020). *Attribute-based Propensity for Unbiased Learning in Recommender Systems*. https://research.google/pubs/attribute-based-propensity-for-unbiased-learning-in-recommender-systems-algorithm-and-case-studies/
- *Economic corollaries of personalized recommendations* (2022). https://doi.org/10.1016/j.jretconser.2022.103003
- *When Does Perceived Risk Drive Shopping Cart Abandonment?* (2026). https://www.mdpi.com/0718-1876/21/8/265
- Microsoft Research. *Patterns of Trustworthy Experimentation: During-Experiment Stage*. https://www.microsoft.com/en-us/research/articles/patterns-of-trustworthy-experimentation-during-experiment-stage/
- Fabijan et al. (2019). *Diagnosing Sample Ratio Mismatch*. https://www.microsoft.com/en-us/research/publication/diagnosing-sample-ratio-mismatch-in-online-controlled-experiments-a-taxonomy-and-rules-of-thumb-for-practitioners/
- WhatsApp Business. *Política de mensajes de WhatsApp Business* (consultada el 28-10-2026). https://business.whatsapp.com/policy/preview?lang=es_LA
- Superintendencia de Protección de Datos Personales (Ecuador). Guías de gestión de riesgos y privacidad desde el diseño. https://www.spdp.gob.ec/docs/guias/2025-2/guia-de-gestion-de-riesgos-y-evaluacion-de-impacto-del-tratamiento-de-datos-personales/

La evidencia citada guía hipótesis y controles; no sustituye validación en Inventary, pruebas con usuarios, revisión legal local ni análisis estadístico de los datos reales.


---

## 14. Fulfillment es parte de la promesa comercial, no un detalle de checkout

La probabilidad de que el comprador acepte un producto depende también de si puede recibirlo de una manera conveniente, cuándo y desde dónde. Por ello, el motor de recomendaciones no debe rankear únicamente productos y precio: debe aplicar primero la elegibilidad logística de cada opción.

Las opciones comerciales deben ser compatibles con las capacidades reales del sistema:

- retirar en la bodega/local que tiene stock;
- enviar al domicilio desde una ubicación de origen válida;
- retirar en otro local solamente si existe un proceso de transferencia y recepción;
- fulfillment dividido en varios grupos solamente si el backend lo soporta y el cliente acepta costes/plazos por grupo.

La API debe separar origen del inventario, destino del cliente, método de cumplimiento, disponibilidad reservable, coste logístico y ventana estimada. No asumir que el almacén de origen es el local de retiro o que la dirección de facturación es la dirección de entrega.

### Cómo afecta al ranking y a los modelos

1. Aplicar restricciones duras antes de ordenar: producto publicado, permiso, cantidad disponible, ubicación activa y modalidad posible.
2. Después ordenar por coincidencia con la necesidad expresada, compatibilidad, coste total transparente y promesa de entrega validada.
3. No favorecer una opción por una estimación de entrega que no tenga datos fiables; no prometer distancia/tiempo si faltan cobertura, tarifa o capacidad.
4. Registrar el origen mostrado, modalidad, tarifa, promesa y resultado, para detectar cuándo una recomendación prometió un modo de recepción que luego no podía cumplirse.
5. Medir conversión, margen, devolución, entrega a tiempo, retiro no completado, coste de envío y cancelaciones por separado, segmentados por modalidad y ubicación.

### Datos y eventos de fulfillment

El esquema de eventos debe incorporar el modo y grupo de cumplimiento: opciones mostradas, origen seleccionado, dirección/punto elegido, cotización logística, reserva, preparación, listo para retiro, despacho, evento en tránsito, recogida, entrega, excepción, cancelación y expiración/liberación. No registrar dirección completa, códigos de retiro ni teléfono directamente en la telemetría analítica.

El inventario y la asignación final son autoridad del backend. Un modelo predictivo puede sugerir una opción logística, pero no puede anular restricciones de stock, permisos, cobertura, capacidad o autorización del usuario. El resultado debe persistir como una decisión validada o como una sugerencia que requiera confirmación, según el riesgo.

El detalle de flujos, modelo de FulfillmentGroup/FulfillmentLine, contratos, fases y pruebas está en [PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md).

---

## Adenda fiscal — La conversión comercial termina solo con estados verificables

Contrato canónico: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

El motor comercial puede optimizar búsqueda, cotización, experiencia y conversión; no puede inventar datos tributarios ni considerar que una venta interna equivale a una factura autorizada. Los eventos de analítica deben separar como mínimo:
- sale_created / venta interna registrada;
- fiscal_issue_requested;
- fiscal_document_authorized;
- fiscal_document_rejected o fiscal_issue_retryable_error;
- cancellation_requested / cancellation_confirmed;
- sales_return_posted;
- credit_note_requested / credit_note_authorized;
- refund_requested / refund_confirmed, solo cuando exista un sistema de pagos.

Los nombres anteriores son conceptos y no implican que los eventos estén ya implementados. Los eventos reales deberán salir del contrato de backend y llevar eventId/correlationId. No contar factura solicitada como factura autorizada, ni nota de crédito como dinero reembolsado. Los informes deben incluir el estado fiscal cuando mida facturación/ventas y deben evitar duplicar una operación al reintentar.

La anulación y las notas de crédito deben seguir el contrato fiscal. La norma reformada por NAC-DGERCGC25-00000017 cambia al día 7 del mes siguiente el plazo del artículo 3 desde el 1 de enero de 2026 y reformó el artículo 5; no basar campañas, experimentos o automatizaciones en la regla desactualizada del día 10.

---

## Revisión analítica — No multiplicar la venta por cada establecimiento que sirve una bodega

Los informes y recomendaciones deben respetar la relación muchos-a-muchos entre bodegas y establecimientos SRI. Una bodega puede estar relacionada con varios establecimientos; un establecimiento puede recibir servicio de varias bodegas. Esta relación describe el ámbito operacional, no una clave de propiedad ni la identidad del emisor fiscal.

- Las ventas se atribuyen por la venta/fulfillment/contexto fiscal realmente registrados, no por todos los establecimientos asociados a cada bodega.
- El stock se cuenta por propietario, producto y bodega, y no se replica al agrupar por establecimientos sin una regla explícita de atribución.
- Las métricas que comparan establecimiento con inventario diferencian stock físico, stock asignado y abastecimiento.
- Si una bodega está compartida entre RUC diferentes, el sistema debe disponer de owner/legalEntity segregado o impedir recomendaciones y agregados que mezclen el stock.
- Los agentes explican la fuente y la regla de unión de cada métrica; no calculan join por parecido de nombre ni seleccionan un establecimiento arbitrario.
