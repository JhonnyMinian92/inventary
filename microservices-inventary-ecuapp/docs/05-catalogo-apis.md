# 05 — Catálogo de APIs: índice del contrato actual

**Fuente contractual vigente:** [25 — Referencia actual de APIs](25-referencia-apis.md). Ese documento enumera las rutas presentes en los controladores del código observado, las autoridades de acceso, ejemplos, filtros, errores, idempotencia y exclusiones.

Este archivo se mantiene como el punto de entrada histórico al tema. Parte de la tabla propuesta original describía rutas objetivo que no coinciden con el contrato implementado actualmente (por ejemplo, endpoints de borrador/confirmación, ubicaciones o barcode que no deben asumirse existentes). No consumir rutas copiadas de las tablas antiguas sin comprobarlas en el controlador del SHA que se despliega.

## Fuentes por detalle

- Rutas/métodos y permisos: [25 — Referencia actual](25-referencia-apis.md).
- Campos exactos de entrada/salida: DTOs bajo src/main/java/com/ecuapp/inventary y OpenAPI del mismo build si SWAGGER_ENABLED está habilitado.
- Flujos de negocio: [06 — Flujos de negocio y API](06-flujos-de-negocio-y-api.md).
- JWT, RBAC y controles de seguridad: [07 — Seguridad](07-seguridad-y-definicion-de-hecho.md).
- Guía de llamadas reproducibles: [27 — Guía de consumo](27-guia-consumo-apis.md).
- Estado y verificación: [24 — Estado actual](24-estado-actual-repositorio.md).

## Convenciones que aplican al código actual

- REST base local: http://localhost:8080/api/v1.
- JSON UTF-8; Authorization: Bearer para usuario autenticado; X-Integration-Key para la integración de máquina.
- Rutas sensibles verifican permisos en el backend; el control de frontend solo es UX.
- Reintentar comandos únicamente con la misma Idempotency-Key y el mismo payload cuando el endpoint la soporte.
- Los errores del handler común usan application/problem+json y requestId; no incluyen stack trace ni SQL.
- No confiar en payload del cliente para precios de venta, stock, permisos o identidad de WhatsApp.
- Los reportes financieros exigen reports:financial y deben interpretarse usando la zona horaria y las fechas de la respuesta.
