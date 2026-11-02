# Estado funcional pendiente por pantalla — Inventary

**Rama:** implementacion_propuestas  
**Corte:** 2026-11-02

Este documento separa la existencia de una pantalla de la finalización real del proceso de negocio. "Disponible" significa que existe un flujo básico; no certifica E2E ni producción.

## Matriz funcional frontend + backend

| Pantalla / módulo | Disponible actualmente | Pendiente en frontend | Pendiente en backend / negocio | Estado |
|---|---|---|---|---|
| Login y sesión | Login cifrado, desafío de un solo uso, JWT, perfil/permisos, expiración y logout; fallback de sesión cuando Storage está bloqueado y feedback accesible | E2E browser por escenario/rol, sincronización entre pestañas, accesibilidad auditada y recuperación de contraseña/MFA si entran en alcance | Rate limit/bloqueo distribuidos, pruebas HTTP de concurrencia y revocación; recuperación segura aún no implementada | Parcial — ver [auditoría de login](app-inventary-ecuapp/docs/login-session-audit.md) |
| Dashboard | Indicadores, alertas, tareas, existencias, movimientos y SSE de refresco | Validar filtros, permisos por widget, estados vacíos y coherencia con reportes | Definir y probar fórmulas de utilidad/ventas; SSE solo notifica refresco, no entrega eventos de negocio completos | Parcial |
| Catálogo — productos | Listado, formularios, búsqueda, variantes, atributos, unidades y medios | E2E de alta/edición/archivo, variantes, validaciones, imágenes y errores | Cerrar reglas de variantes/unidades; confirmar storage persistente/escaneo de medios; aclarar stock y costo compartidos con producto padre | Parcial |
| Catálogo — categorías y atributos | CRUD básico y presets | Probar duplicados, desactivación y asociaciones existentes | Integridad referencial, versionado y reglas de borrado lógico | Parcial |
| Clientes y proveedores | Listados y gestión de contrapartes | E2E de creación, edición, búsqueda y selección desde compras/ventas | Completar datos comerciales/fiscales y ciclo de cuentas por cobrar/pagar | Parcial |
| Almacenes y establecimientos | CRUD y asignación fiscal | E2E de ámbitos, almacén predeterminado y usuarios sin almacén | Concurrencia de asignaciones; aislamiento multiempresa no implementado | Parcial |
| Datos tributarios / contribuyentes | Registro manual, establecimientos y snapshots | Validar RUC, historial, revisión y estados | Sin consulta oficial SRI ni sincronización automática; trazabilidad de fuente pendiente | Parcial |
| Configuración de impuestos | CRUD de tarifas y tratamiento fiscal; filtro de vigencia corregido en código | E2E de vigencias históricas/futuras y cálculo por producto | Snapshot fiscal inmutable por venta/línea, redondeos, exenciones y emisión electrónica SRI (XML, firma, recepción, autorización, consulta y anulación) | Parcial |
| Centro de mando de inventario | Agregados y alertas | Filtros, navegación a detalle y acciones útiles | Consistencia de agregados, umbrales y reglas de alertas | Parcial |
| Existencias y movimientos | Saldos, disponibilidad y Kárdex | E2E por producto/almacén/periodo y exportación | Conciliación saldo-ledger, concurrencia y costeo FIFO/promedio operativo | Parcial |
| Ajustes de inventario | Ajustes con motivo y permisos | Confirmaciones, errores y pruebas de roles | Idempotencia, snapshots obsoletos, reservas y atomicidad saldo/ledger/auditoría/Outbox | Parcial |
| Conteos físicos | Creación y flujo maker-checker | E2E desde apertura hasta contabilización/rechazo | Evitar contabilizar snapshots obsoletos frente a ventas/recepciones/ajustes | Parcial |
| Transferencias | Solicitud y operación de traslado | E2E origen/destino, recepción, rechazo y trazabilidad | Stock en tránsito, doble recepción, cancelación y consistencia por almacén | Parcial |
| Reposición | Reglas y sugerencias | Validar umbrales, filtros y conversión a compra | Algoritmo configurable con demanda, lead time, comprometido y mínimos/máximos | Parcial |
| Compras — órdenes | Listado, creación, envío, aprobación y cancelación | E2E por rol y estados; validar edición y errores | Condiciones comerciales, snapshot fiscal/proveedor, cuentas por pagar y pagos a proveedores | Parcial |
| Compras — recepciones | Recepciones parciales y líneas retornables | E2E de recepciones múltiples, discrepancias y lotes si aplican | No exceder pedido bajo concurrencia; coste de entrada y atomicidad stock/ledger/Outbox | Parcial |
| Compras — devoluciones proveedor | Devolución desde recepción | E2E de cantidades, motivo, estado y reflejo en stock | Límite concurrente de cantidades, nota de crédito, efecto financiero y coste histórico | Parcial |
| Ventas | Registro, listado, detalle y cancelación solicitada/aprobada | E2E de caja, clientes, precios, descuentos, pagos y permisos | Cobro/settlement externo, snapshot fiscal, reglas de precio y pruebas de última unidad | Parcial |
| Devoluciones de venta | Retorno físico RESTOCK/SCRAP | E2E parcial/total, motivo y efecto visible en stock | Límite concurrente de cantidades, coste, nota de crédito y vínculo al reembolso | Parcial |
| Reembolsos | Bandeja y estados; endpoints protegidos en el cambio actual | E2E de permisos/estados/importes; no marcar completado sin confirmación externa | Ledger de pagos liquidados, conciliación, límite contra saldo pagado, gateway, webhook firmado y confirmación idempotente | Parcial |
| Suscripciones | Calendario, cuotas, preview y facturación interna | E2E de altas, vencimientos, fallos y cancelaciones | Cobro externo, reintentos, dunning, conciliación y comprobantes fiscales | Parcial |
| WhatsApp backoffice | Bandeja, resumen, detalle y confirmación manual de pago | E2E carrito→cotización→pedido→pago, expiración y permisos | Integración operativa de mensajería, confirmación externa de pago, reservas y fulfillment/entrega | Parcial |
| Reportes — balance | Balance por periodo y CSV | Validar zona horaria, filtros, exportación y consistencia con listados | Definir ventas netas, compras recibidas, devoluciones y stock final; no es libro contable | Parcial |
| Reportes — ventas/compras/Kárdex/valoración | Endpoints y pantallas | Conciliar resultados con casos controlados | Costeo y cortes consistentes; reportes tributarios oficiales no existen | Parcial |
| Usuarios y permisos | Backend de usuarios, roles y permisos | Falta UI integral documentada para usuarios, roles, permisos, sesiones y ámbitos | Matriz RBAC completa y mínimo privilegio probado en todos los endpoints | Pendiente de UI integral |
| Auditoría y operaciones | Eventos, Outbox, operaciones y webhooks en backend | Falta una consola UI integral de consulta, filtros, detalle y redrive | Worker de operaciones, dispatcher webhook, firma, redelivery, dead-letter y observabilidad | Parcial backend / pendiente UI |
| Configuración de integraciones | Tablas y endpoints administrativos; WhatsApp/n8n | Pantalla segura de configuración, secretos enmascarados y diagnóstico | Workers, rotación segura, conectividad y políticas de reintento | Parcial |
| Operación y monitoreo | Actuator, métricas, CI y Compose backend | Panel operativo no identificado | Backup/restore probado, alertas, readiness, rate limit distribuido y Compose completo | Parcial |

## Pendientes transversales de experiencia y negocio

1. E2E browser autenticados para cada pantalla y cada rol relevante.
2. Estados de carga, vacío, error, retry y operación pendiente/fallida en todas las pantallas.
3. Consistencia entre dashboard, operaciones y reportes para los mismos filtros.
4. Pruebas de doble clic, timeout/reintento, última unidad y devoluciones concurrentes.
5. Responsive y accesibilidad de tablas, formularios, diálogos y navegación.
6. Trazabilidad de actor, fecha, motivo, request ID y movimiento para cada acción sensible.

## Cambios recientes en seguridad, fiscalidad y acceso

- Login: fallback de token en memoria ante Storage bloqueado, manejo accesible de errores y mensajes explícitos sobre recuperación aún no disponible. Se añadieron pruebas de regresión del cliente HTTP. Ver [auditoría detallada](app-inventary-ecuapp/docs/login-session-audit.md).

## Cambios recientes en seguridad y fiscalidad

- La ruta /api/v1/tax/** exige ahora security:manage.
- GET /api/v1/refunds/** exige sales:read; aprobar exige sales:cancel; otras escrituras exigen sales:manage.
- TaxController filtra tarifas activas cuya vigencia contiene la fecha solicitada.
- Se agregó prueba de contrato de acceso sin autenticación y permisos.
- No declarar cerrado el P0 hasta CI verde y validación HTTP. Aún falta revisar autorización por objeto/ámbito y el proceso financiero real de reembolsos.

## Orden recomendado de cierre

1. Seguridad de todos los controladores y permisos.
2. Venta → cobro → devolución → reembolso → conciliación.
3. Compra → recepción → devolución proveedor → cuentas por pagar.
4. Snapshot fiscal y emisión electrónica SRI.
5. Costeo e integridad de inventario bajo concurrencia.
6. Workers, webhooks, WhatsApp y automatización.
7. E2E por pantalla/rol, reportes conciliados, backup/restore y despliegue.

Multiempresa, WMS multi-bin, lotes/series/caducidad, picking avanzado y analítica predictiva deben quedar expresamente fuera del MVP o implementarse antes de anunciarlos como disponibles.