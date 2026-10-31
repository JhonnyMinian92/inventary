# Plan maestro de ejecución de propuestas — Inventary

**Repositorio:** JhonnyMinian92/inventary  
**Rama de trabajo:** `implementacion_propuestas`  
**Fecha de consolidación:** 30 de octubre de 2026  
**Estado:** Fase 1 implementada en código; validación CI pendiente de resultados.

## 1. Alcance revisado

Se consolidaron los 22 documentos Markdown de propuestas y roadmap del directorio raíz, junto con las propuestas de jerarquía frontend/backend y las auditorías técnicas. El plan trata los documentos históricos como contexto, no como requisitos paralelos.

### Inventario de los 22 documentos de raíz

| Documento | Vigencia / tratamiento |
|---|---|
| `PROPUESTA_UX_PRODUCTOS.md` | Histórica; sustituida por Productos V2 |
| `PROPUESTA_UX_PRODUCTOS_V2.md` | Vigente |
| `PROPUESTA_UX_VENTAS.md` | Histórica; sustituida por Ventas V2 |
| `PROPUESTA_UX_VENTAS_V2.md` | Vigente |
| `PROPUESTA_RBAC_ROLES_PERMISOS_AMBITOS_V1.md` | Vigente; base transversal de autorización |
| `PROPUESTA_LOGISTICA_RETIRO_Y_ENVIOS.md` | Vigente; depende de inventario multiubicación y pedidos |
| `PROPUESTA_MOTOR_COMERCIAL_INTELIGENTE.md` | Vigente; posterior a contratos comerciales confiables |
| `PROPUESTA_KAFKA_CACHE_CAFFEINE_V1.md` | Vigente como guía; Kafka no se presume activo |
| `PROPUESTA_UX_SUSCRIPCIONES.md` | Vigente; respetar límites del modelo actual |
| `PROPUESTA_ARQUITECTURA_DISTRIBUIDA_CLIENTES_HTTP_RESILIENCIA_V1.md` | Vigente; primero integración segura y durable |
| `PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md` | Vigente |
| `PROPUESTA_PLATAFORMA_INTEGRACIONES_ASINCRONAS_WEBHOOKS_V1.md` | Vigente; implementar primero dentro del monolito modular |
| `PROPUESTA_INVENTARIO_AGENT_READY_V3.md` | Vigente; reemplaza Inventario V2 |
| `PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md` | Vigente; IA opcional y posterior a flujos deterministas |
| `PROPUESTA_INVENTARIO_CENTRO_CONTROL_Y_AUDITORIA_V2.md` | Histórica; reemplazada por Inventario Agent Ready V3 |
| `PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md` | Vigente; separa almacén, contribuyente y establecimiento fiscal |
| `PROPUESTA_INTEGRACION_FACTURACION_ELECTRONICA_SRI_V1.md` | Vigente como contrato propuesto; requiere acordar API del backend fiscal |
| `PROPUESTA_AUDITORIA_TRAZABILIDAD_INVENTARY.md` | Vigente; ampliar la auditoría existente sin duplicarla |
| `PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md` | Vigente; n8n orquesta, Inventary conserva autoridad de negocio |
| `PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V1.md` | Histórica; sustituida por Reportes V2 |
| `PROPUESTA_CENTRO_REPORTES_GERENCIAL_OPERATIVO_TRIBUTARIO_V2.md` | Vigente; exige evitar doble conteo de ámbitos N:M |
| `ROADMAP_CIERRE_ARQUITECTURA_INVENTARY_PRE_V2.md` | Roadmap transversal de dependencias y condiciones de salida |

También se revisaron:
- `app-inventary-ecuapp/docs/PROPUESTA_JERARQUIA_ARQUITECTURA_FRONTEND_V1.md`
- `microservices-inventary-ecuapp/docs/PROPUESTA_JERARQUIA_ARQUITECTURA_BACKEND_V1.md`
- `AUDITORIA_ARCHIVOS_BASURA_Y_CODIGO_MUERTO.md`
- `AUDITORIA_BUGS_ERRORES_INCONSISTENCIAS.md`
- `AUDITORIA_TECNICA_PENDIENTES.md`

## 2. Decisiones arquitectónicas no negociables

1. El backend Spring Boot/PostgreSQL sigue siendo la autoridad de precios, inventario, permisos, transacciones, ventas, compras, pagos registrados y auditoría.
2. El backend actual es un **monolito modular**, no microservicios desplegables independientes. No se extraen procesos ni bases de datos por reorganizar paquetes.
3. React no replica reglas fiscales, de coste, disponibilidad, descuentos ni permisos; presenta contratos y resultados del servidor.
4. Un almacén operativo no es un establecimiento SRI ni selecciona automáticamente el emisor fiscal. Las relaciones entre ambos pueden ser N:M, con propiedad y ámbito explícitos.
5. Una devolución física, un reembolso monetario y una nota de crédito/anulación fiscal son operaciones distintas.
6. Kafka, n8n, IA, agentes, WebView, WMS multi-bin y facturación externa solo se habilitan cuando existan contratos, seguridad, idempotencia, recuperación y pruebas.
7. No se alteran migraciones históricas ni se recrea la base para acomodar refactorizaciones.
8. Los contratos REST/GraphQL, reglas RBAC, límites transaccionales, Outbox/Inbox e idempotencia se preservan salvo cambios explícitos, versionados y probados.

## 3. Fases de implementación

### Fase 1 — Estabilización y coherencia de reglas actuales

**Objetivo:** reducir errores reproducibles y crear una base de validación antes de reorganizar arquitectura o ampliar el producto.

Incluye:
- Corregir la configuración de CI y del ejemplo de bootstrap para que el administrador inicial respete el límite de contraseña.
- Excluir y retirar artefactos generados de Maven sin borrar datos operativos ni código fuente no demostrado como muerto.
- Mantener instalación frontend reproducible con `npm ci`, pruebas y build.
- Alinear la cotización de precios de venta para que el mínimo por coste se aplique solo a bienes con control de existencias; servicios y bienes no stockeables usan precio de catálogo.
- Añadir una prueba de API que cubra la cotización y venta de un servicio sin exigir un coste de inventario.
- Ejecutar CI frontend/backend en la rama de implementación y corregir los fallos observados.
- Documentar con precisión qué se validó y qué sigue sin evidencia.

**Criterios de salida:**
- [x] Ejemplo de bootstrap válido y artefactos generados excluidos.
- [x] Los 19 artefactos bajo la ruta literal `${project.build.directory}` fueron retirados.
- [x] CI frontend usa lockfile, tests y build; backend CI tiene PostgreSQL y smoke test de Compose.
- [x] Los workflows aceptan push a `implementacion_propuestas`.
- [x] Regla de cotización alineada entre frontend y backend para bienes stockeables, bienes no stockeables y servicios.
- [x] Prueba de regresión añadida para un servicio sin movimiento de inventario.
- [ ] CI verde para el SHA final de esta fase; no marcar como validado hasta ver los resultados de GitHub Actions.

### Fase 2 — Arquitectura frontend y backend

**Objetivo:** aplicar las dos propuestas de jerarquía sin reescribir ni alterar contratos.

Frontend:
- Crear shell, providers, layout, navegación y router separados.
- Extraer sesión y cliente HTTP.
- Reducir `App.jsx` a composición y routing, retirando carga global indiscriminada.
- Organizar módulos por feature y capas `pages`, `widgets`, `features`, `entities`, `shared`.
- Delimitar componentes, hooks, estilos, formularios, errores y permisos.

Backend:
- Empezar por `catalog` como piloto.
- Separar API REST/GraphQL, DTOs request/response, mappers, aplicación, dominio e infraestructura/persistencia.
- Normalizar por dominio gradualmente; evitar interfaces/abstracciones sin consumidor real.
- Mantener Spring Boot monolítico y conservar transacciones, SQL, entidades, serialización y rutas.

Pruebas obligatorias: build/tests frontend, Maven verify, contrato HTTP, serialización, permisos y arranque Spring.

### Fase 3 — Núcleo de inventario y datos maestros

- Asignación de almacén por usuario/terminal/caja y resolución server-side del ámbito autorizado.
- Separación entre empresa/RUC, establecimientos SRI, almacenes y ubicaciones internas.
- Relaciones N:M entre almacenes y establecimientos sin duplicar stock ni seleccionar automáticamente el emisor.
- Contactos cliente/proveedor con CRUD paginado y edición contextual desde compras/ventas.
- Unidades y conversiones con semántica consistente entre catálogo, compras, ventas e inventario.
- Disponibilidad por ubicación, reservas y trazabilidad de movimientos; no simular picking multi-bin no implementado.
- Auditoría append-only y cobertura por dominio con correlación común.

### Fase 4 — Operaciones comerciales completas

- Compras: navegación por órdenes, recepciones y devoluciones a proveedor; búsqueda, filtros, paginación y errores independientes.
- Ventas: flujo de mostrador, cotización, impuestos calculados server-side y políticas de descuento autorizadas.
- Snapshots históricos inmutables de comprador, facturación, líneas, descripción, unidad, precio y condiciones.
- Contexto de entrega/retiro separado del comprador y del almacén que contabiliza el stock.
- Devolución parcial/total vinculada a venta original, con saldo retornable y destino físico.
- Reembolso separado, auditable y conciliable; no afirmar pago ejecutado cuando solo existe registro manual.
- Cancelación operativa, anulación fiscal y nota de crédito como estados y procesos distintos.
- Suscripciones: integridad contrato/precio/calendario, venta desde Ventas y vínculo a factura por cuota.

### Fase 5 — Integración fiscal y distribuida

- Acordar y versionar contrato real Inventary ↔ backend fiscal; Inventary no genera XML, firma ni secuenciales.
- Persistir intención fiscal idempotente, snapshots y estado real recibido; callbacks autenticados y reconciliación.
- Integración de emisión, consulta, anulación y notas de crédito, según la operación legal que corresponda.
- Clientes HTTP tipados, timeouts, clasificación de errores, idempotencia, circuit breaker y bulkhead.
- Outbox/Inbox durable, reintentos acotados, redrive, DLQ y observabilidad.
- Kafka opcional por entorno, con contratos/envelopes y consumidores reales; no declarar activación completa por añadir el broker.
- Integration Hub primero como módulo interno; extraer microservicio solo cuando exista necesidad operativa demostrada.

### Fase 6 — Experiencia operativa y gerencial

- Inventario como centro de mando para dueño, operador y auditor; vistas separadas de resumen, existencias, kárdex, operaciones y auditoría.
- Compras, productos, ventas, suscripciones y pedidos con paginación, filtros, errores y permisos por acción.
- Logística de retiro/envío desde el origen real del stock, con preparación y cumplimiento diferenciados de la confirmación del pedido.
- Pedidos WhatsApp deterministas, deduplicación, handoff humano y n8n; el canal no cambia la fuente de verdad.
- Reportes gerenciales y operativos con definiciones de métricas, drill-down y prevención de doble conteo.
- Reportes tributarios solo con documentos fiscales reales y datos contables suficientes; no prometer declaraciones completas sin libro contable.
- Optimizar rendimiento, accesibilidad, responsive y estados de carga/error.

### Fase 7 — Capacidades avanzadas y cierre

- Contratos agent-ready de lectura y comandos tipados.
- Propuestas persistidas, autorización por ámbito y aprobación humana para mutaciones.
- IA limitada a extracción, clasificación, redacción y recomendaciones explicables; nunca autoridad de precio/stock/fiscalidad.
- Forecasting y reposición basados en evidencia; WMS avanzado solo si la operación lo justifica.
- Pruebas E2E navegador, arquitectura, integración PostgreSQL, concurrencia, seguridad, fallos externos y recuperación.
- Pruebas de carga y observabilidad; documentación, runbooks, matriz de permisos y evidencia del SHA final.
- Revisión final de deuda técnica y Definition of Done de V2.

## 4. Dependencias y exclusiones de secuencia

- Fase 2 depende de una base estable de Fase 1.
- Fase 3 precede a fulfillment multiubicación, permisos de ámbito y reportes sin doble conteo.
- Fase 4 precede a la integración fiscal completa y a reportes comerciales/fiscales confiables.
- Fase 5 depende de contratos comerciales y snapshots de Fase 4.
- Fase 6 consume los contratos consolidados de Fases 3–5.
- Fase 7 depende de seguridad, auditoría, idempotencia, contratos y métricas ya probados.

No forman parte de la primera fase: extracción de microservicios, cambios masivos de esquema, sustitución del stack frontend, migración a WebFlux, activación de Kafka en producción, IA autónoma, WMS multi-bin o declaración tributaria automática.

## 5. Estado de la Fase 1 en esta rama

Cambios heredados y ya publicados en la rama:
- Workflow frontend con `npm ci`, `npm test` y `npm run build`.
- Contraseña del ejemplo de bootstrap corregida.
- Exclusiones Maven y secretos reforzadas en `.gitignore`.
- READMEs raíz y frontend actualizados.
- 19 artefactos generados retirados.

Cambios de esta ejecución:
- El backend devuelve precio de catálogo y mínimo cero para servicios/bienes no stockeables; mantiene cálculo FIFO/promedio y mínimo sin pérdida para bienes stockeables.
- El formulario de ventas solo muestra y valida el mínimo por coste cuando el producto controla existencias.
- Se añadió prueba de API para cotizar y vender un servicio sin generar movimientos de inventario.
- Los workflows backend y frontend ahora también se ejecutan al hacer push a `implementacion_propuestas`.

**Evidencia CI disponible:** Frontend Build terminó correctamente (run [41165392564](https://github.com/JhonnyMinian92/inventary/actions/runs/41165392564)); los pasos de instalación `npm ci`, `npm test` y `npm run build` finalizaron en verde. Backend CI está ejecutándose sobre el SHA `51b3ce2b719d3c35643136a2ee893048a0c6b581` (run [41165468427](https://github.com/JhonnyMinian92/inventary/actions/runs/41165468427)); Maven verify, Compose y smoke test todavía no tienen conclusión.

**Validación pendiente:** la implementación de Fase 1 está publicada; su cierre de validación backend queda pendiente hasta que el workflow termine y se revise el resultado.

## 6. Fases restantes

Después de Fase 1 quedan **6 fases pendientes: Fases 2, 3, 4, 5, 6 y 7**.

La Fase 1 no autoriza a considerar terminado el producto ni a declarar el repositorio listo para producción. Cada fase posterior debe cerrarse con sus pruebas y evidencia asociadas al commit final.
