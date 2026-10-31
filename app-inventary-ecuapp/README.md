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

## Arquitectura del frontend

El frontend está organizado en capas con dependencias dirigidas hacia módulos de menor nivel:

- `src/main.jsx`: bootstrap de React, migración de URL legacy y estilos globales.
- `src/app/`: composición raíz, providers, router, navegación, shell y controlador del espacio de trabajo.
- `src/pages/`: entradas de página por ruta; componen widgets y features, sin implementar acceso HTTP.
- `src/widgets/`: composiciones de pantalla y overlays del workspace.
- `src/features/`: casos de uso de catálogo, ventas, compras, inventario, reportes, suscripciones, WhatsApp y dashboard.
- `src/entities/`: selectores/modelos de producto y transacción, picker de productos y detalles de documentos.
- `src/shared/ui/`: primitivas de tablas, formularios, stepper, paginación, marca, feedback y diálogos.
- `src/shared/lib/`, `src/shared/hooks/` y `src/shared/styles/`: URL state, utilidades, hooks accesibles y tokens visuales.
- `src/shared/api/` y `src/api.js`: núcleo HTTP por dominio y fachada de compatibilidad.
- `src/security/`: cifrado del login y controles relacionados con credenciales/sesión.
- `src/utils/`: formato, exportación y utilidades puras que aún están en migración.
- `docs/frontend-roadmap.md`: alcance, decisiones y criterios de salida.
- `docs/api-coverage.md`: matriz de consumo de API.
- `docs/troubleshooting.md`: configuración y resolución de errores.

Las carpetas antiguas `components/`, `hooks/` y `ui/` conservan fachadas de compatibilidad hacia las implementaciones canónicas para evitar cambios masivos de imports. No se deben añadir implementaciones nuevas en esas fachadas. La capa `features/` conserva módulos de negocio existentes y es responsable de sus estados/acciones; el controlador de `app/model/` solo coordina navegación y delega datos/comandos a hooks dedicados.

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

## Decisiones de arquitectura

La jerarquía implementada sigue y concreta la propuesta feature-first descrita en [PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md](docs/PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md). Las fachadas antiguas son transitorias y se mantienen solo por compatibilidad. Los módulos canónicos nuevos deben respetar las dependencias app → pages/widgets → features/entities → shared; las features llaman al cliente REST por dominio y nunca acceden a PostgreSQL ni aplican reglas de inventario en el navegador.

El contrato del backend y su OpenAPI siguen siendo la fuente de verdad para DTOs y permisos. No reemplazar respuestas reales por mocks para ocultar errores de integración.
