# ADR-004: Auth/JWT

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

IAM firma JWT con RS256; la clave privada vive solo en IAM y los servicios/Gateway verifican con clave pública. Claims incluyen sub, preferred_username, sid, iss, aud, iat y exp. Gateway revisa que sid siga activo en IAM con caché máxima de 30 segundos y falla cerrado si IAM no responde. DELETION_PENDING equivale a sesión inactiva; los tombstones locales protegen mutaciones ya en vuelo. Servicios validan firma y permisos localmente; puertos no son públicos.

## Consecuencias

Logout/revocación tarda como máximo el TTL de caché si IAM está disponible. Mantener inicialmente la duración actual del token para no cambiar vida útil sin requisito. El cambio de firma se hace en un corte coordinado; tokens viejos se vuelven a emitir con login.
