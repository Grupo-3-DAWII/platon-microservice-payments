# platon-microservice-payments

Microservicio de pagos y ventas del sistema de gestion de libreria. Expone una API REST para registrar productos vendidos, consultar ventas por usuario y administrar los estados de venta.

## Tecnologias

- Java 25
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Cloud Netflix Eureka Client
- PostgreSQL
- Lombok
- Maven Wrapper

## Configuracion

La configuracion principal esta en `src/main/resources/application.properties`.

```properties
spring.application.name=platon-microservice-payments
server.port=8082

spring.datasource.url=jdbc:postgresql://localhost:5432/SgestionLibreria_db?currentSchema=sgestionlibreria_payment
spring.datasource.username=SgestionLibreria
spring.datasource.password=123456
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.default_schema=sgestionlibreria_payment
spring.jpa.open-in-view=false
```

El servicio espera una base de datos PostgreSQL disponible en `localhost:5432`, con el esquema `sgestionlibreria_payment` y las tablas ya creadas. Hibernate esta configurado con `ddl-auto=validate`, por lo que valida el modelo contra la base de datos, pero no crea ni actualiza tablas automaticamente.

## Ejecucion

Desde la raiz del proyecto:

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
| `POST` | `/api/v1/products-sold` | Registra una nueva venta. |
| `PUT` | `/api/v1/products-sold/{id}` | Actualiza una venta existente. |
| `PATCH` | `/api/v1/products-sold/{id}/state/{idStateSold}` | Cambia solo el estado de una venta. |
| `DELETE` | `/api/v1/products-sold/{id}` | Elimina una venta por ID. |

Request para crear o actualizar una venta:

```json
{
  "idProducts": 10,
  "idUser": 5,
  "numberSold": 2,
  "idStateSold": 1
}
```

Response:

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

- `ProductsSoldRequest` requiere `idProducts`, `idUser`, `numberSold` e `idStateSold`.
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
