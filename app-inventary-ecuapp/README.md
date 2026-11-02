# Inventary Web — frontend React

SPA React 19 + Vite 8 que consume la API REST de Inventary. El navegador no escribe saldos ni calcula como autoridad los efectos contables/inventario: los comandos transaccionales se envían al backend, que valida permisos, reglas, idempotencia y consistencia.

**Código auditado:** rama `implementacion_propuestas`. Login y sesión revisados; Frontend CI y Backend CI aprobados. Evidencia y brechas: [auditoría de login y sesión](docs/login-session-audit.md).

## Requisitos

- Node.js 22 (versión principal fijada en .nvmrc; package.json exige >=22.12 y <23).
- npm 10 u 11.
- Backend levantado y accesible desde el navegador.
- Usuario activo con los permisos correspondientes en el backend.

## Instalación y ejecución

~~~bash
npm ci
npm test
npm run build
npm run dev
~~~

Variables de Vite principales:

- VITE_API_BASE_URL: prefijo de la API, por ejemplo /api/v1 o /inventary/api/v1.
- VITE_BASE_PATH: base del router/artefacto, por ejemplo / o /inventary/.

El Nginx de Docker sirve la SPA bajo /inventary/ y reenvía /inventary/api/ al backend. Para probar la app fuera de Docker ajusta la URL de API al origen real y configura CORS en el backend.

## Pantallas y funcionalidades implementadas

| Área | Capacidades visibles |
|---|---|
| Acceso | Login con desafío/cifrado del cliente, sesión JWT, permisos, expiración, logout y fallback en memoria cuando Storage está bloqueado. La recuperación automática de contraseña y MFA aún no existen. |
| Dashboard | KPIs, ventas, utilidad, alertas, pendientes, existencias por almacén y movimientos; señal SSE que solicita refresco. |
| Catálogo | Productos, categorías, atributos, unidades, conversiones, galería de imágenes y variantes. |
| Contactos | Clientes/proveedores, almacenes, registro de contribuyentes y establecimientos; gestión fiscal visible según permisos de UI. |
| Inventario | Centro de mando, existencias, movimientos, operaciones, conteos, ajustes, transferencias y reposición. |
| Compras | Pestañas de órdenes, recepciones y devoluciones; filtros/paginación server-side para órdenes, recepciones y devoluciones. |
| Ventas | Pestañas de ventas, devoluciones y reembolsos; acciones de cancelación/aprobación. |
| Suscripciones | Listado paginado con filtros de estado, texto, almacén, próximas fechas y errores de facturación. |
| WhatsApp | Bandeja paginada, resumen operativo, acordeón de detalle y confirmación manual de pagos. |
| Reportes | Balance, ventas, compras, Kárdex, valoración, reposición y exportación CSV. |

La confirmación manual de pago en WhatsApp registra que un usuario autorizado verificó un medio externo; no conecta una pasarela ni acredita conciliación bancaria. La pantalla de reembolsos consume una API que todavía tiene un bloqueo crítico de autorización en backend; no debe considerarse segura para producción.

## Organización actual

- src/app/: entrada real, providers de sesión/permisos, router y workspace.
- src/pages/: adaptadores de pantalla por ruta; delegan en features.
- src/features/: workflows y vistas por dominio (auth, catalog, dashboard, inventory, operations, partners, purchasing, reports, returns, sales, subscriptions y whatsapp).
- src/entities/: modelo UI compartido de entidades, actualmente centrado en producto.
- src/shared/api/: cliente HTTP y módulos API por dominio; transporte común en http/request.js.
- src/shared/ui/, src/ui/: controles neutrales, tablas, overlays y feedback.
- src/components/, src/widgets/: piezas reutilizables y composición visual.
- src/security/, src/hooks/, src/utils/, src/styles.css: seguridad cliente, hooks, utilidades y estilos transversales.
- src/api.js y src/router.js: fachadas de compatibilidad; la implementación principal vive bajo app/ y shared/api/.

## Pruebas y evidencia

El script npm test ejecuta Node Test Runner. En la revisión actual, CI reportó **31 pruebas aprobadas**, build Vite correcto y smoke browser de login a 360/768/1280 px:

[Frontend Build 41394184467](https://github.com/JhonnyMinian92/inventary/actions/runs/41394184467). El backend también pasó Maven Verify, Compose config y smoke de arranque desde volumen vacío en [Backend CI 41394216160](https://github.com/JhonnyMinian92/inventary/actions/runs/41394216160).

Las pruebas unitarias cubren, entre otros, permisos del cliente, modelo de producto, router, cifrado de login, estado URL y exportación CSV. El smoke responsive comprueba login y desbordamiento/tamaño a tres anchuras; no ejecuta los flujos autenticados de compras, ventas, inventario, suscripciones o WhatsApp.

No hay script de lint declarado en package.json. Para validar una entrega ejecuta también los recorridos browser autenticados que se detallan en el plan de cierre del backend.

## Contrato de autorización

La visibilidad de botones y pestañas depende de permisos obtenidos del backend, pero ocultar una acción en React no es un control de seguridad. El backend debe rechazar por HTTP toda acción no autorizada. Consulta docs/api-coverage.md y docs/troubleshooting.md.