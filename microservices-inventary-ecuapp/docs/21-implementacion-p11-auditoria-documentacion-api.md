> **Marcador de vigencia — 2026-10-26:** registro histórico de hito/entrega, no inventario vivo de rutas ni garantía de validación del HEAD actual. Consultar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia de APIs](25-referencia-apis.md), [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar comportamiento y permisos contra el código y pruebas del SHA que se despliega.

# P11 — Auditoría, documentación y API

## Alcance implementado en fuente

- Los errores inesperados se registran en el log del servidor con método, ruta y requestId. La respuesta del cliente conserva RFC 9457 y no expone mensajes internos ni trazas.
- Un filtro registra fallos HTTP de rutas `/api/**` en `audit_event` con actor disponible, acción, tipo de recurso, resultado, correlación y metadatos limitados a método/ruta/estado.
- La auditoría de fallos usa una transacción independiente y es best-effort: un fallo al guardar la auditoría no reemplaza ni oculta la respuesta original.
- `GET /api/v1/audit-events` permite filtros exactos por actor, acción, tipo de recurso, resultado y rango temporal; limita la página a 100 eventos y ordena por fecha descendente. Requiere `audit:read`.
- OpenAPI añade descripción del contrato, seguridad Bearer JWT para rutas privadas, `X-Integration-Key` para rutas WhatsApp y respuestas comunes 400/401/403/409/422/500. Las rutas reales se descubren desde los controladores.
- Se actualizan el catálogo de APIs, la guía operativa y el índice documental.

## Correlación y error

`X-Request-ID` se devuelve en la respuesta. Si el valor entrante no cumple `[A-Za-z0-9._:-]{1,80}`, se genera un UUID nuevo. Los errores `application/problem+json` incluyen el mismo `requestId` cuando existe.

Los fallos HTTP se auditan sin leer cuerpos ni parámetros de consulta y sin guardar Authorization, cookies, claves de integración ni contraseñas. Los detalles técnicos de excepciones inesperadas quedan en el servidor; la respuesta externa es genérica.

## Seguridad y límites

- La bitácora es de solo lectura por la API y exige `audit:read`.
- Los filtros son exactos y parametrizados; tamaño de página máximo: 100.
- No se añaden SRI, pagos/contabilidad, frontend ni OIDC; JWT HS256 sigue siendo el mecanismo del MVP.
- Kafka/GraphQL/WhatsApp no se amplían fuera de la documentación necesaria para describir la seguridad del contrato existente.

## Variables y operación

Variables principales: `JWT_SECRET` (mínimo 32 bytes), `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`, `SWAGGER_ENABLED`, `WHATSAPP_INTEGRATION_API_KEY` y las variables Kafka opcionales. No reutilizar credenciales de ejemplo en entornos compartidos. OpenAPI/Swagger permanece deshabilitado por defecto.

## Validación

Se modificó únicamente el código fuente y la documentación. No se ejecutaron pruebas, build/compilación, CI, arranque, comprobaciones de entorno ni migraciones, conforme a la secuencia acordada.
