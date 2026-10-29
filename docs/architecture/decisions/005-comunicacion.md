# ADR-005: Comunicación

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Sin broker para esta entrega. REST interno con timeout para consultas/comandos que necesitan respuesta. Booking/Ride persisten notificaciones en una outbox local dentro de la transacción de negocio y las publican por REST a Notification. Cada fila lleva eventId único, eventType, payload, estado, intentos y próxima ejecución. IAM no publica notificaciones de baja.

## Consecuencias

Notification no bloquea reserva/viaje. Outbox tiene PENDING/DELIVERED/FAILED, intentos y próxima ejecución; reintento exponencial desde 2 segundos hasta 5 minutos. Agotados 10 intentos queda FAILED para consulta/replay interno del productor. Notification deduplica por eventId. No afirmar entrega instantánea.
