# 23 — Runbook de operación del backend

Guía para quien despliega y opera la API. Complementa `README.md` y `docs/RESULTADO_AUDITORIA_EJECUCION.md`.

## 1. Variables de entorno

| Variable | Obligatoria | Por defecto | Uso |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | no | `jdbc:postgresql://localhost:5432/inventary` | URL JDBC. |
| `DB_USERNAME`, `DB_PASSWORD` | **sí** | — | Credenciales de PostgreSQL. |
| `JWT_SECRET` | **sí** | — | Secreto HS256 de al menos 32 bytes. Cambiarlo invalida todas las sesiones. |
| `JWT_TTL_MINUTES` | no | 60 | Vida del token (acotada entre 5 y 120). |
| `CORS_ALLOWED_ORIGINS` | no | vacío | Orígenes permitidos separados por coma; `*` está prohibido. |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` | solo con base vacía | — | Crea el primer SUPER_ADMIN. La contraseña requiere 16+ caracteres, mayúscula, minúscula, número y símbolo, y no puede contener el usuario. |
| `SPRING_PROFILES_ACTIVE` | no | — | `dev` (Swagger) o `prod` (Swagger, OpenAPI y GraphiQL apagados). |
| `SWAGGER_ENABLED` | no | false | Expone `/v3/api-docs` y `/swagger-ui.html`. |
| `WHATSAPP_INTEGRATION_API_KEY` | no | vacío | Clave de máquina para n8n (32+ bytes). Vacía = integración deshabilitada (401). |
| `WHATSAPP_QUOTE_TTL_MINUTES`, `WHATSAPP_ORDER_TTL_MINUTES` | no | 10 / 20 | Vigencia de cotización y de la reserva del pedido. |
| `KAFKA_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC` | no | false | Publicación del Outbox. Deshabilitado: los eventos quedan PENDING. |
| `REPORTS_TIME_ZONE` | no | `America/Guayaquil` | Zona IANA para los cortes diarios de reportes. |
| `PURCHASING_REQUIRE_DISTINCT_APPROVER` | no | true | Quien crea o envía una orden de compra no puede aprobarla. |
| `MAX_REQUEST_BODY_BYTES` | no | 1048576 | Cuerpos mayores reciben 413. |
| `FORWARD_HEADERS_STRATEGY` | no | `none` | `native` solo detrás de un proxy confiable que sobrescriba `X-Forwarded-For`. |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | no | — | `ecs` o `logstash` para logs JSON. |
| `DB_POOL_MAX`, `DB_POOL_MIN_IDLE` | no | 10 / 2 | Pool Hikari. |

## 2. Despliegue

1. Copia de seguridad de la base (sección 4) antes de cualquier versión con migraciones nuevas.
2. Arrancar la nueva versión: Flyway aplica las migraciones pendientes en orden y Hibernate valida el esquema (`ddl-auto=validate`). Si la validación falla, la aplicación no arranca y no modifica datos de negocio.
3. Verificar:
   - `GET /actuator/health/readiness` → `{"status":"UP"}` (usar como readiness probe).
   - `GET /actuator/health/liveness` → `{"status":"UP"}` (liveness probe).
   - `select version, success from flyway_schema_history order by installed_rank;`
4. Prueba de humo opcional (crea datos con prefijo `SMK`; usar en entornos no productivos):
   ```bash
   python scripts/smoke_test_api.py --base-url https://api.ejemplo --admin-user <usuario> \
     --admin-password '<clave>' --integration-key '<clave n8n>' --report smoke.json
   ```
   Requiere `SWAGGER_ENABLED=true` para comparar la cobertura con `/v3/api-docs`.

`flyway clean` está deshabilitado. Nunca usar `ddl-auto=update`.

## 3. Rollback

- Las migraciones son aditivas. Volver a la versión anterior del JAR **no** revierte el esquema; la versión anterior falla al validar si depende de columnas o restricciones cambiadas.
- V14 cambia `app_user.role` de CHECK a FK hacia `app_role(code)`, agrega tablas y columnas nuevas. Una versión P12 no arranca sobre un esquema V14 (las columnas nuevas no la afectan, pero `ddl-auto=validate` sí valida las tablas que conoce y pasaría; el riesgo real es de datos: roles personalizados asignados que P12 no reconoce).
- Procedimiento recomendado: restaurar el respaldo tomado antes del despliegue (sección 4) y redesplegar la versión anterior. No editar `flyway_schema_history` a mano.

## 4. Copias de seguridad y restauración

```bash
# Respaldo lógico consistente
pg_dump --format=custom --no-owner --file=inventary_$(date +%Y%m%d_%H%M).dump "$DATABASE_URL"

# Restauración en una base nueva y vacía
createdb inventary_restore
pg_restore --no-owner --dbname=inventary_restore inventary_20261023_1200.dump
```

Probar la restauración periódicamente arrancando la API contra la base restaurada (debe validar sin migraciones pendientes). Los triggers append-only se restauran con el esquema.

## 5. Outbox y Kafka

- Entrega **al menos una vez**: los consumidores deben deduplicar por `eventId` (clave del mensaje y campo del envelope).
- El relevo reclama lotes con `FOR UPDATE SKIP LOCKED` y un lease de 60 s, envía fuera de la transacción y registra el resultado. Si un nodo cae tras reclamar, otro recupera los eventos cuando vence el lease.
- Reintentos con backoff exponencial (hasta 1 h). Tras 10 intentos el evento pasa a `FAILED`.

Diagnóstico y redrive (permiso `operations:manage`, roles SUPER_ADMIN y SOPORTE_TI):

```http
GET  /api/v1/operations/outbox/stats
GET  /api/v1/operations/outbox?status=FAILED&aggregateType=SALE
GET  /api/v1/operations/outbox/{id}          # incluye lastError
POST /api/v1/operations/outbox/{id}/retry    # FAILED/PENDING -> PENDING, intentos a 0; queda auditado
```

Un evento `PUBLISHED` no se reenvía desde aquí: el consumidor debe reprocesarlo desde Kafka.

## 6. Métricas y alertas sugeridas

`/actuator/metrics` requiere `technical:read` u `operations:manage`.

| Métrica | Alerta sugerida |
|---|---|
| `inventary.outbox.events{status=failed}` | > 0 |
| `inventary.outbox.oldest.unpublished.seconds` | > 900 con Kafka habilitado |
| `http.server.requests` (status 5xx) | tasa > 1 % en 5 min |
| `http.server.requests` (status 401/403/429) | picos anómalos (posible abuso) |
| `hikaricp.connections.pending` | > 0 sostenido |
| Health readiness | DOWN |

Los eventos de auditoría `HTTP_REQUEST_FAILED` (401/403/4xx/5xx) y `HTTP_RATE_LIMITED` llevan el `requestId`, que también aparece en el cuerpo de error y en la cabecera `X-Request-ID`, para correlacionar con los logs.

## 7. Rate limit y proxy

- Límites por instancia y por dirección de cliente: login 10/min, integración WhatsApp 240/min, catálogo público y GraphQL 300/min. Respuesta 429 con `Retry-After`.
- Con varias réplicas el límite efectivo se multiplica: aplicar el límite global en el gateway o WAF.
- Detrás de un proxy, usar `FORWARD_HEADERS_STRATEGY=native` solo si el proxy sobrescribe `X-Forwarded-For`; de lo contrario un cliente podría falsificar su IP.

## 8. Idempotencia por comando

Todos los comandos que mueven stock exigen `Idempotency-Key` (máx. 120 caracteres). Misma clave y mismo contenido devuelven el resultado original; misma clave con contenido distinto devuelve 409. Las claves se guardan por ámbito (`scope`), así que la misma clave en dos comandos distintos no colisiona.

| Comando | Clave | Repetición |
|---|---|---|
| `POST /inventory/adjustments`, `/inventory/counts`, `/inventory/transfers` | obligatoria | mismo recurso |
| `POST /inventory/transfers/{id}/dispatch`, `/receive`, `/cancel`, `/reverse` | obligatoria | mismo resultado; un estado ya alcanzado no se aplica dos veces |
| `POST /purchase-orders`, `/purchase-orders/{id}/receipts`, `/purchase-returns` | obligatoria | mismo recurso |
| `POST /purchase-orders/{id}/submit`, `/approve`, `/cancel` | no | idempotentes por estado: repetir devuelve el estado actual |
| `POST /inventory/counts/{id}/post`, `/cancel` | no | idempotentes por estado |
| `POST /sales`, `/sales/{id}/request-cancellation`, `/approve-cancellation`, `/sales-returns` | obligatoria | mismo recurso |
| `POST /integrations/whatsapp/carts/{id}/quotes`, `/orders` | obligatoria | mismo recurso |
| `POST /whatsapp-orders/{id}/confirm-payment` | obligatoria | mismo resultado; otra referencia de pago → 409 |
| `PUT/DELETE` de ítems de carrito | no | idempotentes por naturaleza |

Si una petición falla (4xx/5xx) la transacción completa se revierte, incluida la reserva de la clave: el cliente puede reintentar con la misma clave.

## 9. Seguridad operativa

- Rotar `JWT_SECRET` invalida todas las sesiones (planificar ventana).
- `POST /auth/logout`, el reset de contraseña, el cambio de rol y la desactivación revocan inmediatamente los tokens del usuario.
- Desactivar un rol bloquea al instante a todos sus usuarios.
- Debe existir siempre al menos un SUPER_ADMIN activo; la API lo garantiza incluso con cambios concurrentes.
- Cambiar la contraseña del usuario creado por el bootstrap tras el primer arranque (`PUT /api/v1/auth/me/password`).
