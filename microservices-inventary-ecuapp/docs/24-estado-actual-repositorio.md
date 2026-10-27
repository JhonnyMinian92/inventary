# 24 — Estado actual verificable del repositorio

**Corte documental:** 2026-10-26. **Rama observada:** master. **Commit base inspeccionado:** 7d240c9403244e6a7c25d7f22a96fb5af7e12269.  
El presente informe describe ese árbol antes de este commit documental; no declara validado el runtime del HEAD. El commit que añade esta documentación no modifica código de aplicación.

## 1. Dictamen

Inventary es una aplicación web con frontend React/Vite y API transaccional Spring Boot/PostgreSQL. Aunque el nombre del folder backend conserva “microservices”, la topología implementada en este repositorio es un **monolito modular**, no múltiples microservicios desplegables de forma independiente.

Separar estos estados es obligatorio:
- **Implementado en código:** existen controlador, servicio/DTO/migración y reglas en el árbol.
- **Probado históricamente:** un reporte registra ejecución en un snapshot anterior.
- **Validado en HEAD:** build/test/CI reproducible asociado exactamente al SHA desplegable.
- **Fuera de alcance:** no debe ofrecerse como capacidad de esta entrega.
- **Pendiente de decisión:** necesita aprobación explícita de producto/arquitectura.

En el corte, el código estáticamente presente es amplio, pero el build del HEAD actual, la integración Docker y la validación en PostgreSQL 16 no se han confirmado para este corte. No declarar “100 % validado en producción”.

## 2. Identidad y estructura

Repositorio: https://github.com/JhonnyMinian92/inventary  
Rama principal: master. En la consulta se encontró únicamente esta rama y no estaba protegida.  
SHA base revisado: 7d240c9403244e6a7c25d7f22a96fb5af7e12269.

### Frontend: app-inventary-ecuapp

- React y React DOM 19.3.1, Vite 8.3.5, plugin React 6.1.2 y lucide-react 1.61.0.
- Node >=22.12.0 y <23; .nvmrc declara la línea 22; npm 10 u 11.
- SPA coordinada por src/App.jsx; cliente REST compartido, Bearer, errores e idempotencia temporal en src/api.js.
- UI observada: dashboard; productos/categorías/atributos; almacenes, proveedores, clientes y unidades; compras y recepción parcial; devolución a proveedor; ventas, solicitudes/aprobaciones de cancelación y devoluciones de cliente; saldos, movimientos, ajustes, conteos, traslados, reabastecimiento; reportes y CSV.
- package.json solo declara dev, build y preview; no declara test ni lint. No se encontró package-lock.json en el árbol de referencia, por lo que no hay instalación reproducible mediante npm ci.
- El token de acceso está guardado en localStorage. Revisar XSS/CSP y modelo de almacenamiento antes de producción.
- No están como pantallas completas: administración avanzada de usuarios/roles, auditoría completa, Outbox, política de costeo, GraphQL, imágenes, gestión directa de variantes y flujos conversacionales WhatsApp.

### Backend: microservices-inventary-ecuapp

- Java 21, Spring Boot 3.5.6, Spring MVC, Spring Security, Spring Data JPA/Hibernate, PostgreSQL, Flyway, Bean Validation, Actuator, Spring GraphQL, Caffeine, Spring Kafka y springdoc-openapi.
- 25 controladores HTTP observados en el árbol de referencia.
- Migraciones Flyway V1–V14. Hibernate valida el esquema; Flyway aplica migraciones incrementales.
- JWT HS256 con issuer/audience, expiración, usuario activo, versión de token y permisos consultados desde la base.
- RBAC por autoridades/permiso y restricciones de método.
- Integridad de inventario con transacciones, bloqueos, claves únicas, idempotencia, movimientos históricos y asientos/capas de valoración.
- Reportes: balance histórico, valoración, ventas, compras, kardex y stock bajo.
- Catálogo público, consultas GraphQL, integración WhatsApp/n8n, Outbox/relay opcional, auditoría y Actuator.

## 3. Estado por dominio

| Dominio | Estado en el código | Condición/límite importante |
|---|---|---|
| Login y sesión | Login, identidad, logout, cambio de contraseña | Revalidar con JWT real en HEAD |
| Usuarios/roles | Gestión de usuarios, roles y permisos | No hay UI administrativa completa |
| Catálogo | Productos, categorías, atributos dinámicos tipados/versionados | Reglas de historial y autorización |
| Variantes | SKU/barcode, combinaciones y precio | No tienen stock/costo independiente |
| Unidades | Unidades y conversiones | Validar escenarios de uso en integración |
| Terceros | Proveedores/clientes | Protección de documentos históricos |
| Almacenes | Almacenes y endpoints de ubicaciones | Sin multi-bin operativo; una ubicación operativa por producto/almacén |
| Inventario | Saldos, disponibilidad, kardex, ajustes y conteos | No modificar stock a través del CRUD de productos |
| Compras | Orden, envío/aprobación, recepción parcial, devolución | La orden sola no aumenta stock |
| Ventas | Venta multítem y cancelación con aprobación | Disponibilidad se revalida dentro de transacción |
| Devoluciones | Cliente/proveedor | Cantidades limitadas por líneas recibidas/vendidas y cantidades ya devueltas |
| Traslados | Despacho, recepción, cancelación, reversión | Revalidar concurrencia y ledger en PostgreSQL 16 |
| Costeo | FIFO/promedio y ledger | V14 estima capas iniciales con product.costPrice; no reconstruye COGS previo |
| Reportes | Seis familias de endpoints | Balance limita rango a 366 días y página a 100 |
| Auditoría | Eventos de auditoría consultables | Mantener logs sin credenciales ni cuerpos sensibles |
| WhatsApp/n8n | Catálogo, carrito, cotización, pedido, cancelación/backoffice | No es una pasarela de pago |
| Outbox/Kafka | Outbox, relay y endpoints operativos | Consumidores/proyecciones Kafka no incluidos |
| API GraphQL | Consultas públicas del catálogo | No sustituye comandos REST |
| Operación | Docker Compose, Actuator, rate limit local | Compose no aparece ejecutado en evidencia histórica |
| Fiscal/pagos | No implementado de forma fiscal | Impuesto 0, sin SRI ni pasarela/reembolso |
| Multiempresa | No implementado | No anunciar SaaS multi-tenant |
| Imágenes | URL HTTPS y metadatos | Sin carga binaria gestionada |
| Rate limit distribuido | No implementado | Límite local por instancia |

## 4. Evidencia disponible y límites

El documento histórico fechado el 2026-10-23 afirma 76 pruebas backend verdes y un smoke test de 192 llamadas que cubrió 138 operaciones documentadas. También explica que se ejecutó en una copia local que no era un repositorio Git y sin SHA inicial/final. Debe conservarse como evidencia histórica, no como certificación del HEAD.

Los workflows de frontend y backend observados son manuales. Las corridas de frontend visibles corresponden a commits anteriores al SHA de referencia. Ejecutar las pruebas sobre PostgreSQL 16 y en el mismo SHA que se publique.

## 5. Documentación autoritativa

- Endpoints, métodos, permisos y límites: [25 — Referencia de APIs](25-referencia-apis.md).
- Ejemplos reproducibles de consumo: [27 — Guía de consumo](27-guia-consumo-apis.md).
- Arquitectura, jerarquía y patrones reales: [26 — Arquitectura actual](26-arquitectura-y-patrones-actuales.md).
- Riesgos, orden de trabajo y Definition of Done: [28 — Plan de cierre](28-plan-cierre-y-validacion.md).
- Operación: [23 — Runbook](23-runbook-operacion.md).
- Índice: [documentación técnica](README.md).

Los documentos P03–P12 son registros de hitos/decisiones anteriores; no sustituyen este inventario vivo.

## 6. Política de actualización

Para cada API nueva o modificada deben actualizarse en el mismo cambio el controlador/DTO, las pruebas, la referencia API, la matriz de permisos, el ejemplo de consumo y el estado/limitación. “Implementado” describe el árbol; “validado” requiere comando, versión, resultado y SHA. Nunca marcar un resultado como exitoso por inferencia.
