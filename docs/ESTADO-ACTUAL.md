# Jornal Backend: estado actual

Fecha: 24/11/2026 (línea base del plan `documento.md`).

> **Nota F0:** los problemas de la sección 11 quedaron corregidos en la fase F0, salvo el 5 (catálogo sin traducir en las respuestas), que el plan asigna a F1. Las rutas, nombres y migraciones vigentes están en el `README.md`.

## 1. Qué es

Backend de una plataforma parecida a Uber: personas, tiendas o empresas piden un trabajador para una tarea puntual por jornal, sin contrato formal. Es un **monolito modular** pensado para separarse en servicios.

Roles (`Rol`): `APPLICANT` (pide el trabajo), `WORKER` (lo hace) y `ADMIN` (administra).

## 2. Stack

- Java 21, Maven, Spring Boot 4.1.2.
- PostgreSQL 16 con PostGIS (`postgis/postgis:16-3.4`) y `unaccent`.
- Redis 7: en Docker y en el `pom`, sin uso en el código.
- Flyway con `ddl-auto: validate`.
- Spring Security con OAuth2 Resource Server; JWT HS256 (Nimbus) y BCrypt.
- MapStruct 1.6.3 y Lombok. Spring Modulith 2.1.1.
- `libphonenumber` (E.164), springdoc-openapi, Actuator con Prometheus.
- Sin uso todavía: WebSocket, `hibernate-spatial`, `@EnableScheduling`.
- Pruebas: Testcontainers y Modulith test.

## 3. Configuración

- `docker-compose.yaml`: `jornal-postgres` y `jornal-redis` con healthchecks; credenciales en `.env`.
- `application.yaml`: perfil `dev` por defecto; JWT `issuer: jornal`, 60 minutos; `refresh.ttl: 30d` (sin uso); versiones legales `2026-11-13`; administrador inicial por variables `ADMIN_*`; `open-in-view: false`.
- `application-dev.yaml`: Docker Compose `start-only`, SQL en logs, DEBUG, todos los endpoints de Actuator, secreto JWT de desarrollo.
- `application-prod.yaml`: todo por variables (`DB_*`, `REDIS_*`, `JWT_SECRET`, `JWT_ISSUER`); Swagger apagado; Actuator `health`, `info`, `prometheus`.
- Un secreto JWT de menos de 32 bytes impide el arranque.

## 4. Base de datos (V1 a V8)

| Migración | Contenido |
|---|---|
| V1 | PostGIS |
| V2 | `users` |
| V3 | `categories` con 8 categorías iniciales |
| V4 | `worker_profiles` y `worker_profile_categories` (sin FK a `categories`) |
| V5 | `countries` (moneda, locale, prefijo, `commission_bps` = 1000, tipos de documento). Solo CO habilitado |
| V6 | `users`: `country_code`, `locale`, versiones y fechas de términos y privacidad |
| V7 | `unaccent` y `category_translations` (es-CO, en-US) |
| V8 | `users.deleted_at` |

## 5. `shared`

`ApiException` + `ErrorCode`; `GlobalExceptionHandler` (`ProblemDetail` con `code`, `VALIDATION_ERROR`, `INTERNAL_ERROR`); `RestSecurityErrorHandlers` (401/403 en JSON); `SecurityConfig` (stateless, rutas públicas `/auth/**`, `/actuator/health`, Swagger, `GET /countries`; claim de rol → `ROLE_<rol>`); `JwtProperties`; `PageResponse<T>`; `Locales` (`es-CO`, `en-US`; `resolve()` sin uso).

## 6. `users`

- Contratos: `UserApi.findSummaryById` → `UserSummary(id, name, rol, verified, countryCode, locale)`, solo activos; `UserAccessChangedEvent(userId, eligible)`; `Rol`.
- Verificación: `VERIFIED`, `PENDING`, `REJECTED`.
- Registro con país habilitado, términos aceptados, locale y teléfono E.164; login sin filtrar emails por tiempo.
- Cuenta propia, cambio de contraseña, borrado seguro con anonimización (no para admins), administración (listar, ver, activar, desactivar, verificar, borrar).
- Eventos al activar, desactivar, verificar y borrar.
- `InitialAdminRunner` crea el admin inicial si hay `ADMIN_EMAIL` y `ADMIN_PASSWORD`.
- Errores `USER001` a `USER009`; `AUTH001` y `AUTH002` definidos sin uso.

## 7. `catalog`

- Contrato: `CatalogApi.findActiveByIds` → `CategorySummary(id, slug, name)`.
- Público: categorías activas con búsqueda. Admin: listar, ver, crear, editar, traducir, activar y desactivar.
- Búsqueda con comodines escapados y texto limitado a 60 caracteres. Errores `CAT001` a `CAT003`.

## 8. `workers`

- Contrato: `WorkerApi.findSummaryById` → `WorkerSummary`.
- Perfil propio (bio, radio 1–100 km, 1 a 5 categorías activas), disponibilidad (requiere verificación y una categoría activa), vista pública (solo activos y verificados).
- `WorkerEventListener` apaga la disponibilidad si la cuenta deja de ser elegible. Errores `WRK001` a `WRK005`.

## 9. Convenciones

Cada módulo es dueño de sus tablas; UUID sin FK entre módulos; comunicación por contratos en la raíz o eventos; DTOs con MapStruct; enum de errores por módulo; paginación máxima de 100; no se editan migraciones aplicadas.

## 10. Pruebas

`ModularidadTest`, `contextLoads` (Testcontainers), `UserServiceTest` (anonimización y activación) y `WorkerServiceTest` (`findSummaryById`).

## 11. Problemas encontrados

1. **Crítico:** `JwtService` emitía el claim `rol` y `SecurityConfig` leía `role`; todo endpoint con `hasRole` respondía 403.
2. `GET /admin/users` ordenaba por `createdAt`, pero el campo es `createdIn` (error 500).
3. `DELETE /users` desactivaba la propia cuenta sin contraseña y sin forma de reactivarla.
4. Rutas distintas a las del README (`/users`, `/workers` en vez de `/me`); README desactualizado.
5. Traducciones del catálogo, `searchActive` y `Locales.resolve` sin uso: `/categories` responde sin traducir.
6. Errores de escritura visibles para el frontend: `accesToken`, `categorysIds`, `ACCES_DENIED`, "Thr request", "does exist", "slig".
7. Errores de escritura internos: `comissionBps`, `updateAt`.
8. Paquetes en mayúscula (`Controllers`, `DTO`, `Listeners`) en `catalog` y `workers`.
9. Default `'PENDIENTE'` en `verification_state`, distinto del enum `PENDING`.

## 12. Lo que faltaba

Refresh tokens, límite de intentos, CORS y Bearer en Swagger; módulos de solicitudes, asignación con PostGIS y Redis GEO (el perfil del trabajador no tiene ubicación), calificaciones, pagos, notificaciones y verificación de identidad; pruebas de integración, Outbox, Kafka o RabbitMQ, Resilience4j, ShedLock, Dockerfile, CI/CD y caché. El orden de trabajo está en `documento.md`.
