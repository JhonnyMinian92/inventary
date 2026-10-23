# 06 — Diagramas de negocio y flujo de cada familia de API

## 1. Flujo común de todas las solicitudes

~~~mermaid
flowchart TD
    A[HTTP request] --> B[Request size, content type and rate limit]
    B --> C[Authenticate token / session]
    C --> D{Authenticated?}
    D -- No --> E[401 problem+json]
    D -- Yes --> F[Authorize permission and object scope]
    F --> G{Allowed?}
    G -- No --> H[403/404 without sensitive details]
    G -- Yes --> I[Validate route, query and DTO]
    I --> J{Valid?}
    J -- No --> K[400/422 with field errors]
    J -- Yes --> L[Application use case]
    L --> M[Business invariants and idempotency]
    M --> N[Repository / transaction]
    N --> O{Conflict or rule violation?}
    O -- Yes --> P[Rollback and 409/422]
    O -- No --> Q[Persist response, audit and outbox]
    Q --> R[Commit]
    R --> S[HTTP response + correlationId]
~~~

Este flujo es obligatorio como modelo mental. Las consultas no necesitan una transacción de escritura ni una clave de idempotencia; los comandos de stock sí necesitan controles adicionales de concurrencia. La auditoría de seguridad debe diseñarse para registrar también ciertos rechazos/intent failures sin volcar datos sensibles.

## 2. Flujo de autenticación y autorización

### Endpoints de identidad

- GET /auth/me: validar JWT/session, mapear subject a usuario habilitado, obtener autoridades actuales según estrategia y devolver exclusivamente perfil permitido.
- POST /auth/logout: identificar sesión/tokens revocables; invalidar refresh/session si Inventary los administra; emitir evento de auditoría. Con IdP externo, logout local no debe prometer revocar un access token firmado que el backend no puede revocar.
- POST /auth/login y POST /auth/refresh son opcionales: solo se construyen cuando Inventary actúe como proveedor de identidad. El refresh usa rotación atómica, detección de reutilización, expiración absoluta e inactividad, cookies Secure/HttpOnly/SameSite si se utiliza navegador y protección contra CSRF cuando corresponda.

### Operaciones administrativas de usuario/rol

1. Autenticar y verificar el permiso de administración.
2. Cargar usuario/rol dentro del ámbito autorizado.
3. Aplicar una lista allowlisted de cambios; no hacer mass assignment.
4. Verificar invariantes (no auto-escalada de privilegios, roles existentes, último administrador).
5. Persistir el cambio y auditoría dentro de transacción.
6. Invalidar/caducar caché de permisos o sesiones si aplica.
7. Responder solo con campos expuestos.

## 3. Flujo de catálogo y atributos dinámicos

~~~mermaid
flowchart TD
    A[POST product] --> B[Authenticate + PRODUCT_CREATE]
    B --> C[Validate SKU, type, UOM, category]
    C --> D[Load active attribute schema]
    D --> E[Validate JSON attributes and schema version]
    E --> F{SKU / variant collision?}
    F -- Yes --> G[409 conflict]
    F -- No --> H[Persist product + variants]
    H --> I[Audit event]
    I --> J[201 Created]
~~~

- GET /catalog/products: valida filtros allowlisted, aplica ámbito/estado y permisos sobre proyecciones; pagina en BD; suprime costo y margen si el actor no tiene COST_READ.
- POST /catalog/products: SKU único; atributos cumplen plantilla; servicio no genera stock; datos derivados los calcula el backend.
- GET por ID: carga recurso dentro de ámbito; no permitir IDOR (objeto existente de otra empresa no debe ser visible).
- PATCH: verificar versión/ETag si se usa control de edición concurrente; aceptar solo campos editables; no permite cambiar saldos ni históricos.
- POST activate/deactivate: validar referencias y documentos abiertos; guardar estado y auditoría, no borrar.
- GET/PUT de esquema de atributos: nueva versión efectiva explícita; cambios que invalidan datos existentes requieren plan de migración o compatibilidad hacia atrás.
- Variantes y código de barras: asegurar unicidad y combinación válida dentro del producto/ámbito.

## 4. Compra y recepción

~~~mermaid
sequenceDiagram
    actor Operador
    participant API as REST API
    participant UC as Caso de uso
    participant DB as PostgreSQL
    Operador->>API: POST /purchase-orders
    API->>UC: Validar permiso, DTO, proveedor y líneas
    UC->>DB: Persistir DRAFT + audit
    DB-->>API: purchaseOrderId
    Operador->>API: POST /purchase-orders/{id}/submit
    API->>UC: Validar transición y aprobación
    UC->>DB: Confirmar estado
    Operador->>API: POST /purchase-orders/{id}/receipts
    API->>UC: Crear recepción DRAFT con cantidades parciales
    UC->>DB: Persistir recepción y líneas
    Operador->>API: POST /goods-receipts/{id}/post + Idempotency-Key
    API->>UC: Validar estado, cantidades y clave
    UC->>DB: TX: receipt POSTED + stock movement + balance + cost + audit/outbox
    DB-->>API: Commit
    API-->>Operador: 200/201 + resultado contabilizado
~~~

Reglas de cada endpoint:
- POST orden: no toca stock; guarda cantidades/precios documentales y snapshot del proveedor.
- PATCH orden: solo en DRAFT; rechazar cambios sobre líneas ya recibidas o estado no editable.
- submit/approve: transición permitida y permiso independiente si hay segregación.
- crear recepción: comprobar cantidad pendiente de recepción y reglas de lote/serie; guardar borrador no mueve stock.
- post recepción: clave idempotente; dentro de una transacción, validar de nuevo pendiente, actualizar estado, insertar movimientos de entrada, sumar saldo, actualizar capas de costo, guardar auditoría/outbox. Cualquier fallo revierte todo.
- devolución a proveedor: referenciar recepción/lote; no devolver más de lo aceptado menos devoluciones previas; posting mueve stock hacia fuera y revierte valor según método vigente.

## 5. Venta, reserva y despacho

~~~mermaid
sequenceDiagram
    actor Vendedor
    participant API as REST API
    participant UC as Sales / Inventory Use Cases
    participant DB as PostgreSQL
    Vendedor->>API: POST /sales-orders
    API->>UC: Validar catálogo, precio, permisos y cliente
    UC->>DB: Guardar DRAFT
    Vendedor->>API: POST /sales-orders/{id}/confirm + Idempotency-Key
    API->>UC: Validar estado, disponibilidad y política de reserva
    UC->>DB: TX: reservar unidades con update condicionado + confirmar pedido
    DB-->>API: Commit o conflicto
    Vendedor->>API: POST /sales-orders/{id}/dispatch + Idempotency-Key
    API->>UC: Validar reserva, lotes, cantidades y ubicación
    UC->>DB: TX: salida + movimientos + saldo + costo + pedido + audit/outbox
    DB-->>API: Commit
    API-->>Vendedor: 200 con documento despachado
~~~

Reglas de cada endpoint:
- POST venta: guardar DRAFT; precio/discount/tax se calculan/validan en servidor, nunca aceptar total calculado del cliente sin recalcular.
- PATCH venta: solo borrador; validar autorización de precios/descuentos y no filtrar costos.
- confirm: verificar disponibilidad dentro de la misma transacción; reservar atómicamente. La lectura previa de disponibilidad no garantiza stock.
- dispatch: consumir reservas o hacer reserva/salida atómica según política; movimiento de salida y costo se crean una vez. Reintento con la misma clave devuelve resultado previo.
- request-cancellation: registra solicitud y razón, sin revertir movimientos inmediatamente.
- approve-cancellation: comprobar rol/segregación; si ya fue despachada, no borrar la venta. Generar devolución/nota compensatoria; el ingreso de stock depende del estado físico recibido.
- venta de servicio: no debe hacer reserva ni registrar movimiento de stock.

## 6. Transferencia entre ubicaciones/almacenes

~~~mermaid
flowchart TD
    A[Crear transfer DRAFT] --> B[Validar origen, destino, SKU y qty]
    B --> C[Autorizar transferencia]
    C --> D[Despachar: salida origen / stock en tránsito]
    D --> E{Recepción conciliada?}
    E -- Sí --> F[Entrada destino, cerrar transfer]
    E -- Diferencias --> G[Registrar faltantes/sobrantes y aprobar resolución]
    G --> H[Ajustes compensatorios vinculados]
    F --> I[Auditoría + respuesta]
    H --> I
~~~

- POST /inventory/transfers: solo guarda borrador.
- approve: transición permitida, verificación independiente si está configurada.
- dispatch: la fuente se descuenta una vez; la mercancía pasa a ubicación virtual en tránsito cuando la política usa tránsito.
- receive: verifica destino y cantidades reales; el total recibido no supera despachado salvo ajuste autorizado. Los faltantes no se eliminan del historial.
- cancel: solo antes de que el movimiento sea irreversible o mediante flujo explícito de retorno del tránsito.

## 7. Ajuste y conteo

1. Crear borrador con ubicación, SKU/variante, cantidad contada o delta (elegir una semántica), motivo y referencia de conteo.
2. Verificar rol y segregación de funciones.
3. Al aprobar, volver a leer saldo y base de conteo; si la base cambió y el proceso depende de ella, solicitar reconciliación.
4. Al contabilizar, insertar movimiento con la diferencia calculada por el servidor, modificar el saldo de forma atómica y guardar auditoría.
5. La clave idempotente impide contabilización doble.
6. Nunca aceptar desde el cliente un saldo final arbitrario ni modificar saldo directamente.

## 8. Devoluciones

### Devolución de cliente
- Crear borrador referenciado a una venta y líneas despachadas.
- Calcular cantidad retornable = cantidad despachada − devolución ya contabilizada − otras cantidades reservadas para devolver.
- Aprobar según rol; registrar condición de mercancía (vendible, cuarentena, dañada).
- Al contabilizar, ingresar solamente unidades físicamente recibidas a la ubicación correcta, enlazar movimiento a venta original y recalcular/revertir costo según la política.
- Emitir evento compensatorio; una devolución no es una edición de la venta original.

### Devolución a proveedor
- Referenciar recepción y lote/serie.
- Validar disponible para devolución, excluir cantidades ya devueltas o consumidas.
- Registrar salida y corrección de costo de forma atómica.

## 9. Lecturas, reportes y auditoría

- GET balances/availability: autenticar, autorizar por almacén/ámbito, validar filtros y límites, ejecutar consulta indexada/paginada, mapear salida segura. Availability es una fotografía y no garantiza la futura reserva.
- GET movements: solo lectura, no muta ni recalcula históricos; ordenar por timestamp + id para paginación estable.
- GET reports: validar fecha inicial/final, rango máximo, tamaño, permisos financieros y límites de costo.
- GET audit-events: permisos AUDIT_READ, paginación acotada y campos inmutables; filtrar por actor/acción/fecha dentro de ámbito. Evitar exportación ilimitada.
- GET health: mostrar lo mínimo al público; detalles de DB, broker y métricas solo en endpoint interno protegido.

## 10. Flujo estándar de errores y rollback

- Error de validación antes de transacción: no se persiste documento operativo; se devuelve 400/422.
- Usuario sin permiso: 403 o 404 según política, se registra intento cuando sea relevante; no se modifica dato.
- Stock insuficiente/estado concurrente: rollback, respuesta 409 y detalle de negocio seguro; no persistir movimiento parcial.
- Clave idempotente igual y payload igual: devolver respuesta original; mismo key con payload diferente: 409.
- Error inesperado: rollback y 500 genérico + correlation ID; detalle solo en logs protegidos.
- Fallo de API externa: no mantener locks de inventario durante la llamada; marcar operación pendiente/reintentar con outbox o compensación definida.