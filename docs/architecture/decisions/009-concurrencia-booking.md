# ADR-009: Concurrencia Booking

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Booking serializa cambios por vehículo con una fila VehicleSchedule y bloqueo pesimista de esa fila; además, usa un bloqueo por userId para impedir que crear/iniciar una reserva compita con la baja de cuenta. Crear fila con upsert seguro, bloquearla, revisar solapamiento y guardar reserva en una transacción. Índice único para userId + Idempotency-Key.

## Consecuencias

Requiere prueba de dos reservas simultáneas y reintentos. Una solución optimista solo se acepta con prueba que cubra también primera creación de la fila.
