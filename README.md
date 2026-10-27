# Inventary — sistema de inventario

Inventary es una aplicación web para administrar catálogo, compras, recepciones, existencias, ventas, devoluciones, traslados y reportes. El repositorio contiene una SPA React/Vite y una API transaccional Java/Spring Boot/PostgreSQL.

> **Estado documental al 26-10-2026:** el código es amplio, pero no se declara listo para producción hasta verificar build, pruebas, migraciones, Docker Compose y CI contra el SHA exacto del release. Las ejecuciones históricas no validan automáticamente commits posteriores.

## Componentes

- **Frontend:** app-inventary-ecuapp — React, Vite; consume REST a través de src/api.js.
- **Backend:** microservices-inventary-ecuapp — Java 21, Spring Boot 3.5.6, PostgreSQL, Flyway V1–V14, JWT/RBAC, REST y GraphQL.
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

La presencia de una implementación no equivale a validación del SHA actual; leer el estado de verificación por área en la documentación.

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

No están implementados en esta versión: facturación electrónica SRI/impuestos completos, pasarela de pago y reembolso, multiempresa, stock independiente por variante, picking multi-bin, carga binaria de imágenes, consumidores/proyecciones Kafka y rate limit distribuido. El frontend no cubre todas las APIs administrativas/operativas. Consultar el estado actual antes de ofrecer capacidades.

## CI y release

Los workflows del árbol de referencia son manuales. Para aceptar un release, automatizar build/tests para push y pull_request, exigir checks verdes, ejecutar backend contra PostgreSQL 16 y proteger master. No declarar Docker Compose validado hasta ejecutarlo. Toda afirmación de “validado” debe citar SHA, versiones, comando y resultado.
