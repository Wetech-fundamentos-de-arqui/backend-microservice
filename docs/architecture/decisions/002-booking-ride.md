# ADR-002: Booking/Ride

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Booking es dueño y coordinador del estado de reserva; Ride es dueño de la sesión, ruta y resultado del viaje. El endpoint público actual de start/complete permanece en Booking durante esta versión. Booking envía comandos internos idempotentes a Ride.

## Consecuencias

Booking usa estados de proceso STARTING/COMPLETING para resultados no confirmados. Ride usa una sesión única por bookingId. Timeout incierto se resuelve consultando/reintentando por bookingId, nunca creando otra sesión.
