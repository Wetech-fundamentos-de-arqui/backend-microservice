# ADR-006: Persistencia

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Un servidor MySQL 8 para la demo local, ocho esquemas y un usuario por servicio con permisos limitados. Flyway gestiona cambios. La migración inicial es de datos demo mediante seeders por servicio; no hay evidencia de datos productivos que deban conservarse.

## Consecuencias

Es separación lógica y no aislamiento físico. Para cloud se mantiene imagen/config portable; separar instancias puede ser una mejora futura.
