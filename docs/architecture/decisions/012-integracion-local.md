# ADR-012: Integración de la extracción completa

- **Estado:** Implementada localmente para revisión del equipo.
- **Complementa:** ADR-002, 003, 005, 008 y 009.

## Contexto

La extracción debe conservar inicio/finalización, cancelación de una reserva que ya inició, detalles agregados y reportes con exoneración del primer minuto. Estas operaciones escribían varios contextos en una transacción del monolito.

## Decisiones

1. Booking conserva el contrato público y coordina Ride. `Trip` tiene una restricción única por `bookingId` opaco. Ride es autoridad de sesión, ruta y métricas; Booking conserva una proyección confirmada para sus respuestas e historial.
2. Se agrega `POST /internal/v1/rides/{bookingId}/cancel`. Booking persiste `cancelling` antes de enviarlo. Ride cierra idempotentemente la sesión o registra una sesión cancelada si el inicio aún no llegó, impidiendo un viaje huérfano por un inicio retrasado.
3. Booking persiste el payload y hash de finalización antes de llamar a Ride. La repetición devuelve el resultado; un cambio devuelve 409. Un job reconcilia `starting`, `completing` y `cancelling`. La reubicación en Garage conserva un indicador pendiente y se reintenta.
4. Los detalles consumen contratos de Garage, Location y Ride. Garage consulta ratings de Booking por REST; la ausencia de ese agregado no impide consultar la flota.
5. Ride persiste el reporte y progreso antes de pedir exoneración a Booking y mantenimiento a Garage. `reportId` identifica la exoneración y el instante del reporte fija la ventana de 60 segundos. Los reintentos no duplican el efecto ni la notificación.
6. Cada servicio con datos personales tiene una tabla local `user_guards`: serializa mutaciones/baja y conserva tombstones. Booking usa la misma fila en `deletion-lock`.
7. La consulta de solapamientos es una lectura con bloqueo después de bloquear la agenda. Así observa reservas confirmadas bajo `REPEATABLE READ` de MySQL, incluso si hubo una lectura consistente anterior.
8. Notificaciones utiliza siempre `sub`, incluyendo recordatorios del frontend. Deduplica productor/ID y hash del payload.
9. Angular utiliza `/api/v1` relativo en desarrollo/producción. Vite/Nginx lo envían al Gateway. El frontend es el perfil opcional `web` de Compose para permitir construir el backend sin el checkout vecino.

## Consecuencias

Existen estados parciales y recuperación eventual. Los timeouts conservan progreso y producen errores controlados; no hay atomicidad entre bases. Los eventos `FAILED` se consultan y reenvían internamente. Los contratos aditivos deben revisarse con los dueños antes de integrarse al remoto.
