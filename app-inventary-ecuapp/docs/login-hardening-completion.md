# Cierre de hardening del login

**Rama de trabajo:** `feat/login-hardening-e2e`  
**Base:** `implementacion_propuestas`

## Cambios incluidos

- Sincronización del fin de sesión entre pestañas mediante BroadcastChannel y evento de almacenamiento como fallback.
- El cierre local se ejecuta incluso si falla el endpoint remoto de logout.
- Un HTTP 401 y la expiración local terminan la sesión y notifican a las demás pestañas.
- Se añadieron pruebas unitarias del evento de fin de sesión.
- Se añadió un E2E de navegador real con Chromium/CDP para validación de campos, credenciales incorrectas, rate limit/bloqueo, login correcto, logout, expiración automática y labels de formulario.
- El pipeline frontend ejecuta el nuevo E2E después del smoke responsive.
- Se reforzó el transporte seguro con `upgrade-insecure-requests`; se conserva CSP restrictiva (`script-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`) y cabeceras anti-clickjacking/no-sniff/referrer.
- La UI ya evita HTML crudo en el login; React renderiza mensajes como texto y no se encontraron usos de `dangerouslySetInnerHTML` en el repositorio al realizar la búsqueda.

## Alcance de las pruebas

El script `scripts/auth-e2e.mjs` ejecuta el navegador Chromium real, pero simula las respuestas HTTP de autenticación para que sea determinista y no requiera una base de datos ni cuentas reales. Verifica el ciclo del cliente, no sustituye pruebas integradas contra un backend efímero. El pipeline debe ejecutarlo antes de considerar el cierre validado.

## Decisiones de seguridad pendientes de backend/infraestructura

- El bearer token continúa siendo accesible desde JavaScript: cuando el usuario marca «Mantener sesión», se guarda en `localStorage`; en caso contrario se usa `sessionStorage`. La CSP reduce la superficie de XSS, pero no convierte el token en inaccesible ante un XSS con ejecución de código same-origin.
- La solución de mayor garantía es migrar el contrato a cookie `HttpOnly; Secure; SameSite`, con protección CSRF, CORS/origen y renovación coordinados en backend. No se implementa una migración parcial que rompa los clientes bearer.
- La recuperación autoservicio no se habilita: el backend no expone un flujo público de solicitud, verificación y consumo de token de recuperación. La pantalla conserva la indicación de contactar al administrador; no se simula un endpoint inexistente.
- MFA/passkeys, rate limit distribuido y alertas operativas siguen fuera de este cambio.

## Aceptación

- [ ] Frontend unit tests verdes.
- [ ] Build de producción verde.
- [ ] Responsive browser smoke verde.
- [ ] Authentication lifecycle browser E2E verde en CI.
- [ ] Revisión manual con lector de pantalla y teclado en Chrome/Firefox/Safari.
- [ ] Prueba integrada contra backend efímero para revocación entre pestañas.
