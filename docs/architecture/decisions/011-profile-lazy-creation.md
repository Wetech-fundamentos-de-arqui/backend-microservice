# ADR-011: Creación perezosa del perfil

- **Estado:** Aceptada (decisión de Samuel, complementa ADR-001, ADR-003 y ADR-008)

## Contexto

En el monolito el registro creaba el perfil dentro del mismo proceso (`ProfileContextFacade`). Con servicios separados, IAM no debe llamar a Profile al registrar una cuenta: el registro no puede depender de que Profile esté disponible, y el plan no define un contrato IAM → Profile para esto.

## Decisión

- IAM **no** llama a Profile en `sign-up`.
- Profile crea el perfil vacío en el **primer `GET` autenticado** de ese usuario, usando el claim `sub` del JWT como `userId` (ID opaco, string, ADR-003).
- La creación es idempotente: dos primeros `GET` concurrentes producen un único perfil (restricción única por `userId`).
- Profile conserva su propio tombstone cuando IAM ejecuta la baja (`DELETE /internal/v1/users/{userId}/personal-data/{workflowId}`, ADR-008) y rechaza crear de nuevo el perfil de un `userId` con tombstone.

## Consecuencias

- Registrar una cuenta no falla si Profile está caído.
- Un usuario sin perfil existe durante un instante entre el registro y su primer acceso a Profile; Profile no debe responder 404 por ese motivo.
- La implementación vive en `profile-service` y es responsabilidad de su dueño; este ADR solo fija el contrato.
