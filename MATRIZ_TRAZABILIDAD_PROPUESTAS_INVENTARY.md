# Matriz de trazabilidad de las 22 propuestas — Inventary

**Fecha de análisis:** 30 de octubre de 2026  
**Rama revisada:** implementacion_propuestas  
**Plan de ejecución:** PLAN_EJECUCION_PROPUESTAS_INVENTARY.md  
**Propósito:** demostrar dónde se implementa cada requisito y evitar que dos propuestas contradictorias generen trabajo duplicado o regresiones.

## 1. Inventario raíz verificado

GitHub devuelve exactamente **22 archivos Markdown raíz** con prefijo PROPUESTA_ o ROADMAP_: 21 propuestas y un roadmap. Los documentos se agrupan por vigencia y alcance, no por antigüedad del nombre.

| # | Documento raíz | Vigencia | Fases responsables | Cobertura consolidada |
|---:|---|---|---|---|
| 1 | PROPUESTA_UX_PRODUCTOS.md | Histórica; sustituida por V2 | 5, 9 | Se conservan las ideas compatibles de PIM, búsqueda, variantes, edición masiva, ficha de calidad y asistencia humana de contenido. No se implementa su UX en conflicto con V2. |
| 2 | PROPUESTA_UX_PRODUCTOS_V2.md | Vigente | 5, 9 | Catálogo maestro; búsqueda SKU/barcode/atributos; filtros, paginación y columnas; formularios por categoría; borrador/calidad/publicación; variantes; bulk edit; imágenes; recomendación verificable; IA de borrador; telemetría/medición; datos fiscales separados del producto. |
| 3 | PROPUESTA_UX_VENTAS.md | Histórica; sustituida por V2 | 6, 7, 8 | Se retienen los escenarios y reglas no contradictorios de mostrador, devoluciones, cotización y venta; V2 fija la experiencia y el modelo vigente. |
| 4 | PROPUESTA_UX_VENTAS_V2.md | Vigente | 2, 5, 6, 7, 8 | Venta rápida; selección de almacén autorizada por contexto de usuario/caja; cotización server-side; moneda/impuestos/descuentos; snapshots de facturación y entrega; estados separados; cancelación; devolución por venta original; reembolso separado; UX de excepción y WhatsApp. |
| 5 | PROPUESTA_RBAC_ROLES_PERMISOS_AMBITOS_V1.md | Vigente | 2, 3, 4, 5, 6, 7, 8, 9, 10 | RBAC + scopes/ABAC por entidad legal, establecimiento, local/bodega y recurso; permisos por acción; deny-by-default; segregación de funciones; acceso a costes/PII/fiscalidad; pruebas de ID manipulado, listados, agregados y exportaciones. |
| 6 | PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md | Vigente | 5, 6, 8, 9 | Retiro donde existe stock, envío desde origen, retiro en otra ubicación solo tras transferencia/recepción, fulfillment groups, reservas, capacidades/horarios, preparación, despacho, tránsito, entrega, prueba y métricas; split con aceptación explícita. |
| 7 | PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md | Vigente | 5, 8, 9, 10 | Calidad de catálogo, cotización común, ranking explicable, recomendación determinista, razones, consentimiento y telemetría de exposición, métricas por canal, A/B controlado, evaluación causal/calibración antes de ML y rollback. |
| 8 | PROPUESTA_KAFKA_CACHE_CAFFEINE_V1.md | Vigente como guía técnica | 2, 7, 9, 10 | Outbox completo, event envelope versionado, Kafka opcional y operable, consumer groups, Inbox, DLT/replay/redrive, alertas/lag, caché Caffeine por familia con límites/TTL, invalidación multiinstancia y no cachear hechos transaccionales. |
| 9 | PROPUESTA_UX_SUSCRIPCIONES.md | Vigente con revisión v2 y adendas | 2, 5, 6, 7, 8, 9, 10 | Corregir “prepago” vs facturación anticipada; lifecycle actual; búsqueda/bandeja; plan y precio inmutables; venta desde flujo Ventas con caso de uso propio; cuota/factura/pago/beneficio separados; fiscalidad por cuota; canales; planes especializados y beneficios futuros condicionados. |
| 10 | PROPUESTA_ARQUITECTURA_DISTRIBUIDA_CLIENTES_HTTP_RESILIENCIA_V1.md | Vigente | 2, 7, 8, 10 | Clientes HTTP tipados; RestClient por defecto en Spring MVC; auth y secret references; host allowlist/TLS/timeouts; HTTP taxonomy; retry/backoff/jitter; Retry-After; circuit breaker/bulkhead; UNKNOWN_OUTCOME; trazas y pruebas de fallos. |
| 11 | PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md | Vigente | 2, 3, 4, 5, 6, 9 | Pestañas órdenes/recepciones/devoluciones; page/size/sort/q; DTOs summary; filtros URL; skeleton/error/empty/forbidden; permisos por acción; moneda real; resumen SQL; elegibilidad de devolución y concurrencia; límites de reversión y finanzas proveedor. |
| 12 | PROPUESTA_PLATAFORMA_INTEGRACIONES_ASINCRONAS_WEBHOOKS_V1.md | Vigente | 2, 4, 7, 8, 9 | Integration Hub inicialmente interno; partner/endpoint/policy/operation/attempt/inbox/delivery/audit; estados durables; webhooks HMAC, anti-replay, allowlist y anti-SSRF; retry y redrive; permisos y observabilidad. |
| 13 | PROPUESTA_INVENTARIO_AGENT_READY_V3.md | Vigente; sustituye V2 | 2, 3, 4, 5, 9, 10 | Centro de mando del dueño; modo operador/auditor; vistas resumen/existencias/kárdex/operaciones/auditoría; APIs agregadas y trazabilidad; frescura/unidad/moneda; reserva/stock; preparación de agentes con lectura y aprobaciones; forecasting; WMS solo si justificado. |
| 14 | PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md | Vigente | 2, 3, 4, 5, 6, 7, 8, 9, 10 | Inbox de pedidos; acordeón operativo; certeza/procedencia de datos; cotización snapshot y aceptación; pagos y cumplimiento separados; reserva, entrega, devolución, SRI; consentimiento; integración WebView/Flows/n8n y handoff humano. |
| 15 | PROPUESTA_INVENTARIO_CENTRO_CONTROL_Y_AUDITORIA_V2.md | Histórica; sustituida por Agent Ready V3 | 2, 5, 9 | Los conceptos compatibles de centro de control, existencias, movimientos, operaciones, auditoría, trazabilidad y métricas se consolidan en V3; no se implementa su roadmap antiguo como producto paralelo. |
| 16 | PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md | Vigente | 2, 3, 4, 5, 7, 9 | Perfil RUC, snapshots, establecimientos SRI, warehouses y asociaciones N:M tipadas/temporales, propietario legal del stock, contactos con roles, CRUD paginado/contextual, unidades y conversiones versionadas; fuente oficial/proveedor aprobada. |
| 17 | PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md | Vigente como contrato propuesto | 2, 5, 6, 7, 8, 9 | Inventary solicita; backend fiscal genera XML/firma/secuencial/transmite; persistencia y estado real; snapshots; callback firmado; idempotencia y reconciliación; anulación/notas de crédito; proveedor tecnológico RUC; pago separado. |
| 18 | PROPUESTA_AUDITORIA_TRAZABILIDAD_INVENTARY.md | Vigente | 2, 3, 4, 7, 8, 9, 10 | Catálogo de eventos, audit links, append-only, correlación, identidad de canal, eventos de mensajes, telemetría UI minimizada, trazabilidad de entrega, consultas de historia 360, retención, backups/restore e índices. |
| 19 | PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md | Vigente, con revisiones V2.1/V2.2/V2.3 | 2, 5, 6, 7, 8, 9, 10 | Separación backend/n8n/IA; workflows N8N-00…90 y N8N-25 WebView; credenciales, versionado y fallback; WhatsApp Flow/catalog/cart/WebView; costo por mensaje; checkout idempotente; suscripciones; eventos; handoff; notificaciones. |
| 20 | PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V1.md | Histórica; sustituida por V2 | 2, 7, 9, 10 | Se conservan sus necesidades no contradictorias de reportes y conciliación, pero las fórmulas/ámbitos/modelo y roadmap vigentes se rigen por V2. |
| 21 | PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V2.md | Vigente | 2, 5, 6, 7, 9, 10 | Command center gerencial/operativo/fiscal; constructor 2–4 dominios; métricas definidas; joins seguros; reportes por scope/currency; fiscal reconciliation; SRI workpapers versionados; no doble conteo de N:M; libros y fuentes faltantes. |
| 22 | ROADMAP_CIERRE_ARQUITECTURA_INVENTARY_PRE_V2.md | Vigente como secuencia transversal | 2, 4, 7, 9, 10 | Definition of Ready/Done; integridad P0; ámbitos; idempotencia; eventos; Kafka/webhooks/clientes HTTP; scheduler; retry/DLT; caché; datos maestros; fiscal/contable; operación y gate de V2. |

## 2. Documentos adicionales que también gobiernan la implementación

No forman parte de los 22 raíz, pero son especificaciones obligatorias:

| Documento | Fase principal | Cómo se usa |
|---|---:|---|
| app-inventary-ecuapp/docs/PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md | 3 | Shell, router, providers, features/entities/widgets/shared, App.jsx delgado, API, formularios, estilos, accesibilidad y migración gradual. |
| microservices-inventary-ecuapp/docs/PROPUESTA_JERARQUIA_ARQUITECTURA_BACKEND_V1.md | 4 | API/DTOs, aplicación, dominio, infraestructura, platform/shared, catálogo piloto y reglas de dependencias del monolito modular. |
| AUDITORIA_BUGS_ERRORES_INCONSISTENCIAS.md | 1, 5, 6, 7, 8 | Hallazgos históricos y estado de corrección; se debe revisar su snapshot/HEAD antes de llamar vigente un bug. |
| AUDITORIA_TECNICA_PENDIENTES.md | 2–10 | Checklist de pruebas, seguridad, concurrencia, persistencia, APIs, config y operación. Gran parte del cuerpo corresponde a un snapshot histórico; no usarlo como inventario vivo sin revalidación. |
| AUDITORIA_ARCHIVOS_BASURA_Y_CODIGO_MUERTO.md | 1, 3, 4 | Identifica 19 artefactos generados ya retirados; no declara clases Java/React muertas sin evidencia estática/dinámica suficiente. |

## 3. Mapa de dependencias entre fases

1. Fase 1 entrega línea base y CI reproducible.
2. Fase 2 bloquea las operaciones por permisos/scope y demuestra invariantes de stock, coste, dinero, idempotencia y auditoría.
3. Fases 3 y 4 organizan los dos lados del sistema sin alterar contratos; la arquitectura se aplica antes de ampliar dominios masivamente.
4. Fase 5 define ownership, datos maestros, unidades, variantes y almacenes; de ello dependen la venta autorizada, la logística y los informes.
5. Fase 6 cierra las operaciones internas y sus snapshots. No se puede integrar fiscalidad bien si no se sabe exactamente qué venta/cliente/línea se confirmó.
6. Fase 7 establece la verdad fiscal/externa y los estados durables; las notificaciones, reportes y cuotas deben basarse en estos resultados.
7. Fase 8 consume contratos de stock, scope, venta, pagos y fiscalidad para cumplir pedidos sin promesas falsas.
8. Fase 9 consume eventos y documentos ya confiables para KPI, conciliación, analítica y motor comercial.
9. Fase 10 agrega autonomía/ML/WMS y aprueba producción solo cuando seguridad, operaciones y pruebas lo soportan.

## 4. Reglas para evitar trabajo paralelo contradictorio

- No construir primero UI para estados/endpoints que no existen. Crear contrato y pruebas antes de habilitar acción.
- No introducir una entidad SRI dentro de Warehouse ni inferir emisor a partir de almacén.
- No convertir una devolución a proveedor/cliente en nota de crédito/reembolso de forma automática.
- No mostrar paid, authorized, ready, shipped o delivered sin el hecho y evidencia correspondiente.
- No mostrar descuento, crédito, envío, múltiples orígenes, pago al retirar, contra entrega, renovación automática, beneficios o consumo si el backend no puede validarlo y persistirlo.
- No agregar listAllPages para alimentar tablas/KPI; los listados usan page/cursor y los KPI usan agregados en backend.
- No dividir backend en microservicios ni introducir Redis, ML, A2A o una nueva librería frontend sin medir la necesidad y aprobación técnica.
- No construir modelos de recomendación/abandono/uplift con telemetría sin consentimiento/finalidad, exposición real, volumen y evaluación temporal/causal.
- No publicar ni desplegar n8n/Meta/Flow/WebView hasta verificar credenciales, permisos, elegibilidad y políticas en la cuenta real.
- Los documentos históricos permanecen como referencia; no vuelven a ser una especificación competidora.

## 5. Definición de cobertura completa

La matriz se considera cubierta cuando cada requisito:
- tiene fase y entregable asignados;
- tiene fuente de verdad y owner;
- conserva sus precondiciones/decisiones externas;
- tiene pruebas de éxito, fallo, autorización, concurrencia y recuperación cuando corresponda;
- tiene criterio de aceptación y evidencia del SHA final.

Un requisito condicionado por proveedor, norma, datos o decisión de negocio no se elimina ni se marca como terminado: queda como bloqueado/condicionado dentro de su fase y no se habilita en la UI hasta resolver la dependencia.
