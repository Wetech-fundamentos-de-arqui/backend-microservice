# WeRide — backend de microservicios

Backend de WeRide (Java 24, Spring Boot 3.5.7, Maven, MySQL 8). Evoluciona el monolito original hacia servicios desplegables por separado. Plan completo: [docs/architecture/Plan-Microservicios-WeRide.md](docs/architecture/Plan-Microservicios-WeRide.md). Decisiones: [docs/architecture/decisions](docs/architecture/decisions).

## Estado de la migración

| Módulo | Estado |
|---|---|
| `weride-shared` | Hecho. Librería técnica: seguridad (JWT RS256, identidad entre servicios), naming strategy de JPA, OpenAPI y `AuditableAbstractAggregateRoot`. Sin dominio. |
| `api-gateway` | Hecho. Rutas, CORS, verificación del JWT y comprobación de sesión contra IAM. |
| `services/iam-service` | Hecho. Cuentas, sign-in/sign-up, sesiones, JWT y baja de cuenta (parte de IAM del ADR-008). |
| `services/profile-service`, `garage-service`, `location-service`, `booking-service`, `ride-service`, `plan-service`, `notification-service` | **Pendientes.** El módulo existe (solo `pom.xml`), sin código. |
| `src/` (raíz) | Código del monolito original. Es la referencia de comportamiento; **ya no compila solo** porque sus clases compartidas se movieron a `weride-shared`. No forma parte del build (no es un módulo del `pom.xml` raíz). |

## Estructura

```
pom.xml                 POM padre (weride-backend:2.0.0), agrega los módulos
weride-shared/          librería compartida (jar, no ejecutable)
api-gateway/            gateway (Spring Cloud Gateway WebFlux)
services/<x>-service/   un servicio de negocio por carpeta
contracts/openapi/      contratos OpenAPI (por completar)
contracts/events/       contratos de eventos (por completar)
docs/architecture/      plan y ADRs (decisions/)
docs/runbooks/          runbooks (por completar)
docker/mysql/           script que crea esquemas y usuarios
docker-compose.yml, Dockerfile, .env.example
src/                    monolito original (referencia)
```

## Servicios, dueños y puertos (ADR-010)

| Servicio | Puerto | Dueño | Esquema MySQL |
|---|---|---|---|
| api-gateway | 8080 | Samuel | — |
| iam-service | 8081 | Samuel | `weride_iam` |
| profile-service | 8082 | Angel | `weride_profile` |
| garage-service | 8083 | Angel | `weride_garage` |
| location-service | 8084 | Jareth | `weride_location` |
| booking-service | 8085 | Jareth | `weride_booking` |
| ride-service | 8086 | Wanderer | `weride_ride` |
| plan-service | 8087 | Asto | `weride_plan` |
| notification-service | 8088 | Asto | `weride_notification` |

MySQL: host `3307` → contenedor `3306`. Un esquema y un usuario por servicio (mismo nombre que el esquema), con permisos solo sobre su esquema.

## Requisitos

- Java 24 o superior y Maven 3.9.
- Docker con Compose v2.
- `openssl` para generar las claves JWT locales.

## Compilar y probar

```bash
mvn verify                                   # todo el reactor
mvn -pl services/iam-service -am verify      # un módulo y lo que necesita (weride-shared)
mvn -pl api-gateway -am verify
```

## Claves JWT locales (fuera del repo)

IAM firma con la clave privada; el gateway y los servicios verifican con la pública (ADR-004). Los PEM viven en `secrets/`, que está en `.gitignore`.

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem   # PKCS#8: "BEGIN PRIVATE KEY"
openssl rsa -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem                 # "BEGIN PUBLIC KEY"
```

## Levantar con Docker Compose

```bash
cp .env.example .env        # reemplaza cada change-me; .env no se versiona
# (las claves de la sección anterior deben existir en ./secrets)
docker compose up --build -d
docker compose ps           # mysql, iam-service y api-gateway deben quedar "healthy"
docker compose logs -f iam-service
docker compose down         # añade -v para borrar también la base de datos
```

Por defecto levanta `mysql`, `iam-service` y `api-gateway`. Los demás servicios están declarados con un `profile` (p. ej. `docker compose --profile booking up`) y no arrancan hasta que su módulo tenga código; al implementar el tuyo, borra la línea `profiles:` de tu servicio en `docker-compose.yml` y agrega los `INTERNAL_SECRET_*` que necesite.

### Probar con Swagger

Cada servicio publica su Swagger UI en su propio puerto. El gateway **no** lo enruta.

1. Abre IAM: <http://localhost:8081/swagger-ui.html> (JSON del contrato en `/v3/api-docs`).
2. `POST /api/v1/authentication/sign-in` → *Try it out* con la cuenta demo `{"username":"demo@weride.test","password":"Demo1234!"}`. Copia el valor de `token` de la respuesta.
3. Pulsa **Authorize** (arriba a la derecha), pega el token (solo el token, sin `Bearer `) y confirma.
4. Prueba los endpoints protegidos: `GET /api/v1/accounts/{accountId}` (usa el `id` de la respuesta de sign-in), `POST /api/v1/accounts/me/sessions/logout`, etc.
5. Para comprobar el gateway usa `curl` contra `http://localhost:8080` (mismas rutas `/api/v1/...`, con `Authorization: Bearer <token>`).

Las rutas `/internal/v1/**` exigen `X-Service-Name` y `X-Internal-Token`; Swagger no las envía, pruébalas con `curl`.

La cuenta `demo@weride.test` / `Demo1234!` la crea la migración `V2__seed_demo_account.sql` de IAM y es **solo para uso local**.

## Gateway

Mapa de rutas (variables de entorno con default `http://localhost:<puerto>`; en Compose apuntan al nombre del servicio):

| Rutas | Variable | Servicio |
|---|---|---|
| `/api/v1/authentication/**`, `/api/v1/accounts/**` | `IAM_BASE_URL` | iam |
| `/api/v1/profiles/**` | `PROFILE_BASE_URL` | profile |
| `/api/v1/vehicles/**`, `/api/v1/favorites/**` | `GARAGE_BASE_URL` | garage |
| `/api/v1/location/**` | `LOCATION_BASE_URL` | location |
| `/api/v1/bookings/**`, `/api/v1/bookingDrafts/**`, `/api/v1/payments/**` | `BOOKING_BASE_URL` | booking |
| `/api/v1/trips/**`, `/api/v1/travel-history/**`, `/api/v1/unlockRequests/**`, `/api/v1/problem-reports/**` | `RIDE_BASE_URL` | ride |
| `/api/v1/plans/**` | `PLAN_BASE_URL` | plan |
| `/api/v1/notifications/**` | `NOTIFICATION_BASE_URL` | notification |

- **Públicas:** `/api/v1/authentication/**` y `/actuator/health`. Todo lo demás exige un JWT válido.
- **`/internal/**` está bloqueado:** nunca se enruta.
- **CORS** se resuelve solo en el gateway (los servicios lo tienen desactivado). Lista en `CORS_ALLOWED_ORIGINS`, separada por comas y sin `/` final; el valor por defecto está en `api-gateway/src/main/resources/application.yml` y en `.env.example`.
- **JWT:** el gateway verifica firma RS256, `iss=weride-iam` y `aud=weride-api` con la clave pública. Luego comprueba que el `sid` siga activo preguntando a IAM (`GET /internal/v1/sessions/{sid}`) y cachea cada respuesta hasta 30 s.
- **Códigos que produce el gateway:**

| Código | Cuándo |
|---|---|
| 401 | Sin token, token inválido (firma, `iss`, `aud`, expirado) o sesión inactiva (revocada, expirada, cuenta `DELETION_PENDING`) |
| 403 | Cualquier ruta `/internal/**` con token válido |
| 503 | IAM no responde al comprobar la sesión (falla cerrado) o el servicio destino está caído / no existe |
| 504 | El servicio destino no responde en 10 s (`response-timeout`; conexión: 2 s) |

## Cómo crear tu servicio

El módulo ya existe en `services/<nombre>-service` (con su `pom.xml`, padre `../../pom.xml`). Toma `services/iam-service` como ejemplo funcional.

### 1. `pom.xml`

```xml
<dependencies>
    <dependency>
        <groupId>org.example</groupId>
        <artifactId>weride-shared</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>   <!-- /actuator/health para el healthcheck -->
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

`weride-shared` ya trae web, security, data-jpa, validation, springdoc y el resource server OAuth2 (versiones gestionadas por el POM padre). No lleva `spring-boot-maven-plugin` porque es librería.

### 2. Clase principal

Ponla en el paquete `org.example.backendweride` (como `IamServiceApplication`): así el escaneo ya incluye `org.example.backendweride.platform.shared` (configuración de seguridad, OpenAPI, naming strategy).

```java
package org.example.backendweride;

@SpringBootApplication
@EnableJpaAuditing   // obligatorio si alguna entidad extiende AuditableAbstractAggregateRoot
public class BookingServiceApplication {
    public static void main(String[] args) { SpringApplication.run(BookingServiceApplication.class, args); }
}
```

Si prefieres otro paquete, añade `scanBasePackages = {"<tu paquete>", "org.example.backendweride.platform.shared"}`.

`AuditableAbstractAggregateRoot` (`id` Long `IDENTITY`, `createdAt`, `updatedAt`) vive en `weride-shared`; los dueños que la usen no deben copiarla.

### 3. `application.properties` mínimo

```properties
spring.application.name=booking-service
server.port=8085
documentation.application.description=WeRide Booking
documentation.application.version=1.0        # obligatorias: las lee OpenApiConfiguration

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.naming.physical-strategy=org.example.backendweride.platform.shared.infraestructure.persistence.jpa.strategy.SnakeCasePhysicalNamingStrategy

# Verificación del JWT (RS256, solo clave pública)
spring.security.oauth2.resourceserver.jwt.public-key-location=${JWT_PUBLIC_KEY_LOCATION}
spring.security.oauth2.resourceserver.jwt.issuer-uri=weride-iam
spring.security.oauth2.resourceserver.jwt.audiences=weride-api

# Identidad entre servicios (ver sección siguiente)
weride.internal.service-name=booking
weride.internal.callers.iam=${INTERNAL_SECRET_IAM_TO_BOOKING}
weride.internal.callers.ride=${INTERNAL_SECRET_RIDE_TO_BOOKING}

management.endpoints.web.exposure.include=health
```

Notas: el paquete de la naming strategy conserva el typo `infraestructure`. La strategy pasa los nombres a `snake_case` y **pluraliza** los tablas (entidad `Account` → tabla `accounts`); las migraciones Flyway deben usar esos nombres, y `ddl-auto=validate` hará fallar el arranque si no coinciden. Las migraciones van en `src/main/resources/db/migration/V1__...sql`.

### 4. Usuario autenticado

`weride-shared` valida el JWT y expone `AuthenticatedUser(String id, String username)` (`id` = claim `sub`, `username` = `preferred_username`; los IDs externos son strings opacos, ADR-003). En un controlador:

```java
CurrentUser.id(authentication)                         // String; 401 si no hay AuthenticatedUser
CurrentUser.requireSelf(authentication, ownerId)       // 403 si ownerId (String) no es el del token
CurrentUser.requireUsername(authentication, username)  // 403 si no coincide con preferred_username
authentication.getName()                               // el username
```

Cada servicio autoriza propiedad por su cuenta; el gateway no sustituye esa validación. Para desactivar la cadena de seguridad compartida (solo lo hace IAM, que tiene rutas públicas propias) existe `weride.security.shared=false`.

### 5. Rutas internas y llamadas entre servicios

- Los endpoints para otros servicios van bajo `/internal/v1/**`. El `InternalTokenFilter` de `weride-shared` los protege: exige `X-Service-Name` + `X-Internal-Token` y compara el secreto con `weride.internal.callers.<llamante>`. Falta de headers, llamante desconocido o secreto distinto → 401. Si es válido, el principal es `service:<llamante>` (no un `AuthenticatedUser`).
- Hay un secreto por par llamante→receptor (nada de secreto global), definido en `.env.example` como `INTERNAL_SECRET_<LLAMANTE>_TO_<RECEPTOR>`. Es el mismo valor en ambos extremos.
- Para llamar a otro servicio, inyecta el `RestClient.Builder` de Spring Boot: `weride-shared` ya le agrega timeouts de 2 s y `X-Service-Name` (desde `weride.internal.service-name`). Tú pones la URL y el secreto del destino:

```properties
weride.internal.targets.garage.url=${GARAGE_BASE_URL}
weride.internal.targets.garage.secret=${INTERNAL_SECRET_BOOKING_TO_GARAGE}
```

```java
@Bean
RestClient garageClient(RestClient.Builder builder,
                        @Value("${weride.internal.targets.garage.url}") String url,
                        @Value("${weride.internal.targets.garage.secret}") String secret) {
    return builder.baseUrl(url).defaultHeader("X-Internal-Token", secret).build();
}
```

Nunca reenvíes el token del usuario como identidad de servicio. Reintenta solo operaciones idempotentes.

### 6. Datos

Esquema propio `weride_<servicio>` y usuario propio (los crea `docker/mysql/init-schemas.sh`). Sin claves foráneas ni lecturas a esquemas de otros servicios: entre contextos solo viajan IDs opacos y contratos explícitos. Datos demo por seeders/migraciones del propio servicio.

## Contratos pendientes para otros dueños

- **Perfil perezoso (ADR-011):** IAM no llama a Profile al registrar. Profile crea el perfil vacío en el primer `GET` autenticado, con `sub` como `userId`, de forma idempotente.
- **Baja de cuenta (ADR-008):** la parte de IAM está implementada: `POST /api/v1/accounts/me/delete` (valida contraseña) → 202 `{"workflowId"}`. Si Booking está configurado, IAM primero hace `POST /internal/v1/users/{userId}/deletion-lock`: 409 → responde 409 `ACTIVE_BOOKINGS` sin cambios; error o timeout → libera con `DELETE .../deletion-lock` y responde 503. Luego marca la cuenta `DELETION_PENDING`, revoca sesiones y persiste el workflow. Un job cada 60 s llama `DELETE /internal/v1/users/{userId}/personal-data/{workflowId}` a Profile, Garage, Booking, Ride y Notification; solo llama a los que tengan `weride.internal.targets.<x>.url` configurado y deja el paso `PENDING` si no. Termina `DELETED` (y IAM borra solo lo suyo) cuando todos confirman; un 409 tardío de Booking aborta el workflow y reactiva la cuenta. **Cada dueño implementa** el `deletion-lock` (Booking) y el `personal-data` idempotente con tombstone local (Profile, Garage, Booking, Ride, Notification), y cuando existan activa su URL y secreto en la config de IAM (`*_BASE_URL`, `INTERNAL_SECRET_IAM_TO_<X>`; ver el `docker-compose.yml`).
- **Outbox hacia Notification (ADR-005):** Booking y Ride guardan los eventos en una outbox local dentro de la transacción y los publican por `PUT /internal/v1/notification-events/{sourceService}/{eventId}`; Notification deduplica por `(sourceService, eventId)`.
- Contratos internos completos: sección "Contratos internos obligatorios" del [plan](docs/architecture/Plan-Microservicios-WeRide.md).

## Convenciones (plan, sección 4.3)

| Elemento | Convención |
|---|---|
| Carpetas / artefactos Maven | `booking-service`, `api-gateway`; kebab-case |
| Paquetes Java | `org.example.backendweride.platform.<contexto>` |
| Rutas | `/api/v1`, plural, minúsculas, kebab-case; las rutas actuales del frontend no se renombran |
| Rutas internas | `/internal/v1/**` |
| Esquemas / tablas | `weride_<servicio>`; tablas y columnas en `snake_case`, cambios por migración Flyway |
| Variables | `SPRING_DATASOURCE_URL`, `IAM_BASE_URL`, etc.; secretos siempre externos |
| Ramas | `feature/{servicio}/{alcance}`, `fix/{servicio}/{alcance}`, `docs/{alcance}` |
| Commits | Conventional Commits: `feat(booking): ...`, `fix(iam): ...`, `docs(architecture): ...` |
| Eventos | `[contexto].[hecho].v1` en pasado: `ride.started.v1` |

Flujo Git y reglas de PR: sección 5.2 del plan. Los cambios en `weride-shared` son un contrato común: avisa a los demás dueños antes de modificarlo.
