# Inventary - Ecosistema Avanzado de Gestión y Control de Inventario

## 1. Contexto

Los sistemas de inventario modernos (como SAP Business One u Odoo) han evolucionado de simples hojas de cálculo a ecosistemas transaccionales críticos. Las empresas actuales no venden un solo tipo de producto; manejan catálogos mixtos donde un artículo puede requerir tallas y colores (textil), otro lote y fecha de caducidad (farmacéutico), y otro números de serie y garantía (tecnología).

Un sistema monolítico con columnas fijas en base de datos es obsoleto y genera "código espagueti" al intentar adaptar nuevos requerimientos. **Inventary** nace para resolver este problema mediante una arquitectura polimórfica y de alta cohesión. Se diseña desde el primer día para ser una plataforma de grado empresarial, orientada a microservicios (Backend) y micro-frontends/SPAs escalables (Frontend), garantizando la máxima integridad de los datos, trazabilidad absoluta y una adaptación camaleónica a cualquier modelo de negocio.

---

## 2. Capacidades y Restricciones del Sistema

### Lo que el sistema HARA (Core Business)

* **Gestión Híbrida de Catálogos:** Administrará productos genéricos y servicios.
* **Trazabilidad de Extremo a Extremo:** Rastreará el ciclo de vida de un producto desde la orden de compra al proveedor hasta la facturación al cliente final.
* **Cálculo Dinámico de Costos:** Soportará métodos de valoración de inventario (FIFO, LIFO, Costo Promedio Ponderado).
* **Gestión Documental Transaccional:** Generará y gestionará documentos de soporte (Órdenes de Compra, Remisiones, Facturas de Venta).

### Lo que el sistema PERMITIRÁ (Flujos Operativos)

* **Adaptabilidad Absoluta (Esquemas Dinámicos):** Permitirá definir "Plantillas de Características" por categoría. Un celular tendrá atributos `{ "ram": "8GB", "almacenamiento": "256GB" }`, mientras que un alimento tendrá `{ "alergenos": ["soja", "gluten"], "caducidad": "2027-01-01" }`, coexistiendo en la misma estructura sin alterar el código ni la base de datos relacional.
* **Anulación y Reversión:** Permitirá anular ventas o compras mediante **transacciones compensatorias** (ej. Notas de Crédito o Devoluciones), devolviendo automáticamente el stock a su estado anterior de forma segura.
* **Control Concurrente:** Permitirá que múltiples vendedores facturen el mismo producto simultáneamente asignando el stock bajo un modelo de "bloqueo optimista", procesando al primero y notificando al segundo si el stock se agota.

### Lo que el sistema NO PERMITIRÁ (Reglas de Integridad Inquebrantables)

* **No a los "Hard Deletes" (Borrados Físicos):** El sistema **no permitirá** eliminar registros financieros, compras, ventas o productos que ya tengan movimientos. Solo se permitirá el "Soft Delete" (inactivación lógica) para preservar la integridad referencial y la auditoría.
* **No a los Inventarios Negativos:** El sistema bloqueará cualquier salida de mercancía que reduzca el stock por debajo de cero.
* **No a la Modificación Invisible:** El sistema no permitirá alterar precios de compras pasadas o fechas de transacciones históricas. Cualquier corrección debe hacerse mediante un nuevo documento de ajuste auditado.
* **No a la Evasión de Auditoría:** No existirá un "modo superusuario" que pueda apagar los triggers de auditoría en la base de datos. Toda acción deja huella.

---

## 3. Seguridad y Protección

### Cómo el sistema se protegerá a sí mismo (Resiliencia e Infraestructura)

* **Prevención de Sobrecarga:** Implementación de *Rate Limiting* (límite de peticiones) en los API Gateways/Controllers para evitar ataques DDoS o fuerza bruta.
* **Aislamiento de Fallos (Circuit Breakers):** En la arquitectura de microservicios, si el servicio de auditoría cae, el servicio de ventas almacenará temporalmente los logs en una cola (ej. RabbitMQ/Kafka) para no detener la facturación.
* **Seguridad a Nivel de Base de Datos:** Las consultas utilizarán *Prepared Statements* (vía Hibernate) anulando la posibilidad de Inyección SQL.
* **Validación Estricta (Fail-Fast):** Validaciones de DTOs en la entrada del backend (Spring Validation) y validaciones de formularios controlados en React para detectar datos incompletos antes de enviar una petición. El backend mantiene la validación autoritativa.

### Cómo el sistema protegerá a cada usuario (Privacidad y Aislamiento)

* **Autenticación Stateless Segura:** Para el MVP, JWT firmado con HS256 y secreto `JWT_SECRET` obligatorio por entorno, con expiración configurada y validación en endpoints privados. La integración OIDC y la firma asimétrica RS256 quedan como evolución explícita, no como capacidad implementada por defecto.
* **Principio de Mínimo Privilegio (RBAC):** El backend aplica la autorización en cada endpoint. El frontend gestiona los estados 401/403 y evita asumir que ocultar un control equivale a autorización.
* **Enmascaramiento de Datos:** Los DTOs y permisos del backend gobiernan la exposición de costos e informes financieros; el frontend solo debe presentar los campos autorizados que devuelve la API.
* **Renderizado seguro:** React escapa el texto renderizado de forma predeterminada. Los componentes no deben introducir HTML no confiable con dangerouslySetInnerHTML; la validación del backend sigue siendo obligatoria.

---

## 4. Matriz de Roles y Accesos (RBAC - Role Based Access Control)

Para garantizar la seguridad operativa, se implementará un modelo jerárquico y granular:

1. **SUPER_ADMIN (Dueño/Gerencia):** Acceso total. Creación de roles, gestión de usuarios, acceso a reportes financieros de alto nivel.
2. **AUDITOR (Control y Calidad):** Solo lectura (Read-Only) en toda la plataforma. Acceso exclusivo a la bitácora de auditoría (`audit_logs`) para revisar quién modificó qué, desde qué IP y a qué hora.
3. **MANAGER_ALMACEN (Jefe de Bodega):** Puede ingresar compras, actualizar stock, crear nuevos productos, definir atributos dinámicos. Puede inactivar productos. **No puede** realizar ventas ni alterar registros de auditoría.
4. **VENDEDOR (Punto de Venta/Asesor):** Solo puede consultar el catálogo de productos (sin ver costos base), consultar stock disponible y registrar ventas. Puede solicitar la anulación de una venta, pero requiere aprobación.
5. **SOPORTE_TI:** Puede ver logs de errores técnicos y gestionar sesiones, pero no tiene acceso a datos financieros o costos.

---

## 5. Arquitectura de Software y Patrones de Diseño

Para evitar el "código espagueti" y asegurar la escalabilidad, se aplicará **Clean Architecture (Arquitectura Hexagonal)** en el Backend y **Component-Based Architecture** en el Frontend.

### Patrones de Diseño Clave:

* **CQRS (Command Query Responsibility Segregation):** Separar las operaciones de lectura (ej. buscar productos) de las de escritura (ej. registrar una venta). Esto permite optimizar las búsquedas y proteger las transacciones.
* **Cliente REST centralizado (Frontend):** Los módulos React utilizan `src/api.js` para centralizar la base URL, autenticación Bearer, errores HTTP y claves de idempotencia; los componentes no implementan peticiones fetch por su cuenta.
* **Strategy Pattern (Backend):** Para aplicar diferentes algoritmos de cálculo de precios o descuentos sin llenar el código de múltiples `if/else`.
* **Entity-Attribute-Value (EAV) vs JSONB:** En lugar del antipatrón relacional EAV para productos dinámicos, se usará el tipo de dato nativo `JSONB` de PostgreSQL.
* *Justificación Postgres:* Permite guardar cualquier estructura JSON (ej. `{"talla":"M", "color":"Rojo"}`) en una columna del producto e indexarla con un índice **GIN**. Esto otorga la flexibilidad de NoSQL con la consistencia transaccional (ACID) de SQL.



---

## 6. Jerarquía de Directorios (Workspaces)

### Frontend: `app-inventary-ecuapp` (React + Vite)

Frontend SPA implementado con React y Vite. La estructura que existe actualmente en el repositorio es:

```text
app-inventary-ecuapp/
├── .env.example
├── .nvmrc
├── package.json
├── README.md
├── docs/
│   ├── api-coverage.md
│   ├── frontend-roadmap.md
│   └── troubleshooting.md
└── src/
    ├── api.js
    ├── App.jsx
    ├── main.jsx
    ├── styles.css
    ├── components/
    │   ├── DataTable.jsx
    │   └── TransactionDetails.jsx
    ├── hooks/
    │   └── useAccessibleDialog.js
    └── features/
        ├── catalog/
        │   ├── CatalogTables.jsx
        │   ├── CategoryForm.jsx
        │   └── ProductForm.jsx
        ├── dashboard/
        │   └── Overview.jsx
        ├── inventory/
        │   └── InventoryOperations.jsx
        ├── operations/
        │   ├── OperationsCatalogs.jsx
        │   └── UnitConversions.jsx
        ├── purchasing/
        │   ├── PurchaseForm.jsx
        │   ├── PurchaseReturns.jsx
        │   ├── PurchaseTable.jsx
        │   └── ReceiptForm.jsx
        ├── reports/
        │   └── ReportsCenter.jsx
        └── sales/
            ├── SaleForm.jsx
            ├── SalesReturns.jsx
            └── SalesTable.jsx
```

### Backend: `microservices-inventary-ecuapp` (Java Spring Boot)

El backend Spring Boot mantiene los módulos y controladores bajo `src/main/java/com/ecuapp/inventary/`. Los dominios identificados incluyen autenticación, catálogo, inventario, almacenes, contrapartes, compras, ventas, devoluciones, traslados, reportes, auditoría, GraphQL, medios e integraciones. La matriz de rutas REST consumidas por el frontend está en `app-inventary-ecuapp/docs/api-coverage.md`.

## 7. Conclusión del Diseño

Este diseño garantiza que **Inventary** no sea un simple CRUD. Al combinar el poder de **PostgreSQL JSONB** para la adaptabilidad de productos, **Spring Boot Hexagonal** para la mantenibilidad empresarial y testabilidad, y **React + Vite** para un rendimiento hiper-reactivo en el cliente, el sistema está preparado para escalar, auditar cada milímetro de la operación y blindar los datos comerciales contra errores humanos o manipulaciones malintencionadas.


## Documentación de implementación del backend

La especificación del backend, el contrato REST propuesto, los requerimientos, los diagramas, los controles de seguridad y el plan de trabajo se encuentran en [la documentación del backend](microservices-inventary-ecuapp/docs/README.md).

## Estado del backend

El estado vigente se registra en [la documentación técnica del backend](microservices-inventary-ecuapp/docs/README.md). La integración P12 de backend, Docker/PostgreSQL, workflow de validación manual y documentación está incorporada en `master`. La auditoría estática del código y de las APIs, con hallazgos priorizados, pendientes y matriz de pruebas para el agente local, se encuentra en [AUDITORIA_TECNICA_PENDIENTES.md](AUDITORIA_TECNICA_PENDIENTES.md). No se han ejecutado pruebas, build, CI, arranque ni migraciones; la validación funcional permanece pendiente.
