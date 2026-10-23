# Inventary — backend

Backend planificado sobre Java 21 y Spring Boot. El código actual es un esqueleto inicial; las rutas de API incluidas en la documentación son propuestas y todavía no están implementadas.

## Documentación

- [Índice de documentación backend](docs/README.md)
- [Análisis competitivo](docs/01-analisis-competitivo.md)
- [Alcance y requerimientos](docs/02-requisitos-y-alcance.md)
- [Arquitectura y patrones](docs/03-arquitectura-y-patrones.md)
- [Modelo de dominio y reglas](docs/04-modelo-dominio-y-reglas.md)
- [Catálogo propuesto de APIs](docs/05-catalogo-apis.md)
- [Flujos de negocio y API](docs/06-flujos-de-negocio-y-api.md)
- [Seguridad y definición de hecho](docs/07-seguridad-y-definicion-de-hecho.md)
- [Backlog y estimación](docs/08-plan-de-trabajo.md)
- [WebFlux, n8n, WhatsApp y OpenAI](docs/09-webflux-n8n-whatsapp-openai.md)
- [Estimación del agente IA y tokens](docs/10-estimacion-agente-ia-tokens.md)

## Reglas de contribución

- Mantener las reglas del negocio fuera de los controladores y separadas de los adaptadores de persistencia.
- No implementar modificaciones de stock sin movimiento de ledger, control atómico de disponibilidad e idempotencia.
- Actualizar OpenAPI y los diagramas al cambiar endpoints.
- No declarar APIs como terminadas con hallazgos críticos de seguridad abiertos.
- Las pruebas no se ejecutaron al redactar la documentación; el responsable del proyecto realizará la validación local.