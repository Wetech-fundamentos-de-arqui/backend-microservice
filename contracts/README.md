# Contratos de WeRide

Cada servicio publica `http://localhost:<puerto>/v3/api-docs`. Los snapshots en `openapi/` se exportan del stack después de comprobar el arranque; describen este trabajo local, pendiente de revisión del equipo.

Para regenerarlos con el stack levantado: `python scripts/export_openapi.py`.

| Servicio | Públicos bajo `/api/v1` | Internos principales |
|---|---|---|
| IAM | authentication, accounts | GET sessions/{sid} |
| Profile | profiles/{userId} | Baja de datos personales |
| Garage | vehicles, favorites | GET vehicles/{id}; PUT vehicles/{id}/location o /maintenance; baja |
| Location | location | GET locations y locations/{id} |
| Booking | bookings, bookingDrafts, payments | GET bookings/{id}; GET vehicles/ratings; deletion-lock; baja; problem-report; outbox |
| Ride | trips, travel-history, unlockRequests, problem-reports | rides/{bookingId}/start, /complete, /cancel; rides/by-booking/{bookingId}; bookings/{bookingId}/unlock; baja; outbox |
| Plan | plans | — |
| Notification | notifications | PUT notification-events/{sourceService}/{eventId}; baja |

Las rutas internas llevan `/internal/v1`, exigen la identidad permitida y no pasan por Gateway. La baja es `DELETE /internal/v1/users/{userId}/personal-data/{workflowId}`. Los payloads/códigos se detallan en OpenAPI y ADR; las claves idempotentes se conservan durante los reintentos.

El [schema de eventos](events/notification-event.v1.schema.json) describe el cuerpo recibido por Notification; productor e ID van en la ruta. Se emiten:

- `booking.created.v1`, `booking.started.v1`, `booking.completed.v1`, `booking.cancelled.v1`.
- `ride.unlock-requested.v1`, `ride.completed.v1`, `ride.problem-reported.v1`.
