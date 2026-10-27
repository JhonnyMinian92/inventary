> **Marcador de vigencia — 2026-10-26:** este archivo se conserva como registro histórico de decisión/hito, no como inventario vivo de endpoints ni prueba de que el HEAD actual pasó validación. Para estado vigente, usar [24 — Estado actual](24-estado-actual-repositorio.md), [25 — Referencia API actual](25-referencia-apis.md), [26 — Arquitectura/patrones](26-arquitectura-y-patrones-actuales.md) y [28 — Plan de cierre](28-plan-cierre-y-validacion.md). Confirmar cada ruta y comportamiento en el código del SHA desplegado.

# P01 — Decisiones de negocio y reglas de aceptación

**Estado:** baseline funcional definido para desbloquear la implementación del MVP.  
**Alcance:** backend de inventario, catálogo, compras, ventas, almacenes, auditoría y API.  
**Importante:** estas decisiones son el alcance de implementación por defecto. No declaran que el código esté implementado ni probado. Las pruebas y validaciones de ejecución se reservan para la fase final.

## 1. Decisiones de alcance

| Tema bloqueante | Decisión para el MVP | Fuera de alcance inicial |
|---|---|---|
| Modelo de empresa | Una empresa por instalación. El dominio no debe confiar en identificadores de empresa enviados libremente por el cliente. | SaaS multiempresa/tenancy compartido. |
| Documentos fiscales | Documentos comerciales internos con numeración propia. | Emisión electrónica fiscal, firma, autorización y contingencias SRI. |
| Costeo de inventario | Implementar FIFO y promedio ponderado móvil como estrategias explícitas y separadas. La estrategia se configura para la instalación y se registra en la valoración. | LIFO deshabilitado hasta aprobación contable/legal y especificación adicional. |
| Caja, pagos y contabilidad | La venta registra importes y estado comercial; no representa por sí misma un cobro ni un asiento contable. | Caja, pasarelas, conciliación bancaria, impuestos contables y libro mayor. |
| Identidad | Mantener autenticación JWT propia del backend existente, con secreto obligatorio por entorno, expiración y RBAC. Diseñar una frontera que permita integrar OIDC después. | Federación OIDC/SSO y proveedor de identidad externo en el MVP. |
| Volumen y rendimiento | Diseñar consultas paginadas, límites de petición, transacciones cortas y operaciones de inventario seguras. Las metas numéricas de rendimiento/disponibilidad quedan como objetivos por confirmar con infraestructura y carga esperada. | Prometer SLA o capacidad de usuarios/SKUs sin datos de carga. |
| Frontend | El alcance de esta entrega es el backend y sus contratos API. | Implementación de Angular/micro-frontends dentro de este backlog backend. |

## 2. Actores, roles y permisos

El backend aplica permisos en cada operación; ocultar botones en la interfaz nunca sustituye la autorización del servidor.

| Rol | Permisos principales | Restricciones |
|---|---|---|
| `SUPER_ADMIN` | Administrar usuarios, roles, permisos y configuración; acceso completo a operaciones y reportes financieros. | No puede eliminar físicamente documentos contabilizados, movimientos ni auditoría. |
| `AUDITOR` | Lectura transversal autorizada y consulta/exportación acotada de auditoría. | No crea ni modifica datos comerciales. |
| `MANAGER_ALMACEN` | Productos, atributos, almacenes, compras, recepciones, conteos, ajustes, transferencias e inventario. | No registra ventas como vendedor ni altera auditoría o transacciones históricas. |
| `VENDEDOR` | Consultar catálogo y disponibilidad vendible; crear ventas y consultar las propias según permisos asignados. | No ve costos, margen, precios de compra ni reportes financieros; una anulación requiere solicitud y aprobación autorizada. |
| `SOPORTE_TI` | Consultar logs técnicos, salud operativa y gestionar sesiones técnicas permitidas. | No accede a costos, márgenes ni modifica datos comerciales o auditoría. |

Reglas de autorización:
- Aplicar denegación por defecto y mínimo privilegio.
- Verificar permiso de acción y pertenencia del recurso a la instalación en cada lectura y escritura.
- La aprobación de una anulación debe provenir de un actor autorizado distinto cuando la política de separación de funciones lo requiera; el vendedor no puede autoaprobarla.
- La administración de infraestructura no concede permiso implícito para editar datos comerciales.
- Nunca incluir contraseñas, tokens, secretos ni credenciales en logs o eventos de auditoría.

## 3. Catálogo, SKU y atributos

1. Un producto puede ser un bien con control de stock, un bien sin control de stock o un servicio; el tipo determina qué operaciones físicas están permitidas.
2. Un servicio no puede reservar, recibir ni emitir movimientos de stock físico.
3. Cada unidad vendible/almacenable tiene un SKU identificable. Las variantes (talla, color u otras dimensiones) tienen SKU independiente cuando su stock o precio puede diferir.
4. SKU y código de barras son únicos dentro de la instalación; no se reutilizan si existen documentos históricos que los referencian.
5. Los atributos variables se definen mediante una plantilla versionada por categoría. El servidor valida tipo, obligatoriedad, opciones y límites antes de guardar.
6. Los atributos JSON no reemplazan campos críticos como SKU, estado, unidad, precio o saldo.
7. Un producto inactivo no se incorpora a documentos nuevos, pero sigue siendo visible en el historial.
8. Cambiar una plantilla no puede invalidar silenciosamente productos existentes; requiere versionado o migración explícita.
9. Las conversiones de unidad solo se permiten entre dimensiones compatibles y con precisión definida.

## 4. Inventario, reservas y movimientos

1. El libro de movimientos es la fuente histórica inmutable; el saldo es una proyección operativa actualizable y reconciliable.
2. Toda variación contabilizada de existencias genera movimiento(s) dentro de la misma transacción que actualiza saldo y documento.
3. `onHand >= 0`, `reserved >= 0` y `available = onHand - reserved >= 0`. Si la reserva se representa dentro de las existencias físicas, `reserved <= onHand`.
4. Ninguna salida puede generar stock negativo. Las operaciones concurrentes deben usar una actualización condicional/bloqueo transaccional; si no hay saldo suficiente, toda la operación falla sin cambios parciales.
5. Una transferencia genera una salida y una entrada vinculadas. Si se maneja tránsito, se representa explícitamente y no se cuenta dos veces.
6. Ajustes y mermas requieren permiso, motivo y movimiento auditable; no se modifica el saldo de forma silenciosa.
7. Cada movimiento registra SKU/variante, cantidad positiva, unidad, tipo/dirección, ubicación de origen/destino, documento de origen, actor, instante UTC, motivo cuando aplique, correlación e idempotencia.
8. El posting de un documento o línea no puede duplicarse ante reintentos. Recepciones, despachos, devoluciones y ajustes son idempotentes.
9. Una reserva no es una salida física. Tiene ciclo de vida explícito y una política de liberación/expiración definida; la reserva vencida se libera mediante una operación auditable.
10. Cantidades y dinero usan tipos decimales (`BigDecimal`/`NUMERIC`), no `float`/`double`.

## 5. Compras, ventas, devoluciones y estados

### Compras y recepción
- Una orden puede recibirse en varias recepciones parciales.
- Solo cantidades físicamente recibidas y aceptadas incrementan stock.
- Cada recepción referencia la orden y sus líneas; la cantidad acumulada recibida no excede la cantidad autorizada, salvo corrección aprobada explícita.
- Los costos originales y la moneda quedan preservados en el documento y en las capas/asientos de valoración correspondientes.

### Ventas y despacho
- La confirmación/despacho valida actor, estado, cantidades y disponibilidad de stock en una transacción.
- El precio, descripción, unidad y descuentos aplicados se guardan como snapshot de la línea para preservar el historial aunque cambie el catálogo.
- Los roles sin permiso financiero nunca reciben costo unitario, costo promedio, margen o precio de compra.
- Crear una venta no implica cobrarla; el estado de pago solo se añade cuando se apruebe un módulo de caja/pagos.

### Devoluciones y anulaciones
- Toda devolución referencia el documento original, motivo y cantidades retornables.
- La cantidad devuelta acumulada no excede la cantidad despachada menos devoluciones previas.
- Una devolución de cliente vuelve a stock solo si el artículo se inspecciona y se acepta como vendible; en caso contrario va a cuarentena/merma.
- Una anulación posterior al posting se resuelve con una transacción compensatoria, devolución o nota interna autorizada; nunca borrando o editando el original.
- Los movimientos compensatorios conservan vínculo al documento original y al actor que los autorizó.

### Estados mínimos
- Maestros: `ACTIVE`, `INACTIVE`.
- Orden comercial: `DRAFT` → `CONFIRMED` → `PARTIALLY_FULFILLED` → `FULFILLED`.
- Recepción/devolución: borrador → aprobación cuando aplique → contabilizado.
- Transferencia: `DRAFT` → `IN_TRANSIT` (si se usa tránsito) → `RECEIVED`; cancelar solo antes de que sus efectos sean irreversibles.
- Las transiciones se realizan mediante comandos de dominio específicos, no con un PATCH genérico de estado.

## 6. Costos, precios y moneda

1. Precio de venta, costo de adquisición y costo de valoración son conceptos separados.
2. Se implementan FIFO y promedio ponderado móvil como estrategias independientes. No se pueden mezclar fórmulas dentro de un mismo cálculo.
3. LIFO permanece deshabilitado hasta que exista aprobación contable/legal explícita y reglas documentadas para reportes.
4. Cada importe tiene moneda explícita. No se suman importes de monedas distintas sin conversión y regla de tipo de cambio aprobada.
5. Redondeo, escala, descuentos, costos accesorios y tratamiento de devoluciones deben aplicarse de manera consistente y preservarse en el documento histórico.
6. El cambio de estrategia de costeo no reescribe silenciosamente la valoración histórica; se registra vigencia y se define una transición controlada.

## 7. Auditoría, datos y seguridad

- Acciones sensibles registran actor, acción, recurso, resultado, instante UTC y correlation ID.
- La auditoría es append-only desde la API de negocio; no hay endpoint para editar o borrar eventos.
- No se borran físicamente productos con historia, documentos contabilizados, movimientos ni eventos de auditoría. Los maestros se inactivan.
- Las correcciones financieras/de inventario se registran como nuevas transacciones compensatorias.
- Las consultas usan parámetros, allowlists para campos de ordenamiento y límites máximos de paginación.
- Las entradas se validan por formato, longitud, rango y campos permitidos; no se enlaza JSON directamente a entidades JPA.
- Las respuestas de producción no exponen stack traces, SQL, secretos ni datos sensibles.
- Los comandos que afectan inventario son transaccionales, autorizados, auditables e idempotentes.
- Las fechas de auditoría se generan en servidor y se almacenan en UTC; las fechas externas deben incluir zona explícita.

## 8. Requisitos no funcionales de diseño

- API REST JSON versionada bajo `/api/v1`; OpenAPI es el contrato compartido.
- Errores de API uniformes mediante `application/problem+json`, con códigos de dominio estables.
- Configuración por entorno; secretos fuera del repositorio y sin fallback inseguro.
- Límites de tamaño, timeout, paginación y tasa para reducir abuso y consumo no acotado.
- Logs estructurados, correlation ID y métricas operativas; health endpoints no revelan información sensible.
- Copias de seguridad y recuperación deben documentarse para el entorno de despliegue. RPO/RTO concretos quedan por definir según infraestructura.
- No se promete “cero defectos”; el cierre de implementación significa cubrir requisitos acordados, no certificar ausencia de errores.

## 9. Fuera del MVP hasta aprobación explícita

- Multiempresa SaaS compartido.
- Facturación electrónica SRI y procesos fiscales.
- Contabilidad general, impuestos contables, conciliación bancaria y nómina.
- Caja, pagos, pasarelas y reembolsos financieros.
- Manufactura/MRP y listas de materiales.
- Integraciones de marketplace, transportistas y e-commerce.
- Frontend Angular/micro-frontends dentro del backlog del backend.
- LIFO como estrategia activa de valoración.
- SLA numérico, escalado multi-región y compromisos de capacidad no respaldados por datos.

## 10. Criterio de finalización de P01

- [x] Alcance inicial y exclusiones definidos.
- [x] Decisiones que bloquean el modelo de datos resueltas mediante defaults de MVP.
- [x] Roles y límites de permisos definidos.
- [x] Reglas de catálogo, SKU, atributos y unidades definidas.
- [x] Invariantes de stock, reservas, concurrencia y ledger definidos.
- [x] Reglas de compras, ventas, devoluciones y estados definidas.
- [x] Política inicial de costeo y moneda definida.
- [x] Reglas de auditoría, seguridad y datos definidas.
- [x] Requisitos no funcionales y criterios de exclusión definidos.
- [ ] Pruebas y validaciones de ejecución: **reservadas para la fase final, según el plan de trabajo**.

## 11. Relación con los documentos existentes

Este documento concreta las decisiones de P01 y debe leerse junto con:
- [02 — Requisitos y alcance](02-requisitos-y-alcance.md), que conserva la matriz RF/RNF.
- [04 — Modelo de dominio y reglas](04-modelo-dominio-y-reglas.md), que desarrolla entidades e invariantes de persistencia.
- [05 — Catálogo de APIs](05-catalogo-apis.md), contrato de endpoints y permisos.
- [08 — Plan de trabajo](08-plan-de-trabajo.md), secuencia de implementación.

Si un documento anterior mantiene un supuesto abierto que contradice esta decisión, prevalece este baseline P01 para implementar el MVP hasta que se apruebe formalmente un cambio de alcance.
