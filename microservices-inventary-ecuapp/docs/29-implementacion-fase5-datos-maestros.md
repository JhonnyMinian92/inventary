# Fase 5 — Datos maestros, almacenes y fiscalidad inicial

**Código auditado:** dc028a346ccf7b94cfbb60b4fb6cd5493518b481 · **Estado:** PARCIAL.

## Implementado

- Perfil de contribuyente y establecimientos con códigos y estados recibidos.
- Importación manual trazable con source reference, actor, fecha, hash y snapshot; la importación queda MANUAL_REVIEW.
- Relación temporal entre almacenes y establecimientos, con restricciones y scopes de acceso.
- UI de registro tributario, detalle de establecimientos/snapshots e importación manual.
- Datos de clientes/proveedores y transición del tipo de contraparte.
- Unidades y conversiones en catálogo.
- Código y tratamiento fiscal por producto.
- Tabla de tarifas con vigencia, CRUD inicial y UI de administración.
- Servicio de cálculo tributario que resuelve tarifa efectiva a partir del código del producto y fecha enviada por el servicio de venta.

## Endpoints principales

- GET/POST /api/v1/taxpayers y rutas de detalle, establecimientos y snapshots.
- GET/POST /api/v1/warehouses/{warehouseId}/establishment-assignments; cierre de asignación por ruta específica.
- GET /api/v1/tax/rates, POST /api/v1/tax/rates, PUT /api/v1/tax/rates/{id}.
- PUT /api/v1/tax/products/{id}.

## Límites confirmados

- La importación no consulta automáticamente al SRI ni demuestra que la fuente declarada sea oficial.
- No se implementa todavía emisión/firma/autorización de comprobantes SRI ni RIDE/notas de crédito.
- No hay ownership legal del inventario suficiente para permitir varias razones sociales en una misma bodega.
- La entidad Sale no persiste un snapshot tributario histórico completo por venta/línea, aunque V27 añadió columnas para ello. El cálculo no debe presentarse como comprobante fiscal.
- GET /api/v1/tax/rates?date= acepta date pero la implementación actual no filtra las tarifas por esa fecha.
- **Bloqueo P0:** SecurityConfig declara permitAll para toda la familia /api/v1/tax/** y TaxController no añade @PreAuthorize. La UI no protege esta API; no exponerla hasta restringirla y probar 401/403.

## Pendientes de cierre

1. Corregir autorización y auditoría de todas las operaciones fiscales.
2. Filtrar tarifas por fecha y vigencia con pruebas.
3. Persistir snapshots tributarios inmutables por venta y línea y definir tratamiento para ajustes/devoluciones.
4. Completar ownership legal/contribuyente/establecimiento y sus scopes en todos los comandos.
5. Aprobar proveedor/API/fuente fiscal externa y validar en sandbox real, sin scraping ni datos inventados.
6. Completar identificación del cliente y formularios contextuales donde corresponda.
7. Añadir pruebas HTTP positivas/negativas, scopes y pruebas de cambio de tarifa sin alterar historia.

**Criterio:** el registro manual y las tarifas iniciales son capacidades parciales; no equivalen a validación oficial ni a facturación electrónica.