# Inventary — sistema de inventario

Inventary es una aplicación de **EcuApp** para administrar catálogo, compras, recepciones, existencias, ventas, devoluciones, traslados y reportes. El repositorio contiene una SPA React/Vite y una API transaccional Java/Spring Boot/PostgreSQL.

> © 2026 EcuApp. Software propietario, todos los derechos reservados. Ver [LICENSE](LICENSE).

> **Estado de entrega:** consulta los checks de GitHub Actions asociados al SHA exacto antes de desplegar. La existencia de workflows o pruebas no implica que el último commit ya haya sido validado.

## Componentes

- **Frontend:** `app-inventary-ecuapp` — React 19, Vite; consume REST mediante un cliente compartido.
- **Backend:** `microservices-inventary-ecuapp` — Java 21, Spring Boot, PostgreSQL, Flyway, JWT/RBAC, REST y GraphQL.
- **Arquitectura real:** monolito modular Spring Boot, no varios microservicios desplegables de forma independiente.
- **Persistencia:** PostgreSQL es la fuente transaccional; no actualizar saldos directamente ni usar `ddl-auto=update`.

## Inicio rápido con Docker

Requisitos: Docker Desktop y un PostgreSQL accesible desde el anfitrión con la base `inventary` creada (puede estar vacía: Flyway crea las tablas al arrancar).

```bash
cp .env.example .env
# Ajustar DB_USERNAME, DB_PASSWORD, JWT_SECRET y credenciales iniciales.
docker compose up --build -d
```

- Frontend: http://localhost:3000/inventary
- API: http://localhost:8080/api/v1 (publicada solo en 127.0.0.1).
- Swagger: http://localhost:8080/swagger-ui.html cuando `SWAGGER_ENABLED=true`.
- Para un PostgreSQL en contenedor usar `microservices-inventary-ecuapp/docker-compose.yml`.

El primer arranque crea el administrador configurado solo si no existen usuarios. Las operaciones sensibles de aprobación requieren los permisos y la segregación de funciones configurados en el backend.

## Inicio rápido sin Docker

Requisitos: Java 21, PostgreSQL compatible, Node.js 22.12.x y npm 10/11. Crear los archivos `.env` locales a partir de los ejemplos; nunca subir secretos.

```bash
cd microservices-inventary-ecuapp
./mvnw -B verify
./mvnw spring-boot:run
```

Configurar `SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS`. El secreto JWT debe ser aleatorio y tener al menos 32 bytes.

```bash
cd app-inventary-ecuapp
npm ci
npm run dev
```

Configurar `VITE_API_BASE_URL` con la URL base de la API. El lockfile versionado permite instalar exactamente las dependencias declaradas.

## Funcionalidad implementada

- Autenticación JWT, usuarios, roles y permisos.
- Productos, categorías, atributos dinámicos, unidades y conversiones.
- Proveedores, clientes y almacenes.
- Compras, aprobación, recepción parcial y devoluciones a proveedor.
- Ventas multítem, cancelación con aprobación y devoluciones de clientes.
- Saldos, disponibilidad, movimientos, ajustes, conteos y traslados.
- Costeo FIFO/promedio, ledger de valoración y reportes históricos.
- Catálogo público, GraphQL de consulta, integración WhatsApp/n8n y Outbox.
- Auditoría, errores Problem Details, limitación de solicitudes y Actuator.

La presencia de una implementación no sustituye la validación de cada commit. Los workflows ejecutan pruebas/build del frontend y Maven verify con PostgreSQL para el backend. Consulta el SHA y resultado de Actions antes de desplegar.

## Documentación

- [Estado actual por dominio y brechas](microservices-inventary-ecuapp/docs/24-estado-actual-repositorio.md)
- [Catálogo de APIs](microservices-inventary-ecuapp/docs/25-referencia-apis.md)
- [Arquitectura y patrones actuales](microservices-inventary-ecuapp/docs/26-arquitectura-y-patrones-actuales.md)
- [Guía de consumo de API](microservices-inventary-ecuapp/docs/27-guia-consumo-apis.md)
- [Plan de cierre y validación](microservices-inventary-ecuapp/docs/28-plan-cierre-y-validacion.md)
- [Índice de documentación backend](microservices-inventary-ecuapp/docs/README.md)
- [Cobertura de rutas frontend](app-inventary-ecuapp/docs/api-coverage.md)
- [Troubleshooting frontend](app-inventary-ecuapp/docs/troubleshooting.md)
- [Propuesta de arquitectura frontend](app-inventary-ecuapp/docs/PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md)
- [Propuesta de arquitectura backend](microservices-inventary-ecuapp/docs/PROPUESTA_JERARQUIA_ARQUITECTURA_BACKEND_V1.md)

Los documentos de auditoría con fechas y SHA históricos deben leerse como evidencia de su snapshot, no como una descripción automática del HEAD actual.

## Límites funcionales conocidos

La facturación electrónica SRI completa, la integración con pasarelas de pago/reembolsos, multiempresa, picking multi-bin, proyecciones analíticas Kafka y rate limit distribuido requieren implementación y validación específicas. Una cancelación operativa o una devolución de inventario no debe interpretarse por sí sola como anulación fiscal ni como reembolso monetario ejecutado.

## CI y release

Los workflows automatizan pruebas/build del frontend y `mvnw verify` con PostgreSQL para el backend. Antes de liberar, confirmar checks verdes del SHA exacto y probar el despliegue en el entorno objetivo.

## Licencia

Inventary es una aplicación de EcuApp. Copyright © 2026 EcuApp; titular de los derechos: JhonnyMinian92. Software propietario con todos los derechos reservados. Términos completos en [LICENSE](LICENSE).
