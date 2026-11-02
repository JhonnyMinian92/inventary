# Auditoría de la pantalla Productos

**Rama:** `implementacion_propuestas`  
**Alcance:** listado y búsqueda, detalle, alta/edición, categorías, atributos dinámicos, unidades, precios, fiscalidad, variantes e imágenes.  
**Backend relacionado:** `/api/v1/catalog/products`, `/api/v1/catalog/categories`, `/api/v1/catalog/products/{id}/images` y `/api/v1/catalog/products/{id}/variants`.

## Cambios implementados

### Listado y navegación
- Se conserva paginación real del backend, con filtros por texto, categoría, tipo de producto y estado. La búsqueda también encuentra el producto padre por SKU o código de barras de cualquiera de sus variantes.
- Se agrega filtro por tipo: inventariable, no inventariable y servicio.
- El estado de filtros, orden y página queda reflejado en la URL y se restaura con Atrás/Adelante.
- El orden por categoría ahora se ejecuta en el servidor con una ruta de ordenamiento permitida (`category.name`), en vez de ordenar solo la página visible.
- El orden por existencia y el filtro por existencia permanecen explícitamente limitados a la página cargada, porque el endpoint no implementa todavía orden/filtro global por stock.
- El CSV exporta todos los resultados que coinciden con texto, categoría, tipo y estado, recorriendo páginas del servidor; incorpora saldos agregados si el usuario tiene permiso de lectura de inventario. El filtro/orden por existencia siguen siendo de alcance página y no se aplican al conjunto exportado.
- El contador de Productos usa el total de resultados del servidor. El listado de categorías recibe `productCount` con el total de productos activos de cada categoría.

### Formulario y reglas del producto
- Se centraliza la construcción del payload de escritura para evitar que campos mostrados por el formulario se pierdan al guardar.
- Se persisten `taxCode` y `taxTreatment`, además del margen, precio manual, plan de suscripción, unidad, categoría y atributos.
- Si no hay unidad seleccionada, se envía la unidad base UUID en lugar de omitirla o enviar una cadena vacía.
- Los productos inventariables mantienen el precio derivado del costo y margen; los servicios y productos no inventariables permiten precio manual.
- Los atributos de categoría sin opciones predefinidas ahora permiten una lista separada por comas.
- Se mantiene el SKU de producto como código generado/solo lectura; el backend protege su inmutabilidad para preservar referencias históricas.

### Variantes
- El editor ahora consulta y muestra variantes del producto.
- Permite crear variantes con SKU, código de barras, opciones dinámicas y precio opcional.
- Permite editar código de barras, combinación de opciones y precio; el SKU de la variante permanece inmutable.
- Permite desactivar variantes sin borrar el historial.
- Valida SKU, código de barras, opciones y formato del precio en el cliente; el backend vuelve a validar unicidad y reglas de dominio.
- No permite crear variantes para un producto inactivo.

### Detalle e imágenes
- El listado muestra la miniatura de la imagen principal. El detalle muestra clasificación, datos fiscales, atributos, galería de imágenes, variantes, saldos por almacén y últimos movimientos cuando el permiso lo permite.
- Se respeta la regla de no subir nuevas imágenes a productos inactivos.
- La imagen principal de la galería se sincroniza con `product.imageUrl`; al retirarla, se promueve otra imagen publicada o se limpia la miniatura. Las actualizaciones ordinarias de la ficha conservan esa imagen.
- La carga continúa limitada a PNG/JPEG y 5 MB; la validación de contenido real y tamaño también ocurre en backend.

### Backend
- Se permite ordenar productos por nombre, SKU, precio y nombre de categoría; el allowlist sigue rechazando propiedades no autorizadas como `costPrice`.
- El listado de categorías devuelve `productCount` con conteo agregado por lote, evitando una consulta adicional por cada categoría.
- Se añadieron regresiones para conteo de productos por categoría, ordenamiento por categoría y persistencia de código/tratamiento fiscal al actualizar productos.
- Se conservan las reglas existentes de permisos, unicidad de SKU, protección de costos, esquema versionado de atributos, unidad activa, tipo de producto, desactivación segura y borrado físico condicionado.

## Contratos y controles verificados en código

| Área | Regla |
|---|---|
| Lectura | Los endpoints de catálogo aplican autorización de lectura configurada por el backend. |
| Escritura | Crear/editar/desactivar/eliminar productos, categorías, imágenes y variantes requiere `catalog:manage`. |
| Costos | El backend solo incluye costo/margen en las respuestas para permisos autorizados. |
| SKU | SKU de producto y variante único; el SKU del producto es inmutable en actualización. |
| Atributos | Se validan contra la versión activa del esquema de la categoría. |
| Unidades | La unidad debe existir y estar activa; no se permite cambiarla si hay movimientos. |
| Tipo | No se puede convertir un producto con stock/reservas de forma incompatible. |
| Desactivación | Se bloquea si existen existencias/reservas, órdenes de compra pendientes, transferencias pendientes o conteos abiertos. |
| Eliminación | Solo se permite para registros inactivos sin saldos, movimientos, variantes, imágenes ni referencias históricas. |
| Imágenes | PNG/JPEG, máximo 5 MB, contenido decodificado, URL pública controlada y una sola imagen principal. |
| Variantes | SKU/código de barras únicos y combinación de opciones sin duplicados activos. |

## Pruebas agregadas

- Frontend: `productWritePayload` conserva los campos fiscales, la unidad predeterminada, atributos y configuración de suscripción; los productos inventariables no envían precio manual.
- Backend `CatalogApiTest`: conteo activo por categoría, ordenamiento por `category.name`, persistencia de `taxCode`/`taxTreatment`, además de las pruebas existentes de SKU, costo, desactivación, variantes, imágenes y atributos.

## Limitaciones conocidas / trabajo pendiente

1. El filtro y el orden por existencia son de alcance **página actual**, no globales. Para hacerlos globales hace falta un endpoint de inventario que filtre/ordene el catálogo con saldos agregados en base de datos.
2. La exportación CSV completa recorre páginas y puede exportar hasta el límite defensivo de 100.000 registros; el filtro/orden por existencia de página no se aplica a esa exportación.
3. No se ha añadido importación masiva de productos por CSV/XLSX ni actualización masiva con vista previa, conflictos y resultados por fila.
4. No se han implementado edición de SKU, códigos internos alternativos ni etiquetas imprimibles; los SKU son inmutables por diseño.
5. Las imágenes adicionales se gestionan al editar un producto ya creado; no se adjuntan antes del primer guardado.
6. La galería acepta PNG/JPEG; formatos WebP/AVIF, antivirus/escaneo y CDN/almacenamiento externo deben evaluarse para producción.
7. Las reglas de variantes dependen de propiedades simples definidas en la categoría. Atributos tipo lista no forman combinaciones de variante.
8. El costo se mantiene como costo de referencia actualizado por recepción de compra; no equivale por sí solo al costo contable FIFO/promedio del inventario.
9. Falta ejecutar E2E autenticado completo de alta, edición, filtros, paginación, variantes, imágenes, roles y errores contra backend efímero; el CI automatizado no sustituye la aceptación manual de negocio.
10. El contador de categorías corresponde a productos activos, no a todas las referencias históricas inactivas.

## Criterio de cierre

La pantalla queda mejorada en el código y con pruebas de regresión específicas. No se declara cerrada al 100% hasta que Frontend CI y Backend CI del HEAD documental final estén verdes y se ejecute la matriz E2E autenticada descrita arriba.
