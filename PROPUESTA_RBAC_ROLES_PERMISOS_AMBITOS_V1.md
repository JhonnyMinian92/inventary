# Propuesta V1 — Roles, permisos por pantalla/acción y ámbitos de acceso para Inventary

**Objetivo:** convertir RBAC de Inventary en un modelo empresarial claro, con permisos por acción, segregación de funciones y restricciones por entidad/local/bodega.
**Fecha:** 29 de octubre de 2026.
**Estado:** propuesta. No modifica los roles ni permisos existentes.

Documentos relacionados:
- [Arquitectura HTTP resiliente](PROPUESTA_ARQUITECTURA_DISTRIBUIDA_CLIENTES_HTTP_RESILIENCIA_V1.md)
- [Kafka y caché](PROPUESTA_KAFKA_CACHE_CAFFEINE_V1.md)
- [Plataforma de integraciones y webhooks](PROPUESTA_PLATAFORMA_INTEGRACIONES_ASINCRONAS_WEBHOOKS_V1.md)
- [Roadmap previo a Inventary V2](ROADMAP_CIERRE_ARQUITECTURA_INVENTARY_PRE_V2.md)
- [Almacenes, contactos, SRI y unidades V2](PROPUESTA_ALMACENES_CONTACTOS_SRI_UNIDADES_V2.md)

## 1. Resumen ejecutivo

Inventary ya tiene roles configurables y permisos por código: SUPER_ADMIN, AUDITOR, MANAGER_ALMACEN, VENDEDOR y SOPORTE_TI, con permisos como security:manage, catalog:read, inventory:manage, purchasing:approve, sales:cancel, reports:financial y operations:manage.

El principal vacío no es que no exista RBAC, sino que:
- algunos permisos amplios cubren demasiadas acciones dentro de un módulo;
- inventario/almacenes se autorizan sin una asignación de usuario a bodega/local;
- reports:financial se utiliza como puerta general incluso para reportes operativos;
- no hay un rol de contabilidad específico;
- flujos de aprobación necesitan segregación y límites explícitos;
- el backend debe validar cada solicitud, aunque React oculte o deshabilite botones.

Se recomienda RBAC más ABAC: el rol determina qué acción puede ejecutar la persona; el ámbito determina sobre qué entidad, local, bodega o recurso puede actuar. Un bodeguero con permiso de inventario no puede modificar una bodega ajena solo por conocer su ID.

Referencias:
- OWASP Authorization Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- NIST RBAC: https://csrc.nist.gov/projects/role-based-access-control/faqs
- Spring Security Method Security: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html

## 2. Estado actual comprobado

La migración base de RBAC está en microservices-inventary-ecuapp/src/main/resources/db/migration/V10__security_rbac_and_token_revocation.sql. V14 agrega costing:manage y operations:manage; V18 agrega sales:discount, asignado actualmente a SUPER_ADMIN.

Roles actuales:
- SUPER_ADMIN: todos los permisos configurados.
- AUDITOR: lectura de catálogo, inventario, compras, ventas, auditoría y partners.
- MANAGER_ALMACEN: catálogo e inventario de lectura/escritura, compras de lectura/escritura, partners y medios.
- VENDEDOR: catálogo/inventario/ventas de lectura, crear ventas y mantener clientes.
- SOPORTE_TI: permiso técnico y operaciones Outbox.
- Existen roles y permisos administrables, pero AppUser mantiene un rol directo y no modela asignaciones por local, bodega o establecimiento.

Permisos existentes observados en migraciones:
security:manage, catalog:read, catalog:manage, inventory:read, inventory:manage, purchasing:read, purchasing:manage, purchasing:approve, sales:read, sales:manage, sales:cancel, sales:discount, partners:read, partners:manage, customers:manage, media:manage, audit:read, technical:read, reports:financial, costing:manage y operations:manage.

Referencias:
- AppUser.java, UserManagementService.java, AppRole.java, AppRoleRepository.java y SecurityConfig.java.
- Migraciones V10, V14 y V18.
- microservices-inventary-ecuapp/docs/25-referencia-apis.md.

## 3. Principios de autorización

1. **Mínimo privilegio:** denegar por defecto.
2. **Cada solicitud se valida en servidor:** GET, POST, PATCH, PUT y DELETE, incluyendo IDs manipulados y llamadas directas fuera de React.
3. **Separar permiso de ámbito:** la acción permitida y el recurso autorizado son controles independientes.
4. **Centralizar políticas:** no dispersar lógica de autorización según nombres de rol en servicios.
5. **Soporte TI no es administrador comercial:** reintentar una integración no da permiso para modificar ventas, stock o documentos fiscales.
6. **Segregación de funciones:** quien solicita una compra/cancelación/conteo no debe aprobar por defecto la misma operación.
7. **Auditar acciones críticas:** actor, recurso, motivo, resultado y correlationId, sin secretos.
8. **Botones no son controles de seguridad:** UI mejora UX; la API sigue siendo autoridad.
9. **Revocación efectiva:** cambios de rol, permisos o asignación territorial deben surtir efecto en un plazo definido.
10. **Permisos por campo:** leer una venta no implica ver coste, margen, datos fiscales completos o PII innecesaria.
11. **Separación por empresa y ubicación:** filtrar por el ámbito permitido antes de consultas agregadas, paginación y exportación.
12. **Revocar y registrar denegaciones:** múltiples intentos sobre bodegas ajenas pueden ser un error de asignación o un riesgo.

## 4. Modelo recomendado: RBAC + ámbito

### 4.1 Entidades propuestas

- User: identidad activa.
- Role / Permission: roles reutilizables y acciones permitidas.
- UserRoleAssignment: roles múltiples por usuario si el producto los necesita.
- UserScopeAssignment: usuario, tipo/ID de ámbito, permisos aplicables, vigencia, otorgante y motivo.
- Organization/LegalEntity/Tenant: solo cuando multiempresa se implemente expresamente.
- Establishment: establecimiento oficial asociado al RUC.
- Warehouse: bodega operativa.
- WarehouseEstablishmentAssignment: relación N:M de operación entre bodega y establecimiento, separada de los permisos del usuario.
- ApprovalPolicy: límites y requisitos para aprobaciones.
- Delegation: sustitución temporal documentada si el negocio la requiere.

Un usuario puede trabajar en varias bodegas y una bodega tener varios responsables. No usar un único warehouseId en AppUser como modelo completo. Las asignaciones deben tener estado, vigencia, otorgante, razón y auditoría. Para varias entidades legales, el scope debe incluir el tenant/legalEntity.

### 4.2 Evaluación de cada comando

1. Validar autenticación.
2. Validar permiso para la acción.
3. Resolver actor y recursos en el backend.
4. Aplicar scope antes de devolver detalles sensibles.
5. Verificar estado/transición, límite y segregación.
6. Ejecutar en transacción local.
7. Registrar auditoría.
8. Devolver error controlado si no tiene autoridad.

No confiar en un warehouseId o establishmentId del navegador sin validar la asignación. No leer todos los registros y filtrarlos luego en React.

## 5. Roles objetivo para un negocio real

| Rol objetivo | Trabajo real y capacidad base | Prohibiciones por defecto |
|---|---|---|
| Dueño/propietario | Supervisa el negocio, autoriza excepciones sensibles y accede a reportes según política | No compartir una cuenta; las operaciones deben registrar actor individual |
| Administrador de negocio | Configura maestros/operación dentro de su ámbito | No administrar seguridad global ni aprobar todo automáticamente |
| Gerente de local | Supervisa uno o varios locales asignados, ventas y operaciones | No ver entidades/locales ajenos |
| Vendedor | Consultar catálogo, crear ventas, gestionar campos mínimos de clientes | No cambiar coste, stock manual, configuración fiscal o anular factura por su cuenta |
| Cajero | Caja/cobros si existe el módulo, cierre de caja y operaciones asignadas | No cambiar compras, stock o roles |
| Bodeguero | Recibir, preparar y mover mercancía en bodegas asignadas; conteos | No coste global, márgenes ni autoaprobación de ajustes extraordinarios |
| Jefe de bodega | Supervisa inventario, aprueba ajustes dentro de límites y gestiona reposición | No aprobar compras que creó, ni acceso automático a contabilidad |
| Comprador | Proveedores, solicitudes/órdenes y recepción según delegación | No aprobar su propia orden si política exige segregación |
| Aprobador de compras | Aprueba/rechaza compromisos conforme a monto, presupuesto y scope | No autoaprobar operaciones creadas/modificadas por él |
| Contador/finanzas | Documentos fiscales, costes, conciliaciones y workpapers | No cambiar inventario directamente ni marcar declaración presentada sin evidencia |
| Auditor | Verifica evidencia y cambios; solo lectura | No crear, editar, aprobar ni borrar registros auditados |
| Soporte TI | Health, métricas, logs redactados, Outbox y reintentos técnicos | No ver PII/finanzas por defecto; no editar hechos de negocio |
| Integración de máquina | Scope específico para una API/evento de sistema a sistema | Sin sesión humana ni permisos globales; no reutilizar credenciales entre servicios |

Los códigos actuales pueden mapearse a los perfiles nuevos sin forzar un rediseño total inmediato. Los roles personalizados se permiten con advertencias para permisos incompatibles o de alto riesgo.

## 6. Matriz por pantalla y acciones

Leyenda: R = leer; C = crear; E = editar; A = aprobar; X = ejecutar acción. Todo queda restringido por entidad/bodega/local y por permisos de campo.

| Pantalla/acción | Dueño/admin | Gerente local | Vendedor/cajero | Bodeguero/jefe | Comprador/aprobador | Contador | Auditor | Soporte TI |
|---|---|---|---|---|---|---|---|---|
| Resumen/dashboard | R de ámbito autorizado | R de sus locales | R comercial acotada | R de sus bodegas | R de compras asignadas | R financiero/fiscal | R evidencial | Solo salud técnica |
| Productos: consultar | R | R | R | R | R | R si lo necesita | R | No por defecto |
| Productos: crear/editar/desactivar | C/E según política | E delegado en local | — | Solicitar cambio | R | R clasificación | R | — |
| Precio/descuento | Configura política | Autoriza dentro de límites | Precio base y descuentos permitidos | — | — | R | R autorizado | — |
| Clientes: CRUD | C/E | C/E dentro del scope | Crear/editar datos mínimos | — | — | R fiscal según necesidad | R enmascarado | — |
| Ventas: crear | X | X en local | X en local | — | — | Solo con rol comercial adicional | — | — |
| Ventas: solicitar cancelación | Sí | Si scope | Sí con motivo | — | — | Reportar incidencia | — | — |
| Ventas: aprobar cancelación | A con permiso específico y actor distinto | Solo por delegación/límite | — | — | — | Rol fiscal separado si aplica | R | — |
| Devolución cliente | Aprobar excepción según política | Operar en su ámbito | Solicitar/registrar dentro de límites | Registrar recepción física | — | Conciliar efecto fiscal si autorizado | R | — |
| Compras: crear/enviar | X | X según límites | — | Solicitar reposición | Comprador C/E | R fiscal | R | — |
| Compras: aprobar | A según límite | A delegado | — | No autoaprobar | A con purchasing:approve | Revisa impacto, no sustituye aprobador | R | — |
| Recepción de compra | R | R local | — | X sobre orden aprobada/bodega asignada | X si autorizado | R documental | R | — |
| Devolución proveedor | Autorizar excepción | Ejecutar/solicitar por política | — | Preparar y registrar física | A según límite | Conciliar documento fiscal del proveedor | R | — |
| Inventario: consultar | R | R local | Disponibilidad comercial sin coste | R de bodegas asignadas | R para reposición | R de coste/finanzas | R evidencia | — |
| Ajuste manual | Aprobar según política | Aprobar dentro del límite | — | Solicitar o ejecutar si autorizado; ajuste extraordinario con segundo actor | — | No ajustar stock | R | — |
| Conteo físico | R/aprobar diferencia alta | Control local | — | Contar bodega asignada | — | Revisar coste | R | — |
| Traslados | R/X según delegación | Iniciar/aprobar entre locales propios | — | Crear/despachar/recibir según bodega asignada | — | Consultar valoración | R | — |
| Reposición/reglas | Configurar política | Gestionar local delegado | — | Reglas de sus bodegas | Comprar tras solicitud | R | R | — |
| Costeo/valoración | X autorizado | R si política | — | No coste global por defecto | R si justificado | R/X según rol | R | — |
| Suscripciones | X según permiso | X de ámbito | C/gestión comercial; descuento especial restringido | — | — | R/conciliar cuotas | R | — |
| Pedidos WhatsApp | Gestionar según rol | Gestionar de su ámbito | Atención si autorizado | Cumplimiento logístico si asignado | — | Consulta fiscal | R | Diagnóstico solo técnico |
| Almacenes/establecimientos SRI | Configuración fiscal solo autorizada | Ver bodega local | — | Ver bodega asignada; no cambiar mapeo fiscal | R operativo | Mantener datos tributarios según función | R histórico | Diagnóstico |
| Clientes/proveedores | C/E | C/E ámbito | Clientes mínimos; no proveedores | Proveedores operativos si autorizado | C/E proveedores | Datos fiscales | R | — |
| Reportes operativos | R | R local | Comercial limitado | Stock/movimientos | Compras/proveedores | R | R | — |
| Reportes coste/margen/fiscal | R autorizado | No por defecto | — | No por defecto | Costo necesario según política | R | R autorizado | No por defecto |
| Exportación masiva | Permiso separado | Limitada a scope | No por defecto | Solo operativa asignada | Compras de su ámbito | Fiscal/finanzas | Evidencia autorizada | Logs técnicos redactados |
| Auditoría | R | R scoped | — | — | R scoped | R fiscal | R amplia autorizada | Sin PII por defecto |
| Usuarios, roles, permisos | Administrador de seguridad autorizado | — | — | — | — | — | R si autorizado | No, salvo asignación separada |
| Outbox/reintentos | Operación autorizada | — | — | — | — | Estado funcional si aplica | R evidencia | Consultar/reintentar con motivo, sin modificar payload |
| Secrets de integraciones | Operador técnico restringido | — | — | — | — | No ve secretos | No | Administra referencias/rotación; no se muestran valores |

Esta matriz es objetivo futuro, no estado actual. Los perfiles se deben combinar mediante asignaciones explícitas y las acciones críticas necesitan permisos atomizados.

## 7. Permisos por botón: reglas inequívocas

Permisos propuestos:
- sales:create: crear venta en ámbito permitido, revalidando stock/precio/moneda en servidor.
- sales:cancellation:request: solicitar cancelación con motivo.
- sales:cancellation:approve: aprobar, actor distinto del solicitante y estado válido.
- purchasing:order:create / submit / approve: crear, enviar y aprobar por separado.
- purchasing:receipt:post: contabilizar recepción sobre orden aprobada, cantidades pendientes y bodega autorizada.
- inventory:adjust:execute / inventory:adjust:approve: registrar y aprobar por separado los ajustes por encima de umbral.
- inventory:transfer:dispatch y inventory:transfer:receive: permisos sobre bodega origen/destino.
- costing:read y costing:policy:manage: consultar coste y cambiar política de valoración; no equivalen a inventory:read.
- tax:workpaper:prepare y tax:workpaper:approve: preparar y aprobar conciliación fiscal separadamente.
- integration:delivery:retry: reintentar entrega externa con motivo y auditoría.
- security:roles:manage: cambiar permisos/roles; bloquear escalamiento automático a SUPER_ADMIN.

Los nombres son propuesta. No añadirlos sin matriz completa, migración y pruebas.

## 8. Segregación de funciones y límites

- Crear/solicitar compra no equivale a aprobar compromiso.
- Solicitar cancelación no equivale a aprobarla.
- Conteo físico no equivale a aprobar un ajuste extraordinario.
- Preparar declaración no equivale a aprobarla cuando la política exige dos personas.
- Operador técnico puede reintentar una entrega, no cambiar payload fiscal ni marcar factura como autorizada.
- Bodega registra la devolución física; el tratamiento comercial/fiscal lo valida el rol correspondiente.
- Las compras superiores al límite escalan. Si cambian proveedor, líneas, moneda o total materialmente después de aprobarse, se invalida la aprobación anterior.

Los límites se validan en backend con política configurada, no en React.

## 9. Alcance: empresa, local, establecimiento y bodega

No son sinónimos:
- RUC/entidad legal: parte jurídica de la operación.
- establecimiento SRI: unidad oficial dentro del RUC.
- bodega: ubicación física/lógica del inventario.
- asignación de usuario: sitios/recursos a los que puede acceder.
- contexto fiscal: RUC, establecimiento, punto de emisión y configuración validados por backend fiscal.

La relación entre establecimientos y bodegas es N:M, pero no concede automáticamente a una persona acceso a todas las relaciones. El stock y su propietario se filtran antes de construir respuestas. Si hay varios RUC, se requiere aislamiento por legalEntity/owner; no anunciar multiempresa mientras no esté implementado.

## 10. Respuestas de autorización

- 401: sesión ausente, expirada o inválida.
- 403: autenticado pero sin permiso o scope.
- 404: recurso no visible dentro del ámbito si la política anti-enumeración lo exige.
- 409: transición de estado incompatible o operación ya realizada.
- 422: regla de negocio inválida.
- 429: límite de solicitudes.

Usar formato de error común y requestId. No revelar datos de otro local o empresa en el mensaje.

## 11. Cambios técnicos requeridos

1. Decidir si se necesita multirol; hoy el modelo asigna un rol directo.
2. Añadir asignaciones usuario ↔ bodega/local/entidad con vigencia.
3. Aplicar scope en servicios y queries, no solo a nivel HTTP.
4. Separar permisos de lectura operativa, coste, margen, finanzas y fiscal.
5. Atomizar permisos demasiado amplios.
6. Añadir permisos de integraciones y de preparación/aprobación tributaria.
7. Actualizar DTOs, API de usuarios y administración frontend.
8. Mostrar quién otorgó scope, vigencia, razón y última revisión.
9. Verificar conflictos de SoD y permisos de alto riesgo.
10. Auditar concesión, revocación, cambio de ámbito y denegaciones relevantes.

Agregar nuevas migraciones Flyway; no editar las ya aplicadas. Planificar la conversión de roles existentes y probar compatibilidad.

## 12. Pruebas obligatorias

- Acceso directo a cada endpoint con/sin permiso.
- Usuario limitado a bodega A no lee ni modifica bodega B cambiando el ID.
- Agregados no filtran datos de otra bodega/RUC.
- Actor no puede autoaprobar donde SoD lo prohíbe.
- Cambios de rol/scope revocan acceso efectivo.
- Vendedor no consulta coste/margen manipulando URLs.
- Soporte reintenta Outbox, pero no edita estado comercial/fiscal.
- Auditor no puede mutar documentos.
- Desactivar usuario o scope invalida acceso.
- Relación N:M bodega-establecimiento no amplía el scope del usuario.
- Exportaciones aplican los mismos límites.
- Permisos desconocidos se deniegan por defecto.

## 13. Criterios de adopción

- Matriz validada por dueño, operación, contabilidad y seguridad.
- Ningún botón crítico depende solo de ocultarse en frontend.
- Cada permiso tiene dueño, propósito, endpoints y tests.
- Las personas tienen el mínimo acceso necesario.
- Inventario/finanzas conservan segregación y auditoría.
- Scope se aplica antes de consultar, mutar o agregar.
