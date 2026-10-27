# Inventary — índice de documentación técnica

**Corte documental:** 2026-10-26. La fuente viva para el estado del código es la rama master. El commit que publica esta consolidación es documental; la validación de runtime debe registrarse por SHA en CI.

## Empezar por aquí

1. [24 — Estado actual del repositorio](24-estado-actual-repositorio.md): funcionalidades, límites y evidencia.
2. [25 — Referencia actual de APIs](25-referencia-apis.md): rutas actuales, métodos, permisos, ejemplos y errores.
3. [26 — Arquitectura, jerarquía y patrones](26-arquitectura-y-patrones-actuales.md): código real frente a arquitectura objetivo.
4. [27 — Guía de consumo](27-guia-consumo-apis.md): autenticación, curl/JSON, idempotencia, reportes.
5. [28 — Próximos pasos y Definition of Done](28-plan-cierre-y-validacion.md): riesgos, validación y criterios de salida.
6. [23 — Runbook operativo](23-runbook-operacion.md): configuración, despliegue, backup/restore, Outbox y proxy.

## Documentos de negocio y diseño

- [02 — Requisitos y alcance](02-requisitos-y-alcance.md)
- [03 — Arquitectura y patrones, decisiones históricas](03-arquitectura-y-patrones.md)
- [04 — Modelo de dominio y reglas](04-modelo-dominio-y-reglas.md)
- [05 — Catálogo de APIs e índice contractual](05-catalogo-apis.md)
- [06 — Flujos de negocio y API](06-flujos-de-negocio-y-api.md)
- [07 — Seguridad y definición de hecho](07-seguridad-y-definicion-de-hecho.md)
- [08 — Plan de trabajo](08-plan-de-trabajo.md)
- [09 — WebFlux, n8n, WhatsApp y OpenAI](09-webflux-n8n-whatsapp-openai.md)
- [11 — Escalabilidad, GraphQL, Kafka y caché](11-escalabilidad-graphql-kafka-cache-datos.md)

## Decisiones e hitos históricos

- [12 — Decisiones P01](12-decisiones-p01.md)
- [13 — Implementación P03](13-implementacion-p03-arquitectura.md)
- [14 — Implementación P04](14-implementacion-p04-esquema.md)
- [15 — Implementación P05](15-implementacion-p05-seguridad.md)
- [16 — Implementación P06](16-implementacion-p06-ventas-atomicas.md)
- [17 — Implementación P07](17-implementacion-p07-inventario-operativo.md)
- [18 — Implementación P08](18-implementacion-p08-compras-y-recepciones.md)
- [19 — Implementación P09](19-implementacion-p09-devoluciones-y-anulaciones.md)
- [20 — Implementación P10](20-implementacion-p10-transferencias-consistentes.md)
- [21 — Implementación P11](21-implementacion-p11-auditoria-documentacion-api.md)
- [22 — Entrega P12](22-entrega-p12-integracion-codigo.md)

Los documentos P03–P12 son registros de decisiones/hitos y pueden reflejar el estado de cuando se redactaron. Para estado vivo usar 24; para rutas/contratos usar 25; para arquitectura implementada usar 26. No copiar rutas de un documento histórico sin comprobar el controlador actual.

## Evidencia y operación

- [Resultado histórico de ejecución](RESULTADO_AUDITORIA_EJECUCION.md)
- [Auditoría original de pendientes](../AUDITORIA_TECNICA_PENDIENTES.md)
- [Pendientes históricos del reporte de balance](../PENDIENTES.md)
- [README backend](../README.md)
- [Auditoría de la raíz del repositorio](../../AUDITORIA_TECNICA_PENDIENTES.md)

## Regla de calidad documental

“Implementado en código” no significa “validado”. Validado requiere una ejecución reproducible contra el SHA exacto. Histórico describe una corrida anterior; Fuera de alcance describe algo no ofrecido en esta versión. Nunca marcar un resultado como exitoso por inferencia. Para cada endpoint se deben actualizar contrato, DTO, autorización, tests, guía y estado en el mismo cambio.
