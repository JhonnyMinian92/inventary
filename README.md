# Inventary — sistema de inventario

Inventary es una aplicación de **EcuApp** para administrar catálogo, compras, recepciones, existencias, ventas, devoluciones, traslados y reportes. El repositorio contiene una SPA React/Vite y una API transaccional Java/Spring Boot/PostgreSQL.

> © 2026 EcuApp. Software propietario, todos los derechos reservados. Ver [LICENSE](LICENSE).

> **Estado de entrega al 27-10-2026:** el frontend dispone de pruebas automáticas y build en GitHub Actions; backend ejecuta Maven verify contra PostgreSQL 16 y ahora incluye validación de configuración Compose más un smoke test desde un volumen PostgreSQL vacío. Antes de desplegar, comprueba que el workflow más reciente termina en verde para el último commit de código.

## Componentes

- **Frontend:** app-inventary-ecuapp — React, Vite; consume REST a través de src/api.js.
- **Backend:** microservices-inventary-ecuapp — Java 21, Spring Boot 3.5.6, PostgreSQL, Flyway V1–V15, JWT/RBAC, REST, GraphQL, carga de imágenes y consumidor Kafka opcional.
- **Arquitectura real:** monolito modular Spring Boot, no varios microservicios desplegables de forma independiente.
- **Persistencia:** PostgreSQL es la fuente transaccional; no actualizar saldos directamente ni usar ddl-auto=update.

## Inicio rápido con Docker (frontend + backend)

Requisitos: Docker Desktop y un PostgreSQL accesible desde el anfitrión con la base `inventary` ya creada (puede estar vacía: Flyway crea las tablas al arrancar).

~~~bash
cp .env.example .env   # ajustar DB_USERNAME, DB_PASSWORD, JWT_SECRET y el usuario inicial
docker compose up --build -d
~~~

- Frontend: http://localhost:3000/inventary (nginx sirve la SPA bajo `/inventary/` y reenvía `/inventary/api/` al backend; no requiere CORS). La raíz y cualquier ruta fuera del prefijo redirigen a `/inventary/`.
- Rutas de la SPA: `/inventary/iniciar-sesion`, `/inventary/resumen`, `/inventary/productos[/nuevo|/{id}/editar]`, `/inventary/categorias`, `/inventary/compras[/nueva|/{id}|/{id}/recepcion|/devoluciones/nueva]`, `/inventary/ventas[/nueva|/{id}|/devoluciones/nueva]`, `/inventary/inventario[/traslados|/conteos|/ajustes/nuevo]`, `/inventary/reportes`. Sin sesión, cualquier ruta lleva al login con `?destino=` y vuelve allí tras autenticarse. Los enlaces antiguos `#/...` se convierten automáticamente.
- API: http://localhost:8080/api/v1 (publicada solo en 127.0.0.1) — Swagger en http://localhost:8080/swagger-ui.html con `SWAGGER_ENABLED=true`.
- Login: las credenciales se cifran en el navegador (ECDH P-256 + HKDF-SHA256 + AES-256-GCM con desafío de un solo uso) además de TLS; WebCrypto exige HTTPS o `localhost`. Tras 5 fallos la cuenta se bloquea 15 minutos. "Mantener sesión iniciada" guarda el token en `localStorage`; sin marcar, en `sessionStorage`.
- La API se conecta a `host.docker.internal:5432`; cambiar `SPRING_DATASOURCE_URL` en `.env` si la BD está en otro host.
- El primer arranque crea `BOOTSTRAP_ADMIN_USERNAME` solo si no existen usuarios. Aprobar compras y anulaciones de venta exige un segundo usuario con rol `SUPER_ADMIN` (`POST /api/v1/admin/users`).
- Para un PostgreSQL en contenedor usar `microservices-inventary-ecuapp/docker-compose.yml`.

### n8n (ventas por WhatsApp)

El mismo `docker compose up -d` levanta n8n en http://localhost:5678 (datos en el volumen `inventary-n8n-data`). Requiere `N8N_ENCRYPTION_KEY` y `WHATSAPP_INTEGRATION_API_KEY` en `.env` (ver `.env.example`).

- Desde n8n, la API se consume por la red interna: `http://api:8080/api/v1/integrations/whatsapp` (no usar `localhost`, que dentro del contenedor es el propio n8n).
- En n8n crear una credencial **Header Auth** con nombre `X-Integration-Key` y el valor de `WHATSAPP_INTEGRATION_API_KEY`; usarla en los nodos HTTP Request.
- Flujo de venta: catálogo → `POST /carts` → `PUT /carts/{id}/items/{productId}` → `POST /carts/{id}/quotes` → `POST /orders` (`consentConfirmed: true`). Cotización y pedido requieren header `Idempotency-Key`. El pago lo confirma el backoffice en `/api/v1/whatsapp-orders/**`, que genera la venta.
- Meta (WhatsApp Cloud API) solo envía webhooks a HTTPS público: exponer n8n con un túnel (cloudflared/ngrok) o dominio y fijar `N8N_WEBHOOK_URL` y `N8N_PROXY_HOPS=1`.

## Inicio rápido sin Docker

Requisitos: Java 21, PostgreSQL compatible, Node.js 22.12.x, npm 10/11. Crear .env local a partir de .env.example; nunca subir secretos.

~~~bash
cd microservices-inventary-ecuapp
./mvnw -B verify
./mvnw spring-boot:run
~~~

Configurar SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS y credenciales de bootstrap. JWT_SECRET debe ser aleatorio y tener al menos 32 bytes. API local: http://localhost:8080/api/v1.

~~~bash
cd app-inventary-ecuapp
cp .env.example .env
npm install
npm run dev
~~~

VITE_API_BASE_URL define el prefijo del backend. CORS_ALLOWED_ORIGINS debe incluir el origen de Vite. No se encontró package-lock.json en el árbol de referencia: generar y versionar un lockfile antes de la instalación reproducible en CI.

## Funcionalidad en código

- Autenticación JWT, identidad, usuarios, roles y permisos.
- Productos, categorías, atributos dinámicos, unidades y conversiones.
- Proveedores, clientes y almacenes.
- Compras, envío/aprobación, recepción parcial y devoluciones a proveedor.
- Ventas multítem, cancelación con aprobación, devoluciones de cliente.
- Saldos, disponibilidad, movimientos, ajustes, conteos y traslados.
- Costeo FIFO/promedio, ledger de valoración y reportes históricos.
- Catálogo público, GraphQL de consulta, integración WhatsApp/n8n y Outbox.
- Auditoría, errores Problem Details, limitación de solicitudes y Actuator.

La presencia de una implementación no sustituye la validación de cada commit. Los checks de frontend ejecutan `npm test` y `npm run build`; el backend ejecuta `./mvnw verify` contra PostgreSQL 16. Consulta el SHA y resultado de Actions antes de desplegar.

## Documentación autoritativa

- [Estado actual por dominio y brechas](microservices-inventary-ecuapp/docs/24-estado-actual-repositorio.md)
- [Catálogo actual de APIs, rutas y permisos](microservices-inventary-ecuapp/docs/25-referencia-apis.md)
- [Jerarquía, arquitectura y patrones reales](microservices-inventary-ecuapp/docs/26-arquitectura-y-patrones-actuales.md)
- [Guía reproducible de consumo de API](microservices-inventary-ecuapp/docs/27-guia-consumo-apis.md)
- [Riesgos, próximos pasos y Definition of Done](microservices-inventary-ecuapp/docs/28-plan-cierre-y-validacion.md)
- [Índice de la documentación backend](microservices-inventary-ecuapp/docs/README.md)
- [Runbook operativo](microservices-inventary-ecuapp/docs/23-runbook-operacion.md)
- [Cobertura de rutas del frontend](app-inventary-ecuapp/docs/api-coverage.md)
- [Troubleshooting frontend](app-inventary-ecuapp/docs/troubleshooting.md)
- [Auditoría histórica](microservices-inventary-ecuapp/AUDITORIA_TECNICA_PENDIENTES.md)
- [Evidencia histórica de ejecución backend](microservices-inventary-ecuapp/docs/RESULTADO_AUDITORIA_EJECUCION.md)

## Límites de alcance

Fuera del alcance de esta versión: facturación electrónica SRI completa, pasarela de pago/reembolso, multiempresa, picking multi-bin, proyecciones analíticas Kafka y rate limit distribuido. Se incorporó carga binaria segura PNG/JPEG y un consumidor Kafka idempotente para invalidación de caché; el consumidor y el broker permanecen deshabilitados por defecto. El frontend aún no cubre todos los endpoints administrativos/operativos.

## CI y release

Los workflows del repositorio automatizan el build/test de frontend y `mvnw verify` con PostgreSQL 16 en push/PR a `master`. Antes de liberar, confirmar checks verdes del SHA exacto y probar el perfil Docker Kafka cuando se habilite; el CI de base de datos no sustituye pruebas de despliegue en el entorno objetivo.

## Licencia

Inventary es una aplicación de EcuApp. Copyright © 2026 EcuApp; titular de los derechos: JhonnyMinian92. Software propietario con todos los derechos reservados: no se permite copiar, modificar, distribuir ni explotar el código sin autorización escrita del titular. Términos completos en [LICENSE](LICENSE). El logotipo de EcuApp (`app-inventary-ecuapp/src/assets/ecuapp-logo.png`) y la marca Inventary forman parte de esos derechos.
