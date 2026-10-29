# Propuesta de rediseño UX/UI para Productos

**Producto:** Inventary · EcuApp  
**Base inspeccionada:** master · 23c806d6ed5d06d1ff55e5a72be405167a79150f  
**Alcance:** exclusivamente la pantalla y los flujos de Catálogo/Productos, incluidas búsqueda, ficha, creación/edición, atributos, imágenes, variantes y asistencia de IA. No se modificó código.

## 1. Objetivo

La pantalla de Productos debe permitir a una persona no técnica localizar un artículo, reconocerlo visualmente, entender su estado y hacer una tarea segura con la mínima carga mental posible. No debe ser solo una tabla que enumera SKUs ni un formulario largo repleto de campos técnicos.

El catálogo es la ficha maestra del artículo. La existencia física, la valoración del stock y los movimientos pertenecen a los módulos operativos de inventario. La UI puede mostrar un resumen de disponibilidad por ubicación, pero no debe tratar la existencia como una propiedad que el usuario edita libremente en el formulario maestro.

## 2. Hallazgos sobre el punto de partida

En el SHA revisado, el frontend ya tiene una base funcional valiosa: ProductsPage.jsx, ProductForm.jsx, CategoryForm.jsx, CategoryProperties.jsx, CategoryQuickCreate.jsx, ProductPicker.jsx, componentes de imagen, tablas y estilos propios. El backend permite paginación y filtros de catálogo, esquemas tipados de atributos por categoría, productos de stock y servicios, reglas de activación/desactivación, imágenes y unidades de medida.

La oportunidad no es reescribir todo desde cero, sino llevar la experiencia desde «administrar registros» a «encontrar y mantener productos con confianza».

## 3. Investigación de mercado: patrones de sistemas especializados

### Shopify Admin

Shopify ofrece búsqueda y edición por lotes de productos/variantes: seleccionar registros, escoger qué columnas modificar, editar en una tabla y aplicar cambios a varias filas. Para miles de registros también contempla CSV. El rediseño de productos contempla gestión de variantes dentro del detalle del producto.

Aplicación para Inventary: crear una edición masiva controlada con columnas configurables, vista previa de cambios, validación por fila y resumen de éxitos/fallos; mantener variantes debajo del producto padre cuando corresponda, en lugar de inundar la lista con registros de cada variación.

Fuentes: [Shopify: edición masiva](https://help.shopify.com/en/manual/shopify-admin/productivity-tools/bulk-editing) · [Shopify: editar variantes de un producto](https://help.shopify.com/en/manual/products/variants/edit-variants).

### Shopify Magic

Shopify incluye generación de descripciones de producto con IA desde su administrador: el usuario aporta datos, elige tono, genera versiones, edita y decide cuál usar.

Aplicación para Inventary: la IA debe redactar una propuesta desde atributos existentes y nunca inventar dimensiones, compatibilidad, garantías, certificaciones, stock o especificaciones. Mostrar la propuesta en modo borrador, comparar antes/después y requerir revisión humana antes de guardar.

Fuente: [Shopify Magic para descripciones de productos](https://apps.shopify.com/built-in-features/magic-product-descriptions).

### Akeneo y Salsify: product information management / product experience

Los proveedores PIM/PXM trabajan con fuentes de datos de producto consolidadas, atributos, assets digitales, validación de completitud, colaboración y distribución de contenido a distintos canales. Salsify describe centralización, conexión a canales, onboarding y validación de contenidos y workflows habilitados por IA con supervisión humana.

Aplicación para Inventary: la ficha debe mostrar qué campos faltan según el tipo de producto/categoría y para qué uso se necesita esa información. Los errores de calidad deben ser accionables («faltan 2 atributos obligatorios») y no solo un mensaje genérico de validación. La IA puede sugerir normalización y enriquecer texto desde datos confirmados.

Fuentes: [Salsify: Product Experience Management](https://www.salsify.com/product-experience-management/) · [Salsify: enfoque de AI Commerce](https://www.salsify.com/about-salsify).

### Plytix PIM

Plytix presenta funciones para enlazar productos con imágenes/assets, edición de imágenes asistida por IA, comentarios, roles de usuario, automatizaciones y optimización de contenido mediante prompt.

Aplicación para Inventary: gestionar la galería como un conjunto ordenado de assets, enseñar cuál es la imagen principal y qué productos carecen de fotografía, mantener texto alternativo y facilitar tareas repetitivas sin ocultar quién aprobó el cambio.

Fuente: [Plytix PIM y capacidades de AI](https://www.plytix.com/pim/).

### Akeneo Product Cloud: PIM con agentes de IA y gobernanza

Akeneo promueve una fuente central de información de producto, enriquecimiento contextual, validación y activación de datos por canal. Sus recursos de IA incluyen generación de contenido a partir de atributos/assets, traducción, etiquetado de assets y funciones de agentes para detectar brechas o inconsistencias y preparar mejoras del modelo. Su documentación de AI Configuration también advierte que la IA no sustituye un buscador ni puede certificar datos que no existan en las fuentes y recomienda gobernanza y revisión del contenido generado.

Aplicación para Inventary: un agente debe trabajar con atributos, categoría y assets confirmados; sugerir diferencias y correcciones; dejar el resultado en borrador y exigir aprobación humana. No debe modificar permisos, SKU, precio, stock ni publicar cambios sin un comando explícito autorizado.

Fuentes: [Akeneo Product Cloud](https://www.akeneo.com/akeneo-product-cloud/) · [Capacidades actuales de Akeneo AI](https://help.akeneo.com/en_US/serenity-boost-your-productivity/manage-your-ai-configurations) · [AI Data Architect Agent, 2025](https://www.akeneo.com/press-release/akeneo-introduces-first-ai-data-architect-agent-and-new-px-insights-capabilities-to-prepare-enterprises-for-ai-driven-commerce).

### Baymard Institute: investigación de usabilidad de listas

El benchmark de Product List UX 2025 de Baymard evaluó más de 170 sitios de referencia; reporta UX de lista de producto pobre o mediocre en el 58 % de sitios desktop y 78 % mobile del benchmark. Sus hallazgos recomiendan filtros que se puedan combinar, resumen de filtros aplicados, thumbnails suficientes, tipos de ordenamiento comprensibles y una representación que no convierta variantes en ruido.

Esta investigación se realizó en ecommerce orientado a compradores, no en un ERP interno. Sus principios de localización y comparación se pueden adaptar, pero deben validarse con vendedores y operadores de Inventary.

Fuente: [Baymard: Product List UX Best Practices 2025](https://baymard.com/research-articles/current-state-product-list-and-filtering).

## 4. Evidencia científica: decisiones, opciones y carga de búsqueda

### Elección y número de opciones

Hick investigó la relación entre la incertidumbre de las alternativas y el tiempo de reacción en tareas de elección. Aplicación: no presentar decenas de filtros y acciones al mismo tiempo sin jerarquía; permitir comenzar con búsqueda y hacer visibles los controles relevantes según el contexto.

La ley de Hick no demuestra que «menos opciones siempre sea mejor». La pantalla de productos necesita filtrar cuando el catálogo es grande; debe organizar opciones con una estructura que permita reducir la búsqueda, no esconder capacidades importantes.

Fuente primaria: W. E. Hick, «On the Rate of Gain of Information», Quarterly Journal of Experimental Psychology (1952), [DOI 10.1080/17470215208416600](https://journals.sagepub.com/doi/10.1080/17470215208416600).

### Choice overload: no es una regla simplista

La revisión meta-analítica de Scheibehenne, Greifeneder y Todd (2010) analizó 63 condiciones de 50 experimentos, con 5.036 participantes, y encontró un efecto medio cercano a cero, aunque con variación considerable entre estudios. Por tanto, la recomendación no es quitar funcionalidades indiscriminadamente, sino organizar opciones según tareas, hacer búsqueda/filtros comprensibles y evitar obligar a memorizar información entre pantallas.

Fuente: [Journal of Consumer Research, DOI 10.1086/651235](https://doi.org/10.1086/651235).

### Valores predeterminados y autocompletado

Una meta-análisis de 58 estudios de default effects (muestra agrupada de 73.675) encontró un efecto promedio sustancial, pero también heterogeneidad entre estudios y casos con efecto nulo o negativo. Por eso usar valores predeterminados apropiados —por ejemplo, una categoría frecuente o una unidad sugerida según el contexto— puede reducir esfuerzo, pero el usuario debe verlos y poder cambiarlos. No se deben preseleccionar propiedades importantes de forma oculta.

Fuente: Jachimowicz, Duncan, Weber y Johnson, «When and why defaults influence decisions: a meta-analysis of default effects», Behavioural Public Policy (2019), [DOI 10.1017/bpp.2018.43](https://doi.org/10.1017/bpp.2018.43).

### Presentación de información de producto y la intención de compra

Un estudio publicado en Journal of Retailing and Consumer Services en 2024 investigó la presentación de información de producto y la intención de compra en el contexto de digital content marketing. Encontró que ambas formas de navegación analizadas —orientada a la tarea y experiencial— predecían positivamente la intención de compra, y examinó la congruencia entre el nivel de abstracción del texto y de las imágenes. Esto no es evidencia directa de qué debe mostrar un ERP interno, pero refuerza el valor de una ficha visualmente clara y consistente cuando las imágenes se usan para reconocer un artículo.

Para Inventary, la aplicación razonable es que una miniatura tenga relación clara con el nombre/variación, y que las propiedades que distinguen un producto sean visibles sin abrir varias fichas. Validar el impacto con vendedores y no extrapolar métricas de ecommerce directamente al backoffice.

Fuente: [Enjoy to read and enjoy to shop: product information presentation and purchase intention (2024)](https://www.sciencedirect.com/science/article/pii/S0969698923003454).

## 5. Diseño propuesto para la pantalla de Productos

### 5.1 Cabecera orientada a tareas

La cabecera debe contener título, texto de propósito y una acción primaria «Nuevo producto». Acciones secundarias como importar, exportar, edición masiva o administración de categorías deben verse como secundarias y depender de permisos.

No mostrar una colección de botones con igual peso. Debe resultar evidente qué hace la mayoría de usuarios al entrar.

### 5.2 Búsqueda principal

Buscar por nombre, SKU/código, código de barras, categoría, marca si se incorpora al modelo y atributos clave. La búsqueda debe:

- responder sin requerir conocimientos del código interno;
- mostrar sugerencias con miniatura, nombre, SKU y estado;
- permitir abrir el detalle con teclado;
- distinguir claramente «no se encontraron resultados» de «ocurrió un error»;
- preservar la búsqueda y los filtros al volver desde una ficha;
- cancelar solicitudes anteriores o ignorar resultados obsoletos al escribir rápido.

No se recomienda hacer una llamada completa por cada pulsación sin debounce, control de concurrencia y paginación del lado servidor.

### 5.3 Filtros y orden

Propuesta de filtros superiores o panel lateral plegable:

- Estado: activos/inactivos/todos.
- Tipo: mercancía con stock, bien sin stock y servicio.
- Categoría y subcategoría.
- Disponibilidad por almacén (solo como consulta operativa).
- Precio de venta y moneda.
- Completitud de ficha: completa/incompleta.
- Con imágenes/sin imágenes.
- Unidad de medida.
- Atributos específicos de la categoría, cuando sean útiles.
- Fecha de actualización.

Permitir combinar filtros compatibles. Mostrar chips de filtros activos con eliminación individual y acción «Limpiar». Cada filtro debe tener un nombre comprensible, y el conteo de resultados debe actualizarse de forma consistente. Los ordenamientos base deberían incluir nombre, SKU, última actualización y precio cuando los permisos lo permitan.

No ofrecer filtros sin utilidad para el rol actual ni mostrar costo/margen a roles que no tengan autorización.

### 5.4 Tabla densa y vista de tarjetas

Ofrecer dos vistas que compartan filtros, no dos implementaciones distintas:

**Tabla para operación diaria**
- Imagen miniatura.
- Nombre y SKU.
- Categoría.
- Tipo/estado.
- Unidad.
- Precio de venta.
- Indicador de disponibilidad resumida y ubicación de consulta.
- Completitud de ficha.
- Fecha de actualización.
- Menú de acciones.

**Tarjetas para inspección visual**
- Fotografía más grande.
- Nombre, SKU, precio, estado y categoría.
- Alertas discretas de ficha incompleta o ausencia de imagen.
- Acciones claras para ver/editar.

Recordar la preferencia por usuario si aporta valor. En móvil, usar tarjetas compactas y acciones accesibles sin depender del hover.

### 5.5 Paginación real

La lista debe usar paginación del servidor para catálogos grandes. Mostrar total de resultados, tamaño de página y navegación anterior/siguiente; conservar filtros, orden y página. No descargar todo el catálogo para filtrar en el navegador.

Las acciones masivas no deben saltarse al resto de páginas seleccionadas: el modelo de selección debe dejar claro si se seleccionan las filas visibles o todos los resultados filtrados.

### 5.6 Estados visuales: skeleton, vacío, error y refresco

- **Carga inicial:** skeleton con la forma real de la lista, miniaturas, textos y controles, no un spinner suelto.
- **Refresco en segundo plano:** conservar datos visibles, mostrar que están actualizándose y evitar parpadeos.
- **Vacío del catálogo:** explicar cómo crear el primer producto y ofrecer «Nuevo producto».
- **Sin resultados con filtros:** mostrar los filtros activos y una acción para limpiarlos.
- **Error de red/permisos:** explicar el problema y permitir reintentar cuando tenga sentido.
- **Guardar producto:** bloquear envíos duplicados, conservar la información introducida al corregir un error y mostrar qué campos faltan.
- **Carga de imágenes:** progreso, estado por archivo, errores por formato/tamaño y vista previa.

### 5.7 Transiciones y microinteracciones

Usar transiciones breves y consistentes para expandir filtros, abrir detalle, cambiar de vista y completar una acción. Animar principalmente opacidad y desplazamientos pequeños; no usar efectos de rebote o motion que retrasen la operación.

Debe respetarse la preferencia del sistema por movimiento reducido. Cumplir criterios de foco visible, controles operables por teclado y objetivos táctiles apropiados. Referencia: [WCAG 2.2](https://www.w3.org/TR/wcag/).

## 6. Flujo de crear/editar producto: stepper adaptativo

No todos los productos necesitan los mismos campos. Propuesta de pasos:

### Paso 1 — Identidad del producto
- Nombre, SKU/código (generado automáticamente si aplica), categoría, tipo y descripción breve.
- Foto principal con carga sencilla y vista previa.
- Indicador de calidad de datos.
- Si se escoge un tipo de producto, explicar en lenguaje simple qué significa para compras/inventario.

### Paso 2 — Cómo se vende
- Unidad de medida, permite fracciones o no y conversiones relevantes.
- Si es mercancía con stock: unidad y datos necesarios para recibir/vender.
- Si es bien sin stock: no pedir ubicación o stock innecesario.
- Si es servicio: modo de cobro; si es suscripción, frecuencia, duración y precio base.
- Precio sugerido y margen solo con permisos adecuados.

### Paso 3 — Características
- Campos dinámicos según el esquema de atributos de la categoría.
- Obligatorios, opciones válidas, rangos y explicaciones junto al campo.
- Mostrar agrupaciones por propósito (por ejemplo: dimensiones, compatibilidad, presentación).
- No pedir JSON ni claves técnicas a la persona operadora.

### Paso 4 — Fotos, variantes y publicación
- Galería ordenable con imagen principal, alt text y preview.
- Variantes organizadas bajo un producto padre si el modelo de negocio lo necesita.
- Resumen de campos faltantes y errores accionables.
- Previsualización de cómo aparece la ficha en el picker de ventas, sin confundir el catálogo interno con un ecommerce público.

Para productos muy simples se puede reducir el número de pasos, sin ocultar que se omitieron campos opcionales. El resumen final debe mostrar solo lo relevante para ese producto.

## 7. IA útil y segura para catálogo

La IA debe funcionar como asistente con contexto, no como una caja de texto genérica ni como autoridad para alterar el catálogo.

### Casos de uso prioritarios

1. **Redacción:** generar descripción desde datos de ficha confirmados, con tono y longitud seleccionables.
2. **Normalización:** sugerir nombre, marca o unidad normalizados sin eliminar el valor original.
3. **Mapeo de categoría:** proponer una categoría y atributos compatibles, explicando el motivo y la confianza.
4. **Calidad de datos:** detectar campos que faltan, unidades contradictorias, duplicados probables o atributos que no coinciden con el tipo de categoría.
5. **Imágenes:** sugerir alt text y detectar archivos duplicados o una imagen aparentemente inadecuada para la ficha, sujeto a revisión.
6. **Operación masiva:** preparar cambios a muchas fichas y entregar un diff por producto antes de guardar.

### Reglas obligatorias de agente

- Nunca inventar datos técnicos, garantías, composición, compatibilidad, certificaciones, origen o atributos que no estén en fuentes confiables.
- Mostrar qué datos utilizó para generar cada propuesta.
- Presentar cambios como borrador con comparación anterior/nuevo.
- Requerir aprobación humana para guardar/publicar, especialmente para precios, categoría, SKU, atributos técnicos e imágenes.
- Respetar permisos: la IA no puede revelar coste o margen a quien no puede leerlos por API.
- Toda acción masiva debe tener resumen, simulación, validación server-side, auditoría y opción de detenerse antes del commit.
- No permitir que contenido de una descripción o archivo subido se interprete como instrucciones para un agente con acceso a operaciones privilegiadas.

## 8. Cambios de backend/API necesarios o a evaluar

El backend ya soporta q, categoryId, active, kind, paginación y orden, además de atributos dinámicos e imágenes. Antes de añadir endpoints, revisar si los actuales cubren el caso.

Capacidades posibles, según volumen real y permisos:

- búsqueda con coincidencia de SKU, nombre y códigos alternativos;
- filtros combinables y atributos específicos de categoría;
- endpoint de resumen de calidad de ficha;
- endpoint de sugerencias IA separado de los comandos de escritura;
- comando masivo con idempotencia, prevalidación y resultados por fila;
- búsqueda por barcode para el picker y no solo para la página de administración;
- datos de variantes separados del producto padre y reglas claras para inventario por variante, si se decide implementarlas en la UI.

Las APIs actuales de producto no deben ampliarse por estética: cada nueva capacidad debe corresponder a una tarea real, reglas de autorización, contrato documentado y tests de integración.

## 9. Métricas para decidir si el rediseño funciona

Medir antes y después con datos comparables:

- tiempo mediano y percentil 90 para encontrar un producto;
- pasos/clicks hasta abrir y editar una ficha;
- porcentaje de formularios con errores de validación;
- productos incompletos y campos que más producen fallos;
- tasa de errores al modificar precios, categoría, unidades o atributos;
- tiempo de completar cambios por lote;
- fallos o cancelaciones de carga de imagen;
- búsquedas sin resultados y consultas repetidas;
- uso de sugerencias de IA, aceptación, corrección humana y tasa de propuestas incorrectas.

No basta medir clicks o visitas: priorizar exactitud, tiempo de tarea y tasa de recuperación ante errores.

## 10. Criterios de aceptación

- La búsqueda acepta lenguaje cotidiano y códigos existentes.
- Los filtros pueden combinarse y se ven de forma transparente.
- La paginación no requiere descargar todo el catálogo.
- Hay estados de carga, vacío, error, permisos y refresco bien definidos.
- La ficha adapta los pasos al tipo de producto sin mostrar campos irrelevantes.
- Los campos dinámicos respetan el esquema de categoría y explican las reglas.
- Costo y margen nunca se exponen por la UI a roles no autorizados; el backend sigue siendo autoridad.
- Las acciones peligrosas —desactivar, eliminar, cambios masivos y cambios críticos— necesitan contexto, confirmación y registro.
- Los agentes de IA proponen cambios verificables y no guardan sin aprobación.
- Teclado, lector de pantalla, contraste y movimiento reducido se prueban.
- Tests unitarios, pruebas de API y una prueba E2E de la gestión de un producto están ligados al SHA que se entrega.

## Fuentes consultadas

- [Shopify: edición masiva de productos y variantes](https://help.shopify.com/en/manual/shopify-admin/productivity-tools/bulk-editing)
- [Shopify: edición de variantes](https://help.shopify.com/en/manual/products/variants/edit-variants)
- [Shopify Magic: generación de descripciones](https://apps.shopify.com/built-in-features/magic-product-descriptions)
- [Akeneo Product Cloud: PIM y enriquecimiento con IA](https://www.akeneo.com/akeneo-product-cloud/)\n- [Akeneo: administración de configuraciones de IA y límites](https://help.akeneo.com/en_US/serenity-boost-your-productivity/manage-your-ai-configurations)\n- [Salsify: Product Experience Management](https://www.salsify.com/product-experience-management/)
- [Plytix: PIM y funciones de IA](https://www.plytix.com/pim/)
- [Baymard Institute: benchmark UX de listas, filtros y ordenamiento (2025)](https://baymard.com/research-articles/current-state-product-list-and-filtering)
- [Hick (1952), On the Rate of Gain of Information](https://journals.sagepub.com/doi/10.1080/17470215208416600)
- [Scheibehenne, Greifeneder y Todd (2010), meta-análisis de choice overload](https://doi.org/10.1086/651235)
- [Jachimowicz et al. (2019), meta-análisis de efectos de default](https://doi.org/10.1017/bpp.2018.43)
- [Estudio de presentación de información de producto e intención de compra (Journal of Retailing and Consumer Services, 2024)](https://www.sciencedirect.com/science/article/pii/S0969698923003454)\n- [W3C: WCAG 2.2](https://www.w3.org/TR/wcag/)

La investigación orienta el diseño; la selección final de patrones debe validarse con pruebas de tareas de las personas que usan Inventary.


---

## Ampliación 2026-10-28: catálogo y recomendaciones

La propuesta transversal de algoritmos, medición, APIs y WhatsApp está en [PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md](PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md). Para preparar el catálogo, priorizar atributos estructurados y verificables por categoría, reglas de publicación claras, estado de borrador y revisión humana para contenido generado. El backend debe devolver la disponibilidad, el estado comercial y la razón de una recomendación; React no debe duplicar esas reglas. La completitud de ficha puede calcularse por campos ponderados según categoría, pero ese indicador mide calidad de datos, no probabilidad de compra.
