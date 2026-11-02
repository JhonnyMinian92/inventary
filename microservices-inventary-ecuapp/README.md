# Inventary Backend

API transaccional de inventario y ventas. Implementada con Java 21, Spring Boot 3.5.6, Spring MVC, Spring Data JPA, Spring Security, PostgreSQL y Flyway.

**Código auditado:** implementacion_propuestas · SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481.

La aplicación es un monolito modular: los dominios REST/GraphQL comparten proceso, seguridad, transacciones y base PostgreSQL. No son microservicios desplegados individualmente.

## Requisitos y ejecución

- Java 21.
- PostgreSQL 16 para reproducir CI.
- Docker Engine/Compose opcionales.

### Verificación local

~~~bash
./mvnw -B --no-transfer-progress verify
~~~

### Ejecutar contra PostgreSQL existente

Configura SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET y CORS_ALLOWED_ORIGINS. El secreto JWT debe ser aleatorio y tener al menos 32 bytes.

~~~bash
./mvnw spring-boot:run
~~~

El arranque inicial también requiere BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD cuando la base aún no tiene usuarios. La contraseña inicial respeta 8–15 caracteres, mayúscula, minúscula, número y símbolo.

Flyway aplica migraciones incrementales V1–V28. Hibernate usa ddl-auto=validate; no se debe cambiar a update ni editar migraciones históricas ya aplicadas. Para cualquier cambio de esquema se crea una migración nueva.

### Docker Compose del backend

Desde este directorio utiliza el Compose del backend, que administra API y PostgreSQL. Kafka está detrás de configuración opt-in y permanece deshabilitado por defecto.

~~~bash
docker compose config
docker compose up --build -d
~~~

El Compose raíz del repositorio es diferente: levanta frontend, API y n8n contra PostgreSQL accesible desde el anfitrión; no arranca un contenedor PostgreSQL propio.

## Dominios implementados

- Seguridad: login JWT HS256, desafío de cifrado de credenciales, revocación por versión de token, RBAC y scopes por almacén/contribuyente.
- Catálogo: productos, categorías, atributos configurables, variantes, unidades, conversiones e imágenes.
- Partners/almacenes: clientes, proveedores, contactos, bodegas, establecimientos y registro tributario manual.
- Inventario: saldos, movimientos, ajustes, conteos maker-checker, coste FIFO/promedio, valoración, reposición y transferencias compensables.
- Compras: órdenes, envío/aprobación, recepciones parciales y devoluciones a proveedor.
- Ventas: ventas transaccionales, stock/costeo, cancelación con aprobación y devoluciones físicas.
- Suscripciones: calendario de cuotas y facturación interna.
- WhatsApp: API de integración con X-Integration-Key, carritos/cotizaciones/pedidos y backoffice de confirmación manual.
- Reportes: balance histórico, ventas, compras, Kárdex, valoración y bajo mínimo.
- Auditoría y eventos: eventos append-only, correlación, Outbox/Inbox y diagnósticos de Outbox.
- Integraciones V28: operaciones encoladas, suscripciones de webhook y registros de entrega.

## Nuevas capacidades de la rama

- GET /api/v1/inventory/command-center: agregados y alertas para el centro de mando de inventario.
- GET /api/v1/dashboard/events: SSE autenticado; el servidor emite señales periódicas y el cliente vuelve a consultar los recursos protegidos.
- GET /api/v1/whatsapp-orders/work-summary: contadores de la bandeja operativa WhatsApp.
- GET /api/v1/goods-receipts/{id}/returnable-lines: cálculo server-side de cantidades recibidas, devueltas y restantes.
- /api/v1/integration-operations: crear, listar, consultar y reintentar operaciones con permiso integration.
- /api/v1/webhook-subscriptions: crear, listar y desactivar suscripciones.

La presencia de tablas de integración no demuestra que exista un worker externo completo. En este corte no se localiza un despachador que ejecute operaciones encoladas y entregue todas las filas de webhook_delivery con reintento/conciliación de extremo a extremo.

## Contrato de seguridad: advertencia importante

La mayoría de los dominios se protege mediante reglas de SecurityConfig, permisos y scopes. Sin embargo, en el SHA auditado, SecurityConfig contiene permitAll para:
- /api/v1/tax/**
- /api/v1/refunds/**

Los controladores TaxController y RefundController no agregan @PreAuthorize por método/clase. Estas reglas no están limitadas a un perfil local; por lo tanto, las operaciones de escritura fiscales y financieras quedan accesibles sin autenticación en cualquier perfil que use esa SecurityConfig. Esto es un bloqueo P0 para exposición de red/producción. No confiar en el control de la UI ni en documentación de permisos hasta resolverlo.

Además:
- La ruta WhatsApp de integración es permitAll en la cadena JWT, pero sus métodos validan X-Integration-Key.
- El registro de contribuyentes utiliza permisos taxpayer:read/manage y comprobaciones de scope.
- El rate limit es local por instancia; no sustituye un gateway/WAF distribuido.

## Límites funcionales

- No hay proveedor SRI aprobado ni emisión, firma, autorización o anulación real de comprobantes electrónicos.
- El registro tributario es importación manual y revisión humana.
- El cálculo de tarifa tributaria existente no sustituye asesoría fiscal ni comprobante autorizado; el snapshot tributario histórico por línea/venta no está completo.
- No existe un libro de pagos y conciliación bancaria completo ni pasarela de pago.
- La entidad de reembolso es inicial; faltan protección de rutas, límites acumulados y enlace a importe efectivamente liquidado.
- Los pedidos WhatsApp se confirman por personal autorizado, no por confirmación de gateway.
- No hay aislamiento multiempresa/tenant, multi-bin, stock independiente por variante ni WMS completo.
- El consumidor Kafka actual invalida la caché del catálogo público; Kafka continúa opt-in y no crea proyecciones analíticas generales.
- La operación de integration_operation/webhook_delivery no tiene todavía un trabajador completo con transporte externo verificado.

## Pruebas y CI

[Backend CI 41385132612](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132612) para el SHA de código auditado:
- Maven Verify sobre PostgreSQL 16.
- 119 pruebas, 0 fallos, 0 errores.
- Validación de configuración Docker Compose backend normal y perfil Kafka.
- Smoke test de arranque de API/backend desde volumen PostgreSQL vacío.

[Documentación técnica vigente](docs/README.md) · [Referencia de APIs](docs/25-referencia-apis.md) · [Estado verificable](docs/24-estado-actual-repositorio.md) · [Plan de cierre](docs/28-plan-cierre-y-validacion.md).