# Plan de implementación del frontend

La construcción del frontend se divide por fases de código fuente. Las pruebas integrales se ejecutarán después de completar todo el alcance de código fuente, según lo solicitado.

## Fase 1 — Fundación y catálogo

- Mantener React + Vite como única aplicación web.
- Centralizar las llamadas REST, autorización Bearer, errores e idempotencia en \`src/api.js\`.
- Completar el inicio de sesión y el marco común de navegación.
- Separar los formularios de productos y categorías en componentes independientes.
- Crear y editar productos y categorías mediante el backend.
- Leer el esquema de propiedades de cada categoría y renderizar los campos compatibles con el tipo de dato.
- Crear, editar y eliminar definiciones de propiedades; validar claves, opciones de listas y longitudes.
- Incluir acciones de activar/desactivar productos y categorías.
- Documentar los contratos y la configuración local.

**Criterio de cierre:** el código fuente de autenticación, catálogo y propiedades dinámicas está incorporado; no se considera validado hasta ejecutar las pruebas en la fase final.

## Fase 2 — Operación de inventario

- Completar almacenes, proveedores y demás catálogos auxiliares que requiera el flujo.
- Crear órdenes de compra y cubrir envío, aprobación y recepción de mercancía.
- Crear ventas transaccionales desde el frontend sin alterar saldos directamente.
- Consultar saldos y movimientos por almacén, producto y filtros admitidos por la API.
- Mejorar los formularios operativos para líneas múltiples, estados y errores de negocio.

## Fase 3 — Reportes, accesibilidad y consistencia de producto

- Completar el balance por fecha y almacén, mostrando compras, ventas, saldo inicial/final, valoración y margen con los nombres reales del DTO.
- Completar vistas de detalle y estados vacíos, de carga, error y permisos.
- Revisar responsive, teclado, etiquetas, confirmaciones y mensajes de error.
- Reducir el acoplamiento restante de \`App.jsx\` extrayendo páginas, tablas y operaciones a módulos por funcionalidad.

## Fase 4 — Cierre del código fuente y documentación

- Revisar la cobertura funcional de todas las pantallas y cada ruta consumida.
- Completar configuración por entorno, instrucciones de ejecución y resolución de problemas.
- Revisar el manejo de sesión, permisos, duplicidad de acciones e idempotencia.
- Congelar el código fuente para iniciar la fase posterior de pruebas.

## Fase de pruebas posterior

Se ejecutará solo después de finalizar las cuatro fases de código: compilación de producción, revisión estática, pruebas de componentes y escenarios end-to-end contra el backend y PostgreSQL, incluyendo compras, recepción, venta total, reposición del mismo producto y balance por rango de fechas.
