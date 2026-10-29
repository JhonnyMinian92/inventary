# Propuesta de mejora integral de Suscripciones — frontend y backend

**Producto:** Inventary · EcuApp  
**Fecha de análisis:** 28 de octubre de 2026  
**Pantalla objetivo:** /inventary/suscripciones  
**Fuentes internas revisadas:** React SubscriptionsPage.jsx, App.jsx, api.js; SubscriptionController, SubscriptionDtos, SubscriptionService, Subscription, SubscriptionInstallment, SubscriptionSchedule, BillingFrequency, ServiceBilling, SubscriptionBillingJob y SubscriptionApiTest.  
**Alcance:** propuesta funcional, técnica y UX; no implica que las capacidades sugeridas estén implementadas.

> **Límite de inspección:** no pude abrir el servidor local de http://localhost:3000 desde este entorno. La auditoría de la pantalla se hizo contra el código versionado que la implementa y los contratos del backend, no mediante una sesión interactiva en la máquina local. Antes de implementar, validar el aspecto responsive y el comportamiento final con la aplicación ejecutándose.

## 1. Conclusión ejecutiva

La pantalla actual no es un gestor universal de suscripciones. Es una pantalla para **crear y administrar contratos de servicios con duración fija, calendario de cargos y generación automática de facturas de venta**. Para ese caso de uso tiene una base útil: previsualización del calendario, detalle de cobros, cancelación con simulación del efecto económico, facturación programada e idempotencia para crear la suscripción.

Sin embargo, la etiqueta «Suscripciones» cubre modelos comerciales muy diferentes. Una suscripción de streaming concede acceso a contenidos; una de videojuegos puede incluir catálogo, beneficios y créditos consumibles; una de eventos deportivos puede ser un pase de temporada; una de salud puede incluir visitas o citas con condiciones y exclusiones; una de software puede cobrar por usuario o consumo; un servicio de mantenimiento puede incluir cierto número de visitas; una caja física requiere reposición y logística. No es correcto resolver esos casos añadiendo más opciones al mismo formulario sin separar el contrato, el cobro y lo que el cliente tiene derecho a recibir.

La recomendación es mantener la pantalla sencilla para el caso ya soportado y evolucionarla hacia una gestión modular basada en cuatro conceptos independientes:

1. **Plan comercial:** qué se ofrece, a quién, con qué beneficios y condiciones.
2. **Contrato o suscripción:** quién contrata, beneficiarios, vigencia, renovación y estado.
3. **Facturación y pagos:** qué se factura, cuándo, si se ha pagado, qué falló y qué se reembolsó.
4. **Derechos y prestación del servicio:** qué acceso, citas, visitas, cupos, licencias o entregas puede consumir el suscriptor.

La primera prioridad no es incorporar inteligencia artificial. Es corregir las ambigüedades de facturación, cubrir búsquedas y excepciones operativas, y representar de forma fiable el ciclo de vida. Las capacidades especializadas se habilitan después según el dominio real del negocio.

## 2. Estado actual: para qué sirve exactamente la pantalla

### 2.1 Lista principal

La implementación actual de SubscriptionsPage.jsx:

- Consulta las opciones del backend mediante GET /api/v1/subscriptions/options.
- Lista las suscripciones por páginas de 20 registros y orden de creación descendente.
- Permite filtrar por estado: Activas, Canceladas, Finalizadas y Todas.
- Muestra número de suscripción, cliente, servicio, frecuencia/duración, próximo cobro, importe facturado frente al total y estado.
- Marca cuando hay un error de facturación en el registro.
- Abre un panel lateral con el detalle al seleccionar una fila.
- Muestra el botón «Nueva suscripción» a quienes tengan sales:manage.

La lista no contiene búsqueda textual por número/cliente/servicio, filtro por local, tipo de plan, fecha de próximo cobro o error de facturación, ni un resumen superior del calendario próximo. Para equipos que gestionan decenas o cientos de contratos, eso obliga a navegar página por página y dificulta detectar trabajo pendiente.

### 2.2 Crear una suscripción

El formulario NewSubscriptionModal filtra productos activos cuyo tipo es SERVICE y cuya modalidad es SUBSCRIPTION. Solicita:

- Servicio y cliente.
- «Local que factura», seleccionado desde almacenes activos.
- Fecha de inicio.
- Frecuencia definida por backend.
- Duración de 1, 3, 6 o 12 meses.
- Precio mensual.
- Opcionalmente, facturar por adelantado los cobros programados y aplicar descuento, si el permiso lo permite.

La pantalla llama a POST /api/v1/subscriptions/preview para calcular el calendario y a POST /api/v1/subscriptions para crear el contrato. El backend usa el servicio elegido como origen por defecto de precio, frecuencia y duración, aunque permite que el operador reemplace algunos valores en la suscripción.

El estado vacío indica que la pantalla contempla servicios facturados cada 15 días, cada mes o al finalizar el periodo.

### 2.3 Detalle y operaciones

El panel lateral presenta estado, frecuencia, duración, precio mensual, total del plan, facturado, vigencia, próximo cobro, local asociado, error de facturación y calendario de cobros. Permite cancelar o adelantar los cobros pendientes a usuarios autorizados.

La cancelación primero pide una vista previa que indica qué importe se facturará ahora y cuántos cargos futuros se anularán; requiere un motivo. Esta es una buena práctica que debe conservarse.

### 2.4 Qué realiza hoy el backend

Los puntos más relevantes de SubscriptionService, SubscriptionSchedule y SubscriptionBillingJob son:

- Las frecuencias disponibles son BIWEEKLY, MONTHLY y AT_END.
- Las duraciones permitidas son 1, 3, 6 y 12 meses.
- El calendario se crea al generar la suscripción y se persiste por cuotas.
- La tarea programada procesa los cargos cuya fecha llegó y llama al flujo de ventas para generar la factura como una venta del canal SUBSCRIPTION.
- La idempotencia de la venta y de la creación ayuda a evitar facturas duplicadas en reintentos.
- Si falla la facturación, se registra lastBillingError para poder mostrar el problema y reintentar en ejecuciones posteriores.
- Al cancelar, el backend factura, cuando corresponda, el periodo vigente o atrasado y anula los periodos futuros no facturados.
- El adelanto crea una venta/factura por el conjunto de cobros pendientes y asigna esa venta a las cuotas correspondientes.

**Limitación esencial:** el módulo actual programa y registra facturas de venta. No es por sí solo un procesador de pagos automáticos, una pasarela, un sistema de débito recurrente o un gestor de acceso/beneficios. La confirmación de una factura no debe interpretarse como prueba de que el dinero fue cobrado ni como prueba de que el servicio fue activado.

## 3. Hallazgos y prioridades

### P0 — Corregir el significado de «pago por adelantado»

En la pantalla se utilizan textos como «Cobrar todo por adelantado» y «Pagada por adelantado», pero la ruta de backend de prepay genera una factura consolidada por las cuotas pendientes. No se observa en el modelo de suscripción un registro propio de intento de pago, transacción de pasarela, estado de conciliación ni reembolso.

**Riesgo:** operador y cliente pueden interpretar que se recibió dinero cuando únicamente se generó una factura de venta.

**Propuesta:**
- Hasta que exista una integración de pagos confirmados, cambiar el texto a «Facturar las cuotas pendientes en una sola factura» o «Facturación anticipada».
- Distinguir de manera explícita Facturado, Pagado, Pendiente de pago, Pago fallido y Reembolsado. Mostrar estos estados solo cuando existan datos confiables de pago.
- Mantener la propiedad prepaid como método de facturación anticipada si ese es su significado actual, y no utilizarla como equivalente de paid.
- Si el negocio quiere recaudo automático, agregar el dominio de pagos con referencia externa, estado, moneda, importe, fecha, reintentos e idempotencia. Nunca inferir un pago por el hecho de que se haya creado una venta.

### P1 — El nombre «suscripción» es más amplio que el modelo

El modelo actual exige duración fija de 1, 3, 6 o 12 meses. No hay renovación automática continua al llegar al final del contrato; al completar el calendario, el estado pasa a COMPLETED. La pantalla representa ACTIVE, CANCELLED y COMPLETED, pero no incluye prueba gratuita, programación de activación, pausa, pago vencido, periodo de gracia, renovación pendiente o cancelación efectiva al terminar el ciclo.

**Propuesta:** separar el estado del contrato, estado del cobro y estado de acceso/beneficio. No agregar decenas de estados a ciegas; definir la máquina de estados según el producto de suscripción que se habilite.

### P1 — El calendario necesita políticas de periodo explícitas

BillingFrequency ofrece «Cada 15 días», «Cada 30 días» y «Al finalizar». En el código, BIWEEKLY genera dos cargos por mes utilizando startDate y un punto intermedio de 15 días; no es una implementación completa de una política configurable de día fijo del mes. Los meses cortos y las fechas como el 29, 30 o 31 pueden producir periodos de duración desigual. El modo AT_END genera un solo cargo en la fecha de fin del calendario, mientras la interfaz presenta la fecha inclusiva de cobertura.

**Propuesta:** decidir si cada oferta necesita:
- intervalo real de N días;
- frecuencia de calendario (por ejemplo, día 1 y 15);
- mensual/anual anclado a fecha;
- cobro al inicio, al final o por adelantado;
- reglas de prorrateo, festivos, vencimientos y hora/zona horaria.

El nombre, la previsualización, el cálculo y las fechas contables deben corresponder exactamente a esa política. No renombrar BIWEEKLY ni alterar periodos sin una migración y pruebas de compatibilidad.

### P1 — La interfaz oculta errores de configuración

useSubscriptionOptions reemplaza un error de la API por frecuencias y duraciones vacías, y con canDiscount false, sin mostrar que la carga de opciones falló. Si falla subscriptionPreview, el formulario simplemente borra la previsualización. Así el usuario puede quedarse con controles vacíos o sin explicación.

La validación de submit comprueba servicio, cliente, almacén y precio, pero no verifica explícitamente que se haya elegido una frecuencia, una duración y una previsualización válida antes de mandar el comando. La respuesta del servidor debe seguir siendo autoritativa, pero el frontend puede evitar errores previsibles.

**Propuesta:** mostrar estados Cargando opciones, No se pudieron cargar las opciones y Reintentar; no simular un fallo como si fuera una lista vacía válida. No permitir confirmar una combinación incompleta o cuyo calendario no se pudo calcular. En el servidor conservar la validación definitiva.

### P1 — Falta búsqueda y una bandeja de trabajo

La lista solo filtra por status y customerId está disponible en el backend, pero la pantalla no ofrece búsqueda por cliente o servicio. La API de lista actual no declara filtro por producto, local o rango de fechas. El error de facturación aparece como etiqueta pequeña, en lugar de una bandeja operativa.

**Propuesta de primera iteración:**
- Buscar por número de suscripción, cliente, servicio/producto o identificador autorizado.
- Filtrar por estado de contrato, servicio, frecuencia, local que factura, fecha del siguiente cargo y existencia de error de facturación.
- Vista «Próximos cargos» y vista «Requieren atención», usando estados reales de facturación; no mostrar pagos vencidos hasta implementar el estado de pago.
- Ordenar por próximo evento, creación y vigencia.
- Resumir cuántos contratos están activos, cuántos cargos toca facturar en el periodo, cuántos fallaron y cuánto está facturado. El resumen debe diferenciar facturación de dinero cobrado.

### P1 — «Local que factura» y lugar donde se presta el servicio son conceptos distintos

El modelo actual exige warehouseId y lo utiliza para crear la venta de la suscripción. Eso representa la ubicación que da contexto contable/operativo a esa venta, pero no necesariamente dónde se presta el servicio al cliente ni dónde recibe beneficios.

**Propuesta:** separar:
- entidad o local emisor de la factura;
- punto de prestación, clínica, gimnasio, evento o sede cuando aplique;
- canal de contratación;
- ubicación logística de entrega si el plan incluye mercancía física.

En la pantalla básica puede conservarse el local facturador si el negocio lo necesita, pero no presentarlo como si fuera automáticamente el lugar de uso o prestación del servicio.

### P2 — El contrato no define qué recibe el suscriptor

Subscription guarda producto, cliente, almacén, precio, calendario, estado y datos de cancelación. No contiene un dominio general de beneficios, acceso a contenido, cuotas de uso, número de miembros/dependientes, citas, entradas, dispositivos o derechos por nivel.

**Propuesta:** los derechos/beneficios se modelan en una capa separada. La suscripción habilita derechos según el plan y estado; no debe codificar reglas de acceso en React ni asumir que cualquier compra de un producto SERVICE activa automáticamente una prestación digital o médica.

### P2 — El modelo no contempla renovación, consumo variable o eventos

Actualmente no hay un contrato general para auto-renovación, renovación manual, prueba gratuita, consumo medido, número de asientos/beneficiarios, paquetes de créditos, entradas por evento o reposición física periódica. Se debe decidir qué dominios se quieren habilitar y no asumir que todos son un simple cobro mensual.

## 4. Tipos de suscripción que Inventary podría llegar a gestionar

La experiencia debe partir del modelo comercial y de lo que se entrega, no solo de la frecuencia de facturación. Esta taxonomía es una guía de producto; no implica que todas las variantes deban implementarse en el mismo lanzamiento.

| Tipo | Qué compra la persona | Capacidades que requiere | ¿Cabe en el modelo actual? |
|---|---|---|---|
| Entretenimiento digital / streaming | Acceso a biblioteca, canales o contenidos por nivel | Plan/tier, catálogo de beneficios, derechos de acceso, dispositivos, restricciones territoriales y renovación | Solo el calendario de facturación; no el acceso a contenidos |
| Video, música o comunidad de pago | Contenido exclusivo, publicaciones, insignias o comunidad | Beneficios recurrentes, nivel, fechas de acceso, reglas de publicación y baja | No hay gestión de beneficios/acceso |
| Videojuegos y gaming | Catálogo de juegos, multijugador, ventajas, pases de temporada y a veces consumibles | Nivel de plan, inventario de beneficios, códigos/reclamaciones, dispositivo/plataforma y catálogo variable | Solo tarifa fija de servicio; no derechos ni consumibles |
| Eventos y deportes | Abono de temporada, membresía de club, acceso a series de eventos o contenido deportivo | Temporada, eventos elegibles, fechas, aforo/asiento, titular, transferencias, QR/entradas y restricciones | No existe entidad de evento, cupo o ticketing en este módulo |
| Salud y medicina | Membresía de clínica, teleconsulta, visitas incluidas o plan de atención definido | Beneficiarios/dependientes, prestaciones incluidas/excluidas, citas, prestador, límites, renovación y privacidad reforzada | Solo facturación de un servicio; no prestación clínica ni elegibilidad |
| Seguro o plan de salud asegurador | Cobertura contractual y obligaciones reguladas | Producto asegurador, póliza, riesgos, beneficiarios y reglas regulatorias propias | No se debe representar como un servicio mensual genérico |
| Gimnasio, educación o clases | Acceso, clases por semana, sesiones incluidas o cupos | Sede, agenda, aforo, reservas, asistencias, créditos y reglas de congelamiento | Solo factura fija; sin reservas/beneficios |
| SaaS y servicios digitales B2B | Funciones según plan, usuarios/asientos, espacios de trabajo o volumen | Tiers, licencias, seats, límites de uso, métricas, upgrades/downgrades y prorrateo | No hay contadores de uso ni provisión de acceso |
| Mantenimiento/soporte profesional | Servicio continuado, SLA, visitas o cobertura | Contrato, alcance incluido, horarios, SLA, tickets, piezas/visitas consumidas y renovación | Puede facturar un servicio, pero no controla compromisos de prestación |
| Consumibles o cajas recurrentes | Entregas de artículos en cadencia fija | Líneas de pedido, cantidades, dirección, stock, pausa/skip, sustituciones, envío y entrega | El producto debe ser SERVICE hoy; no sustituye compras, inventario y fulfillment de bienes |
| Membresías de club/fidelidad | Descuentos, privilegios, puntos o acceso a actividades | Nivel, beneficios versionados, acumulación/uso, vigencia, límites y exclusiones | No existe el libro general de beneficios |
| Créditos/paquetes prepagados | Un saldo de sesiones, horas, consultas, viajes o unidades | Ledger de créditos, consumo, reversos, caducidad y conciliación | No existe saldo de consumo; el adelanto actual genera una factura consolidada |

### La distinción más importante

Hay al menos cuatro ejes diferentes:
- **Modelo de acceso:** contenido, servicio profesional, membresía, cupo/evento, licencia, bienes.
- **Modelo de precio:** tarifa fija, por nivel, por usuario, por uso, por paquete/créditos, híbrido.
- **Modelo de vigencia:** plazo fijo, renovación automática, renovación manual, hasta cancelar, temporada/evento, prueba gratuita.
- **Modelo de facturación y recaudo:** facturación periódica, prepago, facturación por uso, pago posterior, pago automático mediante proveedor o transferencia confirmada.

No convertir todos esos ejes en un único enum grande. Una combinación explícita y validada evita que un cambio de precio cambie el derecho de acceso, o que «prepagado» signifique al mismo tiempo «facturado», «pagado» y «servicio activo».

## 5. Investigación de mercado y estudios relevantes

### 5.1 Los modelos cambian según el sector

El estudio de Kowalkowski y Ulaga en Industrial Marketing Management (2024) analiza ofertas de suscripción B2B mediante entrevistas a 27 ejecutivos y las clasifica por el foco del servicio y la integración de recursos. Su conclusión práctica es que un modelo de suscripción afecta a oferta, precios, ingresos recurrentes y compromiso con el cliente; no es solo elegir un intervalo de factura.

Referencia: https://doi.org/10.1016/j.indmarman.2024.01.014

### 5.2 Entretenimiento y gaming mezclan suscripciones con otros productos

El informe State of Subscription Apps 2026 de RevenueCat observa combinaciones de suscripción, compras únicas y consumibles en distintas categorías; en gaming identifica una mezcla de monetización más heterogénea que otras categorías. Sus datos describen aplicaciones participantes de esa plataforma y no deben tratarse como una ley para todo negocio o como una previsión de Inventary.

Referencia: https://www.revenuecat.com/state-of-subscription-apps

Xbox Game Pass ilustra beneficios que dependen de la categoría y nivel de plan: catálogo de juegos, juego en la nube, multijugador, recompensas y ventajas que se pueden perder al cambiar o cancelar el plan. F1 TV distingue niveles de contenido y acceso en vivo según sus planificaciones, y YouTube ofrece niveles de membresía con beneficios acumulativos.

Fuentes de referencia:
- https://www.xbox.com/es-ES/xbox-game-pass
- https://f1help.formula1.com/article/F1-TV-subscription-comparison
- https://support.google.com/youtube/answer/7544492?hl=es

**Implicación de diseño:** un plan no es solo precio y fecha de cobro. Hace falta una definición versionada de beneficios y una regla clara que determine cuándo empieza, cuándo termina y qué cambia al cancelar o cambiar de nivel.

### 5.3 Prueba gratuita y hábitos requieren medir la experiencia, no solo convertir

Un estudio empírico publicado en el Journal of the Academy of Marketing Science en septiembre de 2026 examina el paso de freemium a premium en cuatro estudios de campo. Sus resultados describen que los beneficios esperados y experimentados pueden apoyar la conversión/retención, pero también que la costumbre de usar la versión gratuita puede mantener a algunas personas allí; no hay una regla simple que convierta cada prueba gratuita en suscripción.

Referencia: https://link.springer.com/article/10.1007/s11747-026-01205-w

**Implicación:** si se habilitan pruebas gratuitas, registrar comienzo/fin, consentimiento del futuro cobro, beneficio probado, conversión y cancelación. No esconder el primer cobro ni tratar un inicio de prueba como ingreso cobrado.

### 5.4 Retención, baja y supervivencia del contrato

La literatura académica sobre churn de servicios digitales encuentra que el momento de cancelación importa. El análisis de supervivencia permite estudiar no solo si una suscripción termina, sino cuándo, y tratar correctamente a los contratos que siguen activos al cerrar la ventana de observación. Es más apropiado para etapas avanzadas que entrenar un clasificador sin historial temporal suficiente.

Referencias:
- https://doi.org/10.1108/MD-04-2024-0961
- https://doi.org/10.1016/j.ijresmar.2025.03.005
- https://ieeexplore.ieee.org/abstract/document/11557777

**Implicación:** comenzar con cohortes por mes de alta, renovaciones, causas de cancelación y fallos de facturación. Incorporar Kaplan–Meier/Cox o modelos de churn únicamente cuando los eventos, el volumen y las etiquetas sean fiables. No usar modelos predictivos para dificultar la baja.

### 5.5 Salud requiere una frontera de datos y responsabilidad

Una membresía clínica puede incluir consultas, telemedicina, atención preventiva y servicios concretos, con exclusiones importantes. El ejemplo de Direct Primary Care Associates muestra explícitamente que una membresía de atención primaria no equivale a un seguro médico. Es un ejemplo estadounidense de diseño comercial, no una plantilla legal transferible a Ecuador.

Referencia ilustrativa: https://directprimarycareassociates.com/for-individuals/

En Ecuador, la Superintendencia de Protección de Datos Personales identifica los datos de salud como categoría que requiere protección reforzada bajo la LOPDP y publica orientación de privacidad desde el diseño. La futura función médica debe limitar qué datos guarda el módulo comercial: el registro de suscripción puede guardar estado de elegibilidad y referencias autorizadas, no historias clínicas, diagnósticos ni notas asistenciales como si fueran campos comerciales.

Fuentes:
- https://www.spdp.gob.ec/docs/guias/2025-2/guia-de-proteccion-de-datos-personales-desde-el-diseno-y-por-defecto/
- https://www.spdp.gob.ec/docs/consultas-absueltas/2026-2/oficio-n-spdp-ird-2026-0221-o/

### 5.6 Cancelación sencilla y transparencia del cobro

La FTC de Estados Unidos mantiene material y reglas relativas a prácticas de renovación automática, consentimiento informado y mecanismos de cancelación. Es una referencia de principios de UX y transparencia, pero sus obligaciones legales no deben asumirse como aplicables a Ecuador.

Referencia: https://www.ftc.gov/legal-library/browse/rules/negative-option-rule

Para Inventary, con independencia de la jurisdicción, la pantalla debe mostrar antes de confirmar el importe, la frecuencia, vigencia, renovación real o ausencia de renovación, condiciones de cancelación, derechos que se pierden y qué ocurre con el siguiente cargo.

## 6. Diseño UX recomendado para la pantalla

### 6.1 Cabecera y resumen

La pantalla debería empezar con el propósito, no con una tabla sin contexto:
- Título «Suscripciones y contratos».
- Resumen de contratos activos, próximos a vencer, con facturación fallida y cargos previstos en el periodo.
- Los indicadores monetarios deben separar total programado, total facturado y total efectivamente cobrado. Hasta tener pagos, no mostrar «recaudado» como si fuera derivable de ventas.
- Acción principal «Nueva suscripción».
- Acciones secundarias para exportar y revisar incidencias, solo con permisos adecuados.

### 6.2 Lista de gestión

Búsqueda persistente por número, cliente, servicio o referencia. Filtros por estado, plan/categoría, periodo de alta, fecha de próximo cargo, local emisor y error de facturación. Mostrar la última actividad y el próximo evento significativo.

Columnas recomendadas para el modelo actual:
- Número y estado del contrato.
- Cliente.
- Servicio/plan.
- Frecuencia y duración.
- Próxima fecha/importes.
- Total contratado, facturado y pendiente de facturación.
- Error o incidencia.
- Acciones.

Con más de un tipo de suscripción, mostrar atributos específicos con un resumen configurable, no veinte columnas universales. En pantallas estrechas, usar tarjetas con una jerarquía consistente y detalle bajo demanda; no exigir que una persona desplace horizontalmente una tabla muy ancha para descubrir el estado.

### 6.3 Crear suscripción: stepper de 4 pasos

Para evitar fatiga visual, usar un flujo guiado porque los datos dependen unos de otros.

**Paso 1 — Qué se contrata**
- Seleccionar categoría de suscripción y plan.
- Ver un resumen de lo incluido, límites, exclusiones, duración, política de renovación y versión de las condiciones.
- Si se mantiene el modelo actual, el formulario solo ofrece servicios activos con facturación SUBSCRIPTION y comunica la limitación real.

**Paso 2 — Titular y beneficiarios**
- Separar la persona que paga del titular/usuario del beneficio cuando no sean la misma.
- Permitir dependientes o asientos solo en los tipos que lo requieran.
- Recopilar únicamente datos necesarios. En salud, no pedir información clínica en el módulo de facturación.

**Paso 3 — Vigencia, uso y facturación**
- Inicio, fin/renovación, frecuencia y regla de calendario.
- Precio de referencia, precio por periodo, descuentos autorizados, impuestos/cargos devueltos por backend.
- Si hay consumo, cuota/créditos o número de miembros, mostrar sus límites y cómo se calculan.
- Separar facturación anticipada de cobro confirmado y de activación del servicio.
- Para un contrato de plazo fijo, indicar claramente que no se renueva automáticamente si esa es la regla vigente.

**Paso 4 — Revisar y confirmar**
- Resumen de condiciones, importes, fechas y modalidad de cobro.
- Calendario completo descargable/expandible, con el primer evento destacado.
- Política de cancelación y efecto sobre el periodo actual y beneficios futuros.
- Confirmación explícita para cargos recurrentes cuando aplique.
- Mensaje de éxito que diga exactamente qué quedó creado: contrato, factura y/o pago, sin mezclarlos.

### 6.4 Panel de detalle con pestañas

- **Resumen:** titular, beneficiarios, plan, estado, vigencia, entidad/local y siguiente acción.
- **Calendario y facturas:** cuota, periodo cubierto, fecha, venta/factura enlazada e importe.
- **Pagos:** solo cuando el backend tenga un módulo de pago; transacciones, fallos, referencias, devolución y conciliación.
- **Beneficios/acceso:** estado de los derechos y consumo si el tipo de suscripción lo implementa.
- **Actividad:** creador, cambios de plan, aplicación de descuentos, cancelación, reintentos y auditoría.

Las acciones sensibles deben explicar su efecto antes de confirmar. La cancelación actual ya tiene una buena vista previa de cobros; extenderla para explicar qué acceso termina, cuándo termina y qué beneficios se pierden solo cuando el backend pueda calcularlo.

### 6.5 Estados de carga y errores

- Carga: skeleton y texto accesible.
- Error de opciones de backend: explicación y botón de reintentar, no selectores vacíos.
- Error de preview: impedir confirmación hasta recuperar el calendario.
- Error de facturación: incidencia visible en la bandeja, próximo reintento/acción y mensaje entendible para el usuario; los detalles internos deben estar en logs protegidos.
- Resultado de comando incierto por red: consultar por recurso/idempotencia antes de volver a enviar.
- Sin permisos: explicar que no dispone de permiso, sin esconder la causa ni filtrar datos.
- Sin registros: invitar a crear el primer contrato o cambiar filtros.

## 7. Arquitectura de backend recomendada

La mejor opción inicial es evolucionar el monolito modular de Spring Boot, no crear un microservicio independiente para cada sector. Definir límites de dominio en el backend y entregar contratos explícitos al frontend.

### 7.1 Entidades conceptuales

**SubscriptionPlan / versión de plan**
- Código, nombre, descripción, dominio/categoría, estado publicable.
- Precio y moneda con versión efectiva, periodo de precio, nivel y condiciones.
- Beneficios incluidos, límites/exclusiones y política de renovación/cancelación.
- Qué modalidad de entrega/acceso requiere.
- Reglas de elegibilidad y datos mínimos necesarios.

Una suscripción activa debe conservar la versión de plan y precio acordados al contratar. Cambiar el producto maestro no debería modificar silenciosamente un contrato anterior.

**SubscriptionContract**
- Referencia al plan/versión.
- Titular que paga y, cuando corresponda, usuarios/beneficiarios separados.
- Vigencia, política de renovación y zona horaria.
- Precio acordado, moneda, descuentos y condiciones aceptadas.
- Estado del contrato y registro de cambios.
- Origen/canal de alta y consentimiento aplicable.

**BillingSchedule / BillingInstallment**
- Periodo cubierto, fecha de emisión/facturación, base, impuestos, importe y estado.
- Referencia a la factura/venta.
- Clave idempotente y resultado del último intento.
- Reglas de calendario y política de prorrateo.
- Un estado SCHEDULED no debe confundirse con una factura emitida ni con un pago.

**PaymentAttempt / PaymentTransaction (capacidad futura)**
- Proveedor, referencia externa, importe, moneda, estado, fecha, respuesta normalizada e idempotencia.
- Reintentos controlados, conciliación, reembolso y eventos.
- Nunca guardar datos completos de tarjeta. Los medios de pago deben tokenizarse en el proveedor cuando se integre una pasarela.

**Entitlement / SubscriptionBenefit**
- Tipo de beneficio, alcance/plan, fecha inicial/final, límites y versión.
- Usuario/beneficiario al que se aplica.
- Estado activo, suspendido o revocado.
- Fuente y motivo de la concesión/revocación.

**UsageRecord / CreditLedger (si corresponde)**
- Uso medible, unidad, cantidad, timestamp, origen y clave idempotente.
- Libro de créditos con concesión, consumo, reverso y caducidad.
- No recalcular el histórico de consumo cambiando una tarifa actual.

**SubscriptionEvent / audit trail**
- Creación, activación, cambio de plan, renovación, facturación, pago, fallo, pausa, cancelación y fin de derechos.
- Actor, fecha, versión y referencia al efecto persistido.

Esta es una arquitectura conceptual. No se deben crear todas estas entidades sin una decisión de producto. Primero definir el contrato mínimo, y activar módulos de pagos, uso y beneficios solo para los tipos que los necesitan.

### 7.2 Máquina de estados

El modelo actual usa ACTIVE, CANCELLED y COMPLETED. Para un sistema general podría evolucionar hacia estados contractuales como DRAFT, SCHEDULED, TRIAL, ACTIVE, PAUSED, PAST_DUE/GRACE, CANCEL_AT_PERIOD_END, CANCELLED, COMPLETED y EXPIRED, pero solo donde sean estados distintos y accionables.

Separar tres máquinas:
- **Contrato:** qué relación está vigente.
- **Facturación/pago:** qué factura se emitió y si se cobró, falló o reembolsó.
- **Derecho de acceso/prestación:** qué puede consumir y hasta cuándo.

No agregar PAST_DUE a la suscripción si no hay módulo de pagos para determinar el estado. No marcar un contrato como cancelado solo porque una factura falló, sin aplicar la política aprobada del producto. Cada transición debe validar permisos, concurrencia e idempotencia.

### 7.3 Calendario y reglas monetarias

- Definir frecuencia como intervalo real o regla de calendario, no solo etiqueta visual.
- Decidir el anclaje para el último día de mes, febrero y años bisiestos.
- Calcular periodos cubiertos, fechas de factura, fin inclusivo y hora de proceso en una zona horaria definida.
- Definir si el precio es por periodo o mensual de referencia, y cómo se convierte en un cobro quincenal, anual o por uso.
- Fijar la regla de redondeo y distribuir diferencias de centavos sin perder el total.
- Separar prorrateo por alta tardía, cambio de plan, pausa, cancelación y renovación.
- Versionar las reglas y conservar el calendario contractual histórico.

El backend actual ya tiene previsualización y pruebas sobre calendarios; mantenerlas y ampliarlas antes de cambiar su significado.

## 8. APIs: conservar lo que existe y evolucionar gradualmente

### Endpoints implementados según el código inspeccionado

- GET /api/v1/subscriptions/options
- POST /api/v1/subscriptions/preview
- GET /api/v1/subscriptions?status=&customerId=
- GET /api/v1/subscriptions/{id}
- POST /api/v1/subscriptions
- GET /api/v1/subscriptions/{id}/cancellation-preview
- POST /api/v1/subscriptions/{id}/cancel
- POST /api/v1/subscriptions/{id}/prepay

Las lecturas requieren sales:read y las operaciones de escritura sales:manage; la aplicación de descuentos también está limitada por sales:discount. La lista actual tiene filtros status y customerId, sin filtros declarados para plan/producto, local o rango de fechas.

### Extensiones candidatas, no implementadas

1. Ampliar opciones de catálogo y detalle de plan con tipo comercial, frecuencia válida, duración/renovación y beneficios.
2. Añadir búsqueda paginada y filtros server-side para número, cliente, servicio/plan, estado, periodo y errores de facturación.
3. Exponer un calendario de facturación y una bandeja de incidencias; no pedir al frontend calcular qué facturas están vencidas.
4. Incorporar estados de pago solo después de introducir el dominio Payment.
5. Añadir APIs de entitlement/uso para los tipos que necesitan acceso, cuotas o créditos.
6. Definir operaciones de actualización/cambio de plan, pausa/reactivación y renovación solo después de acordar sus reglas.
7. Añadir reportes de retención/ingresos que distingan importes contractuales, facturados y efectivamente cobrados.

Los nuevos contratos deben actualizar el OpenAPI y los DTOs, mantener permisos de servidor y validar la matriz de roles; los nombres propuestos no son rutas existentes.

## 9. Reglas de dominio por sector

### 9.1 Entretenimiento y streaming
- Plan/nivel define contenidos, número de dispositivos o sesiones concurrentes, región y calidad cuando aplique.
- El derecho de acceso se activa y vence con estados contractuales validados.
- No inferir que pagar una factura automáticamente conectó una cuenta de streaming externa: necesita integración/provisión.
- Cambios del catálogo de contenidos deben respetar condiciones comunicadas.

### 9.2 Videojuegos
- Distinguir suscripción de acceso, pase de temporada, complemento comprado una vez y moneda/consumible.
- Beneficios con caducidad o canje requieren un registro de reclamación/consumo para impedir duplicados.
- Plataforma/ID de juego, región y compatibilidad no deben confundirse con el número interno de cliente de Inventary.
- Cancelar la membresía debe revocar derechos según reglas del producto, no borrar compras únicas ni saldos con comportamiento diferente.

### 9.3 Deportes y eventos
- Separar abono de temporada, membresía de club y entrada a un evento puntual.
- Definir temporada, partidos/eventos incluidos, asiento o zona, fechas bloqueadas, capacidad, titular y política de transferencia.
- Un pase no debe ser una suscripción mensual genérica si su valor es el acceso a una lista finita de eventos.
- Entrada/QR, aforo y validación en puerta requieren módulo o integración de ticketing.

### 9.4 Salud y medicina
- Distinguir membresía de clínica, paquete de consultas, cobertura/seguro y cita individual.
- Mostrar con claridad servicios incluidos, excluidos, límites, necesidad de reservar, sedes/profesionales y cargos fuera de cobertura.
- Separar titular pagador del paciente/beneficiario y gestionar dependientes de manera autorizada.
- El módulo comercial registra vigencia/elegibilidad y referencias mínimas; no debe almacenar diagnósticos, historia clínica ni notas médicas.
- Datos de salud y datos de niñas/niños requieren diseño de privacidad y revisión regulatoria reforzada conforme a las normas aplicables en Ecuador.
- No presentar una membresía como seguro médico ni crear promesas clínicas automáticamente a partir del texto de un plan.

### 9.5 Fitness, educación y clases
- Diferenciar acceso ilimitado de un número finito de clases/sesiones.
- Gestionar reservas, cupos, cancelación de turno y no-show solo si el producto lo requiere.
- La vigencia del contrato no debe confundirse con una reserva confirmada de sesión.

### 9.6 SaaS y servicios empresariales
- Definir asientos/usuarios, módulos habilitados, límites, uso medido, SLA y entidad contratante.
- Los cambios de usuarios y niveles pueden tener efecto prorateado; mostrar el importe y fecha antes de confirmar.
- Separar pagador corporativo de usuarios autorizados.

### 9.7 Mantenimiento profesional
- Registrar qué visitas, horas, repuestos o tiempos de respuesta incluye el contrato.
- Las visitas ejecutadas consumen derechos; un cargo mensual no demuestra que se haya prestado el servicio.
- Definir cobertura geográfica, horario, ventanas de mantenimiento y excepciones.

### 9.8 Consumo físico y reposición
- Una caja recurrente puede necesitar compra de bienes, reserva de stock, preparación, envío, retiro, sustituciones y dirección de entrega.
- Pausa/skip de envío no equivale a cancelar el contrato ni a no generar una factura sin revisar la política.
- El modelo actual de suscripción solo acepta producto SERVICE; no asumir que maneja pedidos recurrentes de mercancía. Debe integrar la capa logística existente/propuesta y el inventario sin alterar existencias directamente.

## 10. Métricas de negocio y analítica

### Primero: métricas deterministas que el backend ya puede soportar o preparar

- Contratos activos, cancelados y completados por cohorte y tipo.
- Próximos cargos programados y facturas que fallaron.
- Importe contractual, importe previsto para facturar, importe facturado e importe pagado cuando exista módulo de pagos.
- Duración media y distribución de vigencias.
- Tasa de cancelación por periodo con definición estable.
- Motivos de cancelación normalizados además del texto libre.
- Tiempo entre fallo y resolución de facturación.
- Ratio de facturas duplicadas (debe ser cero), ejecuciones fallidas y reintentos.
- Beneficios concedidos/revocados y consumo de créditos cuando existan esos módulos.

### Fórmulas de referencia

Definir el denominador y el periodo antes de publicar la métrica.

- **Tasa de cancelación de contratos:** contratos cancelados durante el periodo / contratos activos al inicio del periodo, ajustando explícitamente altas y reactivaciones según la definición elegida.
- **Retención por cohorte:** contratos de una cohorte que siguen vigentes en el horizonte / contratos iniciales elegibles de esa cohorte.
- **Ingresos recurrentes mensuales normalizados (MRR):** suma de la parte mensual normalizada de los contratos recurrentes activos, usando una regla documentada para anualidades y sin incluir servicios puntuales que no sean recurrentes.
- **Churn de ingresos:** pérdida de valor recurrente atribuible a cancelaciones/cambios de plan, dividida por el valor recurrente inicial de la cohorte/periodo con una definición publicada.
- **Recaudo real:** suma de transacciones de pago confirmadas menos reembolsos confirmados; nunca inferirlo de facturas o del atributo prepaid.

No mostrar MRR, ARR o churn de ingresos como verdades financieras si todavía no se distingue suscripción recurrente, factura y pago. Los contratos de importe único al finalizar deben clasificarse según su naturaleza, no incluirse artificialmente en MRR.

### Después: modelos estadísticos

Cuando haya datos suficientes:
- Análisis de cohortes por tipo/plan y mes de alta.
- Curvas de retención/supervivencia para estimar el momento de cancelación, tratando contratos activos como observaciones censuradas.
- Modelo interpretable de probabilidad de fallo/cancelación para priorizar atención, no para dificultar la baja.
- Pruebas controladas de una mejora de onboarding, recordatorio o plan, con métrica primaria y guardas.

No empezar con IA generativa ni un modelo de churn en producción sin datos de renovaciones, acceso/uso, facturas, pagos y cancelación que puedan relacionarse. Una correlación en pocos contratos no es base suficiente para automatizar decisiones.

## 11. Seguridad, privacidad, cobros y auditoría

1. Separar permisos de visualizar suscripciones, crear/cancelar contratos, aplicar descuentos, emitir facturas, registrar pagos y gestionar beneficios.
2. No incluir datos bancarios o de tarjetas completos en el modelo de suscripción, logs ni eventos; usar proveedor de pago y tokens si hay integración.
3. Conservar quién creó el plan y quién aceptó precio, frecuencia, duración, renovación y política de cancelación.
4. Las modificaciones a un plan no deben reescribir contratos históricos.
5. No almacenar datos clínicos dentro del registro comercial genérico.
6. Validar permisos de acceso a beneficiarios y dependientes, especialmente menores.
7. No notificar detalles de salud o pagos a canales sin comprobar identidad y consentimiento.
8. Cualquier cambio de precio o nivel debe indicar fecha de entrada en vigor y efecto contractual; el servidor valida la política, no el frontend.
9. Cancelar debe tener una acción clara y efecto predecible. No usar fricción artificial para retener suscriptores.
10. Las reglas legales de renovación, facturación, consentimiento y cancelación se revisan para Ecuador y el sector específico antes de activar cobros reales.

## 12. Plan de implementación priorizado

### Fase 1 — Hacer fiable la pantalla actual
- Corregir las etiquetas «Cobrar/Pagada por adelantado» para que reflejen que el backend consolida facturas, no confirma un pago.
- Mostrar error/reintento de options y preview y validar frecuencia, duración y calendario antes de confirmar.
- Mejorar búsqueda, filtros y bandeja de errores de facturación.
- Diferenciar claramente importe programado, facturado y pagado (este último solo cuando tenga fuente de verdad).
- Revisar y probar semántica de periodos cada 15 días, fin de mes, AT_END y vigencia inclusiva.
- Crear/expandir pruebas de permisos, idempotencia, concurrencia del job, reintentos, fechas futuras, meses cortos y cancelación.
- Mantener el modelo actual para los servicios de duración fija que ya soporta.

### Fase 2 — Planes y lifecycle genéricos
- Versionar plan/precio/condiciones.
- Separar contrato, programación de factura y estados de pago.
- Decidir renovación automática/manual y modelar su consentimiento solo cuando sea un requisito real.
- Incorporar cambios de plan, pausas y renovaciones con reglas y previsualización.
- Crear reportes consistentes de cohortes y cancelaciones.

### Fase 3 — Cobro real y beneficios
- Integrar proveedor de pagos solo tras definir conciliación, fallos, reintentos, reembolsos y seguridad.
- Añadir derechos/beneficios para los primeros dominios seleccionados.
- Crear provisión/revocación de acceso con eventos idempotentes y reintentos.
- Implementar notificaciones de próximo cargo, fallo, renovación y expiración según consentimiento.

### Fase 4 — Dominios especializados
Priorizar un sector según casos reales del negocio: membresías de servicios, entretenimiento, gimnasios/educación, SaaS/soporte o salud/eventos. Añadir solo el módulo que resuelve el sector; no construir ticketing, historia clínica o sistema de licencias dentro del núcleo de facturación.

### Fase 5 — Optimización basada en evidencia
- Cohortes y métricas confiables.
- Experimentos de onboarding y comunicación transparente.
- Supervivencia/churn y predicción de fallos con evaluación temporal y explicable.
- Automatización de retención únicamente si no crea obstáculos a la cancelación y mejora una métrica validada.

## 13. Matriz de pruebas de aceptación

### Pantalla y API actual
1. Crear suscripción mensual a 1, 3, 6 y 12 meses.
2. Previsualizar frecuencia, periodos, redondeo, total y fechas.
3. Configurar fecha futura y no facturar antes de su fecha.
4. Probar el día 28/29/30/31, febrero, año bisiesto y cambio de año.
5. Simular API options y preview caídas: el usuario ve el error y no puede confirmar un formulario incompleto.
6. Ejecutar el job dos veces: una cuota no genera dos facturas.
7. Provocar un fallo de facturación y verificar la bandeja de atención y reintento.
8. Cancelar antes del inicio, durante el periodo actual, tras facturar y al final del plan.
9. Probar cancelación repetida, descuento sin permiso y clave idempotente reutilizada con cuerpo distinto.
10. Comparar facturado vs pagado: no marcar pago a partir de una venta/factura.

### Cobros y estados futuros
11. Pago aprobado, rechazado, pendiente, notificación duplicada y resultado ambiguo.
12. Reintento de pago sin duplicar transacción.
13. Reembolso total/parcial y conciliación con la venta.
14. Renovación con consentimiento, renovación rechazada y cancelación antes del corte.
15. Cambio de plan y efecto monetario antes/después.
16. Estado de acceso según contrato/pago con periodo de gracia definido por el plan.

### Tipos específicos
17. Tier de streaming cambia beneficios a la fecha correcta.
18. Gaming: beneficio mensual no se reclama dos veces; consumible no se confunde con cuota recurrente.
19. Abono deportivo excluye eventos fuera de temporada y no duplica entradas.
20. Salud: titular pagador distinto del paciente; permisos y datos de salud minimizados.
21. Clases: reserva, cupo, no-show y crédito consumido solo si este módulo se habilita.
22. SaaS: cambios de asientos y uso medido con auditoría.
23. Mantenimiento: se consume una visita y queda historial de prestación.
24. Caja física: stock insuficiente, dirección, skip/pausa y pedido/entrega, solo tras integrar logística.

### Accesibilidad y operación
25. Teclado, lector de pantalla, móvil, mensajes de validación y foco.
26. Usuario sin sales:read, sales:manage o sales:discount.
27. Error de red después de crear/cancelar: consultar resultado y no duplicar.
28. Los nombres de estado, cifras y recordatorios deben ser comprensibles para usuarios no técnicos.

## 14. Fuentes consultadas

- Kowalkowski & Ulaga (2024), *Subscription offers in business-to-business markets: Conceptualization, taxonomy, and framework for growth*. Industrial Marketing Management. https://doi.org/10.1016/j.indmarman.2024.01.014
- RevenueCat (2026), *State of Subscription Apps*. Datos de monetización de apps por categoría; referencia de tendencias, no benchmark universal. https://www.revenuecat.com/state-of-subscription-apps
- Kreimer et al. (2026), *Pathways to premium: How habits and benefits shape conversion and retention in freemium subscription models*. Journal of the Academy of Marketing Science. https://link.springer.com/article/10.1007/s11747-026-01205-w
- Libai, Muller & Schoenmueller (2026), *The effects of churn on the growth of subscription services: Adopters, users, money*. International Journal of Research in Marketing. https://doi.org/10.1016/j.ijresmar.2025.03.005
- YouTube Help, niveles, beneficios y análisis de membresías. https://support.google.com/youtube/answer/7544492?hl=es
- Xbox Game Pass, planes y beneficios por nivel. https://www.xbox.com/es-ES/xbox-game-pass
- F1 TV, comparación de planes y tipos de acceso. https://f1help.formula1.com/article/F1-TV-subscription-comparison
- Direct Primary Care Associates, ejemplo de membresía de salud con alcance y exclusiones explícitas; no es asesoría legal para Ecuador. https://directprimarycareassociates.com/for-individuals/
- Superintendencia de Protección de Datos Personales de Ecuador, Guía de Protección de Datos Personales desde el Diseño y por Defecto. https://www.spdp.gob.ec/docs/guias/2025-2/guia-de-proteccion-de-datos-personales-desde-el-diseno-y-por-defecto/
- Federal Trade Commission, Negative Option Rule. Se utiliza como referencia de transparencia de diseño, no como ley ecuatoriana. https://www.ftc.gov/legal-library/browse/rules/negative-option-rule

## 15. Decisión recomendada

No convertir la pantalla actual en un configurador inmenso con opciones que nadie utiliza. Primero consolidar su propósito actual —contratos de servicios con calendario de facturación— y corregir las diferencias entre factura y pago, las fechas de cobro, los errores y la búsqueda.

Después introducir una capa de planes versionados, lifecycle y beneficios. Las suscripciones de entretenimiento, videojuegos, deporte, medicina, SaaS, mantenimiento o consumibles podrán compartir identidad, fechas y auditoría, pero deberán definir por separado sus derechos, límites, prestación y pagos. Así Inventary podrá ampliar su alcance sin perder claridad funcional ni mezclar un contrato de servicio, una factura, un pago y el acceso que recibe el cliente.
