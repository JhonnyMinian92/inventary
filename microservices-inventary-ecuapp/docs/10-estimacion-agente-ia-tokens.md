# 10 — Estimación del agente de IA: tiempo de ejecución y tokens

## 1. Qué se está estimando

Esta estimación es para que un agente de codificación con acceso al repositorio lea el proyecto, implemente el alcance de backend definido en esta especificación, actualice OpenAPI/documentación, haga revisión estática y corrija problemas visibles en código. No incluye ejecutar las pruebas funcionales/de integración, de acuerdo con la instrucción del propietario. Por lo mismo, no equivale a certificación de cero bugs.

La estimación cubre:
- Backend MVP del catálogo e inventario descrito en RF-001 a RF-022.
- Ventas, compras, costos, auditoría, reportes y controles transaccionales.
- Canal de venta de WhatsApp mediante APIs para n8n self-hosted y herramientas OpenAI.
- Endpoint público de catálogo con precio, descripción, imagen y disponibilidad de venta.
- Documentación, configuración local reproducible, OpenAPI y revisión estática.
- Sin frontend Angular, facturación fiscal SRI, pasarela de pagos ni contabilidad general.

## 2. Supuestos del presupuesto de agente

- Base de código inicial casi vacía: actualmente solo hay una clase de arranque Spring Boot, POM mínimo y application.yaml mínimo; no se asume que ya existen controladores o persistencia.
- El agente puede leer/escribir GitHub, modificar archivos y consultar documentación oficial; los cambios se realizan en una rama.
- Un agente técnico de buen nivel resuelve tareas siguiendo el plan sin una conversación de usuario para cada archivo. Las decisiones comerciales que requieren aprobación todavía pueden detener el progreso.
- El agente no ejecuta pruebas de aplicación, integración, carga o seguridad. Puede inspeccionar el código y documentación, pero la validación ejecutable queda al usuario.
- Token budget representa **tokens de entrada + salida acumulados a través de llamadas del agente**, e incluye contexto repetido, recuperación de archivos, explicaciones, código generado y correcciones. No representa tokens de inferencia de WhatsApp de cada cliente.
- Las estimaciones de tokens son presupuestos de planificación, no una factura ni una medición real. Dependiendo del modelo, cache, tool overhead, longitud de contexto y cantidad de correcciones, el consumo puede variar ±30–50 %.
- No es responsable convertir tokens a precio sin conocer el modelo exacto, proporción de input/cache/output y tarifas vigentes.

## 3. Estimación por tarea

Horas = tiempo activo estimado del agente por trabajo (lectura, edición, llamadas a herramientas, análisis estático y autocorrección), no horas humanas. El “tiempo de pared” depende de latencia, límites del proveedor, tareas que se pueden paralelizar y pasos de aprobación humana; no se garantiza como SLA.

| ID | Tarea del agente | Tiempo IA | Tokens totales orientativos |
|---|---|---:|---:|
| AI-01 | Leer repo, cerrar decisiones de alcance y actualizar plan detallado | 1–2 h | 15k–25k |
| AI-02 | Compatibilidad de build, dependencias, perfiles, configuración local y estructura | 1.5–3 h | 35k–60k |
| AI-03 | Arquitectura hexagonal, modelo común, errores, DTO mapping, paginación y utilidades | 2–4 h | 50k–85k |
| AI-04 | Persistencia PostgreSQL/Flyway, esquema, índices y restricciones | 2–4 h | 45k–80k |
| AI-05 | Seguridad, OIDC/resource server, RBAC, ámbitos, CORS, rate limit y secretos | 3–5 h | 70k–120k |
| AI-06 | Usuarios, roles, permisos y auditoría de administración | 2–3 h | 40k–70k |
| AI-07 | Categorías, UOM, productos, servicios, variantes y atributos dinámicos tipados | 2.5–4 h | 65k–110k |
| AI-08 | Imágenes de productos, metadatos, almacenamiento/URL y validación de medios | 1.5–3 h | 35k–65k |
| AI-09 | Almacenes, ubicaciones, balance, consulta de disponibilidad y endpoint público de catálogo | 2–3 h | 45k–75k |
| AI-10 | Ledger de stock, reserva, idempotencia y control atómico/concurrente | 4–7 h | 100k–180k |
| AI-11 | Ajustes, conteos y transferencias de stock | 3–5 h | 70k–120k |
| AI-12 | Proveedores, órdenes de compra, recepción parcial y devolución a proveedor | 3–5 h | 70k–120k |
| AI-13 | Clientes, ventas, confirmación, reserva, despacho y precios | 3.5–6 h | 90k–160k |
| AI-14 | Devoluciones de cliente, solicitudes/aprobación de anulación y reversión compensatoria | 2–4 h | 55k–95k |
| AI-15 | Valoración FIFO/promedio y capas de costo | 3–5 h | 80k–130k |
| AI-16 | Lotes, series, caducidad y FEFO (si obligatorio para el MVP) | 2–4 h | 50k–95k |
| AI-17 | Mínimos/máximos, sugerencias de reabastecimiento y reportes operativos | 2–4 h | 55k–95k |
| AI-18 | APIs seguras para n8n: carritos, cotización, creación idempotente de pedidos WhatsApp | 3–5 h | 80k–145k |
| AI-19 | Contrato de webhook/contexto de canal, estados de pedido, consentimiento y guía de workflow n8n/OpenAI | 2–4 h | 55k–95k |
| AI-20 | Auditoría consultable, logs estructurados, correlation IDs, Actuator y métricas | 2–4 h | 55k–95k |
| AI-21 | OpenAPI completo, ejemplos, errores, diagramas y documentación del desarrollador | 2–4 h | 55k–95k |
| AI-22 | Contenedores/perfiles, guía local, configuración de secretos y backup/documentación operativa | 1.5–3 h | 35k–65k |
| AI-23 | Revisión estática de seguridad, límites, autorización por objeto y controles de inventario; corrección visible | 3–6 h | 80k–155k |
| AI-24 | Reconciliar documentación con el código final y registrar pendientes del propietario | 1–2 h | 25k–45k |

### Totales actualizados para el backend MVP ampliado

- **Tiempo activo del agente:** aproximadamente 67–125 horas para implementar y autocorregir el MVP con GraphQL, Kafka/Outbox, caché y estrategia de escalado.
- **Tokens agregados de todas las tareas (sin contingencia):** aproximadamente 1.65–2.95 millones.
- **Reserva para retrabajo/contexto (aproximadamente 25 %):** 0.4–0.75 millones de tokens.
- **Presupuesto recomendado del proyecto completo:** **2.1–3.7 millones de tokens agregados**.
- **Tiempo de pared orientativo:** 9–18 días laborables de ejecución continua de un agente competente, o 3–5 semanas con aprobaciones, límites del proveedor e iteraciones.

Estos rangos son estimaciones de planificación, no una medición observada ni una garantía. AI-16 (lotes/series/FEFO) es condicional. La suma de tareas no corresponde exactamente al tiempo de pared si algunas pueden hacerse en paralelo, ni incluye pruebas ejecutadas por el agente.

## 4. Qué significa el consumo de tokens

El presupuesto total es la cantidad de tokens que el agente podría procesar en las llamadas usadas para completar el trabajo. No significa que todo sean tokens nuevos de código. El total se compone de:
- **Entrada:** instrucciones, fragmentos de código, archivos leídos y contexto repetido por herramientas.
- **Salida:** análisis, diseño, cambios de código, SQL/migraciones, configuración, OpenAPI y documentación.
- **Reiteración:** lectura de errores estáticos, ajustes de implementación, reescritura de partes y comprobación de coherencia.

El agente puede reutilizar contexto/cache cuando el proveedor lo permite; la facturación puede diferenciar input, cached input, output y tokens de razonamiento. Para estimar costo monetario, elegir primero el proveedor/modelo y aplicar sus tarifas vigentes a los conteos medidos en el uso real. Esta documentación no atribuye precios ni disponibilidad a un modelo concreto.

## 5. Sensibilidad de tiempo y tokens

| Situación | Efecto esperable sobre el presupuesto |
|---|---|
| Scope bien decidido, agente con acceso de escritura y una implementación coherente | Cerca del rango base. |
| Cambios frecuentes del modelo de negocio después de implementar entidades y migraciones | +20–50 % o más por relectura, refactor y contexto repetido. |
| Exigir múltiples microservicios desplegables en lugar del monolito modular | +25–60 % por seguridad, contratos distribuidos, despliegue, tracing y consistencia. |
| Añadir SRI/facturación fiscal electrónica, pagos o contabilidad general | Debe estimarse como proyecto separado; no queda cubierto por el rango base. |
| Herramientas de edición limitadas, falta de acceso a dependencias o contexto de agente pequeño | Más iteraciones; tiempo/token aumentan. |
| Pruebas automatizadas, concurrencia, integración y seguridad exhaustiva | No están ejecutadas aquí; incluir su creación/ejecución elevaría el presupuesto aunque son necesarias para aceptar el software. |

## 6. Entregas que debe producir el agente por tarea

- Cambios del código fuente y migraciones en commits revisables.
- Actualización del contrato OpenAPI y de los diagramas.
- Resumen del alcance realizado, archivos cambiados, controles de seguridad revisados y riesgos no resueltos.
- Lista de comandos de verificación para que el propietario los ejecute localmente, sin afirmar que se ejecutaron.
- Actualización de tareas abiertas y presupuesto de tokens observado si el entorno de agente muestra métricas fiables.

## 7. Definición correcta de “100 % completado”

El 100 % significa que el alcance acordado de backend MVP está implementado y documentado, no que el sistema sea imposible de romper. Requiere resolver decisiones de negocio, implementar todas las tareas de alcance, mantener migraciones y OpenAPI sincronizados y no dejar hallazgos críticos conocidos. Después, el propietario debe ejecutar validación local, funcional, concurrente y de seguridad antes de producción.

## 8. Fuentes técnicas usadas para estimar el diseño

- [Spring Framework: Spring MVC or WebFlux?](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html)
- [n8n: self-hosting and Docker](https://docs.n8n.io/deploy/host-n8n/install-options/install-with-docker.md)
- [n8n: WhatsApp Business Cloud](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.whatsapp)
- [OpenAI: Function Calling](https://developers.openai.com/api/docs/guides/function-calling)
- [WhatsApp Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api)

## 9. Adenda de tareas para GraphQL, Kafka, caché y escalabilidad

Esta adenda complementa y reemplaza cualquier total anterior que no incluya estas decisiones tecnológicas. Los rangos son agentes-hora activas y tokens agregados aproximados.

| ID | Tarea de agente IA | Tiempo IA | Tokens estimados |
|---|---|---:|---:|
| AI-25 | Contrato GraphQL, schema, resolvers de catálogo, paginación cursor-based y delegación a casos de uso | 2–4 h | 50k–90k |
| AI-26 | Persistencia Outbox, publicación Kafka, contratos/event versions, deduplicación e idempotencia del consumidor | 3–6 h | 80k–160k |
| AI-27 | Cache-aside Caffeine/Redis para catálogo/metadatos, TTL, invalidación por eventos y reglas de no cachear checkout | 2–4 h | 45k–100k |
| AI-28 | Configuración y métricas de HikariCP; guía de uso/compatibilidad de PgBouncer y presupuesto global de conexiones | 1–3 h | 30k–55k |
| AI-29 | Observabilidad de persistencia, índices/particionado candidato, lag de Outbox/Kafka/caché y criterios de futura lectura en replica | 2–4 h | 40k–75k |
| AI-30 | Revisión estática de boundaries transaccionales y coherencia REST/GraphQL/Kafka/Redis | 2–4 h | 35k–65k |

La cifra actualizada de 2.1–3.7 millones contempla una contingencia aproximada; el total de entrada/salida dependerá del modelo de codificación, tamaño de contexto, herramientas y retrabajo. No debe sumarse otra vez la tabla AI-25 a AI-30 sobre el nuevo total, pues esos rangos ya están incluidos en él.

No se incluyen pruebas ejecutadas por el agente, tuning a partir de una carga real ni licencias/infraestructura de producción. Tampoco se garantiza que un solo PostgreSQL aguante cualquier tráfico; esta fase implementa medidas y telemetría para decidir el escalado basándose en datos.