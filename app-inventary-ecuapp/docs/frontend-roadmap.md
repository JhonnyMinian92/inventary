> **Estado actualizado — 2026-10-31, rama `implementacion_propuestas`:** la reorganización de código de la Fase 3 está implementada y validada por CI en el SHA `1ec1e4f7a7b3ae9d9e0730a455451e4748a79667`. El workflow ejecutó 22 pruebas y el build de producción con éxito. La comprobación visual E2E en navegador queda pendiente y no se presenta como realizada.

# Plan de implementación del frontend

La construcción del frontend se divide por fases de código fuente. Las pruebas integrales se ejecutarán después de completar todo el alcance de código fuente, según lo solicitado.

## Fase 1 — Fundación y catálogo

**Estado del código fuente: completada en `master`.** La compilación y las pruebas quedan pendientes deliberadamente hasta que se complete el 100 % del frontend.

- Mantener React + Vite como única aplicación web.
- Centralizar las llamadas REST, autorización Bearer, errores e idempotencia en **src/api.js**.
- Completar el inicio de sesión y el marco común de navegación.
- Separar los formularios de productos y categorías en componentes independientes.
- Crear y editar productos y categorías mediante el backend.
- Leer el esquema de propiedades de cada categoría y renderizar los campos compatibles con el tipo de dato.
- Crear, editar y eliminar definiciones de propiedades; validar claves, opciones de listas y longitudes.
- Incluir acciones de activar/desactivar productos y categorías.
- Documentar los contratos y la configuración local.

**Criterio de cierre:** el código fuente de autenticación, catálogo y propiedades dinámicas está incorporado; no se considera validado hasta ejecutar las pruebas en la fase final.

## Fase 2 — Operación de inventario

**Estado del código fuente: completada en `master`.** Las pruebas de compilación y de negocio siguen aplazadas hasta el cierre de todo el frontend.

- Administrar almacenes, proveedores, clientes y unidades de medida; asignar unidades a productos y configurar conversiones.
- Crear órdenes de compra con múltiples productos; enviar a aprobación, cancelar borradores/órdenes pendientes y registrar recepción parcial o completa por línea.
- Registrar devoluciones a proveedores desde recepciones contabilizadas, limitando cantidades a las que aún no se han devuelto.
- Registrar ventas multítem con clientes registrados u ocasionales, consultar disponibilidad y solicitar/aprobar anulaciones.
- Registrar devoluciones de clientes por línea de venta, eligiendo entre reincorporar el artículo al inventario o descartarlo.
- Consultar saldos y movimientos filtrando por almacén y producto, mostrando los campos reales de los DTOs.
- Gestionar traslados multítem con despacho, recepción, cancelación y reversión con motivo.
- Registrar ajustes de stock con dirección, cantidad, motivo obligatorio y costo unitario para entradas.
- Registrar conteos físicos multítem y contabilizar o cancelar los documentos; crear/editar/desactivar reglas de reposición y consultar sugerencias.
- Centralizar las nuevas rutas bajo el cliente REST, usando claves de idempotencia para operaciones transaccionales que modifican inventario.

## Fase 3 — Arquitectura y separación del frontend React

**Estado de implementación en `implementacion_propuestas`: código y CI completados en el SHA `1ec1e4f7a7b3ae9d9e0730a455451e4748a79667`; 22 pruebas y build correctos. La inspección responsive/a11y fue estática; falta prueba visual E2E en navegador.**

- Punto de entrada reducido a composición de providers y workspace; el root `src/App.jsx` queda como fachada de compatibilidad.
- Separación explícita de `app`, `pages`, `widgets`, `features`, `entities` y `shared`, con dependencias dirigidas y una navegación declarativa.
- Controlador del workspace separado de las vistas: navegación/URL en `app/model/useWorkspaceController.js`, consultas por vista en `useWorkspaceData` y operaciones en `useWorkspaceCommands`.
- Autenticación permanece aislada en `AuthProvider`; conserva el flujo de cifrado, persistencia de token, expiración y consulta del perfil.
- Componentes UI neutrales movidos a `shared/ui`; selector de producto y detalles de transacción movidos a `entities`. Fachadas antiguas conservan imports existentes sin duplicar implementación.
- Filtros y paginación de productos, compras, ventas, inventario y reportes se reflejan en query strings; `popstate` restaura el estado de las vistas que lo gestionan.
- Corrección de paginación doble en catálogo: el backend ya entrega una página, por lo que el cliente no vuelve a aplicar el desplazamiento local.
- Corrección de dependencias del filtrado memoizado del catálogo; el resultado ahora responde a búsqueda, categoría, estado y existencias.
- Datos de referencia de reportes se cargan al entrar por URL directa; la capa de datos no retiene registros/contadores/paginación obsoletos ante errores de la consulta correspondiente.
- Estilos conservan el CSS de la aplicación y establecen tokens semánticos y escala de espacio/radio/foco en `shared/styles/tokens.css`.
- Se añaden pruebas unitarias de URL state sin instalar dependencias adicionales.

## Fase 4 — Cierre del código fuente y documentación

**Estado del código fuente: completada en `master`.** La aplicación y sus instrucciones quedan preparadas para iniciar la fase separada de validación.

- Revisada la cobertura de rutas REST consumidas y documentadas en [api-coverage.md](api-coverage.md), incluidos permisos backend relevantes y funcionalidades que no se exponen como pantallas en este frontend.
- Configuración por entorno documentada en `.env.example`; Node 22 está indicado en `.nvmrc` y los requisitos de Node/npm en `package.json`.
- Versiones directas de React, React DOM, Vite, el plugin React y lucide-react fijadas a versiones exactas.
- Guía de ejecución y solución de problemas disponible en [troubleshooting.md](troubleshooting.md), con configuración CORS, variables públicas, errores 401/403/404/409 y manejo de sesión.
- Revisado el cliente REST para distinguir error de red, errores HTTP y pérdida de autorización. La sesión limpia la caché temporal de idempotencia al cambiar el usuario.
- Las operaciones transaccionales marcadas como idempotentes reutilizan temporalmente la misma clave ante un reintento idéntico tras un fallo ambiguo de red o error temporal. La clave no sustituye las validaciones del backend ni persiste tras recargar el navegador.
- Mejorados filtros y métricas paginadas de los informes; cuando cambian los filtros, se descarta el resultado anterior para no mostrar datos que no corresponden a la consulta actual.
- El workflow de compilación de GitHub Actions es manual y requiere confirmar que el código fuente está completo; no ejecuta pruebas automáticamente antes del cierre de esta fase.
- El archivo `package-lock.json` se generará y añadirá en la fase de validación al disponer de acceso al registro de npm. No se afirma que la compilación o la integración estén verificadas porque no se han ejecutado.

**Alcance no representado por pantallas:** administración avanzada de usuarios/roles, auditoría completa, política de costeo, catálogo público/GraphQL, imágenes de productos, variantes, outbox e integraciones de WhatsApp. El mapa de cobertura identifica estas áreas para que no se confundan con módulos ya implementados.


## Fase de pruebas posterior

Se ejecutará solo después de finalizar las cuatro fases de código: compilación de producción, revisión estática, pruebas de componentes y escenarios end-to-end contra el backend y PostgreSQL, incluyendo compras, recepción, venta total, reposición del mismo producto y balance por rango de fechas.
