# Propuesta integral: Almacenes, establecimientos SRI, proveedores, clientes y unidades de medida

**Versión:** 2.0  
**Fecha de análisis:** 29 de octubre de 2026  
**Repositorio revisado:** JhonnyMinian92/inventary · rama master  
**Estado:** propuesta de producto, UX/UI, datos y backend; pendiente de implementación.  
**Propósito:** corregir la separación de dominios, hacer fiables los datos tributarios, habilitar CRUD de contactos con paginación y desde los flujos de compra/venta, y ubicar las unidades de medida donde tengan sentido.

> Este documento describe el estado observado en el código versionado y la solución recomendada. No implica que se hayan modificado la aplicación, los endpoints, las migraciones ni el backend encargado de facturación electrónica.

## 1. Decisión ejecutiva

La pantalla actual se llama «Almacenes y contactos», pero mezcla cinco dominios que necesitan reglas distintas:

1. **Almacenes operativos:** lugares internos donde Inventary administra existencias.
2. **Contribuyente y establecimientos tributarios del SRI:** identidad fiscal oficial, estado del RUC y establecimientos registrados abiertos/cerrados.
3. **Proveedores:** contactos comerciales que intervienen en compras y recepciones.
4. **Clientes:** personas o entidades vinculadas a ventas y atención comercial.
5. **Unidades de medida y conversiones:** configuración del catálogo, las cantidades y los movimientos de inventario.

La propuesta establece una separación fundamental:

**Un establecimiento SRI no es lo mismo que un almacén interno.** Un establecimiento es parte de la información tributaria oficial; un almacén es una ubicación operativa del negocio. Pueden corresponder al mismo sitio físico, pero no son intercambiables ni deben compartir código por conveniencia. Un centro de distribución puede existir como almacén interno sin ser un establecimiento que emite comprobantes. Un establecimiento tributario puede, a su vez, estar relacionado con más de un almacén interno.

El perfil del contribuyente y sus establecimientos se deben basar en datos procedentes del SRI o de un proveedor de consulta aprobado y claramente identificado, preservando el origen, fecha de consulta, estado recibido e historial de cambios. Los datos oficiales son de solo lectura dentro de Inventary; cuando haya un dato incorrecto, el usuario debe corregirlo ante el SRI y luego actualizar la información importada, no editar el registro oficial manualmente.

Proveedores y clientes deben conservar CRUD completo en su sección central, con paginación y búsqueda en servidor. Además, Compras y Ventas deben permitir crear o editar el contacto desde el flujo de trabajo sin perder los datos de la operación. Las unidades de medida **no deben eliminarse**: se recomienda trasladarlas a una sección de configuración del catálogo de Productos y hacerlas más seguras para uso futuro en compras, ventas e inventario.

## 2. Estado actual verificado en el repositorio

### 2.1 Frontend: pantalla Almacenes y contactos

La interfaz está implementada en:

- app-inventary-ecuapp/src/features/operations/OperationsCatalogs.jsx
- app-inventary-ecuapp/src/features/operations/UnitConversions.jsx
- app-inventary-ecuapp/src/App.jsx
- app-inventary-ecuapp/src/api.js

Hoy la pantalla muestra cuatro pestañas: Almacenes, Proveedores, Clientes y Unidades. El formulario genérico de cada pestaña permite crear/editar y activar/desactivar cuando existe el endpoint. Las conversiones se administran dentro de Unidades.

Los problemas importantes son:

| Hallazgo | Evidencia del código | Consecuencia |
|---|---|---|
| La pestaña Almacenes solo tiene código interno, nombre, dirección y estado activo. | OperationsCatalogs.jsx; WarehouseDtos.java | No representa la información del RUC ni los establecimientos oficiales abiertos/cerrados. |
| No existe un catálogo de perfil tributario RUC ni una entidad SRI de establecimientos dentro del dominio de almacenes. | Warehouse.java contiene code, name, address y active | La aplicación no tiene una relación explícita entre la bodega interna y el establecimiento fiscal que le corresponda. |
| Los proveedores y clientes se cargan con listAllPages y se filtran localmente. | OperationsCatalogs.jsx, refresh/visible | El backend ya soporta búsqueda y paginación; la UI descarga el histórico completo y no muestra paginación real. |
| La búsqueda local solo ve los registros descargados. | OperationsCatalogs.jsx, visible | No es una búsqueda fiable para un directorio que crezca. |
| El formulario de proveedor/cliente tiene los campos básicos de nombre/código/RUC/email/teléfono/dirección, pero no consulta ni verifica los datos fiscales del RUC. | OperationsCatalogs.jsx | Se puede registrar información fiscal incompleta o discordante sin saber de dónde procede. |
| Compras recibe la lista de proveedores cargada previamente y muestra un selector. No tiene creación de proveedor dentro del formulario. | PurchaseForm.jsx | Hay que abandonar Compras para registrar un proveedor nuevo; aumenta la fricción y se puede perder contexto. |
| Ventas ofrece selección de cliente y admite una venta rápida sin contacto registrado, con nombre/teléfono opcionales, pero no incorpora un flujo integrado de alta/edición de cliente. | SaleForm.jsx | No se puede completar el CRUD de clientes cómodamente durante una venta. |
| Los endpoints ya distinguen permisos de lectura y gestión, pero OperationsCatalogs no recibe una matriz de permisos para limitar la UI. | App.jsx y OperationsCatalogs.jsx | Se pueden mostrar acciones que el backend rechazará con 403. |
| Las cuatro listas no tienen el mismo contrato: almacenes, proveedores y clientes tienen endpoints paginados; unidades y conversiones devuelven listas sin paginación. | Controllers y CatalogStructureService | Se necesita aplicar la paginación de forma consistente donde haya historial de negocio y mantener las listas de configuración pequeñas y eficientes donde proceda. |
| Las pestañas calculan el contador usando la cantidad cargada, no un total global recibido del servidor. | OperationsCatalogs.jsx | Con paginación, el contador podría dar una impresión incorrecta del total. |
| El formulario usa modales genéricos para registros de diferente complejidad. | OperationsCatalogs.jsx | No escala bien a un perfil SRI con muchos atributos, establecimientos, sincronización, errores y procedencia de datos. |
| La unidad es un registro operativo, pero aparece al mismo nivel que almacenes, proveedores y clientes. | OperationsCatalogs.jsx y CatalogStructureService.java | La ubicación funcional no refleja que las unidades forman parte del modelo de producto/cantidad. |

### 2.2 Backend actual de almacenes

Warehouse.java y WarehouseDtos.java muestran que un almacén contiene:
- id;
- code;
- name;
- address;
- active;
- createdAt y updatedAt.

WarehouseController ofrece listado paginado, detalle, creación, actualización, activación/desactivación, eliminación controlada y gestión de ubicaciones internas. WarehouseService valida si hay stock, órdenes pendientes, traslados en borrador/en tránsito o conteos abiertos antes de permitir desactivar un almacén.

Este comportamiento de inventario es correcto como base. No debe reemplazarse por un objeto tributario ni perderse cuando se añada la capa SRI.

### 2.3 Backend actual de proveedores y clientes

PartnerController y PartnerService ya implementan:

- GET de proveedores con q, active y pageable.
- GET de clientes con q, active y pageable.
- GET de detalle por ID.
- POST y PUT para proveedores/clientes.
- Activar, desactivar y eliminación física con restricciones.
- Búsqueda por nombre, código e identificación tributaria.
- Auditoría de las acciones principales.

BusinessPartner contiene código, un tipo CUSTOMER/SUPPLIER/BOTH, nombre, taxId, email, phone, address, active y timestamps. El código y taxId son únicos globalmente.

Limitaciones relevantes:

1. La UI no aprovecha la paginación ni q que ya existen.
2. El registro fiscal está reducido a un campo taxId de texto: no hay tipo de identificación ni un perfil SRI asociado.
3. La API crea un contacto desde una ruta de cliente o proveedor y guarda el tipo pedido. El modelo acepta BOTH, pero el flujo visible no permite promover de forma simple un contacto existente a ambos roles.
4. Por la unicidad global de taxId, crear por separado un proveedor y un cliente con el mismo RUC puede entrar en conflicto. No se debe resolver creando duplicados ni eliminando la unicidad: debe existir un solo contacto de negocio con roles de proveedor y/o cliente.
5. Lecturas de proveedores requieren purchasing:read; su mantenimiento usa partners:manage. Lecturas de clientes usan partners:read; su mantenimiento usa customers:manage. La UI contextual debe respetar estos permisos sin asumir que tener permiso para emitir una compra o venta implica permiso para crear contactos.

### 2.4 Unidades de medida actuales

El dominio incluye:

- UnitOfMeasure: código, nombre, dimensión, decimalScale y estado activo.
- Catálogo de dimensiones de medición y sus ejemplos.
- Conversiones entre unidades con factor.
- Asociación de unidad base al producto y a la variante.
- Asociación de unidad a movimientos de inventario y a información de valoración.

Las dimensiones disponibles incluyen categorías como cantidad, peso, volumen, longitud, superficie, tiempo y otras. La unidad base se almacena en el producto; no debe eliminarse la dimensión ni las referencias históricas.

Pero hay una limitación crítica: las líneas actuales de compras y ventas reciben productId, quantity y coste/precio, sin una unidad explícita por línea. Los servicios consultan las unidades de los productos/movimientos, pero las solicitudes no declaran si el usuario ingresó 1 caja, 12 unidades o 0,5 kg. La tabla de conversión existe, pero la API de compra/venta actual no ofrece un contrato transaccional de cantidad en unidad alternativa con factor capturado.

Por tanto, no debe habilitarse todavía una conversión automática de las cantidades de compra/venta en la interfaz solo porque se pueda registrar un factor.

## 3. Lo que exige separar el modelo tributario de la ubicación física

### 3.1 Perfil del contribuyente (RUC)

El perfil tributario debe conservar, siempre que la fuente autorizada los proporcione, los campos solicitados por el negocio:

- RUC;
- razón social;
- estado del contribuyente;
- motivo de suspensión o estado tributario;
- indicador de contribuyente fantasma;
- indicador de transacciones inexistentes;
- tipo de contribuyente;
- régimen;
- categoría, cuando exista;
- obligación de llevar contabilidad;
- agente de retención;
- contribuyente especial;
- fecha de inicio de actividades;
- fecha de última actualización;
- fecha de cese;
- fecha de reinicio;
- establecimientos asociados.

No convertir todos los campos en simples booleanos si la fuente entrega textos como SI/NO, valores nulos o estados con nuevos códigos. El backend debe conservar el valor original y proporcionar una representación normalizada sin perder la procedencia. Los estados desconocidos nuevos no pueden convertirse silenciosamente en “Activo”.

### 3.2 Establecimientos registrados en el RUC

Cada establecimiento del RUC debe poder conservar:

- número del establecimiento como texto de tres dígitos, preservando ceros iniciales;
- nombre comercial, que puede ser nulo;
- ubicación y dirección recibidas de la fuente;
- estado recibido, por ejemplo ABIERTO/CERRADO según el catálogo del proveedor/SRI;
- indicador de matriz, solo cuando la fuente realmente lo proporcione;
- fecha de observación y procedencia;
- historial de cambios del estado/dirección cuando exista una nueva consulta.

El ejemplo entregado por el negocio incluye un establecimiento con un nombre comercial nulo y estado CERRADO. La aplicación debe representar ese estado como un dato oficial y advertencia tributaria; no debe sustituirlo por el nombre del almacén ni asumir que el almacén operativo está inactivo.

No inferir campos que no llegan en el origen. Si un atributo no está disponible, debe marcarse como “No informado por la fuente”, no generarse por heurística.

### 3.3 El establecimiento de emisión tampoco es el almacén

Para la facturación electrónica ecuatoriana, la documentación técnica del SRI distingue el código del establecimiento del código del punto de emisión; la especificación consultada los representa como códigos numéricos de tres dígitos. La guía oficial de actualización del RUC confirma que el catastro muestra establecimientos abiertos y cerrados y que la información de cada establecimiento puede actualizarse ante el SRI. La ficha técnica consultada también describe rechazos en determinadas condiciones tributarias, por ejemplo RUC no activo y establecimiento cerrado/clausurado. Antes de implementar el emisor electrónico se debe contrastar la última ficha técnica vigente publicada por el SRI; este documento no sustituye esa validación normativa.

En consecuencia:
- No generar el código de establecimiento con el código interno de Warehouse.
- No tratar el ID de base de datos del almacén como número oficial SRI.
- No asumir que el punto de emisión puede derivarse del número de establecimiento.
- No permitir que el frontend elija un número inventado solo para completar el formulario.
- La selección final de perfil tributario, establecimiento abierto y punto de emisión corresponde al backend de facturación, que debe validar sus requisitos actuales y consultar/usar la configuración oficial que le corresponda.

La pantalla «Almacenes y contactos» debe mantener correctos los datos fiscales y ofrecer la relación con los almacenes operativos, mientras que el backend de facturación seguirá siendo el responsable de construir y validar comprobantes.

### 3.4 No asumir una API pública con el JSON exacto

El SRI dispone de una consulta pública de contribuyentes y de herramientas autenticadas para actualizar el RUC y obtener certificados. La evidencia oficial revisada no establece que el JSON concreto compartido por el negocio sea una API pública estable del SRI. El JSON podría proceder de un proveedor de consulta, de un adaptador privado o de una transformación intermedia.

Antes de construir la integración automática hay que registrar de forma explícita:
- fuente real de la consulta;
- URL/documentación y versión del contrato si se trata de un proveedor;
- licencia, condiciones de uso y límites;
- autenticación y gestión de secretos;
- campos originales y su mapeo al modelo interno;
- política de errores, rate limit y tiempo de vigencia.

No se recomienda depender de scraping no documentado del portal del SRI ni simular que un proveedor de terceros es la fuente oficial. Si no existe una API automatizada aprobada para el caso de uso, la alternativa segura es una importación/verificación controlada de datos obtenidos desde canales oficiales, identificando quién los cargó y cuándo se verificaron.

## 4. Modelo de dominio recomendado

### 4.1 Entidades independientes

| Entidad | Qué representa | Datos principales |
|---|---|---|
| TaxpayerProfile | Un RUC de contribuyente de la organización | RUC, razón social, estado, régimen, obligaciones, fechas, flags tributarios, source, verifiedAt, freshness, schemaVersion. |
| TaxpayerSnapshot | Evidencia de una consulta/actualización de un perfil | fuente, hora, hash del contenido, campos normalizados, resultado, versión de contrato, usuario o tarea que la obtuvo. |
| SriEstablishment | Establecimiento oficial en el RUC | perfil tributario, código de tres dígitos, nombre comercial opcional, dirección/ubicación, estado oficial, fuente y última verificación. |
| SriEstablishmentSnapshot | Histórico del estado/dirección y datos observados | establecimiento, timestamp, código normalizado, estado recibido y hash del snapshot. |
| Warehouse | Lugar operativo de inventario actual | código interno, nombre, dirección operativa, active y ubicaciones del almacén. |
| WarehouseFiscalMapping | Asociación entre un almacén operativo y un establecimiento SRI | warehouseId, sriEstablishmentId, propósito, vigencia desde/hasta, preferencia y auditoría. |
| BusinessPartner | Contacto comercial común | código, identificación y datos de contacto; un registro con roles de proveedor, cliente o ambos. |
| PartnerRole | Rol comercial vigente del contacto | SUPPLIER, CUSTOMER u otros roles futuros; debe evitar entidades duplicadas por la misma identificación. |
| UnitOfMeasure | Unidad de medida usada por productos y movimientos | código, nombre, dimensión, escala decimal, estado y regla de compatibilidad. |
| UnitConversion | Conversión declarada entre unidades compatibles | unidad origen/destino, factor, versión, vigencia y auditoría. |

Los nombres son propuestas de modelo; se pueden adaptar a convenciones del proyecto. No se debe convertir el actual monolito modular en microservicios separados solo por introducir estas entidades.

### 4.2 Perfil tributario y sincronización

El perfil actual debe separar, como mínimo, cuatro estados:

- **Estado recibido del SRI/proveedor:** valor original (por ejemplo, ACTIVO o SUSPENDIDO).
- **Estado normalizado interno:** mapeo conocido; UNKNOWN para valores nuevos/no interpretados.
- **Estado de sincronización:** VERIFIED, STALE, SOURCE_UNAVAILABLE, SOURCE_ERROR o MANUAL_REVIEW.
- **Estado de uso operativo local:** si el perfil está configurado para el negocio, independientemente del estado tributario.

Los estados no deben fusionarse en un solo booleano active. Un contacto puede seguir siendo histórico y un almacén puede tener existencias aunque el RUC esté suspendido o un establecimiento esté cerrado. Esto no significa que se deba emitir una factura desde él. La decisión fiscal la impone el backend de facturación; la operación de inventario sigue sus propias reglas.

### 4.3 Mapeo de almacenes a establecimientos

La relación entre un almacén y un establecimiento SRI debe ser opcional y explícita. Puede haber varios almacenes internos asociados a un mismo establecimiento tributario. Un almacén central de distribución que no emite comprobantes puede permanecer sin asociación fiscal. Si una operación futura requiere asignar un origen fiscal diferente, se deberá especificar la política y una asociación con vigencia, no reutilizar un código arbitrario.

El mapeo es una referencia útil para el sistema de facturación, no una orden automática de emitir desde ese establecimiento. El backend de facturación es responsable de seleccionar un establecimiento elegible y punto de emisión válido con las reglas pertinentes. Si un establecimiento vinculado pasa a cerrado o el contribuyente pasa a suspendido, la pantalla debe señalizar la inconsistencia y pedir revisar el mapeo; no debe desactivar ni borrar el almacén automáticamente.

### 4.4 Fuente y trazabilidad de cada campo

Para los datos tributarios que vienen del SRI/proveedor, cada sincronización debe conservar:
- hora consultada y hora recibida de la fuente si están disponibles;
- nombre/ID de la fuente;
- versión del mapeador;
- estado de respuesta;
- identificador de solicitud del proveedor, si existe;
- hash del payload y una instantánea auditada de los campos necesarios;
- campos que cambiaron en comparación con la consulta anterior.

Guardar el mínimo de datos necesario y restringir acceso a los payloads originales. No guardar contraseñas del SRI ni credenciales de un proveedor de consulta dentro del JSON del contribuyente. Los campos normalizados sirven para el negocio; la evidencia de origen permite explicar por qué cambió un estado.

## 5. Diseño de la pantalla «Almacenes y contactos»

### 5.1 Encabezado

Título: **Almacenes y contactos**  
Subtítulo: “Ubicaciones operativas, datos tributarios y contactos comerciales”.

El encabezado debe ofrecer:
- botón contextual “Nuevo almacén”, “Buscar/añadir RUC”, “Nuevo proveedor” o “Nuevo cliente” según pestaña;
- estado de carga y fecha de última actualización;
- botón “Actualizar datos tributarios” solo en la vista SRI y para roles autorizados;
- indicadores de filtros activos y opción de limpiarlos.

No mostrar un botón genérico de “Nuevo registro” que abra el formulario incorrecto. Los accesos deben coincidir con el dominio de la pestaña.

### 5.2 Pestañas recomendadas

1. **Almacenes operativos**
2. **Contribuyente y establecimientos SRI**
3. **Proveedores**
4. **Clientes**

Un enlace secundario dirige a **Productos → Configuración del catálogo → Unidades y conversiones**. Se conserva acceso desde la pantalla actual durante la migración, pero no se sigue presentando la configuración de unidades como un contacto.

Cada pestaña tendrá su propio buscador, filtros, paginación, skeleton, estado vacío, error y permisos. La UI no debe cargar todas las listas al entrar en esta pantalla.

### 5.3 Pestaña Almacenes operativos

La tabla debe describir espacios físicos que contienen stock. Columnas:
- código interno del almacén;
- nombre operativo;
- dirección operativa;
- estado interno (activo/inactivo);
- establecimientos fiscales asociados (número y estado, cuando exista asociación);
- resumen de stock/tareas pendientes;
- última actualización;
- acciones permitidas.

Filtros:
- texto en código/nombre/dirección;
- estado operativo;
- vínculo fiscal: asociado, no asociado o con incidencia;
- establecimiento y su estado oficial;
- almacenes con stock o con operaciones abiertas.

Las acciones incluyen detalle, editar datos operativos, gestionar ubicaciones internas y activar/desactivar conforme a las reglas ya existentes. No incluir controles para editar razón social, estado tributario o código de establecimiento en el formulario de almacén.

La ficha de detalle mostrará dos bloques claramente separados:

**Datos operativos:** código, nombre, dirección interna, ubicaciones, existencias y movimientos.

**Relación tributaria:** establecimiento SRI vinculado (si lo hay), código oficial, estado recibido, última verificación y advertencias. El usuario no cambia el estado SRI manualmente. Para corregir un dato, se actualiza en el origen y se sincroniza.

### 5.4 Pestaña Contribuyente y establecimientos SRI

Esta vista debe ofrecer primero el perfil del RUC y debajo el conjunto completo de establecimientos registrados.

**Cabecera del contribuyente**
- RUC y razón social;
- estado del contribuyente destacado en texto, no solo color;
- motivo de suspensión si aplica;
- tipo de contribuyente, régimen, categoría;
- obligado a llevar contabilidad, agente de retención y contribuyente especial;
- indicadores de contribuyente fantasma y transacciones inexistentes si la fuente los proporciona;
- fechas de inicio, actualización, cese y reinicio;
- origen de los datos y última verificación.

**Establecimientos**
- pestañas o filtros “Todos”, “Abiertos” y “Cerrados”;
- código oficial (tres dígitos, preservar ceros);
- indicador de matriz solo si está presente en la fuente;
- nombre comercial (puede estar vacío);
- ubicación/dirección;
- estado oficial;
- almacenes internos asociados;
- última observación y advertencia de datos desactualizados.

**Acciones**
- consultar/actualizar RUC;
- comparar cambios con la consulta anterior;
- revisar el historial de estado;
- vincular/desvincular un almacén operativo con autorización;
- copiar o exportar una ficha interna, si se necesita.

No permitir crear, cerrar ni cambiar un establecimiento oficial desde Inventary. Esas acciones se hacen ante el SRI; Inventary importa el resultado verificado. El botón “Actualizar” significa refrescar la consulta/instantánea, no hacer una actualización tributaria en nombre del contribuyente.

**Estados de experiencia**
- RUC nuevo: captura de identificación, consulta, vista previa de datos recibidos y confirmación para añadir al catálogo.
- Consulta en curso: skeleton y bloqueo del doble envío.
- RUC no encontrado: mensaje explícito y posibilidad de revisar identificación.
- Fuente no disponible: mantener el último snapshot confirmado y marcarlo desactualizado; no reemplazar los datos por vacío.
- Datos desactualizados: mostrar fecha y advertencia; ofrecer reconsulta.
- Campos nuevos desconocidos: preservar valor original y mostrar “Estado no reconocido; requiere revisión”.
- RUC con estado no activo: mostrar advertencia fiscal clara y no marcar como activo tributariamente.
- Establecimiento cerrado: no permitir usarlo como establecimiento fiscal predeterminado sin revisar la configuración; conservar el registro para historia.
- Varios establecimientos: presentarlos como registros independientes, no concatenar códigos ni elegir el primero automáticamente.

### 5.5 Pestaña Proveedores

Tabla paginada, con:
- nombre/razón social;
- RUC/identificación;
- código comercial;
- contacto principal (correo/teléfono);
- estado comercial local;
- indicador tributario si el contacto usa RUC;
- última verificación del RUC;
- última compra o última recepción si existe una consulta eficiente;
- acciones.

Filtros: texto (nombre, código, RUC, correo, teléfono), activo/inactivo, estado tributario y tipo de identificación. La búsqueda debe ser en backend. La fila puede abrir un detalle de proveedor con historial de compras y documentos enlazados.

CRUD que debe poder completarse:
- crear proveedor;
- ver detalle;
- editar datos comerciales y contacto;
- activar/desactivar;
- eliminar físicamente solo si está inactivo y el backend confirma que no hay referencias;
- actualizar/verificar datos fiscales cuando la identificación sea un RUC y exista una fuente autorizada.

Separar claramente datos oficiales importados del RUC (solo lectura) de datos comerciales mantenidos por el negocio (alias, correo de compras, teléfono, plazo de pago si este campo existe en el dominio, notas y contactos). Un nombre comercial no debe sobrescribir automáticamente la razón social oficial.

### 5.6 Pestaña Clientes

La lista y CRUD deben ser paralelos al directorio de proveedores en cuanto a búsqueda, paginación, estado, detalle y manejo de errores, pero con etiquetas y acciones centradas en ventas.

Columnas: nombre/razón social, identificación, código, correo, teléfono, estado comercial local, estado tributario consultado cuando corresponda, última venta y acciones.

El alta de clientes debe distinguir:
- persona natural con cédula;
- contribuyente con RUC;
- pasaporte/identificación extranjera, si el negocio la admite;
- consumidor final/venta rápida conforme a la política de ventas/facturación.

La validación debe depender del tipo de identificación y de las reglas actuales del dominio, no exigir un RUC a todos los clientes. La consulta SRI se ejecuta solamente cuando corresponde.

La ficha no debe convertir el estado tributario del cliente en un estado comercial automático. Se muestra el dato oficial con su fecha para que las reglas de facturación, a cargo de su backend, decidan cómo aplica.

## 6. CRUD desde Compras y Ventas, sin abandonar el flujo

### 6.1 Compras: proveedor en contexto

En PurchaseForm.jsx, el selector de proveedores debe:
- buscar en servidor cuando el usuario escribe nombre, código o RUC;
- mostrar nombre comercial/razón social, identificación y estado cuando ayude a distinguir;
- mostrar solo proveedores activos para nuevas órdenes;
- ofrecer “Crear proveedor” a quien tenga partners:manage y el permiso requerido para continuar con la compra;
- ofrecer “Editar proveedor” al usuario autorizado, sin convertir el cambio de contacto en una modificación de la orden ya registrada.

Al crear un proveedor desde Compras:
1. abrir un panel lateral de formulario compacto o una página guiada si el alta fiscal requiere varios pasos;
2. permitir búsqueda/verificación de RUC cuando corresponda;
3. guardar datos oficiales y comerciales en los respectivos campos;
4. volver al formulario de compra original;
5. seleccionar automáticamente el contacto creado;
6. conservar almacén, productos, cantidades, costes, notas y todos los datos ya ingresados.

El fallo al guardar un proveedor no debe resetear el formulario de compra. Si el usuario no tiene partners:manage, el botón de creación no se muestra y se ofrece un enlace al contacto central o una explicación de permisos.

El alta rápida no debe omitir las validaciones del endpoint central ni crear un segundo proveedor solo porque el RUC ya exista como cliente.

### 6.2 Ventas: cliente en contexto

En SaleForm.jsx, el selector de clientes debe permitir buscar por nombre/código/RUC/teléfono y crear un nuevo cliente sin salir del carrito. Se mantiene la venta rápida sin cliente registrado donde las reglas de negocio la permiten.

Al crear un cliente en contexto:
- abrir el panel lateral de alta simple;
- mostrar campos distintos por tipo de identificación;
- consultar SRI si se introduce RUC y la fuente está disponible;
- al terminar, regresar a la venta y seleccionar el cliente creado;
- mantener almacén, líneas, cantidades y cotización/carrito sin pérdida.

La edición contextual solo cambia el registro maestro futuro. No debe cambiar retroactivamente la identificación o el nombre que quedaron fotografiados en ventas ya emitidas. Los documentos históricos conservan sus snapshots según el diseño del backend de ventas/facturación.

### 6.3 Un único contacto con múltiples roles

Como BusinessPartner admite CUSTOMER, SUPPLIER y BOTH y el taxId es único globalmente, la UX debe resolver el caso “este mismo RUC ya existe, pero lo necesito también como cliente/proveedor” con una acción segura:
- detectar el contacto existente;
- ofrecer añadir el rol faltante, sujeto a permiso;
- reutilizar el mismo ID en compras y ventas;
- no clonar el contacto ni borrar historial;
- auditar el cambio de roles;
- bloquear la operación si el contacto ya está inactivo o hay una incompatibilidad real del dominio, y explicar cómo resolverla.

El backend necesita un endpoint/operación específica para añadir o quitar roles con reglas de integridad. No basta con editar el tipo en React.

## 7. Paginación, búsqueda y estados comunes

### 7.1 Listados centrales

Almacenes, proveedores y clientes deben utilizar page/size/sort y totales del backend. Los proveedores/clientes ya aceptan q y active, así que la primera mejora consiste en dejar de llamar listAllPages y exponer paginación en OperationsCatalogs.

Parámetros iniciales sugeridos: page 0, size 20, opciones 10/20/50 y máximo del backend. Mostrar “21–40 de 386” y permitir anterior/siguiente y páginas cuando sean razonables. Cambiar búsqueda/filtro vuelve a página 0. Conservar tab, búsqueda, filtros, sort y page en URL.

La búsqueda puede ser debounced 300–400 ms o ejecutarse al enviar el formulario. Las respuestas antiguas de búsquedas rápidas no deben sobrescribir la más reciente. Al cambiar un estado y bajar el total, mover la vista a la página válida más cercana.

Unidades y conversiones pueden seguir usando listas pequeñas sin paginación en la fase inicial, pero deben tener filtros de activos/inactivos donde aplique, estados de carga y errores independientes. Si se convierten en catálogos grandes, se paginan por backend; no se justifica paginar por obligación una lista técnica que mantiene pocas decenas de registros.

### 7.2 Estados de interfaz

Todas las pestañas deben contemplar:
- skeleton para primera carga;
- actualización que conserva los datos anteriores mientras consulta;
- vacío real;
- ningún resultado por filtros con acción “Limpiar filtros”;
- error 4xx/5xx o red con Reintentar;
- 401, 403 y sesión expirada con tratamiento específico;
- conflicto 409 (duplicado de RUC, código o taxId) con enlace al registro existente cuando pueda resolverse de forma segura;
- validación 422 junto al campo;
- datos tributarios viejos/actuales diferenciados;
- estado de eliminación bloqueada por referencias históricas.

Nunca traducir un error o permiso insuficiente a “no hay registros”.

## 8. Backend: modelo y endpoints propuestos

### 8.1 API de RUC y establecimientos

Las rutas son un diseño propuesto; no están implementadas actualmente. Se recomienda una API de aplicación con operaciones como:

- GET /api/v1/taxpayers: listados de perfiles tributarios registrados, con búsqueda, estado y paginación.
- POST /api/v1/taxpayers/lookup: consultar una fuente aprobada por RUC, sin guardar todavía el resultado como perfil activo.
- POST /api/v1/taxpayers: confirmar/importar un perfil con el snapshot consultado.
- GET /api/v1/taxpayers/{id}: detalle y última verificación.
- POST /api/v1/taxpayers/{id}/refresh: reconsultar la fuente aprobada y comparar cambios.
- GET /api/v1/taxpayers/{id}/snapshots: historial paginado de consultas y cambios.
- GET /api/v1/taxpayers/{id}/establishments: lista de establecimientos del perfil.
- GET /api/v1/sri-establishments/{id}: detalle/estado/origen.
- PUT o POST para crear/eliminar un WarehouseFiscalMapping con vigencia y auditoría; nunca mutar los datos fiscales recibidos.

Si ya existe un cliente aprobado para consultar el RUC, se implementa como adapter detrás del servicio. La UI nunca debe llamar directamente a una API de terceros con secreto desde el navegador.

### 8.2 Proveedor de consulta tributaria intercambiable

Definir una interfaz de backend, por ejemplo SriRegistryProvider, que normalice la fuente externa a un modelo canónico:
- fuente e identificador de fuente;
- hora consultada;
- estado técnico de consulta;
- perfil tributario;
- lista de establecimientos;
- valores originales de cada campo;
- versión del mapeo.

La interfaz permite sustituir un proveedor si cambia el contrato o deja de estar disponible. La integración no debe depender de una transformación hecha exclusivamente en React.

Requisitos:
- timeout y rate limit;
- reintentos limitados para fallos transitorios;
- manejo distinto de “RUC no encontrado” y “proveedor no disponible”;
- logs sin secretos y con requestId;
- autenticación en backend, si el proveedor la exige;
- protección contra respuesta parcial que borre establecimientos válidos;
- validación de esquema y tratamiento de campos desconocidos;
- tests con fixtures anonimados o sintéticos; no usar un RUC real de terceros como fixture compartido sin motivo.

### 8.3 Normalización y frescura

- Guardar RUC como texto, no número, para preservar formato y ceros cuando correspondan.
- Validación de formato previa a consulta; la regla final para tipo de contribuyente se debe contrastar con la especificación oficial y no depender solo de un checksum universal.
- Guardar estado bruto y normalizado.
- Mantener el último snapshot válido si la fuente falla.
- La antigüedad de los datos se calcula por fecha consultada, con un umbral configurable por negocio; ese umbral operativo no se debe anunciar como plazo legal del SRI.
- Comparar snapshots y registrar el diff de estado, razón social, régimen y establecimientos.
- Si aparece un nuevo código/estado no conocido, preservarlo y elevar una advertencia; no convertirlo en activo.
- No permitir editar desde Inventary campos que la fuente declara como oficiales.
- Separar perfiles tributarios del negocio de clientes/proveedores: un perfil RUC puede ser utilizado por el proveedor y/o cliente correspondiente sin duplicar la ficha tributaria.

### 8.4 API de almacenes

Las APIs actuales de /warehouses continúan administrando la ubicación física. Se propone ampliar el detalle/DTO con un resumen de asociaciones fiscales, pero no copiar todos los campos SRI dentro de Warehouse ni reemplazar el código interno.

El endpoint de almacenes debe mantener:
- paginación y filtros de texto;
- filtros por estado operativo;
- filtros por asociación y estado fiscal asociado, si se necesitan;
- detalle de ubicación, stock y operaciones pendientes;
- relación con el establecimiento fiscal mediante entidad de asociación;
- reglas actuales para no desactivar/eliminar almacenes con stock o documentos históricos.

No bloquear creación de un almacén interno de distribución por no tener establecimiento fiscal. En cambio, si el negocio marca que desde una ubicación se gestionarán operaciones de venta/emisión, exigir que su mapeo fiscal se configure conforme a la política. El backend de facturación es quien valida el emisor y el punto de emisión antes de emitir.

### 8.5 CRUD de proveedores y clientes

Aprovechar GET /suppliers y GET /customers con q, active y pageable. El frontend debe llamar estos endpoints página por página.

Mejoras backend recomendadas:
- añadir tipo de identificación (RUC, cédula, pasaporte/identificación exterior, consumidor final u otros valores soportados) con catálogo explícito;
- validación por tipo de identificación y país;
- normalización de búsqueda de RUC y código;
- mantener la unicidad global donde corresponda, pero permitir asignar roles a la misma entidad;
- operar de forma atómica al agregar/quitar roles;
- proteger cambios concurrentes de taxId/código para evitar duplicados;
- devolver un error de duplicado con un código estable y, si es seguro, el ID del contacto existente;
- auditoría de creación, edición, cambios de rol, activación/desactivación y sincronización fiscal;
- conservar la eliminación física restringida cuando existan compras, ventas u otros documentos;
- soportar campos comerciales adicionales como alias comercial, contactos secundarios, dirección de entrega/facturación y condiciones comerciales solo después de definirlos en el dominio y los flujos que los consumen.

No se recomienda ampliar el DTO de cliente/proveedor con decenas de campos “por si acaso”. Los campos deben tener dueño, validación y consumidor de negocio.

### 8.6 Unidades y conversiones: ordenar antes de ampliar

Mantener CatalogStructureController y CatalogStructureService como dueños de las unidades. Cambiar de ubicación la sección de UI, no duplicar la API.

Mejoras backend:
- permitir filtro por active y uso claro de una unidad por código/dimensión;
- proteger la dimensión de una unidad cuando ya se use en productos, variantes o movimientos;
- limitar cambios de decimalScale cuando la unidad tenga referencias, porque altera cómo se capturan y validan cantidades;
- exponer una lista paginada únicamente si el número de unidades crece lo suficiente; inicialmente es un catálogo pequeño;
- añadir GET de conversiones filtrable por dimensión y operaciones de desactivación/edición cuando se definan reglas seguras;
- no borrar conversiones históricas que estén referenciadas;
- validar igualdad de dimensión y factor en el servicio y devolver error de dominio consistente, sin depender únicamente de que el constructor de entidad arroje IllegalArgumentException;
- detectar ciclos contradictorios y conversiones duplicadas inversas;
- declarar una dirección y una semántica exacta para el factor (por ejemplo, 1 caja equivale a 12 unidades base);
- no permitir conversiones entre dimensiones incompatibles como kilogramo y litro sin una conversión por producto/densidad explícitamente modelada.

### 8.7 El contrato transaccional de unidades futuras

La implementación actual de compra/venta recibe cantidades sin unidad de medida por línea, y el producto mantiene su unidad base. Antes de admitir una venta por cajas o una compra por paquetes, definir un contrato como:

- enteredQuantity y enteredUnitOfMeasureId: lo ingresado por la persona;
- baseQuantity y baseUnitOfMeasureId: cantidad normalizada para inventario;
- conversionFactorSnapshot y conversionVersion: factor aplicado en el momento de la operación;
- enteredUnitPrice y priceBasis: precio por la unidad ingresada o por unidad base;
- unidad, factor y cantidades snapshot en la línea histórica.

Las cantidades normalizadas deben ser la unidad usada por saldo, reserva y movimientos. Así, una recepción de 2 cajas de 12 crea la cantidad base equivalente y queda registrada la unidad en que se compró. La venta de una caja debe consumir exactamente la cantidad base correspondiente. Un cambio futuro de factor no puede reescribir operaciones antiguas.

Fase previa a su implementación:
1. decidir si compras/ventas permiten unidades alternativas por línea;
2. definir conversiones válidas por producto y dimensiones;
3. acordar reglas de precio, redondeo, fracciones, coste y devoluciones;
4. adaptar DTOs, líneas de documento y snapshots;
5. actualizar formularios y validación de backend juntos;
6. migrar sin cambiar la interpretación de documentos históricos.

Hasta completar esos pasos, la interfaz debe mostrar la unidad base del producto y no hacer conversiones implícitas.

## 9. Permisos recomendados

| Acción | Permiso/decisión |
|---|---|
| Consultar almacenes y asociaciones | inventory:read |
| Crear/editar/activar/desactivar almacenes | inventory:manage |
| Consultar proveedores | purchasing:read |
| Crear/editar/activar/desactivar proveedores | partners:manage, y permisos del flujo de compras cuando aplique |
| Consultar clientes | partners:read |
| Crear/editar/activar/desactivar clientes | customers:manage, y permisos del flujo de ventas cuando aplique |
| Consultar perfil RUC y establecimientos | Permiso de lectura de contactos/operación, limitado al ámbito configurado |
| Actualizar fuente tributaria | Permiso de gestión de datos maestros o tributarios y endpoint de backend autorizado |
| Ver snapshots/historial fiscal completo | Permiso separado si el payload incluye datos adicionales sensibles |
| Gestionar unidades/conversiones | catalog:manage |
| Consultar unidades | catalog:read |
| Ver costes de inventario o datos financieros | reports:financial según contrato vigente |

La UI recibe el mapa de permisos desde App y no muestra controles que no puede ejecutar. Pero el backend sigue validando todo por llamada. Un 403 se presenta como error de autorización, nunca como “sin datos”.

Para proveedor en Compras, mostrar el alta contextual solo si el usuario tiene partners:manage y el permiso para continuar la operación que está creando. Para cliente en Ventas, aplicar la misma regla con customers:manage y sales:manage. Un usuario que puede vender pero no crear clientes puede seguir seleccionando los existentes o usar venta rápida cuando el dominio la permita.

## 10. Migración de datos y convivencia con el sistema actual

### Fase 0 — Corregir UI con APIs existentes
- Dejar de usar listAllPages en proveedores/clientes/almacenes para renderizar tablas.
- Consumir page/size/q/active y mostrar paginación real.
- Añadir permisos a OperationsCatalogs y acciones.
- Crear proveedor/cliente contextual conservando el formulario de compra/venta.
- Mantener los campos actuales de Warehouse.
- Reubicar Unidades a Configuración del catálogo sin eliminar endpoints ni datos.
- Corregir estados de loading/error y distinguir vacío de 403/fallo.

### Fase 1 — Modelo tributario y establecimientos
- Formalizar el adapter/fuente de consulta y su contrato.
- Crear las tablas de perfil tributario, establecimientos y snapshots.
- Importar los datos de RUC mediante flujo explícito de consulta/verificación.
- Crear asociación opcional entre Warehouse y establecimiento SRI, con vigencia y auditoría.
- Agregar estado/frescura en la vista y mantener datos fiscales read-only.
- No modificar el flujo de facturación en esta fase, pero documentar el contrato que ese backend consumirá.

### Fase 2 — Contactos robustos
- Unificar roles CUSTOMER/SUPPLIER/BOTH o añadir operaciones de rol equivalentes.
- Añadir tipo de identificación.
- Añadir búsqueda por taxId, email/teléfono si las necesidades la justifican.
- Duplicados con enlace al registro existente.
- Crear/editar cliente/proveedor contextual.
- Mantener snapshots de datos en documentos de negocio para que un cambio posterior de contacto no altere el historial.

### Fase 3 — Unidades transaccionales
- Analizar cómo se compran/venden cantidades hoy.
- Implementar unit snapshots y conversionFactorSnapshot por línea si se requieren unidades de compra/venta alternativas.
- Actualizar saldo, valoración, devoluciones, reportes y forms.
- Migrar con pruebas de regresión e integridad.
- Mantener el catálogo de unidades como configuración de Productos.

## 11. Matriz de escenarios que hay que cubrir

| Escenario | Resultado esperado |
|---|---|
| Un RUC tiene varios establecimientos abiertos y cerrados | Se muestran todos, cada uno con número, estado y dirección; no se elige uno automáticamente. |
| Nombre comercial nulo | Se presenta “Sin nombre comercial registrado”; no se rellena con el nombre del almacén. |
| Estado tributario SUSPENDIDO | Se conserva el estado recibido, motivo y timestamp; no se transforma en “Inactivo” sin distinguir el origen. |
| Contribuyente con estado desconocido nuevo | Preservar el valor original y marcar revisión necesaria. |
| SRI/proveedor no disponible | Mantener último dato confirmado y marcarlo desactualizado; no vaciar ni marcar activo por defecto. |
| RUC no encontrado | Mostrar estado no encontrado y permitir corregir la identificación. |
| Código del establecimiento 001 | Guardarlo como texto para no perder ceros iniciales. |
| Establecimiento cerrado vinculado a almacén con stock | Mantener stock e historial; alertar asociación fiscal y dejar la decisión de emisión al backend fiscal. |
| Almacén interno sin vínculo fiscal | Permitir su administración operativa, pero no presentarlo como establecimiento fiscal habilitado. |
| Varios almacenes vinculados a un establecimiento | Permitido si el mapeo se define explícitamente; no copiar el establecimiento como si fueran entidades distintas. |
| Cambio de RUC/razón social en una sincronización | Crear snapshot y auditar diferencias; actualizar datos derivados sin reescribir facturas antiguas. |
| Un contacto ya existe como cliente y se necesita como proveedor | Añadir rol al mismo contacto; no intentar duplicar código/taxId. |
| Proveedor con compras históricas se desactiva | Conservar órdenes y recepciones; impedir nuevas selecciones en compras cuando corresponda. |
| Eliminar proveedor/cliente con referencias | Backend bloquea la eliminación física y explica el motivo. |
| Crear proveedor desde Compras | Guardar y volver a la compra con proveedor seleccionado y todas las líneas preservadas. |
| Crear cliente desde Ventas | Guardar y volver a la venta con cliente seleccionado y el carrito intacto. |
| Usuario sin partners:manage | No mostrar alta/edición de proveedor, pero permitir seleccionar proveedores autorizados. |
| Más de 100 proveedores/clientes | Paginación remota; buscar por RUC/nombre sin descargar el catálogo completo. |
| Buscar contacto mientras responde una consulta anterior | Solo aplicar la respuesta de la última búsqueda. |
| Unidad ya asignada a producto histórico | Evitar eliminación física o cambio de dimensión que modifique la semántica de documentos. |
| Conversión entre unidades de dimensión distinta | Rechazar con error de dominio legible. |
| Conversión inversa duplicada o factor incoherente | Prevenir duplicación y definir reglas de consistencia. |
| Cambio de factor después de compras/ventas | Los documentos históricos mantienen el factor snapshot, no se reinterpretan. |
| Venta rápida sin cliente | Seguir permitida si lo autorizan las reglas actuales y el flujo fiscal correspondiente. |
| Consulta devuelve error 403/500 | Mostrar error de permisos/servidor, nunca un empty state. |

## 12. Pruebas y criterios de aceptación

### Frontend
- Paginación de almacenes, proveedores y clientes: primera/última página, tamaño 10/20/50, filtros y query params en URL.
- Skeleton, empty, filtered-empty, error, forbidden, retry y freshness warning.
- Formulario de alta/edición, validación visible y confirmación de abandono.
- Crear proveedor durante compra y cliente durante venta, conservando todos los campos de la operación.
- No mostrar acciones sin el permiso requerido.
- Resolver duplicado por taxId/código proponiendo abrir el contacto existente.
- Mostrar correctamente múltiples establecimientos, código con ceros, null de nombre comercial, suspendido y cerrado.
- Fuente tributaria no disponible: último snapshot visible y marcado como stale.
- Navegación de teclado, foco y formularios accesibles.
- Unidades en configuración de catálogo con enlace desde Almacenes y contactos; sin pérdida de acceso ni datos.

### Backend
- CRUD y paginación de proveedores/clientes/almacenes con filtros y sort válidos.
- Un mismo contacto puede tener roles de proveedor y cliente sin duplicidad de ID ni pérdida de histórico.
- Integridad de taxId/código concurrente.
- RUC de 13 dígitos como texto y validación por tipo según contrato; entradas inválidas no generan una consulta de proveedor.
- Adaptador tributario: success, not found, timeout, 429, 5xx, payload parcial, campos desconocidos y cambios de esquema.
- Snapshots de consulta, fuente, hash, timestamp y auditoría.
- No se sobrescriben datos confirmados con un error de consulta.
- Estados del RUC y establecimientos independientes de estado de Warehouse.
- Asociación fiscal con vigencia, almacenes sin asociación y varios almacenes por establecimiento.
- Permisos de lectura/sincronización y no filtración del payload en logs.
- Unidades: compatibilidad de dimensión, factor, vigencia, redondeo, bloqueo de cambios peligrosos y conservación de referencias.
- Si se implementan unidades por línea: compras/ventas/recepciones/devoluciones/valoración/movimientos convierten a unidad base exactamente una vez y preservan snapshot.
- Los documentos históricos de compras/ventas no cambian al modificar el contacto o las reglas de unidad actuales.

### Criterios de salida
- La pantalla no consulta todos los contactos solo para renderizar una tabla.
- Los datos SRI se distinguen de datos comerciales y de los campos internos de almacén.
- Todos los establecimientos registrados se pueden representar con estado y procedencia.
- La vinculación entre almacén interno y establecimiento fiscal es explícita y auditable.
- Compras y Ventas permiten el alta contextual sin perder el borrador/carrito.
- Los proveedores/clientes tienen CRUD completo central con paginación real.
- Las unidades siguen existiendo, pero su ubicación y contratos reflejan su función de catálogo y cantidad.
- No se ofrecen conversiones de compra/venta hasta que el contrato de línea incluya unidad origen, cantidad base y factor snapshot.
- El backend de facturación recibe datos tributarios consistentes pero sigue validando la emisión con su propia lógica y la especificación vigente.
- Las pruebas de permisos, duplicados, concurrencia, snapshots y regresión pasan.

## 13. Fuentes oficiales y documentos técnicos

Referencias oficiales del SRI consultadas el 29 de octubre de 2026:

- Consulta pública del RUC en SRI en Línea: https://srienlinea.sri.gob.ec/facturacion-internet/consultas/publico/ruc-datos1.jspa
- Portal oficial RUC de personas naturales; explica consulta del catastro y gestiones del RUC: https://www.sri.gob.ec/ruc-personas-naturales
- Portal oficial RUC de sociedades: https://www.sri.gob.ec/ruc-sociedades
- Guía del contribuyente de actualización del RUC y certificados, publicada por el SRI en 2026. Describe establecimientos abiertos/cerrados, actualización de direcciones y gestión en el sistema oficial: https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/960ce2a0-c184-4b6a-a66c-1aa41b43377c/Gu%C3%ADa%20para%20el%20contribuyente-Actualizaci%C3%B3n%20de%20RUC%20en%20l%C3%ADnea.pdf
- Ficha técnica SRI de comprobantes electrónicos consultada como referencia de códigos de establecimiento/punto de emisión y reglas de validación. La versión consultada es histórica, por lo que el backend de facturación debe verificar la ficha técnica vigente antes de implementar: https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/ea5a031a-08ff-4292-94b1-c9d759c63553/FICHA%2BTECNICA%2BCOMPROBANTES%2BELECTR%25D3NICOS%2Bversi%25F3n%2B1%2B10%2B13%2B04%2B2015.pdf
- Herramienta oficial de comprobantes electrónicos y sus avisos de actualización: https://www.sri.gob.ec/herramienta-de-comprobantes-electronicos
- Manual de usuario del Facturador SRI en línea, con configuración de puntos de emisión vinculados a establecimientos que figuran activos: https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/016ec0eb-6c25-445c-b929-6ca971cf1df1/Manual%20de%20usuario%20-%20facturador_SRI.pdf

### Fuentes internas del repositorio revisadas

- app-inventary-ecuapp/src/features/operations/OperationsCatalogs.jsx
- app-inventary-ecuapp/src/features/operations/UnitConversions.jsx
- app-inventary-ecuapp/src/features/purchasing/PurchaseForm.jsx
- app-inventary-ecuapp/src/features/sales/SaleForm.jsx
- app-inventary-ecuapp/src/api.js
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/warehouse/Warehouse.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/warehouse/WarehouseController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/warehouse/WarehouseService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/warehouse/WarehouseDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/partners/PartnerController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/partners/PartnerService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/partners/PartnerDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/partners/BusinessPartner.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/catalog/CatalogStructureController.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/catalog/CatalogStructureService.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/catalog/UnitOfMeasure.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/catalog/UnitConversion.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/catalog/Product.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/catalog/ProductVariant.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseDtos.java
- microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SaleDtos.java
- microservices-inventary-ecuapp/docs/25-referencia-apis.md

Propuestas relacionadas:
- PROPUESTA_UX_PRODUCTOS_V2.md
- PROPUESTA_UX_VENTAS_V2.md
- PROPUESTA_COMPRAS_FRONTEND_BACKEND_V2.md
- PROPUESTA_INVENTARIO_AGENT_READY_V3.md
- PROPUESTA_UX_PEDIDOS_WHATSAPP_V2.md
- PROPUESTA_UX_SUSCRIPCIONES.md
- PROPUESTA_ARQUITECTURA_N8N_BACKEND_COMERCIAL_V2.md

## 14. Límites de esta entrega

Esta entrega añade el diseño en Markdown. No agrega todavía entidades, migraciones, endpoints de consulta SRI, adaptadores externos, permisos, CRUD contextual, paginación React ni nuevos contratos de unidad por línea. Las reglas tributarias definitivas, selección del punto de emisión y autorización de comprobantes corresponden al backend de facturación y deben contrastarse con la especificación oficial vigente del SRI. La consulta automática del RUC debe implementarse solo después de confirmar fuente autorizada y contrato real.
