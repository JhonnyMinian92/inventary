# Arquitectura implementada del frontend

Este documento describe el árbol actual, no una arquitectura objetivo.

## Composición

- app/: bootstrap React, providers de sesión/permisos, router y workspace.
- pages/: pantallas enrutadas delgadas; delegan la lógica de dominio en features.
- features/: pantallas y workflows organizados por contexto funcional.
- entities/: modelos y presentación compartidos de entidades; actualmente contiene la primera frontera de producto.
- shared/api/http/: transporte HTTP, token, errores e idempotencia.
- shared/api/<dominio>/: operaciones REST por autenticación, catálogo, inventario, socios, compras, reportes, ventas, suscripciones y WhatsApp.
- shared/ui/, ui/: controles neutrales, tablas, overlays y feedback.
- components/, widgets/: piezas visuales y composición reutilizable.
- security/, hooks/, utils/: seguridad del cliente, hooks y utilidades.

## Reglas de dependencia

1. Las features consumen contratos de API compartidos; no deben recrear fetch, autorización ni manejo de errores.
2. Los servicios API de dominio dependen del transporte común, no de componentes React.
3. Los permisos de UI controlan visibilidad y affordances; la autorización definitiva vive en backend.
4. Los errores 401/403 se mantienen diferenciados; no deben transformarse en listas vacías o importes cero.
5. Las operaciones transaccionales usan claves de idempotencia donde la API lo exige. Un reintento con resultado ambiguo debe consultar primero el estado en servidor.
6. Las fachadas src/api.js y src/router.js se mantienen por compatibilidad, no como nuevos puntos de implementación.

## Límite conocido

La separación por features está aplicada, pero el modelo compartido entities aún no representa todos los dominios como paquetes independientes. No afirmar que el frontend tiene una arquitectura hexagonal completa: es una SPA modular en transición.