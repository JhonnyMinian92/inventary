# Inventary Web — React

Aplicación web SPA de inventario construida con **React + Vite**. Consume el backend mediante el cliente REST compartido de `src/api.js`; el navegador no modifica directamente los saldos de inventario.

## Requisitos

- Node.js 22.12.x (línea 22).
- npm 10 u 11.
- Backend levantado y accesible desde el navegador.
- Usuario activo con los permisos correspondientes en el backend.

## Ejecución local

Desde este directorio:

```bash
cp .env.example .env
npm ci
npm run dev
```

Configura `VITE_API_BASE_URL` con la URL base de la API, incluyendo `/api/v1`. El valor predeterminado es `http://localhost:8080/api/v1`. El backend debe permitir el origen de Vite en su configuración CORS, normalmente `http://localhost:5173`.

## Estructura relevante actual

- `src/main.jsx`: punto de entrada React.
- `src/App.jsx`: marco de navegación y coordinación de la aplicación.
- `src/router.js`: resolución de rutas y navegación.
- `src/api.js`: cliente REST, autenticación, errores e idempotencia.
- `src/features/dashboard/Dashboard.jsx`: dashboard.
- `src/features/catalog/ProductsPage.jsx`: pantalla de productos.
- `src/features/catalog/ProductForm.jsx` y `CategoryForm.jsx`: formularios de catálogo.
- `src/features/operations/OperationsCatalogs.jsx` y `UnitConversions.jsx`: catálogos operativos.
- `src/features/purchasing/`: compras, recepciones y devoluciones a proveedores.
- `src/features/sales/`: ventas y devoluciones de clientes.
- `src/features/inventory/`: traslados, ajustes, conteos y reposición.
- `src/features/reports/ReportsCenter.jsx`: informes y exportación CSV.
- `src/components/`: componentes reutilizables; algunos conocen conceptos de negocio y no son primitivas UI genéricas.
- `src/hooks/`, `src/security/`, `src/ui/` y `src/utils/`: hooks, seguridad, componentes UI y utilidades.
- `docs/frontend-roadmap.md`: fases y criterios de cierre.
- `docs/api-coverage.md`: matriz de consumo de API.
- `docs/troubleshooting.md`: configuración y resolución de errores.

La estructura anterior describe archivos que existen en el árbol de referencia; no se deben inferir componentes a partir de documentación histórica.

## Funciones principales

- Autenticación y gestión de sesión.
- Catálogo de productos y categorías con atributos dinámicos.
- Almacenes, proveedores, clientes y unidades de medida.
- Compras multítem, aprobación, recepción parcial y devoluciones a proveedores.
- Ventas multítem, aprobación de anulaciones y devoluciones de clientes.
- Disponibilidad, saldos, movimientos, ajustes, conteos y traslados.
- Centro de informes, paginación y exportación CSV.
- Control de permisos, estados de carga/error y diálogos accesibles.

Una orden de compra no aumenta las existencias por sí sola: el stock cambia al registrar su recepción aprobada mediante la API. Las ventas y devoluciones también se procesan transaccionalmente en el backend.

## Pruebas y build

```bash
npm ci
npm test
npm run build
npm run preview
```

El workflow de frontend ejecuta instalación reproducible con `npm ci`, pruebas y build de producción. Comprueba el resultado de GitHub Actions para el SHA que deseas desplegar; no asumas que un commit está validado hasta que termine su workflow.

## Arquitectura objetivo

La propuesta de reorganización feature-first está documentada en [PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md](docs/PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md). Es una arquitectura objetivo: los movimientos deben realizarse gradualmente, conservando rutas, contratos API, permisos y comportamiento existente, con pruebas entre etapas.

El contrato del backend y su OpenAPI siguen siendo la fuente de verdad para DTOs y permisos. No reemplazar respuestas reales por mocks para ocultar errores de integración.
