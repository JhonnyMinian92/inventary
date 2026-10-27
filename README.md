# Inventary — sistema de inventario

Inventary es una aplicación web para administrar catálogo, compras, recepciones, existencias, ventas, devoluciones, traslados y reportes. El repositorio contiene una SPA React/Vite y una API transaccional Java/Spring Boot/PostgreSQL.

> **Estado documental al 26-10-2026:** el código es amplio, pero no se declara listo para producción hasta verificar build, pruebas, migraciones, Docker Compose y CI contra el SHA exacto del release. Las ejecuciones históricas no validan automáticamente commits posteriores.

## Componentes

- **Frontend:** app-inventary-ecuapp — React, Vite; consume REST a través de src/api.js.
- **Backend:** microservices-inventary-ecuapp — Java 21, Spring Boot 3.5.6, PostgreSQL, Flyway V1–V15, JWT/RBAC, REST, GraphQL, carga de imágenes y consumidor Kafka opcional.
- **Arquitectura real:** monolito modular Spring Boot, no varios microservicios desplegables de forma independiente.
- **Persistencia:** PostgreSQL es la fuente transaccional; no actualizar saldos directamente ni usar ddl-auto=update.

## Inicio rápido

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
