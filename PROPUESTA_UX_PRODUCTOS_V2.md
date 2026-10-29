# Propuesta UX/UI de Productos — versión revisada

> **Disponibilidad logística:** el stock debe presentarse por local/bodega y diferenciarse de la disponibilidad para retiro o despacho. Ver [PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md).


**Fecha:** 28 de octubre de 2026  
**Base:** propuesta original de Productos y estado del repositorio documentado; no se ha modificado código fuente.  
**Relación:** reemplaza como dirección de diseño las prioridades de la propuesta anterior; conserva sus ideas de búsqueda, variantes, edición masiva y asistencia humana. La arquitectura de API, métricas, modelos y WhatsApp se detalla en [PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md).

## 1. Objetivo

La pantalla de Productos debe ayudar a encontrar, entender, crear y mantener una ficha correcta con el mínimo trabajo repetido. Una ficha precisa es la base de una búsqueda buena, una recomendación pertinente, una cotización correcta y una respuesta confiable del chatbot. El objetivo no es hacer el producto artificialmente persuasivo: es permitir que el comprador compare alternativas reales y que el negocio reduzca errores de información.

## 2. Estructura de la pantalla de catálogo

### Lista de productos

- Buscador prominente por nombre, SKU, código de barras y atributos relevantes.
- Filtros combinables con resumen visible de filtros aplicados y acción para limpiar.
- Columnas configurables según tarea: nombre/foto, SKU, categoría, tipo, precio de venta autorizado, estado de publicación y disponibilidad resumida.
- Etiquetas explícitas para servicio, producto con stock, variante, borrador, publicado y agotado.
- Paginación y estado de ordenamiento claros; conservar filtros al volver del detalle.
- Acciones principales visibles; acciones destructivas o poco frecuentes dentro de un menú con confirmación contextual.
- Estado vacío que ofrezca pasos para crear el primer producto o retirar filtros, sin mostrar una pantalla muerta.

La cantidad de controles no se debe reducir solo por un supuesto psicológico. La literatura de choice overload encuentra efectos dependientes de contexto; diseñar filtros claros y medir si facilitan la tarea es más responsable que quitar opciones arbitrariamente.

### Detalle de producto

Organizar la información por propósito, con encabezado persistente que comunique nombre, SKU, estado y acciones autorizadas. Mostrar alertas accionables, no una lista de errores sin explicación. La vista del catálogo es ficha maestra; los movimientos, existencias reales y valoración pertenecen a los flujos de inventario.

## 3. Formulario guiado por tipo y categoría

Usar un stepper cuando la creación o edición incluya varios conjuntos de campos. El formulario puede variar según la categoría y el tipo de producto, pero la UI debe recibir la estructura y obligatoriedad del backend, no duplicarlas en condiciones dispersas de React.

### Paso 1 — Identidad y clasificación
- Nombre, SKU/código, categoría y tipo de producto o servicio.
- Unidad de medida y reglas de conversión cuando apliquen.
- Identificación de variante/familia cuando el producto lo necesite.
- Guardar como borrador, incluso si faltan campos opcionales.

### Paso 2 — Atributos comerciales
- Marca/modelo, compatibilidad declarada, dimensiones, presentación, contenido del paquete u otros atributos según categoría.
- Solo mostrar campos útiles para esa categoría.
- Marcar con lenguaje claro los campos obligatorios y explicar para qué se piden.
- No afirmar compatibilidad o especificaciones no verificadas.

### Paso 3 — Imagen y descripción
- Imagen principal identificada y galería ordenada.
- Texto alternativo y descripción legible.
- Ayuda de IA opcional para título, resumen, normalización y términos de búsqueda.
- Mostrar la propuesta generada y permitir comparar, editar, aprobar o rechazar antes de guardar/publicar.

### Paso 4 — Revisión y publicación
- Resumen de cambios.
- Reglas bloqueantes y campos pendientes.
- Estado de publicación real.
- Confirmar antes de aplicar cambios masivos o acciones que afecten a ventas.

En un producto simple, estos pasos pueden reducirse si no añaden valor; en formularios extensos, evitar una modal con scroll largo.

## 4. Calidad de ficha: puntuación explicable

Se propone una medida de completitud por categoría/canal:

C = suma(wⱼ × indicador de campo válidoⱼ) / suma(wⱼ)

Los pesos reflejan la importancia del campo para esa categoría y deben documentarse. Esta puntuación mide calidad del dato, no la intención del comprador ni la probabilidad de venta. En vez de mostrar un número aislado, la UI presenta mejoras concretas: falta imagen principal, unidad por confirmar o atributo requerido sin completar.

Separar:
- Requisitos duros para publicar.
- Campos recomendados que mejoran búsqueda/comparación.
- Campos no aplicables a ese tipo de artículo.
- Datos calculados, como disponibilidad, que no se editan desde la ficha maestra.

El borrador se debe poder guardar aunque la ficha no esté lista. Los requisitos de publicación sí se validan en backend y muestran un error por campo.

## 5. Edición masiva y variantes

La edición masiva debe permitir elegir columnas, seleccionar productos, previsualizar el antes/después, validar por fila y aplicar con un resumen. Detectar cambios concurrentes para no sobrescribir silenciosamente una edición de otra persona. Si unas filas fallan, reportar cuáles y permitir reintentar solo esas.

Una familia de variantes debe ser legible: resaltar qué atributo cambia, conservar SKU por variante y evitar que el listado se convierta en cientos de filas indistinguibles. Los productos alternativos, complementos y variantes deben ser conceptos separados.

## 6. Asistencia de IA: propuesta, nunca dato inventado

La IA debe partir de atributos verificados y no inventar compatibilidad, certificaciones, garantía, dimensiones, rendimiento, disponibilidad, precio, stock ni reseñas. Mostrar los campos de origen, los que faltan y el texto propuesto; las sugerencias quedan en borrador hasta aprobación humana. Auditar quién aprobó un cambio.

La primera versión debe favorecer normalización y redacción asistida. No necesita crear un perfil psicológico del comprador. Las señales de búsqueda sirven para mejorar atributos y sinónimos, siempre con finalidad clara y minimización de datos.

## 7. Recomendaciones de producto

Primero usar reglas transparentes: coincidencia de consulta y atributos, compatibilidad indicada, categoría, disponibilidad y relación complemento/sustituto. El backend filtra artículos que no están publicados o no son elegibles antes de ordenarlos.

Las recomendaciones devuelven una razón de negocio verificable, por ejemplo «coincide con la medida seleccionada» o «es compatible con el modelo indicado». No exponer score interno ni llamar «mejor para ti» a lo que solo es más popular. Marcar claramente los productos agotados; ofrecer alternativas compatibles sin fingir disponibilidad.

Una investigación de campo sobre recomendadores halló que la personalización puede aumentar o reducir la diversidad de consumo, por lo que se debe medir cobertura y repetición además de compras. Los clics están condicionados por la posición: registrar impresiones realmente visibles y posición del artículo antes de aprender de ellas.

## 8. Contratos de backend que necesita la UI

**No se afirma que estos contratos estén todos implementados.** Antes de crear rutas, contrastarlas con la referencia de APIs actual.

| Capacidad de API | Qué debe mostrar React |
|---|---|
| Esquema de atributos por categoría | Campos requeridos, tipo, unidad, ayuda y validación |
| Validación/completitud de ficha | Requisitos pendientes y acciones concretas |
| Búsqueda paginada con filtros aplicados | Resultados, total, ordenamiento y filtros activos |
| Vista previa de edición masiva | Diferencias, conflictos y errores por fila |
| Recomendaciones con razón de resultado | Producto elegible, estado comercial y razón verificable |
| Registro de evento comercial | Confirmar exposición y resultados para evaluación |

El backend determina el estado de publicación, disponibilidad, elegibilidad y razón de recomendación. React renderiza dichos resultados y traduce códigos de error estables a mensajes accesibles; no mantiene reglas duplicadas por categoría.

## 9. Métricas de éxito

Medir con tareas reales y por categoría/canal:

- Tiempo para encontrar y completar la ficha correcta.
- Búsquedas sin resultado, uso de filtros y errores de selección de variante.
- Completitud de datos clave y número de correcciones posteriores.
- Devoluciones o consultas de soporte causadas por información incorrecta.
- Tiempo de creación/edición y errores en lotes.
- Impresiones visibles, posición, aperturas, carritos y compras asociadas a recomendaciones.
- Cobertura de productos recomendados y repetición excesiva.
- Exactitud de contenido generado, devoluciones, reclamaciones y correcciones.
- Latencia, errores de API y divergencia de datos entre web y WhatsApp.

No declarar ganador un texto solo porque recibe más clics. Debe conservar exactitud, accesibilidad, disponibilidad y satisfacción, además de mejorar la tarea o una métrica comercial predefinida.

## 10. Despliegue por prioridad

1. Normalizar esquema y reglas por categoría.
2. Mejorar búsqueda, filtros, estados y validación inline.
3. Añadir la puntuación explicable de calidad de ficha.
4. Mejorar edición masiva y variantes.
5. Incorporar IA en modo borrador con revisión humana.
6. Enlazar atributos de catálogo con la API común de recomendaciones.
7. Instrumentar eventos solo después de definir esquema, finalidad, acceso y conservación.
8. Probar con usuarios y experimentos controlados antes de desplegar personalización más avanzada.

## 11. Referencias

- Scheibehenne et al. (2010), metaanálisis de choice overload: https://doi.org/10.1086/651235
- Chernev et al. (2015), moderadores de choice overload: https://doi.org/10.1016/j.jcps.2014.08.002
- Qin et al. (2020), sesgo de exposición/posición en sistemas de recomendación: https://research.google/pubs/attribute-based-propensity-for-unbiased-learning-in-recommender-systems-algorithm-and-case-studies/
- Experimento de campo sobre recomendaciones y diversidad: https://doi.org/10.1016/j.jretconser.2022.103003
- Patrones de experimentación confiable, Microsoft Research: https://www.microsoft.com/en-us/research/articles/patterns-of-trustworthy-experimentation-during-experiment-stage/
- Arquitectura transversal propuesta para modelos y WhatsApp: [PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md)


## Ampliación: mostrar dónde está el producto

La ficha y los resultados de búsqueda deben diferenciar «hay unidades en Inventary» de «puedes retirar ahora en este local» y «se puede enviar desde esta bodega». El primero es un saldo; los otros dependen del stock reservable, capacidades del punto, horario, cobertura, método de cumplimiento y estado de preparación.

Para cada ubicación, mostrar disponibilidad recibida del backend y evitar afirmaciones que supongan que un producto en Bodega Norte ya puede recogerse en Local Centro. Si se requiere moverlo, etiquetar esa opción como traslado a punto de retiro con tiempo/coste estimado y confirmación futura.

La asignación por ubicación y el flujo de preparación se detallan en [PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md](PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md). El frontend no debe simular stock por estantería, picking multi-bin o reserva si esas capacidades no están expuestas por las APIs.

---

## Adenda fiscal — Datos de producto que necesita el backend de facturación

Contrato de integración: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

La ficha de Producto debe diferenciar datos comerciales (SKU, descripción, unidad, precio) de la clasificación y tratamiento tributario que el backend fiscal necesite para emitir. El backend de facturación debe recibir una línea de venta coherente, con código/descripción, unidad, cantidad, precio, descuento y clasificación fiscal aplicable.

Antes de añadir códigos tributarios al catálogo, se debe acordar una única fuente de verdad: el backend fiscal puede mantener el mapeo de SKU/producto a códigos fiscales, o Inventary puede mantener una clasificación versionada que el facturador valida. No duplicar tarifas/códigos manualmente en React y en el otro backend, ni hardcodear una tarifa de impuesto que pueda cambiar. Los productos y sus líneas de venta deben conservar snapshot de descripción/unidad/cantidades/precio usados en el documento histórico.

El estado tributario de un producto/servicio, el tipo de producto para inventario y la tasa fiscal no son el mismo atributo. Un servicio puede generar factura sin movimiento físico; un producto stockeable puede generar movimientos y, por separado, una factura fiscal autorizada. El detalle y las propuestas de producto deben mantenerse coordinados con el contrato de facturación.

---

## Adenda adicional — Configuración fiscal del proveedor tecnológico

Contrato canónico: [PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md](PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md).

La clasificación tributaria del producto y el RUC del proveedor tecnológico son conceptos diferentes. La Resolución NAC-DGERCGC26-00000027 exige, a los emisores que usen un proveedor dentro del ámbito, incluir el RUC de ese proveedor en la sección de información adicional del comprobante. Este dato no pertenece al SKU y no se duplica en cada artículo; es configuración central del backend fiscal. El proveedor debe aplicar la ficha técnica SRI vigente (actualmente publicada como esquema Off-line 2.34, actualización julio de 2026) y mantener el dato independiente de códigos de producto/impuesto.

La aplicabilidad de registro del proveedor debe verificarse conforme al artículo 2 de la resolución y no suponerse por el simple hecho de que exista un endpoint remoto. Fuente: [aviso SRI sobre NAC-DGERCGC26-00000027](https://www.sri.gob.ec/detalle-noticias?idnoticia=1318&marquesina=1).
