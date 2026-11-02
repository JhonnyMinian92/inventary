# Ejecución y solución de problemas del frontend

## Requisitos

- Node.js 22; consulta .nvmrc.
- npm 10 u 11.
- API backend levantada y con CORS configurado si frontend/API usan orígenes distintos.

## Preparación

~~~bash
cd app-inventary-ecuapp
npm ci
npm test
npm run build
npm run dev
~~~

Variables:
- VITE_API_BASE_URL: prefijo REST consumido por el cliente (por ejemplo /api/v1 o /inventary/api/v1).
- VITE_BASE_PATH: base pública de la SPA (por ejemplo / o /inventary/).

El navegador no recibe secretos de backend. No incluir JWT de servicio, clave WhatsApp ni credenciales en variables Vite con prefijo público.

## Problemas frecuentes

| Síntoma | Diagnóstico/acción |
|---|---|
| 404 de assets o ruta al refrescar | Comprueba VITE_BASE_PATH, la ruta /inventary/ y el fallback SPA de Nginx. |
| API devuelve 404 | Revisa VITE_API_BASE_URL; debe incluir el prefijo correcto de API y no duplicar /api/v1. |
| CORS/OPTIONS falla | Configura el origen exacto en CORS_ALLOWED_ORIGINS; no uses comodín. Si Nginx sirve SPA y API en mismo origen, usa la ruta proxy configurada. |
| 401 | La sesión/token expiró o fue revocado. Volver a login; no convertirlo en estado vacío. |
| 403 | El usuario no tiene el permiso. La UI puede ocultar la acción, pero el backend sigue siendo autoritativo. |
| 409 en POST | Consultar el recurso/estado y verificar si la operación ya se aplicó antes de repetir. |
| Datos aparentemente vacíos | Comprueba permiso, filtro, página, almacén y error de API; no asumas que 403 es cero registros. |
| Pedido WhatsApp no se confirma | La confirmación de pago es manual y exige permiso, referencia/método válidos, clave de idempotencia y reserva vigente. No llama a una pasarela. |
| Reembolso no disponible o inesperadamente expuesto | Bloqueo conocido en backend: la ruta de reembolsos es permitAll; no confiar en el control UI y no utilizar con datos reales hasta corregirla. |

## Reglas para cambios de frontend

- Las llamadas REST nuevas se agregan al módulo API del dominio bajo src/shared/api.
- Los componentes no implementan su propio transporte ni parsean JWT.
- Las acciones de escritura usan las claves de idempotencia previstas por el contrato backend.
- Filtros y paginación de datos extensos deben ejecutarse en backend, no sobre una muestra local.
- No se muestran cifras simuladas cuando falla una petición.
- El estado de venta, documento fiscal, cobro, devolución y cumplimiento debe permanecer separado.

## Validación en CI

En SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481, [Frontend Build 41385132616](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132616) aprobó 26 pruebas, build y smoke browser de login a 360/768/1280 px. Ese smoke no navega por los flujos autenticados completos.