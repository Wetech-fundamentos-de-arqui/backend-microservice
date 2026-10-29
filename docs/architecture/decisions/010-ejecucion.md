# ADR-010: Ejecución

- **Estado:** Aceptada (decisión base para iniciar la implementación)
- **Fuente:** [Plan de microservicios](../Plan-Microservicios-WeRide.md), sección 3

## Decisión

Docker Compose es el entorno reproducible obligatorio: Gateway 8080, IAM 8081, Profile 8082, Garage 8083, Location 8084, Booking 8085, Ride 8086, Plan 8087, Notification 8088; host MySQL 3307 → contenedor 3306.

## Consecuencias

La elección de proveedor cloud no bloquea código; el equipo despliega las mismas imágenes en el proveedor al que tenga acceso y documenta costo/límites antes de publicar. No asumir Render/Aiven sin verificar cuenta y disponibilidad.
