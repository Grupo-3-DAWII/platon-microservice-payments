# platon-microservice-payments

Microservicio de pagos y ventas. Registra compras (con uno o varios productos), confirma o rechaza pagos y publica los eventos de compra por RabbitMQ.

## Requisitos

- Java 25 (Maven no hace falta, el proyecto incluye `mvnw`) o Docker con Compose.
- Proyecto `DATABASE` levantado (PostgreSQL y RabbitMQ).
- `platon-config-server` en ejecución: si no está disponible, el microservicio no inicia.
- `platon-microservice-products` en ejecución: valida el stock de cada compra, así que sin él las compras no se completan.

## Configuración

Toda la configuración (puerto, conexiones, RabbitMQ, timeout, Eureka) se carga desde `platon-config-server/config-repo/platon-microservice-payments.yml`. El `application.yml` local solo define el nombre del servicio y la URL del Config Server (`CONFIG_SERVER_URL`, por defecto `http://localhost:8888`).

Los valores se pueden sobrescribir con variables de entorno (`SERVER_PORT`, `RABBITMQ_*`, `PURCHASE_VALIDATION_TIMEOUT_MS`, etc.). Eureka está desactivado por defecto; se habilita con `EUREKA_ENABLED=true`.

## Ejecutar

```bash
# Local (Linux/macOS)
./mvnw spring-boot:run

# Local (Windows)
.\mvnw.cmd spring-boot:run

# Docker
docker compose up --build
```

El servicio queda en <http://localhost:8082>.

## Swagger

Con el servicio en ejecución, abre:

- Interfaz: <http://localhost:8082/swagger-ui.html>
- Especificación OpenAPI (JSON): <http://localhost:8082/v3/api-docs>

Lla validación (JWT) la hace el `platon-api-gateway`.

## Flujo de una compra

1. `POST /api/products-sold` crea la compra con el estado `PENDIENTE`. Payments pide a Products validar y descontar el stock por RabbitMQ y espera la respuesta.
2. Solo si la validación es positiva se registra la compra y se publica el evento `purchase.created`.
3. El pago se decide para toda la venta:
    - `POST /api/products-sold/{id}/payment/accept` marca `PAGADO` y asigna los productos al usuario. Requiere el cuerpo `{ "amount": 130.00 }`, que debe coincidir con el total de la venta.
    - `POST /api/products-sold/{id}/payment/reject` marca `CANCELADO` y publica `payment.rejected`; Products devuelve el stock de forma asíncrona.

Ejemplo de creación:

```bash
curl -X POST http://localhost:8082/api/products-sold \
  -H "Content-Type: application/json" \
  -d '{"idUser":2,"idStateSold":1,"totalAmount":130.00,"products":[{"idProducts":10,"numberSold":2},{"idProducts":20,"numberSold":1}]}'
```

Reglas a tener en cuenta:

- `idStateSold` debe corresponder a `PENDIENTE`, y `products` no puede estar vacío ni repetir IDs.
- `totalAmount` debe ser igual a la suma de precio de venta × cantidad de cada producto; si no, responde `409` y no descuenta stock.
- Repetir la misma decisión de pago no duplica nada; cambiar una decisión final responde `409`.
- Las rutas genéricas de editar, cambiar estado o eliminar no sirven para saltarse este flujo en ventas con descuento de stock.

| Código | Cuándo ocurre |
| --- | --- |
| `200` | Compra registrada o pago procesado |
| `400` | Datos inválidos |
| `404` | Recurso inexistente |
| `409` | Products rechaza la disponibilidad, el importe no coincide o la decisión de pago cambia |
| `503` | Falla la comunicación con Products o su respuesta es inválida |
| `504` | Products no responde en 10 segundos |

## Eventos (RabbitMQ)

Exchange `platon.purchase.events` (topic). Las colas y bindings fijos los crea el proyecto `DATABASE`; Payments solo crea una cola temporal propia para recibir las respuestas de Products.

| Routing key | Dirección | Uso |
| --- | --- | --- |
| `purchase.requested` | Payments → Products | Pide validar y descontar stock |
| `purchase.availability.checked.<uuid>` | Products → Payments | Respuesta a la validación |
| `purchase.created` | Payments → otros consumidores | Compra registrada |
| `payment.rejected` | Payments → Products | Devolución de stock |

`purchase.created` y `payment.rejected` se publican en segundo plano cada 5 segundos, con entrega **al menos una vez**: los consumidores deben deduplicar por `eventId`.

## Orden recomendado

1. `platon-eureka-server`
2. `platon-config-server`
3. Microservicios
4. `platon-api-gateway`