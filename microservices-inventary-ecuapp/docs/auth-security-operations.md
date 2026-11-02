# Seguridad de autenticación: despliegue y operación

## Recuperación de contraseña por correo

- El usuario debe asociar un correo desde **Seguridad** dentro de la aplicación.
- El correo no se usa para recuperación hasta confirmar el código de verificación.
- La solicitud pública responde de forma genérica; solo las cuentas activas con correo verificado reciben el enlace.
- El token es aleatorio de 256 bits, se almacena como SHA-256, vence en 30 minutos por defecto y se consume una sola vez.
- Al restablecer la contraseña se incrementa `token_version`, revocando todas las sesiones activas.
- Configurar `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `AUTH_MAIL_FROM` y `PASSWORD_RECOVERY_FRONTEND_URL`.
- Los correos se despachan fuera del hilo HTTP para reducir filtraciones temporales sobre la existencia de cuentas. Los fallos de SMTP incrementan `inventary.auth.email{outcome=failed}` y disparan alerta; usar un relay SMTP con cola/reintentos confiables para producción.
- El frontend usa `/inventary/iniciar-sesion?token=...` (la vista de login procesa el token de query string).

## MFA TOTP

- MFA usa TOTP RFC 6238, seis dígitos, periodo de 30 segundos y ventana de tolerancia de ±1 periodo.
- El secreto se cifra con AES-GCM antes de persistirlo. Definir `MFA_ENCRYPTION_KEY` con al menos 32 caracteres aleatorios y mantener el mismo valor en todas las instancias.
- No se debe rotar `MFA_ENCRYPTION_KEY` sin un procedimiento de recifrado o una desactivación/reinscripción de los factores existentes.
- El segundo factor es obligatorio en el inicio de sesión de las cuentas que lo hayan activado.
- Los tickets de segundo factor se almacenan como hash, vencen en cinco minutos y son de un solo uso.
- Se puede utilizar cualquier aplicación compatible con TOTP (Google Authenticator, Microsoft Authenticator, Authy, etc.).

## Limitación distribuida

- El bloqueo por cuenta se persiste en PostgreSQL en `auth_login_attempt`; las instancias comparten contadores y bloqueos mediante un UPSERT atómico.
- Los límites por IP para login, desafío criptográfico, recuperación, verificación de correo y MFA se comparten mediante `auth_rate_limit_window`, también con UPSERT atómico.
- Los identificadores de cuenta e IP se almacenan como SHA-256, no como nombres de usuario ni direcciones IP en claro.
- Los límites de otras rutas públicas/WhatsApp permanecen locales; para defensa perimetral adicional se recomienda un WAF/API Gateway compartido. Confiar en IP reenviada solo cuando el proxy confiable sobrescriba las cabeceras y `FORWARD_HEADERS_STRATEGY` esté configurado correctamente.
- La migración Flyway V29 crea las tablas de seguridad y añade correo verificado a `app_user`.

## Métricas y alertas

Micrometer expone, entre otras, estas familias:
- `inventary.auth.authentication`: autenticaciones correctas y fallidas.
- `inventary.auth.authentication_errors` y `inventary.auth.authorization_errors`: rechazos del filtro de seguridad HTTP.
- `inventary.auth.login`: intentos correctos/fallidos.
- `inventary.auth.lockouts`: bloqueos por cuenta.
- `inventary.auth.rate_limited`: solicitudes rechazadas por política.
- `inventary.auth.mfa`: inscripción y verificación MFA.
- `inventary.auth.password_recovery`: solicitudes y consumos de recuperación.
- `inventary.auth.email`: entregas de correo.

El endpoint Actuator `/actuator/metrics` requiere permiso técnico/operativo. Importar `ops/prometheus/alerts-auth.yml` en Prometheus y conectar Alertmanager con el canal de guardia. Las métricas son por instancia; Prometheus debe scrapear todas las réplicas y agregar con `sum()`.

## Operación segura

- Usar HTTPS de extremo a extremo y un SMTP con TLS.
- No exponer `MFA_ENCRYPTION_KEY`, JWT secret ni credenciales SMTP en logs o frontend.
- Probar el envío de correo y el enrollment TOTP en staging antes de habilitarlo en producción.
- Revisar el runbook de alertas y configurar umbrales según el tráfico real.
