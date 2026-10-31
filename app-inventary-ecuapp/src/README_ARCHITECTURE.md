# Arquitectura del frontend

Se conserva React 19, Vite 8, JavaScript/JSX y Node 22. Las fachadas `src/api.js`, `src/router.js` y `src/App.jsx` preservan compatibilidad para los módulos y pruebas existentes.

## Límites actuales

- `app/`: composición, providers de sesión, router y entrada de estilos.
- `shared/api/http/`: almacenamiento de token, transporte HTTP, errores e idempotencia.
- `shared/api/<dominio>/`: operaciones REST por autenticación, catálogo, socios, inventario, reportes, compras, ventas, suscripciones y WhatsApp.
- `shared/styles/`: tokens visuales globales.
- `features/`: workflows y pantallas por dominio.
- `components/`: componentes reutilizables existentes; los componentes que conocen entidades se migrarán al dominio cuando haya un límite comprobable.
- `ui/`: overlays, feedback y componentes visuales neutrales.

## Reglas

- `AuthProvider` administra sesión, usuario, permisos y expiración; nunca catálogos ni datos transaccionales.
- Los servicios API de dominio dependen del transporte común, no de otros dominios.
- Las fachadas mantienen el contrato actual para evitar cambios masivos simultáneos.
- Los permisos de UI solo controlan visibilidad; el backend conserva la autorización.
- Los errores 401/403 no se convierten en listas vacías ni cifras cero.
- No se incorporan dependencias nuevas por reorganizar carpetas.
