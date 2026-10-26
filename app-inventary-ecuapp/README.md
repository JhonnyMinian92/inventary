# Inventary Web — React

Aplicación web SPA de inventario construida con **React + Vite**. El frontend usa un cliente REST compartido en **src/api.js** y consume directamente el backend bajo **/api/v1**; no mantiene un inventario simulado ni modifica el saldo directamente desde el navegador.

## Requisitos

- Node.js 20 o superior.
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
- **src/features/operations/OperationsCatalogs.jsx**: administración de almacenes, proveedores y clientes.
- **src/features/purchasing/PurchaseForm.jsx** y **ReceiptForm.jsx**: órdenes y recepciones de múltiples líneas.
- **src/features/sales/SaleForm.jsx**: venta transaccional con múltiples artículos.
- **src/features/inventory/InventoryOperations.jsx**: traslados, ajustes, conteos físicos y reposición.
- **src/styles.css**: sistema visual responsive.
- **docs/frontend-roadmap.md**: alcance y criterios de cierre por fase.

## Funciones incorporadas

- Inicio de sesión con token emitido por el backend, cierre de sesión en la API y manejo de sesión vencida.
- Catálogo de productos y categorías con propiedades derivadas del esquema de atributos (STRING, NUMBER, BOOLEAN, DATE, ENUM).
- Administración de almacenes, proveedores y clientes.
- Órdenes de compra con múltiples líneas, flujo de aprobación y recepción por artículo.
- Ventas con múltiples artículos, clientes registrados y solicitud/aprobación de anulaciones.
- Consulta de saldos y movimientos por almacén y producto.
- Traslados con despacho y recepción, ajustes de stock con motivo, conteos físicos y reglas/sugerencias de reposición.
- Balance de inventario por almacén y rango de fechas.

Una orden de compra por sí sola no aumenta las existencias: el stock cambia al registrar la recepción aprobada, mediante la API. De forma equivalente, las ventas se registran como operaciones transaccionales del backend.

## Fases y pruebas

El trabajo está separado en fases descritas en [docs/frontend-roadmap.md](docs/frontend-roadmap.md). Las Fases 1 y 2 cubren la fundación, el catálogo, las operaciones auxiliares y los flujos principales de inventario. De acuerdo con el plan solicitado, la compilación, las pruebas de componentes y la verificación end-to-end se ejecutarán después de completar el 100 % del código fuente.

## Build de producción (para la fase de pruebas)

Cuando se abra la fase de pruebas, los comandos serán:

```bash
npm run build
npm run preview
```

El contrato del backend y su OpenAPI siguen siendo la fuente de verdad para los DTOs y permisos. Los errores de validación y autorización deben solucionarse en coordinación con ese contrato; no se deben reemplazar por respuestas simuladas.
