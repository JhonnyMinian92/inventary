## Adenda de actualización — 2026-11-02

**Código actualizado:** commit 992b131c2de9cf5ed60c83ec4b826d2797029270. Los commits posteriores en esta rama contienen documentación funcional por pantalla.

- SecurityConfig ya no permite acceso público a /api/v1/tax/** ni /api/v1/refunds/**.
- Fiscal: requiere security:manage.
- Reembolsos: GET requiere sales:read; aprobar requiere sales:cancel; otras escrituras requieren sales:manage.
- TaxController aplica filtro por vigencia efectiva y estado activo.
- Se agregó prueba HTTP de contrato para ausencia de autenticación y permisos.
- CI del commit de código está en ejecución; no marcar este hallazgo como verificado hasta que finalice correctamente.
- El cierre financiero de reembolsos sigue pendiente: ledger de pagos liquidados, conciliación, pasarela y confirmación autenticada del proveedor.

La matriz funcional más útil para conocer qué falta por pantalla frontend/backend está en [ESTADO_FUNCIONAL_POR_PANTALLA.md](../../ESTADO_FUNCIONAL_POR_PANTALLA.md).

# 24 — Estado actual verificable del repositorio

**Rama:** implementacion_propuestas  
**SHA de código auditado:** dc028a346ccf7b94cfbb60b4fb6cd5493518b481  
**Evidencia de Actions:** [Backend CI 41385132612](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132612) · [Frontend Build 41385132616](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132616).

Este documento es la referencia viva del estado del código. Los documentos anteriores o con SHA distinto se leen como historia, no como certificación del HEAD.

## 1. Dictamen ejecutivo

Inventary contiene una SPA React/Vite y una API Spring Boot/PostgreSQL. El backend es un monolito modular, no un conjunto de microservicios independientes. La rama incorpora catálogo, inventario, compras, ventas, devoluciones, suscripciones, WhatsApp, reportes, auditoría, Outbox/Inbox y las primeras tablas/endpoints de operaciones y webhooks.

El estado de calidad de código es bueno en los workflows del SHA auditado: backend 119 pruebas y smoke de backend desde base vacía; frontend 26 pruebas, build y smoke responsive del login. Esto no significa que todos los módulos tengan E2E browser ni que el entorno sea apto para producción.

> **Bloqueo P0:** el backend permite actualmente acceso sin autenticación a las rutas /api/v1/tax/** y /api/v1/refunds/** en la cadena general de Spring Security. Los controladores no agregan autorización por método. No es una excepción limitada a local/dev. Esta situación impide aprobar la exposición del sistema a usuarios o redes no confiables.

## 2. Inventario técnico observado

| Elemento | Conteo/versión en el árbol |
|---|---:|
| Archivos Java de producción | 263 |
| Archivos Java de prueba | 25 |
| Controladores Spring identificados | 32 |
| Migraciones Flyway | V1–V28 (28 archivos) |
| Archivos bajo frontend src | 127 |
| Módulos feature frontend | 12 |
| Archivos de prueba frontend | 6 |
| Scripts frontend | test, build, dev, preview |
| Java / Spring Boot | 21 / 3.5.6 |
| React / Vite | 19 / 8.3.5 |

## 3. Frontend: funcionalidades visibles

- Auth/login, providers de sesión, permisos y router.
- Dashboard con ventas, utilidad, alertas, tareas, existencias y movimientos.
- Escena isométrica operativa con SSE; el evento recibido dispara una consulta de refresco, no entrega un payload completo de negocio.
- Catálogo de productos/categorías/atributos/unidades/variantes/medios.
- Contactos y almacenes; registro tributario manual con establecimientos y snapshots.
- Inventario: centro de mando, existencias, movimientos, operaciones, conteos, ajustes, traslados y reposición.
- Compras: pestañas órdenes, recepciones y devoluciones a proveedores; filtros/paginación server-side.
- Ventas: pestañas ventas, devoluciones y reembolsos; acciones de cancelación/aprobación.
- Suscripciones con filtros y vistas operativas.
- WhatsApp: bandeja, resumen de trabajo, detalle y confirmación manual de pago.
- Reportes: balance, ventas, compras, Kárdex, valoración y CSV.

La organización por features está presente, pero las pruebas browser actuales no recorren todos estos flujos con autenticación y roles diferentes.

## 4. Backend: funcionalidades y límites

| Dominio | Implementado en código | Límite real |
|---|---|---|
| Auth/RBAC | JWT, cifrado de login, revocación, roles/permisos | Excepciones fiscales/reembolso exponen rutas sin JWT; revisar scope en todos los controladores. |
| Catálogo | Productos, categorías, atributos, variantes, unidades, imágenes | Variantes comparten stock/costo del producto padre. |
| Inventario | Saldos, movimientos, ajustes, conteos maker-checker, FIFO/promedio, transferencias | Una ubicación operativa por producto/almacén; no WMS multi-bin. |
| Compras | Órdenes, aprobación, recepciones parciales, devoluciones | No existe factura de proveedor/AP ni pagos a proveedores. |
| Ventas | Venta transaccional, stock, coste, cancelación aprobada, retorno físico | Snapshot tributario/comercial completo y cumplimiento no están cerrados. |
| Fiscal | Datos tributarios manuales, tarifas, tratamiento de producto y cálculo inicial | Sin SRI real; snapshot histórico incompleto; API fiscal pública por configuración actual. |
| Reembolsos | Entidad, estados, servicio, endpoints y panel | API pública, sin libro de pagos liquidado ni conciliación segura. |
| Suscripciones | Calendario/cuotas y facturación interna | Facturación interna no prueba cobro externo. |
| WhatsApp | Carrito/cotización/pedido, backoffice y pago confirmado manualmente | No hay gateway ni fulfillment completo. |
| Outbox/Inbox | Envelope versionado, lease, reintentos, deduplicación | Kafka opt-in; consumidor de alcance limitado. |
| Integraciones V28 | Operaciones/tabla de entrega/suscripciones de webhook y endpoints de administración | No se identifica worker/dispatcher de extremo a extremo. |
| Reportes | Balance histórico, ventas, compras, Kárdex, valoración, bajo mínimo | No constituye libro contable ni reportes tributarios oficiales. |
| Multiempresa | No implementado | No anunciar SaaS multi-tenant. |
| WMS avanzado | No implementado | Sin bins/lotes/series/caducidad/picking multi-bin. |

## 5. Integraciones nuevas del último corte

- GET /api/v1/inventory/command-center: agregados y alertas del centro de mando.
- GET /api/v1/dashboard/events: SSE autenticado; emite un evento de conexión y señales snapshot periódicas con updatedAt.
- GET /api/v1/whatsapp-orders/work-summary: contadores de trabajo por ámbito del usuario.
- GET /api/v1/goods-receipts/{id}/returnable-lines: cantidades retornables calculadas en servidor.
- /api/v1/integration-operations: crear, consultar, listar y reintentar operaciones administrativas.
- /api/v1/webhook-subscriptions: crear, listar y desactivar suscripciones.
- Migración V28: tablas integration_operation, webhook_subscription y webhook_delivery, además de permisos integration:read/manage.

La migración y los endpoints administrativos no demuestran por sí mismos que se ejecute la operación remota o que se envíe/reintente un webhook. Falta el ejecutor externo, redelivery y conciliación.

## 6. Seguridad y riesgos confirmados

1. **P0 — Fiscal:** SecurityConfig declara permitAll para /api/v1/tax/**; TaxController no tiene @PreAuthorize. Crear/modificar tarifas y tratamiento fiscal de productos queda público.
2. **P0 — Reembolsos:** SecurityConfig declara permitAll para /api/v1/refunds/**; RefundController no tiene @PreAuthorize. Los endpoints permiten listar y mutar estados sin autenticación.
3. **P1 — Reembolso no financiero:** no existe ledger de pagos realmente liquidados; no se limita suma de reembolsos al saldo liquidado y amount ya reembolsado; complete no exige respuesta autenticada de gateway.
4. **P1 — Snapshot fiscal:** se calcula impuesto usando una tarifa, pero Sale no persiste un detalle fiscal inmutable suficiente por venta/línea. Las columnas de V27 no equivalen al snapshot utilizado.
5. **P1 — Consulta de tarifas:** TaxController acepta parámetro date, pero el código no lo aplica como filtro real por vigencia.
6. **P1 — Integraciones V28:** las operaciones pueden quedar QUEUED; no se encontró un worker externo completo de operación ni de webhook delivery.
7. **P2 — Rate limit:** Caffeine local por instancia; no es global.
8. **P2 — Reproducibilidad operativa:** CI valida el Compose del backend; no prueba el stack completo del Compose raíz con frontend+n8n y PostgreSQL del anfitrión.
9. **P2 — E2E:** smoke browser solo login responsive; faltan recorridos de negocio autenticados.

No utilizar ocultación de botones del frontend como corrección de los riesgos 1–3.

## 7. Evidencia de validación

### Backend CI 41385132612

- Maven Verify con Java 21 y PostgreSQL 16.
- 119 pruebas: 0 fallos, 0 errores, 0 omitidas.
- Configuración Compose backend normal y perfil Kafka.
- Smoke test de arranque de API/backend con volumen PostgreSQL vacío.
- No equivale a probar el Compose raíz con web y n8n.

### Frontend Build 41385132616

- 26 pruebas Node Test Runner.
- Build de producción Vite.
- Preview del build.
- Smoke Chromium de login en 360, 768 y 1280 px.
- No equivale a E2E autenticado de todas las pantallas.

## 8. Estado de entrega

**No aprobado para producción** mientras las rutas fiscales y de reembolsos mantengan permitAll y no exista un ciclo financiero/fiscal validado. La base de aplicación y los checks de CI son reales; el cierre de seguridad, operación e integraciones externas sigue pendiente.

## 9. Documentos autoritativos

- [25 — Referencia de APIs](25-referencia-apis.md)
- [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md)
- [27 — Guía de consumo](27-guia-consumo-apis.md)
- [28 — Plan de cierre](28-plan-cierre-y-validacion.md)
- [29 — Fase 5](29-implementacion-fase5-datos-maestros.md)
- [30 — Fase 6](30-implementacion-fase6-operaciones-comerciales.md)
- [31 — Fase 7](31-implementacion-fase7-integraciones-durables.md)