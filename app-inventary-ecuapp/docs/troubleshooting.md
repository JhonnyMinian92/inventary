# Ejecución y solución de problemas

## Requisitos

- Node.js 22.12.0 o superior dentro de la línea 22.
- npm 10 u 11.
- Backend Spring Boot accesible y usuario de backend habilitado.
- Origen del frontend permitido por CORS en el backend.

La versión principal de Node está declarada en .nvmrc y los requisitos de runtime en package.json. Las dependencias directas del frontend están fijadas a versiones exactas. El archivo package-lock.json debe generarse y añadirse al repositorio durante la fase de validación, ejecutando npm install con acceso al registro de npm.

## Preparar el entorno

Desde la raíz del repositorio:

    cd app-inventary-ecuapp
    cp .env.example .env
    npm install
    npm run dev

Edita .env para apuntar al backend. La variable pública VITE_API_BASE_URL debe incluir la ruta base, normalmente /api/v1.

    VITE_API_BASE_URL=http://localhost:8080/api/v1

No pongas contraseñas, claves privadas, tokens permanentes ni secretos de servicios en variables VITE_*: Vite los incorpora al código que recibe el navegador. El token de sesión lo emite la API y se guarda en localStorage; para equipos compartidos, cierra siempre sesión al terminar.

El backend debe permitir el origen real del frontend en CORS. El origen de Vite suele ser http://localhost:5173, pero cambia cuando se despliega en otro host.

## Errores comunes

| Síntoma | Causa probable | Qué revisar |
|---|---|---|
| No conecta o muestra error de red | API apagada, URL incorrecta o política CORS | Confirma que VITE_API_BASE_URL apunta al backend y que el navegador puede alcanzar esa URL. Reinicia Vite después de cambiar .env. |
| Error 404 en casi todas las rutas | Base URL sin /api/v1 o con prefijo duplicado | Usa la URL base completa /api/v1, sin añadir rutas de recursos al valor de la variable. |
| Error 401 | Sesión vencida o credenciales inválidas | Inicia sesión de nuevo; la aplicación limpia el token cuando la API devuelve 401. |
| Error 403 | El usuario autenticado no tiene autoridad suficiente | Solicita el permiso necesario al administrador. Los informes financieros requieren reports:financial. La UI no puede concederse permisos. |
| Error 400 o validación de campo | El DTO del backend rechazó los datos | Lee el mensaje de respuesta y revisa identificadores, cantidades, límites de precisión, fechas y campos obligatorios. |
| Error 409 o conflicto de negocio | Documento en estado incompatible, stock insuficiente, código duplicado o clave de idempotencia ya usada con otro cuerpo | Actualiza los datos y revisa el estado del documento antes de reintentar. No fuerces una repetición ciega de una operación financiera. |
| No aparecen registros esperados | Filtros activos, permisos o listado paginado | Revisa filtros y permisos; confirma también el almacén, rango de fechas y página que devuelve el backend. |
| Un cambio en .env no se refleja | Vite conserva la configuración del proceso actual | Detén y vuelve a iniciar el servidor de desarrollo. |

## Reglas de operación

- Crear una orden de compra no modifica existencias. El stock aumenta cuando se contabiliza una recepción aprobada.
- Una venta, devolución, ajuste, traslado o conteo debe registrarse mediante su endpoint. No cambies existencias editando el estado local del frontend.
- La aprobación de compra y la aprobación de una anulación pueden requerir permisos específicos o un usuario autorizado distinto, según las reglas del backend.
- Las acciones transaccionales incluyen Idempotency-Key. La aplicación reutiliza temporalmente la misma clave para una solicitud idéntica después de un fallo de red o error temporal (5xx/429), durante el proceso actual del navegador. La caché se pierde al recargar la página; si la respuesta de una operación quedó en duda después de una recarga, consulta primero el documento o los movimientos del backend antes de repetirla.
- El permiso mostrado en la UI es una ayuda de experiencia. El backend sigue siendo el único responsable de autorizar cada petición.

## Validación final

Una vez congelado todo el código fuente, ejecutar desde app-inventary-ecuapp:

    npm install
    npm run build
    npm run preview

Después se deben ejecutar pruebas automatizadas y escenarios end-to-end contra el backend y PostgreSQL. La compilación y las pruebas no forman parte de la fase de construcción del código fuente descrita aquí.
