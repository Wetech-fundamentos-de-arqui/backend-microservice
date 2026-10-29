# Plan de arquitectura e implementación de microservicios — WeRide

**Versión:** 1.0  
**Estado:** Base operativa; decisiones por defecto fijadas para iniciar implementación  
**Alcance:** Plan, reparto y prompts para cinco integrantes. Este documento no modifica el backend.

> No existe un plan perfecto antes de validar límites de dominio, restricciones y tiempo. Este plan ofrece una base concreta, identifica decisiones abiertas y exige contrastar cada cambio con el backend anterior y con el trabajo ya integrado.

## 1. Objetivo y fuentes de verdad

Evolucionar el backend monolítico hacia servicios desplegables independientemente, preservando las funciones que realmente usan el frontend y el curso. Aplicar DDD para definir responsabilidades, ADD para justificar decisiones y prácticas cloud-native acordes al alcance académico.

- El backend anterior es la **referencia de comportamiento**.
- El repositorio remoto nuevo será la **fuente de verdad del trabajo nuevo**. Sus contratos, ADRs y cambios aceptados prevalecen sobre una copia local antigua o sobre este plan.
- El documento del curso define expectativas: microservicios, DDD, ADD, REST, diagramas, decisiones, validación, despliegue y evidencia.
- La guía de refactorización es una propuesta que debe verificarse; no se debe copiar sin revisar.
- El informe ayuda a entender el producto, pero sus afirmaciones técnicas se deben contrastar con el código y el despliegue.

Referencias: [Guía de refactorización](WeRide-Guia-Refactorizacion.md), [Documento del proyecto final](Trabajo%20Final_1ASI0657_202620%20%284%29.md) e [Informe de WeRide](upc-pre-202620-1ASI0657-9231-WeTech-report-TB1.md).

### Hechos observados y crítica

1. El backend disponible es Java 24, Spring Boot 3.5.7, Maven, Spring MVC/JPA, MySQL 8 y JWT. La copia abierta contiene una aplicación Maven y persistencia configurada para el monolito; no es un backend distribuido.
2. Hay paquetes de booking, garage, IAM, location, notifications, plan, profile, trip, travelhistory, unlock y problemreports, además de infraestructura común. Las carpetas son pistas para descubrir contextos, no prueba de que cada carpeta merezca ser microservicio.
3. La guía describe acoplamientos que deben investigarse: Booking consulta modelos/repositorios de Garage, Location e IAM; IAM elimina datos de otros contextos; Unlock/Trip/ProblemReports consulta Booking, Garage y Notifications. No conservar estos accesos como imports o acceso cruzado a bases.
4. Reserva y viaje no están claramente separados: Booking expone operaciones de inicio/finalización mientras Trip/Unlock viven en otros paquetes. Mover clases sin decidir dueño de estado produciría dos fuentes de verdad.
5. La guía reporta inconsistencias de tipos de ID entre reserva, viaje e historial; una carrera en reservas; falta de idempotencia/expiración; IDs discordantes en creación/consulta de notificaciones; y el mapeo MySQL “3307:3307”, aunque el servicio dentro del contenedor normalmente escucha en 3306. Verificar cada punto en el HEAD actual.
6. La guía anterior asigna servicios a otros integrantes. Este documento reemplaza ese reparto.
7. La guía propone librería compartida de seguridad y una llamada síncrona de Gateway a IAM para revisar sesiones. No copiarlas ciegamente: la librería acopla versiones y la llamada remota puede hacer que IAM sea dependencia de cada solicitud.
8. Si el informe describe tecnologías o límites que el código no demuestra, corregir el informe o aportar evidencia; no hacer que código coincida con una descripción incorrecta.
9. La carpeta actual no muestra un repositorio Git en su raíz ni dentro de la copia del backend, y aún no hay URL del remoto nuevo. Al crearlo, registrar URL, ramas y commit de partida en README.

## 2. Arquitectura objetivo

Monorepo con ocho servicios de negocio y un Gateway: iam-service, profile-service, garage-service, location-service, booking-service, ride-service, plan-service, notification-service y api-gateway.

Se propone agrupar trip, travelhistory, unlock y problemreports en ride-service porque parecen participar del ciclo de un viaje. Validar con historias, reglas y agregados. Un reporte de mantenimiento ajeno a un viaje podría pertenecer a Garage.

Ocho servicios agregan operación, configuración, seguridad e integración. La cantidad no es una meta. Si el calendario no permite dar a cada servicio API, datos, despliegue y validación propios, es mejor entregar menos servicios independientes que ocho cascarones acoplados.

~~~mermaid
flowchart LR
    Client[Frontend Angular] --> GW[API Gateway]
    GW --> IAM[IAM]
    GW --> PROF[Profile]
    GW --> GAR[Garage]
    GW --> LOC[Location]
    GW --> BOOK[Booking]
    GW --> RIDE[Ride]
    GW --> PLAN[Plan]
    GW --> NOTIF[Notification]
    BOOK -->|consulta vehículo| GAR
    BOOK -->|consulta punto o sede| LOC
    RIDE -->|valida reserva| BOOK
    RIDE -->|consulta estado del vehículo| GAR
    BOOK -.->|notificación| NOTIF
    RIDE -.->|notificación| NOTIF
    IAM -.->|proceso de baja| PROF
    IAM -.->|proceso de baja| GAR
    IAM -.->|proceso de baja| BOOK
    IAM -.->|proceso de baja| RIDE
    IAM -.->|proceso de baja| NOTIF
~~~

Las líneas discontinuas son interacciones cuyo transporte/garantías se deben decidir; nunca significan acceso directo a otra base.

| Servicio | Dueño y funciones | Datos que escribe | Referencias y límites |
|---|---|---|---|
| **IAM** | Registro, login, cuentas, sesiones, cambio de clave, logout y baja | Cuentas, credenciales, sesiones y roles/estado | No elimina tablas ajenas; coordina baja idempotente. |
| **Profile** | Lectura/actualización del perfil | Perfil y preferencias | userId opaco. Estadísticas de viajes son proyección; Ride conserva la verdad. |
| **Garage** | Vehículos, estado y favoritos | Vehículos, atributos y favoritos | locationId opaco; no relación JPA con Location. Define estados que consultan Booking/Ride. |
| **Location** | Sedes/puntos, coordenadas, horarios y amenidades existentes | Ubicaciones y metadatos | No administra vehículos ni disponibilidad temporal. |
| **Booking** | Reservas, borradores, disponibilidad, historial, cancelación/archivo, cotización, pagos simulados y rating existente | Reservas, borradores y datos simulados | Consulta Garage/Location; posee disponibilidad temporal en su BD. |
| **Ride** | Desbloqueo, viaje, ruta, historial y reportes asociados al viaje | Trip, rutas, historial, unlock y reportes | bookingId, vehicleId y userId opacos; no escribe Booking/Garage/Notification. |
| **Plan** | Catálogo y beneficios existentes | Planes/beneficios | No inventar suscripciones o pagos recurrentes sin evidencia. |
| **Notification** | Bandeja, creación, lectura/no leídas | Notificaciones | Solo Notification escribe su tabla; mismo userId estable al crear, consultar y borrar. |
| **Gateway** | Rutas, CORS, entrada y observabilidad | Configuración | Sin reglas de negocio ni repositorios; no publica rutas internas. |

### DDD

- Cada servicio es dueño de un bounded context, lenguaje, agregados, reglas, API y persistencia.
- Entre contextos pasan IDs opacos y contratos explícitos; no compartir entidades JPA, repositorios, tablas, transacciones o clases de dominio.
- ACID solo garantiza invariantes dentro del servicio. Procesos multi-servicio requieren workflow/saga, reintentos idempotentes y estado parcial; no prometer atomicidad distribuida.
- Consumidores traducen contratos externos con adaptadores/ACL.
- Compartir OpenAPI, eventos y convenciones, no modelos de dominio. No crear un cajón de sastre shared. Una librería técnica solo se aprueba si es estable, versionada y tiene dueño.
- Mantener capas de interfaces, aplicación, dominio e infraestructura. Reglas fuera de controladores y Gateway.
- No agregar CQRS, event sourcing, Kubernetes, service mesh o broker por moda: cada patrón requiere un driver y una decisión.

## 3. Decisiones adoptadas para iniciar

Estas son las decisiones base para que los agentes implementen sin escoger arquitecturas incompatibles. Se registran como ADRs al crear el repositorio. Se reabren solo si el código o un requisito confirmado contradice la hipótesis; quien proponga el cambio debe actualizar contratos y avisar a consumidores.

| ADR | Decisión base | Consecuencia |
|---|---|---|
| **001 Límites DDD** | Mantener los ocho contextos de la sección 2. Ride agrupa unlock, trip, travelhistory y problemreports. Plan administra catálogo/beneficios existentes. Profile no es dueño de datos transaccionales de viaje. | Revisar ProblemReports contra uso actual; si el reporte no es de un viaje, se conserva en Ride para esta entrega hasta aprobar otro límite. No se crea un noveno servicio. |
| **002 Booking/Ride** | Booking es dueño y coordinador del estado de reserva; Ride es dueño de la sesión, ruta y resultado del viaje. El endpoint público actual de start/complete permanece en Booking durante esta versión. Booking envía comandos internos idempotentes a Ride. | Booking usa estados de proceso STARTING/COMPLETING para resultados no confirmados. Ride usa una sesión única por bookingId. Timeout incierto se resuelve consultando/reintentando por bookingId, nunca creando otra sesión. |
| **003 IDs** | Los IDs externos son strings opacos. Se conservan IDs y tipos existentes en la base que sea dueña; consumidores no convierten un ID remoto a Long. Token: sub = ID estable de cuenta como string y preferred_username = username. | Puede requerirse adaptar respuestas y claims en un corte coordinado. No migrar todos los IDs a UUID solo por uniformidad. |
| **004 Auth/JWT** | IAM firma JWT con RS256; la clave privada vive solo en IAM y los servicios/Gateway verifican con clave pública. Claims incluyen sub, preferred_username, sid, iss, aud, iat y exp. Gateway revisa que sid siga activo en IAM con caché máxima de 30 segundos y falla cerrado si IAM no responde. DELETION_PENDING equivale a sesión inactiva; los tombstones locales protegen mutaciones ya en vuelo. Servicios validan firma y permisos localmente; puertos no son públicos. | Logout/revocación tarda como máximo el TTL de caché si IAM está disponible. Mantener inicialmente la duración actual del token para no cambiar vida útil sin requisito. El cambio de firma se hace en un corte coordinado; tokens viejos se vuelven a emitir con login. |
| **005 Comunicación** | Sin broker para esta entrega. REST interno con timeout para consultas/comandos que necesitan respuesta. Booking/Ride persisten notificaciones en una outbox local dentro de la transacción de negocio y las publican por REST a Notification. Cada fila lleva eventId único, eventType, payload, estado, intentos y próxima ejecución. IAM no publica notificaciones de baja. | Notification no bloquea reserva/viaje. Outbox tiene PENDING/DELIVERED/FAILED, intentos y próxima ejecución; reintento exponencial desde 2 segundos hasta 5 minutos. Agotados 10 intentos queda FAILED para consulta/replay interno del productor. Notification deduplica por eventId. No afirmar entrega instantánea. |
| **006 Persistencia** | Un servidor MySQL 8 para la demo local, ocho esquemas y un usuario por servicio con permisos limitados. Flyway gestiona cambios. La migración inicial es de datos demo mediante seeders por servicio; no hay evidencia de datos productivos que deban conservarse. | Es separación lógica y no aislamiento físico. Para cloud se mantiene imagen/config portable; separar instancias puede ser una mejora futura. |
| **007 API** | Mantener paths, JSON y códigos que consume el frontend en /api/v1. Gateway enruta esos paths al servicio dueño. Nuevos paths usan kebab-case plural; no cambiar frontend como parte de la extracción sin PR coordinado. | Se publica un mapa de rutas validado contra Angular antes de enrutar. Alias antiguos solo se retiran con evidencia de que no hay consumidores. |
| **008 Baja de cuenta** | Tras validar contraseña, IAM crea el workflow de baja y pide a Booking prepareUserDeletion. Booking adquiere el mismo bloqueo por usuario que usan crear/iniciar reserva; rechaza con 409 si hay estados reserved, in_progress o paused y bloquea nuevas reservas mientras la baja progresa. Si pasa, IAM marca DELETION_PENDING y revoca sesiones; orquesta Profile, Garage (favoritos), Booking, Ride y Notification. Booking anonimiza historial; Ride elimina viajes/desbloqueos/reportes; los demás borran datos personales que poseen. Location y Plan no tienen datos personales por cuenta y no se llaman. Cada servicio crea un tombstone local antes de borrar datos y rechaza nuevas mutaciones de ese userId. Cada operación es idempotente. Si Booking responde 409, IAM cierra el workflow sin borrar datos y devuelve 409. Si IAM falla después de reservar el bloqueo, ejecuta abortUserDeletion; si la baja ya comenzó, el workflow queda persistido y bloqueado. IAM termina DELETED cuando todos confirman y reintenta pendientes cada 60 s. | No hay transacción distribuida. Cualquier error deja progreso recuperable y la cuenta bloqueada. |
| **009 Concurrencia Booking** | Booking serializa cambios por vehículo con una fila VehicleSchedule y bloqueo pesimista de esa fila; además, usa un bloqueo por userId para impedir que crear/iniciar una reserva compita con la baja de cuenta. Crear fila con upsert seguro, bloquearla, revisar solapamiento y guardar reserva en una transacción. Índice único para userId + Idempotency-Key. | Requiere prueba de dos reservas simultáneas y reintentos. Una solución optimista solo se acepta con prueba que cubra también primera creación de la fila. |
| **010 Ejecución** | Docker Compose es el entorno reproducible obligatorio: Gateway 8080, IAM 8081, Profile 8082, Garage 8083, Location 8084, Booking 8085, Ride 8086, Plan 8087, Notification 8088; host MySQL 3307 → contenedor 3306. | La elección de proveedor cloud no bloquea código; el equipo despliega las mismas imágenes en el proveedor al que tenga acceso y documenta costo/límites antes de publicar. No asumir Render/Aiven sin verificar cuenta y disponibilidad. |

~~~text
Inicio de viaje:
1. Cliente llama a POST /api/v1/bookings/{id}/start.
2. Booking valida dueño, estado, hora y vehículo; bloquea su agregado y persiste STARTING.
3. Booking llama POST /internal/v1/rides/{bookingId}/start con clave idempotente bookingId.
4. Ride verifica autorización/booking por contrato y crea una sesión única; si ya existe devuelve la misma sesión.
5. Booking confirma IN_PROGRESS. Ante timeout consulta/reintenta el mismo bookingId; no borra ni duplica la sesión.
Finalización:
1. Cliente llama al endpoint público actual de Booking.
2. Booking pasa a COMPLETING y ordena a Ride cerrar la sesión de forma idempotente.
3. Ride persiste el resultado; Booking confirma COMPLETED y rating sigue bajo Booking.
4. Si un paso queda incierto, el estado queda recuperable y la misma operación reconcilia.
~~~

#### Contratos internos obligatorios

- Booking → Ride: POST /internal/v1/rides/{bookingId}/start, GET /internal/v1/rides/by-booking/{bookingId} para reconciliar y POST /internal/v1/rides/{bookingId}/complete. Usa el contrato del flujo existente y una Idempotency-Key estable por bookingId + acción. Ride devuelve la misma sesión ante duplicado.
- IAM → Booking: POST /internal/v1/users/{userId}/deletion-lock. Booking serializa con crear/iniciar reservas, devuelve 409 si hay estados activos y mantiene el bloqueo si aprueba. DELETE /internal/v1/users/{userId}/deletion-lock libera solo si IAM aborta antes de iniciar el borrado.
- IAM → servicios con datos personales: DELETE /internal/v1/users/{userId}/personal-data/{workflowId}. Profile, Garage, Booking, Ride y Notification crean tombstone local y responden de forma idempotente. Cada dueño implementa solo su borrado/anónimo.
- Booking/Ride → Notification: PUT /internal/v1/notification-events/{sourceService}/{eventId}. El payload incluye eventType versionado, userId estable, datos mínimos para mostrar la notificación y occurredAt. Notification impone unique(sourceService,eventId) y devuelve el resultado existente en duplicados.
- Cada llamada interna usa identidad de servicio dedicada por caller/receiver, almacenada como secreto externo. Nunca se reenvía un token de usuario como sustituto de identidad de servicio ni se expone este API por Gateway.
- Cada productor ofrece consulta/replay operativa de eventos FAILED protegida internamente; el evento se identifica por eventId. No guardar secretos ni payload con datos personales innecesarios en outbox/logs.

Estos valores cierran decisiones de diseño para arrancar. El proveedor cloud exacto sigue siendo una elección operativa porque depende de cuentas y límites que no están en la carpeta compartida; no bloquea que el equipo implemente y valide localmente.

## 4. Contratos y nomenclaturas

### 4.1 APIs

- Prefijo /api/v1. Preservar rutas que consume Angular durante primera migración. Hay rutas mixtas como /location, /bookingDrafts y /unlockRequests; no renombrarlas silenciosamente.
- Rutas nuevas: minúsculas, plurales y kebab-case: /api/v1/locations, /api/v1/bookings, /api/v1/rides, /api/v1/problem-reports.
- OpenAPI por servicio, con request/response, validaciones, códigos, seguridad y ownership. Guardar en contracts/openapi o por módulo con índice común.
- Errores consistentes, preferentemente application/problem+json; distinguir 400, 401, 403, 404, 409 y 5xx.
- POST con posibles reintentos define idempotencia. Booking usa Idempotency-Key asociada a usuario; misma clave y payload distinto se detecta.
- Tiempos ISO-8601 y almacenamiento UTC; reglas de horario local declaran zona horaria.
- Usar DTOs, no entidades JPA ni excepciones internas.

#### Mapa inicial de rutas públicas

Confirmar cada ruta con el backend y el consumo real del Angular. El Gateway conserva temporalmente las rutas antiguas y las dirige al dueño nuevo; los servicios no deben inventar paths incompatibles.

| Rutas actuales a verificar | Dueño objetivo |
|---|---|
| /api/v1/authentication/**, /api/v1/accounts/** | IAM |
| /api/v1/profiles/** | Profile |
| /api/v1/vehicles/**, /api/v1/favorites/** | Garage |
| /api/v1/location/** | Location |
| /api/v1/bookings/**, /api/v1/bookingDrafts/**, /api/v1/payments/** | Booking |
| /api/v1/trips/**, /api/v1/travel-history/**, /api/v1/unlockRequests/**, /api/v1/problem-reports/** | Ride |
| /api/v1/plans/** | Plan |
| /api/v1/notifications/** | Notification |

### 4.2 Seguridad y llamadas internas

- Cada servicio autoriza usuario/propiedad; Gateway no sustituye autorización local.
- ID estable distinto de username; username no es llave si puede cambiar.
- Rutas internas bajo /internal/v1/**, autenticadas con identidad de servicio, privadas y bloqueadas en Gateway. No usar secreto global hard-coded.
- CORS en Gateway si se acuerda; health/docs públicos solo según entorno.
- HTTP interno con timeout. Reintentos breves solo para operaciones idempotentes; nunca reintentar a ciegas reserva/pago.
- Propagar traceId/correlationId sin registrar tokens, secretos o datos personales.

### 4.3 Convenciones

| Elemento | Convención |
|---|---|
| Carpetas/artefactos Maven | booking-service, api-gateway; kebab-case |
| Paquetes Java | minúsculas; conservar org.example.backendweride.platform.[contexto] durante extracción |
| Clases/records Java | PascalCase; métodos/variables camelCase; constantes UPPER_SNAKE_CASE |
| Rutas | plural, minúsculas, kebab-case y versionadas para nuevas rutas |
| Tablas/columnas | snake_case, nombres explícitos, cambios por migración |
| Esquemas | weride_iam, weride_booking, etc. |
| Variables | SPRING_DATASOURCE_URL, IAM_BASE_URL; secretos externos |
| Ramas | feature/{servicio}/{alcance}, fix/{servicio}/{alcance}, docs/{alcance} |
| Commits | Conventional Commits: feat(booking): ..., fix(iam): ..., docs(architecture): ... |
| Eventos | [contexto].[hecho].v1 en pasado: ride.started.v1 |

No inventar namespace Java usando un dominio que el equipo no controla. org.example es temporal, pero cambiarlo a mitad de extracción añade ruido; decidirlo una vez antes de crear módulos.

## 5. Repositorio remoto y coordinación de agentes

### 5.1 Estructura recomendada

~~~text
/
├── pom.xml
├── api-gateway/
├── services/
│   ├── iam-service/
│   ├── profile-service/
│   ├── garage-service/
│   ├── location-service/
│   ├── booking-service/
│   ├── ride-service/
│   ├── plan-service/
│   └── notification-service/
├── contracts/
│   ├── openapi/
│   └── events/
├── docs/architecture/decisions/
├── docs/runbooks/
├── docker-compose.yml
├── .env.example
└── README.md
~~~

Un POM padre agrega módulos; cada servicio construye su JAR/imagen. Gateway usa la pila que exija su implementación y versión Spring Cloud compatible; los demás conservan MVC salvo decisión. No hace falta librería compartida para compilar.

El repo nuevo incluye este plan y referencia al backend legado (URL y commit/versión), pero no duplica secretos ni datos productivos. El legado se consulta para entender comportamiento, no se modifica desde agentes del monorepo salvo tarea explícita.

### 5.2 Flujo Git y reglas

- Monorepo remoto con main y develop protegidas; develop es integración y main recibe releases. Ramas cortas y PR pequeño.
- Antes de tarea: revisar git status, branch, log, README, AGENTS.md, ADRs y contratos; actualizar desde origin/develop. Nunca hacer reset/checkout destructivo ni descartar cambios.
- Cada integrante modifica sus servicios/contratos. Cambios de límite requieren PR y revisión de los dueños afectados.
- PR describe motivo, comportamiento, contrato, persistencia/migración, riesgos y evidencia. Al menos un compañero revisa.
- Extraer incrementalmente: servicio que arranca y posee sus datos antes de conectarlo.
- .env.example sin secretos; semillas reproducibles.
- Agentes leen el estado común. No asumen que su copia antigua es más correcta ni sobrescriben módulos de otros.

### 5.3 “Servicio independiente” significa

Aplicación de arranque, build, config, API/contrato, acceso exclusivo a datos, ejecución/Docker, health y validación propia. Copiar paquete a otra carpeta manteniendo EntityManager, repositorios o esquema monolítico no cuenta como extracción.

## 6. Datos y migración

1. Inventariar entidades, tablas, claves, enums, constraints, seeds y consultas cruzadas. Crear mapa “tabla actual → dueño”.
2. Migraciones versionadas por servicio y esquema. En demo puede haber MySQL compartido, pero cada usuario tiene permisos solo a su esquema: separación lógica, no aislamiento físico/HA.
3. No crear FK SQL entre esquemas. Validar referencias externas por contrato o guardar ID/snapshot.
4. Si solo hay datos demo, crear seeders propios sin depender del orden accidental. Si se conservan datos, crear importación única, segura al reejecutar, con conteos, IDs y conciliación.
5. No permitir dos servicios escribiendo la misma tabla ni dual writes indefinidos. Congelar escritura monolítica al corte o definir sincronización explícita.
6. Strangler en Gateway: activar ruta nueva cuando el flujo pase. Rutas aún no extraídas pueden ir al legado temporalmente si convivencia se implementa/documenta; no llamar migración completa a solución dependiente del monolito.
7. Rollback: imagen/configuración anterior, respaldo y ventana de corte. Cambios destructivos requieren reversa o estrategia expand/contract.

## 7. Etapas de implementación

El orden expresa dependencias; trabajar en paralelo una vez publicados los contratos base.

### Etapa 0 — Descubrimiento y acuerdos

Todos participan; Samuel mantiene el índice de decisiones.

- Leer contextos asignados completos: controladores, recursos, entidades/agregados, servicios, repositorios, seguridad, properties, seeders y pruebas.
- Comparar APIs de Angular con backend; registrar rutas, JSON y status.
- Crear DDD Context Map, C4 Context/Container, secuencias de reserva/viaje/notificación/baja y ERD por servicio.
- Registrar las decisiones base de la sección 3 como ADRs; reabrir solo con evidencia y compatibilidad revisada.
- Verificar hallazgos de la guía en HEAD: confirmado, corregido o no reproducible.
- Backlog con responsable, dependencia y criterio de aceptación.

**Salida:** contratos publicados, límites aceptados y repo común accesible. No iniciar integraciones con contratos inventados.

### Etapa 1 — Base técnica

Samuel coordina POM, IAM y Gateway; dueños crean esqueletos coordinados.

- POM padre, versiones Spring compatibles, build raíz y build independiente.
- Aplicación Spring, config por entorno, Dockerfile y health/readiness.
- IAM define identidad; Gateway configura rutas y políticas.
- Compose con healthchecks, puertos correctos, redes y credenciales por servicio/esquema.
- .env.example y README reproducible.

**Salida:** módulos arrancan independientemente con config conocida.

### Etapa 2 — Extracción por dependencias

1. IAM + Gateway como base de acceso/rutas.
2. Profile/Garage y Location/Plan en paralelo.
3. Booking después de contratos de Garage/Location/IDs.
4. Ride después de ADR-002 y contrato Booking.
5. Notification después de publicar contrato outbox REST e idempotencia con emisores.
6. Sustituir cada acceso cruzado a repositorio/modelo por contrato.

Conservar rutas del frontend; mover comportamiento primero. Cambios de regla solo con requisito y PR.

### Etapa 3 — Flujos verticales

- **Acceso:** registro/login → token con ID estable → perfil y vehículos autorizados.
- **Reserva:** Location/Garage → verificar fechas → crear Booking una vez → dos peticiones incompatibles no reservan simultáneamente.
- **Viaje:** verificar reserva/dueño → Ride desbloquea/inicia → registra ruta/historial → completa; estados siguen ADR-002.
- **Notificación:** hecho de negocio → notificación con userId estable → dueño consulta/marca leída; reintento no duplica.
- **Seguridad:** A no ve datos de B; rutas internas no pasan Gateway; IAM no escribe bases ajenas.
- **Baja:** cuenta deshabilitada y workflow por contexto con progreso, reintentos y fallo parcial explícito.

### Etapa 4 — Despliegue y evidencia

- Levantar Compose desde cero, semillas, puertos y perfiles local/demo/cloud.
- Publicar imágenes y desplegar según proveedor; solo Gateway es público.
- Importar datos solo si el alcance lo requiere y verificar conteos/IDs.
- Validar reglas, contratos, auth/ownership, concurrencia, servicio caído y reintentos.
- Recoger evidencia real de PRs, build, pruebas, Swagger, ejecución, despliegue y board.
- Actualizar informe/diagramas para que describan código desplegado.


## 8. Reparto del equipo

Este plan fija el reparto acordado: **Plan pasa a Asto y Wanderer queda con Ride**.

| Integrante | Asignación | Entregables | Colaboración crítica |
|---|---|---|---|
| **Jareth** | location-service + booking-service | Location primero; Booking, borradores, pagos simulados, historial, disponibilidad, cancelación/archivo y rating solo según alcance existente. Contratos, migraciones, idempotencia. | Angel publica vehículo; Samuel auth; coordinar con Wanderer inicio/fin del viaje. |
| **Wanderer** | ride-service | Consolidar unlock/trip/history/reportes de viaje; estado Ride, ruta, IDs y contratos/eventos. | Jareth para Booking; Angel para estado de vehículo; Asto para notificaciones. |
| **Angel** | profile-service + garage-service | Perfil/preferencias; vehículos/favoritos/estados; esquema/seed/migración y API de vehículo. | Jareth y Wanderer consumen contratos; Location es referencia externa. |
| **Samuel** | iam-service + api-gateway | IAM, claims/sesiones/seguridad; POM base y rutas/políticas Gateway; CORS, entrada e integración de paths. | Todos: ID/auth y rutas. No asumir solo la integración de la lógica ajena. |
| **Asto** | notification-service + plan-service | Bandeja/mark-read; contrato de eventos/comandos; catálogo de planes/beneficios existentes. | Jareth/Wanderer para eventos; Samuel para userId y baja. |

Todos documentan DDD, APIs, persistencia, decisiones y validación. Nadie es dueño exclusivo de arquitectura; cambios de límites requieren revisión de los afectados.

## 9. Criterios de aceptación y ADD

### Por servicio

- Dueño, bounded context, reglas/agregados, OpenAPI, aplicación, config, health, migraciones, esquema y usuario de BD.
- Construye y arranca solo; no importa modelos, entidades o repositorios ajenos.
- Usa contratos/IDs, no lee tablas ajenas; llamadas externas tienen timeout y manejo de fallas.
- Pruebas de reglas y contratos/integración en casos críticos.
- Autorización local; secretos fuera del repo; Compose y README reproducibles.

### Sistema integrado

- Angular usa Gateway con rutas acordadas.
- Sin token/token inválido → 401 en endpoint protegido; otro dueño no obtiene datos.
- Gateway no publica /internal/**.
- Booking evita doble reserva concurrente; misma Idempotency-Key no duplica; reservas en estado reserved expiran 15 minutos después de startDate si no se iniciaron.
- Ride conserva bookingId correcto; notificación usa mismo userId al crear, consultar y dar de baja.
- Dependencia caída termina con error controlado/timeout.
- Baja reintentable con falla parcial visible; no se afirma atomicidad global.
- Sin escrituras compartidas ni FK entre contextos.
- Informe, diagramas, ADRs, capturas y despliegue describen versión real.

### Escenarios ADD

Acordar carga/ambiente antes de inventar umbrales numéricos. Documentar estímulo, contexto, respuesta y medida:

- **Seguridad:** petición anónima → 401; usuario distinto → no divulgar datos.
- **Integridad:** dos usuarios reservan mismo vehículo/horario → como máximo uno confirma; otro recibe conflicto.
- **Idempotencia:** misma clave/usuario/payload → mismo resultado; distinto payload → conflicto.
- **Modificabilidad:** actualizar un servicio no obliga a compilar dominio de otros; validar contrato.
- **Resiliencia:** Notification caído → reserva/viaje se confirman si la transacción de negocio es válida; outbox reintenta y deja FAILED visible tras 10 fallos para replay.
- **Desplegabilidad:** servicio inicia con variables/esquema propios sin pasos manuales ocultos.

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Ocho servicios para cinco personas | Entregar verticales funcionales; si no alcanza, recortar complejidad operativa y reevaluar límites, no dejar ocho cascarones. |
| Contratos cambian tarde | OpenAPI antes del cliente; cambio incompatible por ADR/PR y revisión de consumidores. |
| Booking/Ride duplican estado | ADR-002, autoridad por estado y workflow recuperable/idempotente. |
| Exceso de REST síncrono | Timeouts y sin retry ciego; Booking posee disponibilidad temporal; efectos secundarios desacoplados. |
| Librería shared se vuelve monolito | No compartir dominio/JPA; infraestructura pequeña y versionada. |
| IDs heredados Long/String/UUID | IDs opacos, mapeo controlado y validación de migración. |
| Exposición de secretos/servicios | Secretos externos, Gateway único público y autorización local. |
| Guía/informe desactualizados | Registro de discrepancias y verificación en commit actual. |
| Declarar microservicios sin evidencia | Criterio de independencia, builds/despliegues y lenguaje preciso. |

## 11. Entregables para el curso

- README con producto, prerequisitos, builds, puertos, Compose, variables y Swagger.
- DDD Context Map/glosario; C4 Context/Container; despliegue; secuencias reserva/viaje/baja; ERD por servicio.
- ADRs y ADD por iteración: driver, objetivo, elementos refinados, conceptos, vistas y revisión.
- Índice OpenAPI/eventos; runbooks para levantar, migrar, backup, rollback y diagnóstico.
- Backlog/sprints, PRs por integrante y evidencia real de pruebas, ejecución, Swagger y despliegue.
- Informe actualizado contra el código. No conservar tecnologías, diagramas o capturas no demostrables.

## 12. Reglas comunes para agentes

Cada prompt de integrante incluye estas reglas:

1. Lee este plan, README, AGENTS.md si existe, ADRs, OpenAPI, historial y estado del monorepo remoto.
2. Revisa Git status/branch/log; protege cambios locales y sincroniza rama de integración antes de crear rama corta.
3. Inspecciona en backend original tu contexto: controladores, recursos, agregados, servicios, repositorios, seguridad, config, seeders, pruebas y dependencias.
4. Compara código original con cambios integrados. Repo remoto y contratos aceptados prevalecen. Si hay conflicto, explica opciones y pide decisión/ADR antes de romper contrato.
5. Modifica tu alcance y contratos coordinados; no toques bases, entidades o paquetes de otro dueño.
6. Conserva rutas/comportamiento salvo cambio aprobado; no inventes funciones sin soporte en código/requisitos.
7. Revisa diff y reporta archivos, decisiones, migraciones, dependencias y validaciones realmente ejecutadas. No afirmes pruebas que no corriste.
8. Si repo/contratos no están accesibles, di qué falta y continúa solo análisis independiente. No inventes URL, branch, API o commit.

## 13. Prompts listos para copiar

Reemplaza [RUTA_BACKEND_ORIGINAL] y [RUTA_REPO_COMPARTIDO]. Cada agente lee el plan desde el monorepo antes de editar.

### Orden de envío y ejecución

~~~text
ORDEN DE ENVÍO Y EJECUCIÓN

0. Antes de los prompts: crear el remoto, compartirlo con los cinco, subir este plan y fijar la referencia/commit del backend legado.
1. Samuel empieza primero: estructura raíz Maven, ramas/README, configuración base e IAM (firma/claims). Puede dejar rutas Gateway como placeholders. No mover todavía los demás dominios.
2. En paralelo, Jareth extrae Location y publica su OpenAPI; Angel extrae Garage/Profile y publica el contrato de vehículos; Asto extrae Plan y publica el contrato REST/idempotencia de Notification. Los tres revisan cambios comunes antes de editar el POM raíz.
3. Jareth comienza Booking cuando los contratos de Location y Garage estén aceptados. Samuel finaliza la especificación de JWT/internal auth antes de integrar consumidores.
4. Wanderer implementa Ride según ADR-002, después de aceptar el contrato Booking start/complete.
5. Asto completa Notification al integrar el contrato PUT idempotente con Booking y Ride. Plan puede avanzar desde el paso 2.
6. Samuel configura rutas finales del Gateway después de que cada servicio publique OpenAPI y lidera el release cloud; Angel valida MySQL/esquemas y cada dueño entrega su Dockerfile. Luego los cinco hacen revisión cruzada e integración.
7. Para cada entrega, el dueño manda su prompt asignado a su agente y usa el mismo hilo para continuar por etapas. No mandar a los cinco agentes a editar simultáneamente archivos base/POM/contratos comunes.
~~~

### Prompt de Jareth — Location y Booking

~~~text
Actúa como ingeniero responsable de location-service y booking-service en WeRide.

Fuentes:
- Backend anterior, referencia de comportamiento: [RUTA_BACKEND_ORIGINAL]
- Monorepo remoto nuevo, fuente de verdad: [RUTA_REPO_COMPARTIDO]
- Lee primero Plan-Microservicios-WeRide.md, README, AGENTS.md si existe, ADRs, OpenAPI y cambios integrados.

Antes de editar revisa git status, branch, log y estructura. No sobrescribas cambios ajenos ni trabajes en main/develop. Comprende en el legado controladores, recursos, agregados, servicios, repositorios, DTOs, seguridad, seeders, pruebas y acoplamientos de Location/Booking.

Aplica el plan DDD: Location posee ubicaciones; Booking posee reservas, borradores y disponibilidad. Trabaja en dos entregas: Location primero y publica su OpenAPI; inicia Booking solo cuando los contratos de Location y Garage estén aceptados. No compartas entidades JPA ni leas tablas de Garage, IAM o Location. Conserva rutas/funciones usadas por frontend: borradores, pagos simulados, cotización, historial, cancelación/archivo y rating solo si existen. Cambios incompatibles requieren contrato y coordinación.

Coordina con Angel contrato de vehículo/estado; con Wanderer ADR-002 e inicio/fin de viaje. Usa IDs opacos. Implementa persistencia/migraciones propias. Resuelve concurrencia para que haya como máximo una reserva por vehículo/intervalo. Implementa expiración de reservas en estado reserved que no se iniciaron dentro de los 15 minutos posteriores a startDate, como regla base de la guía, con valor configurable. Idempotency-Key se asocia al usuario; misma clave con payload distinto se detecta. No aceptes @Version sin cubrir inserción inicial concurrente.

Define timeouts/errores de llamadas a Garage/Location; no uses transacciones entre bases. Añade OpenAPI, health/config, documentación, outbox durable de notificaciones de Booking y validaciones críticas.

Entrega PRs pequeños en ramas feature/location-service/... y feature/booking-service/... coordinadas; reporta rutas preservadas, tablas, contratos, riesgos y pruebas/build realmente ejecutados. No edites otros servicios. Si el código contradice ADR-002 o un contrato aceptado, no elijas unilateralmente: presenta evidencia y propone un ADR de reemplazo.
~~~

### Prompt de Wanderer — Ride

~~~text
Actúa como ingeniero responsable de ride-service en WeRide.

Fuentes:
- Backend anterior, referencia de comportamiento: [RUTA_BACKEND_ORIGINAL]
- Monorepo remoto nuevo, fuente de verdad: [RUTA_REPO_COMPARTIDO]
- Lee plan, README, AGENTS.md si existe, ADRs, contratos y cambios actuales de Jareth, Angel, Samuel y Asto.

Inspecciona Git/branch/status y protege cambios ajenos. Comprende unlock, trip, travelhistory y problemreports: controladores, agregados, validaciones, repositorios, seguridad, pruebas y dependencias. Consolida en Ride solo reportes asociados a viajes; si alguno es mantenimiento de Garage, propón ajuste con evidencia.

Ride posee sesión/viaje, desbloqueo, ruta, historial y reportes de viaje. No escribas Booking, Garage, IAM o Notification mediante sus tablas. Consume contratos publicados con adaptadores. bookingId, vehicleId y userId son opacos: corrige incompatibilidades sin migrar todos a UUID.

Implementa inicio/fin conforme ADR-002: Booking posee reserva y Ride sesión; una autoridad por estado y recuperación idempotente. Conserva rutas del frontend (/api/v1/trips, /api/v1/travel-history, /api/v1/unlockRequests y /api/v1/problem-reports) o acuerda alias Gateway. Define estados, ownership, UTC y fallas de dependencias.

Publica OpenAPI y contrato del mensaje/outbox con Asto. Añade persistencia/migraciones/health/Docker propios, outbox durable de notificaciones y validaciones de permisos, transiciones, IDs y timeouts.

Entrega rama feature/ride-service/... y reporta tablas/rutas, contratos, validaciones ejecutadas y riesgos. No afirmes atomicidad entre Ride y Booking ni edites módulos ajenos.
~~~

### Prompt de Angel — Profile y Garage

~~~text
Actúa como ingeniero responsable de profile-service y garage-service en WeRide.

Fuentes:
- Backend anterior, referencia de comportamiento: [RUTA_BACKEND_ORIGINAL]
- Monorepo remoto nuevo, fuente de verdad: [RUTA_REPO_COMPARTIDO]
- Lee plan, README, AGENTS.md si existe, ADRs, contratos y cambios de otros dueños.

Revisa Git status, branch, log y módulos. Comprende controladores, recursos, agregados, repositorios, asociaciones, seguridad, seeders y pruebas de Profile/Garage; identifica consumidores de Vehicle/Profile.

Profile posee perfil y preferencias. Estadísticas de viaje son proyección si existen; Ride conserva la verdad. Garage posee vehículo, estado y favoritos. Define estados; Booking posee disponibilidad temporal. locationId es ID opaco, no entidad JPA/FK de Location.

No compartas bases, repositorios, entidades o clases de dominio. Publica primero el contrato de Garage que Booking necesita; coordina con Jareth/Wanderer. Profile puede avanzar en paralelo. Protege datos privados. Conserva API/frontend y no inventes funciones.

Cada servicio necesita módulo ejecutable, esquema/migraciones/usuario propios, config, health, Docker y OpenAPI. Secretos fuera del repo.

Entrega ramas feature/profile-service/... y feature/garage-service/... coordinadas; mapa de tablas/seed/migración, ownership, contratos y validaciones ejecutadas. No cambies Booking, Location o Ride.
~~~

### Prompt de Samuel — IAM y API Gateway

~~~text
Actúa como ingeniero responsable de iam-service y api-gateway y coordina la estructura técnica inicial.

Fuentes:
- Backend anterior, referencia de comportamiento: [RUTA_BACKEND_ORIGINAL]
- Monorepo remoto nuevo, fuente de verdad: [RUTA_REPO_COMPARTIDO]
- Lee plan, README, AGENTS.md si existe, ADRs, OpenAPI, historial y cambios integrados.

Inspecciona Git/branch/status. Coordina POM raíz/Compose si otros trabajan allí; no sobrescribas módulos. En IAM legado entiende registro/login, hashing, cuenta, claims, sesiones, logout/revocación, contraseña y baja. Conserva compatibilidad frontend.

IAM posee cuenta/credenciales/sesiones. Elimina borrado JPQL de datos ajenos y define baja idempotente con workflow persistido, llamada a Booking para preparar/bloquear el userId, progreso, reintento y falla parcial. Implementa ADR-003/004: ID estable distinto de username, claims, audience, firma, expiración y revocación. No versionar secretos ni hacer depender cada request de llamada remota a IAM sin medir/documentar.

Gateway enruta y aplica políticas, sin repositorios/reglas. Conserva paths de Angular; agrega rutas según OpenAPI; CORS único si se acuerda; no publica /internal/**. Define seguridad para acceso directo a servicios y health/docs por entorno.

Mantén versiones Spring compatibles y builds reproducibles. POM/Compose/librería técnica mínimos y coordinados; no dominio compartido.

Entrega PRs pequeños de IAM, Gateway y base técnica; documenta token, rutas, variables y secretos externos. Mantén Compose/deploy reproducibles y lidera la publicación cloud cuando el equipo tenga proveedor y credenciales disponibles. Reporta pruebas/build ejecutados. Revisa contratos sin reescribir otros módulos ni asumir toda la integración.
~~~

### Prompt de Asto — Notification y Plan

~~~text
Actúa como ingeniero responsable de notification-service y plan-service en WeRide.

Fuentes:
- Backend anterior, referencia de comportamiento: [RUTA_BACKEND_ORIGINAL]
- Monorepo remoto nuevo, fuente de verdad: [RUTA_REPO_COMPARTIDO]
- Lee plan, README, AGENTS.md si existe, ADRs, contratos y cambios de IAM, Booking y Ride.

Revisa Git y protege cambios ajenos. Implementa Plan en paralelo desde el inicio; deja Notification como contrato inicial y completa outbox REST después de acordar payload/eventId/idempotencia con Jareth, Wanderer y Samuel. El receptor debe deduplicar por sourceService + eventId; cada productor conserva estado FAILED y mecanismo interno de replay. Inspecciona dominio real de Plan, endpoints, beneficios, datos y uso frontend. Traslada funciones existentes; no inventes billing/membresías recurrentes.

Notification posee bandeja/lectura/persistencia. Otros servicios emiten evento/contrato o comando autorizado; nunca insertan en su BD. Coordina con Jareth/Wanderer payload, versión y garantías; con Samuel userId estable y baja.

Verifica discrepancia descrita entre ID usado por Unlock al crear y el usado por Notifications al listar. Unifica con ID estable, no username. Implementa ADR-005 adoptado: API interna autenticada con timeout e idempotencia; Booking/Ride envían outbox durable por REST. No agregues broker. Implementa PUT /internal/v1/notification-events/{sourceService}/{eventId}, deduplica con índice único, y verifica reintentos y replay de FAILED en cada productor. Notificar no debe romper transacción crítica.

Conserva rutas /api/v1/notifications y /api/v1/plans. Rutas internas fuera de Gateway. Cada servicio tiene módulo, esquema/migraciones, Docker, health, config y OpenAPI propios.

Entrega ramas feature/notification-service/... y feature/plan-service/... coordinadas; valida ownership, lectura, deduplicación y contratos; reporta validaciones realmente ejecutadas. No cambies IAM, Booking o Ride para adaptarlos sin PR coordinado.
~~~

## 14. Primeros pasos

1. Crear repositorio remoto, dar acceso a los cinco y proteger main y develop. Usar develop para integración y main solo para releases.
2. Guardar este plan bajo docs/architecture/Plan-Microservicios-WeRide.md; añadir enlace/commit del legado.
3. Comunicar el reparto fijado en la sección 8 y asignar revisores cruzados.
4. Publicar mapa de rutas, contratos e IDs; crear esqueletos en ramas separadas.
5. Integrar IAM/Gateway, después contextos independientes, luego Booking/Ride; cerrar con flujo end-to-end, migración y despliegue.
6. Actualizar plan cuando una ADR cambie. No dejar documento que contradiga código aceptado.

**Criterio final:** WeRide se considera migrado cuando los flujos críticos pasan por Gateway, cada servicio escribe sus datos, dependencias están documentadas y despliegue/validación demuestran independencia. Hasta entonces, describir con precisión qué está extraído y qué sigue dependiendo del backend anterior.












