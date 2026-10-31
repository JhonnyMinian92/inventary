# Plan maestro de implementación integral — Inventary

**Repositorio:** JhonnyMinian92/inventary  
**Rama de ejecución:** implementacion_propuestas  
**Corte de análisis:** 31 de octubre de 2026  
**Alcance:** frontend, backend, datos, seguridad, integraciones, operación y experiencia comercial.  
**Estado:** Fase 1 completada; Fase 3 implementada y validada por CI en `1ec1e4f7a7b3ae9d9e0730a455451e4748a79667`; pendientes la verificación visual E2E de navegador de la Fase 3 y las fases 2 y 4–10.

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
- Reorganización registrada en `implementacion_propuestas`; commit de arquitectura `0148e061b848418e9c633a6742f78a3000f3a762` y optimización de carga por ruta `1ec1e4f7a7b3ae9d9e0730a455451e4748a79667`.
- GitHub Actions [Frontend Build #41209006673](https://github.com/JhonnyMinian92/inventary/actions/runs/41209006673): **success** sobre el SHA final exacto.
- `npm test`: 22 pruebas aprobadas, 0 fallidas. `npm run build`: correcto.
- La división por demanda redujo el chunk inicial de JavaScript de 586.47 kB (155.64 kB gzip antes de la división) a 309.24 kB (95.82 kB gzip); los módulos de página y formularios se generan como chunks separados.
- Revisión estática confirmó base Vite `/inventary/`, fallback SPA de Nginx, breakpoints responsive, `:focus-visible` y `prefers-reduced-motion`. No se ejecutó una prueba visual automatizada en navegador a 360/768/escritorio; esa comprobación queda explícitamente pendiente y no se declara como validada.


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

### Fase 5 — Datos maestros, catálogo, almacenes y unidades

**Objetivo:** construir la base semántica y de datos fiable para ventas, compras, inventario, fiscalidad y logística.

**Fiscalidad de datos maestros y almacenes**
- Modelar perfil del contribuyente/RUC, snapshots de consulta, establecimientos SRI y snapshots históricos con fuente, hora, versión, estado recibido y frescura.
- Fuente SRI/proveedor aprobada y documentada; no asumir API pública con JSON exacto ni usar scraping no documentado. Si no existe fuente aprobada, importación controlada y trazable.
- Información oficial de SRI es de solo lectura en Inventary; no editar manualmente ni reemplazar códigos oficiales con código de almacén.
- Mantener establecimiento como código textual de tres dígitos preservando ceros, estados y nombres comerciales nulos.
- Modelar WarehouseEstablishmentAssignment N:M con tipo de relación, vigencia, prioridad/uso, responsable y auditoría; no define stock owner ni emisor.
- Separar legalEntity/owner del stock para impedir acceso y mezcla de inventario entre RUC diferentes en bodegas compartidas.
- No desactivar una bodega por tener un establecimiento cerrado/suspendido; son estados independientes.

**Socios comerciales y unidades**
- Un BusinessPartner con roles cliente/proveedor/ambos; evitar duplicados de una misma identificación tributaria.
- CRUD central de clientes y proveedores con búsqueda y paginación del servidor; crear/editar en contexto de compras y ventas preservando el formulario.
- Mover unidades a configuración del catálogo de productos; conservar unidades, dimensiones, precisión y conversiones existentes.
- Conversiones versionadas y compatibles; no convertir automáticamente una operación de compra/venta por existir un factor sin especificar la semántica, unidad base, redondeo y snapshot.

**Productos**
- Consolidar tipo STOCKED_GOOD, NON_STOCKED_GOOD y SERVICE; separar servicio one-time de servicio por suscripción.
- Atributos dinámicos con esquema versionado por categoría, obligatoriedad, tipos y validación server-side.
- Variantes con SKU/barcode propios y vínculo integral a búsqueda, compra, venta, precio, unidad e inventario cuando se apruebe la semántica vendible.
- Búsqueda por nombre/SKU/barcode/atributos, filtros combinables, paginación, columnas y restauración de filtros.
- Ficha maestra separada del saldo físico/movimientos/valoración.
- Crear/editar por pasos, borrador, calidad explicable y publicación validada en backend.
- Imágenes con archivos seguros, metadatos, orden, alt-text y estado público controlado.
- Edición masiva con previsualización antes/después, validación por fila, control de concurrencia y reintento parcial.
- Asistencia IA de descripción/normalización en borrador, con fuentes verificables y aprobación humana; no inventar compatibilidad, medidas, certificaciones, garantía, precio o stock.

### Fase 6 — Operaciones comerciales y estados financieros completos

**Compras**
- Pestañas separadas: órdenes, recepciones y devoluciones a proveedores.
- Listas paginadas con filtros/orden del servidor, búsqueda q, DTOs ligeros, filtros persistidos en URL, contadores agregados con semántica y moneda definidas.
- Carga, error, forbidden y empty por pestaña, skeletons, reintento, acción por fila independiente, permiso por operación y diseño responsive.
- Preservar máquina DRAFT → SUBMITTED → APPROVED → PARTIALLY_RECEIVED → RECEIVED / CANCELLED, segregación de aprobación y recepciones parciales.
- Elegibilidad de devolución por línea con recibida/devuelta/pendiente/procesable y revalidación transaccional; no permitir sobredevolución ni devolución sin stock real.
- Edición de DRAFT y reversión solo si se aprueba su contrato y compensación transaccional, no por simple cambio de estado.
- No confundir devolución a proveedor con reembolso de dinero, nota de crédito o factura de proveedor.

**Ventas**
- Flujo de mostrador de pocos pasos, cotización inmediata, buyer/facturación, fulfillment y pago separados.
- Resolver almacén automáticamente a partir del usuario/terminal/caja y scope del servidor; permitir cambio solo a ubicaciones autorizadas con validación.
- Descuentos/promociones con elegibilidad, límites por rol, vigencia, motivo, aprobación y cálculo server-side; sin precio dinámico manipulador.
- Impuestos/cargos como resultados calculados por autoridad fiscal/contrato; nada de tasa hardcodeada en React o subtotal presentado como impuesto.
- Snapshot histórico inmutable del receptor, identificación, dirección de facturación, dirección de entrega/retiro, línea, unidad, descripción, precio, descuento, moneda y condiciones.
- Separar cliente/contacto del destinatario y del punto de cumplimiento.
- Vínculo con pedido/cotización, venta, movimientos/ledger, factura fiscal y pago.
- Cancelación antes de factura, cancelación operativa posterior y devolución parcial/total con permisos y compensaciones distintas.
- Flujo de devolución desde la venta original: cantidades vendidas/devueltas/retornables, motivo, prorrateo, RESTOCK/SCRAP y estados de conciliación.

**Pagos y reembolsos**
- Libro de pagos y PaymentAttempt/Transaction: método, importe, moneda, estado, referencia externa, actor, fecha, proveedor y correlación.
- Registrar pagos manuales autorizados cuando aplique, claramente identificados como registro manual; no declarar transferencia/pasarela verificada por una captura.
- Anticipos, pagos parciales, saldos por cobrar, créditos y vencimientos solo con reglas aprobadas.
- Reembolso es transacción separada enlazada al pago original, idempotente y auditable; estado de dinero no se deriva de devolución física ni de nota de crédito.
- Conciliación con proveedor/banco cuando exista integración real.

**Suscripciones**
- Corregir etiquetas y semántica actuales: “facturación anticipada” no significa “pago anticipado”.
- Mantener el alcance actual fijo mientras no se apruebe plan/lifecycle más amplio; preservar frecuencia, calendario, zona horaria, previsualización, cancelación AT_END y claves idempotentes.
- Venta inicia contratación mediante caso de uso propio; plan, contrato, cuota/factura y pago siguen separados.
- Snapshot inmutable de plan, precio, moneda, términos, consentimiento y calendario.
- Listado con bandeja de próximos cargos y errores; búsqueda/filtros, estados y totales que distingan programado/facturado/cobrado.
- Vincular venta interna y cuota a documento fiscal en Fase 7.
- No habilitar auto-renovación, prueba gratis, créditos, derechos, acceso o sector médico sin modelo y pruebas específicos.

### Fase 7 — Facturación SRI e integración distribuida durable

**Contrato fiscal**
- Acordar API real y ownership con el backend fiscal externo antes de codificar clientes o payloads.
- Inventary conserva venta, snapshots, contexto fiscal, idempotencia, referencia fiscal y estado. Backend fiscal mantiene certificados, XML, firma, numeración, transmisión, RIDE y autorización.
- Persistir solicitud fiscal idempotente ligada a saleId/line snapshots; estado PENDING/ACCEPTED no es autorizado.
- Callbacks firmados o polling por operationId; verificar firma, timestamp, replay, cuenta y documento origen.
- Conciliar autorización/rechazo/anulación/notas de crédito y solicitudes de revisión.
- Aplicar reglas consolidadas y vigentes de SRI, incluido el régimen de consumidor final y NAC-DGERCGC26-00000027 cuando corresponda. El RUC del proveedor tecnológico es configuración del backend fiscal, separado del RUC emisor y comprador; validar aplicabilidad con responsable fiscal.
- No generar XML, firma, secuenciales, RIDE, códigos fiscales ni plazos desde React/n8n.

**Clientes HTTP e Integration Hub**
- Cliente tipado por proveedor/destino, RestClient por defecto con Spring MVC; WebClient solo si streaming/I/O no bloqueante lo justifica.
- Config por entorno y destino allowlist; auth de servicio, secret references, TLS validado, timeouts/deadline, límites, clasificación de errores y observabilidad.
- Retry acotado + backoff/jitter, Retry-After, circuit breaker, bulkhead y rate limit coordinados; no reintentar POST ambiguo sin idempotencia del proveedor.
- Estado durable de integración y de intentos: ACCEPTED, UNKNOWN_OUTCOME, NEEDS_REVIEW, FAILED_PERMANENT, RETRY_SCHEDULED, etc.
- Iniciar Integration Hub como módulo del monolito; extraer servicio independiente solo si varios backends lo usan y la operación lo justifica.
- Webhooks salientes firmados, destinos registrados/allowlisted, SSRF guard, anti-replay, rotación de secretos; webhooks entrantes con Inbox persistente y deduplicación.
- Adaptadores fiscal, mailing, flota y analítica según contrato. Mailing/flota no alteran stock ni factura.

**Kafka, Outbox, caché y jobs**
- Kafka sigue opt-in; desarrollo/staging/prod con TLS/auth/ACL/replicación/lag/DLT y runbook antes de marcarlo operativo.
- Envelope versionado, ownership por topic, retención y clasificación de datos; consumer group por consumidor lógico, Inbox dedupe por eventId.
- Outbox cubre cambios de dominio; reintentos, DLQ, replay y redrive autorizados con auditoría.
- Jobs scheduler multiinstancia con lease/lock, recuperación y no doble facturación/caducidad.
- Caffeine con maximumSize y TTL explícito no mayor a 60 minutos; TTL específico más corto donde aplique. Nunca cachear stock transaccional, reservas, precio de cotización ni estado fiscal como fuente de verdad.
- Invalidación coherente con múltiples réplicas; medir freshness/hit/miss/lag y no asumir que Kafka entrega un evento a cada instancia del mismo consumer group.

### Fase 8 — Logística, Pedidos WhatsApp y automatización n8n

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

- Fases totales: **10**.
- Fase 1: completada y validada por CI.
- Fase 3: reorganización de código, pruebas unitarias y build completados; queda pendiente la verificación visual E2E en navegador indicada en su evidencia.
- Fases pendientes de implementación: **2 y 4–10** (8 fases).
- Rama actual de trabajo: `implementacion_propuestas`; último SHA validado por CI: `1ec1e4f7a7b3ae9d9e0730a455451e4748a79667`.
- `master` no fue modificada.
- Este plan es la fuente viva de secuencia; la matriz de cobertura complementaria detalla cada uno de los 22 documentos y su asignación por fase.
