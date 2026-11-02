# 26 — Arquitectura y patrones actuales

**Código auditado:** dc028a346ccf7b94cfbb60b4fb6cd5493518b481.

## 1. Topología efectiva

Inventary es un monolito modular Spring Boot conectado a PostgreSQL, acompañado de una SPA React/Vite. REST, GraphQL, seguridad, jobs y acceso JPA corren en el mismo proceso backend. La carpeta con prefijo “microservices” es un nombre histórico; no existen servicios independientes por dominio.

~~~text
Navegador
  └── SPA React/Vite
       └── cliente HTTP compartido
            └── Nginx/proxy (Docker)
                 └── Spring Boot API
                      ├── Auth/RBAC/scopes
                      ├── Catalog / Partners / Warehouse
                      ├── Inventory / Purchasing / Sales / Returns
                      ├── Subscriptions / WhatsApp
                      ├── Taxpayer / Tax / Refund (con bloqueos de seguridad documentados)
                      ├── Reports / Audit / Integration / Realtime
                      ├── JPA + Flyway
                      └── PostgreSQL
~~~

Outbox/Kafka es transporte opcional; n8n es una integración/servicio externo del Compose raíz, no un microservicio de dominio interno.

## 2. Estructura real del frontend

- src/app: composición, providers, sesión, router y workspace.
- src/pages: pantallas por ruta.
- src/features: 12 áreas funcionales.
- src/entities: modelos y componentes de entidades.
- src/shared/api: transporte HTTP y APIs por dominio.
- src/shared/ui, src/ui: controles neutrales y feedback.
- src/components, src/widgets: gráficos y composición visual reutilizable.
- src/security, src/hooks, src/utils: cifrado de login, hooks y utilidades.

Las fachadas src/api.js y src/router.js mantienen compatibilidad. No son el lugar preferido para añadir lógica nueva.

## 3. Estructura real del backend

Paquetes de dominio principales:
- auth, config, common, domain
- catalog, partners, warehouse, taxpayer, tax
- inventory, purchasing, sales, returns, refund, subscriptions
- reports, audit, media, whatsapp
- integration, realtime

Persistencia: JPA repositories/entities, consultas SQL puntuales con JdbcTemplate y migraciones Flyway V1–V28. La fuente autoritativa del esquema es Flyway, no ddl-auto.

## 4. Separación de capas y arquitectura hexagonal

La separación hexagonal se aplica parcialmente al catálogo: casos de uso, comandos/resultados, puerto de persistencia y adaptador JPA. En varios dominios los servicios consumen repositorios Spring Data y entidades JPA de forma directa; existen dependencias cruzadas entre paquetes.

No afirmar arquitectura hexagonal completa. La evolución correcta es por cortes verticales con DTOs/puertos claros, no una reescritura total.

## 5. Patrones presentes

- JWT Resource Server, RBAC y scopes de almacén/contribuyente.
- Transacciones locales en comandos de inventario/compras/ventas.
- Bloqueos de fila, restricciones de base e idempotencia.
- Movimientos de inventario append-only y ledger de valoración.
- Outbox/Inbox, envelopes versionados y deduplicación por eventId.
- Maker-checker en conteos; segregación en aprobación de compras y cancelaciones.
- DTOs REST, errores Problem Details y correlación X-Request-ID.
- Paginación server-side, allowlists de sort en controladores que la implementan.
- Dashboard SSE de señales; la señal dispara nuevas consultas, no es una proyección distribuida.

## 6. Límites arquitectónicos y operativos

- Kafka permanece apagado por defecto. El consumidor actual invalida cache de catálogo público.
- Outbox entrega al menos una vez; no declarar exactly-once.
- V28 crea estados/tablas y endpoints de administración para operaciones y webhooks; no hay worker externo completo en el árbol.
- SSE mantiene emitters en memoria del proceso; no es broker distribuido ni feed de cambios de negocio.
- Caffeine y rate limit son locales por instancia.
- No existe aislamiento multiempresa.
- Inventario tiene una ubicación por producto/almacén; multi-bin no está implementado.
- No hay proveedor fiscal ni gateway de pagos aprobado.
- Las rutas tax y refunds están permitAll en SecurityConfig; este defecto debe resolverse antes de exposición.

## 7. Transacciones e inventario

El servidor valida nuevamente disponibilidad, bloquea/serializa saldos, crea documentos y movimientos, actualiza valoración y registra auditoría/Outbox dentro de transacciones locales. La disponibilidad consultada por UI es informativa y no reserva stock. Las operaciones externas nunca quedan atómicas solo por compartir un evento Outbox.

Las capas iniciales derivadas de coste de catálogo para existencias preexistentes son estimaciones, no reconstrucción del coste histórico contable.

## 8. Evidencia

En el SHA documentado:
- Backend: Maven Verify/PostgreSQL 16, 119 pruebas y smoke de backend Compose desde volumen vacío.
- Frontend: 26 pruebas, build y smoke responsive login a 360/768/1280.
- Los workflows no certifican E2E autenticado de cada dominio ni el Compose raíz frontend+API+n8n.

## 9. Evolución sugerida

1. Corregir de inmediato la seguridad fiscal y de reembolsos.
2. Terminar snapshots fiscales y ciclo de pagos/reembolsos antes de anunciar capacidades financieras.
3. Completar executor de operaciones y entregador de webhooks con idempotencia, retry, firma, observabilidad y sandbox.
4. Mantener límites por dominio y ampliar la arquitectura modular gradualmente.
5. Añadir pruebas de concurrencia y E2E autenticado.
6. Solo evaluar microservicios, multi-bin o agentes con requisitos y ownership demostrables.