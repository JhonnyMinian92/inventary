# Auditoría de archivos basura, artefactos y código candidato a muerto

**Fecha de revisión:** 28 de octubre de 2026  
**Snapshot inspeccionado:** master · 23c806d6ed5d06d1ff55e5a72be405167a79150f  
**Estado:** inventario estático de solo lectura. No se eliminó ni se modificó ningún archivo.

## Cómo interpretar este inventario

Este documento separa cuatro casos que no deben tratarse de la misma manera:

- **Candidato de alta confianza a eliminar del control de versiones:** artefacto generado o duplicado que no corresponde a una fuente mantenible.
- **Dato operativo que está mal ubicado:** no debería versionarse junto con el código, pero no se debe eliminar sin identificar su propietario, uso y copia de seguridad.
- **Documentación obsoleta o duplicada:** puede archivarse, consolidarse o corregirse, pero no se borra automáticamente.
- **Código muerto confirmado:** archivo fuente cuya falta de uso se ha comprobado; no se debe declarar código muerto solo por su nombre, por no haberlo visto en pantalla o por no encontrar una referencia literal.

La revisión encontró artefactos claros que parecen venir de una compilación y datos de imágenes que parecen pertenecer al almacenamiento operativo. **No encontré suficiente evidencia estática para declarar que un componente fuente React o una clase Java funcional completa está definitivamente muerta y se puede borrar sin más verificación.** El informe lo señala explícitamente, en lugar de inventar una lista de eliminaciones.

## 1. Candidatos de alta confianza: salida de build añadida al repositorio

Se encontraron 19 archivos bajo la ruta literal:

**microservices-inventary-ecuapp/${project.build.directory}/**

La ruta contiene copias de recursos que normalmente se generan dentro de la salida de Maven. No es la ubicación fuente de esos recursos; las fuentes mantenibles están bajo src/main/resources y src/test/resources. La carpeta usa además la expresión literal del directorio de build como parte de su nombre.

### 1.1 Configuraciones duplicadas

Candidatos:

- microservices-inventary-ecuapp/${project.build.directory}/classes/application-dev.yaml
- microservices-inventary-ecuapp/${project.build.directory}/classes/application-prod.yaml
- microservices-inventary-ecuapp/${project.build.directory}/classes/application.yaml

**Motivo:** son copias de recursos de configuración de la aplicación dentro de una ruta de salida generada. No deben usarse como fuente canónica de configuración.

### 1.2 Copias de migraciones Flyway

Candidatos:

- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V1__create_core_schema.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V2__partners_and_procurement.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V3__audit_transfers_returns_and_media.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V4__purchase_returns_and_reorder_rules.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V5__whatsapp_carts_quotes_and_orders.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V6__sale_cancellation_movements.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V7__case_insensitive_business_keys.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V8__link_sales_to_customers.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V9__complete_inventory_domain_schema.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V10__security_rbac_and_token_revocation.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V11__inventory_counts_and_ledger_hardening.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V12__approved_sale_cancellations.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V13__transfer_compensation_and_consistency.sql
- microservices-inventary-ecuapp/${project.build.directory}/classes/db/migration/V14__costing_roles_and_operational_completion.sql

**Motivo:** son copias de migraciones; las fuentes originales se mantienen en src/main/resources/db/migration. No deben editarse dos versiones de una misma migración.

### 1.3 Otros recursos generados

- microservices-inventary-ecuapp/${project.build.directory}/classes/graphql/schema.graphqls
- microservices-inventary-ecuapp/${project.build.directory}/test-classes/application-test.yaml

**Motivo:** copias de un esquema GraphQL y de configuración de tests en directorios que corresponden a recursos procesados. Las versiones canónicas están en las carpetas fuente del proyecto.

**Recomendación:** eliminar estos 19 artefactos del control de versiones en un commit independiente y verificar que .gitignore excluya las salidas de build. Este documento solo los identifica; no ha borrado nada.

## 2. Datos operativos que no deberían confundirse con código

Se encontraron ocho PNG bajo microservices-inventary-ecuapp/data/product-images/:

- 07e0ab85-c954-4406-bedf-fde1e80f9ca3.png
- 21eec400-3aa3-403b-b201-1cb7b1cd5d5d.png
- 5e8ac0f0-0d63-40a1-983d-0f11b464d91b.png
- 73790a66-f83d-42c4-a29e-1b2c8f6a38b0.png
- 9f021611-dc04-485a-8e5b-0dd22cd4c9ad.png
- a2ceb330-4a77-4757-b9f3-309c4d9d32a9.png
- c08a5454-3755-4e19-95ec-42e865713a18.png
- d1272f17-6385-4c43-8a53-d63cba178a1e.png

La enumeración refleja el árbol del SHA revisado; hay **ocho** archivos PNG, no siete.

El backend implementa almacenamiento de imágenes de producto con ruta configurable y Docker Compose monta un volumen para mantenerlas. Estas imágenes pueden ser cargas de productos reales, datos de ejemplo o restos de desarrollo. Por sus nombres UUID y ubicación no se puede determinar con seguridad cuál de esas posibilidades se aplica.

**No eliminarlas de forma automática.** Antes de retirarlas del repositorio se debe comprobar:

1. Si se muestran en los productos de la base local, demo o producción.
2. Si existen URLs o referencias que apuntan a esos archivos.
3. Si pertenecen a usuarios/clientes o son solo datos de prueba.
4. Si hay copia de seguridad y un destino de almacenamiento persistente.
5. Si el repositorio debe contener ejemplos anónimos en una carpeta separada.

Si son cargas de runtime, la solución es mover la persistencia fuera del repositorio, establecer una política de almacenamiento y backup y dejar de versionar imágenes cambiantes.

## 3. Documentación con referencias a archivos inexistentes

En app-inventary-ecuapp/README.md se mencionan como estructura actual:

- src/features/dashboard/Overview.jsx
- src/features/catalog/CatalogTables.jsx

Ninguno de esos archivos existe en el árbol de la revisión. El dashboard real está en src/features/dashboard/Dashboard.jsx y las pantallas del catálogo se concentran en ProductsPage.jsx, junto con sus componentes dependientes.

Esto **no es código muerto**: son referencias documentales obsoletas. Deben actualizarse para que un desarrollador o agente no pierda tiempo intentando localizar componentes inexistentes.

También se encontró una afirmación documental antigua indicando que no existe package-lock.json y que el frontend no declara tests. En el SHA inspeccionado sí están app-inventary-ecuapp/package-lock.json y el script npm test. Se recomienda corregir esa documentación.

## 4. Documentación histórica, duplicada o potencialmente confusa

Existen informes y documentos históricos en la raíz y en el módulo backend, entre ellos:

- AUDITORIA_TECNICA_PENDIENTES.md en la raíz.
- microservices-inventary-ecuapp/AUDITORIA_TECNICA_PENDIENTES.md.
- microservices-inventary-ecuapp/PENDIENTES.md.
- microservices-inventary-ecuapp/docs/01-analisis-competitivo.md hasta docs/23-runbook-operacion.md.
- microservices-inventary-ecuapp/docs/24-estado-actual-repositorio.md hasta docs/29-catalogo-apis-propuesta-historica.md.
- microservices-inventary-ecuapp/docs/RESULTADO_AUDITORIA_EJECUCION.md.
- app-inventary-ecuapp/docs/context.md y microservices-inventary-ecuapp/docs/context.md.

Parte de estos documentos identifica explícitamente planes y snapshots históricos; conservarlos puede ser útil para la trazabilidad. El riesgo viene de que algunos contienen SHA, estados de CI, versiones Flyway y pendientes de un estado previo al código actual.

**No deben borrarse todos por el simple hecho de ser históricos.** Recomendación: designar un único documento de estado vivo, marcar las auditorías antiguas como históricas y evitar que los README actuales enlacen una documentación incompatible con el HEAD.

## 5. Código fuente muerto: qué se confirmó y qué no

### 5.1 No declarar muertos los componentes actualmente utilizados

La búsqueda de referencias confirma uso desde la aplicación para componentes como:

- components/charts/AreaChart.jsx, BarList.jsx y Sparkline.jsx desde Dashboard.jsx.
- components/ProductPicker.jsx desde los formularios de compras, ventas e inventario.
- components/StepProgress.jsx desde los formularios guiados.
- features/operations/UnitConversions.jsx desde OperationsCatalogs.jsx.
- components/TransactionDetails.jsx desde App.jsx.
- hooks/useAccessibleDialog.js desde componentes interactivos.

Estos no son candidatos de eliminación según la evidencia actual.

### 5.2 Los dos archivos inexistentes del README no son eliminaciones pendientes

Overview.jsx y CatalogTables.jsx no aparecen en el árbol. No hay archivos que borrar con esos nombres; hay que reparar sus referencias.

### 5.3 Qué falta para afirmar que una clase Java está muerta

Una búsqueda de nombre no basta para eliminar código Java: controladores Spring, beans, entidades JPA, consumidores de eventos y migraciones pueden registrarse por escaneo, reflexión o convenciones. Se necesita combinar:

- búsqueda de referencias e imports en toda la fuente;
- análisis de dependencias y beans registrados;
- revisión de controladores, rutas OpenAPI y contratos;
- tests de regresión y cobertura;
- comprobación de configuraciones/profiles;
- comparación con consumidores externos documentados.

Por eso no se declaran clases Java eliminables sin esa verificación. No se encontró evidencia suficiente para afirmar que un módulo de negocio entero no se usa.

## 6. Orden recomendado para limpiar, cuando se autorice

1. **Primero:** retirar del control de versiones los 19 artefactos de salida de build tras comparar contenido con sus fuentes originales.
2. **Segundo:** revisar y migrar las ocho imágenes UUID a un almacenamiento apropiado solo tras comprobar su uso y preservar una copia.
3. **Tercero:** corregir referencias de README a archivos inexistentes, scripts y lockfile.
4. **Cuarto:** archivar la documentación histórica con un índice claro y un documento vivo.
5. **Quinto:** realizar una revisión de dead code con herramientas estáticas y evidencia de ejecución antes de borrar clases o componentes.
6. **Por último:** comprobar que Docker, Maven, tests, migraciones Flyway y build de frontend siguen funcionando después de una limpieza autorizada.

## Límite de esta auditoría

La revisión no ejecutó una búsqueda dinámica de clases cargadas, no desplegó el sistema y no examinó bases locales del usuario. Las categorías «candidato» y «requiere comprobación» son intencionales. No se ha borrado ningún archivo ni código.


## Adenda de ejecución — limpieza Fase 1 · 2026-10-30

Los 19 artefactos generados identificados en la ruta literal del directorio de build Maven fueron retirados del control de versiones en la rama implementacion_propuestas. Se reforzó .gitignore para excluir las salidas de build y secretos. La limpieza se limitó a esos artefactos duplicados; no se eliminaron archivos operativos de imágenes ni se declararon componentes React o clases Java como código muerto sin pruebas suficientes.

La validación asociada a la Fase 1 y el plan de todas las fases restantes están documentados en [PLAN_EJECUCION_PROPUESTAS_INVENTARY.md](PLAN_EJECUCION_PROPUESTAS_INVENTARY.md) y [MATRIZ_TRAZABILIDAD_PROPUESTAS_INVENTARY.md](MATRIZ_TRAZABILIDAD_PROPUESTAS_INVENTARY.md).
