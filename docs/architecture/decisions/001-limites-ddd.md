# ADR-001: Límites DDD

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Mantener los ocho contextos de la sección 2. Ride agrupa unlock, trip, travelhistory y problemreports. Plan administra catálogo/beneficios existentes. Profile no es dueño de datos transaccionales de viaje.

## Consecuencias

Revisar ProblemReports contra uso actual; si el reporte no es de un viaje, se conserva en Ride para esta entrega hasta aprobar otro límite. No se crea un noveno servicio.
