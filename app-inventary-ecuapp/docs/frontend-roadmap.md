# Plan de implementación del frontend

La construcción del frontend se divide por fases de código fuente. Las pruebas integrales se ejecutarán después de completar todo el alcance de código fuente, según lo solicitado.

## Fase 1 — Fundación y catálogo

**Estado del código fuente: completada en `master`.** La compilación y las pruebas quedan pendientes deliberadamente hasta que se complete el 100 % del frontend.

- Mantener React + Vite como única aplicación web.
- Centralizar las llamadas REST, autorización Bearer, errores e idempotencia en **src/api.js**.
- Completar el inicio de sesión y el marco común de navegación.
- Separar los formularios de productos y categorías en componentes independientes.
- Crear y editar productos y categorías mediante el backend.
- Leer el esquema de propiedades de cada categoría y renderizar los campos compatibles con el tipo de dato.
- Crear, editar y eliminar definiciones de propiedades; validar claves, opciones de listas y longitudes.
- Incluir acciones de activar/desactivar productos y categorías.
- Documentar los contratos y la configuración local.

**Criterio de cierre:** el código fuente de autenticación, catálogo y propiedades dinámicas está incorporado; no se considera validado hasta ejecutar las pruebas en la fase final.

## Fase 2 — Operación de inventario

**Estado del código fuente: completada en `master`.** Las pruebas de compilación y de negocio siguen aplazadas hasta el cierre de todo el frontend.

- Administrar almacenes, proveedores, clientes y unidades de medida; asignar unidades a productos y configurar conversiones.
- Crear órdenes de compra con múltiples productos; enviar a aprobación, cancelar borradores/órdenes pendientes y registrar recepción parcial o completa por línea.
- Registrar devoluciones a proveedores desde recepciones contabilizadas, limitando cantidades a las que aún no se han devuelto.
- Registrar ventas multítem con clientes registrados u ocasionales, consultar disponibilidad y solicitar/aprobar anulaciones.
- Registrar devoluciones de clientes por línea de venta, eligiendo entre reincorporar el artículo al inventario o descartarlo.
- Consultar saldos y movimientos filtrando por almacén y producto, mostrando los campos reales de los DTOs.
- Gestionar traslados multítem con despacho, recepción, cancelación y reversión con motivo.
- Registrar ajustes de stock con dirección, cantidad, motivo obligatorio y costo unitario para entradas.
- Registrar conteos físicos multítem y contabilizar o cancelar los documentos; crear/editar/desactivar reglas de reposición y consultar sugerencias.
- Centralizar las nuevas rutas bajo el cliente REST, usando claves de idempotencia para operaciones transaccionales que modifican inventario.

## Fase 3 — Reportes, accesibilidad y consistencia de producto

**Estado del código fuente: completada en `master`.** No se han ejecutado compilación ni pruebas, conforme a la secuencia solicitada.

- Centro de informes conectado a los endpoints reales: balance de inventario, ventas/margen, compras y devoluciones, kardex, valoración y stock bajo.
- Balance por fechas y almacén con saldo inicial/final, compras y ventas netas, valoración inicial/final, costo de ventas, utilidad y margen bruto, usando los nombres reales de `InventoryBalanceReportRow`.
- Paginación donde el DTO expone páginas y exportación CSV de los resultados.
- Estados de carga, vacío, error y falta de permiso `reports:financial`; los fallos no se sustituyen por datos ficticios ni mantienen visibles resultados financieros obsoletos.
- Vistas de detalle de órdenes y ventas que consultan el documento completo en el backend.
- Diálogos con navegación por teclado, Escape, foco atrapado/restaurado, etiquetas accesibles, anuncios de error/notificación y confirmaciones para operaciones sensibles.
- Ajustes responsive y foco visible, incluido el centro de informes, tablas extensas y diálogos en pantallas pequeñas.
- Refactor de `App.jsx`: dashboard, catálogo de tablas, tablas de compras/ventas, tabla compartida, centro de informes, detalles transaccionales y accesibilidad movidos a módulos por funcionalidad.

## Fase 4 — Cierre del código fuente y documentación

- Revisar la cobertura funcional de todas las pantallas y cada ruta consumida.
- Completar configuración por entorno, instrucciones de ejecución y resolución de problemas.
- Revisar el manejo de sesión, permisos, duplicidad de acciones e idempotencia.
- Congelar el código fuente para iniciar la fase posterior de pruebas.

## Fase de pruebas posterior

Se ejecutará solo después de finalizar las cuatro fases de código: compilación de producción, revisión estática, pruebas de componentes y escenarios end-to-end contra el backend y PostgreSQL, incluyendo compras, recepción, venta total, reposición del mismo producto y balance por rango de fechas.
