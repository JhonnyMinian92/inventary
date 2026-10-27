> **Adenda de cierre — 2026-10-27.** La evidencia de este corte ha cambiado respecto a la tabla histórica inferior. El backend ha pasado `./mvnw -B --no-transfer-progress verify` contra PostgreSQL 16, `docker compose config --quiet` (incluido el perfil Kafka) y el smoke test de Compose con un volumen PostgreSQL vacío en [backend-ci 40504351485](https://github.com/JhonnyMinian92/inventary/actions/runs/40504351485), SHA `ac60c93a97c90c6384262880bf970dea813a6dce`. El ajuste posterior de limpieza del workflow se está verificando en [backend-ci 40504639896](https://github.com/JhonnyMinian92/inventary/actions/runs/40504639896), SHA `b81ce241aff62d6eabff3408c1be4e6c10eb3be7`; Maven verify y la validación de Compose están verdes en esa ejecución, el smoke test está en curso al redactar esta adenda. El frontend `npm test` y `npm run build` pasaron en [Frontend Build 40504502526](https://github.com/JhonnyMinian92/inventary/actions/runs/40504502526), SHA `26dc33cd209133be037beb13fb7990933cde394d`.

> **Estado de alcance:** implementados y cubiertos por pruebas: subida validada de imágenes PNG/JPEG con límite de tamaño y resolución, retirada que revoca la URL pública, inbox Kafka persistente con idempotencia y DLQ opcional, protección CSV contra fórmulas, y CI automatizada de backend/frontend. Kafka sigue deshabilitado por defecto y el consumidor implementado invalida caché; no es una plataforma de proyecciones analíticas. Quedan como riesgos o decisiones de producto explícitos: lockfile npm reproducible (CI usa `npm install`), revisión XSS/CSP y token en localStorage, rate limit distribuido multiinstancia, reconciliación de capas de costo iniciales con inventario real, SRI/facturación electrónica, pagos, multiempresa y picking multi-bin. Estos límites no se consideran capacidades entregadas.

# 28 — Próximos pasos, riesgos y Definition of Done

## 1. Objetivo

Cerrar la brecha entre implementación fuente, contrato documentado y evidencia del artefacto desplegable. No declarar un hallazgo resuelto por la existencia de archivos; solo se declara validado si una ejecución reproducible está ligada al SHA.

## 2. Riesgos priorizados

| Prioridad | Riesgo/condición | Estado del corte | Acción |
|---|---|---|---|
| P1 | Build y tests del HEAD actual | Sin evidencia vinculada a SHA actual | Ejecutar frontend y backend en CI y preservar logs/resumen |
| P1 | Compatibilidad PostgreSQL 16 | Informe histórico ejecutó localmente en PostgreSQL 18.6; CI/Compose declaran 16 | Tests+migraciones V1–V14 en PostgreSQL 16 |
| P1 | Balance y costeo | Código y evidencia histórica | E2E agotar/reposición del mismo producto, devolución, cancelación, kardex y conciliación |
| P1 | Tests frontend | package.json no declara test/lint | Añadir pruebas de cliente, componentes y navegador |
| P1 | Automatización de calidad | Workflows manuales | CI automática en push/PR, checks obligatorios y protección de master |
| P2 | Lockfile npm | No se encontró package-lock.json | Generarlo y versionarlo; usar npm ci en CI |
| P2 | CSV formula injection | Escapa comillas, pero requiere neutralizar fórmulas de spreadsheet | Sanear valores iniciados por =, +, - o @ y probar |
| P2 | Categoría + esquema en dos peticiones | Un paso puede persistir cuando falla el segundo | Endpoint transaccional o compensación documentada y probada |
| P2 | Token en localStorage | Implementado en cliente web | Revisar CSP, XSS, dependencias y aislamiento de origen |
| P2 | Rate limit multiinstancia | Filtro local | Gateway/WAF distribuido cuando escale a varias réplicas |
| P2 | Datos de smoke | El informe histórico indica filas SMK y usuario admin en base de prueba | No reutilizar esa base con datos reales; backup/limpieza aprobada |
| P2 | Valor inicial de coste | V14 estima capas previas desde costPrice | Conciliar con inventario real antes de usar como contabilidad |
| P2 | Documentación heredada | Algunos MD reflejan diseños previos | Usar docs/24–28 como referencia viva; marcar históricos |
| P3 | Hexagonal parcial | Es un monolito modular con puertos en parte del catálogo | Evolución incremental y justificada |
| Alcance | Pago/SRI/tenant/variant stock/multi-bin/consumidores Kafka | No implementado | No anunciar soporte; requerir decisión de arquitectura |

## 3. Orden de ejecución

### Fase A — Baseline

1. Registrar rama, git status y SHA; no sobrescribir cambios locales.
2. Frontend con Node/npm declarados: instalar, build y añadir pruebas.
3. Backend con Java 21: ./mvnw -B verify contra PostgreSQL 16.
4. Migrar V1–V14 desde cero y actualizar base de prueba desde V13.
5. Ejecutar smoke HTTP con credenciales desechables.
6. Registrar los comandos, versiones, SHA y resultados.

Aceptación: sin desactivar controles, seguridad, migraciones ni aserciones.

### Fase B — Flujos críticos

1. Crear producto, categoría y atributos; probar tipos inválidos.
2. Crear compra, someter/aprobar, recepción parcial y restante.
3. Vender todo el saldo y verificar que una venta concurrente se rechaza.
4. Comprar de nuevo el mismo SKU tras agotamiento.
5. Devoluciones de cliente y proveedor con cantidades límites.
6. Solicitud/aprobación de anulación por actores distintos.
7. Despachar/recibir/cancelar/revertir transferencia y conciliar saldos.
8. Consultar balance, kardex y valoración antes y después de la reposición.

Aceptación: sin sobreventa, duplicación de movimientos, diferencias de ledger ni uso de costo vigente incorrecto para valorar movimientos históricos nuevos.

### Fase C — Frontend

1. Versionar package-lock.json y usar npm ci.
2. Probar cliente REST: bearer, 401, 403, Problem Details, requestId e idempotencia.
3. Probar formularios, estados de carga/vacío/error/permiso, filtros y diálogos.
4. E2E navegador: compra/recepción → venta total → reposición → balance.
5. Probar que el usuario sin permiso financiero no ve datos de costo.
6. Probar CSV con fórmulas, comas, comillas, saltos de línea, Unicode y decimales.

### Fase D — Seguridad/operación

1. Rotar secretos y validar JWT/CORS; no usar valores de ejemplo.
2. Revisar health/readiness/liveness y proteger info/metrics/Swagger.
3. Ejecutar Docker Compose e imagen de verdad; comprobar usuario no root, puertos, healthchecks y persistencia.
4. Probar proxy/headers y asegurar que el cliente no falsifique X-Forwarded-For.
5. Ensayar backup y restore en base aislada.
6. Añadir rate limit distribuido si hay más de una instancia.

### Fase E — Gobernanza

1. Actualizar estado y limitaciones de cada API al cambiar código.
2. Actualizar referencia API, arquitectura, permisos, ejemplos y tests en el mismo PR.
3. Mantener identificados los documentos P03–P12 como hitos históricos.
4. Cambiar workflows a push/pull_request, bloquear merge con checks fallidos y proteger master.
5. Crear release/tag solo tras cumplir todos los criterios.

## 4. Definition of Done para una API

- Método/ruta, DTO, status, errores, filtros, paginación, orden y zona documentados.
- DTO valida sintaxis y reglas de negocio; campos desconocidos no se aceptan silenciosamente.
- 401, 403, permiso válido/no válido probados por HTTP.
- DTO no expone costo ni PII a usuarios sin permiso.
- Transacción y rollback validados; FK, unicidad, restricciones y migración verificadas.
- Idempotencia de reintento/clave conflictiva probada donde aplica.
- Concurrencia y límites de cantidad probados en comandos de inventario.
- Auditoría de acciones sensibles sin secretos.
- Errores uniformes, requestId y sin stack trace.
- Consumidor frontend/integración y documentación actualizados.
- Evidencia enlazada a versión, comando, entorno y SHA.

## 5. Definition of Done del repositorio

- [ ] SHA final de master registrado y revisado.
- [ ] Frontend build y tests verdes.
- [ ] Backend mvn verify verde con PostgreSQL 16.
- [ ] Flyway V1–V14 validado desde cero y upgrade.
- [ ] Pruebas HTTP/RBAC y concurrencia verdes.
- [ ] Smoke test de APIs y contrato OpenAPI cotejados.
- [ ] Escenario agotar → comprar de nuevo → devolución → balance conciliado.
- [ ] Docker Compose ejecutado realmente.
- [ ] CI automática, master protegido y checks requeridos.
- [ ] Secretos, backups, restore, rate limit y observabilidad revisados.
- [ ] Funciones fuera de alcance explícitas.
- [ ] README y estado de cada API no contradicen el código.

## 6. Estados de auditoría permitidos

- **Implementado en código:** la implementación puede localizarse en el árbol.
- **Validado:** las pruebas reproducibles pasan en el SHA indicado.
- **Bloqueado:** falta entorno, acceso o decisión, por lo cual no puede declararse aprobado.
- **Fuera de alcance:** exclusión aprobada y publicada.
- **Abierto:** requiere trabajo.
