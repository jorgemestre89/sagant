# Sagant Notifications

Servicio REST para registrar y procesar notificaciones de forma asíncrona, con persistencia en PostgreSQL, autenticación mediante Keycloak y reintentos automáticos ante fallos de entrega.

## 1. Tecnologías

- Java 17.0.2
- Spring Boot 3.5.16
- Gradle 8.6
- Spring Data JPA e Hibernate
- PostgreSQL 17
- Flyway
- Spring Security y JWT
- Swagger/OpenApi
- Keycloak 26.8.1
- Docker Compose
- JUnit y Testcontainers

## 2. Arquitectura

El proyecto está dividido en tres módulos Gradle:

- **shared:** modelo y componentes compartidos.
- **api:** expone la API REST, valida las solicitudes, verifica la autenticación y registra las notificaciones.
- **worker:** busca notificaciones pendientes y las procesa de forma asíncrona.

La solución utiliza PostgreSQL como mecanismo de persistencia y coordinación entre API y worker. No requiere un broker externo de mensajes.

### Flujo de procesamiento

1. El consumidor obtiene un token JWT de Keycloak.
2. Envía una solicitud `POST /api/notifications` con el token y una clave de idempotencia.
3. La API valida la solicitud y registra la notificación con estado `PENDING`.
4. El worker reclama notificaciones pendientes utilizando bloqueos de base de datos para evitar que varios procesos reclamen simultáneamente el mismo registro.
5. El worker selecciona el dispatcher correspondiente al canal.
6. Si la entrega tiene éxito, la notificación pasa a `SENT`.
7. Si falla, se programa un reintento o se marca como `FAILED` al alcanzar el máximo de intentos.
8. Un proceso de recuperación permite volver a procesar notificaciones que quedaron en `PROCESSING` tras una interrupción.

## 3. Canales disponibles

### LOG

Registra un evento estructurado en los logs de la aplicación. El contenido del cuerpo de la notificación no se incluye en el evento de log, para reducir la exposición de información potencialmente sensible.

### SERVICE

Envía una solicitud HTTP `POST` a la URL indicada para la notificación. Para las pruebas locales se incluye un servicio simulado en Python.

El campo `serviceUrl` debe apuntar a un endpoint accesible desde el entorno donde se ejecuta el worker:

- Worker dentro de Docker: `http://service-mock:8090/notifications`
- Worker ejecutado desde IntelliJ: `http://localhost:8090/notifications`

El servicio simulado permite probar el despacho sin depender de un sistema externo real.

### EMAIL

No se implementó en esta versión porque se priorizaron dos canales que permiten demostrar los aspectos fundamentales del sistema: el registro estructurado de eventos y la integración HTTP con un servicio externo.

La entrega de correos normalmente requiere configurar un proveedor SMTP o una API de correo, gestionar credenciales, definir el comportamiento ante errores específicos del proveedor y contemplar aspectos adicionales como límites de envío y tratamiento de rebotes.

Dado el alcance y el tiempo disponible para el ejercicio, se consideró más conveniente concentrar el esfuerzo en la persistencia, el procesamiento asíncrono, la idempotencia, los reintentos y la recuperación ante fallos.

La arquitectura basada en dispatchers permite incorporar EMAIL en una evolución futura sin tener que modificar el flujo principal de procesamiento de notificaciones.

## 4. Requisitos previos

Para ejecutar el proyecto localmente se necesita:

- Git.
- JDK 17.
- Docker Desktop con Docker Compose.
- Opcionalmente, IntelliJ IDEA para ejecutar y depurar los módulos por separado.

## 5. Configuración de variables de entorno

El repositorio incluye `.env.example` con los nombres de las variables necesarias para inicializar Keycloak, PostgreSQL y PgAdmin:

```dotenv
USER_POSTGRE_API_DB=sagant
PWD_POSTGRE_API_DB=sagant_dev

KEYCLOAK_ADMIN_USERNAME=kcadmin
KEYCLOAK_ADMIN_PASSWORD=change-me
KEYCLOAK_DB_PASSWORD=change-me

EMAIL_PG_ADMIN=change-me
PWD_PG_ADMIN=change-me

NOTIFICATIONS_SERVICE_URL=http://service-mock:8090/notifications

```

Crear el archivo local a partir del ejemplo:

```powershell
Copy-Item .env.example .env
```

Editar `.env` y sustituir los valores de ejemplo por credenciales locales propias.

PostgreSQL de la aplicación tiene credenciales de desarrollo para los test. No deben reutilizarse como credenciales de producción. 

## 6. Ejecución con Docker Compose

Iniciar DockerDesktop, luego desde la raíz del repositorio:

```powershell
docker compose up -d postgres
docker compose ps
.\gradlew.bat clean test
docker compose config --quiet
docker compose up --build -d
```

Comprobar los servicios:

```powershell
docker compose ps
docker compose logs -f api worker
```

Docker Compose inicia PostgreSQL, la API, el worker, el servicio simulado, pgAdmin, la base de datos de Keycloak y Keycloak. 


La base de datos de la aplicación utiliza Flyway para aplicar las migraciones. El worker utiliza el esquema existente y no ejecuta las migraciones.

### Servicios y puertos locales

| Servicio | URL o puerto |
|---|---|
| API | `http://localhost:8080` |
| Worker / Actuator | `http://localhost:8081` |
| Keycloak | `http://localhost:8180` |
| pgAdmin | `http://localhost:5050` |
| PostgreSQL de la aplicación | `localhost:5432` |
| PostgreSQL de Keycloak | `localhost:5433` |
| Servicio simulado | `http://localhost:8090` |

Al registrar la conexión con PostgreSQL desde pgAdmin, utilizar `postgres` como host, `5432` como puerto, `sagant` como base de datos y las credenciales 

configuradas en el `.env` para acceder a la base de datos de PostgreSQL.

```dotenv
USER_POSTGRE_API_DB=change-me
PWD_POSTGRE_API_DB=change-me
```


Para detener los servicios:

```powershell
docker compose down
```

Para eliminar también los volúmenes persistentes y sus datos:

```powershell
docker compose down -v
```

**Advertencia:** `down -v` elimina los datos persistidos de las bases de datos. Utilizarlo únicamente cuando se quiera reiniciar el entorno desde cero.

## 7. Configuración de Keycloak

Keycloak se utiliza como proveedor de identidad para autenticar las solicitudes a la API mediante tokens JWT.

El entorno Docker levanta Keycloak y su base de datos PostgreSQL independiente de la de la API REST, pero la configuración del realm y del cliente se realiza manualmente desde la consola de administración.

### 7.1. Iniciar sesión

Acceder a `http://localhost:8180` y utilizar las credenciales administrativas definidas en el archivo `.env`.

### 7.2. Crear el realm

Crear un realm con el nombre exacto `Sagant`.

El nombre debe respetar las mayúsculas y minúsculas, ya que la API utiliza el emisor:

```text
http://localhost:8180/realms/Sagant
```

### 7.3. Crear el cliente

Para obtener tokens desde Postman, crear un cliente, por ejemplo `sagant-postman` como Client ID y `Sagant Postman` como nombre, además con estas características:

- Habilitar la autenticación del cliente (*Client authentication*).
- Habilitar *Service accounts roles*.
- Consultar y guardar el secreto generado para el cliente (*Clients → sagant-postman → Credentials → Client secret*).

La configuración exacta de los permisos y roles debe ser compatible con las reglas de autorización configuradas en la aplicación.

### 7.4. Obtener un token

Solicitar un token utilizando el flujo *Client Credentials*:

```http
POST http://localhost:8180/realms/Sagant/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
```

Parámetros del formulario:

```text
grant_type=client_credentials
client_id=sagant-postman
client_secret=<secreto-del-cliente>
```

La respuesta contiene un `access_token`, que debe enviarse en las solicitudes a la API mediante:

```http
Authorization: Bearer <access_token>
```

### 7.5. Configuración local y Docker

La API utiliza `SAGANT_SECURITY_ISSUER_URI` para validar el emisor del token y `SAGANT_SECURITY_JWK_SET_URI` para obtener las claves públicas con las que verifica la firma JWT.

En Docker Compose, la API accede a las claves mediante el nombre interno `keycloak`, mientras que el emisor se configura con la URL accesible desde el entorno local.

Esta configuración está pensada para desarrollo. En producción se deben utilizar direcciones HTTPS y una configuración de seguridad adecuada para el entorno de despliegue.

**Nota:** la creación del realm y del cliente debe repetirse al inicializar un entorno de Keycloak nuevo, salvo que se incorpore posteriormente un mecanismo de provisión automatizada.


## 8. Crear una notificación

## Crear una notificación

La API expone el endpoint:

```http
POST http://localhost:8080/api/notifications
```

La solicitud requiere autenticación Bearer y el encabezado `Idempotency-Key`.

### Swagger UI

La API incluye Swagger UI para explorar y probar los endpoints REST desde el navegador, sin necesidad de configurar manualmente cada solicitud en Postman.

Con la aplicación levantada mediante Docker Compose, acceder a:

`http://localhost:8080/swagger-ui/index.html`

La especificación OpenAPI está disponible en:

`http://localhost:8080/v3/api-docs`

Para probar los endpoints protegidos, es necesario obtener previamente un token JWT de Keycloak, siguiendo los pasos de la sección **7. Configuración de Keycloak**.

En Swagger UI:

1. Abrir el endpoint que se desea probar `POST /api/notifications`.
2. Seleccionar **Try it out**.
3. Introducir el cuerpo JSON de la solicitud con los datos de la notificación.
4. Enviar la solicitud incluyendo el encabezado `Idempotency-Key`.
5. Si el endpoint requiere autenticación, utilizar **Authorize** para configurar el token Bearer obtenido de Keycloak.

La API devuelve `201 Created` cuando la notificación se registra correctamente. Esto confirma su persistencia; el procesamiento y la entrega se realizan de forma asíncrona.


### DTO de entrada: `NotificationRequestDTO`

| Campo | Tipo | Descripción |
|---|---|---|
| `recipient` | String / email | Destinatario de la notificación. |
| `channel` | String | Canal de entrega, por ejemplo `LOG` o `SERVICE`. |
| `subject` | String | Asunto de la notificación. |
| `body` | String | Contenido de la notificación. |
| `priority` | String | Prioridad, por ejemplo `LOW`, `MEDIUM` o `HIGH`. |
| `metadata` | Object | Datos adicionales asociados a la notificación. |
| `serviceUrl` | String | URL del destino HTTP para el canal `SERVICE`. Si el canal es `LOG` no se toma en cuenta este attr|

`recipient`, `subject` y `body` deben contener valores válidos. La validación del resto de los campos depende de las restricciones configuradas en el DTO.

### Ejemplo: canal LOG

```http
POST /api/notifications
Authorization: Bearer <access_token>
Content-Type: application/json
Idempotency-Key: prueba-log-001
```

```json
{
  "recipient": "usuario@example.com",
  "channel": "LOG",
  "subject": "Confirmación de operación",
  "body": "La operación se completó correctamente",
  "priority": "LOW",
  "metadata": {
    "origen": "sistema-operaciones"
  }
}
```

### Ejemplo: canal SERVICE

```http
POST /api/notifications
Authorization: Bearer <access_token>
Content-Type: application/json
Idempotency-Key: prueba-service-001
```

```json
{
  "recipient": "usuario@example.com",
  "channel": "SERVICE",
  "subject": "Confirmación de operación",
  "body": "La operación se completó correctamente",
  "priority": "LOW",
  "metadata": {
    "origen": "sistema-operaciones"
  },
  "serviceUrl": "http://service-mock:8090/notifications"
}
```

La URL de `serviceUrl` debe ser accesible desde el worker. Cuando el worker se ejecuta dentro de Docker Compose, se utiliza `service-mock` como nombre de host. 

Si se ejecuta desde IntelliJ, se debe utilizar una dirección accesible desde el equipo, por ejemplo `http://localhost:8090/notifications`.

### Respuesta

La API devuelve `201 Created` cuando la notificación se registra correctamente.

Ejemplo de respuesta:

```json
{
  "idNotification": 130,
  "recipient": "usuario@example.com",
  "channel": "SERVICE",
  "subject": "Confirmación de operación",
  "body": "La operación se completó correctamente",
  "status": "PENDING",
  "priority": "LOW",
  "metadata": {
    "origen": "sistema-operaciones"
  },
  "createdAt": "2026-10-28T00:39:17.019215729Z",
  "serviceUrl": "http://service-mock:8090/notifications"
}
```

El estado inicial `PENDING` indica que la notificación fue registrada y está pendiente de procesamiento. La entrega se realiza de forma asíncrona, por lo que la respuesta HTTP no implica que el destinatario ya haya recibido la notificación.


## 9. Idempotencia y validaciones

La API exige el encabezado `Idempotency-Key` para evitar crear notificaciones duplicadas cuando un consumidor reintenta una solicitud.

El comportamiento esperado es:

- Misma clave y misma solicitud: se devuelve la notificación ya registrada.
- Misma clave y solicitud diferente: se devuelve `409 Conflict`.
- Clave ausente: se rechaza la solicitud.
- Token ausente o inválido: se rechaza la autenticación.
- Solicitud inválida: se devuelve un error de validación.

La idempotencia de la creación evita duplicar registros por reintentos del consumidor. No garantiza, por sí sola, que un sistema externo no reciba una entrega duplicada si el worker se interrumpe después de entregar la notificación y antes de persistir el resultado.

## 10. Reintentos y recuperación

El worker procesa notificaciones de forma asíncrona mediante un scheduler y un executor.

Configuración predeterminada:

| Parámetro | Valor |Es configurable a través de propertie
|---|---|
| Intervalo de procesamiento | 2 segundos |SI
| Tamaño del lote | 4 notificaciones |NO
| Máximo de intentos | 3 |SI
| Retraso inicial de reintento | 10 segundos |SI
| Intervalo de recuperación | 1 minuto |SI
| Timeout de procesamiento para recuperación | 5 minutos |NO

Los reintentos utilizan un retraso exponencial. Si se agotan los intentos configurados, la notificación termina en `FAILED` y se conserva información del error para facilitar el diagnóstico.

Las notificaciones en estado `PROCESSING` que permanecen bloqueadas más allá del timeout pueden recuperarse para permitir un nuevo procesamiento.

## 11. Observabilidad

Los endpoints de Actuator habilitados son:

- API: `/actuator/health` y `/actuator/info`.
- Worker: `/actuator/health`, `/actuator/info` y `/actuator/metrics`.

Ejemplos:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8081/actuator/health
GET http://localhost:8081/actuator/metrics
```

Los logs permiten seguir el procesamiento y diagnosticar fallos. El worker también expone métricas relacionadas con el procesamiento, los reintentos, los fallos, los rechazos y la recuperación, cuando están registradas por la aplicación.

```metrics
- /actuator/metrics/sagant.notifications.retry.scheduled
- /actuator/metrics/sagant.notifications.failed
- /actuator/metrics/sagant.notifications.rejected
- /actuator/metrics/sagant.notifications.recovered
```

Para consultar los logs:

```powershell
docker compose logs -f api
docker compose logs -f worker
docker compose logs -f service-mock
```

## 12. Pruebas

Ejecutar la suite completa:

```powershell
.\gradlew.bat clean test
```

Las pruebas cubren la lógica de negocio, la API y los escenarios de procesamiento implementados. Las pruebas de integración que utilizan Testcontainers necesitan que Docker esté disponible.


## 13. Decisiones de diseño y trade-offs

- **PostgreSQL como cola persistente:** simplifica la infraestructura y permite conservar el estado de las notificaciones sin incorporar un broker adicional.
- **Bloqueos con `SKIP LOCKED`:** permiten reclamar registros pendientes sin que varios workers procesen simultáneamente el mismo registro en la fase de selección.
- **Procesamiento asíncrono:** desacopla la recepción HTTP de la entrega efectiva.
- **Idempotencia en la API:** protege contra duplicados provocados por reintentos del consumidor.
- **Reintentos con backoff:** reducen la frecuencia de nuevas llamadas a un destino que está fallando.
- **Dispatcher por canal:** separa la lógica de entrega de la lógica de procesamiento y facilita incorporar canales adicionales.
- **Servicio simulado:** permite probar la integración HTTP sin depender de un proveedor externo.
- **Sin broker externo:** reduce la complejidad de despliegue para el alcance del ejercicio, a cambio de utilizar la base de datos para coordinar el trabajo.

### Limitaciones conocidas

- La entrega externa no garantiza semántica *exactly once*. Un fallo entre la entrega y la actualización del estado puede ocasionar una entrega repetida.
- La disponibilidad de SERVICE depende del destino HTTP configurado.
- LOG y SERVICE están implementados; EMAIL queda fuera del alcance actual.
- La configuración de desarrollo de Docker Compose no debe considerarse una configuración de producción.
- El mecanismo de reintentos y recuperación no sustituye una estrategia completa de alta disponibilidad, alertas y operación.

## 14. Posible adaptación a Jakarta EE

La solución podría adaptarse a Jakarta EE y desplegarse como un WAR en un servidor como WildFly.

| Solución actual | Alternativa Jakarta EE |
|---|---|
| Spring MVC / REST Controllers | Jakarta REST (JAX-RS) |
| Spring Dependency Injection | CDI |
| Spring Data JPA | Jakarta Persistence |
| Configuración de datasource de Spring | Datasource gestionado por el servidor y configurado mediante JNDI |
| Executor y scheduler de la aplicación | `ManagedExecutorService` y `ManagedScheduledExecutorService` |
| Spring Security Resource Server | Integración OIDC/JWT compatible con Jakarta EE y Keycloak |
| Flyway | Flyway integrado al despliegue o estrategia de migraciones administrada |

Para un sistema de mayor escala, también podría evaluarse Jakarta Messaging (JMS) con un proveedor compatible. Esto permitiría separar la recepción REST del procesamiento mediante un broker, aunque agregaría infraestructura y complejidad operativa.

La adaptación requeriría revisar el manejo de transacciones, la concurrencia, la configuración de seguridad y el ciclo de vida de los componentes para delegar esas responsabilidades al contenedor cuando corresponda.

