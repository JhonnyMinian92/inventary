# Inventary

Inventary es una aplicación web de EcuApp para administrar catálogo, almacenes, inventario, compras, ventas, devoluciones, suscripciones, pedidos de WhatsApp y reportes.

**Código auditado:** rama implementacion_propuestas, SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481.  
**Arquitectura efectiva:** SPA React/Vite y API Spring Boot como monolito modular; PostgreSQL es la fuente transaccional. El nombre histórico de la carpeta backend no significa que existan microservicios desplegables por separado.

> La documentación describe el código del SHA indicado. Las auditorías antiguas y las propuestas de producto se conservan como historial/diseño; no demuestran por sí mismas que una capacidad esté implementada.

## Componentes

| Componente | Tecnología | Ruta |
|---|---|---|
| Frontend | React 19, Vite 8, JavaScript/JSX | app-inventary-ecuapp |
| Backend | Java 21, Spring Boot 3.5.6, Spring MVC, JPA, Spring Security | microservices-inventary-ecuapp |
| Base de datos | PostgreSQL, Flyway V1–V28 | microservices-inventary-ecuapp/src/main/resources/db/migration |
| Orquestación local | Docker Compose, Nginx, n8n opcional | docker-compose.yml y Compose del backend |

## Funcionalidades visibles en la aplicación

- Inicio de sesión, sesión JWT, permisos efectivos y navegación protegida por capacidades.
- Dashboard con indicadores, pendientes y escena operativa; el navegador recibe señales SSE para refrescar datos.
- Catálogo de productos, categorías, atributos, unidades, conversiones, imágenes y variantes.
- Contactos de clientes/proveedores, almacenes, registro tributario manual y administración de tarifas.
- Inventario: centro de mando, existencias, movimientos, operaciones, conteos, ajustes, traslados y reposición.
- Compras: pestañas de órdenes, recepciones y devoluciones a proveedores; búsqueda y paginación de servidor.
- Ventas: pestañas de ventas, devoluciones y reembolsos; cancelaciones sujetas a aprobación.
- Suscripciones con filtros, calendario/cuotas y operaciones de facturación interna.
- Bandeja de pedidos WhatsApp con detalle, resumen de trabajo y confirmación manual de pago.
- Centro de reportes: balance histórico, ventas, compras, Kárdex, valoración y bajo mínimo; exportación CSV.

La matriz de consumo de API está en [Cobertura API del frontend](app-inventary-ecuapp/docs/api-coverage.md). Las funciones de backend sin pantalla propia están identificadas en [Referencia de APIs](microservices-inventary-ecuapp/docs/25-referencia-apis.md).

## Inicio rápido

### Frontend

Requisitos: Node.js 22 (ver .nvmrc) y npm 10 u 11.

~~~bash
cd app-inventary-ecuapp
npm ci
npm test
npm run dev
~~~

Configura VITE_API_BASE_URL para que apunte al prefijo REST del backend, normalmente /api/v1 en desarrollo directo o /inventary/api/v1 detrás del Nginx del stack raíz.

### Backend

Requisitos: Java 21 y PostgreSQL 16 para reproducir la validación de CI.

~~~bash
cd microservices-inventary-ecuapp
./mvnw -B --no-transfer-progress verify
./mvnw spring-boot:run
~~~

Configura SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD y un JWT_SECRET aleatorio de al menos 32 bytes. El arranque inicial también requiere BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD cuando la base aún no tiene usuarios. La contraseña inicial debe respetar la política de la aplicación: 8–15 caracteres, mayúscula, minúscula, número y símbolo.

### Stack raíz con Docker

El Compose de la raíz levanta API, frontend/Nginx y n8n, y conecta la API a un PostgreSQL accesible desde el anfitrión. No incluye un servicio PostgreSQL propio. Para un PostgreSQL en contenedor utiliza el Compose del directorio backend.

~~~bash
cp .env.example .env
# Ajusta credenciales, JWT_SECRET y N8N_ENCRYPTION_KEY.
docker compose config
docker compose up --build -d
~~~

## Estado de validación del código

Los checks verdes más recientes están asociados al SHA de código indicado arriba:

- [Backend CI 41385132612](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132612): Maven Verify con PostgreSQL 16, 119 pruebas sin fallos, validación de Compose del backend normal/Kafka y smoke test de la API desde un volumen PostgreSQL vacío.
- [Frontend Build 41385132616](https://github.com/JhonnyMinian92/inventary/actions/runs/41385132616): 26 pruebas, build de producción y smoke browser de login a 360, 768 y 1280 px.

El smoke browser no es una aceptación E2E de todos los flujos autenticados. Los cambios documentales posteriores no alteran el código validado.

## Límites y riesgos que no deben ocultarse

- La topología sigue siendo un monolito modular, no microservicios distribuidos.
- No existe integración aprobada con proveedor SRI ni emisión, firma y autorización real de comprobantes electrónicos.
- Los datos tributarios se importan manualmente y quedan en revisión; no validan por sí solos una condición oficial ante el SRI.
- El cálculo de impuestos no equivale a un comprobante fiscal y el snapshot tributario histórico por línea no está completo.
- No hay gateway de pagos ni libro de pagos conciliado. La confirmación de pago de pedidos WhatsApp es manual.
- Existe un modelo inicial de reembolsos, pero no representa un ciclo financiero seguro y completo.
- No hay WMS multi-bin, stock independiente por variante ni multiempresa/tenant isolation.
- Kafka está deshabilitado por defecto; el consumidor actual tiene un efecto limitado de invalidación de caché.
- La migración V28 y sus endpoints crean/administran operaciones y suscripciones de webhooks, pero todavía no hay un worker completo que ejecute operaciones externas y entregue webhooks de extremo a extremo.
- El límite de solicitudes es local por instancia; el rate limit global requiere gateway/WAF u otra coordinación distribuida.

> **Bloqueo de seguridad:** en el código auditado, SecurityConfig mantiene permitAll para /api/v1/tax/** y /api/v1/refunds/**. Los controladores correspondientes no incorporan una autorización equivalente por método. La excepción no está limitada a un perfil local. No expongas esta API a redes no confiables ni a datos reales hasta corregirla y añadir pruebas HTTP de autorización.

## Documentación vigente

- [Frontend README](app-inventary-ecuapp/README.md)
- [Arquitectura frontend](app-inventary-ecuapp/src/README_ARCHITECTURE.md)
- [Cobertura de API consumida por frontend](app-inventary-ecuapp/docs/api-coverage.md)
- [Ejecución y troubleshooting frontend](app-inventary-ecuapp/docs/troubleshooting.md)
- [README backend](microservices-inventary-ecuapp/README.md)
- [Índice técnico backend](microservices-inventary-ecuapp/docs/README.md)
- [Estado verificable por dominio](microservices-inventary-ecuapp/docs/24-estado-actual-repositorio.md)
- [Referencia de APIs](microservices-inventary-ecuapp/docs/25-referencia-apis.md)
- [Arquitectura implementada](microservices-inventary-ecuapp/docs/26-arquitectura-y-patrones-actuales.md)
- [Guía de consumo de API](microservices-inventary-ecuapp/docs/27-guia-consumo-apis.md)
- [Plan de cierre y Definition of Done](microservices-inventary-ecuapp/docs/28-plan-cierre-y-validacion.md)
- [Plan maestro de las diez fases](PLAN_EJECUCION_PROPUESTAS_INVENTARY.md)
- [Matriz de trazabilidad de propuestas](MATRIZ_TRAZABILIDAD_PROPUESTAS_INVENTARY.md)

Las propuestas de la raíz describen alcance deseado. Los documentos P01–P23 y las auditorías anteriores se conservan como antecedentes; el índice vigente identifica cuáles son historia y cuáles son referencias operativas.

## Licencia

Software propietario de EcuApp. Ver LICENSE.

## Seguimiento funcional

- [Estado funcional pendiente por pantalla (frontend + backend)](ESTADO_FUNCIONAL_POR_PANTALLA.md)
