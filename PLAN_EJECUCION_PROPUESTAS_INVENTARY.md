# Plan maestro de implementación integral — Inventary

**Repositorio:** JhonnyMinian92/inventary  
**Rama de ejecución:** implementacion_propuestas  
**Corte de análisis:** SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481  
**Alcance:** frontend, backend, datos, seguridad, integraciones, operación y experiencia comercial.  
**Estado al SHA auditado:** Fase 1 validada por CI; Fase 2 parcial con bloqueo de seguridad; Fase 3 estructura implementada y smoke responsive de login; Fase 4 en progreso; Fase 5 parcial con UI/datos fiscales manuales y cálculo inicial; Fase 6 en progreso con pestañas y búsqueda server-side; Fase 7 en progreso con Outbox/Inbox V1–V28; Fase 8 parcial por la bandeja WhatsApp sin fulfillment; Fase 9 parcial por dashboard/command-center/reportes sin analítica avanzada; Fase 10 pendiente.

## 1. Resultado de la consolidación

Se identificaron exactamente **22 documentos Markdown en la raíz**: 21 propuestas y un roadmap transversal. Se revisaron junto con las propuestas de jerarquía frontend/backend y las tres auditorías. El detalle de trazabilidad —documento, vigencia, requerimientos y fases que lo cubren— se encuentra en MATRIZ_TRAZABILIDAD_PROPUESTAS_INVENTARY.md.

No se implementarán los documentos históricos en paralelo con sus versiones sustitutas. Sin embargo, los requisitos útiles que no contradicen la versión vigente se conservan en el alcance consolidado.

### Precedencia documental aplicada

- Productos: PROPUESTA_UX_PRODUCTOS_V2.md prevalece sobre PROPUESTA_UX_PRODUCTOS.md; se mantienen las necesidades históricas compatibles como edición masiva, variantes y asistencia de contenido con revisión humana.
- Ventas: PROPUESTA_UX_VENTAS_V2.md prevalece sobre PROPUESTA_UX_VENTAS.md.
- Inventario: PROPUESTA_INVENTARIO_AGENT_READY_V3.md prevalece sobre PROPUESTA_INVENTARIO_CENTRO_CONTROL_Y_AUDITORIA_V2.md.
- Reportes: PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V2.md prevalece sobre V1.
- El roadmap previo a V2, las propuestas de arquitectura y las auditorías se utilizan para ordenar dependencias, seguridad, pruebas y criterios de salida; no se tratan como autorización para cambiar funcionalidades sin contratos y migraciones.

## 2. Modelo mental común y reglas que gobiernan todas las fases

Estas reglas resuelven los solapamientos entre documentos. Cada fase y cada prueba deben respetarlas.

### 2.1 Fuentes de verdad y topología

1. PostgreSQL y el backend Spring Boot de Inventary conservan la autoridad sobre catálogo, precios, descuentos, impuestos que correspondan, disponibilidad, reservas, stock, coste, operaciones, permisos, pedidos, ventas, contratos, pagos registrados y auditoría.
2. El backend actual es un monolito modular Spring Boot, no una colección de microservicios desplegables por separado. La carpeta histórica microservices-inventary-ecuapp no cambia esa realidad. No se extraerán procesos o bases de datos por reorganizar paquetes.
3. React presenta contratos y estados del servidor. No replica reglas de precio, stock, coste, descuentos, impuestos, fiscalidad, permisos ni elegibilidad.
4. n8n transporta y coordina conversaciones, webhooks, notificaciones y automatizaciones; no es la fuente de verdad ni escribe directamente en PostgreSQL.
5. Una IA extrae, clasifica, resume y propone borradores. No inventa hechos, no concede permisos, no fija importes, no decide impuestos y no ejecuta mutaciones fuera de herramientas tipadas y autorizadas.

### 2.2 Conceptos que nunca se deben fusionar

- Contribuyente/RUC, establecimiento SRI, punto de emisión, entidad legal propietaria, almacén, ubicación/bin y local de prestación son conceptos distintos.
- La relación N:M entre establecimientos y bodegas es una asociación operativa tipada y temporal; no define por sí misma propiedad del stock ni selecciona el emisor fiscal.
- Consulta de disponibilidad no es reserva; reserva no es preparación; preparación no es despacho; despacho no es entrega.
- Pedido recibido no es venta registrada; venta no es cobro; cobro no es factura autorizada.
- Cancelación operativa no es anulación fiscal; devolución física no es nota de crédito; nota de crédito no es reembolso monetario.
- Orden de compra no es recepción; recepción física no es factura de proveedor; factura de proveedor no es pago.
- Suscripción, plan, contrato, cuota/factura, pago y derecho/beneficio son entidades y estados separados.
- Mensaje enviado no es mensaje entregado; interpretación del modelo no es consentimiento.
- Un evento Outbox, una venta y una proyección analítica no son tres operaciones económicas distintas.

### 2.3 Integridad, privacidad y evidencia

- Aplicar autorización en backend por acción y ámbito antes de devolver detalles, paginar, agregar o exportar. La visibilidad de botones es solo UX.
- Usar permisos de mínimo privilegio, separación de funciones y scopes por entidad legal/establecimiento/almacén/local según el caso.
- Toda escritura de negocio relevante conserva transacción local, idempotencia, hash canónico, auditoría, movimiento/valoración y Outbox cuando corresponda.
- Misma clave y mismo contenido recuperan la misma operación; misma clave con contenido diferente genera conflicto. Tras timeout ambiguo se consulta el estado antes de repetir efectos.
- Los eventos se entregan al menos una vez; los consumidores Inbox deduplican por eventId. No se promete exactly-once extremo a extremo.
- No llamar sistemas externos dentro de transacciones PostgreSQL largas. Las operaciones externas críticas tienen estado durable, operación/intent ID, timeout, reintento acotado y reconciliación.
- No registrar contraseñas, tokens, credenciales, tarjetas, mensajes completos o PII innecesaria en logs, telemetría, prompts ni eventos.
- Migraciones Flyway históricas no se editan; los cambios de esquema son incrementales y compatibles con los datos existentes. No usar ddl-auto update ni flyway clean para ocultar incompatibilidades.
- No borrar archivos fuente como “código muerto” sin demostrar que no se usan. Los datos operativos de imágenes y archivos se preservan salvo decisión de su propietario y copia de seguridad.

### 2.4 Reglas de cantidades, moneda e informes

- No sumar cantidades de unidades incompatibles ni valores de monedas distintas.
- Las cifras agregadas declaran fórmula, granularidad, ámbito, moneda/unidad, fecha de frescura y permiso requerido.
- Las uniones analíticas declaran grano y relaciones; ventas, líneas, movimientos, documentos fiscales, pagos y cuotas no se multiplican al unir tablas.
- Stock se atribuye a su propietario, producto y almacén real. Una relación con varios establecimientos nunca duplica saldos.
- El reporte comercial no se presenta como contabilidad o declaración tributaria completa si faltan libros, documentos fiscales, retenciones, gastos, pagos u otras fuentes.

## 3. Fases consolidadas y orden de ejecución

La secuencia tiene **10 fases en total**. La Fase 1 ya está completada; restan 9. Ningún requisito de las 22 propuestas queda fuera de la matriz de cobertura.

### Fase 1 — Estabilización, línea base y coherencia de reglas actuales

**Objetivo:** eliminar defectos reproducibles de la base vigente y disponer de una validación repetible antes de refactorizar o ampliar el producto.

**Trabajo incluido**
- Corregir CI de backend y frontend para instalar y probar con versiones y lockfiles reproducibles.
- Validar backend con Java 21, Maven, PostgreSQL real, migraciones y Docker Compose desde una base vacía.
- Corregir la configuración de bootstrap y evitar secretos/artefactos de build versionados.
- Retirar exclusivamente los 19 artefactos de build demostrados bajo la ruta literal de directorio Maven.
- Corregir el mínimo de precio que el formulario aplicaba indebidamente a servicios y bienes sin control de stock; preservar coste FIFO/promedio y el mínimo sin pérdida para bienes stockeables.
- Añadir prueba de regresión HTTP para SERVICE y NON_STOCKED_GOOD, verificando precio de catálogo, aceptación de precio por debajo del coste de referencia no inventariable y ausencia de movimiento de stock.
- Mantener documentación de instalación/arquitectura coherente con el árbol real y registrar el plan maestro.
- No intentar “resolver” el impuesto cero con tasas adivinadas: la fiscalidad se implementa contra contrato y reglas vigentes en la Fase 7.

**Criterios de salida**
- [x] Bootstrap de desarrollo/CI válido y sin secretos reales en Git.
- [x] Artefactos de build de alta confianza eliminados y excluidos.
- [x] npm ci, npm test y npm run build ejecutados por CI en la rama.
- [x] Maven verify con PostgreSQL 16, validación Compose normal/Kafka y smoke test con volumen PostgreSQL vacío en verde.
- [x] Precio mínimo por coste limitado a productos STOCKED_GOOD en servidor y frontend.
- [x] Regresión API añadida para SERVICE y NON_STOCKED_GOOD.
- [x] Auditorías y documentación referenciadas desde el plan maestro.
- [x] Resultados asociados a SHA y enlazados abajo.

**Evidencia**
- Frontend Build: https://github.com/JhonnyMinian92/inventary/actions/runs/41165392564 — success; npm ci, npm test y npm run build.
- Backend CI: https://github.com/JhonnyMinian92/inventary/actions/runs/41165468427 — success; Maven verify/PostgreSQL 16, Compose normal y Kafka, smoke test de base vacía y limpieza.
- El backend probó el código fuente de Fase 1 en SHA 51b3ce2b719d3c35643136a2ee893048a0c6b581; el frontend probó el código de interfaz de la fase en SHA 8a9d9e3137f0af61cd14dca0e8ae0c2556f4cfa6. Los commits posteriores indicados en este plan son documentación; no se atribuye una prueba de código distinta a esos resultados.

**Estado: FASE 1 COMPLETADA**

### Fase 2 — Seguridad, ámbitos, integridad transaccional y trazabilidad base

**Estado al SHA auditado: PARCIAL.** JWT/RBAC, scopes, auditoría y Outbox existen, pero SecurityConfig deja públicas sin autenticación las familias /api/v1/tax/** y /api/v1/refunds/**. Corregir ese bloqueo P0 y probar cada ruta sensible antes de cerrar.


**Objetivo:** cerrar los controles fundacionales antes de ampliar pantallas, datos sensibles o integraciones.

**Backend y datos**
- Formalizar matriz rol × permiso × acción × ámbito y catálogo de scopes por entidad legal/RUC, establecimiento, almacén/local y recurso.
- Aplicar el scope en cada endpoint, incluidos ID directo, GraphQL, búsqueda, reportes agregados, exportación, auditoría y operaciones de soporte. Denegar por defecto.
- Separar coste/margen, información financiera, datos fiscales y PII de los permisos operativos ordinarios.
- Establecer segregación de funciones: alta/envío/aprobación de compras, solicitud/aprobación de cancelaciones, conteos, ajustes, devoluciones y reembolsos.
- Revisar todos los comandos críticos: venta, recepción, devolución, ajuste, conteo, transferencia, reserva WhatsApp, suscripción y operaciones de pago.
- Normalizar Idempotency-Key, scopes y hashes de solicitud; cubrir doble clic, concurrencia, replay, timeout después del commit y reintento tras reinicio.
- Asegurar transacciones y locks para evitar sobreventa, sobredevolución, doble recepción, doble compensación, reserva negativa, stock negativo y pérdida de consistencia entre saldo, ledger y cost layers.
- Completar eventos Outbox para todos los hechos de negocio requeridos (incluyendo ajustes y conteos); definir envelope versionado, eventId, aggregate/version, correlationId, causationId y payload mínimo.
- Endurecer auditoría existente: catálogo de acciones, vínculos entre varias entidades, audit append-only, escritura atómica con hechos críticos, denegaciones/lecturas sensibles y permisos SQL mínimos.
- Separar auditoría de negocio, telemetría UI y observabilidad técnica. No capturar valores de campos sensibles, teclado, contraseñas, tokens o transcripciones indiscriminadamente.
- Definir identidad de clientes por canal y deduplicación de webhooks entrantes.
- Documentar retención, backup/PITR, restore probado, RPO/RTO y procesos de redrive/reconciliación.

**Frontend**
- Aplicar visibilidad por permiso como ayuda, conservar errores 401/403 visibles y nunca convertir 403/fallo en estado vacío o cero.
- Mantener contexto/filtros ante errores, no presentar cifras o acciones fuera del scope.

**Pruebas obligatorias**
- Matriz HTTP/JWT por ruta y rol/scope: 2xx, 400/401/403/404/409/422/429/5xx según contrato.
- Manipulación de IDs de otro scope; lectura, detalle, export y agregados.
- Concurrencia real en PostgreSQL para últimos stocks, últimas unidades retornables, receipts, count/adjustment y SUPER_ADMIN.
- Duplicación Outbox/Inbox, webhook repetido, timeout ambiguo, rollback integral y fallo al guardar auditoría.
- Pruebas SQL de append-only/constraints con la cuenta real de ejecución.

**Avance de implementación y validación (2026-10-31; cierre todavía parcial):**

- La autorización de acciones del frontend ahora falla de forma cerrada: si el perfil/permisos aún no se cargaron o la colección de permisos es inválida, no concede permisos explícitos. Se añadieron cuatro pruebas unitarias específicas.
- El filtro de auditoría HTTP registra lecturas GET exitosas de rutas sensibles (incluidas administración, auditoría, reportes, costes, socios, operaciones e inventario) sin copiar cuerpos, parámetros, cookies, credenciales ni respuestas; la metadata queda limitada a método, ruta y status.
- Los ajustes manuales y los conteos físicos publican eventos Outbox transaccionales. Los conteos emiten creación, contabilización y cancelación; los ajustes emiten INVENTORY_ADJUSTMENT_POSTED. Las pruebas comprueban que la repetición idempotente no duplica eventos.
- Las órdenes de compra ahora emiten PURCHASE_ORDER_CREATED, PURCHASE_ORDER_SUBMITTED, PURCHASE_ORDER_APPROVED y PURCHASE_ORDER_CANCELLED, solo cuando la transición cambia realmente el estado. Las pruebas API verifican un evento por transición incluso al repetir el comando.
- Los conteos físicos aplican maker-checker: quien captura no puede contabilizar su propio conteo. La identidad de quien contabiliza se guarda en posted_by y se devuelve en el DTO, auditoría y Outbox. La migración V20 etiqueta los conteos históricos ya contabilizados como LEGACY_UNATTRIBUTED, sin atribuirles un operador ficticio.
- La política de ámbitos temporales de almacén y contribuyente (RUC), RBAC, lectura financiera separada, aprobación de órdenes de compra, cancelación de ventas por solicitante/aprobador distintos, auditoría append-only, Outbox/Inbox y las pruebas de PostgreSQL existentes se conservan y se ejercitan en la suite.
- V25 incorpora ámbitos temporales explícitos de contribuyente por usuario; búsquedas, detalles, establecimientos, snapshots y asociaciones fiscales quedan filtrados por RUC autorizado. El alta/revocación exige `security:manage` y deja auditoría. Las lecturas `/api/v1/taxpayers/**` quedan incluidas en la bitácora de acceso a datos sensibles.
- En el corte actual se añadieron permisos fiscales separados `taxpayer:read`/`taxpayer:manage` en V23, asignados a AUDITOR/SUPER_ADMIN y solo SUPER_ADMIN respectivamente; se añadió cobertura HTTP de aislamiento y se conectó el registro tributario manual a la UI con visibilidad/acciones por permiso. Esto mejora la separación de datos fiscales, pero no sustituye scopes por RUC/establecimiento.
- GitHub Actions [backend-ci #41214296258](https://github.com/JhonnyMinian92/inventary/actions/runs/41214296258): **success** sobre el SHA de código `7e6d982895b020d1fae26d3e30602edb0997cc47`; Maven/Java 21 y PostgreSQL 16 aprobaron **106/106 pruebas**, Flyway aplicó V1–V20 y Hibernate validó los mappings. Docker Compose normal y Kafka validaron su configuración; el smoke test construyó e inició la API desde un volumen PostgreSQL vacío y el healthcheck respondió tras cuatro intentos.

**Pendiente para cerrar formalmente la Fase 2:**

- Extender el ámbito de RUC a entidad legal/establecimiento y a los demás recursos fiscales/operativos relacionados; el scope de contribuyente ya restringe el registro tributario, pero no reemplaza un modelo completo de ownership del stock ni scopes legales por recurso.
- Separar permisos de PII y datos fiscales de los permisos operativos/financieros y aplicar la redacción de esos campos en todos los DTOs y exportaciones.
- Completar maker-checker para ajustes manuales, devoluciones de ventas, devoluciones a proveedores y reembolsos; actualmente existen controles por rol/ámbito e idempotencia, pero esos flujos no tienen aprobación independiente completa.
- Completar y versionar el envelope Outbox con versión de esquema, versión de agregado, correlación y causalidad persistidas; revisar todos los eventos de dominio y su redrive.
- Cerrar identidad/deduplicación de webhooks por canal y aportar evidencia de backup/PITR, restore, RPO/RTO y redrive posterior a recuperación.
- Completar la matriz de pruebas HTTP por rol y ámbito, manipulación de IDs, concurrencia y aislamiento de datos para todas las operaciones incluidas en la fase.

**Estado: EN PROGRESO — los trabajos anteriores están implementados, pero la fase no se declara cerrada mientras sigan pendientes estos controles.**

### Fase 3 — Arquitectura y separación del frontend React

**Objetivo:** migrar por etapas a la jerarquía propuesta manteniendo rutas, comportamiento, contrato HTTP y stack.

- Preservar React 19, Vite 8, JavaScript/JSX, Node 22 y npm actual; nuevas dependencias solo con justificación y compatibilidad comprobada.
- Separar bootstrap, providers, App shell, layout, navegación, router, rutas, páginas, widgets, features, entidades y shared/UI.
- Convertir App.jsx en composición/navegación mínima; retirar cargas globales de productos, balances, compras, ventas e historial completo.
- Extraer sesión/auth provider para usuario, permisos, expiración y logout únicamente; mantener cifrado de login y almacenamiento seguro de tokens.
- Mantener un núcleo HTTP central y separar endpoints por dominio; centralizar errores, request IDs, expiración de sesión, idempotencia solo en rutas que la soporten.
- Adoptar carga por página/feature, cancelación o descarte de solicitudes obsoletas, refresh selectivo, estados loading/error/forbidden/empty/success y paginación del servidor.
- Organizar internamente catálogo, inventario, compras, ventas, devoluciones, reportes, contactos/almacenes, suscripciones, WhatsApp y dashboard.
- Separar shared UI neutral, componentes de entidad, acciones/features y composiciones/widgets; evitar archivos genéricos que mezclen dominios.
- Formularios largos como páginas y steppers; modales solo para acciones cortas. Preservar historial del navegador y evitar pérdida accidental.
- Definir capas de estilos, tokens, CSS global y CSS por feature; accesibilidad teclado/lector de pantalla, responsive 360/768/escritorio.
- Mantener rutas públicas, alias, title, login, base pública /inventary/ y despliegue Nginx.
- Conservar Node test como suite actual; ampliar gradualmente componentes y browser E2E después de evaluar Vitest/Testing Library/Playwright.

**Criterios de salida:** cada ruta existente sigue funcionando; App.jsx no orquesta operaciones; ninguna tabla descarga el historial completo; filtros/página viven en URL; cada vista tiene estado independiente; test, build, responsive, accesibilidad y base /inventary/ validados.

**Implementación y evidencia (2026-10-31):**
- Reorganización registrada en `implementacion_propuestas`; commits de arquitectura `0148e061b848418e9c633a6742f78a3000f3a762`, carga por ruta `1ec1e4f7a7b3ae9d9e0730a455451e4748a79667` y separación de estilos `f8d197b5076d1cccd9ae5bd4e77ff51ecf51364f`.
- GitHub Actions [Frontend Build #41209564984](https://github.com/JhonnyMinian92/inventary/actions/runs/41209564984): **success** sobre el SHA final exacto `f8d197b5076d1cccd9ae5bd4e77ff51ecf51364f`.
- `npm test`: 22 pruebas aprobadas, 0 fallidas. `npm run build`: correcto.
- Carga por demanda redujo el chunk inicial de JavaScript de 586.47 kB (155.64 kB gzip antes de la división) a 309.23 kB (95.84 kB gzip). Las páginas y formularios se generan como chunks separados.
- La extracción de estilos redujo `src/styles.css` de 49.8 KB a 23.1 KB y separó CSS de catálogo, operaciones, devoluciones, reportes, detalles transaccionales, workflows y accesibilidad en módulos asociados a sus features/shared UI.
- Revisión estática confirmó base Vite /inventary/, fallback SPA de Nginx, breakpoints responsive, :focus-visible y prefers-reduced-motion.
- GitHub Actions [Frontend Build #41213440556](https://github.com/JhonnyMinian92/inventary/actions/runs/41213440556): **success** sobre SHA a566795289fa4caef45dfe81c24b81fa922dc33c; npm test aprobó **26/26**, npm run build y el preview de producción finalizaron correctamente.
- El smoke E2E ejecutó Chromium en 360, 768 y 1280 px. Verificó que el documento y el body no desbordan horizontalmente, que se renderiza el encabezado de login y que los dos campos tienen etiquetas asociadas. El panel complementario se oculta en 360/768 y aparece en 1280. Las tres capturas se guardaron como artefacto del workflow.
- El test E2E cubre la pantalla de login y su comportamiento responsive; no equivale a una aceptación browser exhaustiva de todos los recorridos autenticados y de negocio. Esos recorridos deben probarse con las cuentas y datos de cada rol antes de declarar aceptación funcional completa.
- La autorización visual se ajustó a fail-closed y se cubrió con pruebas unitarias; frontend y backend mantienen la separación de contratos y la estructura por features.

**Estado: IMPLEMENTACIÓN ESTRUCTURAL COMPLETADA; E2E responsive de login validado. La aceptación browser integral de todos los flujos/roles sigue siendo una tarea de cierre, no se declara cubierta por el smoke de login.**


### Fase 4 — Arquitectura modular del backend Spring Boot

**Objetivo:** aplicar la jerarquía objetivo al monolito modular, sin cambios funcionales ni alteración de contratos.

- Usar catalog como piloto: separar API REST/GraphQL, request/response DTOs, mappers, application/use cases/ports, domain y infrastructure/persistence.
- Introducir una clase request/response separada solo cuando el contrato y el caso de uso difieran; no dividir clases mecánicamente sin semántica real.
- Reorganizar progresivamente auth, audit, catalog, inventory, media, partners, purchasing, reports, returns, sales, subscriptions, transfers, warehouse y whatsapp.
- Mover entidades JPA y Spring Data a infraestructura; controladores no aceptan ni devuelven entidades JPA.
- Application coordina casos de uso y depende de puertos; dominio no importa Spring, Hibernate, HTTP, Kafka ni PostgreSQL.
- Evitar dependencias entre repositorios/entidades internas de dominios; usar contratos/puertos solo donde exista una necesidad real.
- Separar mappers API de mappers de persistencia. Delimitar platform (configuración, seguridad, web, filtros, observabilidad, Kafka/cache) y shared (conceptos transversales verdaderos).
- Aislar Outbox/Inbox/Kafka, almacenamiento de media y clientes externos en infraestructura/adaptadores.
- Conservar nombres de tabla/columna, relaciones, índices, constraints, locks, triggers, orden/checksum Flyway, transacciones, payload JSON y rutas REST/GraphQL.
- No extraer microservicios como parte de la reorganización.

**Criterios de salida:** arquitectura de dependencias comprobable; Maven verify, tests PostgreSQL, HTTP/security/contract y arranque Spring verde; sin cambio de esquema o contrato no acordado; excepciones documentadas.

**Avance implementado (2026-10-31; piloto vertical inicial):**
- Los controladores REST de productos, categorías, estructura del catálogo y catálogo público se ubicaron en `catalog.api.rest.controller`.
- El resolver GraphQL del catálogo se ubicó en `catalog.api.graphql.controller`; para listar categorías delega en `CategoryService`, no accede directamente al repositorio Spring Data.
- Los servicios de aplicación `ProductService`, `CategoryService`, `CatalogStructureService`, `ProductVariantService` y `CategorySuggestionService` se ubicaron en `catalog.application.service`.
- Se actualizó el adaptador de integración WhatsApp para consumir el nuevo paquete de `ProductService`.
- Los DTOs `ProductDtos`, `CategoryDtos` y `CatalogStructureDtos` se trasladaron a `catalog.api.rest.dto`; se hicieron públicos los mappers estáticos usados entre paquetes.
- Los paths REST, los nombres de las operaciones GraphQL, el schema GraphQL, los DTOs serializados, las entidades y los mappings JPA se conservaron. No se añadieron ni editaron migraciones Flyway.

**Brechas que impiden declarar cerrada la Fase 4:**
- Las entidades JPA y los repositorios Spring Data de catálogo siguen en el paquete raíz heredado; el puerto `ProductPersistencePort` todavía usa la entidad JPA `Product` como modelo.
- Los servicios del catálogo mantienen dependencias directas a repositorios de otros dominios y a DTOs de presentación en flujos heredados; requieren puertos/contratos de aplicación y mappers específicos antes de cumplir aislamiento estricto.
- Aunque `CategoryDtos` y `CatalogStructureDtos` ya residen en `api.rest.dto`, todavía combinan request/response records y los servicios los reciben directamente; faltan comandos/resultados de aplicación para eliminar la dependencia Application→API.
- Los demás dominios del backend aún no se han migrado a la jerarquía objetivo.
- La suite Maven/PostgreSQL y el smoke de arranque deben validarse en el SHA resultante de esta reorganización.

**Estado: EN PROGRESO — reorganización inicial del piloto catalog registrada; la arquitectura completa de dominios y sus criterios de salida no se declaran cerrados.**

### Fase 5 — Datos maestros, catálogo, almacenes y unidades

**Estado al SHA auditado: PARCIAL.** El registro tributario manual, snapshots, establecimientos, asignaciones, UI, tratamiento fiscal de productos y CRUD inicial de tarifas existen. La importación queda en MANUAL_REVIEW y no equivale a consulta oficial SRI.

Para cerrar:
- Proveedor fiscal aprobado y consulta real o proceso manual controlado aprobado.
- Scope por contribuyente/establecimiento y propietario legal de inventario.
- Corregir autorización pública de /api/v1/tax/**.
- Aplicar correctamente filtro de fecha a GET /tax/rates?date=.
- Snapshots fiscales inmutables por venta/línea: tarifa, base, tratamiento, importe y contexto.
- Contratos de identificación y formularios de socios/compras/ventas completos.
- Pruebas de permisos, vigencias, aislamiento y cambios de tarifa sin alterar historia.

No habilitar facturación por la mera existencia de tarifas, RUC o establecimientos manuales.

### Fase 6 — Operaciones comerciales y estados financieros completos

**Estado al SHA auditado: EN PROGRESO.** La UI tiene pestañas Órdenes/Recepciones/Devoluciones en Compras y Ventas/Devoluciones/Reembolsos en Ventas. Órdenes, recepciones y devoluciones usan búsqueda/paginación server-side y el backend calcula líneas retornables.

Capacidades confirmadas:
- Compras/recepciones parciales/devoluciones a proveedor y movimientos de stock valorizados.
- Ventas, cancelación con aprobador distinto y devoluciones físicas.
- Suscripciones con cuotas/facturación interna y filtros de listado.
- WhatsApp backoffice con resumen operativo y confirmación manual de pago.
- Modelo inicial de reembolso con estados y UI.

Pendientes de cierre:
- Libro de pagos liquidados, gateway/contrato externo y conciliación.
- Control de reembolso acumulado contra pago realmente liquidado; idempotencia/replay robustos y auditoría de cada transición.
- Cerrar autorización de /api/v1/refunds/** y segregación de funciones.
- Snapshots inmutables de comprador/fiscalidad/fulfillment por documento.
- Factura de proveedor/cuentas por pagar si el producto necesita deuda y liquidación de proveedores.
- Contadores/detalles/estados por fila y E2E autenticado de las nuevas pestañas.
- Separación visual y contable entre venta, factura, pago, devolución física y reembolso.

No presentar el reembolso actual como flujo financiero seguro ni el pago de WhatsApp como pasarela.

### Fase 7 — Facturación SRI e integración distribuida durable

**Estado al SHA auditado: EN PROGRESO.** Outbox/Inbox versionado, lease con transacciones cortas, retry, deduplicación y endpoints de diagnóstico están implementados. V28 agrega tablas y endpoints de administración de integration_operation y webhook_subscription/webhook_delivery. Kafka sigue opt-in.

También existen:
- GET /api/v1/dashboard/events, SSE autenticado con señales periódicas para refresco de dashboard.
- GET /api/v1/whatsapp-orders/work-summary.
- APIs integration:read/manage para crear/consultar/reintentar operaciones y administrar suscripciones.

Límites:
- No se encontró worker que ejecute integration_operation ni dispatcher que entregue webhook_delivery de extremo a extremo.
- No hay proveedor fiscal aprobado, XML, firma, secuenciales, RIDE, autorización ni sandbox real.
- La tarifa fiscal/calculadora no es comprobante; el snapshot histórico aún no está completo.
- Kafka no está configurado para producción con TLS/ACL/replicación/DLQ/lag/runbook.

Para cerrar, aprobar contratos/proveedores, crear worker/dispatcher con firma, retry, idempotencia y conciliación, y probar outcomes ambiguos/callback duplicado/falso contra sandbox real.

### Fase 8 — Logística, Pedidos WhatsApp y automatización n8n

**Estado al SHA auditado: PARCIAL.** La aplicación ya cuenta con bandeja WhatsApp paginada, work-summary, detalle y confirmación manual de pago; n8n aparece en el Compose raíz. No se encontró un conjunto de workflows n8n exportados/versionados ni fulfillment, picking/envío, prueba de entrega o handoff de extremo a extremo.


**Fulfillment**
- Modelar opción de cumplimiento, origen, destino, grupo, líneas, reservas, shipment, pickup record, capacidad/horario, coste y ventana estimada.
- Primera entrega: retirar en el mismo almacén que tiene stock o enviar desde una sola bodega de origen.
- Después: preparación/picking simple, listo para retirar, despacho, tránsito, entrega y prueba de entrega con estados y permisos.
- Retirar en otro local requiere transferencia, recepción y reserva posterior; nunca fingir que el stock de origen está disponible en el destino.
- Multi-origen/multi-fulfillment dividido solo con cotización por grupo, costes/plazos y aceptación explícita.
- Condiciones de retiro por tercero y códigos de un solo uso; horarios/capacidad y prioridad según decisiones.
- No implementar bins/lotes/series/caducidad/picking multi-bin hasta Fase 10 y necesidad operativa demostrada.

**Pedidos WhatsApp y React**
- Bandeja operativa por tareas/estado, búsqueda, filtros y paginación; detalle tipo acordeón/página con conversación, cliente, origen de dato/certidumbre, cotización snapshot, pagos, cumplimiento, riesgo, auditoría y siguiente acción segura.
- Separar estados comerciales, pagos, reserva, fulfillment y documento fiscal.
- Registrar quién capturó, quién confirmó y quién verificó información; mostrar pendientes y contradicciones.
- Políticas de reserva distintas para prepago, pago al retirar, noche, preparación prolongada y crédito; solo habilitar las modalidades implementadas.
- Mantener carrito/cotización y aceptación; cambio de precio/disponibilidad requiere nueva cotización y aceptación.
- Human handoff inmediato por petición explícita, pago disputado, reclamación, baja confianza o excepción.

**n8n y componentes WhatsApp**
- Versionar workflows N8N-00/10/15/20/25/30/40/50/60/70/80/90, export saneado, responsable, entradas/salidas, credenciales, pruebas, rollback y errores.
- Entrada webhook: firma/identidad, persistir primero, deduplicar messageId, correlationId y enrutamiento.
- LLM solo para interpretación de intención/extracción tentativa/redacción; las reglas de interfaz y los permisos son deterministas.
- Usar el componente mínimo válido: catálogo/carrito nativo, producto, botones, listas, Flow estático/dinámico, WebView/IAB, texto o humano. Conservar contexto al cambiar de modalidad.
- Compra menor a un minuto es un objetivo solo para casos sencillos, no una promesa universal; no recortar confirmaciones económicas ni acelerar casos de excepción.
- Medir costo real por mensajes entregados de Meta/BSP, categoría, tarifa vigente, Flow, IA y coste por pedido; no hardcodear tarifas de mensajería.
- WebView requiere confirmar elegibilidad real de WABA/BSP/tipo de CTA. Sesión web opaca, corta, revocable, limitada a categoría/carrito; BFF con scope; no poner X-Integration-Key, JWT ni secretos en URL/JavaScript.
- n8n no crea ventas por SQL, no marca pagos por capturas, no decide bodega/establecimiento fiscal, no inventa precios, y no repite claves con valor nuevo ante timeout.

### Fase 9 — Centros de mando, reportes, analítica y motor comercial

**Estado al SHA auditado: PARCIAL.** Hay dashboard gerencial/operativo, endpoint inventory command-center, reportes operativos/financieros y CSV. El SSE solo envía señal periódica de refresco. No existe todavía el catálogo de datasets/medidas ni el motor de experimentos/recomendaciones avanzado descrito más abajo.


**Inventario Agent Ready V3**
- Vista de dueño orientada a alertas/decisiones y modo separado para operador y auditor.
- Vistas de primer nivel: Resumen, Existencias, Movimientos/Kárdex, Operaciones y Auditoría.
- Endpoint command-center de agregados deterministas, alertas trazables, dataFreshness, fórmulas, unidades, moneda, fecha y permisos.
- Muestra pequeña de movimientos recientes con deep link al Kárdex; no cargar saldos/históricos completos en el navegador.
- Search/q, filtros, DTOs ligeros, paginación, cursor/snapshot, trazabilidad de movimiento a documento y auditoría, reconciliación saldo/valoración y corrección N+1.

**Centro de reportes V2**
- Centro de decisión del dueño, reportes comercial/operativo y fiscal con vistas específicas; no prometer sustituir contador sin libros y fuentes contables completas.
- Constructor declarativo y seguro que combina 2–4 dominios; registry de datasets/medidas/dimensiones/joins autorizados, granularidad, agregaciones y fórmulas explicables.
- Evitar double count entre cabecera/líneas/movimientos/cuotas/documento fiscal/pagos/asientos y entre warehouse-establishment N:M.
- Bajar de indicador a documento origen, filtros del servidor, paginación y export asíncrono backend; no descargar todas las páginas en browser ni ejecutar SQL arbitrario.
- Reportes por moneda/unidad/ámbito y timestamps de actualización por fuente.
- Conciliar ventas internas, documento fiscal, anulaciones/notas de crédito, compras/recepciones/documento proveedor, inventario/costeo, pagos/bancos y cuotas.
- Workpapers IVA/ATS/retenciones/Renta/RDEP únicamente cuando existan fuentes, schemas y obligaciones aplicables; versión de formularios/catálogos/XSD/periodos; revisión y aprobación humana; nunca marcar declaración como presentada sin evidencia del portal/autoridad.
- Si el libro contable, bancos, AP/AR, gasto o nómina están en otro sistema, importar por contrato versionado e idempotente; no fingir completitud con datos parciales.

**Motor comercial inteligente**
- Calidad de catálogo por categoría y relevancia determinista, búsqueda/filtros/comparación, razones verificables y disponibilidad contextual.
- Taxonomía de eventos de producto, carrito, quote, promotions, checkout, order, return, refund y consent, versionada e idempotente.
- Registrar exposiciones reales, posición, canal, resultado y finalidad; minimizar datos.
- Experimentos A/B con hipótesis, unidad de asignación, una métrica primaria, guardas y SRM; no declarar éxito por clics/facturación bruta si caen margen, exactitud o satisfacción.
- Recomendaciones transparentes por atributos, compatibilidad y complementos; modelos estadísticos no antes de tener volumen, consentimiento, exposición, labels y evaluación temporal/causal suficiente.

### Fase 10 — Agentes, capacidades especializadas, WMS opcional y cierre productivo

**Estado al SHA auditado: PENDIENTE.** No se localizó registro/herramientas de agentes A0–A4 ni WMS multi-bin. Se mantiene como alcance condicionado y no debe anunciarse como funcionalidad disponible.


- Contratos OpenAPI estables, catálogo de herramientas tipadas y scopes por función, permisos y ámbito. Empezar solo con lectura (A0), luego recomendación (A1).
- Propuestas persistidas para compra/traslado/ajuste/conteo y acciones (A2); hash del contenido exacto, actor aprobador, expiración, snapshot de precondiciones y estado STALE.
- Ejecución tras aprobación humana (A3) con revalidación, límites, idempotencia, trazas y status/operationId. Autonomía limitada A4 solo en acciones de bajo riesgo con política explícita, cuotas, kill switch, alertas y revisión de seguridad.
- Red team contra prompt injection en producto, documentos, mensajes y archivos; no tratar contenido recuperado como instrucción.
- Evaluaciones offline, presupuesto de llamadas/tokens, privacidad/retención, fallback y kill switch global/por agente.
- Forecast/replenishment y análisis de stock lento, exceso, cobertura, coste de stockout y demanda con backtesting/intervalos y aprobación.
- Suscripciones especializadas por dominio solo si el negocio lo necesita: beneficios/entitlements, asientos, créditos, consumo variable, trials, auto-renewal, eventos, salud con privacidad reforzada, bienes recurrentes y ledger de consumo. No convertir todo en un enum gigante ni en un único calendario de factura.
- WMS avanzado condicionado a evidencia: bins, lotes, series, caducidad, cuarentena/merma, escáner, put-away, picking, ciclo de conteo, packing y expedición; modelar la dimensión del stock y migrar MAIN sin inventar historia.
- Evaluar A2A o extracción futura a microservicios solo si varios sistemas de agentes o requisitos de escalado/propiedad/operación lo justifican.
- Cierre final: auditoría de arquitectura, tests completos, concurrencia, security, rendimiento, restauración/backup, migraciones sobre base vacía y upgrade, Docker/Compose, observabilidad, runbooks, accesibilidad, OpenAPI, documentación, soporte y despliegue reproducible. Cierre por SHA exacto probado.

## 4. Dependencias y puntos de bloqueo legítimos

No son requisitos eliminados; son condiciones externas o decisiones de negocio que deben resolverse antes de activar el flujo correspondiente:

- Contrato real, sandbox, credenciales, estados y política de callbacks del backend fiscal.
- Confirmación profesional de la normativa SRI consolidada aplicable, datos del proveedor tecnológico y responsabilidades del emisor.
- Decisión de la fuente de datos RUC y autorización/licencia del proveedor de consulta; si no hay API aprobada, importación manual controlada.
- Proveedor/gateway de pagos compatible con Ecuador, contratos de charge/refund, firmas y conciliación bancaria.
- Política comercial de descuentos, límites por rol, formas de pago, crédito, reserva, cancelación y devoluciones.
- Reglas de propiedad legal del stock y coexistencia de varios RUC en una bodega; no presumir que la relación N:M determina propiedad.
- Horarios, capacidad, cobertura, tarifas, carriers, prueba de entrega, elegibilidad WABA/WebView/Flows y tarifas efectivas de Meta/BSP.
- Decisión de negocio sobre tipos de suscripción, beneficios, renovación, pruebas, consumo y sectores especializados.
- Volumen y calidad de datos suficientes para analítica predictiva, experimentos y ML.
- Justificación operativa de WMS avanzado, bins, lotes, series y caducidad.

Cada bloqueo tendrá owner, fuente/contrato, dato requerido, fallback y decisión registrada. No se activa una pantalla u opción que prometa una capacidad no operativa.

## 5. Definition of Ready y Definition of Done de cada fase

Una capacidad no entra en implementación hasta que tenga:
- caso de uso/actor y fuente de verdad;
- estados y transiciones;
- request/response/event schema versionado;
- permisos/ámbitos y datos sensibles;
- transacción, idempotencia y concurrencia;
- comportamiento ante timeout, reintento, duplicado y recuperación;
- estrategia de migración/backfill compatible;
- pruebas positivas, negativas, autorización, persistencia, concurrencia y fallo.

Una fase no se cierra por “compilar” solamente. Debe:
- cumplir sus criterios funcionales y de seguridad;
- tener pruebas automatizadas ligadas al SHA final;
- contar con migraciones y documentación sincronizadas;
- no romper contratos/consumidores previos;
- incluir evidencias CI, riesgos conocidos, exclusiones aprobadas y runbook si hay operación externa.

## 6. Estado final del plan

- SHA del código auditado: dc028a346ccf7b94cfbb60b4fb6cd5493518b481, rama implementacion_propuestas.
- Fase 1: COMPLETADA; Backend CI y Frontend Build verdes sobre el SHA documentado.
- Fase 2: PARCIAL; seguridad y scopes en progreso; rutas fiscales y de reembolso públicas bloquean cierre.
- Fase 3: ESTRUCTURA IMPLEMENTADA; smoke responsive del login validado; E2E browser de flujos autenticados pendiente.
- Fase 4: EN PROGRESO; primera separación vertical del catálogo; arquitectura hexagonal no uniforme.
- Fase 5: PARCIAL; registro manual, establecimientos, UI, tarifas y cálculo inicial; integración SRI, seguridad y snapshots fiscales pendientes.
- Fase 6: EN PROGRESO; pestañas separadas y búsqueda server-side mejoradas; pagos/reembolsos seguros y ciclo financiero completo pendiente.
- Fase 7: EN PROGRESO; Outbox/Inbox y modelo V28; worker de integración, dispatcher webhook y SRI real pendientes.
- Fase 8: PARCIAL; base WhatsApp/backoffice/n8n; fulfillment y workflows operativos pendientes.
- Fase 9: PARCIAL; dashboard, command-center, reportes y SSE señalizador; analítica y motor comercial avanzados pendientes.
- Fase 10: PENDIENTE; agentes y WMS opcional no implementados.
- Evidencia backend: [run 41385132612](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132612), 119/119 pruebas, PostgreSQL 16, Compose backend y smoke desde volumen vacío.
- Evidencia frontend: [run 41385132616](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132616), 26/26 pruebas, build y Chromium login 360/768/1280.
- La evidencia browser actual no acredita E2E autenticado de todas las pantallas. El Compose raíz con frontend+n8n no es el Compose probado por backend CI.
- Este plan describe código existente y brechas; propuestas de producto no se interpretan como implementación.
