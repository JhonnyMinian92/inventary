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
* **Validación Estricta (Fail-Fast):** Validaciones de DTOs en la capa de entrada (Spring Validation) y validación reactiva en el Frontend (Angular Strict Typed Forms) para rechazar datos corruptos antes de que consuman recursos.

### Cómo el sistema protegerá a cada usuario (Privacidad y Aislamiento)

* **Autenticación Stateless Segura:** Uso de tokens JWT firmados asimétricamente (RS256) con tiempos de expiración cortos y *Refresh Tokens* rotatorios.
* **Principio de Mínimo Privilegio (RBAC):** Las vistas del frontend y los endpoints del backend se bloquean según el rol del usuario.
* **Enmascaramiento de Datos:** Los usuarios sin permisos administrativos no podrán ver los márgenes de ganancia (costo de compra vs precio de venta), solo el precio de venta final.
* **Sanitización:** Prevención de ataques XSS (Cross-Site Scripting) delegando la sanitización al motor de plantillas de Angular.

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
* **Facade Pattern (Frontend):** En Angular, los componentes no llamarán directamente al `HttpClient`. Pasarán por una Capa Facade (Servicios de Estado) que orquesta las llamadas y actualiza las Signals.
* **Strategy Pattern (Backend):** Para aplicar diferentes algoritmos de cálculo de precios o descuentos sin llenar el código de múltiples `if/else`.
* **Entity-Attribute-Value (EAV) vs JSONB:** En lugar del antipatrón relacional EAV para productos dinámicos, se usará el tipo de dato nativo `JSONB` de PostgreSQL.
* *Justificación Postgres:* Permite guardar cualquier estructura JSON (ej. `{"talla":"M", "color":"Rojo"}`) en una columna del producto e indexarla con un índice **GIN**. Esto otorga la flexibilidad de NoSQL con la consistencia transaccional (ACID) de SQL.



---

## 6. Jerarquía de Directorios (Workspaces)

### Frontend: `app-inventary-ecuapp` (Angular 21)

Estructura basada en dominios de negocio, orientada a Standalone Components y lazy loading automático mediante las nuevas rutas funcionales.

```text
app-inventary-ecuapp/
├── src/
│   ├── app/
│   │   ├── core/                  # Singleton services, guards, interceptors, tokens
│   │   │   ├── auth/              # JWT interceptor, Auth guard
│   │   │   └── http/              # Error handling interceptor
│   │   ├── shared/                # UI components reutilizables (Botones, Tablas, Modales)
│   │   │   ├── components/
│   │   │   └── ui-kit/            # Componentes puramente presentacionales (Dumb components)
│   │   ├── features/              # Dominios de la aplicación (Smart components)
│   │   │   ├── products/          # Catálogo, atributos dinámicos
│   │   │   ├── purchases/         # Módulo de ingresos y proveedores
│   │   │   ├── sales/             # Módulo de salidas, facturación, POS
│   │   │   ├── audit/             # Visor de trazabilidad
│   │   │   └── users/             # Gestión de RBAC
│   │   ├── layout/                # Sidebar, Topbar, Footer
│   │   └── app.routes.ts          # Configuración de Lazy Loading por feature
│   ├── environments/
│   └── styles/                    # Tailwind CSS o SCSS global

```

### Backend: `microservices-inventary-ecuapp` (Java Spring Boot)

Estructura basada en Arquitectura Hexagonal. Aunque inicie como un monolito modular, esta separación permite extraer dominios a microservicios independientes (ej. separar Ventas de Auditoría) en el futuro mediante contenedores Docker.

```text
microservices-inventary-ecuapp/
├── inventory-service/             # Dominio principal (Microservicio o Módulo)
│   ├── src/main/java/com/ecuapp/inventory/
│   │   ├── application/           # Casos de uso (Servicios de Aplicación). Lógica de orquestación.
│   │   │   ├── port/              # Puertos (Interfaces). Ej: ProductRepositoryPort
│   │   │   └── usecase/           # Implementación de las operaciones del negocio.
│   │   ├── domain/                # Modelos de negocio puros, libres de frameworks. (Clean Code)
│   │   │   ├── model/             # Entidades de dominio (Product, Sale, Purchase)
│   │   │   └── exception/         # Excepciones de negocio (ej. InsufficientStockException)
│   │   ├── infrastructure/        # Adaptadores tecnológicos (Base de datos, API, Seguridad)
│   │   │   ├── adapter/
│   │   │   │   ├── in/            # Adaptadores de Entrada (REST Controllers, WebSockets)
│   │   │   │   │   └── web/       # Endpoints protegidos por @PreAuthorize
│   │   │   │   └── out/           # Adaptadores de Salida (Bases de datos, APIs externas)
│   │   │   │       └── persistence/# JPA Repositories, Entities (@Entity), Mappers
│   │   │   └── config/            # Configuraciones de Spring (Security, Beans, OpenAPI)
├── audit-service/                 # (Opcional a futuro) Microservicio independiente para logs
└── api-gateway/                   # (Opcional a futuro) Enrutamiento, Rate Limiting, Filtros JWT

```

## 7. Conclusión del Diseño

Este diseño garantiza que **Inventary** no sea un simple CRUD. Al combinar el poder de **PostgreSQL JSONB** para la adaptabilidad de productos, **Spring Boot Hexagonal** para la mantenibilidad empresarial y testabilidad, y **Angular 21 (Signals)** para un rendimiento hiper-reactivo en el cliente, el sistema está preparado para escalar, auditar cada milímetro de la operación y blindar los datos comerciales contra errores humanos o manipulaciones malintencionadas.
