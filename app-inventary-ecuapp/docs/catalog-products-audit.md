# Auditoría funcional: pantalla de productos

La búsqueda, categoría, tipo y estado se resuelven en el endpoint paginado del backend. El frontend aplica localmente únicamente el filtro de existencias de la página cargada. Esto es importante porque la búsqueda del backend puede encontrar productos por el código de barras de una variante, aunque ese código no esté repetido en la proyección del producto padre.

## Corrección aplicada

- Se eliminó el segundo filtrado local por nombre/SKU/descripción, categoría y estado después de recibir la página del servidor.
- Se conserva el filtro local de existencias y se aclara en la interfaz que opera sobre la página actual.
- La ordenación por nombre, categoría y precio permanece en el servidor; la ordenación por existencias se calcula sobre la página visible.

## Cobertura backend existente

`CatalogApiTest` cubre unicidad de SKU, campos fiscales, control de costos por permiso, atributos versionados, búsqueda, paginación/ordenación, restricciones de activación/desactivación y variantes con SKU/código de barras.

## Pendientes identificados

- Ejecutar pruebas E2E autenticadas de alta, edición, filtros, paginación, imágenes y variantes desde navegador.
- Validar manualmente la accesibilidad del flujo de tres pasos y la gestión de galería en móvil.
- El filtro de existencias y su ordenación son deliberadamente de página actual; para un filtro global se necesita un contrato backend que consulte saldos agregados, no descargar todo el inventario en el navegador.
- La edición de producto mantiene el SKU inmutable para proteger referencias históricas; cambios de código deben resolverse con una variante o un flujo administrativo trazable.

## Evidencia

La corrección está en `implementacion_propuestas`, commit `b544f7cb359aadefc951aa33be032ffb6cb70e52`. El workflow Frontend Build de ese commit se lanzó y estaba en ejecución al redactar esta auditoría: https://github.com/JhonnyMinian92/inventary/actions/runs/41449330376. No se declara aprobado hasta que finalice.
