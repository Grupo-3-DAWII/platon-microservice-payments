# platon-microservice-payments

Microservicio de pagos y ventas del sistema de gestion de libreria. Expone una API REST para registrar productos vendidos, consultar ventas por usuario y administrar los estados de venta.

## Tecnologias

- Java 25
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Cloud Netflix Eureka Client
- Spring Cloud Config Client
- PostgreSQL
- RabbitMQ (productor de eventos de compra)
- Lombok
- Maven Wrapper

## Configuracion

El archivo local `src/main/resources/application.yml` solo identifica el microservicio e importa el Config Server:

```yaml
spring:
  application:
    name: platon-microservice-payments
  config:
    import: configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

La configuracion de puerto, PostgreSQL, JPA, RabbitMQ, exchanges, colas, timeout y Eureka vive en `../platon-config-server/config-repo/platon-microservice-payments.yml`. El Config Server tambien entrega la configuracion comun de `config-repo/application.yml`. Payments mantiene Eureka desactivado por defecto; se puede habilitar con `EUREKA_ENABLED=true`.

El Config Server debe estar disponible antes de arrancar Payments y de ejecutar sus pruebas de integracion. Puedes comprobar su configuracion con:

```bash
curl 'http://localhost:8888/platon-microservice-payments/default'
```

Para otra direccion de Config Server, define `CONFIG_SERVER_URL`. La configuracion central permite ajustar `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, las variables `RABBITMQ_*` y `PURCHASE_VALIDATION_TIMEOUT_MS` sin agregar configuracion de infraestructura al YAML local.

El servicio espera PostgreSQL en `localhost:5432`, con el esquema `payment` ya creado. Hibernate valida el modelo con `ddl-auto=validate` y Spring SQL Init esta desactivado (`spring.sql.init.mode=never`). Los scripts de creacion y migracion pertenecen al proyecto hermano `../DATABASE`: `init/04-payment-purchase-flow.sql` prepara el outbox, los productos asignados y el identificador de descuento; `init/05-products-stock-deduction.sql` prepara el registro de descuentos de Products. El microservicio no incluye ni ejecuta scripts DDL al arrancar. Eureka esta desactivado para el entorno local.

Para un volumen Docker existente se aplican solo esas migraciones, segun `DATABASE/README.md`, conservando los datos. En un volumen nuevo, el init de PostgreSQL las ejecuta automaticamente despues de los scripts base.

## Eventos de compra en RabbitMQ

`POST /api/v1/products-sold` publica `purchase.requested` y espera la respuesta de Products mediante RabbitMQ. Products verifica que el producto exista, este activo y tenga stock suficiente, descuenta la cantidad y registra un movimiento `EXIT` en la misma transaccion. Devuelve `purchase.availability.checked` a una cola de respuestas exclusiva de Payments mediante el exchange `topic`. El `requestId` identifica tambien el descuento para impedir descuentos y devoluciones duplicados.

Solo una validacion positiva permite registrar la compra y su evento final `purchase.created` en una misma transaccion PostgreSQL. El controlador delega al servicio. La espera de RabbitMQ no mantiene abierta la transaccion de base de datos. La publicacion del evento final se ejecuta en segundo plano cada 5 segundos, despues de confirmar esa transaccion.

El POST responde `200 OK` una vez registrada la compra validada, `409 Conflict` si Products rechaza la disponibilidad, `504 Gateway Timeout` si no llega respuesta en 10 segundos, y `503 Service Unavailable` si falla la comunicacion o la respuesta es invalida. Los rechazos y timeouts no registran compras ni eventos finales. Las solicitudes expiran y las respuestas tardias se descartan. `PURCHASE_VALIDATION_TIMEOUT_MS` permite cambiar el tiempo de espera.

La solicitud debe iniciar en `PENDIENTE`. No hay reservas ni vencimientos de inventario. Al aceptar el pago se marca `PAGADO` y se asigna el producto al usuario en `payment.user_products`, sin enviar eventos ni modificar nuevamente el stock. Al rechazar se marca `CANCELADO` y se registra `payment.rejected` en el outbox; Products consume ese evento y devuelve exactamente la cantidad descontada, registrando un movimiento `ENTRY`.

Las decisiones se serializan por compra: repetir la misma decision es idempotente y cambiar una decision final devuelve `409`. Los endpoints genericos de editar, cambiar estado o eliminar no permiten saltarse el flujo de una compra con descuento de stock. Las compras antiguas sin descuento asociado no pueden usarse para generar una devolucion.

```bash
# Usar el idProductsSold devuelto por una solicitud nueva.
curl --request POST 'http://localhost:8082/api/v1/products-sold/123/payment/accept'
curl --request POST 'http://localhost:8082/api/v1/products-sold/124/payment/reject'
```

Ambos endpoints devuelven `200 OK`. La devolucion por rechazo es asincrona y se publica cada 5 segundos; si Products esta temporalmente fuera de linea, el evento permanece en la cola durable y se procesa cuando regresa. Si la solicitud no llega a guardarse tras intentar descontar stock, se registra un evento compensatorio de devolucion con el mismo identificador de descuento. La tabla de descuentos de Products evita que una solicitud atrasada vuelva a descontar despues de esa compensacion.

| Recurso | Valor por defecto |
| --- | --- |
| Broker | `localhost:5672`, virtual host `/` |
| Credenciales locales | `guest` / `guest` |
| Exchange durable de tipo `topic` | `platon.purchase.events` |
| Routing key | `purchase.created` |
| Cola durable vinculada | `platon.purchase.created` |
| Routing key de solicitud | `purchase.requested` |
| Cola de solicitudes de Products | `platon.products.purchase.validation` |
| Routing key de rechazo / devolucion | `payment.rejected` |
| Cola de devoluciones de Products | `platon.products.payment.rejected` |
| Routing key de respuesta por instancia | `purchase.availability.checked.<uuid>` |
| Panel de administracion | `http://localhost:15672` |

El Docker Compose de `../DATABASE` levanta PostgreSQL y RabbitMQ con `docker compose up -d --wait`. El broker carga sus exchanges, colas durables y bindings desde `DATABASE/rabbitmq/definitions.json`; Payments solo crea su cola temporal de respuestas y el binding de esa instancia. Las variables `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD` permiten cambiar la conexion. Los nombres de los clientes siguen en Config Server y deben coincidir con el init de DATABASE.

Ejemplo para registrar y publicar una compra (el estado `1` debe existir):

```bash
curl --request POST 'http://localhost:8082/api/v1/products-sold' \
  --header 'Content-Type: application/json' \
  --data '{"idUser":2,"idStateSold":1,"products":[{"idProducts":1,"numberSold":1},{"idProducts":2,"numberSold":3}]}'
```

Los eventos `purchase.created` y `payment.rejected` contienen `eventId` (UUID), `eventType`, `eventVersion`, `occurredAt` (UTC), `idProductsSold`, `idProducts`, `idUser`, `numberSold`, `createDate`, `idStateSold`, `state` y `stockDeductionId` (UUID). Una compensacion de solicitud fallida puede tener `idProductsSold=null`. Los ceros del listado son solo de presentacion.

El productor usa mensajes persistentes y confirma la recepcion y el enrutamiento antes de marcar el evento como publicado. Si RabbitMQ falla, el evento permanece en la base y se reintenta. Una compra revertida no deja un evento publicable. La entrega es **al menos una vez**: los consumidores deben deduplicar por `eventId` si una interrupcion ocurre entre la confirmacion del broker y el marcado en PostgreSQL. No se publican eventos al listar, editar o eliminar compras, ni para los datos de ejemplo insertados por SQL.

Se puede inspeccionar el estado de entrega con:

```sql
SELECT event_id, id_products_sold, published_at, attempts, last_error
FROM payment.purchase_event_outbox
ORDER BY created_at DESC;
```

Products debe estar ejecutandose y consumir la cola de validacion para que las nuevas compras puedan completarse. La cola del evento final `purchase.created` queda disponible para otros consumidores.

## Ejecucion

Desde la raiz del proyecto:

Primero levanta PostgreSQL y RabbitMQ en Docker y el Config Server (`8888`). Luego inicia Payments:

```bash
./mvnw spring-boot:run
```

En Windows:

```bash
mvnw.cmd spring-boot:run
```

La API queda disponible en:

```text
http://localhost:8082
```

## Pruebas

```bash
./mvnw test
```

En Windows:

```bash
mvnw.cmd test
```

## Datos Iniciales

Al iniciar la aplicacion, `DataInitializer` crea los estados iniciales si no existen:

- `PENDIENTE`
- `PAGADO`
- `CANCELADO`

## Endpoints

### Productos asignados al usuario

`GET /api/v1/user-products?idUser=2` consulta `payment.user_products`. Devuelve las compras aceptadas del usuario con ID de compra, ID de usuario, ID de producto, cantidad, fecha de asignacion y datos del producto (nombre, ISBN, autor e imagen). Cada compra genera su propio registro. Si no tiene productos asignados, responde `200 OK` con `[]`. Un `idUser` ausente o menor a 1 devuelve `400 Bad Request`.

```bash
curl 'http://localhost:8082/api/v1/user-products?idUser=2'
```

### Estados de Venta

Base URL: `/api/v1/states-sold`

| Metodo | Ruta | Descripcion |
| --- | --- | --- |
| `GET` | `/api/v1/states-sold` | Lista todos los estados de venta. |
| `GET` | `/api/v1/states-sold/{id}` | Obtiene un estado por ID. |
| `POST` | `/api/v1/states-sold` | Crea un nuevo estado. |
| `PUT` | `/api/v1/states-sold/{id}` | Actualiza un estado existente. |
| `DELETE` | `/api/v1/states-sold/{id}` | Elimina un estado por ID. |

Request para crear o actualizar un estado:

```json
{
  "nameState": "PAGADO"
}
```

Response:

```json
{
  "idStateSold": 1,
  "nameState": "PAGADO"
}
```

### Productos Vendidos

Base URL: `/api/v1/products-sold`

| Metodo | Ruta | Descripcion |
| --- | --- | --- |
| `GET` | `/api/v1/products-sold` | Lista todas las ventas. Permite filtrar por usuario con `?idUser={id}`. |
| `GET` | `/api/v1/products-sold/{id}` | Obtiene una venta por ID. |
| `POST` | `/api/v1/products-sold` | Compra varios productos y descuenta stock mediante Products. |
| `POST` | `/api/v1/products-sold/{id}/payment/accept` | Marca PAGADO y asigna el producto al usuario. |
| `POST` | `/api/v1/products-sold/{id}/payment/reject` | Marca CANCELADO y publica la devolucion de stock. |
| `PUT` | `/api/v1/products-sold/{id}` | Actualiza una venta existente. |
| `PATCH` | `/api/v1/products-sold/{id}/state/{idStateSold}` | Cambia solo el estado de una venta. |
| `DELETE` | `/api/v1/products-sold/{id}` | Elimina una venta por ID. |

Request para comprar uno o varios productos (`POST`):

```json
{
  "idUser": 5,
  "idStateSold": 1,
  "products": [
    { "idProducts": 10, "numberSold": 2 },
    { "idProducts": 20, "numberSold": 1 }
  ]
}
```

`idStateSold` debe corresponder a `PENDIENTE`. `products` es obligatorio y no puede estar vacio ni contener elementos nulos. Los IDs deben ser positivos y cada cantidad debe ser mayor a cero. No se permiten IDs de producto repetidos: use `numberSold` para la cantidad total de cada producto.

El `POST` responde `200 OK` con un arreglo de ventas, en el mismo orden que `products`, cada una con su propio `idProductsSold`. Para comprar un solo producto, envie un arreglo de un elemento. El formato anterior con `idProducts` y `numberSold` en la raiz se reemplaza por este contrato para crear compras; `PUT` conserva el formato anterior.

Se valida y descuenta el stock de cada producto antes de guardar. Todas las ventas y sus eventos se guardan en una sola transaccion: si falla un producto o el registro en base de datos, no se guardan ventas parciales y se solicita la devolucion asincrona de los descuentos intentados. Esto incluye la solicitud fallida, porque un timeout puede ocurrir despues de descontar stock. Si no se puede registrar alguna compensacion, el error queda en los logs para su recuperacion.

El pago se acepta o rechaza por cada `idProductsSold` usando los endpoints existentes; no se crea un pedido agrupado ni un pago unico para el conjunto.

Ejemplo de respuesta del `POST`:

```json
[
  {
    "idProductsSold": 100,
    "idProducts": 10,
    "idUser": 5,
    "numberSold": 2,
    "createDate": "2026-10-22T10:30:00",
    "stateSold": { "idStateSold": 1, "nameState": "PENDIENTE" }
  },
  {
    "idProductsSold": 101,
    "idProducts": 20,
    "idUser": 5,
    "numberSold": 1,
    "createDate": "2026-10-22T10:30:00",
    "stateSold": { "idStateSold": 1, "nameState": "PENDIENTE" }
  }
]
```

Request para actualizar una venta (`PUT`):

```json
{
  "idProducts": 10,
  "idUser": 5,
  "numberSold": 2,
  "idStateSold": 1
}
```

Response de `PUT` o de la consulta por ID:

```json
{
  "idProductsSold": 1,
  "idProducts": 10,
  "idUser": 5,
  "numberSold": 2,
  "createDate": "2026-10-06T10:30:00",
  "stateSold": {
    "idStateSold": 1,
    "nameState": "PENDIENTE"
  }
}
```

## Validaciones y Errores

- `PurchaseRequest` requiere `idUser`, `idStateSold` y una lista `products` con `idProducts` y `numberSold` por producto.
- `ProductsSoldRequest` (actualizacion) requiere `idProducts`, `idUser`, `numberSold` e `idStateSold`.
- `numberSold` debe ser mayor a `0`.
- `StateSoldRequest` requiere `nameState` con un maximo de `100` caracteres.
- No se permiten estados duplicados por nombre, ignorando mayusculas y minusculas.
- Si un recurso no existe, la API responde `404 Not Found`.
- Si hay datos invalidos, la API responde `400 Bad Request`.
- Si hay duplicados o restricciones de integridad, la API responde `409 Conflict`.

Formato general de error:

```json
{
  "timestamp": "2026-10-06T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Datos de entrada invalidos",
  "path": "/api/v1/products-sold",
  "validationErrors": {
    "numberSold": "La cantidad vendida debe ser mayor a 0"
  }
}
```

## Modelo de Datos

### `state_sold`

| Campo | Tipo | Descripcion |
| --- | --- | --- |
| `id_state_sold` | `Long` | Identificador del estado. |
| `name_state` | `String` | Nombre del estado de venta. Es unico. |

### `products_sold`

| Campo | Tipo | Descripcion |
| --- | --- | --- |
| `id_products_sold` | `Long` | Identificador de la venta. |
| `id_products` | `Long` | Referencia al producto vendido. |
| `id_user` | `Long` | Referencia al usuario que compra. |
| `number_sold` | `Integer` | Cantidad vendida. |
| `create_date` | `LocalDateTime` | Fecha de creacion asignada automaticamente. |
| `id_state_sold` | `Long` | Estado asociado a la venta. |

## Estructura del Proyecto

```text
src/main/java/pe/edu/cibertec/platonmicroservicepayments
|-- config        # Carga de datos iniciales
|-- controller    # Endpoints REST
|-- dto           # Objetos de entrada, salida y errores
|-- exception     # Manejo global de excepciones
|-- mapper        # Conversion entre entidades y DTOs
|-- model         # Entidades JPA
|-- repository    # Repositorios Spring Data JPA
`-- service       # Contratos e implementaciones de negocio
```
