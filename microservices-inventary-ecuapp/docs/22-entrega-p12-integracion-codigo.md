> **Marcador de vigencia — 2026-10-26:** registro histórico de hito/entrega, no inventario vivo de rutas ni garantía de validación del HEAD actual. Consultar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia de APIs](25-referencia-apis.md), [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar comportamiento y permisos contra el código y pruebas del SHA que se despliega.

# P12 — Integración del código y entrega

## Propósito

Consolidar la entrega de las fases P01–P11 en una rama de integración reproducible, con PostgreSQL local/Docker, documentación de operación y un workflow de CI listo para ejecutar de forma explícita después de autorizar la validación.

## Contenido entregado en fuente

- La rama P12 parte de la rama P11 e incluye sus cambios de auditoría, errores RFC 9457, correlación y OpenAPI.
- Docker Compose usa PostgreSQL 16, espera a que la base esté saludable antes de iniciar la API y mantiene datos en un volumen nombrado.
- El Dockerfile construye con Maven/Java 21 y ejecuta el artefacto en una imagen JRE con usuario no root.
- El workflow `.github/workflows/backend-ci.yml` usa Java 21, PostgreSQL 16 y `mvn -B verify`; está configurado únicamente con `workflow_dispatch` para no iniciar CI automáticamente antes de la autorización de validación.
- Se consolida el procedimiento de entrega, la lista de seguridad, las condiciones de salida y las tareas pendientes.

## Entorno local

Requisitos: Java 21, Maven 3.9+ y PostgreSQL 15+; o Docker Engine con Docker Compose.

### Docker Compose

Desde `microservices-inventary-ecuapp/`:

1. Copiar `.env.example` a `.env`.
2. Cambiar las credenciales de ejemplo por valores exclusivos del entorno local.
3. Confirmar que `JWT_SECRET` tiene al menos 32 bytes y que la contraseña inicial de administrador es temporal y robusta.
4. Ejecutar manualmente `docker compose up --build` cuando se autorice la fase de ejecución.
5. API: `http://localhost:8080`; salud: `/actuator/health`. Swagger solo se habilita con `SWAGGER_ENABLED=true`.

El archivo `.env` está excluido del control de versiones. No reutilizar secretos de ejemplo ni publicar credenciales en logs o tickets.

### PostgreSQL local

Configurar `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` en el entorno. Flyway gestiona las migraciones al iniciar la aplicación; por eso no iniciar la API ni permitir migraciones hasta recibir autorización expresa.

## CI preparado

El workflow manual instala Temurin Java 21, levanta un servicio efímero PostgreSQL 16 con healthcheck y ejecuta `mvn -B verify`. Se eligió `workflow_dispatch` sin triggers automáticos para evitar ejecutar pruebas/CI antes de que el usuario autorice esa fase. Tras la autorización, lanzar el workflow desde GitHub Actions y corregir los fallos antes de crear una versión final.

## Lista de entrega

- [x] Código fuente de las fases P01–P11 incluido en la base de integración P12.
- [x] Configuración local y Docker Compose existente documentados.
- [x] Workflow de verificación con PostgreSQL preparado, sin ejecución automática.
- [x] Procedimiento de secretos, ejecución y publicación documentado.
- [ ] Ejecutar pruebas unitarias e integración con PostgreSQL.
- [ ] Ejecutar `mvn -B verify` y corregir cualquier fallo.
- [ ] Ejecutar el workflow manual y revisar sus resultados.
- [ ] Revisar diff/mergeabilidad, integrar a `master` y etiquetar una versión cuando la validación esté autorizada y aprobada.

## Condiciones de salida

P12 no debe marcarse como entrega validada hasta que las pruebas, la compilación y la verificación CI se hayan ejecutado y aprobado. La existencia de la rama o de este documento no equivale a una compilación exitosa ni garantiza el comportamiento en runtime.

## Alcance fuera de esta entrega

No incluye frontend, SRI/facturación electrónica, pagos/contabilidad, multiempresa, OIDC, ni consumidores Kafka adicionales. Estos requieren definición y trabajo separados.
