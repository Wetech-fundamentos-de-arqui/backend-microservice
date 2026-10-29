# ADR-008: Baja de cuenta

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Tras validar contraseña, IAM crea el workflow de baja y pide a Booking prepareUserDeletion. Booking adquiere el mismo bloqueo por usuario que usan crear/iniciar reserva; rechaza con 409 si hay estados reserved, in_progress o paused y bloquea nuevas reservas mientras la baja progresa. Si pasa, IAM marca DELETION_PENDING y revoca sesiones; orquesta Profile, Garage (favoritos), Booking, Ride y Notification. Booking anonimiza historial; Ride elimina viajes/desbloqueos/reportes; los demás borran datos personales que poseen. Location y Plan no tienen datos personales por cuenta y no se llaman. Cada servicio crea un tombstone local antes de borrar datos y rechaza nuevas mutaciones de ese userId. Cada operación es idempotente. Si Booking responde 409, IAM cierra el workflow sin borrar datos y devuelve 409. Si IAM falla después de reservar el bloqueo, ejecuta abortUserDeletion; si la baja ya comenzó, el workflow queda persistido y bloqueado. IAM termina DELETED cuando todos confirman y reintenta pendientes cada 60 s.

## Consecuencias

No hay transacción distribuida. Cualquier error deja progreso recuperable y la cuenta bloqueada.
