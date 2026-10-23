# 02 — Alcance, requerimientos y criterios de aceptación

## 1. Estado y alcance

El backend revisado solo arranca Spring Boot. Este documento establece lo que se debe construir; no certifica que ya exista.

### En alcance propuesto para el MVP

- API REST versionada y documentación OpenAPI.
- Seguridad, autenticación integrada con un proveedor OIDC preferentemente, usuarios, roles y permisos.
- Catálogo de productos, variantes/SKU, servicios, categorías, unidades de medida y atributos dinámicos.
- Empresas/tenants si se confirma operación multiempresa; los límites de empresa se deben decidir antes de crear tablas y políticas.
- Almacenes, ubicaciones, cantidades físicas, reservas, disponibilidad y libro de movimientos.
- Ajustes, conteos y transferencias entre ubicaciones.
- Proveedores, órdenes de compra, recepción total/parcial y devoluciones de compra.
- Clientes, órdenes de venta, validación/reserva de stock, despacho y devoluciones.
- Auditoría de acciones relevantes, consultas, reportes de operación, health/observabilidad.
- Migraciones de base de datos, configuración segura, manejo uniforme de errores y control de concurrencia.

### No se considera parte del MVP sin aprobación explícita

- Facturación electrónica fiscal y comunicación con SRI.
- Libro contable general, impuestos, conciliación bancaria o nómina.
- Manufactura/MRP, recetas o listas de materiales.
- Integraciones con marketplaces, transportistas y pasarelas de pago.
- Aplicación frontend, diseño visual o micro-frontends.
- Alta disponibilidad multi-región y operación SaaS a gran escala.
- Pruebas ejecutadas por esta tarea. El responsable las correrá localmente; se recomienda mantener casos automatizados como entregables de desarrollo aunque no se ejecuten durante esta fase documental.

## 2. Supuestos de diseño

1. Java 21 y Spring Boot del proyecto se conservan inicialmente; verificar compatibilidad de versión del framework y dependencias antes de codificar.
2. PostgreSQL es la base objetivo por la necesidad de transacciones, restricciones relacionales y atributos JSONB.
3. API REST JSON bajo /api/v1, TLS en cualquier entorno expuesto.
4. Las operaciones monetarias usan NUMERIC y BigDecimal, con moneda explícita.
5. Un documento contabilizado es inmutable en campos comerciales que afectan historial; se corrige por reversión/nota de crédito/retorno autorizado.
6. El estado agregado de stock nunca se actualiza sin registrar el movimiento dentro de la misma transacción.
7. El modo multiempresa queda por confirmar. Si se activa, cada lectura/escritura se filtra por tenant desde el contexto autenticado, no desde un parámetro libre del cliente.
8. El backend no debe devolver costo unitario, costo promedio, margen o precio de compra a roles sin permiso específico.
9. No existe garantía absoluta de “cero bugs”. La definición de hecho exige revisión estática, validación de contrato y revisión de amenazas; la ejecución de pruebas locales seguirá siendo responsabilidad del usuario.

## 3. Requerimientos funcionales (RF)

| ID | Requerimiento | Criterio de aceptación |
|---|---|---|
| RF-001 | Autenticación segura | Rechaza tokens inválidos, vencidos, revocados o con issuer/audience incorrectos; sesiones y refresh tokens siguen política definida. |
| RF-002 | Autorización RBAC | Cada endpoint y acción de dominio declara permisos. Usuario no autorizado no puede acceder por URL directa ni manipulando IDs. |
| RF-003 | Usuarios y roles | Crear, modificar, activar/desactivar y asignar permisos con validación y auditoría. No crear un último administrador inhabilitable por accidente. |
| RF-004 | Catálogo de productos/servicios | SKU/código único dentro de su ámbito; tipos y unidades coherentes; servicios excluidos de stock físico. |
| RF-005 | Atributos dinámicos | Atributos con tipos, obligatoriedad, cardinalidad, límites y opciones; rechazar valores que no satisfagan la plantilla de categoría. |
| RF-006 | Variantes | Talla/color u otras dimensiones crean SKUs identificables y su stock se contabiliza por variante. |
| RF-007 | Almacenes/ubicaciones | Cada saldo pertenece a una combinación válida de producto/variante/ubicación y empresa. |
| RF-008 | Consulta de stock | Diferencia disponible, reservado, en tránsito y físico; filtros y paginación en servidor. |
| RF-009 | Movimientos | Registrar entradas, salidas, reservas, liberaciones, transferencias, devoluciones y ajustes con actor, fuente y motivo. |
| RF-010 | Stock no negativo | Una salida concurrente no reduce disponibilidad por debajo de cero; conflicto retorna error de dominio y no deja cambios parciales. |
| RF-011 | Conteo/ajuste | Ajustes exigen motivo y permiso; diferencias quedan registradas como movimiento compensatorio. |
| RF-012 | Compra/recepción | Orden admite estados definidos, recepción parcial/total y registro de solo cantidades efectivamente aceptadas. |
| RF-013 | Venta/despacho | Al confirmar o despachar, verificar permisos, estado e inventario; la operación completa confirma o revierte íntegramente. |
| RF-014 | Devoluciones/anulaciones | Referencia el documento original; no duplica devolución y genera movimientos inversos explícitos. |
| RF-015 | Costeo | Algoritmo de valoración intercambiable y versionado; la política de costo se define a nivel de ámbito aprobado. LIFO no se activa hasta aprobación contable. |
| RF-016 | Auditoría | Operaciones sensibles producen eventos de auditoría con actor, acción, recurso, fecha, resultado y correlation ID. No guardar contraseñas, tokens o secretos. |
| RF-017 | Reportes operativos | Reportes de stock, movimientos, mínimos, compras, ventas, devoluciones y valoración con filtros acotados por permisos. |
| RF-018 | Búsqueda/paginación | Orden permitido por allowlist, tamaños máximos de página y consultas parametrizadas. |
| RF-019 | Idempotencia | Comandos de posting, recepción, venta y devolución aceptan clave idempotente o identificador de operación; repetirlos no contabiliza dos veces. |
| RF-020 | Errores consistentes | Respuestas usan un esquema uniforme, sin stack traces ni información sensible en producción. |
| RF-021 | Observabilidad | Solicitudes tienen correlation ID, logs estructurados y métricas de latencia/errores; endpoints de salud no exponen secretos. |
| RF-022 | Documentación de API | Cada operación documenta permisos, DTOs, códigos de error, efectos transaccionales e idempotencia en OpenAPI. |

## 4. Requerimientos no funcionales (RNF)

| ID | Requerimiento | Objetivo verificable propuesto |
|---|---|---|
| RNF-001 | Seguridad | Baseline OWASP ASVS 5.0.0 nivel 2 como objetivo de diseño; revisar el nivel apropiado según exposición y sensibilidad. |
| RNF-002 | Seguridad de APIs | Mitigar los riesgos OWASP API Security Top 10, especialmente autorización por objeto/función, autenticación, exposición excesiva y consumo ilimitado. |
| RNF-003 | Integridad | Ledger y saldo agregado se actualizan atómicamente; constraints en BD respaldan reglas críticas. |
| RNF-004 | Concurrencia | No perder actualizaciones ni sobre-vender ante operaciones simultáneas; conflicto explícito/reintentable cuando aplique. |
| RNF-005 | Disponibilidad | Objetivo inicial de servicio >= 99.5 % mensual en producción si la infraestructura está bajo control del proyecto; excluir mantenimiento acordado. |
| RNF-006 | Rendimiento | Conjunto de endpoints de consulta: p95 <= 300 ms en carga objetivo acordada; comandos habituales p95 <= 700 ms, sin incluir terceros. Recalibrar según hardware/dataset real. |
| RNF-007 | Escalabilidad | Backend stateless en réplicas; estado persistente en PostgreSQL/cola; límites de conexiones y timeouts configurados. |
| RNF-008 | Recuperación | Backup automatizado, retención y procedimiento documentado. Objetivo inicial RPO <= 15 min y RTO <= 4 h solo si infraestructura y presupuesto lo soportan. |
| RNF-009 | Mantenibilidad | Dominio sin dependencia directa de Spring/JPA; reglas de negocio en casos de uso/modelo y adaptadores reemplazables. |
| RNF-010 | Trazabilidad | Todo cambio de inventario puede reconstruirse desde movimientos y documento de origen. |
| RNF-011 | Compatibilidad | Versionado /api/v1; cambios incompatibles requieren nueva versión o periodo formal de deprecación. |
| RNF-012 | Privacidad | Minimización de PII, retención definida, accesos auditados y secretos fuera del repositorio. |
| RNF-013 | Accesibilidad de integración | OpenAPI, errores estables, fechas/zonas explícitas e idempotencia documentada. |
| RNF-014 | Portabilidad | Configuración por ambiente; construcción reproducible; ejecución local y empaquetada documentadas. |
| RNF-015 | Calidad de dependencias | Versiones administradas, análisis de vulnerabilidades/licencias y actualización planificada. |

Los números de rendimiento/disponibilidad son objetivos iniciales, no métricas obtenidas. Deben ratificarse con requisitos del negocio e infraestructura.

## 5. Reglas globales de aceptación

- Todo acceso a un objeto se autoriza con su ámbito y permiso, no solo con autenticación.
- Cada DTO de entrada tiene validación de formato, tamaño, rango y campos permitidos; no se enlaza directamente JSON a entidades JPA.
- Los importes se redondean según regla comercial explícita y escala definida.
- El reloj usado para auditoría es el del servidor; fechas externas se normalizan.
- Borrado físico prohibido para documentos contabilizados y movimientos; se permite inactivación lógica en maestros cuando no rompe referencias.
- Una excepción antes del commit no debe dejar saldo, ledger ni documento parcialmente persistidos.
- Respuestas no filtran stack traces, SQL, nombres internos, credenciales ni datos sensibles.
- Cada operación documenta permisos y escenarios de conflicto antes de considerarse completada.

## 6. Condiciones de salida del MVP

El backend está “completo según alcance” cuando todas las tareas P0/P1 incluidas están implementadas, migraciones y OpenAPI están sincronizadas, los controles de seguridad se revisaron, todos los endpoints tienen semántica de error e idempotencia definida y el despliegue está documentado. Esta condición no equivale a garantía de ausencia de defectos ni sustituye las pruebas locales.

## 7. Adenda: canal de venta con catálogo público, n8n y WhatsApp

Se incorporan estos requisitos funcionales adicionales a la matriz RF:

| ID | Requisito adicional | Criterio de aceptación |
|---|---|---|
| RF-023 | Catálogo público de venta | Endpoint paginado con nombre, descripción, SKU, imagen HTTPS, precio, moneda, variante y disponibilidad vendible; no incluye costos ni datos privados. |
| RF-024 | Imágenes de producto | Modelo de metadatos de imagen, URL estable o entrega de medios; rechazar rutas locales como URL para clientes/Meta. Subidas futuras deben validar tipo, tamaño y contenido. |
| RF-025 | Integración técnica n8n | Credencial de servicio con scopes limitados; endpoints para buscar catálogo, gestionar carrito, cotizar, crear y consultar pedidos. Sin acceso directo a DB. |
| RF-026 | Compra desde WhatsApp | Pedido creado solo después de una confirmación explícita del resumen por parte del cliente; la confirmación se vincula a un mensaje entrante validado. |
| RF-027 | Precio y stock autoritativos | Inventary calcula el total y revalida precio/disponibilidad al crear el pedido; nunca confía en cálculos del LLM o del cliente. |
| RF-028 | Webhook idempotente | Reintentos del proveedor/workflow no duplican mensajes procesados, reservas ni pedidos. |
| RF-029 | Estado correcto de cobro | Pedido pendiente de pago no se declara pagado; solo se marca pagado tras una señal de pago validada o una operación autorizada. |
| RF-030 | Carrito/cotización con vencimiento | La cotización tiene quoteId y expiración; al confirmar se revalida y si algo cambió, se vuelve a pedir consentimiento. |

### Requisitos de infraestructura del canal

n8n y el backend pueden estar autoalojados/locales y hablar por una red privada, pero el webhook de WhatsApp necesita un callback HTTPS alcanzable desde fuera. En desarrollo puede usarse un túnel temporal protegido; en producción, un callback HTTPS estable con verificación y monitoreo. La IA utiliza OpenAI API, por tanto ese componente no es inferencia 100 % local aunque n8n esté autoalojado.

El diagrama, contratos propuestos y flujo transaccional están en [09 — WebFlux, n8n, WhatsApp y OpenAI](09-webflux-n8n-whatsapp-openai.md).

## 8. Adenda: escalabilidad y consistencia transaccional

| ID | Requisito adicional | Criterio de aceptación |
|---|---|---|
| RF-031 | API GraphQL de consulta | Consultas compuestas del catálogo con paginación cursor-based y DTOs públicos; todos los resolvers usan casos de uso/repositorios optimizados. |
| RF-032 | Protección GraphQL | Límite de profundidad, complejidad, aliases, tamaño de página, duración y tasa; autorización por objeto/campo y sin fuga de costos. |
| RF-033 | Eventos Kafka | Publicar eventos de negocio después del commit mediante Outbox; contratos versionados, claves de partición e idempotencia del consumidor. |
| RF-034 | Venta ACID | Pedido, líneas, reserva, ledger/saldo, idempotencia y Outbox se confirman o revierten según una frontera transaccional definida. Ninguna llamada externa/LLM permanece dentro de la transacción. |
| RF-035 | Caché por frescura | Imagen y metadatos relativamente estables tienen cache versionada; stock y checkout se revalidan desde PostgreSQL. |
| RF-036 | Gestión de conexiones | Pool global dimensionado considerando instancias de aplicación, jobs, n8n y conexiones operativas; métricas de espera y saturación. |
| RF-037 | Escalado basado en métricas | Registrar latencia, QPS, locks, pool, I/O, lag de Kafka/outbox, caché y lag de réplica antes de introducir réplicas o sharding. |
| RF-038 | Búsqueda semántica opcional | Si se aprueba, indexar embeddings reconstruibles del catálogo; resultados semánticos siempre revalidan producto, precio y stock relacional. |

Estos requisitos no prometen capacidad ilimitada ni que una base primaria baste para cualquier carga. Capacidad objetivo se confirma a partir de usuarios concurrentes, tasa de transacciones, tamaño de datos, hardware y medición.