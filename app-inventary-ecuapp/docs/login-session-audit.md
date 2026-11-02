# Auditoría funcional y técnica — Login y sesión

**Rama:** `implementacion_propuestas`  
**Alcance:** pantalla de login, transporte de credenciales, emisión y almacenamiento del token, carga del perfil, expiración, cierre de sesión y controles de seguridad del backend.  
**Fecha:** 2026-11-02.

## Resultado de la revisión

El flujo existente ya implementaba cifrado ECDH P-256 + HKDF-SHA256 + AES-256-GCM, desafíos de un solo uso, JWT de corta duración, invalidación por versión de token, auditoría y limitación de intentos. La revisión encontró un defecto de resiliencia del cliente: si el navegador bloqueaba o no permitía escribir en el almacenamiento web, el login podía recibir un token válido, pero las peticiones posteriores no podían recuperarlo porque el cliente HTTP solo consultaba Storage. También se detectó que la ayuda de recuperación parecía una acción funcional aunque no existe un endpoint de restablecimiento.

### Correcciones aplicadas

| Área | Corrección | Resultado esperado |
|---|---|---|
| Persistencia de sesión | Se agregó un respaldo en memoria para el token y su vencimiento cuando Storage está bloqueado o falla por cuota/permisos. | El usuario puede completar la sesión durante la pestaña actual sin depender de Storage. |
| Preferencia «Mantener sesión» | La selección de `localStorage` ya no accede a una propiedad bloqueada fuera de un `try/catch`; usa `sessionStorage` si es el único disponible. | No se rompe el login cuando localStorage está bloqueado. El respaldo alternativo no sobrevive al cierre de la pestaña. |
| Expiración | El token del respaldo en memoria se valida contra `expiresAt` igual que los tokens persistidos y se elimina al vencer. | No se conserva una sesión vencida. |
| Errores de login | Los errores ahora se presentan dentro del formulario mediante una región accesible `role=alert`; se conserva el foco en contraseña después de un fallo y se limpia la contraseña. | El usuario recibe feedback visible y accesible sin depender exclusivamente de notificaciones temporales. |
| Errores de seguridad | Se agregaron mensajes específicos para desafío expirado, cifrado obligatorio, bloqueo temporal, rate limit y navegador sin WebCrypto. | Se explica el siguiente paso sin exponer detalles criptográficos sensibles. |
| Recuperación de acceso | Se sustituyó la etiqueta ambigua «Olvidé mi contraseña» por ayuda explícita que indica que el restablecimiento automático no está habilitado y deriva al administrador. | La UI no promete una funcionalidad inexistente. |
| Movimiento reducido | Se respetan preferencias de movimiento reducido para transiciones de controles. | Mejora de accesibilidad. |
| Pruebas de cliente HTTP | Se añadieron pruebas para Storage bloqueado, token vencido y fallback de localStorage a sessionStorage. | Regresión automatizada de los defectos corregidos. |

## Flujo funcional verificado por contrato

1. El formulario valida presencia de usuario y contraseña; normaliza el usuario quitando espacios externos, pero no altera la contraseña.
2. El cliente solicita `POST /api/v1/auth/login-challenge`.
3. El cliente cifra las credenciales para ese desafío; el texto plano no se envía al endpoint de login.
4. El cliente envía `POST /api/v1/auth/login` con el objeto cifrado.
5. El backend consume el desafío de un solo uso, verifica ECDH P-256 y AES-GCM, valida las credenciales y devuelve un JWT.
6. El frontend almacena el JWT según la preferencia de sesión, con respaldo volátil si Storage no está disponible.
7. El proveedor de autenticación consulta `GET /api/v1/auth/me` y solo marca la sesión como conectada cuando recibe el perfil y permisos.
8. El backend valida firma, emisor, audiencia, expiración, usuario activo, rol activo y versión del token en cada petición protegida.
9. El frontend expira la sesión al alcanzar `expiresAt`; un 401 también elimina el token y fuerza la salida.
10. El cierre de sesión llama `POST /api/v1/auth/logout`, incrementa la versión del token del usuario y elimina el token local incluso si la petición remota falla.

## Controles del backend existentes

- Login y solicitud de desafío son los únicos métodos públicos de `/api/v1/auth/**`; perfil, logout y cambio de contraseña requieren autenticación.
- El cifrado de credenciales es obligatorio por defecto (`LOGIN_ENCRYPTION_REQUIRED=true`).
- Desafío de vida limitada, clave ECDH efímera y consumo atómico de un solo uso.
- JWT HS256 con emisor `inventary`, audiencia `inventary-api`, TTL configurable limitado a 5–120 minutos y claim de versión.
- Invalidación global de sesiones al cambiar contraseña o cerrar sesión mediante `tokenVersion`.
- Usuario inactivo o rol desactivado no puede autenticarse ni mantener una sesión válida.
- Comparación de contraseña con hash ficticio cuando el usuario no existe para reducir enumeración por tiempo.
- Bloqueo temporal por cuenta tras fallos consecutivos; usuario inexistente también recibe el mismo tratamiento.
- Rate limit por dirección para `/auth/login` y `/auth/login-challenge`, respuesta 429 con `Retry-After` y auditoría de exceso.
- Auditoría de login exitoso, fallido, bloqueos y logout sin registrar contraseña, cuerpo de credenciales ni token.
- Respuestas de seguridad con `Cache-Control: no-store` y problemas HTTP con códigos estables.

## Pruebas automatizadas relacionadas

### Frontend

- `src/security/loginCrypto.test.js`: interoperabilidad criptográfica cliente-servidor simulada, IV y claves efímeras, AAD y rechazo de esquemas/orígenes no compatibles.
- `src/shared/api/http/request.test.js`: sesión cuando Storage está bloqueado, expiración del token volátil y fallback de localStorage a sessionStorage.
- Smoke responsive del login: script `scripts/responsive-smoke.mjs` en 360, 768 y 1280 px (el smoke no sustituye pruebas de accesibilidad ni un E2E autenticado completo).

### Backend

- `LoginEncryptionApiTest`: desafío público/no cacheable, claves frescas, login cifrado de un solo uso, rechazo de replay, payload en texto plano, payload mixto, manipulación, desafío desconocido, bloqueo secuencial y bloqueo bajo fallos concurrentes.
- `AuthAdminApiTest`: perfil y permisos efectivos, política de cambio de contraseña, invalidación del token al cambiar contraseña y revocación inmediata/auditada al cerrar sesión.
- `AuthAdminApiTest` y pruebas de contrato de seguridad: complementar la verificación del ciclo de usuario/roles y protección de rutas.

**Importante:** las pruebas se consideran aprobadas únicamente cuando el workflow del commit final de esta auditoría termine en estado `success`. No interpretar el código de prueba como evidencia de ejecución.

## Matriz de aceptación manual

| Caso | Resultado esperado |
|---|---|
| Usuario y contraseña vacíos | No se llama a la API; se informa y enfoca el campo correspondiente. |
| Credenciales incorrectas | Mensaje genérico; contraseña borrada; usuario no se revela. |
| Usuario bloqueado | Mensaje temporal; no se revela si la cuenta existe. |
| Rate limit de IP | Mensaje con tiempo de espera. |
| Desafío expirado o replay | El cliente solicita un desafío nuevo una vez; si vuelve a fallar, informa el problema. |
| Origen HTTP no seguro sin WebCrypto | Se bloquea el envío y se indica HTTPS/localhost. |
| Storage bloqueado | Login y llamadas autenticadas funcionan en memoria durante la pestaña. |
| «Mantener sesión» con localStorage disponible | El token queda en localStorage hasta vencimiento/logout. |
| «Mantener sesión» con localStorage bloqueado | Se usa sessionStorage si está disponible; no se promete persistencia tras cerrar pestaña. |
| Token expirado o revocado | Se elimina localmente y se vuelve al login. |
| Usuario desactivado o rol desactivado | El backend rechaza login y sesiones existentes. |
| Logout remoto fallido | El cliente elimina igualmente el token local. |
| Navegación por teclado/lector | Labels, controles, mensajes de error y estados de foco son anunciados y operables. |
| Móvil 360 px / tablet 768 px / escritorio 1280 px | No hay scroll horizontal ni controles inaccesibles. |

## Brechas que siguen abiertas (no se deben presentar como implementadas)

1. **Recuperación/restablecimiento de contraseña:** no existe flujo backend de solicitud, verificación, token de recuperación y cambio seguro. La pantalla ahora lo declara explícitamente.
2. **MFA/passkeys:** no están implementados. El cifrado de credenciales no es un segundo factor.
3. **Rate limit y bloqueo distribuidos:** los contadores de `ApiRateLimitFilter` y `LoginAttemptGuard` son por instancia y memoria local. En despliegue con varias réplicas se necesita WAF/gateway con límite distribuido y estado de bloqueo compartido; no confiar en `X-Forwarded-For` salvo proxy de confianza.
4. **Almacenamiento del bearer token:** la opción «Mantener sesión» usa localStorage; un XSS con ejecución de JavaScript podría leerlo. La defensa requiere CSP estricta, control de dependencias y prevención de XSS. Una migración a cookie HttpOnly/SameSite requiere rediseñar CORS/CSRF y el contrato de autenticación; no debe hacerse parcialmente.
5. **E2E browser real:** falta una prueba Playwright/Cypress que cubra login correcto, rechazo, lockout, logout, expiración y Storage bloqueado contra backend efímero.
6. **Recuperación de sesión en varias pestañas:** no hay sincronización explícita de logout/rotación entre pestañas; el backend invalida tokens al cerrar sesión, pero otra pestaña se entera al recibir 401.
7. **Telemetría de autenticación:** existe auditoría de eventos; faltan métricas operativas/alertas específicas de tasa de fallos, lockouts, 429 y latencia del login.

## Criterio de cierre

La corrección del fallback de Storage y del feedback de la pantalla está implementada en esta rama. La pantalla solo debe declararse **cerrada para el alcance actual** después de que Frontend CI, Backend CI y smoke responsive del commit final estén verdes, y después de ejecutar la matriz manual. Las brechas anteriores son trabajo explícitamente pendiente, no defectos ocultos por esta auditoría.
