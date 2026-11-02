# Estado y roadmap del frontend Inventary

**Código auditado:** revisión de login y sesión en `implementacion_propuestas`; Frontend CI aprobado y Backend CI aprobado. Evidencia: [auditoría de login y sesión](login-session-audit.md).

Este documento sustituye el calendario histórico de implementación por una descripción del código actual y de las brechas que siguen abiertas.

## Estado actual por área

| Área | Estado del código |
|---|---|
| Fundación, login y sesión | Flujo cifrado, perfil/permisos, expiración y logout; fallback de Storage y feedback accesible corregidos. Frontend CI y Backend CI aprobados, incluido responsive smoke y arranque Compose. Pendientes: E2E autenticado completo, recuperación autoservicio y MFA. |
| Catálogo | Productos: filtros por tipo/estado/categoría, paginación, orden por categoría, detalle enriquecido, atributos dinámicos, datos fiscales, unidades, galería y CRUD de variantes. Categorías muestran conteo activo de productos. Stock filter/sort y CSV siguen acotados a página; faltan importación masiva y E2E autenticado. Detalle: [auditoría de Productos](catalog-products-audit.md). |
| Contactos y datos fiscales | Clientes/proveedores/almacenes y registro tributario manual están en UI. La importación sigue en revisión manual; la gestión de tarifas tiene una API con excepción de autorización pública. |
| Inventario | Centro de mando con KPIs/alertas/acciones; pestañas de existencias, movimientos, operaciones y auditoría; consumos de endpoint command-center. |
| Compras | Pestañas Órdenes, Recepciones y Devoluciones a proveedores. Órdenes, recepciones y devoluciones usan búsqueda/paginación desde servidor. |
| Ventas | Pestañas Ventas, Devoluciones y Reembolsos; acciones de cancelación y aprobación. El filtrado local de ventas no debe confundirse con búsqueda server-side completa. |
| Suscripciones | Paginación y filtros de estado, texto, almacén, ventana de próximo cobro y fallos de facturación. |
| WhatsApp | Bandeja con colas, query, resumen de trabajo, detalle expandible y confirmación manual de pago. |
| Reportes | Centro de reportes operativo/financiero con filtros, páginas y CSV. |

## Mejoras de interfaz recientes ya presentes

- Compras se organiza en tres pestañas, con estados, búsquedas y paginación separadas. La elegibilidad de devolución se obtiene del endpoint del backend, no se reconstruye a partir de un conjunto limitado de filas.
- Ventas separa las tablas de ventas, devoluciones y reembolsos. La acción de completar reembolso utiliza un diálogo y evita envíos simultáneos, pero no resuelve los defectos de seguridad/finanzas del backend.
- Inventario tiene una vista de mando conectada a agregados del servidor, además de vistas de saldos, movimientos, operaciones y auditoría.
- Suscripciones expone filtros y detalle operativo; las acciones reflejan facturación interna, no cobro bancario.
- WhatsApp incorpora indicadores de trabajo, paginación y detalle de pedido; la confirmación sigue siendo una revisión manual por un usuario autorizado.
- El dashboard incorpora una escena operativa que mantiene una conexión SSE. El evento recibido funciona como señal para volver a consultar la API; no transporta un snapshot completo de negocio.

## Arquitectura

La composición está en src/app; las rutas delegan a src/pages; los módulos de negocio viven en src/features; el transporte y APIs por dominio están en src/shared/api. Las fachadas src/api.js y src/router.js mantienen compatibilidad.

Consulta [Arquitectura implementada](../src/README_ARCHITECTURE.md).

## Evidencia de calidad

En el SHA auditado, Frontend Build 41385132616 aprobó:
- 26 pruebas Node Test Runner.
- Build Vite de producción.
- Preview del build.
- Smoke browser de login en Chromium a 360, 768 y 1280 px.

Esto no equivale a E2E autenticado del producto. Siguen pendientes pruebas browser para roles, permisos, compra/recepción/devolución, venta/cancelación, inventario/conteo/traslado, suscripciones, WhatsApp, reportes y errores/expiración de sesión.

No existe script lint en package.json. No presentar la suite actual como cobertura completa de UI.

## Trabajo frontend pendiente

0. Cerrar la validación del login según [la matriz de aceptación y brechas de sesión](login-session-audit.md): CI final, E2E autenticado, accesibilidad, recuperación de contraseña y evaluación de MFA.

1. Automatizar E2E autenticado con datos efímeros y distintos roles.
2. Cubrir los recorridos comerciales y los casos de error/resultado incierto.
3. Completar pruebas de accesibilidad y navegación con teclado de formularios, modales y tablas.
4. Extender filtros server-side donde aún se aplican solo sobre la página recibida.
5. Revisar el flujo de reembolso solo después de corregir la API y su autorización.
6. Diferenciar visualmente estados comercial, fiscal, pago y cumplimiento; no inferir un cobro a partir de una venta o un pedido.
7. Mantener el mapa API sincronizado con cada endpoint nuevo o retirado.

La implementación actual es una SPA operativa con módulos por dominio, no una aceptación completa del producto ni prueba de aptitud productiva.