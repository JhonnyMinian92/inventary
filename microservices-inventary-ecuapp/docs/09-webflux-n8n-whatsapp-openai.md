> **Adenda de estado — 2026-10-26:** las secciones de arquitectura/contrato de este documento describen parte de la visión y decisiones del canal. La API implementada real está en [25 — Referencia de APIs](25-referencia-apis.md). El backend actual usa Spring MVC, no una migración global a WebFlux. El canal WhatsApp usa X-Integration-Key y requiere identidad verificada; las cotizaciones expiran y el pago se confirma manualmente. No hay pasarela de pago, reembolso o factura fiscal integrados. Consumir esta integración solo tras validar credenciales y pruebas de no duplicación.

# 09 — WebFlux, n8n local, WhatsApp y OpenAI: arquitectura de venta conversacional

## 1. Decisión ejecutiva

**Recomendación inicial: Spring MVC + PostgreSQL/JPA para el backend transaccional, n8n self-hosted como orquestador y OpenAI API para comprensión/diálogo.** No adoptar WebFlux de forma global en el primer release.

El núcleo de Inventary exige transacciones relacionales, restricciones de stock, reservas, ventas y ledger. Con JPA/JDBC, la ruta típica es bloqueante; WebFlux no vuelve asíncrona una capa de persistencia bloqueante. La documentación oficial de Spring recomienda MVC para arquitecturas que utilizan APIs bloqueantes de persistencia (JPA/JDBC) y señala que el valor reactivo se nota sobre todo cuando existe mucha E/S remota lenta o impredecible y toda la pila puede operar sin bloquear.

Fuente: [Spring Framework — Spring MVC or WebFlux?](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html).

WebClient se puede utilizar en Spring MVC para llamadas HTTP a servicios externos cuando lo necesite el backend. Sin embargo, este diseño asigna principalmente la orquestación con OpenAI y WhatsApp a n8n, por lo que el backend no necesita hacer una llamada a un LLM dentro del request de checkout.

## 2. Cuándo sí evaluar WebFlux

Evaluar un servicio o adaptador WebFlux solo si hay evidencia de al menos uno de estos casos:
- Muchas conexiones simultáneas de larga duración o streaming (SSE/WebSocket).
- Alto volumen de llamadas remotas con latencias variables y una cadena enteramente no bloqueante.
- Concurrencia de E/S que no se resuelve de forma rentable aumentando capacidad de MVC.
- Persistencia realmente reactiva con driver compatible, límites de conexión y transacciones entendidas.

Para adoptar una ruta totalmente reactiva habría que reemplazar JPA/JDBC por alternativas no bloqueantes como R2DBC en esa ruta, revisar el manejo de locking y transacciones, evitar llamadas bloqueantes en event loops y medir con una carga representativa. No mezclar bloqueos síncronos dentro del event loop. No se asume que WebFlux sea automáticamente más rápido: su beneficio principal es la utilización de threads y memoria bajo carga de E/S.

No usar WebFlux solo porque n8n, WhatsApp o OpenAI sean externos; esa latencia debe quedar fuera de la transacción de inventario.

## 3. Arquitectura recomendada

~~~mermaid
flowchart LR
    Customer[Cliente en WhatsApp] --> Meta[WhatsApp Business Platform / Cloud API]
    Meta --> PublicWebhook[HTTPS público para webhook]
    PublicWebhook --> N8N[n8n self-hosted / workflow]
    N8N --> OpenAI[OpenAI Responses API / function calling]
    OpenAI --> N8N
    N8N -->|HTTPS + service credential| API[Inventary REST API]
    API --> Auth[Scopes integración y límites]
    Auth --> UseCase[Casos de uso de catálogo y ventas]
    UseCase --> DB[(PostgreSQL)]
    UseCase --> Ledger[Stock ledger + balances]
    API --> N8N
    N8N --> Meta
    Meta --> Customer
~~~

### Responsabilidad de cada pieza

- **WhatsApp Business Platform / Cloud API:** transporta los mensajes y eventos del número comercial.
- **n8n self-hosted:** recibe el webhook, normaliza el mensaje, administra la conversación y decide qué herramienta de negocio llamar. Mantiene las credenciales de WhatsApp/OpenAI y llama a Inventary por HTTP.
- **OpenAI API:** interpreta intención y lenguaje natural, extrae parámetros propuestos y redacta respuestas. No es la fuente de verdad del catálogo, precios, saldo ni estados de pedido.
- **Inventary:** valida las reglas del negocio, confirma el precio actual, disponibilidad, permisos, reserva y crea el pedido de manera transaccional. Nunca delega decisiones de stock al modelo.
- **PostgreSQL:** fuente de verdad de productos, precios, stock, reservas y pedidos.

No se comparte acceso directo de n8n a las tablas de negocio. n8n consume endpoints documentados. La credencial de integración tendrá permisos limitados a catálogo público y pedidos del canal WhatsApp; no tendrá acceso a administración, costos ni margen bruto.

## 4. Restricción fundamental del modo local

n8n y el backend pueden correr en equipos locales/contendedores y comunicarse por una red privada o Docker. Sin embargo, el proveedor de WhatsApp necesita poder enviar eventos a un callback HTTPS accesible desde su infraestructura. Un endpoint disponible únicamente en localhost o en una LAN privada no puede recibir directamente webhooks de Meta.

- **Desarrollo:** usar un túnel HTTPS temporal seguro o una URL de pruebas accesible desde internet; protegerlo, limitarlo y no usar datos reales innecesarios.
- **Producción:** exponer solo el callback del gateway/n8n mediante HTTPS y reverse proxy/túnel estable, o desplegar el componente receptor en un host accesible. El servicio de negocio puede seguir en una red privada detrás del receptor.
- No exponer directamente PostgreSQL ni todo el backend a internet para solucionar el webhook.
- Validar el mecanismo de verificación del webhook, su token de challenge y la firma/autenticidad conforme al método soportado por la versión de WhatsApp Cloud API configurada.
- Aplicar idempotencia porque un webhook puede reintentarse y el mismo mensaje no debe crear varios pedidos.

Fuentes:
- [Documentación oficial de WhatsApp Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api)
- [n8n — instalación con Docker](https://docs.n8n.io/deploy/host-n8n/install-options/install-with-docker.md)
- [n8n — nodo WhatsApp Business Cloud](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.whatsapp)

n8n puede autoalojarse con Docker; el contenedor requiere volúmenes persistentes, una clave de cifrado protegida, actualizaciones y backup. “Local” no significa que el canal WhatsApp/OpenAI deje de depender de servicios externos.

## 5. APIs de venta conversacional

Las rutas siguientes son propuestas; deben añadirse al catálogo OpenAPI y se implementarán con el resto del backend. Se recomienda un namespace explícito para separar el catálogo público y el cliente técnico del canal.

### Catálogo de venta pública

#### GET /api/v1/public/catalog/products

Lista paginada y filtrable de productos publicables para clientes finales.

Filtros:
- q: texto de búsqueda permitido.
- categoryId o categorySlug.
- minPrice, maxPrice, sort por relevancia/nombre/precio.
- page, size, con máximo size=50 para uso conversacional.
- inStockOnly=true por defecto para recomendaciones de compra, configurable según necesidad.

Response ilustrativa:

~~~json
{
  "items": [
    {
      "id": "prd_123",
      "sku": "SKU-123",
      "slug": "audifonos-bluetooth",
      "name": "Audífonos Bluetooth",
      "description": "Audífonos inalámbricos con estuche de carga.",
      "price": "29.90",
      "currency": "USD",
      "image": {
        "url": "https://media.example.com/products/audifonos.webp",
        "alt": "Audífonos Bluetooth negros"
      },
      "stock": {
        "availableToSell": 8,
        "availability": "IN_STOCK"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
~~~

Reglas:
- Solo productos activos, publicables y con precio de venta vigente.
- La imagen debe usar un URL estable HTTPS accesible al canal consumidor, o n8n debe descargarla a través de una ruta autenticada y cargarla al proveedor de WhatsApp según la capacidad soportada. Nunca devolver rutas locales, por ejemplo una ruta de disco o localhost, como URL de imagen para WhatsApp.
- Devolver existencia vendible, no costo, margen, precio proveedor, stock de otras empresas o movimientos. Si revelar una cifra exacta de inventario es comercialmente sensible, configurar disponibilidad pública (IN_STOCK / LOW_STOCK / OUT_OF_STOCK) y reservar el detalle exacto a clientes autenticados.
- No usar availableToSell como reserva. Es una lectura de catálogo y puede cambiar inmediatamente.
- El endpoint público debe tener rate limit, límite de página, caché de lectura con invalidación/versionado y defensa de scraping. Nunca incluir datos de clientes.

#### GET /api/v1/public/catalog/products/{productIdOrSlug}

Devuelve detalle del producto, descripción, precio/currency, imagen principal y galería permitida, variantes/opciones y estado de disponibilidad. No devuelve campos internos de costos.

#### GET /api/v1/public/catalog/categories

Categorías publicables y contadores opcionales. Los contadores deben ser baratos y no permitir inferir datos internos.

### Integración privada de n8n

Autenticación con OAuth2 client credentials, token de servicio con scopes o API key fuerte rotada tras gateway, guardada como credential de n8n. Preferencia: OAuth2 client credentials o JWT de máquina con expiración. El secreto nunca se incluye en un prompt al LLM ni en un nodo/expresión exportable sin protección.

| Método y ruta | Uso |
|---|---|
| GET /api/v1/integrations/whatsapp/catalog/products | Buscar catálogo para el bot con campos de venta e imagen. Requiere scope catalog:read. |
| GET /api/v1/integrations/whatsapp/catalog/products/{idOrSku} | Recuperar detalle vigente. |
| POST /api/v1/integrations/whatsapp/carts | Crear o recuperar carrito de una conversación; asociar customerRef derivado del wa_id verificado en el flujo, no de texto libre del LLM. |
| GET /api/v1/integrations/whatsapp/carts/{cartId} | Consultar carrito y sus importes recalculados. |
| PUT /api/v1/integrations/whatsapp/carts/{cartId}/items/{variantId} | Añadir o establecer cantidad de una variante; valida activo y cantidad positiva. |
| DELETE /api/v1/integrations/whatsapp/carts/{cartId}/items/{variantId} | Retirar línea antes de confirmar. |
| POST /api/v1/integrations/whatsapp/carts/{cartId}/quote | Recalcular precios, disponibilidad, descuentos permitidos, gastos de envío si se implementan y total; genera quoteId y expiresAt. No cobra ni afirma que el stock quede reservado. |
| POST /api/v1/integrations/whatsapp/orders | Convertir cotización no expirada a pedido y reservar stock atómicamente tras confirmación explícita del cliente. Requiere Idempotency-Key y quoteId. |
| GET /api/v1/integrations/whatsapp/orders/{orderId} | Estado limitado del pedido para atención por ese canal, comprobando customerRef y scope. |
| POST /api/v1/integrations/whatsapp/orders/{orderId}/cancel-request | Solicitar cancelación según la política comercial; no borra ni revierte de manera incondicional. |

No aceptar precio, subtotal, descuento, disponibilidad final, tenant, rol, actor ni estado definitivo enviados por el modelo. Inventary toma cada precio de su fuente oficial y calcula los importes en servidor. El backend deriva el actor de integración desde el token y el customerRef desde el contexto autenticado del workflow.

### Contrato de creación de pedido

Request ilustrativa:
~~~json
{
  "quoteId": "qte_987",
  "customer": {
    "displayName": "Cliente",
    "phoneE164": "+593XXXXXXXXX"
  },
  "delivery": {
    "method": "PICKUP",
    "address": null
  },
  "customerConfirmation": {
    "channel": "WHATSAPP",
    "messageId": "wamid.provider-message-id"
  }
}
~~~

El messageId debe proceder de un evento entrante real de WhatsApp verificado por el workflow, no de un argumento generado libremente por el modelo. No recoger ni almacenar dirección si no se necesita para la modalidad de entrega.

Response ilustrativa:
~~~json
{
  "orderId": "ord_2026_000123",
  "status": "AWAITING_PAYMENT",
  "currency": "USD",
  "total": "59.80",
  "items": [
    {
      "sku": "SKU-123",
      "name": "Audífonos Bluetooth",
      "quantity": 2,
      "unitPrice": "29.90",
      "lineTotal": "59.80"
    }
  ],
  "expiresAt": "2026-10-22T21:30:00Z"
}
~~~

AWAITING_PAYMENT significa pedido creado y stock reservado según la política, no pago cobrado. Solo un callback verificado del proveedor de pago o una confirmación permitida por el negocio puede marcar como pagado. El MVP puede registrar pedidos contra entrega/retiro sin procesar pagos en línea, si así se aprueba.

## 6. Flujo de compra con WhatsApp

~~~mermaid
sequenceDiagram
    actor C as Cliente
    participant W as WhatsApp Cloud API
    participant N as n8n local / workflow
    participant L as OpenAI API
    participant I as Inventary API
    participant DB as PostgreSQL
    C->>W: Consulta o intención de comprar
    W->>N: Webhook entrante con messageId y wa_id
    N->>N: Verificar evento, deduplicar y cargar contexto
    N->>L: Mensaje + herramientas permitidas
    L-->>N: Intento de buscar producto / modificar carrito
    N->>I: GET catálogo o PUT carrito con credencial de servicio
    I->>DB: Consulta de catálogo/sesión/carrito
    DB-->>I: Datos vigentes
    I-->>N: Productos, precio, imagen y disponibilidad
    N->>L: Resultado de herramientas
    L-->>N: Propuesta en lenguaje natural
    N->>W: Catálogo/resumen/cotización
    W->>C: Productos e importe para confirmar
    C->>W: Confirmación explícita del resumen
    W->>N: Evento de confirmación verificado
    N->>I: POST order con quoteId e Idempotency-Key
    I->>DB: TX: validar quote, revalidar stock, crear pedido + reserva + auditoría
    DB-->>I: Commit o conflicto
    I-->>N: Resultado del pedido
    N->>W: Confirmación de pedido o alternativa por stock agotado
    W->>C: Estado y número de pedido
~~~

## 7. Reglas obligatorias contra pedidos erróneos

1. El agente puede buscar y explicar, pero no confirmar una compra sin mostrar productos, cantidades, precio unitario, total, entrega y obtener confirmación inequívoca del cliente.
2. Solo el backend decide si el pedido es válido, calcula totales y reserva las unidades.
3. La cotización expira; al confirmar, volver a comprobar precio y stock en una única transacción.
4. Si cambió el precio o falta stock, no crear silenciosamente el pedido: generar una nueva cotización y pedir consentimiento.
5. El messageId entrante y clave idempotente evitan duplicación por reintentos de webhook o de workflow.
6. Un wa_id no equivale necesariamente a identidad civil verificada. Guardar solo los datos necesarios y no conceder acceso a pedidos ajenos por conocer un teléfono.
7. Limitar las herramientas del LLM a consultas de catálogo, manipulación de carrito, cotización y creación de pedido con confirmación. No darle SQL, administración, descuentos arbitrarios o cambios de stock.
8. Mantener historial corto y minimizado de conversación; no enviar información sensible o costos internos a OpenAI.
9. Configurar los mensajes de WhatsApp y plantillas conforme a las políticas vigentes del proveedor y la ventana de conversación aplicable.
10. Proporcionar ruta de escalamiento a una persona y un mensaje claro cuando el proveedor o backend no estén disponibles.

## 8. Funciones de n8n y OpenAI

n8n cuenta con un nodo WhatsApp Business Cloud para enviar mensajes y manejar medios, además de opciones de espera/aprobación de humano. Fuente: [n8n WhatsApp Business Cloud node](https://docs.n8n.io/integrations/builtin/app-nodes/n8n-nodes-base.whatsapp).

Para OpenAI, utilizar Responses API con tool/function calling o la integración soportada por la versión de n8n instalada. El modelo genera una propuesta estructurada de llamada; el workflow valida y ejecuta las herramientas con respuestas del backend. No confiar el control de transacciones a la “memoria” del modelo.

Fuente: [OpenAI — Function Calling](https://developers.openai.com/api/docs/guides/function-calling).

## 9. Fuera de alcance hasta aprobación

Pasarela de pago y confirmación automática de pago; factura electrónica SRI/notas de crédito legales; integración con catálogo nativo de Meta; logística/cotización dinámica de envío; devoluciones iniciadas completamente por WhatsApp; CRM/marketing y campañas masivas. Deben estimarse y aprobarse por separado.

## 10. ChatGPT versus OpenAI API e imágenes

Para automatizar conversaciones desde n8n, se debe integrar con la **OpenAI API** mediante credenciales de API y el nodo/integración compatible de n8n o su HTTP Request node. No se automatiza la interfaz web de ChatGPT ni se debe asumir que una suscripción de ChatGPT habilita automáticamente el uso API; verificar el proyecto, credenciales, facturación y políticas vigentes de la plataforma API.

Fuente técnica: [OpenAI API — Responses API overview](https://developers.openai.com/api/reference/responses/overview) y [Function Calling](https://developers.openai.com/api/docs/guides/function-calling).

La llamada al modelo debe recibir la intención del cliente y una lista pequeña de herramientas estructuradas. Por ejemplo: search_products, get_product_details, add_cart_item, remove_cart_item, quote_cart, submit_confirmed_order y get_order_status. El modelo nunca recibe credenciales, consulta SQL ni autoridad para modificar stock. El resultado de cada herramienta proviene de Inventary y se devuelve como datos estructurados.

### Imágenes: dos caminos válidos

1. **URL HTTPS pública:** el catálogo devuelve una URL estable de un storage/CDN público para imágenes de producto, que se puede enviar al cliente o usar como referencia.
2. **Subida de medios por workflow:** si la imagen solo está en un servicio local/private, n8n la descarga con autorización y la carga a la API de medios de WhatsApp, cuando el tipo de mensaje lo permita; después usa el media ID del proveedor. El proveedor no puede acceder a un archivo local por una URL localhost.

No publicar todo el storage por conveniencia: solo las imágenes autorizadas para catálogo deben ser públicas.