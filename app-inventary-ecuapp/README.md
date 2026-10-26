# Inventary Web — React

Aplicación web SPA de inventario construida con **React + Vite**. El frontend usa un cliente REST compartido en **src/api.js** y consume directamente el backend bajo **/api/v1**; no mantiene un inventario simulado ni modifica el saldo directamente desde el navegador.

## Requisitos

- Node.js 22.12.0 o superior dentro de la línea 22 (versión declarada en .nvmrc).
- npm 10 u 11.
- Backend levantado y accesible desde el navegador.
- Usuario activo con los permisos correspondientes en el backend.

## Ejecución local

Desde la raíz del repositorio:

```bash
cd app-inventary-ecuapp
cp .env.example .env
npm install
npm run dev
```

Configura **VITE_API_BASE_URL** con la URL base de la API, incluyendo **/api/v1**. El valor predeterminado es **http://localhost:8080/api/v1**. El backend debe permitir el origen de Vite en su configuración CORS, normalmente **http://localhost:5173**.

## Estructura relevante

- **src/App.jsx**: marco de navegación, carga de datos y coordinación de flujos.
- **src/api.js**: llamadas REST, autenticación Bearer, manejo centralizado de errores e idempotencia para operaciones transaccionales.
- **src/features/catalog/ProductForm.jsx**: crear/editar productos y renderizar campos a partir del esquema de atributos de la categoría.
- **src/features/catalog/CategoryForm.jsx**: crear/editar categorías y configurar campos tipados, obligatorios, enumerados y restricciones básicas.
- **src/features/operations/OperationsCatalogs.jsx** y **UnitConversions.jsx**: almacenes, proveedores, clientes, unidades y conversiones.
- **src/features/purchasing/PurchaseForm.jsx**, **ReceiptForm.jsx**, **PurchaseTable.jsx** y **PurchaseReturns.jsx**: órdenes multítem, recepción parcial, detalle y devoluciones a proveedores.
- **src/features/sales/SaleForm.jsx**, **SalesTable.jsx** y **SalesReturns.jsx**: venta transaccional multítem, detalle y devoluciones de clientes.
- **src/features/inventory/InventoryOperations.jsx**: traslados, ajustes, conteos físicos y reposición.
- **src/features/reports/ReportsCenter.jsx**: informes con filtros, detalle de DTO, paginación, estados de error/permisos y exportación CSV.
- **src/features/dashboard/Overview.jsx**, **src/features/catalog/CatalogTables.jsx** y **src/components/DataTable.jsx**: dashboard y tablas reutilizables.
- **src/components/TransactionDetails.jsx**: vistas de detalle de órdenes y ventas.
- **src/hooks/useAccessibleDialog.js**: diálogo accesible mediante teclado y gestión de foco.
- **src/styles.css**: sistema visual responsive y estados de foco.
- **docs/frontend-roadmap.md**: alcance y criterios de cierre por fase.
- **docs/api-coverage.md**: matriz de módulos del frontend y rutas REST del backend consumidas.
- **docs/troubleshooting.md**: configuración de entorno, errores comunes, sesión, permisos e idempotencia.

## Funciones incorporadas

- Inicio de sesión con token emitido por el backend, cierre de sesión en la API y manejo de sesión vencida.
- Catálogo de productos y categorías con propiedades derivadas del esquema de atributos (STRING, NUMBER, BOOLEAN, DATE, ENUM).
- Administración de almacenes, proveedores, clientes y unidades de medida; asignación de unidad y conversiones entre unidades.
- Órdenes de compra con múltiples líneas, envío a aprobación, recepción parcial y devoluciones a proveedores.
- Ventas multítem con clientes registrados u ocasionales, solicitud/aprobación de anulaciones y devoluciones de clientes (reincorporación o descarte).
- Consulta de disponibilidad en tiempo real antes de vender y consulta de saldos/movimientos por almacén y producto.
- Traslados con despacho/recepción, ajustes con motivo, conteos físicos y reglas/sugerencias de reposición.
- Centro de informes: balance histórico, ventas netas y margen, compras y devoluciones, kardex, valoración y stock bajo.
- Exportación CSV y paginación en informes que exponen múltiples páginas.
- Detalle de órdenes y ventas obtenido directamente desde el backend.
- Manejo explícito de permisos financieros, carga, errores, resultados vacíos y sesiones vencidas.
- Diálogos accesibles por teclado, foco administrado, confirmaciones y estilos responsive.

Una orden de compra por sí sola no aumenta las existencias: el stock cambia al registrar la recepción aprobada mediante la API. De forma equivalente, las ventas y devoluciones se procesan transaccionalmente en el backend.

## Fases y pruebas

Las Fases 1, 2, 3 y 4 tienen el código fuente y su documentación incorporados en `master`. La Fase 4 cubre el mapa de rutas, configuración por entorno, instrucciones para resolver errores, permisos y manejo de idempotencia. Queda pendiente la **fase de validación**: generar el archivo de bloqueo de dependencias con acceso al registro de npm, compilar el frontend, revisar errores estáticos y ejecutar pruebas de componentes y escenarios end-to-end contra el backend y PostgreSQL. Conforme a la secuencia acordada, todavía no he ejecutado compilación ni pruebas.

## Build de producción (para la fase de pruebas)

Al abrir la fase de validación, desde app-inventary-ecuapp ejecutar:

```bash
npm install
npm run build
npm run preview
```

El proyecto fija las versiones directas en package.json. El primer npm install debe generar package-lock.json; guarda ese archivo en el repositorio y usa npm ci en las ejecuciones siguientes.

El contrato del backend y su OpenAPI siguen siendo la fuente de verdad para los DTOs y permisos. Los errores de validación y autorización deben solucionarse en coordinación con ese contrato; no se deben reemplazar por respuestas simuladas.
