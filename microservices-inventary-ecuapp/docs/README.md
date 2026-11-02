# Índice de documentación técnica de Inventary

**Código auditado:** rama implementacion_propuestas, SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481. Los checks de CI enlazados en el estado verificable corresponden a ese SHA.

## Fuente operativa vigente

1. [24 — Estado actual verificable](24-estado-actual-repositorio.md): funcionalidades, brechas, evidencia y límites reales.
2. [25 — Referencia de APIs](25-referencia-apis.md): rutas existentes, permisos, excepciones conocidas y APIs sin UI.
3. [26 — Arquitectura y patrones actuales](26-arquitectura-y-patrones-actuales.md): topología y estructura real del monolito modular.
4. [27 — Guía de consumo de APIs](27-guia-consumo-apis.md): autenticación, paginación, idempotencia, filtros y errores.
5. [28 — Plan de cierre y Definition of Done](28-plan-cierre-y-validacion.md): prioridades, riesgos, criterios de salida y evidencia requerida.
6. [23 — Runbook operativo](23-runbook-operacion.md): variables, despliegue, proxy, backup/restore y Outbox. Confirmar valores con el Compose y perfiles actuales antes de ejecutarlo.
7. [29 — Fase 5, datos maestros](29-implementacion-fase5-datos-maestros.md).
8. [30 — Fase 6, operaciones comerciales](30-implementacion-fase6-operaciones-comerciales.md).
9. [31 — Fase 7, integraciones durables](31-implementacion-fase7-integraciones-durables.md).

## Documentos de negocio y diseño

- [02 — Requisitos y alcance](02-requisitos-y-alcance.md)
- [03 — Arquitectura y patrones propuestos](03-arquitectura-y-patrones.md)
- [04 — Modelo de dominio y reglas](04-modelo-dominio-y-reglas.md)
- [05 — Catálogo inicial de APIs](05-catalogo-apis.md)
- [06 — Flujos de negocio](06-flujos-de-negocio-y-api.md)
- [07 — Seguridad y definición de hecho](07-seguridad-y-definicion-de-hecho.md)
- [08 — Plan de trabajo inicial](08-plan-de-trabajo.md)
- [09–11 — WebFlux, WhatsApp, GraphQL, Kafka y escalabilidad](09-webflux-n8n-whatsapp-openai.md), (10-estimacion-agente-ia-tokens.md), (11-escalabilidad-graphql-kafka-cache-datos.md).

## Hitos e historia

Los documentos 12–23 describen decisiones e hitos de implementación P01–P12. Son evidencia histórica, no la referencia primaria del código posterior. El documento 29-catalogo-apis-propuesta-historica.md también es archivo histórico y no debe emplearse como contrato vigente.

El resultado de auditoría de ejecución conserva un snapshot anterior y su evidencia; no sustituye los runs actuales de Actions.

## Regla de calidad documental

- Estado del código y contrato vivo: documentos 24–28 y 29–31, más README y CI.
- Diseño futuro: propuestas de la raíz, siempre etiquetadas como propuesta.
- Histórico: documentos P01–P12, auditorías antiguas y catálogos históricos.
- Una clase, tabla o endpoint no demuestra un flujo completo ni su autorización.
- Toda afirmación de validación debe indicar SHA, comando/entorno, resultado y enlace al run correspondiente.
- No afirmar seguridad productiva mientras existan excepciones permitAll en rutas fiscales y de reembolso.