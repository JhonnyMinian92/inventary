# 08 — Backlog de tareas, secuencia y estimación de entrega

## 1. Cómo interpretar la estimación

La estimación cubre el **100 % del backend MVP descrito en estos documentos**, no todo lo que hace un ERP comercial ni el frontend. Es una estimación de planificación, no una fecha garantizada. Asume una persona backend senior con dedicación completa, decisiones de negocio disponibles a tiempo y sin integraciones externas fiscales/contables incluidas.

**Estimación inicial: 16–20 semanas de trabajo (80–100 días-persona)** para implementar el alcance, mantener documentación/OpenAPI, hacer revisión estática y corregir hallazgos del código. No se ejecutan las pruebas locales en este trabajo; el usuario las hará. La aceptación local, los fallos que revele y su corrección podrían ampliar el calendario. Si se confirma SaaS multiempresa, emisión SRI, pagos, contabilidad o microservicios desplegables independientemente, estimar esos alcances por separado.

## 2. Backlog priorizado

| ID | Prioridad | Tarea / entregable | Dependencia | Estimación |
|---|---|---|---|---:|
| BK-001 | P0 | Resolver decisiones de negocio bloqueantes y criterios de aceptación | Ninguna | 2–3 días |
| BK-002 | P0 | Validar versiones y actualizar POM, configuración por perfiles y build reproducible | BK-001 | 2–3 días |
| BK-003 | P0 | Base de datos, PostgreSQL, Flyway, esquema inicial e índices/restricciones | BK-001, BK-002 | 3–4 días |
| BK-004 | P0 | Crear base Hexagonal: dominio, puertos, adaptadores, mappers y errores comunes | BK-002 | 3–4 días |
| BK-005 | P0 | SecurityFilterChain, Resource Server/OIDC, RBAC, CORS, rate limiting y headers | BK-001, BK-002 | 5–7 días |
| BK-006 | P0 | Usuarios, roles, permisos, ámbito de acceso y auditoría de seguridad | BK-005, BK-003 | 4–6 días |
| BK-007 | P0 | Categorías, UOM, productos/servicios, variantes y atributos con esquema validado | BK-003, BK-004 | 5–7 días |
| BK-008 | P0 | Almacenes, ubicaciones y saldos iniciales | BK-003, BK-004 | 3–4 días |
| BK-009 | P0 | Ledger de movimientos, saldos, reservas, idempotencia y concurrencia | BK-008 | 7–9 días |
| BK-010 | P0 | Ajustes/conteos y transferencias, con revisión de integridad | BK-009 | 4–6 días |
| BK-011 | P0 | Proveedores, órdenes de compra y recepción parcial con posting atómico | BK-007, BK-009 | 5–7 días |
| BK-012 | P0 | Clientes, órdenes de venta, reserva, despacho, precios y costos sensibles | BK-007, BK-009 | 6–8 días |
| BK-013 | P0 | Devoluciones/anulación aprobada y transacciones compensatorias | BK-011, BK-012 | 4–6 días |
| BK-014 | P1 | Estrategia de valoración FIFO y promedio con registro reproducible | BK-009, BK-011, BK-012 | 5–7 días |
| BK-015 | P1 | Modelo lote/serie/caducidad y FEFO, si se confirma como requisito del MVP | BK-007, BK-009 | 4–7 días |
| BK-016 | P1 | Mínimos/máximos y sugerencias de reabastecimiento | BK-009, BK-011 | 2–3 días |
| BK-017 | P1 | Reportes operativos y valoración con control de exposición de datos | BK-011, BK-012, BK-014 | 4–6 días |
| BK-018 | P0 | Auditoría consultable, exportación acotada y protección de logs | BK-005, BK-006 | 3–4 días |
| BK-019 | P0 | OpenAPI completo, códigos de error, ejemplos y permisos | BK-005 a BK-018 | 3–5 días |
| BK-020 | P0 | Observabilidad, Actuator protegido, correlation ID, métricas y logs estructurados | BK-002, BK-005 | 2–4 días |
| BK-021 | P0 | Docker/local configuration, documentación de ejecución, migración y backup | BK-003, BK-020 | 2–4 días |
| BK-022 | P0 | Revisión estática final: seguridad, concurrencia, errores, dependencias y consistencia | BK-005 a BK-021 | 4–6 días |
| BK-023 | P0 | Consolidar observaciones locales reportadas por el usuario y corregirlas | Entrega local | Variable |

Los rangos por tarea no se deben sumar como si todas fueran secuenciales: algunas actividades se solapan y algunas quedan condicionales. Lote/serie/FEFO y Costing requieren decisiones tempranas porque afectan el modelo de datos.

## 3. Hitos

| Hito | Resultado esperado | Semana objetivo |
|---|---|---|
| H1 — Fundaciones | POM, migraciones, capas, errores y configuración por entorno | 1–2 |
| H2 — Seguridad | Identity integration, RBAC, usuarios/roles y controles básicos | 2–4 |
| H3 — Catálogo | Productos, atributos tipados, unidades, variantes y almacenes | 4–6 |
| H4 — Inventario core | Ledger, saldos, reserva, idempotencia, ajustes y transferencias | 6–9 |
| H5 — Compras/ventas | Pedidos, recepción, despacho, devoluciones y aprobaciones | 9–13 |
| H6 — Costos/reportes | FIFO/promedio aprobados, mínimos y reportes | 12–15 |
| H7 — Integración técnica | Auditoría, OpenAPI, observabilidad, despliegue/documentación | 14–17 |
| H8 — Revisión estática | Checklist por endpoint y corrección de hallazgos | 16–20 |

La secuencia exacta cambia si se confirma multiempresa, SRI, caja/pagos, multi-moneda o lote/serie con caducidad como obligación de primera versión.

## 4. Definition of Ready para iniciar cada tarea

- Requisito y responsable de decisión confirmados.
- Entidades y estados relevantes documentados.
- Roles/permisos definidos.
- Request/response y errores documentados.
- Efectos transaccionales e idempotencia descritos.
- Reglas históricas y estrategia de reversión aclaradas.
- Riesgos y dependencias anotados.

## 5. Definition of Done para cada API

- Código siguiendo capas y convenciones del dominio.
- DTOs, validación, mapeo y error uniforme.
- Autenticación, permiso de acción y control de ámbito verificados por código.
- Transacción/rollback/constraints correctas.
- Idempotencia y concurrencia revisadas para comandos críticos.
- Auditoría/correlation ID y límites de recursos.
- OpenAPI, diagrama y checklist de seguridad actualizados.
- Revisión estática sin hallazgo crítico abierto.
- Pruebas no ejecutadas por esta fase. El usuario ejecuta pruebas locales antes de aceptar la API; defectos encontrados se incorporan como nuevas tareas de corrección.

## 6. Fuera de estimación

Añadir estimación después de especificar: interfaz Angular y micro-frontends; integración fiscal SRI (firma, envío, autorización, contingencias y notas de crédito); contabilidad completa; pago/caja; sincronización con e-commerce; transportistas; fabricación/MRP; despliegue cloud productivo, alta disponibilidad y DR formal; migración de datos de un sistema anterior; operación multiregión.

## 7. Cómo medir el “100 %”

La expresión debe significar “100 % de requisitos acordados dentro del MVP”, no todo el código posible ni ausencia de defectos. El cierre requiere:

1. Matriz de RF/RNF con estado implementado y evidencia de revisión.
2. Inventario de rutas OpenAPI coincidente con controladores reales.
3. Cada endpoint con permisos, errores, validación e invariantes documentados.
4. Migraciones completas y configuración local/deploy documentada.
5. Checklist por API sin hallazgos críticos abiertos.
6. Pendientes de alcance posterior explícitamente etiquetados.
7. Validación local realizada por el usuario y observaciones resueltas antes del uso productivo.

La fecha más fiable se podrá fijar tras BK-001 y una primera entrega vertical de catálogo + movimientos, que permitirá recalibrar velocidad real.