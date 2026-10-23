# P05 — Seguridad, identidad y RBAC

## Alcance implementado en código fuente

Esta entrega fortalece el login JWT existente, incorpora roles/permisos persistidos y aplica políticas de autorización en la capa HTTP. La migración es incremental: V1–V9 no se modifican y V10 adapta los roles existentes.

### Autenticación JWT

- Firma HS256 explícita con JWT_SECRET obligatorio y mínimo de 32 bytes.
- Validación de firma, emisor inventary, expiración, not-before y audiencia inventary-api.
- Tokens con vencimiento configurable limitado a 5–120 minutos, jti, uid y versión de sesión.
- El backend consulta el usuario activo y los permisos persistidos en cada solicitud autenticada; no confía solo en roles embebidos en el token.
- Cambiar rol, desactivar usuario o restablecer contraseña incrementa token_version e invalida tokens ya emitidos.
- Contraseñas con BCrypt strength 12. Login usa un mensaje genérico para usuario inexistente, contraseña incorrecta o usuario inactivo.
- Límite de login existente: 10 intentos por minuto por IP y por instancia; producción debe complementarlo en gateway/WAF.

### Roles y permisos

La migración V10 crea app_role, app_permission y app_role_permission, normaliza los roles históricos y añade token_version.

| Rol | Acceso principal |
|---|---|
| SUPER_ADMIN | Administración y permisos completos. |
| AUDITOR | Lectura transversal y auditoría; sin mutaciones comerciales. |
| MANAGER_ALMACEN | Catálogo, almacenes, inventario y compras. |
| VENDEDOR | Lectura de catálogo/stock y creación de ventas. |
| SOPORTE_TI | Permiso técnico de solo lectura; sin permisos financieros. |

Las autoridades de permisos (catalog:read, inventory:manage, purchasing:manage, sales:manage, audit:read, security:manage, entre otras) derivan de la matriz persistida en PostgreSQL. Se conservan alias de rol transitorios para mantener compatibilidad con anotaciones históricas durante la migración gradual. SUPER_ADMIN puede consultar permisos, crear roles personalizados y asignar permisos existentes mediante /api/v1/admin/roles y /api/v1/admin/permissions; la matriz de SUPER_ADMIN es inmutable por API.

La capa HTTP deniega por defecto y exige permisos específicos para catálogo, inventario, compras, ventas, administración y auditoría. La cancelación de ventas exige permiso de cancelación y autorización de administrador; un vendedor no puede autoaprobar la anulación. El catálogo público solo admite GET. WhatsApp continúa validando su propia clave de integración.

### Administración de roles y permisos

Todas las rutas de administración requieren el permiso security:manage. SUPER_ADMIN puede consultar la matriz y crear roles personalizados con permisos del catálogo existente:

| Verbo | Ruta | Uso |
|---|---|---|
| GET | /api/v1/admin/roles | Listar roles y permisos asignados |
| POST | /api/v1/admin/roles | Crear rol personalizado con una lista de permisos válidos |
| GET | /api/v1/admin/permissions | Consultar permisos disponibles |
| PUT | /api/v1/admin/roles/{code}/permissions | Reemplazar la matriz de permisos de un rol existente |

No se crean permisos arbitrarios desde HTTP. El rol SUPER_ADMIN y su matriz están protegidos contra cambios desde estos endpoints. Los roles personalizados pueden asignarse a usuarios mediante la API de administración de usuarios.

### Alta inicial segura

- No hay usuario ni contraseña predeterminados en el código.
- Si la tabla de usuarios está vacía, el arranque exige BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD juntos; si faltan, falla explícitamente.
- El primer administrador usa SUPER_ADMIN; la contraseña exige al menos 16 caracteres, mayúscula, minúscula, número y símbolo.
- El bootstrap solo crea un usuario con la tabla vacía y nunca crea administradores adicionales en arranques posteriores.
- La administración valida contraseñas, normaliza nombres, protege al último SUPER_ADMIN activo y audita altas, cambios y restablecimientos.
- CORS usa CORS_ALLOWED_ORIGINS explícito, sin wildcard ni credenciales de navegador. Se añaden cabeceras de seguridad.

### Variables de entorno

- JWT_SECRET: obligatorio, al menos 32 bytes aleatorios.
- JWT_TTL_MINUTES: 60 por defecto, límites efectivos de 5 a 120.
- BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD: obligatorias solo al inicializar una base sin usuarios.
- CORS_ALLOWED_ORIGINS: orígenes separados por coma; vacío por defecto.

## Límites conocidos

- El rate limit de login es por instancia; varias réplicas requieren límite en gateway/WAF o almacén distribuido.
- HS256 se mantiene por decisión del MVP. Rotación de claves y federación OIDC/RS256 quedan para una fase posterior.
- Algunas rutas históricas mantienen alias de roles mientras migran a permisos explícitos; la capa HTTP aplica permisos RBAC adicionales.
- La autorización por objeto y el aislamiento por almacén deben completarse cuando cada endpoint incorpore contexto de recurso. P01 mantiene una empresa por instalación.
- Los roles personalizados se asignan a permisos del catálogo existente; no se permite crear permisos arbitrarios desde HTTP. El rol SUPER_ADMIN y su matriz no se pueden modificar por API.

## Validación

No se ejecutaron pruebas, compilación/build, CI, arranque, migraciones ni comprobaciones de entorno. Esta entrega no afirma que el esquema migre sin errores ni que la aplicación arranque correctamente.
