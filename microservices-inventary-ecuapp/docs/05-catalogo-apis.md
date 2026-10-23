# 05 — Catálogo de APIs REST y contrato propuesto

## 1. Convenciones del contrato

- Base path: /api/v1.
- Formato: JSON UTF-8. Errores con application/problem+json.
- Autorización: Bearer token OIDC/JWT o mecanismo aprobado; cada endpoint exige autenticación salvo health/metadata públicos explícitos.
- Las rutas siguientes son **propuestas**, todavía no existen en el backend revisado.
- Colecciones paginadas responden con items, page, size, totalElements y totalPages. Límite inicial sugerido: size <= 100.
- Fechas ISO 8601 con zona. Dinero mediante decimal como string JSON o número decimal validado, con currency explícita y escala fija.
- No exponer entidades JPA directamente. DTOs independientes para requests/responses y allowlist de campos editables.
- No implementar borrado físico de documentos/movimientos.
- Comandos que cambian stock usan Idempotency-Key y generan correlation ID. Igual key + mismo hash de payload devuelve el resultado previo; igual key + payload diferente devuelve 409.
- Ordenamiento, filtros, includes y campos seleccionables son allowlist; nunca concatenar entrada del usuario a SQL.
- Cambios de estado se hacen mediante comandos explícitos, no un PATCH de status genérico.
- El esquema OpenAPI final debe codificar roles/permisos, errores, campos requeridos, límites y ejemplos.

## 2. Identidad, usuarios y acceso

Se recomienda federar autenticación con un proveedor OIDC y configurar Spring Security como Resource Server. Si se elige identidad propia, login/refresh/logout requieren implementación y revisión de seguridad dedicada.

| Método y ruta | Objetivo / permiso | Validación y resultado |
|---|---|---|
| POST /auth/login (opcional) | Inicio de sesión local | Rate limit, credenciales genéricas ante fallo, registro de intento; devuelve tokens/cookie según política. Solo si no hay IdP OIDC. |
| POST /auth/refresh (opcional) | Rotar refresh token | Token válido y no reutilizado, rotación atómica, revocación al detectar reuse. |
| POST /auth/logout | Cerrar sesión | Requiere identidad; revoca refresh/session si el sistema los administra. |
| GET /auth/me | Identidad y permisos efectivos | No devuelve secretos ni credenciales. |
| PATCH /auth/me/password (opcional) | Cambiar contraseña local | Exigir contraseña actual, política robusta y revocación de sesiones; solo con identidad propia. |
| GET /users | Listar usuarios | USER_READ; paginación y campos seguros. |
| POST /users | Crear/invitar usuario | USER_CREATE; validar identificador único y asignaciones. |
| GET /users/{userId} | Consultar usuario | USER_READ + autorización sobre ámbito. |
| PATCH /users/{userId} | Editar datos permitidos | USER_UPDATE; no permitir escalada de rol indirecta. |
| POST /users/{userId}/activate | Activar | USER_ADMIN; auditar. |
| POST /users/{userId}/deactivate | Desactivar | USER_ADMIN; impedir desactivar el último administrador crítico sin reemplazo. |
| PUT /users/{userId}/roles | Reemplazar roles asignados | ROLE_ASSIGN; validar rol y ámbito, auditar la diferencia. |
| GET /roles | Listar roles | ROLE_READ. |
| POST /roles | Crear rol | ROLE_ADMIN; nombre único y permisos permitidos. |
| GET /roles/{roleId} | Detalle de rol | ROLE_READ. |
| PATCH /roles/{roleId} | Actualizar metadatos | ROLE_ADMIN; no alterar en silencio roles reservados. |
| PUT /roles/{roleId}/permissions | Reemplazar permisos del rol | PERMISSION_ADMIN; lista allowlisted y auditoría. |
| GET /permissions | Catálogo de permisos | ROLE_READ; consulta. |

Roles base a configurar: SUPER_ADMIN, AUDITOR, MANAGER_ALMACEN, VENDEDOR y SOPORTE_TI. Los roles son ejemplos de perfil; la autorización efectiva debe expresarse como permisos como PRODUCT_READ, STOCK_READ, STOCK_ADJUST, SALE_DISPATCH, COST_READ, AUDIT_READ. No confiar solo en nombres/roles enviados en el token sin mapearlos a autoridades validadas.

## 3. Catálogo, categorías y atributos

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /catalog/categories | Listar categorías | PRODUCT_READ; paginado y filtro de estado. |
| POST /catalog/categories | Crear categoría | PRODUCT_CREATE; código único, nombre acotado. |
| GET /catalog/categories/{categoryId} | Detalle de categoría | PRODUCT_READ + ámbito. |
| PATCH /catalog/categories/{categoryId} | Editar metadatos | PRODUCT_UPDATE; no cambiar esquema en uso sin migración/versionado. |
| POST /catalog/categories/{categoryId}/deactivate | Inactivar | PRODUCT_ADMIN; rechazar uso en nuevos documentos, conservar historial. |
| GET /catalog/categories/{categoryId}/attributes | Consultar esquema de atributos | PRODUCT_READ. |
| PUT /catalog/categories/{categoryId}/attributes | Definir esquema/versionar plantilla | PRODUCT_ADMIN; tipos, opciones, required, rangos y versión validados. |
| GET /catalog/units | Listar unidades de medida | PRODUCT_READ. |
| POST /catalog/units | Crear unidad | PRODUCT_ADMIN; código y dimensión únicos según política. |
| GET /catalog/products | Buscar productos | PRODUCT_READ; paginación, filtros, variante, categoría, estado; costo/margen omitidos salvo permiso COST_READ. |
| POST /catalog/products | Crear producto/servicio | PRODUCT_CREATE; SKU único, tipo, categoría, UOM, atributos contra esquema. |
| GET /catalog/products/{productId} | Detalle de producto | PRODUCT_READ + ámbito. |
| PATCH /catalog/products/{productId} | Actualizar campos no históricos | PRODUCT_UPDATE; sin editar stock directamente ni reescribir precio/costo histórico. |
| POST /catalog/products/{productId}/activate | Activar | PRODUCT_ADMIN. |
| POST /catalog/products/{productId}/deactivate | Inactivar | PRODUCT_ADMIN; rechazar si regla de negocio lo impide. |
| GET /catalog/products/{productId}/variants | Consultar variantes | PRODUCT_READ. |
| POST /catalog/products/{productId}/variants | Crear variante/SKU | PRODUCT_CREATE; combinación de atributos no duplicada. |
| PATCH /catalog/variants/{variantId} | Editar datos descriptivos permitidos | PRODUCT_UPDATE; no altera cantidades ni movimientos. |
| POST /catalog/variants/{variantId}/deactivate | Inactivar variante | PRODUCT_ADMIN; no borrar referencias. |
| GET /catalog/products/{productId}/barcode | Consultar datos/código de barras | PRODUCT_READ; formato validado. |
| POST /catalog/products/{productId}/barcode | Asignar código de barras | PRODUCT_ADMIN; unicidad y formato. |

No se aceptarán atributos dinámicos arbitrarios sin esquema. Los atributos que gobiernan trazabilidad física, precio, costo, caducidad o seguridad se deben representar en entidades/campos tipados cuando corresponda.

## 4. Almacenes, ubicaciones e inventario

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /warehouses | Listar almacenes | WAREHOUSE_READ. |
| POST /warehouses | Crear almacén | WAREHOUSE_ADMIN; código único dentro del ámbito. |
| GET /warehouses/{warehouseId} | Detalle | WAREHOUSE_READ + ámbito. |
| PATCH /warehouses/{warehouseId} | Actualizar metadatos | WAREHOUSE_ADMIN; no reasignar movimientos históricos. |
| POST /warehouses/{warehouseId}/deactivate | Inactivar | WAREHOUSE_ADMIN; exige resolver stock/operaciones pendientes. |
| GET /warehouses/{warehouseId}/locations | Listar ubicaciones | WAREHOUSE_READ. |
| POST /warehouses/{warehouseId}/locations | Crear ubicación | WAREHOUSE_ADMIN; validar árbol, código y tipo. |
| PATCH /locations/{locationId} | Editar metadatos | WAREHOUSE_ADMIN; sin romper referencias o jerarquía. |
| GET /inventory/balances | Saldos por producto/ubicación | STOCK_READ; devuelve onHand, reserved, available, inTransit cuando aplique. |
| GET /inventory/availability | Consultar disponibilidad para operación | STOCK_READ; filtrar por SKU, ubicación y cantidad solicitada. Es informativa, no sustituye la reserva atómica. |
| GET /inventory/movements | Historial/kardex | STOCK_READ; filtros, paginación y orden allowlisted. |
| GET /inventory/movements/{movementId} | Detalle de movimiento | STOCK_READ; no mutación. |
| POST /inventory/adjustments | Crear ajuste en borrador | STOCK_ADJUST; motivo obligatorio, línea, cantidad contabilizada y contada. |
| GET /inventory/adjustments/{adjustmentId} | Consultar ajuste | STOCK_READ. |
| POST /inventory/adjustments/{adjustmentId}/approve | Aprobar ajuste | STOCK_ADJUST_APPROVE, separado del creador cuando se exige segregación de funciones. |
| POST /inventory/adjustments/{adjustmentId}/post | Contabilizar ajuste | STOCK_ADJUST; Idempotency-Key, validación de permiso y atomicidad. |
| POST /inventory/transfers | Crear transferencia en borrador | STOCK_TRANSFER; origen/destino distintos y líneas válidas. |
| GET /inventory/transfers/{transferId} | Consultar transferencia | STOCK_READ. |
| POST /inventory/transfers/{transferId}/approve | Aprobar transferencia | STOCK_TRANSFER_APPROVE si la política lo exige. |
| POST /inventory/transfers/{transferId}/dispatch | Despachar transferencia | STOCK_TRANSFER; Idempotency-Key; salida de origen a ubicación EN_TRÁNSITO si el flujo la utiliza. |
| POST /inventory/transfers/{transferId}/receive | Confirmar recepción | STOCK_TRANSFER; validar cantidades, destino y diferencia; generar entrada de destino y tratamiento de faltantes. |
| POST /inventory/transfers/{transferId}/cancel | Cancelar transferencia no completada | STOCK_TRANSFER; solo si la transición y stock permiten revertir de forma segura. |
| GET /inventory/reorder-rules | Consultar mínimos/máximos | REORDER_READ. |
| POST /inventory/reorder-rules | Crear regla de reabastecimiento | REORDER_ADMIN; rango mínimo/máximo consistente y proveedor/método si aplica. |
| PATCH /inventory/reorder-rules/{ruleId} | Modificar regla | REORDER_ADMIN. |
| GET /inventory/replenishment-suggestions | Productos que requieren reabastecimiento | REORDER_READ; datos paginados basados en stock disponible y parámetros definidos. |

La consulta de disponibilidad no reserva stock. Los consumidores deben usar el comando de confirmar venta/reservar o despachar, que repite la comprobación de forma atómica.

## 5. Compras y proveedores

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /suppliers | Listar proveedores | SUPPLIER_READ. |
| POST /suppliers | Crear proveedor | SUPPLIER_CREATE; códigos/contactos validados. |
| GET /suppliers/{supplierId} | Consultar proveedor | SUPPLIER_READ. |
| PATCH /suppliers/{supplierId} | Editar proveedor | SUPPLIER_UPDATE; conservar snapshot histórico en documentos. |
| POST /suppliers/{supplierId}/deactivate | Inactivar | SUPPLIER_ADMIN; bloquear nuevas compras y preservar historial. |
| GET /purchase-orders | Listar órdenes | PURCHASE_READ; filtros por proveedor/estado/fecha. |
| POST /purchase-orders | Crear orden borrador | PURCHASE_CREATE; líneas, moneda, precios, cantidad y proveedor válidos. |
| GET /purchase-orders/{purchaseOrderId} | Detalle de orden | PURCHASE_READ. |
| PATCH /purchase-orders/{purchaseOrderId} | Editar orden borrador | PURCHASE_UPDATE; rechazo si ya está contabilizada o no permite cambio. |
| POST /purchase-orders/{purchaseOrderId}/submit | Enviar a aprobación/confirmación | PURCHASE_CONFIRM; transición de estado explícita. |
| POST /purchase-orders/{purchaseOrderId}/approve | Aprobar orden | PURCHASE_APPROVE; segregación opcional de creador/aprobador. |
| POST /purchase-orders/{purchaseOrderId}/cancel | Cancelar orden | PURCHASE_CANCEL; no se puede cancelar lo ya recibido sin flujo de compensación. |
| POST /purchase-orders/{purchaseOrderId}/receipts | Crear recepción borrador | GOODS_RECEIPT_CREATE; cantidades restantes, lote/serie si requerido. |
| GET /goods-receipts/{receiptId} | Consultar recepción | GOODS_RECEIPT_READ. |
| PATCH /goods-receipts/{receiptId} | Editar recepción en borrador | GOODS_RECEIPT_UPDATE; cantidades contra pedido y tolerancias. |
| POST /goods-receipts/{receiptId}/post | Contabilizar recepción | GOODS_RECEIPT_POST; Idempotency-Key; actualiza documento, ledger, saldo y costo atómicamente. |
| POST /goods-receipts/{receiptId}/reject | Rechazar recepción en borrador | GOODS_RECEIPT_APPROVE; motivo auditado. |
| POST /purchase-returns | Crear y contabilizar devolución a proveedor | `purchasing:manage`; Idempotency-Key; recepción contabilizada, cantidad no devuelta y stock disponible. |

| GET /purchase-returns/{returnId} | Consultar devolución | PURCHASE_READ. |

Facturas del proveedor, pagos y cuentas por pagar no quedan incluidas automáticamente. Si se requieren, deben modelarse con reglas fiscales/contables propias.

## 6. Ventas, clientes y devoluciones

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /customers | Listar clientes | CUSTOMER_READ. |
| POST /customers | Crear cliente | CUSTOMER_CREATE; validar identificadores según ámbito legal definido. |
| GET /customers/{customerId} | Consultar cliente | CUSTOMER_READ. |
| PATCH /customers/{customerId} | Editar datos | CUSTOMER_UPDATE; documento histórico conserva snapshot. |
| POST /customers/{customerId}/deactivate | Inactivar cliente | CUSTOMER_ADMIN; preservar documentos. |
| GET /sales-orders | Listar ventas | SALE_READ; ocultar costos/márgenes si falta permiso. |
| POST /sales-orders | Crear venta borrador | SALE_CREATE; líneas, precios autorizados, moneda, cliente y tipo de producto válidos. |
| GET /sales-orders/{salesOrderId} | Detalle | SALE_READ; proyección de datos por permiso. |
| PATCH /sales-orders/{salesOrderId} | Editar borrador | SALE_UPDATE; no modificar un documento contabilizado. |
| POST /sales-orders/{salesOrderId}/confirm | Confirmar y, según política, reservar stock | SALE_CONFIRM; Idempotency-Key, validación atómica de disponibilidad. |
| POST /sales-orders/{salesOrderId}/dispatch | Despachar y emitir salida | SALE_DISPATCH; Idempotency-Key, saldo reservado y cantidades enviadas. |
| POST /sales/{saleId}/request-cancellation | Solicitar cancelación de venta | `sales:manage`; Idempotency-Key y razón obligatoria; no modifica stock. |
| POST /sales/{saleId}/approve-cancellation | Aprobar anulación solicitada | `sales:cancel`; Idempotency-Key; actor distinto al solicitante; reversión de stock atómica. |
| POST /sales-returns | Crear y contabilizar devolución de venta | `sales:manage`; Idempotency-Key; venta completada, línea original, cantidad disponible y destino RESTOCK/SCRAP. |
| GET /sales-returns/{returnId} | Consultar devolución | SALE_READ. |



La emisión de factura fiscal electrónica, cobro, impuestos y notas de crédito legales quedan condicionados a definir integración SRI y requisitos contables. La orden/registro comercial no debe presentarse como comprobante fiscal si no tiene esa integración.

## 7. Costos, reportes, auditoría y plataforma

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /costing/policy | Consultar método vigente | COST_READ; devolver política efectiva y versión. |
| PUT /costing/policy | Configurar método | COST_ADMIN; cambio aprobado, versionado y efectivo desde fecha/periodo definido; LIFO bloqueado hasta aprobación explícita. |
| GET /reports/stock | Existencia por producto/ubicación | REPORT_STOCK_READ; límites de rango y tamaño. |
| GET /reports/movements | Kardex agregado | REPORT_STOCK_READ; filtros acotados. |
| GET /reports/valuation | Valoración/costo total | COST_READ; campos financieros separados de vistas para vendedores. |
| GET /reports/low-stock | Bajo mínimos | REPORT_STOCK_READ. |
| GET /reports/purchases | Métricas de compras | REPORT_PURCHASE_READ. |
| GET /reports/sales | Métricas comerciales | REPORT_SALE_READ y filtro de margen por COST_READ. |
| GET /audit-events | Consultar bitácora | AUDIT_READ, solo lectura y exportación limitada. |
| GET /audit-events/{eventId} | Detalle de evento | AUDIT_READ. |
| GET /actuator/health | Estado mínimo de salud | Público o red interna; no detallar dependencias/secretos al público. |
| GET /actuator/metrics (interno) | Métricas técnicas | Solo red/perfil protegido. |
| GET /v3/api-docs / swagger-ui (configurable) | Esquema API | Solo entornos autorizados; no publicar información operativa sensible en producción sin decisión explícita. |

## 8. Esquema de errores

Cuerpo estándar problem+json, con campos como type, title, status, detail seguro, instance, code y correlationId. No retornar stack trace, SQL ni secretos.

| HTTP | Uso |
|---|---|
| 400 Bad Request | JSON mal formado, parámetros o filtros inválidos. |
| 401 Unauthorized | Identidad ausente, token inválido/vencido. |
| 403 Forbidden | Identidad válida sin permiso. Puede ocultarse existencia de ciertos recursos mediante 404 según política. |
| 404 Not Found | Recurso inexistente o no visible en el ámbito autorizado. |
| 409 Conflict | Estado incompatible, stock insuficiente, versión concurrente, idempotency key reutilizada con payload distinto. |
| 422 Unprocessable Content | Reglas de dominio/validación semántica no satisfechas cuando se distingue de 400. |
| 429 Too Many Requests | Rate limit excedido, con Retry-After cuando aplique. |
| 500 Internal Server Error | Error inesperado con correlation ID; detalle genérico en producción. |
| 503 Service Unavailable | Dependencia crítica temporalmente indisponible o mantenimiento. |

## 9. DTOs mínimos para comandos

- Crear producto: sku, name, kind, categoryId, unitOfMeasureId, attributes, variantOptions opcionales. No permitir campos calculados como onHand.
- Ajuste: warehouse/location, líneas con variantId, countedQuantity o delta según convención, reasonCode, comment. Escoger una sola semántica y documentarla.
- Confirmar venta: salesOrderId, ubicación o reglas de picking, versión si aplica. El precio final debe recalcularse/validarse en servidor.
- Recepción: purchaseOrderId, líneas con cantidad recibida, lote/serie/expiración cuando aplique y cantidades rechazadas justificadas.
- Devolución: sourceOrderId, líneas/cantidades, condición y destino de devolución; límites comparados con el total ya devuelto.
- Posting: identificador idempotente, actor autenticado y recurso objetivo; no confiar actorId en el body.

Estos DTOs deben recibir Bean Validation, límites de longitud/número de elementos y rechazo de propiedades desconocidas si no rompen compatibilidad pactada.

## 10. Catálogo público e integración comercial n8n + WhatsApp

Estas rutas amplían el contrato para el requisito de venta directa por WhatsApp. Su diseño detallado y diagramas están en [09 — WebFlux, n8n, WhatsApp y OpenAI](09-webflux-n8n-whatsapp-openai.md).

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /public/catalog/products | Catálogo público paginado: imagen principal, SKU, nombre, descripción, precio vigente, moneda y disponibilidad de venta | Público con rate limit; solo productos activos/publicables; límite de página; nunca exponer costo/margen. |
| GET /public/catalog/products/{productIdOrSlug} | Ficha de producto/variantes | Campos públicos allowlisted e imagen HTTPS accesible. |
| GET /public/catalog/categories | Categorías visibles en el canal | Solo categorías activas/publicables. |
| GET /integrations/whatsapp/catalog/products | Búsqueda de catálogo para n8n | Credencial de máquina con scope catalog:read; no usa datos directos de DB. |
| GET /integrations/whatsapp/catalog/products/{idOrSku} | Detalle actualizado para la conversación | Scope de integración; oculta campos internos. |
| POST /integrations/whatsapp/carts | Crear/recuperar carrito asociado a la identidad del workflow | Autenticación de servicio; customerRef derivado de wa_id verificado por el workflow, nunca del texto propuesto por el LLM. |
| GET /integrations/whatsapp/carts/{cartId} | Estado del carrito | Verifica acceso del canal al carrito y devuelve importes recalculados. |
| PUT /integrations/whatsapp/carts/{cartId}/items/{variantId} | Añadir o fijar cantidad de variante | Cantidad positiva, producto activo y variante válida. |
| DELETE /integrations/whatsapp/carts/{cartId}/items/{variantId} | Retirar una línea | Solo carrito modificable, no pedido confirmado. |
| POST /integrations/whatsapp/carts/{cartId}/quote | Cotización final con quoteId/expiración | Recalcula precio y stock en servidor; no representa pago ni reserva permanente. |
| POST /integrations/whatsapp/orders | Crear pedido desde una cotización válida tras consentimiento expreso | Idempotency-Key, quote sin expirar, confirmación vinculada a mensaje entrante real, revalidación y reserva atómicas. |
| GET /integrations/whatsapp/orders/{orderId} | Consultar estado de pedido del canal | Scope limitado y comprobación de customerRef. |
| POST /integrations/whatsapp/orders/{orderId}/cancel-request | Solicitar cancelación | No borra ni revierte silenciosamente una venta contabilizada. |

Prefijo completo de las rutas de la tabla: /api/v1. El token de integración no puede usar endpoints administrativos ni recibir costos. El endpoint público devuelve stock vendible; puede devolver cifra exacta o estado IN_STOCK/LOW_STOCK/OUT_OF_STOCK según decisión comercial. Una consulta de stock no lo reserva.

### Datos mínimos de producto en el catálogo público

- Identificador estable, SKU, slug/nombre, descripción breve y categoría publicable.
- Precio de venta actual, código de moneda e información comercial visible.
- Imagen principal y, opcionalmente, galería con URL HTTPS estable y texto alternativo.
- Variantes comprables y disponibilidad vendible según permisos/canal.
- No incluir costo promedio, margen, precio de proveedor, movimientos ni información de cliente.

Si las imágenes se almacenan inicialmente en una máquina local, la API no debe devolver rutas de disco ni localhost. Para enviar imágenes mediante WhatsApp, usar una URL accesible por el proveedor o descargar/cargar el archivo mediante una operación de medios soportada por WhatsApp Cloud API.

### Regla de confirmación

Un pedido significa una obligación comercial y no solo una respuesta del chatbot. El LLM no crea la venta directamente: debe presentar al cliente las líneas, cantidad, precio unitario, total, método de entrega y estado de pago; después de una confirmación inequívoca, n8n ejecuta el POST. Inventary recalcula precios, revalida stock y crea el pedido con reserva en una sola transacción idempotente. AWAITING_PAYMENT no significa PAID.

## 11. Imágenes y medios de producto

| Método y ruta | Objetivo / permiso | Validaciones clave |
|---|---|---|
| GET /catalog/products/{productId}/images | Listar medios de producto | PRODUCT_READ; devuelve imagen principal, alt, orden y URL apta para el consumidor autorizado. |
| POST /catalog/products/{productId}/images | Subir imagen o registrar medio gestionado | PRODUCT_MEDIA_MANAGE; límite de bytes, allowlist MIME, inspección real de contenido, dimensiones/tipo permitidos, nombre generado por el servidor y almacenamiento aislado. |
| PATCH /catalog/products/{productId}/images/{imageId} | Cambiar texto alternativo, orden o principal | PRODUCT_MEDIA_MANAGE; campos permitidos; auditar cambio relevante. |
| DELETE /catalog/products/{productId}/images/{imageId} | Retirar una imagen de publicación | PRODUCT_MEDIA_MANAGE; solo desasocia/despublica la imagen, no elimina documentos ni datos de producto. |

Reglas:
- No aceptar una URL arbitraria para que el backend la descargue; esto puede introducir SSRF. Si se admite importar una URL, requiere un flujo separado con hosts allowlisted y controles de red.
- Guardar bytes en almacenamiento de objetos (servicio compatible con S3, CDN o equivalente) y metadatos en PostgreSQL. No guardar archivos enormes como BLOB por defecto.
- En local, puede usarse almacenamiento de desarrollo compatible con S3. Las URL que el cliente de WhatsApp o Meta deba recuperar han de ser HTTPS públicamente alcanzables, o el workflow debe subir el medio por la API de media de WhatsApp y usar el identificador devuelto.
- Validar tamaño, formato y contenido real del archivo; ignorar el nombre/extensión enviados por el usuario; no ejecutar ni servir SVG/HTML arbitrario como contenido confiable.
- No incrustar secretos o enlaces temporales que expongan archivos internos. Las imágenes comerciales publicadas pueden usar URL pública estable; activos privados deben seguir una política diferente.
- Las respuestas del endpoint público deben incluir solo medios publicados y autorizados.

## 12. GraphQL y semántica de comandos

Añadir un endpoint GraphQL (habitualmente /graphql) mediante Spring for GraphQL para consultas flexibles del catálogo y vistas agregadas. La selección de campos incluye nombre, descripción, imágenes, precio público vigente, variantes y estado de disponibilidad. No se expone costo/margen en el esquema público.

Reglas de implementación:
- REST sigue siendo el contrato preferido para n8n, webhooks, comandos operativos y ventas de WhatsApp.
- GraphQL se usa cuando la consulta compuesta evita requests múltiples; no se añade por sustitución total de REST.
- Las mutaciones GraphQL opcionales llaman exactamente a los mismos casos de uso que REST. No hay lógica duplicada de venta ni acceso directo al repositorio desde resolvers.
- Los listados usan paginación cursor-based y filtros allowlisted.
- Activar límites de profundidad, aliases, tamaño de página y complejidad de query; DataLoader/batching para evitar N+1; rate limits ponderados por coste de query.
- Autorización por recurso/campo; no confiar en ocultación de campos como único control de seguridad.
- Queries públicas de catálogo pueden cachearse según política de la sección de caché; la disponibilidad expuesta nunca representa reserva.
- No cachear en CDN compartido estado de checkout, costos ni datos de cliente.

## 13. Consistencia y eventos

Cualquier endpoint de confirmación o creación de pedido —REST o GraphQL— debe delegar en un caso de uso transaccional único. La transacción local verifica quote y expiración, recalcula total, reserva stock atómicamente, persiste pedido/líneas, idempotencia, movimientos/saldo cuando corresponda y Outbox. Si un paso falla, todo revierte.

Kafka se alimenta de Outbox posterior al commit; no se usa una secuencia “publicar evento y luego intentar escribir venta”. Los consumidores de Kafka pueden actualizar proyecciones, Redis, índices de búsqueda o solicitar notificaciones; el consumidor es idempotente y ningún mensaje aislado equivale a una venta confirmada.

La estrategia de rendimiento, caché y escala de PostgreSQL está en [11 — Escalabilidad, GraphQL, Kafka, caché y datos](11-escalabilidad-graphql-kafka-cache-datos.md).