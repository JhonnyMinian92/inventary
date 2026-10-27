> **Marcador de vigencia — 2026-10-26:** registro del hito P12 actualizado el 2026-10-27; los resultados de validación deben comprobarse siempre contra el SHA enlazado en Actions. Consultar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia de APIs](25-referencia-apis.md), [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar comportamiento y permisos contra el código y pruebas del SHA que se despliega.

# P12 — Integración del código y entrega

## Propósito

Consolidar las fases P01–P11 en `master`, con PostgreSQL local/Docker, documentación de operación y workflows automáticos de verificación para frontend y backend.

## Contenido entregado en fuente

- La rama P12 parte de la rama P11 e incluye sus cambios de auditoría, errores RFC 9457, correlación y OpenAPI.
- Docker Compose usa PostgreSQL 16, espera a que la base esté saludable antes de iniciar la API y mantiene datos en un volumen nombrado.
- El Dockerfile construye con Maven/Java 21 y ejecuta el artefacto en una imagen JRE con usuario no root.
- El workflow raíz `.github/workflows/backend-ci.yml` usa Java 21, PostgreSQL 16 y `./mvnw -B --no-transfer-progress verify` en push/PR a `master`, además de permitir ejecución manual.
- Se consolida el procedimiento de entrega, la lista de seguridad, las condiciones de salida y las tareas pendientes.

## Entorno local

Requisitos: Java 21, Maven 3.9+ y PostgreSQL 15+; o Docker Engine con Docker Compose.

### Docker Compose

Desde `microservices-inventary-ecuapp/`:

1. Copiar `.env.example` a `.env`.
2. Cambiar las credenciales de ejemplo por valores exclusivos del entorno local.
3. Confirmar que `JWT_SECRET` tiene al menos 32 bytes y que la contraseña inicial de administrador es temporal y robusta.
4. Para API y PostgreSQL, ejecutar `docker compose up --build`; para levantar además el broker opcional, usar `docker compose --profile kafka up --build` con `KAFKA_ENABLED=true`.
5. API: `http://localhost:8080`; salud: `/actuator/health`. Swagger solo se habilita con `SWAGGER_ENABLED=true`.

El archivo `.env` está excluido del control de versiones. No reutilizar secretos de ejemplo ni publicar credenciales en logs o tickets.

### PostgreSQL local

Configurar `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` en el entorno. Flyway aplica migraciones versionadas en el inicio. No ejecute migraciones sobre una base de datos con datos importantes sin respaldo y un plan de recuperación.

## CI preparado

El workflow de backend instala Temurin Java 21, levanta PostgreSQL 16 con healthcheck, ejecuta `./mvnw -B --no-transfer-progress verify` y publica reportes incluso cuando falla. El workflow de frontend ejecuta `npm test` y `npm run build`.

## Lista de entrega

- [x] Código fuente de las fases P01–P11 incluido en la base de integración P12.
- [x] Configuración local y Docker Compose existente documentados.
- [x] Workflow de verificación de backend con PostgreSQL ejecutado automáticamente en push/PR.
- [x] Pruebas frontend automatizadas y compilación de producción integradas en CI.
- [x] Migración de inbox Kafka y flujo de carga de imagen implementados con pruebas de integración.
- [ ] Validar el despliegue completo de Compose (API, PostgreSQL y perfil Kafka) en un entorno operacional antes de etiquetar un release.

## Condiciones de salida

P12 no debe marcarse como entrega validada hasta que las pruebas, la compilación y la verificación CI se hayan ejecutado y aprobado. La existencia de la rama o de este documento no equivale a una compilación exitosa ni garantiza el comportamiento en runtime.

## Alcance fuera de esta entrega

Fuera del alcance: facturación electrónica SRI completa, pasarela de pagos/reembolsos, multiempresa, picking multi-bin y proyecciones analíticas Kafka. El consumidor actual ofrece idempotencia e invalidación de caché, no una plataforma de proyecciones.
