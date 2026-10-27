> **Adenda de estado — 2026-10-26:** la Definition of Done sigue siendo obligatoria, pero la evidencia fechada 2026-10-23 no acredita por sí sola el HEAD actual. Exigir JWT/RBAC por HTTP, PostgreSQL 16, migraciones V1–V14, concurrencia e integración del frontend para el SHA de release. Usar [28 — Plan de cierre](28-plan-cierre-y-validacion.md) como checklist vigente y [24](24-estado-actual-repositorio.md) para estado y exclusiones.

# 07 — Seguridad, revisión estática y definición de hecho por API

## 1. Principio operativo

Ninguna API debe considerarse terminada solo porque compile o responda 200. Antes de marcarla como implementada, revisar control de acceso, validación, límites, integridad transaccional, errores, logs y contrato. La revisión estática reduce riesgos, pero **no demuestra que no haya bugs o vulnerabilidades**; las pruebas locales y validaciones de despliegue siguen siendo necesarias.

Marco recomendado:
- [OWASP ASVS 5.0.0](https://asvs.owasp.org/) como catálogo de controles de seguridad de aplicaciones.
- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/) para riesgos específicos de APIs.
- [Spring Security — Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) para validación de JWT.

## 2. Controles obligatorios

### Identidad y sesión

- Preferir un proveedor OIDC estable; validar firma, algoritmo permitido, issuer, audience, expiry y not-before.
- Si se emiten JWT propios: clave privada fuera del repo, rotación de llaves, TTL corto, revocación/denylist o control de sesión cuando se exija, refresh rotation y detección de reuse.
- No almacenar secretos ni refresh tokens en logs. Para browser, usar cookies Secure/HttpOnly/SameSite según el diseño; proteger CSRF si el navegador envía credenciales automáticamente.
- Rate limit en login/refresh y protección ante enumeración de usuarios, credential stuffing y fuerza bruta.
- No aceptar roles/permisos inventados desde parámetros de petición.

### Autorización

- Aplicar RBAC por acción y autorización por objeto (IDOR/BOLA), ámbito de empresa y almacén.
- Revalidar permisos en el caso de uso para operaciones de negocio críticas; no depender de guards de frontend.
- Controlar exposición por campo: costo, margen, datos de cliente y trazas solo para roles autorizados.
- Prohibir escalada indirecta mediante asignación de roles, modificación de owner/organization, propiedad de documentos o mass assignment.
- Auditor debe tener permisos de consulta y no de mutación; soporte TI accede a logs técnicos sin costos comerciales.
- Segregar aprobación y ejecución de operaciones sensibles cuando las reglas del negocio lo requieran.

### Entrada y salida

- DTOs explícitos + Bean Validation: tipo, tamaño, rango, regex acotada, elementos de arrays, profundidad/tamaño JSON y valores permitidos.
- Rechazar campos desconocidos o sensibles no permitidos; no deserializar directamente en entidades de persistencia.
- Consultas parametrizadas/ORM; SQL dinámico solo con fragmentos de orden/filtro allowlisted.
- Límite de payload, paginación, timeout de request y límites para reportes/exportaciones.
- Content-Type correcto; no permitir deserialización polimórfica insegura ni formatos no necesarios.
- Respuesta de error uniforme sin stack trace, rutas del servidor, SQL, token, secreto ni PII no autorizada.
- CORS explícito por origen; no usar wildcard con credenciales.
- Configurar límites de subida si se añaden adjuntos; validación por contenido y almacenamiento aislado.

### Integridad y negocio

- Claves idempotentes para posting, recepción, despacho, transferencia y devolución.
- Restricciones de BD: FK, unique, check, no-null y claves por ámbito.
- Actualización atómica del saldo disponible; no confiar en “consultar y después descontar” sin locking/condición.
- Ledger append-only y documentos posteados inmutables; reversiones como documentos nuevos.
- Una transacción de escritura debe incluir estado, movimientos, saldo, costo y outbox cuando corresponda.
- No llamar a terceros con una transacción DB abierta y locks críticos.
- Usar locks/reintentos acotados y reportar conflicto legible, sin bucles infinitos.

### Secretos, dependencias e infraestructura

- No commitear contraseñas, llaves, certificados, tokens o .env real.
- Secret manager/variables del entorno, permisos mínimos y rotación documentada.
- TLS en tránsito; cifrado y política de backup en reposo según el despliegue.
- Headers seguros en la capa expuesta, límites de conexión, request size, timeouts, rate limits y gestión de CORS.
- Actuator y métricas en red privada o protegidos por autenticación/autorización.
- Fijar versiones y revisar CVEs/licencias. Evitar agregar dependencia sin razón y dueño operativo.
- Separar perfiles local/ci/staging/prod; producción debe fallar al arrancar si faltan secretos esenciales o se habilita configuración insegura.
- Restauración de backups y recuperación deben quedar documentadas y verificables por el propietario de operación.

## 3. Mapa de riesgos OWASP API y mitigación

| Riesgo | Control previsto |
|---|---|
| BOLA / autorización por objeto | Comprobar ámbito y permiso al cargar cada recurso usando contexto autenticado. |
| Broken Authentication | OIDC/JWT robusto, expiración, rotación, rate limit y revocación de sesiones gestionadas por el sistema. |
| Exposición/alteración de propiedades | DTO allowlist, DTOs de salida por rol y no binding a entidades JPA. |
| Consumo ilimitado | Pagina/tamaño/rango máximos, timeout, rate limit, cuota de exportación. |
| Broken Function Level Authorization | Política por endpoint y caso de uso, incluidos comandos de approve/cancel/post. |
| Flujos sensibles abusables | Límites, idempotencia, segregación de funciones, alertas y protección de posting/devolución. |
| SSRF | No aceptar URLs arbitrarias; destinos externos preconfigurados, allowlist y bloqueo de rangos internos si existe fetching externo. |
| Configuración insegura | Perfiles por entorno, CORS explícito, Actuator protegido y secretos externos. |
| Inventario de APIs impropio | OpenAPI actual, endpoints/dependencias registrados, retirar rutas obsoletas, no publicar debug endpoints. |
| Consumo inseguro de APIs externas | Timeouts, validación de respuesta, TLS, allowlist de host, circuit breaker/retry acotado y límites. |

## 4. Checklist obligatorio al terminar **cada** API

Marcar en el PR/issue. El usuario ejecutará las pruebas locales; esta lista exige revisión del código y de la especificación.

### Contrato y validación
- [ ] Ruta, verbo, permiso, request/response y códigos HTTP documentados en OpenAPI.
- [ ] Los campos de entrada tienen tipos, tamaños, rangos, obligatoriedad y formato validados.
- [ ] Filtros, orden y paginación usan allowlist y límites máximos.
- [ ] El DTO no permite manipular ID de actor, rol, empresa, saldo, costos o totales que calcula el servidor.
- [ ] Error de validación no deja efecto parcial ni informa datos internos.

### Identidad y autorización
- [ ] La ruta exige autenticación salvo excepción documentada.
- [ ] Permiso de función y ámbito del objeto verificados desde el servidor.
- [ ] Casos de IDOR/BOLA, cambio de tenant/warehouse y exposición de costos revisados.
- [ ] La asignación de roles no permite auto-escalada ni rompe el último administrador.
- [ ] Logs de seguridad no incluyen token, contraseña, secretos o PII innecesaria.

### Persistencia y transacciones
- [ ] Consultas parametrizadas; SQL dinámico está allowlisted.
- [ ] Unicidad, FK y restricciones de integridad están implementadas en BD cuando aplique.
- [ ] Operación multi-escritura tiene una frontera transaccional explícita.
- [ ] Los errores fuerzan rollback; no hay saldo/documento/costo parcialmente actualizado.
- [ ] Idempotencia y comportamiento ante reintento están definidos para comandos.
- [ ] Concurrencia/actualización perdida evaluada; stock protegido con operación atómica.
- [ ] Documentos posteados y ledger no se editan/borran físicamente.

### Errores, desempeño y documentación
- [ ] Errores esperados se traducen a 400/401/403/404/409/422/429 según contrato.
- [ ] Error inesperado devuelve mensaje genérico + correlation ID y detalle solo en logs protegidos.
- [ ] Timeouts, límites de query y tamaño de respuesta se consideran.
- [ ] Logs, métricas y auditoría se producen sin filtrar secretos.
- [ ] Los permisos, estados y efectos secundarios están reflejados en los diagramas.

### Estado de verificación local
- [ ] No se afirma que se ejecutaron pruebas si no se ejecutaron.
- [ ] Queda registrado en el PR qué revisión se completó, qué riesgos quedan abiertos y qué debe validar el usuario localmente.

## 5. Qué significa “sin bugs o vulnerabilidades” en la práctica

No existe una revisión de código que pruebe ausencia absoluta de defectos. El objetivo del proceso será que cada endpoint tenga: reglas claras, DTO y permisos explícitos, defensa en profundidad, límites de recursos, controles de integridad, transacciones correctas, documentación y una revisión estática previa a entregarlo. El responsable debe después ejecutar pruebas funcionales, de concurrencia y de seguridad localmente antes de usar datos reales o desplegar a producción.

No se deben autoaprobar operaciones P0 como venta/despacho, posting de recepción, ajuste y devolución si algún item crítico de este checklist permanece abierto.