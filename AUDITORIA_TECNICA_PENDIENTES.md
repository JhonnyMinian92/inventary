# Auditoría técnica de Inventary — pendientes, defectos y plan de cierre

> **Objetivo:** documento operativo para que un agente local audite, complete, corrija y pruebe el backend y sus APIs REST/GraphQL hasta obtener una entrega reproducible.
>
> **Repositorio:** `JhonnyMinian92/inventary`  
> **Rama examinada:** `master`  
> **Commit del árbol de código examinado:** `b310bd511f7663ff9d94c2602ce002e5ed36c8b8`  
> **Alcance del commit:** snapshot P12 integrado en `master` antes de añadir este informe. La auditoría se publica en `master` en un commit posterior; las referencias de código y hallazgos corresponden al SHA anterior.  
> **Alcance observado:** backend Java 21 / Spring Boot 3.5.6, PostgreSQL, Flyway, Security/JWT/RBAC, REST, GraphQL, integración WhatsApp/n8n, Outbox/Kafka opcional, Docker Compose y documentación.  
> **Tipo de revisión:** inspección estática de archivos del repositorio. **No se ejecutaron** build, tests, CI, aplicación, migraciones ni comprobaciones de entorno durante esta auditoría.

## 1. Resumen ejecutivo

La rama contiene una base de backend considerable: catálogo, usuarios y RBAC, almacenes, saldos y movimientos, ajustes/conteos, compras/recepciones/devoluciones, ventas y cancelación con aprobación, transferencias, auditoría, imágenes por URL, cotización/carritos/pedidos WhatsApp y un publicador Outbox/Kafka opcional. Hay buenas decisiones de integridad y seguridad, descritas abajo.

**Estado recomendado: NO declarar listo para producción ni cerrar entrega hasta completar las tareas P1, ejecutar la matriz de pruebas y guardar evidencias.** Esto no significa que el backend entero esté roto: significa que no existe evidencia de ejecución y todavía hay funcionalidades documentadas como alcance futuro, cobertura de pruebas insuficiente y algunos desajustes de configuración/contrato visibles en código.

### Hechos conocidos

- El árbol revisado contiene alrededor de **178 archivos Java de producción**, **9 archivos/clases de test** y **13 migraciones Flyway V1–V13**.
- Los tests de integración existentes cubren algunas rutas de servicios, pero no hay una suite de pruebas HTTP amplia que atraviese controladores, autenticación JWT, autorización RBAC, filtros, serialización y códigos HTTP.
- El test de carga de contexto (`InventaryApplicationTests`) necesita la configuración y la base de datos de la aplicación. No se puede inferir que pasa porque exista el archivo.
- El workflow `.github/workflows/backend-ci.yml` está limitado intencionalmente a `workflow_dispatch`; está preparado, pero no hay resultado de CI asociado a esta auditoría.
- El workspace `app-inventary-ecuapp/` es un placeholder (README corto y `docs/context.md`); no se observó implementación de frontend Angular en el árbol de `master` examinado.
- El inventario de ramas remoto se revisó para esta consolidación: las ramas P01–P11 y `fix/p02-require-jwt-secret` están detrás de `master` sin commits exclusivos; la única rama de integración con contenido exclusivo era P12, cuyo commit `b310bd511f7663ff9d94c2602ce002e5ed36c8b8` se incorporó a `master` como snapshot base de esta auditoría.
- Algunas capacidades son explícitamente pendientes según `microservices-inventary-ecuapp/README.md`: pagos, facturación electrónica/SRI, reglas fiscales, carga binaria de imágenes, consumidor/proyecciones Kafka, reportes, política de valoración completa, multiempresa y mayor cobertura de concurrencia.

## 2. Cómo debe trabajar el agente local

1. **Preservar la evidencia:** empezar en la rama/commit que se pretende entregar y registrar `git status`, rama y SHA antes de cambios. No sobrescribir cambios locales del usuario.
2. **Leer este documento junto con** `microservices-inventary-ecuapp/docs/README.md`, `docs/02-requisitos-y-alcance.md`, `docs/04-modelo-dominio-y-reglas.md`, `docs/05-catalogo-apis.md`, `docs/07-seguridad-y-definicion-de-hecho.md` y los documentos P03–P12. Diferenciar requisito aprobado de visión futura.
3. **Corregir código y añadir tests primero.** No alterar migraciones históricas ya aplicadas; crear nuevas migraciones incrementales cuando haga falta. No usar `ddl-auto=update` ni destruir la base para lograr que arranque.
4. **No “arreglar” tests eliminando aserciones, desactivando seguridad, excluyendo pruebas o sustituyendo PostgreSQL por H2 sin justificar equivalencia.** Para SQL, locks, JSONB y triggers se necesita PostgreSQL real (por ejemplo, Testcontainers o servicio PostgreSQL aislado).
5. Ejecutar primero pruebas unitarias, luego integración PostgreSQL, luego HTTP/security/contract, concurrencia, y al final `mvn -B verify` y CI manual. Registrar comandos exactos, versiones, SHA, resultado, fallos y correcciones.
6. Toda API corregida debe contar con pruebas positivas, negativas, permiso permitido/denegado, validación, serialización, idempotencia (cuando aplique) y efecto persistido en base de datos.
7. Antes de merge/release, verificar migraciones sobre una base vacía y sobre un esquema representativo con datos; no improvisar reseteos destructivos. Si no se puede reproducir un caso, reportarlo como bloqueado, no como aprobado.

## 3. Fortalezas que conviene conservar

- JWT HS256 valida firma, issuer, audience y expiración; los usuarios inactivos o con versión de sesión obsoleta no deberían poder seguir usando el token. El secreto se exige por variable y el código exige al menos 32 bytes.
- Contraseñas cifradas con BCrypt (coste configurado en 12), política de contraseña inicial y creación del primer administrador condicionada a una base sin usuarios.
- CORS usa orígenes explícitos y rechaza `*`; API stateless con cabeceras de seguridad y CSRF deshabilitado para autenticación bearer stateless.
- Roles/permisos viven en PostgreSQL; el token no constituye la única fuente del permiso actual, pues los permisos se vuelven a consultar y los cambios de usuario invalidan versiones de sesión.
- Los precios de ventas se toman del catálogo del servidor; la cantidad usa `BigDecimal`; los saldos rechazan stock negativo y respetan reservas activas.
- Los principales comandos de ventas, ajustes, compras/recepciones, devoluciones, transferencias y WhatsApp intentan implementar idempotencia mediante clave, hash de solicitud y restricciones/locks SQL.
- Se usa Outbox para persistir eventos junto a transacciones del negocio; los movimientos y las entradas de valoración están protegidos por triggers append-only en el esquema.
- Migraciones incrementales, claves foráneas, restricciones de dominio y `ddl-auto: validate` son bases apropiadas para integridad. La validación real de estos elementos continúa pendiente.
- El manejador de errores HTTP evita devolver stack trace/SQL/secretos al cliente; el request ID facilita correlación. El filtro de auditoría no registra cuerpo ni cabeceras secretas.
- Dockerfile multi-stage, runtime JRE y usuario no root; PostgreSQL Compose incorpora healthcheck y el API depende del estado saludable de la base.
- Hay pruebas de servicio/integración para movimientos, compras parciales, devoluciones de venta, cancelaciones de venta, transferencias y ciclo WhatsApp, que deben ampliarse en lugar de descartarse.

## 4. Hallazgos priorizados

Severidades: **P1** bloquea cierre funcional/seguridad o pone en duda reglas esenciales; **P2** defecto importante o riesgo operativo; **P3** deuda, alcance o mejora. “Confirmado estático” significa que el archivo observado respalda la afirmación. Los elementos marcados “validar en ejecución” requieren pruebas y pueden resultar correctos.

### P1-01 — No hay evidencia de que el backend compile, migre o arranque

- **Evidencia:** `microservices-inventary-ecuapp/pom.xml`, `src/main/resources/application.yaml`, migraciones `src/main/resources/db/migration/V1__...sql` a `V13__...sql`, `src/test/java/com/ecuapp/inventary/InventaryApplicationTests.java` y `.github/workflows/backend-ci.yml`.
- **Estado:** validación pendiente; no afirmar que build o migraciones pasan.
- **Acción:** ejecutar contra PostgreSQL 16 limpio; confirmar Flyway aplica V1–V13, `ddl-auto=validate` termina sin mismatch y `mvn -B verify` pasa con Java 21. Repetir sobre instancia con migraciones previas/datos representativos.
- **Aceptación:** evidencia de CI para SHA final; migraciones reproducibles; sin errores de mapping JPA; tests pasan sin ignorar casos ni relajar restricciones.

### P1-02 — La lógica de valoración FIFO/promedio no está integrada en los flujos de stock revisados

- **Evidencia:** el esquema V9 define `inventory_costing_config`, `inventory_cost_layer` e `inventory_valuation_entry`; existen `InventoryCostLayer`, `InventoryCostingConfig`, `InventoryValuationEntry` y repositorios. Sin embargo, en `PurchaseOrderService.receive()` se actualiza el saldo y se registra `StockMovement` con `unitCost`, pero no se crea capa de costo/entrada de valoración; `SaleService.create()` registra salida con el `product.getCostPrice()` actual. No se observó servicio/controlador de costing dedicado en el árbol revisado.
- **Impacto:** el coste y el margen históricos pueden no representar FIFO o costo promedio ponderado cuando cambie el costo de catálogo o existan múltiples recepciones con costos distintos.
- **Acción:** confirmar la política aprobada y hacer explícito el alcance real. Si FIFO/promedio forma parte del MVP, implementar motor transaccional para entradas/salidas/devoluciones/transferencias/ajustes, bloquear capas en orden estable, crear entradas append-only y evitar usar el costo de catálogo actual como sustituto de costo histórico. Alinear moneda, redondeo, devoluciones, cancelaciones y reversas.
- **Aceptación:** pruebas de cálculo con dos o más recepciones a costos diferentes, venta parcial, devolución, cancelación/reversa, cantidades decimales, redondeo y concurrencia; conciliación de cantidad/costo de capas contra stock/ledger. Si se excluye de MVP, documentar expresamente y retirar las promesas de costeo en la documentación operativa.

### P1-03 — Cobertura HTTP/API y controles de autorización insuficientes

- **Evidencia:** existen 20 controladores y 9 archivos de test; los tests de integración revisados invocan la capa de servicio directamente (`InventoryIntegrationTest`, `ProcurementIntegrationTest`, `SalesReturnIntegrationTest`, `SaleCancellationIntegrationTest`, `StockTransferIntegrationTest`, `WhatsAppCommerceIntegrationTest`). No se observó una suite de `MockMvc`/`WebTestClient` que ejercite todas las APIs con JWT real.
- **Acción:** añadir pruebas de contrato HTTP para cada ruta del inventario de endpoints de la sección 6. Probar JSON y status codes, `ProblemDetail`, request ID, Bearer inválido/ausente, rol/permiso correcto e incorrecto, headers obligatorios, filtros, paginación, errores de validación y que las operaciones denegadas no escriban datos ni auditoría de éxito.
- **Aceptación:** matriz de cobertura con una fila por operación; no queda API de escritura sin un test HTTP autorizado y uno denegado; pruebas ejecutadas y reproducibles en PostgreSQL.

### P1-04 — Las respuestas 401/403 de Spring Security no tienen handlers explícitos RFC 9457

- **Evidencia:** `config/SecurityConfig.java` no configura `AuthenticationEntryPoint` ni `AccessDeniedHandler`; `common/GlobalExceptionHandler.java` y `common/ApiProblemFactory.java` solo operan en el pipeline MVC, mientras que errores de autenticación/autorización pueden ser convertidos directamente por el filtro de seguridad.
- **Acción:** configurar handlers de seguridad para JSON `application/problem+json`, con status/título/código y `requestId` coherentes; no filtrar mensajes internos; cubrir token faltante, inválido, expirado, usuario desactivado, tokenVersion inválida y permiso insuficiente.
- **Aceptación:** todas las respuestas de error dentro de `/api/**`, incluidos 401/403 y 429, cumplen una forma única y tests comprueban content type, status y correlación.

### P1-05 — Las capacidades de producto dinámico y variantes están parcialmente modeladas, pero no completas como API

- **Evidencia:** V9 crea JSONB `product.attributes`, `category_attribute_definition`, `product_variant`, unidades y conversiones. Existen entidades/repositorios de atributos y variantes, pero en los 20 controladores enumerados no aparecen endpoints para gestionar definiciones de atributos, validar valores por esquema, variantes, unidad de medida o conversiones; el `ProductDtos.WriteRequest` tampoco muestra `attributes` ni variantes.
- **Impacto:** el requisito de definir plantillas dinámicas por categoría y operar tallas/colores/variantes no queda demostrado por el CRUD de producto actual.
- **Acción:** decidir lo que forma parte del MVP; implementar servicios, DTOs, permisos, validación de esquema versionado y APIs o marcar formalmente fuera de alcance. Integrar variante con SKU/barcode, precio, unidad, stock, compras y ventas si la variante debe ser una unidad vendible.
- **Aceptación:** contrato API y pruebas de esquemas/versiones/campos obligatorios/tipos inválidos/valores enum/atributos desconocidos/compatibilidad de datos previos.

### P1-06 — No hay frontend implementado en la rama examinada

- **Evidencia:** `app-inventary-ecuapp/README.md` contiene texto de placeholder y `app-inventary-ecuapp/docs/context.md` es un contexto, sin `package.json`, `angular.json` ni `src/` de Angular en el árbol revisado.
- **Acción:** si la entrega prometida incluye aplicación web, implementar el frontend por su plan/contrato: login, renovación/expiración de sesión, guardas y visibilidad de permisos, catálogo, inventario, compras, ventas, devoluciones, transferencias, auditoría, errores y loading states. Si esta entrega es solo backend, declararlo como exclusión formal y no presentar el proyecto como completo de extremo a extremo.
- **Aceptación:** build/test del frontend si está dentro del alcance, integración con APIs reales y pruebas de permisos; o alcance backend-only firmado/documentado.

### P2-01 — Docker Compose no pasa `CORS_ALLOWED_ORIGINS` al contenedor API

- **Evidencia:** `microservices-inventary-ecuapp/.env.example` declara `CORS_ALLOWED_ORIGINS`; `application.yaml` la lee, `application-dev.yaml` tiene fallback local, pero `docker-compose.yml` no incluye esta variable en `api.environment`.
- **Impacto:** cambiar la variable en `.env` puede no surtir efecto en la API contenedorizada y en perfil dev podría aplicarse el origen fallback; deployments con otro dominio pueden fallar o no respetar su configuración esperada.
- **Acción:** pasar `CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-}` explícitamente al contenedor y decidir fallback seguro por perfil. Documentar que origen no equivale a autenticación.
- **Aceptación:** prueba manual/automatizada de preflight desde origen permitido y origen no permitido, tanto Compose dev como prod.

### P2-02 — Rate limit no consistente con el formato de error ni la auditoría

- **Evidencia:** `config/ApiRateLimitFilter.java` retorna 429 directamente, con body hecho a mano, sin `code`/`requestId`. El filtro está ordenado antes de `ApiRequestAuditFilter`, por lo que las peticiones limitadas que terminan en el propio filtro no pasan por el siguiente filtro de auditoría.
- **Acción:** construir el 429 a través de un formato común; preservar/exponer el request ID generado por `RequestCorrelationFilter`; emitir evento seguro de límite/rate-denied sin incluir secretos; verificar que la auditoría no genere recursión ni bloquee la petición.
- **Aceptación:** tests para límite N/N+1, `Retry-After`, request ID, forma RFC 9457 y auditoría registrada.

### P2-03 — El rate limit es por instancia y usa `getRemoteAddr()` sin estrategia de proxy explícita

- **Evidencia:** `ApiRateLimitFilter` utiliza caché Caffeine local y `request.getRemoteAddr()`; comentario del propio código advierte que producción requiere gateway/WAF/limitador distribuido.
- **Acción:** documentar topología de proxy confiable; no confiar indiscriminadamente en `X-Forwarded-For`. Añadir rate limit distribuido/gateway para despliegues con múltiples réplicas y límites por integración/credencial cuando sea apropiado.
- **Aceptación:** prueba detrás del proxy real; los clientes no pueden falsificar su IP desde cabeceras, y el rate limit mantiene el límite total entre réplicas.

### P2-04 — Paginación: propiedad de sort no validada

- **Evidencia:** `catalog/application/PageQuery.java` comprueba que `property` no esté vacío, pero `JpaProductPersistenceAdapter.pageable()` convierte la propiedad recibida en `Sort.Order` sin allowlist. Los endpoints aceptan `Pageable`.
- **Impacto:** un `?sort=propiedadInexistente` puede provocar excepción del repositorio y HTTP 500, según ruta/repositorio.
- **Acción:** normalizar y allowlistear las columnas por endpoint; devolver 400 problem detail para propiedad inválida; conservar límite de tamaño y evitar paginación negativa. Aplicar de modo consistente a productos/categorías/almacenes/ventas/compras/conteos/auditoría.
- **Aceptación:** test de sort permitido, múltiple, descendente, nombre inválido, tamaño máximo, número negativo y página fuera de rango.

### P2-05 — El modelo de ubicaciones no habilita todavía múltiples ubicaciones de stock por producto y almacén

- **Evidencia:** V9 crea `warehouse_location` y clave única `(product_id, location_id)`, pero conserva también `uk_stock_balance_product_warehouse` en `(product_id, warehouse_id)`. Esto impide más de un saldo de un producto en distintas ubicaciones del mismo almacén.
- **Acción:** si multi-bin forma parte del alcance, rediseñar las consultas y comandos para direccionar ubicación; ajustar restricción/ledger, reserva, transferencia interna, conteo, picking y consolidación. Si no, documentar que existe una única ubicación operativa por producto/almacén.
- **Aceptación:** tests con dos ubicaciones del mismo almacén y transferencia/picking entre ellas o exclusión aprobada del alcance.

### P2-06 — Regla de “último SUPER_ADMIN” vulnerable a concurrencia

- **Evidencia:** `auth/UserManagementService.update()` valida `countByRoleAndActiveTrue(SUPER_ADMIN) <= 1` antes de mutar usuario, pero no se aprecia lock de conjunto/tabla o restricción de base de datos que serialice dos cambios simultáneos sobre distintos administradores.
- **Acción:** serializar cambios de rol/estado de administradores (por ejemplo bloqueo de una fila de configuración común) o introducir un diseño de restricción seguro compatible con el modelo; mantener transacción y auditoría. No agregar una restricción parcial sin revisar la portabilidad/esquema PostgreSQL.
- **Aceptación:** test concurrente de dos solicitudes que intentan quitar privilegios a los dos últimos administradores: como mínimo uno debe fallar y siempre debe quedar uno activo.

### P2-07 — Matcher de cancelación de venta desactualizado

- **Evidencia:** `SecurityConfig` especifica matcher POST `/api/v1/sales/*/cancel`, pero `SalesController` expone `/api/v1/sales/{id}/request-cancellation` y `/api/v1/sales/{id}/approve-cancellation`. El método de aprobación sí tiene `@PreAuthorize('sales:cancel')`, pero el matcher antiguo es código muerto/confuso.
- **Acción:** alinear los matchers con rutas reales y dejar una política única y legible. Probar separadamente solicitante con `sales:manage` y aprobador con `sales:cancel`, incluyendo prohibición de autoaprobación.
- **Aceptación:** tests explícitos y retirada del matcher obsoleto.

### P2-08 — Outbox mantiene transacción/locks durante llamada de red y no se observa estrategia de redrive operativa

- **Evidencia:** `sales/OutboxPublisher.publishPending()` tiene `@Transactional`, reclama eventos con lock y espera hasta 5 s por evento Kafka. `OutboxEvent.markFailed()` reintenta con backoff y marca FAILED al llegar a 10 intentos. No hay consumidor/proyección en la aplicación y README lo declara pendiente.
- **Impacto:** lotes lentos bloquean filas por tiempo de red; un envío Kafka aceptado seguido de rollback puede duplicar al reintentar; un evento FAILED necesita diagnóstico/redrive y alertas.
- **Acción:** considerar reclamar en transacción corta (`FOR UPDATE SKIP LOCKED` o estado de claim con lease), enviar fuera de la transacción y guardar resultado después; mantener entrega al menos una vez y documentar idempotencia de consumidores por `eventId`; monitorizar pending/failed/age y crear procedimiento seguro de replay.
- **Aceptación:** tests con fallo/timeout/reintento/restart/duplicado, dos publicadores concurrentes y alerta/redrive documentado. No afirmar exactly-once.

### P2-09 — Algunos caminos de autorización/documentación de permisos pueden ser incoherentes

- **Evidencia:** los endpoints de proveedores en `partners/PartnerController.java` usan método `@PreAuthorize('purchasing:read')`, mientras el matcher de rutas en `SecurityConfig` asocia GET de proveedores/clientes/partners con `partners:read`. El efecto real es la intersección de ambos controles. `MANAGER_ALMACEN` tiene ambos permisos en V10; `VENDEDOR` tiene `partners:read` pero no `purchasing:read`, por lo que no podrá consultar proveedores pese al permiso de ruta. Puede ser política intencional, debe resolverse explícitamente.
- **Acción:** decidir la matriz de acceso por rol y hacerla coincidir en SecurityConfig, `@PreAuthorize`, semilla SQL, OpenAPI y pruebas.
- **Aceptación:** matriz con al menos los cinco roles, por cada operación crítica, que demuestre acceso permitido/denegado.

### P2-10 — Los permisos de aprobación de compra merecen revisión explícita

- **Evidencia:** `PurchaseOrderController.approve` requiere `purchasing:approve`; la semilla V10 asigna ese permiso a `SUPER_ADMIN`, no a `MANAGER_ALMACEN`.
- **Acción:** validar si el responsable de almacén debe aprobar compras conforme a requisitos/segregación de funciones. No ampliar permisos automáticamente; documentar decisión y testearla.
- **Aceptación:** aprobación permitida solo por roles intencionales; pruebas de segregación vendedor/solicitante/aprobador si aplica.

### P2-11 — Contabilidad fiscal, pagos y facturación no forman parte del flujo actual

- **Evidencia:** README confirma que el impuesto queda a cero; no se emite factura electrónica ni procesa pago en venta; pedidos WhatsApp permanecen `AWAITING_PAYMENT` y no existe integración externa de confirmación/conciliación de pago.
- **Acción:** no presentar el total como fiscalmente definitivo ni un pedido como pagado. Antes de uso comercial en Ecuador, definir reglas tributarias con responsable contable, integración SRI/comprobantes, estados de pago, conciliación, idempotencia webhook y reversos/reembolsos. Mantener estas funciones detrás de feature flags si se desarrollan por etapas.
- **Aceptación:** especificación aprobada y tests de impuestos, fallos/duplicados del proveedor de pago, estados legales y conciliación, o exclusión explícita de producción comercial.

### P2-12 — Integración de medios solo registra URL, no archivos

- **Evidencia:** `media/ProductImageService.java`, controladores y README indican URL HTTPS y metadatos; no hay carga binaria. El código no descarga URLs de imagen, mitigando SSRF en la ruta actual.
- **Acción:** dejar la limitación clara en API y UI. Si se implementa carga, usar storage de objetos, límite de tamaño, MIME y firma de contenido, escaneo, nombres aleatorios, CDN y política para URLs; jamás confiar solo en extensión/Content-Type del cliente.
- **Aceptación:** tests de URL inválida/HTTP, permisos, imagen principal única, despublicación y límites si hay upload.

### P2-13 — Endpoints de escritura que requieren `Idempotency-Key` deben revisarse sistemáticamente

- **Evidencia:** ventas, ajustes, transferencias, conteos, recepciones, devoluciones y pedidos/cotizaciones WhatsApp usan claves idempotentes; transiciones de compras (`submit`, `approve`, `cancel`) y operaciones de carrito no siguen necesariamente el mismo contrato. Las pruebas de idempotencia concurrente están limitadas.
- **Acción:** construir una tabla por comando POST/PUT/DELETE con columna “clave obligatoria, scope, hash canónico, replay esperado, conflicto por body distinto”. Añadir idempotencia donde el comando sea susceptible de reintentos de cliente/red; no imponer una clave indiscriminadamente a acciones puramente idempotentes sin definir contrato.
- **Aceptación:** mismo key+payload no duplica eventos/saldos; mismo key+payload distinto retorna 409; la recuperación después de timeout se documenta; claves en ámbitos diferentes no colisionan accidentalmente.

### P2-14 — Auditoría y ledger necesitan pruebas de integridad adicionales

- **Evidencia:** hay auditoría de operaciones y filtro HTTP para errores; los movimientos son append-only por triggers SQL. Aún no hay tests para acceso a bitácora, filtros/paginación, fallos de auditoría, intento de modificar/borrar ledger con SQL, ni auditoría de 401/403/429.
- **Acción:** probar permisos `audit:read`, exclusión de actor no autorizado, límites de página, filtros/intervalos, correlación, redacción de secretos y persistencia de auditoría. Probar directamente que las migraciones/triggers niegan UPDATE/DELETE de `stock_movement`/`inventory_valuation_entry` y de las líneas inmutables de conteo.
- **Aceptación:** el sistema no expone cuerpos, tokens, claves de integración ni contraseñas en metadatos; intento de hard delete de ledger falla en PostgreSQL; acciones exitosas/fallidas producen eventos apropiados.

### P3-01 — Arquitectura hexagonal aplicada solo parcialmente

- **Evidencia:** el catálogo de productos tiene `ProductUseCases`, `ProductPersistencePort` y `JpaProductPersistenceAdapter`; buena parte de inventario, compras, ventas, WhatsApp y auditoría trabaja directamente con repositorios JPA y entidades.
- **Acción:** no es obligatorio reescribir todo para cerrar el bug backlog. Documentar explícitamente que es monolito modular en transición. En nuevos cambios, evitar que controladores se conviertan en lógica de negocio y extender puertos/casos de uso por módulos conforme se refactoricen.
- **Aceptación:** límites y convenciones documentados; sin reescritura masiva que retrase corrección de integridad/tests.

### P3-02 — Falta estrategia de datos/observabilidad operativa completa

- **Evidencia:** Actuator solo expone `health,info`; no se observan métricas de negocio de stock, latencia API, fallos de Outbox, tasa 429 ni edad de cola. README menciona observabilidad como pendiente.
- **Acción:** añadir métricas seguras y no cardinalidad alta, logs JSON/correlación donde aplique, alarmas para Outbox FAILED y fallos de migración/DB, readiness/liveness diferenciados y política de backup/restore.
- **Aceptación:** runbook de despliegue/rollback/restore y smoke test de salud sin revelar detalles internos.

### P3-03 — Diferenciar alcance vigente de visión objetivo

- **Evidencia:** README raíz describe ambición frontend, microservicios, variantes, atributos dinámicos, costeo y ciclo documental; varios puntos aún no están implementados como APIs o están declarados pendientes.
- **Acción:** clasificar cada requisito como `IMPLEMENTADO`, `PARCIAL`, `PENDIENTE`, `FUERA DE ALCANCE` y asociar issue/PR/tests. No llamar “completo” a un módulo por existir entidades o tablas.
- **Aceptación:** todos los elementos de `docs/02-requisitos-y-alcance.md` y `docs/05-catalogo-apis.md` tienen un estado trazable y criterios de aceptación.

## 5. APIs detectadas y plan mínimo de pruebas

Los paths listados se observaron en controladores. Confirmar rutas y contratos exactos con OpenAPI una vez se ejecute la aplicación y corregir el documento si hay diferencia.

| Área | Rutas / comandos observados | Pruebas mínimas obligatorias |
|---|---|---|
| Autenticación | `POST /api/v1/auth/login`, `GET /api/v1/auth/me` | Credencial correcta/incorrecta, usuario inactivo, username normalizado, validación de request, JWT exp/issuer/audience/firma, revocación por desactivar/cambiar rol/restablecer clave, login rate limit. No filtrar si el usuario existe. |
| Administración | `GET/POST /api/v1/admin/users`, `GET/PATCH /api/v1/admin/users/{id}`, `PUT /api/v1/admin/users/{id}/password`, `GET/POST /api/v1/admin/roles`, `GET /api/v1/admin/permissions`, `PUT /api/v1/admin/roles/{code}/permissions` | Solo `security:manage`; hashing BCrypt; políticas de clave; duplicados; último SUPER_ADMIN en secuencial y concurrencia; permiso inexistente/inactivo; `security:manage` no delegable; token antiguo rechazado. |
| Categorías | `/api/v1/catalog/categories` GET/POST/PUT `/{id}`, POST `/{id}/deactivate` | Paginación/sort/slug; nombres duplicados (case-insensitive), colisión de slug, categoría inactiva y productos; permiso para leer/escribir. |
| Productos | `/api/v1/catalog/products` GET/POST/GET `/{id}`/PUT `/{id}`/POST `/{id}/deactivate` | SKU único/normalizado/inmutable; validación de precios/currency/URL; categoría activa; sort inválido; precios/costos no filtrados a usuario público; stock solo para productos físicos; caché pública invalida/expira correctamente tras cambios. |
| Catálogo público | `GET /api/v1/public/catalog/products` y lectura por producto si aplica; integración WhatsApp `/catalog/products` | Nunca incluye costo/margen; solo producto/categoría activos; disponibilidad usa `available`, no solo `on_hand`; cache TTL; paginación; URLs; autenticación de máquina en WhatsApp. |
| Imágenes | `/api/v1/catalog/products/{productId}/images` GET/POST/PATCH `/{imageId}`/DELETE `/{imageId}` | URL HTTPS válida y no control chars; pertenencia imageId al producto; permisos; primary única bajo concurrencia; despublicar idempotente/no expone una imagen no publicada; no hace fetch remoto. |
| Almacenes | `/api/v1/warehouses` GET/POST/PUT `/{id}`/POST `/{id}/deactivate` | Código único, activos/inactivos, referencias a movimientos históricos, permisos y concurrencia con stock/transferencias. |
| Inventario | `GET /api/v1/inventory/balances`, `/balances/{productId}/{warehouseId}`, `GET /movements`, `POST /adjustments` | Producto/almacén inexistente o inactivo; cantidad >0 y escala 3; entradas/salidas, reserva y no-stock negativo; mismo key/body reproduce mismo movimiento; key distinto body da 409; no se permite modificar ledger. |
| Conteos | `/api/v1/inventory/counts` GET/POST, GET `/{id}`, POST `/{id}/post`, POST `/{id}/cancel` | Cantidades capturadas, duplicación de productos, saldo esperado cambió entre captura y post, reservas activas, diferencia positiva/negativa/cero, post/cancel repetido, key replay, no se modifican líneas ni movimientos históricos. |
| Reposición | `/api/v1/inventory/reorder-rules` GET/POST/PUT `/{id}`/POST `/{id}/deactivate`, `GET /replenishment-suggestions` | Mínimo/objetivo; proveedor activo de tipo SUPPLIER/BOTH; duplicado producto+almacén; sugestión con disponible y reserva; inactivo excluido; no hay modificación de producto/almacén una vez creada. |
| Transferencias | `/api/v1/inventory/transfers` GET/POST, GET `/{id}`, POST `/{id}/dispatch`, `/receive`, `/cancel`, `/reverse` | Flujos válidos/inválidos; almacenes distintos; insuficiencia; idempotencia por etapa; cancelación en tránsito compensa origen una vez; reversal completa compensa ambos almacenes; stock total conserva invariantes; concurrencia/orden de lock; movimientos y Outbox atómicos. |
| Proveedores/clientes | `/api/v1/suppliers`, `/api/v1/customers`, `/api/v1/partners` según `PartnerController` | Role/permission matrix, tipo CUSTOMER/SUPPLIER/BOTH, código/tax ID/email inválidos/duplicados, desactivar con histórico, paginación y sort. Aclarar diferencia `partners:read` vs `purchasing:read`. |
| Compras | `/api/v1/purchase-orders` GET/POST, GET `/{id}`, POST `/{id}/submit`, `/approve`, `/cancel`, `/{id}/receipts`; `GET /api/v1/goods-receipts/{id}` | Máquina de estados, proveedor y almacén activos, producto stockeable, cantidades y monedas, segregación de aprobación, recepciones parciales, reintento idempotente, no recibir más de lo pedido ni líneas ajenas, movimientos/cost layers, auditoría y Outbox atómicos. |
| Devolución a proveedor | `/api/v1/purchase-returns` GET `/{id}`/POST | Solo recepción contabilizada; línea pertenece a recepción; acumulado devuelto no excede cantidad recibida bajo concurrencia; stock no negativo ni reservado; ledger, idempotencia, auditoría y Outbox; coste histórico y estado después de devolución. |
| Ventas | `/api/v1/sales` GET/POST, GET `/{id}`, POST `/{id}/request-cancellation`, POST `/{id}/approve-cancellation` | Stock, precio servidor, tax declarado, misma moneda, productos duplicados, cliente válido, misma key/hash, replay de venta; dos ventas simultáneas por el último stock: no sobreventa; cancelación requiere solicitud, actor distinto y sin devolución incompatible; reponer exactamente una vez. |
| Devolución venta | `/api/v1/sales-returns` GET `/{id}`/POST | Sólo venta COMPLETED; línea de venta válida; cantidades acumuladas bajo concurrencia; RESTOCK vs SCRAP; no devolver más de vendido; coste/total correcto; replay y ledger atomicidad. |
| Auditoría | `GET /api/v1/audit-events` | Solo `audit:read`; filtros actor/acción/recurso/outcome/intervalo; from<to; tamaño max 100; newest-first; no exposición de datos sensibles; eventos de fallo/denied/429 y correlación. |
| GraphQL | `POST /graphql` (según Spring GraphQL) | Requiere permiso; campos solo de catálogo autorizado; query inválida; profundidad >5; alias/fan-out/variables enormes; request muy grande; timeout/rate limiting; no acceso a costo/margen ni datos privados. La profundidad 5 por sí sola no controla complejidad total. |
| WhatsApp/n8n | `/api/v1/integrations/whatsapp/catalog/products`, `/catalog/products/{identifier}`, `/carts`, `/carts/{cartId}`, `/carts/{cartId}/items/{productId}` PUT/DELETE, `/carts/{cartId}/quotes`, `/quotes/{quoteId}`, `/orders`, `/orders/{orderId}`, `/orders/{orderId}/cancel` | Clave ausente/incorrecta/config vacía/rotación; quote TTL; precio cambiado después de cotizar; consent false; reservas en stock disponible; dos pedidos concurrentes para última unidad; expiración/liberación una sola vez; recursos de distinto cliente; idempotencia quote/order; cancelación repetida. No registrar como cobrado un pedido sin proveedor de pagos. |
| Salud/operación | `GET /actuator/health`, `GET /actuator/info` con permiso donde aplique; OpenAPI `/v3/api-docs`, Swagger UI | Health no filtra detalles; info protegido; Swagger apagado en prod y encendido solo en dev; OpenAPI refleja rutas/seguridad/headers/códigos/esquemas reales. |

## 6. Suite de pruebas que debe crear el agente

### 6.1 Unitarias

- Reglas de cantidad positiva, precisión NUMERIC (cantidades a escala 3, dinero a escala 2), moneda y redondeo.
- Todas las transiciones de estado de `Sale`, `PurchaseOrder`, `StockTransfer`, `InventoryCount`, `WhatsAppCart`, `WhatsAppQuote` y `WhatsAppOrder`.
- Cálculo de totales, snapshots y costes si se implementa costing.
- `PasswordPolicy`, normalización de SKU/username/códigos, hash canónico de idempotencia, `RequestCorrelationFilter`, `ApiProblemFactory` y formato de 429.

### 6.2 Integración PostgreSQL

- Preferir Testcontainers con PostgreSQL de la misma versión mayor o servicio local aislado reproducible. No usar H2 para asumir equivalencia de JSONB, `ON CONFLICT`, triggers, `TIMESTAMPTZ`, locks y restricciones.
- Aplicar desde cero todas las migraciones en orden y validar mappings de entidades. Probar upgrade desde una copia anonimizadas de la última versión previa real si se dispone de ella.
- Verificar que las restricciones impidan duplicados, stock/reservas imposibles, transiciones inválidas y edición/borrado físico del ledger.
- Garantizar rollback completo si falla cualquier paso de una operación (stock, documento, movimiento, auditoría, idempotencia y Outbox).

### 6.3 Concurrencia obligatoria

- `N` solicitudes de venta que excedan el saldo total en conjunto: suma vendida no puede superar disponible y saldo nunca negativo.
- Misma idempotency key + mismo body concurrente: un solo recurso/efecto; todas las respuestas convergen al resultado o se documenta el conflicto temporal.
- Misma key + body distinto: 409 y ningún segundo efecto.
- Dos devoluciones simultáneas de la misma línea no pueden superar la cantidad original.
- Recepción y devolución a proveedor concurrentes de las mismas líneas no deben sobredevolver ni corromper stock.
- Conteo concurrente con venta/ajuste: post debe detectar que cambió la cantidad esperada y no generar un ajuste basado en snapshot obsoleto.
- Dos últimos administradores modificados en paralelo: nunca dejar el sistema sin SUPER_ADMIN activo.
- Dos publicadores Outbox en paralelo y fallo entre publish Kafka y commit de base: una publicación posiblemente duplicada debe ser deduplicable por `eventId`.
- Pedidos WhatsApp que reservan la última unidad y job de expiración/cancelación simultáneos: una sola liberación/consumo, sin reserva negativa.

### 6.4 REST y seguridad

- Usar `MockMvc` o test HTTP real dentro del contexto Spring para ejercer filtros, controlador, validación y seguridad; obtener JWT con la estrategia de test apropiada y mantener una prueba separada del contrato real de login.
- Para cada controlador, probar un caso con JWT correcto y permiso correcto, un caso con permiso incorrecto, token ausente, token mal formado/caducado, DTO inválido, ID inexistente y conflicto de dominio.
- Assertar status, `Content-Type`, propiedades de RFC 9457, `requestId`, no stack trace, no password/token/SQL en respuesta y efecto transaccional.
- Añadir colección reproducible (Postman/Newman o equivalente) de smoke tests para operar un ciclo completo con variables de entorno y cleanup seguro.

## 7. Contrato HTTP: decisiones uniformes

El agente debe registrar y homogeneizar la semántica del contrato. Como guía, validar y documentar estas respuestas, salvo que el contrato actual aprobado disponga otra cosa:

- `200 OK`: lectura o transición con representación.
- `201 Created`: creación de documento/recurso y ubicación si aplica.
- `204 No Content`: borrado lógico/físico permitido sin cuerpo, solo donde proceda.
- `400 Bad Request`: JSON/formato/query/param/header ausente o inválido.
- `401 Unauthorized`: sin token o token no válido.
- `403 Forbidden`: token válido pero permiso insuficiente.
- `404 Not Found`: recurso inexistente o intencionalmente oculto por política pública.
- `409 Conflict`: clave idempotente reutilizada con body distinto, estado concurrente/duplicado o transición incompatible.
- `422 Unprocessable Entity`: regla de negocio válida sintácticamente pero no satisfacible (por ejemplo, stock insuficiente), según política común elegida.
- `429 Too Many Requests`: rate limit con `Retry-After` y request ID.
- `500 Internal Server Error`: mensaje genérico; detalle interno solo en logs seguros correlacionados.

No convertir todo `IllegalArgumentException` automáticamente a 422 si representa un parámetro de paginación/sort inválido; separar errores del contrato de errores de dominio.

## 8. Persistencia, migraciones y modelo de dominio

- [ ] Hacer diff de todas las entidades JPA frente a columnas, FK, índices, nombres y escalas de migraciones V1–V13.
- [ ] Confirmar Flyway validation, checksums y naming constraints PostgreSQL en base vacía y upgrade real.
- [ ] Confirmar `spring.jpa.open-in-view=false` no provoca lazy access fuera de transacción en todos los mappers/responses.
- [ ] Revisar `@Version`/row locks donde corresponda, incluyendo `StockBalance`, compras, ventas, conteos, devoluciones y reservas.
- [ ] Revisar restricciones únicas para claves de idempotencia: `idempotency_record` usa scope+key, pero algunas tablas mantienen claves globales únicas; decidir si las claves se deben poder reutilizar entre endpoints y si `scope` se debe incluir en esas claves antiguas.
- [ ] Asegurar que una fila idempotente fallida no quede persistida parcialmente: las excepciones dentro de una transacción pueden revertir también el registro `IN_PROGRESS`; documentar comportamiento para retry real tras timeout/rollback.
- [ ] Probar triggers append-only y excepciones por constraints desde PostgreSQL real.
- [ ] Confirmar que devoluciones, ajustes, conteos y transferencias registran movimiento coherente con saldos y ubicaciones.
- [ ] Definir cómo migrar y reconstruir el stock/cost layers si la valoración se implementa después de haber movimientos históricos.

## 9. Configuración y despliegue

- [ ] Corregir paso de `CORS_ALLOWED_ORIGINS` en `docker-compose.yml` y probar perfil dev/prod.
- [ ] Verificar que `.env` jamás se añade al repositorio ni al Docker build context, y que todos los ejemplos se etiquetan como desarrollo.
- [ ] Hacer que API tenga healthcheck/readiness apropiados; no confundir puerto abierto con migraciones completas o DB lista.
- [ ] Documentar migración/rollback compatible con datos; `flyway clean` debe permanecer deshabilitado.
- [ ] Verificar que los endpoints Actuator no muestren detalle sensible y que `/actuator/info` exige permiso real.
- [ ] Revisar timeouts y límites de request body/conexión, pool JDBC, shutdown graceful y configuración proxy/TLS.
- [ ] Mantener Docker runtime no root; evaluar si el build de Docker debe ejecutar tests en una etapa separada (CI ya debe ser el punto de verdad, pero la imagen actual compila con `-DskipTests`).
- [ ] Configurar logs/alertas para salud de DB, excepciones de API, 401/403/429, latencia, migraciones, Outbox PENDING/FAILED y expiración/liberación de reservas.

## 10. Backlog de alcance de negocio que debe resolverse antes de declararlo completo

| Capacidad | Estado observable en esta rama | Acción de cierre |
|---|---|---|
| Catálogo básico / categorías / productos | Parcialmente implementado | Tests HTTP, validaciones, concurrencia, contratos. |
| Atributos JSONB versionados / variantes / conversión unidades | Esquema y entidades disponibles; APIs/uso de punta a punta no demostrado | Completar o excluir expresamente del MVP. |
| Inventario por almacén con saldo y ledger | Implementado parcialmente | Tests end-to-end y de carrera; clarificar límite de una ubicación. |
| Conteo físico y ajustes | Código y migración presentes | Probar stale snapshot, reservas, idempotencia y trigger. |
| Reposición | Código presente | Tests de reglas, sugerencias y permisos. |
| Compras/recepciones parciales/devolución proveedor | Código presente | Tests de estados, carrera, coste e idempotencia. |
| Ventas y devolución cliente / cancelación aprobada | Código presente | Pruebas HTTP y concurrencia; coste/impuestos aún incompletos. |
| Costeo FIFO/promedio | Esquema/entidades; motor de aplicación no encontrado en flujos revisados | Implementar o declarar explícitamente no disponible. |
| Pagos, reembolsos y conciliación | No implementado según README | Diseñar integrador/estados idempotentes; no asumir pago. |
| Impuestos y factura electrónica SRI Ecuador | No implementado; impuesto a cero según README | Definición fiscal + integración aprobada + pruebas con proveedor/sandbox. |
| Carga binaria de medios | No implementada; solo URLs | Integrar storage/escaneo o mantener límite documentado. |
| Integración WhatsApp/n8n | API con clave, carrito, cotización, reserva y orden pendiente de pago | Pruebas E2E; mecanismo externo de pagos/confirmación queda pendiente. |
| Kafka/Outbox | Publicador opcional, consumidores no implementados | Reintentos, dead-letter/redrive, deduplicación/consumidores y observabilidad. |
| Multiempresa / tenant isolation | No implementado según documentación | No desplegar como SaaS multiempresa sin límite por tenant probado end-to-end. |
| Reportes y analítica | No demostrados en endpoints observados | Definir alcance, permisos y consultas. |
| Frontend Angular | No presente en esta rama | Implementar o declarar backend-only. |

## 11. Orden de ejecución recomendado

### Fase A — Contrato y seguridad

- [ ] Inventario real de rutas a partir de controladores/OpenAPI.
- [ ] Corregir RFC 9457 para 401/403/429 y requestId.
- [ ] Corregir matcher de cancelación obsoleto.
- [ ] Establecer matriz y tests RBAC por rol.
- [ ] Validación de query sort, paginación y headers.
- [ ] CORS Compose y pruebas de preflight.
- [ ] Revisar endpoint privado de auditoría, GraphQL y media.

### Fase B — Integridad de negocio

- [ ] Auditoría de transiciones de estados y efectos duplicados.
- [ ] Pruebas concurrentes de stock, returns, recepciones, conteo, reserva y último superadmin.
- [ ] Resolver costing: implementar o retirar promesa del alcance.
- [ ] Confirmar stock/ledger/location balance en toda compensación.
- [ ] Idempotencia por endpoint documentada y probada.
- [ ] Outbox con recuperación, duplicados esperables y redrive.

### Fase C — Persistencia e integración real

- [ ] PostgreSQL limpio: Flyway V1–V13, mapeos JPA, constraints y triggers.
- [ ] PostgreSQL con upgrade desde snapshot representativo.
- [ ] Suites unitarias, integración y HTTP.
- [ ] Pruebas de concurrencia repetibles.
- [ ] Smoke tests de todo el camino desde login hasta movimientos/venta/recepción/devolución/transferencia.
- [ ] Resolver todos los fallos hasta suite verde; nunca modificar tests para ocultar defectos.

### Fase D — Preparación de entrega

- [ ] Sin secretos en Git, logs ni respuestas.
- [ ] OpenAPI/README sincronizados con comportamiento observado.
- [ ] Resultado CI asociado al SHA final, CI disparada de manera autorizada.
- [ ] Runbook de backup, restore, migración, rollback, configuración/secrets y redrive Outbox.
- [ ] Marcar cada hallazgo con enlace a PR/commit/test y responsable.
- [ ] Solo entonces aprobar merge/release; cerrar alcance excluido con decisión explícita.

## 12. Evidencia que debe entregar el agente local

Crear/actualizar un archivo `docs/RESULTADO_AUDITORIA_EJECUCION.md` que incluya:

- commit SHA inicial y final, rama, Java/Maven/Docker/PostgreSQL versions;
- comandos exactos ejecutados y su resultado;
- número de pruebas unitarias/integración/API; fallos iniciales y finales;
- evidencia de Flyway V1–V13 y Hibernate validate;
- tabla por endpoint con resultado de 2xx/4xx/5xx esperado y pruebas RBAC;
- reporte de pruebas concurrentes e invariantes calculadas;
- contrato OpenAPI final y cualquier endpoint no probado;
- cada hallazgo de este documento marcado `RESUELTO`, `NO APLICA` (con justificación), `ABIERTO` o `BLOQUEADO`;
- enlaces a commits/PR/issues de los cambios; cualquier riesgo aceptado explícitamente y por quién.

## 13. Definition of Done

No cerrar auditoría ni declarar backend completo hasta cumplir todo lo siguiente:

- [ ] Cada P1 está resuelto o tiene exclusión del MVP aceptada de forma explícita.
- [ ] No hay defecto conocido que permita stock negativo, sobreventa, sobredevolución, doble recepción, doble reversa o duplicación de una operación idempotente en la matriz concurrente.
- [ ] Todos los endpoints de escritura están probados con autorización real y rechazo de autorización.
- [ ] Las respuestas 4xx y 5xx tienen contrato estable sin información sensible y request ID correlacionable.
- [ ] PostgreSQL aplica todas las migraciones y Hibernate valida el mapping.
- [ ] Unit tests, integration tests, HTTP/security contract tests y concurrencia pasan sin exclusiones injustificadas.
- [ ] `mvn -B verify` y CI del SHA exacto final están verdes.
- [ ] Docker Compose se puede configurar con orígenes CORS reales y secretos obligatorios, y health/readiness está documentado.
- [ ] README/OpenAPI/matriz funcional coinciden con las funciones reales.
- [ ] Pagos/SRI/frontend/costing/multiempresa y otras exclusiones están claramente declaradas, no insinuadas como terminadas.

---

## Anexo A — Archivos de referencia principales

- Configuración/seguridad: `src/main/resources/application.yaml`, `application-dev.yaml`, `application-prod.yaml`, `src/main/java/com/ecuapp/inventary/config/SecurityConfig.java`, `ApiRateLimitFilter.java`, `ApiRequestAuditFilter.java`, `RequestCorrelationFilter.java`, `GraphqlSecurityConfig.java`.
- Errores/auditoría: `common/GlobalExceptionHandler.java`, `common/ApiProblemFactory.java`, `audit/AuditController.java`, `audit/AuditService.java`.
- Catálogo: `catalog/ProductController.java`, `ProductService.java`, `ProductDtos.java`, `CategoryController.java`, `CategoryService.java`, `catalog/application/PageQuery.java`, `catalog/infrastructure/adapter/out/persistence/JpaProductPersistenceAdapter.java`.
- Inventario: `inventory/InventoryService.java`, `InventoryCountService.java`, `ReorderService.java`, `StockBalance.java`, `StockMovement.java` y controladores correspondientes.
- Ventas/compras/devoluciones/transferencias: `sales/SaleService.java`, `purchasing/PurchaseOrderService.java`, `purchasing/PurchaseReturnService.java`, `returns/SalesReturnService.java`, `transfers/StockTransferService.java`.
- Integraciones: `whatsapp/WhatsAppIntegrationController.java`, `WhatsAppIntegrationKey.java`, `WhatsAppCommerceService.java`, `sales/OutboxPublisher.java`, `sales/OutboxEvent.java`.
- Persistencia: `src/main/resources/db/migration/V1__...sql` hasta `V13__...sql`.
- Despliegue: `pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env.example`, `.github/workflows/backend-ci.yml`.
- Pruebas actuales: `src/test/java/com/ecuapp/inventary/**`.

**Recordatorio:** la existencia de clases, tablas o documentación no demuestra funcionamiento en producción. La aprobación final debe apoyarse en pruebas reproducibles y evidencia del commit probado.