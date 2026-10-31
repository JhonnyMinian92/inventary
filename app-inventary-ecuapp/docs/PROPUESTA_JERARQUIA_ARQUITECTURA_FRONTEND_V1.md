# Propuesta de jerarquía y arquitectura del frontend Inventary

**Repositorio:** JhonnyMinian92/inventary  
**Aplicación:** app-inventary-ecuapp  
**Rama de referencia:** master  
**Stack actual:** React 19, Vite 8, JavaScript/JSX, Node 22  
**Estado:** propuesta de arquitectura; no aplicada al código  
**Fecha:** 2026-10-30

---

## 1. Objetivo

Definir una jerarquía del frontend clara, consistente y escalable, en la que cada archivo tenga una responsabilidad identificable y los componentes estén separados según su nivel de reutilización y conocimiento del negocio.

La propuesta busca:

- Separar el arranque de la aplicación, el enrutamiento, los layouts y las páginas.
- Evitar que App.jsx concentre navegación, sesión, carga global de datos, reglas y operaciones de todos los dominios.
- Separar componentes de presentación de la lógica de aplicación y del acceso a API.
- Organizar los módulos por capacidades funcionales: catálogo, inventario, compras, ventas, reportes, almacenes/contactos y demás áreas.
- Reutilizar componentes visuales sin convertir la carpeta compartida en un lugar para cualquier componente.
- Mantener accesibilidad, diseño responsive y consistencia visual.
- Facilitar pruebas unitarias, de componentes, de integración y de navegación.
- Conservar los contratos y comportamientos actuales del backend.

Esta propuesta es una arquitectura objetivo. No cambia React, Vite, JavaScript, las rutas públicas ni las funcionalidades existentes. Tampoco introduce nuevas dependencias obligatorias.

## 2. Diagnóstico del frontend actual

El frontend es una SPA construida con React y Vite. Consume directamente el backend bajo /api/v1 mediante un cliente REST compartido. La base de despliegue predeterminada de Vite es /inventary/.

### 2.1 Stack y dependencias observadas

El package.json actual declara:

- React y React DOM 19.3.1.
- Vite 8.3.5 y plugin React 6.1.2.
- lucide-react 1.61.0.
- Node.js >=22.12.0 <23 y npm >=10 <12.
- npm run build para generar producción.
- node --test para ejecutar las pruebas automatizadas existentes.

No se declara actualmente una librería de componentes, un framework de estado global, un cliente de caché de consultas ni una librería externa de routing. La aplicación usa React, hooks y un enrutador propio.

La propuesta conserva ese stack. Incorporar React Router, Redux, Zustand, TanStack Query, Tailwind u otra tecnología debe ser una decisión independiente y justificada, no una consecuencia automática de reorganizar carpetas.

### 2.2 Estructura actual resumida

El frontend actualmente tiene esta estructura:

~~~text
app-inventary-ecuapp/
├── package.json
├── package-lock.json
├── vite.config.js
├── index.html
├── Dockerfile
├── nginx.conf
├── nginx-security-headers.conf
├── .env.example
├── .nvmrc
├── README.md
├── docs/
│   ├── api-coverage.md
│   ├── context.md
│   ├── frontend-roadmap.md
│   └── troubleshooting.md
└── src/
    ├── App.jsx
    ├── main.jsx
    ├── api.js
    ├── router.js
    ├── router.test.js
    ├── assets/
    ├── components/
    │   ├── BrandLogo.jsx
    │   ├── DataTable.jsx
    │   ├── ProductPicker.jsx
    │   ├── StepProgress.jsx
    │   ├── TransactionDetails.jsx
    │   └── charts/
    ├── features/
    │   ├── auth/
    │   ├── catalog/
    │   ├── dashboard/
    │   ├── inventory/
    │   ├── operations/
    │   ├── purchasing/
    │   ├── reports/
    │   ├── sales/
    │   ├── subscriptions/
    │   └── whatsapp/
    ├── hooks/
    ├── security/
    ├── ui/
    ├── utils/
    └── styles.css
~~~

Ya existe una primera separación por features, lo cual debe conservarse y profundizarse. El problema no es que falten carpetas por crear: es que varios módulos todavía concentran en uno o pocos archivos demasiadas responsabilidades, y las fronteras entre página, formulario, estado, API y componentes reutilizables no son uniformes.

### 2.3 Hallazgos principales

**A. App.jsx está sobrecargado**

App.jsx concentra, entre otras tareas:

- Estado de sesión, usuario y permisos.
- Carga inicial de productos, categorías, unidades, almacenes, clientes, proveedores, saldos, movimientos, compras, ventas, valoración y pedidos pendientes de WhatsApp.
- Navegación lateral y títulos de secciones.
- Resolución de rutas, workflows y detalles.
- Apertura y cierre de formularios y detalles.
- Acciones de negocio para productos, categorías, compras, recepciones y ventas.
- Confirmaciones, mensajes, actualización de datos y cierre de sesión.
- Selección del contenido que se renderiza en cada sección.

Esto convierte a App.jsx en un coordinador de demasiadas responsabilidades y hace que una modificación de un área pueda afectar a otras que no están relacionadas.

**B. Módulos con componentes extensos**

Se observan archivos de considerable tamaño y responsabilidades combinadas, por ejemplo:

- ProductForm.jsx: formulario de producto, pasos, lógica de unidades de alta rápida, categorías y gestión de imágenes.
- ReportsCenter.jsx: pestañas, filtros, llamadas, paginación, cálculos, tablas y exportación CSV.
- InventoryOperations.jsx: traslados, conteos, ajustes, reposición, formularios y coordinación de workflows.
- OperationsCatalogs.jsx: almacenes, proveedores, clientes y unidades con configuración dinámica, listado y formularios.
- Dashboard.jsx: indicadores, consultas adicionales, cálculos, gráficos, pendientes y movimientos.
- SaleForm.jsx y PurchaseForm.jsx: formularios multítem, estado, validaciones, cálculos y envío.

No se propone fragmentar cada archivo en decenas de componentes diminutos. La separación debe responder a responsabilidades funcionales y facilitar pruebas y mantenimiento.

**C. Componentes compartidos con distintos niveles de reutilización**

Algunos componentes son genéricos (DataTable, StepProgress, Drawer, Skeleton, FeedbackHost); otros tienen un conocimiento específico del negocio (ProductPicker, TransactionDetails). Es importante que un selector de productos no termine en el mismo nivel arquitectónico que un botón genérico, aunque ambos puedan reutilizarse.

**D. Cliente API centralizado**

api.js contiene la infraestructura HTTP, manejo de token, errores, idempotencia, funciones de endpoints por dominio, paginación y utilidades de resolución de URL. Es valioso mantener un núcleo HTTP único, pero conviene separar ese núcleo de los endpoints de cada dominio.

**E. Estado y carga de datos**

El arranque carga muchos conjuntos de datos aunque la ruta activa no necesariamente los necesite todos. Cada feature debería ser propietaria de las consultas y mutaciones que utiliza. Los datos realmente compartidos se pueden coordinar mediante contratos/hooks concretos, sin convertir todo el estado remoto en estado global.

**F. Estilos**

Existe una hoja global styles.css con buena parte del sistema visual y hojas específicas por algunos módulos, como catálogo, dashboard y login. Se necesita separar tokens y estilos globales de estilos por componente o funcionalidad, reduciendo colisiones y dependencias implícitas.

## 3. Arquitectura objetivo

Se recomienda una arquitectura **feature-first por dominios funcionales**, inspirada en Feature-Sliced Design, con seis niveles:

1. app: arranque, proveedores, configuración de aplicación, layouts y routing.
2. pages: composición de las páginas que corresponden a rutas.
3. widgets: secciones visuales completas que componen páginas.
4. features: acciones y flujos de usuario que producen un resultado de negocio.
5. entities: modelos de negocio del frontend y componentes visuales propios de cada entidad.
6. shared: infraestructura y componentes verdaderamente reutilizables y neutrales al negocio.

No se deben crear capas por moda. Si una carpeta solo contiene un archivo y no aporta separación, se puede omitir hasta que sea necesaria.

### 3.1 Qué pertenece a cada nivel

| Nivel | Contiene | No debe contener |
|---|---|---|
| app | Bootstrap, providers, rutas, layouts, navegación y configuración | Lógica de una operación de venta o compra |
| pages | Composición de una ruta y obtención de parámetros de ruta | Componentes visuales genéricos o detalles de bajo nivel HTTP |
| widgets | Paneles y secciones compuestas: tabla con filtros, KPIs, detalle o workspace | Reglas centrales de negocio o primitivas genéricas |
| features | Acciones y workflows: crear venta, recibir compra, ajustar stock, crear producto | Shell completo ni controles visuales neutrales |
| entities | Representación de producto, cliente, almacén, compra, venta y stock | Orquestación de páginas completas |
| shared | API HTTP común, UI base, utilidades, hooks neutrales, assets y tokens | DTOs, formularios o reglas exclusivas de un dominio |

### 3.2 Regla de dependencia

La dirección de dependencias recomendada es:

~~~text
app
 |
 v
pages
 |
 v
widgets
 |
 v
features
 |
 v
entities
 |
 v
shared
~~~

Esta pirámide es una guía de dependencia, no una obligación de que todos los módulos importen al nivel inferior. La regla importante es que los niveles inferiores no importen páginas, layouts ni la aplicación.

Ejemplos:

- shared/ui/Button no puede importar features/sales.
- entities/product no puede importar pages/catalog.
- features/create-sale puede usar entities/product y shared/ui.
- widgets/sales-workspace puede componer features y entities.
- pages/sales puede componer widgets.
- app/router puede referenciar pages para asociarlas a rutas.

Las dependencias entre features de distintos dominios deben evitarse. Cuando un flujo necesite datos de otra entidad, debe consumir una interfaz o un hook público definido para ese propósito, o delegar la composición en el nivel superior.

## 4. Jerarquía de directorios propuesta

Esta es la estructura objetivo de src. Es una guía de organización, no una instrucción para crear todos los directorios vacíos ni para mover cada archivo de inmediato.

~~~text
src/
│
├── main.jsx
│
├── app/
│   ├── App.jsx
│   ├── bootstrap/
│   │   └── createApp.jsx
│   ├── providers/
│   │   ├── AppProviders.jsx
│   │   ├── AuthProvider.jsx
│   │   └── FeedbackProvider.jsx
│   ├── router/
│   │   ├── index.js
│   │   ├── routes.js
│   │   ├── routeMatcher.js
│   │   ├── navigation.js
│   │   ├── routeGuards.js
│   │   └── legacyRoutes.js
│   ├── layouts/
│   │   ├── AuthLayout.jsx
│   │   ├── AppLayout.jsx
│   │   ├── Sidebar/
│   │   │   ├── Sidebar.jsx
│   │   │   ├── NavigationGroup.jsx
│   │   │   └── NavigationItem.jsx
│   │   └── Topbar/
│   │       ├── Topbar.jsx
│   │       ├── PageHeading.jsx
│   │       └── UserMenu.jsx
│   ├── navigation/
│   │   ├── navigationConfig.js
│   │   └── navigationPermissions.js
│   └── config/
│       └── appConfig.js
│
├── pages/
│   ├── auth/
│   │   └── LoginPage.jsx
│   ├── dashboard/
│   │   └── DashboardPage.jsx
│   ├── catalog/
│   │   ├── ProductsPage.jsx
│   │   ├── CategoriesPage.jsx
│   │   ├── ProductCreatePage.jsx
│   │   ├── ProductEditPage.jsx
│   │   ├── CategoryCreatePage.jsx
│   │   └── CategoryEditPage.jsx
│   ├── inventory/
│   │   ├── InventoryPage.jsx
│   │   ├── TransferCreatePage.jsx
│   │   ├── InventoryCountCreatePage.jsx
│   │   ├── AdjustmentCreatePage.jsx
│   │   └── ReorderRuleCreatePage.jsx
│   ├── purchasing/
│   │   ├── PurchaseOrdersPage.jsx
│   │   ├── PurchaseCreatePage.jsx
│   │   ├── PurchaseDetailPage.jsx
│   │   ├── ReceiptCreatePage.jsx
│   │   └── PurchaseReturnCreatePage.jsx
│   ├── sales/
│   │   ├── SalesPage.jsx
│   │   ├── SaleCreatePage.jsx
│   │   ├── SaleDetailPage.jsx
│   │   └── SalesReturnCreatePage.jsx
│   ├── partners/
│   │   └── PartnersPage.jsx
│   ├── reports/
│   │   └── ReportsPage.jsx
│   ├── subscriptions/
│   │   ├── SubscriptionsPage.jsx
│   │   └── SubscriptionDetailPage.jsx
│   ├── whatsapp/
│   │   ├── WhatsAppOrdersPage.jsx
│   │   └── WhatsAppOrderDetailPage.jsx
│   └── errors/
│       ├── NotFoundPage.jsx
│       └── ForbiddenPage.jsx
│
├── widgets/
│   ├── dashboard/
│   │   ├── DashboardOverview.jsx
│   │   ├── SalesTrendWidget.jsx
│   │   ├── PendingTasksWidget.jsx
│   │   ├── InventorySummaryWidget.jsx
│   │   └── RecentMovementsWidget.jsx
│   ├── catalog/
│   │   ├── ProductListWidget.jsx
│   │   ├── CategoryListWidget.jsx
│   │   └── ProductDetailWidget.jsx
│   ├── inventory/
│   │   ├── InventoryBalancesWidget.jsx
│   │   ├── InventoryMovementsWidget.jsx
│   │   └── InventoryOperationsWidget.jsx
│   ├── purchasing/
│   │   ├── PurchaseOrdersWidget.jsx
│   │   ├── PurchaseOrderDetailWidget.jsx
│   │   └── PurchaseReturnsWidget.jsx
│   ├── sales/
│   │   ├── SalesListWidget.jsx
│   │   ├── SaleDetailWidget.jsx
│   │   └── SalesReturnsWidget.jsx
│   ├── partners/
│   │   └── OperationsCatalogsWidget.jsx
│   ├── reports/
│   │   └── ReportsWorkspace.jsx
│   └── whatsapp/
│       └── WhatsAppOrdersWorkspace.jsx
│
├── features/
│   ├── auth/
│   │   ├── sign-in/
│   │   ├── sign-out/
│   │   ├── session-expiration/
│   │   └── current-user/
│   ├── catalog/
│   │   ├── create-product/
│   │   ├── edit-product/
│   │   ├── change-product-status/
│   │   ├── delete-product/
│   │   ├── create-category/
│   │   ├── edit-category/
│   │   ├── manage-category-attributes/
│   │   └── manage-product-images/
│   ├── inventory/
│   │   ├── filter-inventory/
│   │   ├── create-adjustment/
│   │   ├── create-transfer/
│   │   ├── dispatch-transfer/
│   │   ├── receive-transfer/
│   │   ├── create-inventory-count/
│   │   ├── post-inventory-count/
│   │   └── manage-reorder-rule/
│   ├── purchasing/
│   │   ├── create-purchase-order/
│   │   ├── submit-purchase-order/
│   │   ├── approve-purchase-order/
│   │   ├── cancel-purchase-order/
│   │   ├── receive-purchase-order/
│   │   └── create-purchase-return/
│   ├── sales/
│   │   ├── create-sale/
│   │   ├── request-sale-cancellation/
│   │   ├── approve-sale-cancellation/
│   │   └── create-sales-return/
│   ├── partners/
│   │   ├── manage-warehouse/
│   │   ├── manage-supplier/
│   │   ├── manage-customer/
│   │   ├── manage-unit/
│   │   └── manage-unit-conversion/
│   ├── reports/
│   │   ├── query-inventory-balance/
│   │   ├── query-sales-report/
│   │   ├── query-purchase-report/
│   │   ├── query-kardex/
│   │   ├── query-valuation/
│   │   └── query-low-stock/
│   ├── subscriptions/
│   │   ├── create-subscription/
│   │   ├── cancel-subscription/
│   │   └── prepay-subscription/
│   └── whatsapp/
│       ├── query-whatsapp-orders/
│       └── confirm-whatsapp-payment/
│
├── entities/
│   ├── product/
│   │   ├── model/
│   │   ├── ui/
│   │   └── lib/
│   ├── category/
│   │   ├── model/
│   │   └── ui/
│   ├── warehouse/
│   │   ├── model/
│   │   └── ui/
│   ├── supplier/
│   │   ├── model/
│   │   └── ui/
│   ├── customer/
│   │   ├── model/
│   │   └── ui/
│   ├── purchase/
│   │   ├── model/
│   │   └── ui/
│   ├── sale/
│   │   ├── model/
│   │   └── ui/
│   └── inventory/
│       ├── model/
│       └── ui/
│
└── shared/
    ├── api/
    │   ├── http/
    │   │   ├── httpClient.js
    │   │   ├── ApiError.js
    │   │   ├── request.js
    │   │   └── idempotency.js
    │   ├── pagination/
    │   │   └── pagination.js
    │   └── url/
    │       └── resolveApiUrl.js
    ├── ui/
    │   ├── primitives/
    │   │   ├── Button/
    │   │   ├── IconButton/
    │   │   ├── Input/
    │   │   ├── Select/
    │   │   ├── Checkbox/
    │   │   ├── Textarea/
    │   │   ├── Badge/
    │   │   └── Spinner/
    │   ├── forms/
    │   │   ├── FormField/
    │   │   ├── FormSection/
    │   │   ├── FormActions/
    │   │   └── FieldError/
    │   ├── data-display/
    │   │   ├── DataTable/
    │   │   ├── Pagination/
    │   │   ├── StatusBadge/
    │   │   └── MetricCard/
    │   ├── feedback/
    │   │   ├── Toast/
    │   │   ├── FeedbackHost/
    │   │   ├── ConfirmDialog/
    │   │   ├── EmptyState/
    │   │   ├── ErrorState/
    │   │   └── LoadingState/
    │   ├── overlays/
    │   │   ├── Dialog/
    │   │   ├── Drawer/
    │   │   └── Popover/
    │   └── navigation/
    │       └── Stepper/
    ├── hooks/
    │   ├── useDebounce.js
    │   ├── useMediaQuery.js
    │   └── useEventListener.js
    ├── lib/
    │   ├── format/
    │   │   ├── money.js
    │   │   ├── number.js
    │   │   ├── date.js
    │   │   └── text.js
    │   ├── csv/
    │   │   └── csv.js
    │   └── accessibility/
    ├── assets/
    │   ├── brand/
    │   ├── illustrations/
    │   └── icons/
    └── styles/
        ├── tokens.css
        ├── reset.css
        ├── globals.css
        ├── typography.css
        └── utilities.css
~~~

### 4.1 Ajustes permitidos a la estructura

- No crear carpetas vacías por anticipado.
- Si un feature tiene un solo archivo pequeño, puede conservarlo en su carpeta hasta que necesite separar responsabilidades.
- Los componentes de negocio reutilizables pertenecen a entities o a su dominio, no a shared/ui.
- Los widgets solo deben existir cuando una composición visual tenga valor propio y se reutilice o sea suficientemente compleja.
- Los componentes privados de un feature deben permanecer dentro de ese feature y no exponerse como API pública.
- Mantener extensiones .js y .jsx mientras el proyecto use JavaScript. No introducir TypeScript como parte de esta tarea.

## 5. Organización interna de cada feature

Cada feature que tenga suficiente complejidad debe seguir una estructura interna consistente. No es necesario que todos los subdirectorios existan desde el primer día.

~~~text
features/<dominio>/<accion>/
│
├── ui/
│   ├── CreateProductForm.jsx
│   ├── ProductFormHeader.jsx
│   ├── ProductGeneralStep.jsx
│   └── ProductReviewStep.jsx
│
├── model/
│   ├── useCreateProduct.js
│   ├── productFormState.js
│   └── productValidation.js
│
├── api/
│   └── createProduct.js
│
├── lib/
│   ├── mapProductFormToRequest.js
│   └── mapProductResponse.js
│
└── index.js
~~~

Responsabilidades:

- ui: componentes de presentación, inputs y formularios propios de la acción.
- model: hooks y estado del flujo, validaciones y reglas de interacción.
- api: funciones de petición específicas de la operación o mutación.
- lib: funciones puras para transformar datos y cálculos exclusivos del feature.
- index.js: exporta únicamente lo necesario para consumir el feature desde widgets o páginas.

Para un feature pequeño, una organización reducida es suficiente:

~~~text
features/<dominio>/<accion>/
├── CreateAction.jsx
├── useCreateAction.js
└── index.js
~~~

No se debe crear una capa adicional si solo cambia el nombre del archivo y no separa una responsabilidad real.

## 6. Separación de componentes: reglas prácticas

La separación debe distinguir cuatro tipos de componente.

### 6.1 Primitivas visuales compartidas

Son neutrales al negocio y no conocen endpoints, permisos comerciales ni modelos de una entidad.

Ejemplos:

- Button, IconButton, Input, Select, Checkbox y Textarea.
- FormField, FieldError, FormSection y FormActions.
- Dialog, Drawer y Popover accesibles.
- Badge, StatusBadge, Skeleton y Spinner.
- EmptyState, ErrorState y LoadingState.
- DataTable, Pagination y Stepper cuando sus APIs sean genéricas.

Un componente debe estar en shared/ui solo cuando su contrato puede reutilizarse sin importar el dominio de negocio.

### 6.2 Componentes de entidad

Representan una entidad específica y pueden conocer su modelo, pero no deben ejecutar workflows completos.

Ejemplos:

- ProductPicker: selector especializado de productos.
- ProductSummary: información resumida del producto.
- WarehouseSelector: selector de almacenes.
- CustomerSummary: resumen del cliente.
- PurchaseStatusBadge y SaleStatusBadge.
- InventoryBalanceRow: representación de un saldo.

Estos componentes viven en entities/<entidad>/ui. Si son exclusivos de una sola operación y no se reutilizan, deben permanecer dentro del feature que los creó.

### 6.3 Componentes de feature

Implementan una interacción de negocio concreta:

- Formulario para crear o editar producto.
- Editor de propiedades dinámicas de categoría.
- Formulario multítem de compra.
- Selector de líneas y cantidades de una venta.
- Formulario de recepción parcial.
- Asistente de conteo físico.
- Confirmación de una devolución.

Conocen el propósito de la operación y sus validaciones de presentación, pero no deben convertirse en layouts generales ni duplicar el cliente HTTP.

### 6.4 Widgets y páginas

Un widget compone elementos completos, por ejemplo una tabla con filtros, paginación y acciones; una sección de KPIs; o una pantalla de detalle formada por paneles.

La página coordina los widgets y determina qué contenido corresponde a la URL. No debe contener consultas HTTP extensas, transformaciones repetidas ni lógica de negocio.

### 6.5 Regla para dividir archivos grandes

Extraer un componente cuando se cumpla una o más de estas condiciones:

- Tiene una responsabilidad funcional identificable que puede probarse por separado.
- Se utiliza en más de una pantalla o flujo.
- Su estado y eventos complican la comprensión del componente principal.
- Tiene un conjunto de validaciones propio.
- Tiene un ciclo de carga, error o actualización independiente.
- Su contenido visual tiene identidad y puede evolucionar sin modificar el resto de la pantalla.

No extraerlo únicamente porque supera cierto número de líneas. Un componente demasiado pequeño que solo reenvía props agrega navegación y complejidad sin aportar claridad.

## 7. Reorganización concreta de los módulos actuales

### 7.1 Catálogo

Estado actual: features/catalog contiene ProductForm.jsx, CategoryForm.jsx, CategoryProperties.jsx, CategoryQuickCreate.jsx, ProductsPage.jsx y CSS compartido.

Organización objetivo:

~~~text
features/catalog/
├── create-product/
│   ├── ui/
│   │   ├── ProductForm.jsx
│   │   ├── ProductGeneralStep.jsx
│   │   ├── ProductClassificationStep.jsx
│   │   ├── ProductAttributesStep.jsx
│   │   ├── ProductImagesStep.jsx
│   │   └── ProductReviewStep.jsx
│   ├── model/
│   │   ├── useProductForm.js
│   │   └── productFormValidation.js
│   └── lib/
├── edit-product/
├── create-category/
├── manage-category-attributes/
│   ├── ui/
│   │   ├── PropertiesEditor.jsx
│   │   ├── TemplatePicker.jsx
│   │   └── SuggestionNote.jsx
│   └── model/
├── manage-product-images/
├── change-product-status/
├── delete-product/
└── shared/
    ├── ui/
    ├── model/
    └── lib/
~~~

ProductsPage.jsx deja de concentrar pestañas, listado, filtros, detalle y formularios. La página se compone de ProductListWidget, CategoryListWidget y el detalle de producto. El formulario de producto es un feature invocado desde una ruta propia.

### 7.2 Inventario

InventoryOperations.jsx concentra actualmente varios procesos: traslados, conteos, ajustes y reglas de reposición. Deben separarse porque tienen permisos, validaciones, estados y APIs diferentes.

~~~text
features/inventory/
├── create-adjustment/
├── create-transfer/
├── dispatch-transfer/
├── receive-transfer/
├── create-inventory-count/
├── post-inventory-count/
├── manage-reorder-rule/
└── filter-inventory/

widgets/inventory/
├── InventoryBalancesWidget.jsx
├── InventoryMovementsWidget.jsx
├── TransfersWidget.jsx
├── InventoryCountsWidget.jsx
└── ReplenishmentWidget.jsx
~~~

Las consultas de saldo, kardex y disponibilidad deben pertenecer a los hooks o servicios de consulta de inventario. La UI no recalcula ni modifica existencias por su cuenta.

### 7.3 Compras

~~~text
features/purchasing/
├── create-purchase-order/
│   ├── ui/
│   │   ├── PurchaseForm.jsx
│   │   ├── PurchaseSupplierStep.jsx
│   │   ├── PurchaseItemsStep.jsx
│   │   └── PurchaseReviewStep.jsx
│   └── model/
├── submit-purchase-order/
├── approve-purchase-order/
├── cancel-purchase-order/
├── receive-purchase-order/
│   ├── ui/
│   │   ├── ReceiptForm.jsx
│   │   ├── ReceiptQuantitiesStep.jsx
│   │   └── ReceiptReviewStep.jsx
│   └── model/
└── create-purchase-return/

widgets/purchasing/
├── PurchaseOrdersWidget.jsx
├── PurchaseOrderDetailWidget.jsx
└── PurchaseReturnsWidget.jsx
~~~

PurchaseTable.jsx y PurchaseReturns.jsx deben separarse según su papel real: tabla y acciones de órdenes, detalle, o creación/consulta de devoluciones. El detalle debe disponer de una ruta navegable y no depender exclusivamente de un modal.

### 7.4 Ventas

~~~text
features/sales/
├── create-sale/
│   ├── ui/
│   │   ├── SaleForm.jsx
│   │   ├── SaleCustomerStep.jsx
│   │   ├── SaleItemsStep.jsx
│   │   ├── SalePaymentStep.jsx
│   │   └── SaleReviewStep.jsx
│   └── model/
├── request-sale-cancellation/
├── approve-sale-cancellation/
└── create-sales-return/

widgets/sales/
├── SalesListWidget.jsx
├── SaleDetailWidget.jsx
└── SalesReturnsWidget.jsx
~~~

El frontend consulta disponibilidad, presenta precios y prepara la solicitud, pero el backend es siempre la autoridad para validar stock, precios permitidos, descuentos y contabilización.

### 7.5 Reportes

ReportsCenter.jsx debe dividirse por reporte y por responsabilidad:

~~~text
features/reports/
├── inventory-balance/
│   ├── ui/
│   ├── model/
│   └── api/
├── sales-report/
├── purchase-report/
├── kardex-report/
├── valuation-report/
├── low-stock-report/
└── export-report/

widgets/reports/
├── ReportsWorkspace.jsx
├── ReportFilters.jsx
├── ReportResultsTable.jsx
└── ReportSummary.jsx
~~~

Cada reporte debe tener su propio estado de filtros, carga, error, resultado y paginación. Cambiar de filtros debe invalidar o descartar respuestas anteriores para impedir que se muestren datos de otra consulta. La exportación CSV se mantiene en utilidades compartidas cuando su implementación sea genérica; el mapeo de columnas pertenece al reporte.

### 7.6 Almacenes, proveedores, clientes y unidades

OperationsCatalogs.jsx combina cuatro catálogos distintos mediante configuración dinámica. Puede conservar un workspace con pestañas, pero cada catálogo debe delegar sus operaciones a features propios.

~~~text
widgets/partners/
└── OperationsCatalogsWidget.jsx

features/partners/
├── manage-warehouse/
├── manage-supplier/
├── manage-customer/
├── manage-unit/
└── manage-unit-conversion/
~~~

### 7.7 Dashboard

El dashboard debe ser una composición de widgets pequeños e independientes. La página no debe cargar todos los datos de la aplicación para construir el resumen.

Cada widget solicita únicamente el conjunto de datos necesario y gestiona su estado de carga, vacío, error y permiso.

### 7.8 Autenticación y sesión

Separar la pantalla de login, el estado de sesión, la expiración y el cierre de sesión:

~~~text
features/auth/
├── sign-in/
│   ├── ui/
│   │   └── LoginForm.jsx
│   ├── model/
│   │   └── useSignIn.js
│   └── api/
│       └── signIn.js
├── sign-out/
├── current-user/
└── session-expiration/

app/providers/
└── AuthProvider.jsx

shared/security/
└── loginCrypto.js
~~~

AuthProvider debe administrar únicamente sesión, usuario autenticado, expiración y estado de autenticación. No debe almacenar saldos, productos, compras o ventas.

## 8. App.jsx: responsabilidad final

App.jsx debe ser un componente pequeño que componga la aplicación. No es el lugar para cargar todos los datos, definir reglas de negocio ni implementar operaciones.

Responsabilidades recomendadas:

- Montar providers.
- Resolver la ruta actual mediante el router.
- Aplicar layouts públicos o privados.
- Mostrar la página correspondiente.
- Exponer los límites globales de feedback y error cuando aplique.

Ejemplo conceptual:

~~~jsx
function App() {
  return (
    <AppProviders>
      <AppRouter />
      <FeedbackHost />
    </AppProviders>
  );
}

export default App;
~~~

El código anterior es ilustrativo; no es una instrucción para reemplazar el archivo actual sin una migración.

### 8.1 Qué sale de App.jsx

| Responsabilidad actual | Destino propuesto |
|---|---|
| NAV_GROUPS y navegación | app/navigation/navigationConfig.js |
| Shell, Sidebar y Topbar | app/layouts/ |
| Lectura y sincronización de rutas | app/router/ |
| Sesión, token y usuario | app/providers/AuthProvider y features/auth |
| Carga de productos | entities/product o features/catalog, según endpoint/uso |
| Carga de compras y aprobaciones | features/purchasing |
| Carga de ventas y cancelaciones | features/sales |
| Carga de saldos y movimientos | features/inventory |
| Carga de reportes | features/reports |
| Carga de suscripciones | features/subscriptions |
| Carga de pedidos WhatsApp | features/whatsapp |
| submitProduct y submitCategory | features/catalog |
| submitPurchase y submitReceipt | features/purchasing |
| submitSale y cancelación | features/sales |
| openDetail y detalles de entidades | pages y widgets del dominio correspondiente |
| Confirmaciones y avisos genéricos | shared/ui/feedback |
| Formato, moneda y números | shared/lib/format |

### 8.2 Carga de datos y estado

No se recomienda crear un GlobalContext con todos los datos de negocio. Eso trasladaría el problema de App.jsx a otro archivo.

Reglas:

- El estado de sesión es global.
- El estado de navegación pertenece al router.
- El estado de un formulario pertenece a su feature.
- Los filtros y la paginación pertenecen al widget o página correspondiente.
- Los datos remotos se cargan donde se consumen.
- Los datos compartidos se exponen mediante hooks pequeños y contratos claros.
- Los datos derivados se calculan con funciones puras o useMemo cuando realmente sea necesario.
- No duplicar una misma colección en varios contextos globales.
- Al cerrar sesión se eliminan los datos sensibles y estados temporales del usuario.
- Las solicitudes deben poder cancelarse o ignorarse cuando el usuario cambia de ruta o filtros.

Mientras no exista una librería de caché de consultas, se pueden utilizar hooks por feature con fetch, AbortController y estados explícitos. La introducción futura de TanStack Query u otra herramienta debe evaluarse por necesidades de caché, invalidación y concurrencia, no para organizar carpetas.

## 9. Enrutamiento

El proyecto ya tiene un router propio en router.js, con rutas directas, alias heredados, parámetros de detalle, workflows y soporte de base /inventary/.

Se recomienda moverlo a app/router/ conservando el comportamiento actual:

- Definiciones de rutas.
- Matcher y resolución de parámetros.
- Navegación y generación de URLs.
- Guardas de sesión y rutas privadas.
- Títulos de documento.
- Compatibilidad de rutas antiguas.
- Redirección de login y parámetro de destino.

Las rutas deberían mapear una URL a una página concreta:

| Ruta actual o equivalente | Página propuesta |
|---|---|
| /iniciar-sesion | pages/auth/LoginPage |
| /resumen | pages/dashboard/DashboardPage |
| /productos | pages/catalog/ProductsPage |
| /productos/categorias | pages/catalog/CategoriesPage |
| /productos/nuevo | pages/catalog/ProductCreatePage |
| /productos/:id/editar | pages/catalog/ProductEditPage |
| /compras | pages/purchasing/PurchaseOrdersPage |
| /compras/nueva | pages/purchasing/PurchaseCreatePage |
| /compras/:id | pages/purchasing/PurchaseDetailPage |
| /compras/:id/recepcion | pages/purchasing/ReceiptCreatePage |
| /ventas | pages/sales/SalesPage |
| /ventas/nueva | pages/sales/SaleCreatePage |
| /ventas/:id | pages/sales/SaleDetailPage |
| /inventario | pages/inventory/InventoryPage |
| /inventario/traslados/nuevo | pages/inventory/TransferCreatePage |
| /inventario/conteos/nuevo | pages/inventory/InventoryCountCreatePage |
| /inventario/ajustes/nuevo | pages/inventory/AdjustmentCreatePage |
| /reportes | pages/reports/ReportsPage |
| /suscripciones | pages/subscriptions/SubscriptionsPage |
| /pedidos-whatsapp | pages/whatsapp/WhatsAppOrdersPage |

La tabla muestra la intención de composición; deben conservarse las rutas reales, alias y destinos existentes en la primera fase.

**Importante:** rutas de creación y edición extensas deben ser páginas navegables, con URL propia, historial del navegador, posibilidad de volver y protección ante pérdida accidental de cambios. No deben depender de un modal que bloquee toda la aplicación.

## 10. Cliente API y contratos del backend

shared/api/http debe ser el único núcleo HTTP. Debe centralizar:

- URL base desde VITE_API_BASE_URL.
- Headers comunes y token de sesión.
- Serialización y lectura de respuestas.
- Normalización de errores HTTP.
- Tratamiento de expiración de sesión.
- Claves de idempotencia para operaciones que el backend soporte.
- Resolución de URL de recursos.
- Cancelación de solicitudes cuando sea aplicable.

Los endpoints se organizan por dominio, sin crear un gran archivo de API que mezcle toda la aplicación:

~~~text
features/
├── catalog/
│   ├── create-product/api/createProduct.js
│   └── manage-product-images/api/productImagesApi.js
├── inventory/
│   ├── create-transfer/api/createTransfer.js
│   └── filter-inventory/api/inventoryQueries.js
├── purchasing/
│   └── create-purchase-order/api/purchaseOrdersApi.js
└── sales/
    └── create-sale/api/createSale.js
~~~

Una alternativa válida para un dominio con muchas operaciones es un features/<dominio>/api/<dominio>Api.js que agrupe endpoints relacionados, siempre que no se convierta en un cliente global.

Reglas de integración:

1. Los contratos y permisos del backend son la fuente de verdad.
2. No construir inventarios ni cálculos contables simulados en el navegador.
3. No modificar directamente el saldo del inventario desde el frontend.
4. Las claves de idempotencia se aplican solo a endpoints que las soportan.
5. Los errores 401, 403, 404, 409, 413 y de red deben mantener tratamiento explícito.
6. No almacenar secretos de backend en variables públicas Vite.
7. Los datos sensibles o financieros se muestran solo cuando el usuario tiene el permiso correspondiente; la autorización definitiva siempre es del backend.
8. La matriz docs/api-coverage.md debe actualizarse junto con cada cambio de endpoints consumidos.

## 11. Formularios y experiencia de usuario

La arquitectura debe favorecer formularios comprensibles, predecibles y accesibles. La separación de componentes debe reducir la fatiga visual y los errores de operación.

### 11.1 Formularios extensos: página y stepper, no modal

Los formularios con múltiples secciones, conjuntos de datos, decisiones o líneas de productos deben mostrarse en páginas dedicadas. Deben utilizar un stepper cuando el flujo tenga etapas naturales.

Aplicación prevista:

- Producto: datos generales, clasificación/unidad, atributos, imágenes y revisión.
- Categoría: datos básicos, plantilla/atributos y revisión.
- Compra: proveedor/almacén, productos/cantidades/costos, revisión.
- Venta: almacén/cliente, productos y disponibilidad, revisión/confirmación.
- Recepción: cantidades por línea, validación de pendientes, revisión.
- Traslado: origen/destino, productos/cantidades, revisión.
- Conteo físico: almacén y alcance, captura, diferencias y contabilización.

### 11.2 Cuándo utilizar modales o drawers

Los modales deben reservarse para tareas puntuales y cortas:

- Confirmar una acción destructiva o irreversible.
- Crear rápidamente un dato auxiliar pequeño cuando el formulario principal necesita conservar su estado.
- Mostrar una ayuda o selección breve.
- Capturar una razón corta o una decisión simple.

No utilizar modales como contenedor principal de formularios extensos ni encadenar diálogos uno dentro de otro.

Los drawers se reservan para detalles complementarios que no requieren una ruta completa. Un detalle importante o compartible debe tener URL propia.

### 11.3 Reglas del stepper

- Mostrar claramente el número y nombre de cada paso.
- Indicar cuáles están completos, activos o pendientes.
- Permitir volver a pasos anteriores sin perder datos.
- Validar el paso actual antes de avanzar.
- Resumir los errores junto al campo y enfocar el primer error.
- No permitir confirmar operaciones con datos incompletos.
- Mostrar un resumen antes de acciones sensibles.
- Deshabilitar doble envío mientras la operación está en curso.
- Advertir antes de salir si existen cambios sin guardar.
- No borrar el formulario al recibir un error del backend.
- Mantener navegación por teclado y etiquetas accesibles.

### 11.4 Componentes de formularios

shared/ui/forms debe proporcionar controles y patrones visuales, mientras que cada feature define los campos que corresponden a su operación.

Un formulario de compra no debe contener un componente genérico que conozca ventas. Ambos pueden reutilizar FormField, FormSection, FormActions, FieldError, Stepper y controles base.

## 12. Sistema visual, CSS y assets

Se recomienda organizar el CSS sin imponer una librería nueva.

### 12.1 Capas de estilos

| Archivo/capa | Responsabilidad |
|---|---|
| tokens.css | Colores, tipografía, escalas de espacio, radios, sombras, breakpoints y z-index |
| reset.css | Normalización y box sizing |
| typography.css | Jerarquía tipográfica y estilos de texto |
| globals.css | Estilos globales de body, root, focus y elementos base |
| utilities.css | Clases utilitarias realmente transversales |
| CSS de feature/widget | Layout y estilos privados de cada funcionalidad |
| CSS Modules, cuando se adopten | Aislar estilos de componentes específicos y evitar colisiones |

Los estilos de catalog, dashboard y auth deben migrar gradualmente a sus módulos. No se recomienda una reescritura completa de CSS dentro del cambio de arquitectura.

### 12.2 Convenciones

- Preferir Component.module.css o estilos co-localizados con prefijo de dominio mientras se mantiene CSS global.
- Evitar selectores globales que alteren controles dentro de módulos ajenos.
- No duplicar valores de color, espaciado o tipografía que deban ser tokens.
- Definir breakpoints y comportamiento responsive de manera consistente.
- Mantener estados hover, focus-visible, disabled, loading, error y success.
- Respetar prefers-reduced-motion.
- Probar pantallas con contenido largo, tablas anchas y formularios multítem.

### 12.3 Assets

- Logos de EcuApp e Inventary en shared/assets/brand.
- Ilustraciones de autenticación en shared/assets/illustrations o dentro de auth si son exclusivas de login.
- Imágenes y archivos cargados por usuarios no se incluyen en assets estáticos: se obtienen mediante el backend.
- Los iconos de interfaz siguen usando lucide-react, con etiquetas accesibles para controles sin texto.

## 13. Estado, hooks y utilidades

### 13.1 Hooks

Los hooks se ubican según su responsabilidad:

- Hooks de sesión y autenticación: auth.
- Hooks de consulta y mutación: feature o entidad propietaria.
- Hooks neutrales de navegador: shared/hooks.
- Hooks de accesibilidad de un diálogo: shared/ui/overlays o shared/lib/accessibility.
- Hooks de un formulario: dentro del feature del formulario.

No crear hooks/ como una carpeta global para todo.

### 13.2 Utilidades

Las utilidades compartidas deben ser funciones puras, sin conocer componentes ni estado global.

- Formato de moneda, número, fechas y texto: shared/lib/format.
- CSV genérico: shared/lib/csv.
- Accesibilidad: shared/lib/accessibility.
- Transformaciones propias de un producto, compra o reporte: lib de ese módulo.

### 13.3 Seguridad

loginCrypto.js debe conservarse aislado del UI. El cifrado del login, manejo de tokens y expiración de sesión no deben mezclarse con tablas, páginas ni formularios comerciales.

La SPA no sustituye el RBAC del backend. Ocultar una acción según permisos mejora la experiencia, pero el servidor debe seguir rechazando cualquier operación no autorizada.

## 14. Pruebas y calidad

La reorganización debe conservar node --test como mecanismo actual mientras se prepara una estrategia de pruebas de UI más completa.

Estructura sugerida:

~~~text
src/
├── app/
│   └── router/
│       └── routeMatcher.test.js
├── features/
│   ├── catalog/
│   │   └── create-product/
│   │       ├── productFormValidation.test.js
│   │       └── mapProductFormToRequest.test.js
│   ├── inventory/
│   ├── purchasing/
│   ├── sales/
│   └── reports/
├── shared/
│   ├── api/
│   │   └── http/
│   │       └── request.test.js
│   └── lib/
│       ├── format/
│       └── csv/
└── test/
    ├── fixtures/
    ├── mocks/
    └── scenarios/
~~~

Cobertura recomendada:

- Router: rutas válidas, alias antiguos, IDs, base pública, login y destino después de autenticación.
- Seguridad de UI: visibilidad de acciones por permiso y expiración de sesión.
- Formularios: valores válidos, obligatorios, límites, datos dinámicos, errores y prevención de doble envío.
- Catálogo: creación/edición, categoría y atributos, imágenes.
- Compras: orden, aprobación y recepción parcial.
- Ventas: disponibilidad, venta, cancelación y devolución.
- Inventario: ajustes, transferencias, conteos y reposición.
- Reportes: filtros, rangos de fechas, paginación, cambios rápidos de filtros y exportación.
- Componentes: teclado, foco, lector de pantalla, estados de carga, vacío y error.
- Integración: verificar contrato contra backend real, sin reemplazar errores por fixtures silenciosos.

Se recomienda evaluar la incorporación de Vitest y React Testing Library para pruebas de componentes y Playwright para escenarios end-to-end. Son propuestas de herramientas futuras, no dependencias que deban instalarse durante la reorganización documental. Antes de agregarlas se debe comprobar compatibilidad con Node, Vite y el pipeline vigente.

También se recomienda automatizar controles de calidad:

- Lint de JavaScript/JSX.
- Formato consistente.
- Build de producción.
- Pruebas unitarias y de componentes.
- Pruebas de rutas críticas.
- Detección de imports circulares y dependencias entre capas.
- Revisión de dependencias y vulnerabilidades.

El workflow de CI debe validar el SHA que se pretende integrar; los resultados antiguos no demuestran el estado del código actual.

## 15. Estrategia de migración recomendada

La reorganización debe ser incremental. No se deben mover todos los componentes ni reescribir todo el frontend en un solo cambio.

### Fase 0 — Línea base

- Registrar el SHA de partida.
- Revisar package.json, package-lock.json, vite.config.js y configuración de despliegue.
- Ejecutar build y pruebas disponibles en el HEAD de partida.
- Registrar las rutas actuales, alias y enlaces profundos.
- Registrar el contrato REST consumido y permisos por módulo.
- Verificar rutas de imágenes, base /inventary/ y configuración Nginx.
- Documentar errores y comportamientos actuales para no introducir cambios involuntarios.

### Fase 1 — Crear el shell de aplicación

- Separar layouts, navegación, topbar, sidebar y providers.
- Mover el router propio a app/router/ conservando sus pruebas.
- Mantener main.jsx como punto de entrada.
- Hacer que App.jsx se limite a providers y router.
- No cambiar rutas públicas ni estructura visual en esta etapa, salvo correcciones imprescindibles.

### Fase 2 — Extraer sesión y cliente HTTP

- Separar sesión/usuario/expiración del resto de datos.
- Dividir infraestructura HTTP de funciones de endpoints.
- Mover login crypto a una zona compartida de seguridad.
- Mantener manejo centralizado de errores, idempotencia y logout.
- No duplicar clientes HTTP por cada módulo.

### Fase 3 — Separar módulos funcionales

Orden recomendado:

1. Catálogo.
2. Compras y recepción.
3. Ventas y devoluciones.
4. Inventario.
5. Almacenes, proveedores, clientes y unidades.
6. Reportes.
7. Dashboard.
8. Suscripciones y WhatsApp.

Para cada módulo:

- Crear sus páginas, widgets, features y componentes de entidad según necesidad.
- Extraer la lógica de consulta y mutación a hooks/servicios del módulo.
- Separar formulario, pasos, validación y resumen.
- Dejar cada página como composición.
- Probar el contrato con el backend y el comportamiento del flujo.

### Fase 4 — Normalizar componentes compartidos

- Catalogar componentes existentes y decidir si son shared, entity, feature o widget.
- Extraer primitivas neutrales.
- Mantener componentes de negocio dentro de sus dominios.
- Consolidar tablas, filtros, paginación, estados vacíos y feedback sin acoplarlos a una API concreta.
- Eliminar duplicación solo después de verificar que los componentes tienen el mismo comportamiento y semántica.

### Fase 5 — CSS, accesibilidad y responsive

- Introducir tokens visuales.
- Separar globales de estilos locales.
- Revisar cada módulo en escritorio, tablet y móvil.
- Comprobar formularios largos, stepper, tablas y overlays.
- Validar navegación por teclado y foco.
- Mantener consistencia de colores, tipografía, espacios y estados.

### Fase 6 — Calidad y cierre

- Completar pruebas unitarias, de componentes y escenarios.
- Añadir reglas de arquitectura e imports permitidos.
- Actualizar README, roadmap, api-coverage y troubleshooting.
- Revisar que no queden imports ni rutas obsoletas.
- Validar la imagen Docker y configuración Nginx.
- Ejecutar build y CI sobre el commit final.
- Documentar decisiones, excepciones y deuda técnica que permanezca.

## 16. Criterios de aceptación

La nueva arquitectura se considera implementada cuando todos los siguientes puntos se cumplan:

- [ ] App.jsx no coordina operaciones ni carga indiscriminadamente datos de todos los dominios.
- [ ] Layout, navegación, providers y router están separados.
- [ ] Las rutas existentes y sus alias continúan funcionando.
- [ ] Cada página representa una URL y compone widgets o features.
- [ ] Cada dominio posee sus componentes y lógica específicos.
- [ ] Los formularios extensos viven en páginas dedicadas y usan stepper cuando corresponde.
- [ ] Los modales se reservan para interacciones cortas.
- [ ] Los componentes neutrales al negocio viven en shared/ui.
- [ ] Los componentes específicos de entidades viven en entities.
- [ ] Las acciones de negocio viven en features.
- [ ] Las secciones compuestas viven en widgets cuando aportan una composición reutilizable.
- [ ] El núcleo HTTP es único y las operaciones API están organizadas por dominio.
- [ ] El estado global se limita principalmente a sesión y capacidades transversales.
- [ ] Las consultas y mutaciones se gestionan cerca de la funcionalidad que las utiliza.
- [ ] Ningún módulo modifica el inventario fuera de las APIs del backend.
- [ ] Permisos, errores, idempotencia y expiración de sesión mantienen su comportamiento.
- [ ] Los estilos globales, tokens y estilos por módulo están delimitados.
- [ ] Componentes accesibles por teclado y lector de pantalla.
- [ ] Interfaces responsive sin desbordamientos ni pérdida de acciones importantes.
- [ ] Build de producción correcto.
- [ ] Pruebas automatizadas correctas y asociadas al SHA final.
- [ ] Docker, Nginx y base pública /inventary/ funcionan correctamente.
- [ ] README y documentación reflejan el árbol final.

## 17. Restricciones para evitar regresiones

### 17.1 No cambiar contratos del backend

La refactorización del frontend no autoriza a cambiar rutas REST, nombres de campos, permisos, formato JSON, claves de idempotencia ni semántica de los endpoints.

### 17.2 No simular reglas del servidor

La UI puede anticipar errores para ayudar al usuario, pero la disponibilidad, contabilización de compras, ventas, recepciones, devoluciones, ajustes y transferencias debe confirmarse en el backend.

### 17.3 No perder accesibilidad

No eliminar el control de foco, soporte Escape, foco inicial, retorno de foco, etiquetas ARIA ni navegación por teclado de los diálogos existentes al moverlos.

### 17.4 No introducir dependencias de forma incidental

La reorganización de carpetas no requiere sustituir React ni Vite. Cualquier librería nueva debe tener justificación, compatibilidad verificada, actualización del lockfile y cobertura apropiada.

### 17.5 No ocultar deuda técnica

Una página que aún necesite lógica temporal puede permanecer documentada como excepción durante la migración. No se debe declarar una arquitectura completa solo porque se hayan creado directorios.

## 18. Decisión recomendada

Se recomienda aprobar la arquitectura feature-first por dominios y comenzar por extraer el shell y el routing de App.jsx.

Orden de trabajo:

1. Crear app/ y separar providers, layouts y router.
2. Extraer sesión y núcleo HTTP.
3. Reorganizar catalog como primer módulo de referencia.
4. Separar compras, ventas e inventario en páginas, widgets y features.
5. Reorganizar reportes, dashboard, partners, suscripciones y WhatsApp.
6. Normalizar entities, shared/ui, hooks, utilidades y estilos.
7. Incorporar pruebas de arquitectura y UI.
8. Actualizar documentación y criterios de cierre.

**Resultado esperado:** un frontend organizado por funcionalidades, con un App.jsx pequeño, páginas enfocadas, widgets compuestos, features responsables de cada operación, entidades reutilizables y una biblioteca compartida neutral al negocio. Los componentes tendrán límites claros y podrán evolucionar y probarse sin acoplar todos los módulos entre sí.

---

## 19. Alcance de este documento

Este documento define una propuesta. No ejecuta movimientos, renombrados, refactorizaciones, instalaciones de dependencias ni eliminación de archivos.

La futura implementación debe hacerse gradualmente, verificando el comportamiento en cada fase. La estructura objetivo no autoriza a cambiar contratos públicos, permisos, rutas ni comportamiento funcional sin una decisión específica.
