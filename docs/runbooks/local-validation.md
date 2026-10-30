# Validación local

El trabajo permanece en `feature/migration/local-completion`, sin envío al remoto.

## Ejecutar

1. `node scripts/init-local.mjs`.
2. `docker compose --profile web up --build -d`.
3. Esperar que `docker compose --profile web ps` muestre servicios saludables.
4. Abrir `http://localhost:4200` y registrar/iniciar una cuenta.
5. `python scripts/verify_local.py --base http://localhost:4200/api/v1 --resilience`.

El frontend vecino está en `../frontend-main/frontend-main`. Para ejecutar solo backend, omitir `--profile web`.

El verificador se limita a localhost. Genera cuentas/vehículos de prueba y comprueba dos reservas simultáneas sobre una agenda que aún no existía, repeticiones de clave, propiedad, perfil perezoso concurrente, borradores, desbloqueo, ciclo del viaje, historial/rating/pagos, reportes, notificaciones, baja con tombstones, contraseña y sesiones. `--resilience` detiene/restaura Ride y Notification y reinicia Booking para comprobar recuperación y persistencia del outbox. La revocación se observa dentro del TTL de 30 segundos del Gateway.

## Evidencia

- Backend: `mvn verify`, incluyendo la base anterior y regresiones trasladadas.
- Angular: `npm run build` y `npm test -- --watch=false --browsers=ChromeHeadless`.
- `local-verification.json` registra los grupos HTTP realmente ejecutados. Su ausencia no acredita una verificación integrada.
- Los snapshots OpenAPI se exportan de servicios en ejecución.

Resultados comprobados el 29 de octubre de 2026 (America/Lima):

| Comprobación | Resultado |
|---|---|
| Reactor Maven, 11 módulos | Correcto; 43 pruebas, sin fallos ni omisiones |
| Angular en Edge Headless | 58 pruebas correctas; las 2 regresiones de simulación también se ejecutaron por separado |
| Imagen de producción de Angular/Nginx | Construida correctamente |
| Límites de dominio y dependencias Maven | Correctos en los 8 servicios (`python scripts/check_boundaries.py`) |
| OpenAPI en ejecución | 8 documentos, 71 rutas publicadas |
| Stack Docker | MySQL, 8 servicios, Gateway y frontend saludables |
| Flujos HTTP a través de Nginx | 10 grupos verificados; incluye concurrencia, ciclo del viaje, privacidad, sesiones y recuperación |
| Validación de credenciales | 6 entradas inválidas devuelven 400 sin contraseñas rechazadas en los logs |

El [resumen de pruebas](unit-verification.json) conserva los recuentos. La revisión visual quedó pendiente: el navegador integrado no consiguió conectarse y la revisión automática de permisos no autorizó controlar Brave. Las pruebas HTTP y de Angular no acreditan una inspección visual de la interfaz.

Posteriormente se verificó en el navegador integrado el flujo del botón **Simular y finalizar**: acceso con una cuenta de prueba, botón visible en el viaje activo, finalización y apertura de la calificación. El [resultado persistido](trip-simulation.json) registra una ruta simulada de 11 puntos, 3 minutos y costo 1.50. Esta comprobación visual cubre ese flujo concreto. El botón usa las estimaciones del destino y la API real de finalización; ante errores conserva el viaje y reintenta el mismo payload.

El [informe HTTP final](local-verification.json) consolida la batería completa y la regresión posterior de IAM, indicando la procedencia de cada resultado. La [batería completa](local-integration-full.json) detectó el último defecto de IAM: consultar una cuenta eliminada devolvía 500. Después de corregirlo, la [regresión de IAM](iam-http-regression.json) verificó autenticación, baja con tombstones y contraseña/sesiones; los tres grupos pasaron. Los otros siete grupos permanecieron correctos. La [prueba de credenciales](auth-validation.json) se reproduce con `python scripts/verify_auth_format.py`.

Las colecciones persistidas de Ride son mutables y los puntos GPS usan un índice por viaje (`V2__ordered_route_points.sql`). La migración conserva las filas existentes. Los errores de los servicios mantienen su código original durante el despacho de errores de Spring Security. El chequeo de salud de Nginx usa `127.0.0.1` para comprobar el mismo listener IPv4 que sirve la aplicación.

IAM devuelve 404 para una cuenta propia que ya no existe. Los límites de longitud y bytes de credenciales se comprueban antes de acceder al repositorio o BCrypt; los errores contienen un código genérico y evitan que el manejador de validación incluya el valor rechazado de la contraseña.

Gateway limita a 5 segundos la caché DNS de rutas y del cliente de sesiones. Nginx vuelve a resolver Gateway mediante el DNS interno de Docker. Así pueden renovar destinos cuando se reemplazan endpoints de contenedores, según las opciones de [Reactor Netty](https://projectreactor.io/docs/netty/release/reference/http-client.html#_host_name_resolution_timeout) y [Nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass). La prueba de recuperación reinicia también Gateway y vuelve a consultar por el puerto del frontend.

## Diagnóstico

```powershell
docker compose --profile web ps
docker compose logs --tail 100 iam-service booking-service ride-service notification-service
docker compose logs --tail 100 api-gateway frontend
```

401 indica token/sesión inválidos; 403, propiedad o identidad interna no permitida; 409, conflicto de regla/payload; 410, usuario con tombstone; 503, dependencia no disponible. Ante un timeout de inicio/finalización/cancelación, consultar Booking antes de generar otra operación.

Los eventos `FAILED` se consultan en `GET /internal/v1/outbox/failed` del productor y reenvían con `POST /internal/v1/outbox/{eventId}/replay`, usando el secreto local de `ops` y los headers `X-Service-Name: ops` / `X-Internal-Token`. Se conserva ID/payload y Notification deduplica.

No incluir claves, `.env`, contraseñas, tokens ni payloads personales en la evidencia.
