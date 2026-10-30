# WeRide — microservicios locales

Java 24, Spring Boot 3.5.7, Spring Cloud Gateway, Maven y MySQL 8.4. Ocho servicios poseen sus datos y se comunican por REST interno; Angular entra por el Gateway. `src/` conserva el monolito como referencia y no participa del reactor Maven.

## Inicio

Requisitos: Docker Desktop con motor Linux, Compose v2 y Node.js 22 para generar las credenciales locales.

Solo la primera vez, genera las credenciales locales:

```powershell
node scripts/init-local.mjs
```

El inicializador crea `.env` con secretos aleatorios y un par RSA en `secrets/`; conserva los valores existentes y no imprime secretos. Esos archivos no se versionan. Solo IAM recibe la clave privada.

Levanta todo de una vez (MySQL, los ocho servicios, el Gateway y el frontend vecino `../frontend-main/frontend-main`). Compose respeta el orden de arranque, así que no hace falta iniciar cada servicio por separado:

```powershell
docker compose --profile web up --build -d
```

La primera compilación tarda varios minutos. Después, omite `--build` si no cambiaste el código. Comandos habituales:

```powershell
docker compose --profile web ps          # estado; espera a que todos estén healthy
docker compose logs -f booking-service   # logs en vivo de un servicio
docker compose --profile web down        # apaga todo y conserva los datos
```

Sin `--profile web` no se incluye el frontend. Si está en otra carpeta, define `FRONTEND_PATH` en `.env` con su ruta; debe contener su `Dockerfile` y `nginx.conf`. `docker compose down -v` borra el volumen de MySQL y con él todos los datos locales.

Abre [WeRide local](http://localhost:4200). El Gateway está en [localhost:8080](http://localhost:8080). La cuenta demo local es `demo@weride.test` / `Demo1234!`; también puedes registrarte. Las escrituras de catálogo (vehículos, estaciones y planes) requieren el rol `ADMIN`: en local, `admin@weride.test` / `Admin1234!`. El registro abre el perfil con un GET autenticado antes de actualizar su nombre (ADR-011).

| Servicio | Puerto | Datos y responsabilidad |
|---|---:|---|
| api-gateway | 8080 | Rutas, CORS, JWT y estado de sesión |
| iam-service | 8081 | Cuentas, credenciales, sesiones y baja |
| profile-service | 8082 | Perfil y preferencias; creación perezosa |
| garage-service | 8083 | Flota, estado y favoritos |
| location-service | 8084 | Estaciones, coordenadas y horarios |
| booking-service | 8085 | Reservas, disponibilidad, borradores, cotización, rating y pagos simulados |
| ride-service | 8086 | Sesión, ruta, desbloqueos, historial y reportes |
| plan-service | 8087 | Catálogo y beneficios |
| notification-service | 8088 | Bandeja, lectura y recepción idempotente de eventos |
| MySQL | 3307 → 3306 | Esquema y usuario `weride_<servicio>` por servicio |
| frontend (perfil `web`) | 4200 | Angular/Nginx; `/api/` se envía al Gateway |

Los puertos publicados se enlazan a `127.0.0.1`. Cada servicio expone `/actuator/health`, `/swagger-ui.html` y `/v3/api-docs` en su puerto. El Gateway bloquea `/internal/**`.

**Swagger general:** [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) reúne todos los servicios; elige uno en el selector superior. Inicia sesión con `POST /api/v1/authentication/sign-in` en `iam`, pega el `token` en **Authorize** y prueba cualquier endpoint a través del Gateway. Solo se publica con `WERIDE_SWAGGER_ENABLED=true`, que Compose activa para local; por defecto está apagado. Las rutas `/internal/**` aparecen en la documentación, pero el Gateway responde 403.

## Contratos y persistencia

Consultar el [plan](docs/architecture/Plan-Microservicios-WeRide.md), los [ADR](docs/architecture/decisions), las [extensiones locales](docs/architecture/decisions/012-integracion-local.md) y el [índice de contratos](contracts/README.md).

- Se preservan las rutas de Angular, incluyendo `location`, `bookingDrafts` y `unlockRequests`. `PUT /bookings/{id}/start` conserva compatibilidad; POST es un alias.
- Booking coordina inicio, finalización y cancelación. Ride persiste una sesión única por `bookingId`. Los estados `starting`, `completing` y `cancelling` quedan recuperables tras timeouts. Booking conserva una proyección del resultado para sus respuestas e historial. La reubicación del vehículo se reintenta si Garage/Location estaban caídos.
- La reserva se serializa por vehículo, incluyendo la creación inicial de su agenda. `Idempotency-Key`, asociado al usuario, devuelve la misma reserva; otro payload con la misma clave produce 409. Una reserva sin iniciar caduca 15 minutos después del inicio programado o de reservarla si era inmediata.
- Los borradores duran 24 horas y no reservan vehículos. El desbloqueo manual/QR es simulado y no inicia automáticamente el viaje. Los pagos son simulados y no almacenan tarjetas. Plan es catálogo, sin cobros recurrentes.
- Los reportes conservan progreso antes de coordinar mantenimiento y exoneración del primer minuto. Una dependencia caída deja el reporte pendiente para reintento. No hay transacciones distribuidas.
- Flyway posee cada esquema y Hibernate usa `ddl-auto=validate`. No hay entidades, repositorios, SQL ni claves foráneas entre esquemas. Los IDs remotos son strings opacos.

## Seguridad y notificaciones

IAM firma RS256: `sub` es el ID estable, `preferred_username` el nombre de acceso y `role` el rol (`USER` o `ADMIN`). Los servicios verifican JWT y propiedad. El Gateway consulta el `sid`, cachea hasta 30 segundos y responde 503 si IAM no puede comprobarlo. La revocación puede tardar ese TTL en observarse.

Las llamadas `/internal/v1/**` requieren `X-Service-Name` y `X-Internal-Token`, con un secreto distinto por par llamante/receptor y autorización por operación. Los clientes internos tienen timeouts de 2 segundos. `X-Correlation-Id` se propaga sin registrar credenciales.

Booking/Ride guardan eventos en su outbox dentro de la transacción local. Reintentan desde 2 segundos hasta 5 minutos, con máximo 10 intentos. Notification deduplica `(sourceService,eventId)` y detecta cambios de payload. La identidad `ops` puede consultar `/internal/v1/outbox/failed` y reenviar con `POST /internal/v1/outbox/{eventId}/replay`. Véase el [schema](contracts/events/notification-event.v1.schema.json).

La baja valida contraseña, consulta/bloquea reservas, revoca sesiones y conserva un workflow en IAM. Los otros servicios ejecutan su limpieza idempotente con tombstone local; nuevas mutaciones se rechazan. Booking anonimiza historial. IAM reintenta pendientes cada 60 segundos y elimina su cuenta cuando todos confirman.

## Desarrollo y comprobaciones

Fuera de Docker: JDK 24 o superior y Maven 3.9.

```powershell
mvn verify
mvn -pl services/booking-service -am verify
python scripts/verify_local.py --resilience
```

El POM inicia Mockito como agente para evitar depender del autoanexado del JDK. Las regresiones trasladadas están en los módulos; `src/test` permanece como referencia del monolito.

En el frontend vecino: `npm ci`, `npm run build`, `npm test -- --watch=false --browsers=ChromeHeadless` y `npm start`. Puedes configurar `CHROME_BIN` para un Chromium instalado. El proxy de desarrollo y Nginx usan `/api/v1` relativo hacia el Gateway.

El verificador HTTP usa datos de prueba locales y registra sus resultados en `docs/runbooks/local-verification.json`. `--resilience` detiene/restaura Ride y Notification y reinicia Booking y Gateway para comprobar persistencia del outbox y recuperación del proxy. Con `--base http://localhost:4200/api/v1` comprueba los flujos a través del proxy del frontend. El [runbook](docs/runbooks/local-validation.md) registra la evidencia realmente obtenida.

