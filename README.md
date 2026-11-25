# Jornal Backend

Plataforma tipo Uber para que personas, tiendas o empresas soliciten un ayudante o trabajador para una tarea puntual, sin contrato formal (trabajo informal por jornal). El backend está pensado como un **monolito modular** que pueda evolucionar a un sistema distribuido.

> **Estado:** MVP en desarrollo. Última actualización: 24 de noviembre de 2026.
> Existen la infraestructura, la seguridad con JWT y los módulos `region`, `users`, `catalog` y `workers`. El plan por fases para llevarlo a sistema distribuido está en [`documento.md`](documento.md) y el estado de partida en [`docs/ESTADO-ACTUAL.md`](docs/ESTADO-ACTUAL.md). Fases cerradas: **F0 (correcciones)**, **F1 (perfil regional y localización)**, **F2 P1 (sesión segura)**, **F3 (procesos, regiones e infraestructura)**, **F4 (pedidos y saga)**, **F5 (pagos)**, **F6 (asignación y seguimiento)**, **F7 (notificaciones y tiempo real)**, **F8 (facturación, tasas de cambio y calificaciones)** y **F10 (observabilidad y consola)**.

---

## Contenido

1. [Stack](#stack)
2. [Requisitos](#requisitos)
3. [Cómo correrlo](#cómo-correrlo)
4. [Perfiles y configuración](#perfiles-y-configuración)
5. [Seguridad y JWT](#seguridad-y-jwt)
6. [Estructura del proyecto](#estructura-del-proyecto)
7. [Módulos y endpoints](#módulos-y-endpoints)
8. [Migraciones](#migraciones)
9. [Convenciones](#convenciones)
10. [Estado actual](#estado-actual)
11. [Lo que falta](#lo-que-falta)
12. [Decisiones](#decisiones)
13. [Problemas frecuentes](#problemas-frecuentes)

---

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje / build | Java 21, Maven |
| Framework | Spring Boot 4.1.2 |
| Base de datos | PostgreSQL 16 + PostGIS (geolocalización) |
| Cache / geo en vivo | Redis 7 |
| Migraciones | Flyway |
| Seguridad | Spring Security + OAuth2 Resource Server, JWT firmado con HS256 |
| Mapeo de objetos | MapStruct + Lombok |
| Modularidad | Maven multimódulo + Spring Modulith 2.1.1 dentro del core |
| Mensajería | RabbitMQ 4 (AMQP), Outbox con el registro de eventos de Spring Modulith (`starter-jdbc` + `events-amqp`), sobre CloudEvents 1.0, inbox en los consumidores |
| Gateway | Nginx (enrutamiento por región, límite de tasa, id de correlación) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Observabilidad | Actuator + Micrometer (Prometheus) |
| Pruebas | Spring Boot Test, Testcontainers (PostgreSQL + PostGIS, RabbitMQ, Redis) |
| Entorno local | Docker Compose integrado con Spring Boot (desarrollo) y `infra/docker-compose.yml` (sistema completo) |

---

## Requisitos

- **JDK 21**
- **Maven 3.9+** (o el wrapper `mvnw`)
- **Docker Desktop** abierto y corriendo
- IntelliJ IDEA reciente (las versiones antiguas no interpretan bien Spring Boot 4 y muestran errores falsos de compilación)

---

## Cómo correrlo

### 1. Crear el archivo `.env`

El `.env` **no se sube a git**. Créalo en la raíz a partir de `.env.example`:

```env
POSTGRES_DB=jornalDb
POSTGRES_USER=jornalAdmin
POSTGRES_PASSWORD=<una_clave_sin_caracteres_especiales>
POSTGRES_PORT=5432

REDIS_PORT=6379
```

- Usa solo letras y números en las claves (evita `ñ`, tildes y `$`).
- Si ya tienes un PostgreSQL instalado en tu máquina usando el 5432, cambia `POSTGRES_PORT` (por ejemplo a `5433`). Spring Boot toma el puerto publicado desde Docker Compose, no hay que tocar nada más.

### 2. Levantar el core en desarrollo

El proyecto es **multimódulo**: la API regional está en `jornal-core`. Con Docker Desktop abierto:

```bash
./mvnw install -DskipTests            # la primera vez y cuando cambie jornal-common
./mvnw -pl jornal-core spring-boot:run
```

En Windows: `mvnw.cmd ...`, o ejecuta `JornalBackendApplication` desde IntelliJ (módulo `jornal-core`, perfil `dev`, directorio de trabajo la raíz del repositorio, que es el que IntelliJ pone por defecto).

Spring Boot ejecuta `docker compose up` con el `docker-compose.yaml` de la raíz (`spring.docker.compose.file: docker-compose.yaml`, relativo al directorio de trabajo; `spring-boot:run` también arranca desde la raíz), espera a que PostgreSQL, Redis y RabbitMQ estén sanos, configura las conexiones y Flyway aplica las migraciones. En `dev` no necesitas levantar los contenedores a mano. Consola de RabbitMQ: `http://localhost:15672` (guest/guest).

### Sistema distribuido completo (5 regiones + 4 subsistemas)

```bash
docker compose -f infra/docker-compose.yml up -d --build --wait                  # CO y BR + subsistemas
docker compose -f infra/docker-compose.yml --profile full up -d --build --wait   # las 5 regiones
scripts/demo-mensajeria.sh co                                                    # demostración del flujo de mensajes
scripts/demo-pagos.sh                                                            # pedido real: autoriza, anula y rechazo simulado
scripts/demo-asignacion.sh                                                       # pedido real: oferta, aceptación, seguimiento y captura
scripts/demo-notificaciones.sh                                                   # avisos en el idioma de cada persona y correos en Mailpit
scripts/demo-facturacion.sh                                                      # pedido hasta COMPLETED, comprobante, calificaciones y reporte en USD
scripts/demo-observabilidad.sh                                                   # id de correlación en los logs, Prometheus, Grafana y consola con un servicio caído
scripts/falla-pagos.sh                                                           # pruebas de falla (F11): falla-*.sh, ver «Pruebas de falla y de localización»
scripts/prueba-localizacion.sh                                                   # prueba de localización: pedido D4 en los 5 países a la hora fija
scripts/seed-demo.sh                                                             # cliente, comercio y trabajador listos en cada región (clave Demo12345)
docker compose -f infra/docker-compose.yml --profile full down                   # apagar (conserva datos)
```

| Servicio | Dirección |
|---|---|
| Gateway (entrada única) | `http://localhost:8000/api/{co,br,us,fr,jp}/...` o `http://co.jornal.localhost:8000/...`; con TLS: `https://localhost:8443/...` y `https://co.jornal.localhost:8443/...` |
| Estado de un subsistema | `http://localhost:8000/subsystems/payments/status` (también dispatch, notifications, billing) |
| Swagger de una región | `http://localhost:8000/api/co/swagger-ui.html` |
| RabbitMQ (consola) | `http://localhost:15673` (jornal / jornal-dev-password) |
| Mailpit (correos de los avisos) | `http://localhost:8025` |
| WebSocket de avisos (STOMP) | `ws://localhost:8000/subsystems/notifications/ws` o `wss://localhost:8443/subsystems/notifications/ws` (JWT en el CONNECT) |
| Prometheus / Grafana | `http://localhost:9090` / `http://localhost:3000` (admin / admin; tablero «Jornal: sistema distribuido» en la carpeta Jornal) |
| PostgreSQL | `localhost:55432` (bases `jornal_co` ... `jornal_jp` y una por subsistema) |

Administrador de cada región: `admin@{co,br,us,fr,jp}.jornal.test` / `Admin12345`. Si una región está apagada, el gateway responde `503 SERVICE_UNAVAILABLE` solo para ella. Cada JVM corre con `-Xmx256m` (unos 420 MB por core).

**TLS de desarrollo:** el gateway (`infra/nginx/Dockerfile`) genera al primer arranque un certificado autofirmado para `localhost`, `jornal.localhost` y `*.jornal.localhost` (TLS 1.2 y 1.3, HTTP/2). Lo guarda en el volumen `jornal-certs`, así la excepción del navegador sobrevive a los reinicios. El gateway pasa `X-Forwarded-Proto` a los servicios. Para confiar en él sin avisos, sácalo con `docker compose -f infra/docker-compose.yml cp gateway:/etc/nginx/certs/jornal-dev.crt .` e impórtalo como certificado de confianza, o úsalo en curl con `--cacert jornal-dev.crt`. HTTP en el puerto 8000 sigue disponible para los scripts y el proxy de Angular. En producción se usan certificados públicos por dominio regional.

### 3. Comprobar que funciona

- Contenedores: `docker compose ps`
- Salud: `http://localhost:8080/actuator/health`
- Swagger: `http://localhost:8080/swagger-ui.html`

### 4. Probar el flujo básico

En Windows usa `curl.exe` (con `.exe`) y escapa las comillas del JSON, o prueba desde Postman/Hoppscotch. Los nombres de los campos deben coincidir con los de `RegisterRequest`.

```bash
# 1. Registrar en la región de la instancia (APP_REGION, por defecto CO); acceptedTerms es obligatorio
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" -H "Accept-Language: es-CO" \
  -d '{"email":"test@jornal.com","password":"Clave12345","givenName":"Ana","familyName":"Prueba","rol":"WORKER","acceptedTerms":true}'

# 2. Login (devuelve accessToken, tokenType y expiresIn)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@jornal.com","password":"Clave12345"}'

# 3. Usar el token
curl http://localhost:8080/users/me -H "Authorization: Bearer <accessToken>"
```

Respuestas esperadas: `201` al registrar, `409` con `USER001_EMAIL_REGISTERED` si el email ya existe, `400` con `VALIDATION_ERROR` si el cuerpo es inválido, `401` si el token falta o es inválido.

### 5. Crear el primer administrador

Un usuario no puede registrarse como `ADMIN` por el endpoint público. Al arrancar, `InitialAdminRunner` crea el administrador (verificado) si están definidas `ADMIN_EMAIL` y `ADMIN_PASSWORD` y ese email no existe. Se crea en el país de la instancia (`APP_REGION`). Opcionales: `ADMIN_GIVEN_NAME`, `ADMIN_FAMILY_NAME` y `ADMIN_LOCALE`. Ver `.env.example`.

### Región e idioma

- Cada instancia atiende **un país**: `APP_REGION` (`CO`, `BR`, `US`, `FR`, `JP` o `DE`; por defecto `CO`). Solo registra usuarios de ese país y usa su perfil regional. Ejemplo: `APP_REGION=JP ./mvnw spring-boot:run`.
- El idioma de cada respuesta sale del header `Accept-Language`: es-CO, pt-BR, en-US, fr-FR y ja-JP, que tienen todos los textos traducidos, y además los idiomas que declare algún perfil regional (`supported_locales`, por ejemplo de-DE). Si falta o no está soportado, se usa el idioma del perfil y luego en-US. Los mensajes de error se traducen; el `code` no cambia. Los textos sin traducción salen del respaldo en-US.

En desarrollo también se puede promover un usuario en la base y **volver a hacer login**, porque el rol viaja dentro del token:

```sql
update users set rol = 'ADMIN' where email = 'tu@correo.com';
```

### 6. Comandos útiles de Docker

```bash
docker compose ps                 # estado
docker compose logs -f postgres   # logs de la base
docker compose down               # detener (conserva datos)
docker compose down -v            # detener y BORRAR datos (volumen pgdata)

# Consultar la base
docker exec -it jornal-postgres psql -U jornalAdmin -d jornalDb
```

---

## Perfiles y configuración

| Archivo | Uso |
|---|---|
| `application.yaml` | Común. Perfil por defecto `dev` (variable `SPRING_PROFILES_ACTIVE`) |
| `application-dev.yaml` | Desarrollo con Docker Compose, logs detallados, secreto JWT por defecto solo para desarrollo |
| `application-prod.yaml` | Producción: todo por variables de entorno, Swagger desactivado |

Variables de producción:

| Variable | Para qué |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Base de datos |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis |
| `JWT_SECRET` | Clave de firma, **mínimo 32 caracteres** (por ejemplo `openssl rand -base64 48`) |
| `JWT_ISSUER` | Emisor del token (por ejemplo `jornal-prod`) |

Cambiar de perfil:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
java -jar app.jar --spring.profiles.active=prod
```

En producción no se usa el Docker Compose de desarrollo ni hay secretos en el repositorio. Si falta `JWT_SECRET` o `JWT_ISSUER`, la aplicación no arranca, a propósito.

---

## Seguridad y JWT

- **Registro y login** por email y contraseña (BCrypt).
- **Access token JWT** firmado con HS256, vigente **15 minutos**. Claims: `sub` (id del usuario), `role`, `region`, `email`, `name`, `iss`, `iat`, `exp`. El emisor y la vigencia salen de la configuración (`jornal.security.jwt.*`). La hora sale del bean `Clock` (`TimeConfig`).
- **Refresh token** opaco de 30 días (`jornal.security.refresh.ttl`), guardado solo como hash SHA-256 en `refresh_tokens`. `POST /auth/refresh` entrega un par nuevo y revoca el anterior (rotación). Si llega un refresh token ya rotado, se revoca toda su familia (posible robo). `POST /auth/logout` revoca la sesión. Cambiar la contraseña, bloquear o borrar la cuenta revoca todas sus sesiones.
- **Validación**: el resource server valida firma, expiración, emisor y que `region` sea la de esta instancia (un token de otra región recibe 401). El claim `role` se convierte en la autoridad `ROLE_<rol>`.
- **CORS** para el frontend: `jornal.cors.allowed-origins` (variable `CORS_ALLOWED_ORIGINS`; por defecto `http://localhost:4200` y `https://*.jornal.localhost`). Sin cookies: el token viaja en `Authorization`.
- **Swagger** con botón *Authorize* (esquema Bearer JWT): pega el `accessToken` del login.
- **Autorización por rol** con `@PreAuthorize` (por ejemplo `hasRole('ADMIN')`). `SecurityRolesTest` lo prueba con tokens reales.
- **Errores 401 y 403** como `ProblemDetail` con `code` (`UNAUTHORIZED`, `ACCESS_DENIED`).
- **Rutas públicas**: `/auth/**`, `GET /countries`, `GET /region-profile/**`, `/actuator/health`, Swagger y `/v3/api-docs/**`. Todo lo demás exige token.
- El login responde con el mismo error ante email inexistente, clave incorrecta o usuario desactivado, y compara siempre contra un hash para no revelar qué emails existen por tiempos de respuesta.
- **Límite de intentos de login** (contado en Redis, compartido por todas las instancias de la región): 5 fallos por cuenta o 20 por dirección IP dentro de 15 minutos responden `429 AUTH002_TOO_MANY_ATTEMPTS` hasta que vence la ventana, aunque la clave siguiente sea correcta. Un login correcto borra el contador de la cuenta. El correo se guarda en Redis como hash SHA-256. Si Redis no responde, no se bloquea a nadie (falla abierta). Variables: `LOGIN_MAX_ATTEMPTS`, `LOGIN_MAX_ATTEMPTS_PER_IP`, `LOGIN_WINDOW`. Lo prueba `LoginRateLimitIntegrationTest`.

Pendiente (P2 de la fase F2): firma RS256 con JWKS para que los subsistemas validen el token sin el secreto (D10; respaldo: HS256 compartido). Un access token ya emitido vive hasta 15 minutos aunque la cuenta se bloquee.

---

## Estructura del proyecto

```
jornal
├── pom.xml                        # padre multimódulo (jornal-parent)
├── Dockerfile                     # multietapa; --build-arg MODULE=jornal-core|jornal-payments|...
├── docker-compose.yaml            # desarrollo del core: PostGIS, Redis y RabbitMQ
├── infra/                         # sistema completo: docker-compose.yml, nginx, postgres, prometheus, grafana
├── scripts/                       # demo-mensajeria.sh (Outbox → RabbitMQ → inbox), demo-pagos.sh (F5)
├── .env / .env.example            # credenciales locales (.env NO se versiona)
├── documento.md                   # plan de desarrollo por fases (F0 a F11)
├── docs/ESTADO-ACTUAL.md          # estado de partida del backend
├── jornal-common/                 # paquete jornal.common (fuera de com.jornal para que el core no lo escanee)
│   ├── events/                    # CloudEvent (sobre 1.0), EventTypes
│   ├── messaging/                 # Topology (exchange, colas por subsistema y región, DLQ), MessagingConfig
│   └── subsystem/                 # Inbox, CloudEventListener, CloudEventHandler, RegionProfileCache, /status
├── jornal-payments/               # subsistema de pagos: 3 flujos simulados, libro particionado por región
├── jornal-dispatch/               # subsistema de asignación: candidatos, ofertas, reserva atómica, posición en vivo (Redis)
├── jornal-notifications/          # subsistema (esqueleto)
├── jornal-billing/                # subsistema (esqueleto)
└── jornal-core/src                # core regional (una instancia por país)
    ├── main
    │   ├── java/com/jornal
    │   │   ├── JornalBackendApplication.java
    │   │   ├── shared        # ApiException, ErrorCode, GlobalExceptionHandler, RestSecurityErrorHandlers,
    │   │   │                 # SecurityConfig, JwtProperties, TimeConfig (Clock), Locales, PageResponse
    │   │   ├── region        # raíz: RegionApi, RegionProfileView, RegionProfileUpdated, NameOrder, TipMode,
    │   │   │   │             #       AddressField, TaxRule, PaymentMethod, DocumentType, RegionProfile...
    │   │   │   ├── controllers   # RegionController (público), AdminRegionController
    │   │   │   ├── services      # RegionService, DocumentValidators (estrategias), RegionStartupCheck
    │   │   │   ├── web           # RegionLocaleResolver (Accept-Language → perfil → en-US)
    │   │   │   ├── dto
    │   │   │   └── enums         # ErrorRegion
    │   │   ├── users         # raíz: UserApi, UserSummary, UserAccessChangedEvent, Rol, User, Address, BusinessProfile...
    │   │   │   ├── controllers   # Auth, User, Address, Business, AdminUser y Country
    │   │   │   ├── services      # UserService, AddressService, BusinessService, AuthService, JwtService,
    │   │   │   │                 # PhoneNormalizer, InitialAdminRunner
    │   │   │   ├── dto
    │   │   │   └── enums         # ErrorUser, VerificationState
    │   │   ├── catalog       # raíz: CatalogApi, CategorySummary, Category, CategoryService, traducciones, precios
    │   │   │   ├── controllers   # CategoryController, AdminCategoryController
    │   │   │   ├── dto
    │   │   │   └── enums         # ErrorCatalog
    │   │   ├── integration   # eventos internos → CloudEvents en el Outbox; reintento del Outbox cada 30 s
    │   │   └── workers       # raíz: WorkerApi, WorkerSummary, WorkerProfile, WorkerService
    │   │       ├── controllers   # WorkerController
    │   │       ├── listeners     # WorkerEventListener
    │   │       ├── dto
    │   │       └── enums         # ErrorWorker
    │   └── resources
    │       ├── application.yaml, application-dev.yaml, application-prod.yaml
    │       ├── messages*.properties  # errores en en (respaldo), es, pt, fr y ja
    │       └── db/migration      # V1 a V14
    └── test/java/com/jornal
        ├── ModularidadTest.java              # límites entre módulos
        ├── NoCountryConditionalsTest.java    # falla si hay literales de país en el código
        ├── SecurityRolesTest.java            # 200/401/403 por rol con JWT reales, errores traducidos
        ├── LocalizationIntegrationTest.java  # criterio de F1 en CO con PostgreSQL real (Docker)
        ├── JapanRegionIntegrationTest.java   # la misma app con APP_REGION=JP (Docker)
        ├── AuthFlowIntegrationTest.java      # sesión: refresh, reuso, logout (Docker)
        ├── EventExternalizationIntegrationTest.java  # Outbox → RabbitMQ con CloudEvents (Docker)
        ├── JornalBackendApplicationTests     # arranque con Testcontainers (Docker)
        ├── region/services/DocumentValidatorsTest.java
        ├── shared/LocalesTest.java
        ├── users/services/UserServiceTest.java
        └── workers/WorkerServiceTest.java
```

---

## Módulos y endpoints

Todos los endpoints exigen token salvo los marcados como públicos.

### Auth y países (`users`)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `POST /auth/register` | público | Crear cuenta en el país de la instancia (no permite `ADMIN`). `givenName` y `familyName` (el nombre se compone con `nameOrder`), `acceptedTerms=true`, `country` opcional (debe coincidir), teléfono validado y normalizado a E.164, `address` opcional validada con el esquema del país. Para `BUSINESS`, `business` obligatorio: `legalName`, `taxIdType` de clase TAX, `taxIdNumber` validado con su estrategia y `billingAddress` |
| `POST /auth/login` | público | Devuelve `{accessToken, tokenType, expiresIn, refreshToken, refreshExpiresIn}` |
| `POST /auth/refresh` | público | `{"refreshToken"}` → par nuevo; el refresh token usado deja de servir |
| `POST /auth/logout` | público | `{"refreshToken"}` → revoca la sesión (204 aunque el token no exista) |
| `GET /countries` | público | Países habilitados con moneda, locale, prefijo y tipos de documento |

### Users

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /users/me` | autenticado | Ver la propia cuenta (incluye `consentUpToDate`) |
| `PATCH /users/me` | autenticado | Editar `givenName`, `familyName`, teléfono y locale |
| `GET /users/me/addresses` | autenticado | Direcciones propias, con campos en el orden del país y `formatted` |
| `POST /users/me/addresses` | autenticado | Agregar dirección (`{"label", "fields": {...}}`, máximo 10) |
| `DELETE /users/me/addresses/{id}` | autenticado | Borrar (no si es la dirección de facturación) |
| `GET /users/me/business` y `PUT` | BUSINESS | Ver y editar el perfil empresarial |
| `PUT /users/me/password` | autenticado | Cambiar la contraseña (exige la actual y una distinta) |
| `POST /users/me/deletion` | autenticado | Borrado seguro con confirmación de contraseña (anonimiza; no aplica a admins) |
| `GET /admin/users?rol=&page=&size=` | ADMIN | Listar con paginación (máx. 100), filtro por rol, sin eliminados, más recientes primero |
| `GET /admin/users/{id}` | ADMIN | Ver un usuario |
| `POST /admin/users/{id}/deactivate` y `/activate` | ADMIN | Bloquear y reactivar |
| `DELETE /admin/users/{id}` | ADMIN | Borrado seguro (anonimización) |
| `PATCH /admin/users/{id}/verification` | ADMIN | Cambiar el estado de verificación (`VERIFIED`, `PENDING`, `REJECTED`) |

Activar, desactivar, cambiar la verificación y borrar publican `UserAccessChangedEvent(userId, eligible)`.

### Catalog

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /categories?q=` | autenticado | Categorías activas (hasta 20) en el idioma de la petición, ordenadas según ese idioma, con `price` de la región (unidad, monto en unidad menor, moneda y decimales). `q` busca por slug o por el nombre en cualquier idioma, sin tildes |
| `GET /admin/categories?q=&active=&page=&size=` | ADMIN | Listado con filtros y paginación |
| `GET /admin/categories/{id}` | ADMIN | Ver una categoría |
| `POST /admin/categories` | ADMIN | Crear (slug kebab-case, único e inmutable) |
| `PUT /admin/categories/{id}` | ADMIN | Editar nombre y descripción |
| `PUT /admin/categories/{id}/translations/{locale}` | ADMIN | Crear o actualizar la traducción (es-CO, pt-BR, en-US, fr-FR, ja-JP) |
| `PUT /admin/categories/{id}/price` | ADMIN | Precio en la región de la instancia: `{"unit": "DAY", "amountMinor": 90000}`; la moneda sale del perfil |
| `POST /admin/categories/{id}/deactivate` y `/activate` | ADMIN | Ocultar y mostrar |

Traducción: idioma pedido → idioma del perfil → en-US → nombre base.

**Caché en Redis:** `GET /categories` se sirve desde Redis con TTL de 10 minutos (`CATALOG_CACHE_TTL`; `0s` la desactiva). La clave lleva la región, el idioma pedido, el texto buscado y una «generación» del catálogo (`jornal:<región>:catalog:gen<n>:<idioma>:<q>`). Crear, editar, activar o desactivar una categoría, cambiar una traducción o un precio, o cambiar el perfil regional sube la generación al confirmar la transacción, y así el cambio se ve de inmediato. Si Redis no responde, se lee de la base. La métrica `jornal_catalog_cache_total{result="hit|miss|error"}` muestra la tasa de aciertos. Lo prueba `CatalogCacheIntegrationTest`.

### Region (perfil regional)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /region-profile` | público | Perfil del país de la instancia: idiomas, moneda y decimales, zonas horarias, formatos, esquema de dirección, orden del nombre, impuestos, comprobante, documentos, ley de datos, medios de pago, propina, comisión, `version` y `reviewStatus` |
| `GET /region-profile/holidays?year=` | público | Festivos del año (por defecto, el año actual en la zona del país) |
| `PUT /admin/region-profile` | ADMIN | Reemplaza el perfil completo. Exige la `version` leída (409 `REG005_VERSION_CONFLICT` si cambió), sube la versión y publica `RegionProfileUpdated` |
| `PUT /admin/region-profile/holidays/{fecha}` y `DELETE` | ADMIN | Crear, editar o borrar un festivo |

Validadores de documentos por nombre (`documentTypes[].validator`): `regex`, `nit-dv`, `cpf`, `cnpj`, `luhn`, `jp-mynumber`, `jp-corporate`.

### Workers

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /workers/me` | WORKER | Ver el propio perfil |
| `PUT /workers/me` | WORKER | Crear o reemplazar el perfil: `bio`, `workRadiusKm` (1 a 100), `categoryIds` (1 a 5, activas) y ubicación base `baseLatitude`/`baseLongitude` |
| `PATCH /workers/me/availability` | WORKER | `{"available": true}` o `false`; para activarla hay que estar verificado, tener una categoría activa y ubicación base (`WRK006`) |
| `GET /workers/{userId}` | autenticado | Vista pública: nombre, bio, calificación y categorías (solo activos y verificados) |

Si la cuenta deja de ser elegible (desverificada, desactivada o eliminada), `WorkerEventListener` apaga la disponibilidad.

---

### Requests (pedidos y saga)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `POST /requests/quote` | APPLICANT, BUSINESS | Cotiza el carrito sin guardar: líneas (servicio, desplazamiento, impuesto, propina), totales en unidad menor, `tipMode` y propinas sugeridas del país |
| `POST /requests` | APPLICANT, BUSINESS | Crea el pedido en `PENDING_PAYMENT`. Exige `Idempotency-Key`; repetirla devuelve el mismo pedido (200) |
| `GET /requests?status=` | autenticado | Propios: como cliente, los pedidos; como trabajador, los asignados |
| `GET /requests/{id}` y `/history` | cliente, trabajador asignado o ADMIN | Detalle e historial de estados (origen, destino, instante UTC, actor, motivo) |
| `PATCH /requests/{id}` | cliente | Antes de la asignación: recotiza (unidades, fecha, propina), sube `paymentAttempt` y vuelve a `PENDING_PAYMENT` |
| `POST /requests/{id}/cancel` | cliente (hasta `ASSIGNED`) o ADMIN (incidencias) | Cancela con compensación de pago |
| `POST /requests/{id}/en-route`, `/start`, `/finish` | WORKER asignado | `ASSIGNED → EN_ROUTE → IN_PROGRESS → PENDING_CONFIRMATION` |
| `POST /requests/{id}/confirm` | cliente | Pide la captura del pago; se confirma solo a las 24 h (`REQUESTS_AUTO_CONFIRM_AFTER`) |
| `POST /requests/{id}/rating` | cliente o trabajador del pedido | Solo en `COMPLETED`: `{"score": 1-5, "comment"}`, una vez por sentido (409 `REQ011_NOT_COMPLETED` o `REQ012_ALREADY_RATED`). La del cliente actualiza el promedio del trabajador y su foto en asignación |
| `GET /requests/{id}/ratings` | participantes o ADMIN | Calificaciones del pedido en ambos sentidos |
| `GET /admin/requests?status=` | ADMIN | Todos los pedidos de la región |

Cuerpo de cotizar y crear (`RequestDraft`): `categoryId`, `units` (1 a 30), `scheduledLocal` (`2026-11-15T20:30`) y `timezone` (una zona permitida del país; por defecto la del perfil), `address` (campos del país), `latitude`, `longitude`, `paymentMethod` (del perfil), `tipMinor` o `tipPercent`, `notes`. La hora se guarda en UTC con la zona IANA del lugar; el lugar, además, como punto PostGIS.

**Precio** (`PricingService`, todo en enteros): subtotal = precio × unidades; más desplazamiento del perfil; impuesto sobre ambos (si está incluido en el precio se informa y no se suma); propina según `tipMode` (`HIDDEN` no admite propina).

**Saga** (`RequestSaga`, cola `core.<región>`), con los mensajes en el Outbox:

| El core recibe | Paso | El core emite |
|---|---|---|
| (creación) | → `PENDING_PAYMENT` | `jornal.request.created.v1` (pagos) |
| `jornal.payment.authorized.v1` | `PENDING_PAYMENT → SEARCHING_WORKER` | `jornal.request.assignment-requested.v1` (asignación) |
| `jornal.payment.failed.v1` | `PENDING_PAYMENT → CANCELLED` | — |
| `jornal.dispatch.worker-assigned.v1` | `SEARCHING_WORKER → ASSIGNED` | — |
| `jornal.dispatch.assignment-failed.v1` | `SEARCHING_WORKER → CANCELLED` | `jornal.request.cancelled.v1` (pagos anula o reembolsa) |
| (confirmación) | `PENDING_CONFIRMATION` | `jornal.request.capture-requested.v1` (pagos) |
| `jornal.payment.captured.v1` + `jornal.billing.receipt-issued.v1` | `→ COMPLETED` | — |

Cada cambio emite además `jornal.request.status-changed.v1` (notificaciones). Las respuestas de un intento de pago viejo, tardías o duplicadas se ignoran. Plazos (`RequestTimeouts`, una sola instancia con ShedLock): sin respuesta de pagos en 2 min → `CANCELLED`; la hora pasa sin trabajador → `EXPIRED` con compensación; sin confirmación en 24 h → se confirma solo. Concurrencia: versión optimista (`@Version`); un choque devuelve 409 `CONCURRENT_UPDATE` en la API y se reintenta en los mensajes.

### Dispatch (subsistema `jornal-dispatch`): asignación y seguimiento

Consume `jornal.worker.updated.v1` (foto del trabajador que publica el core al cambiar su perfil, disponibilidad o verificación), `jornal.request.assignment-requested.v1`, `jornal.request.cancelled.v1` y `jornal.request.status-changed.v1`.

- **Candidatos**: misma categoría, elegibles y disponibles, dentro de su radio, sin reserva, sin otra oferta pendiente y a los que no se les ofreció ya el pedido. Posición: la en vivo si está vigente (Redis); si no, la base del perfil. Orden: distancia redondeada al km, menos pedidos asignados hoy (balanceo de carga) y mejor calificación.
- **Oferta secuencial (D7)**: un candidato a la vez con plazo (`DISPATCH_OFFER_TIMEOUT`, 45 s). Si rechaza o vence, pasa al siguiente; tras `DISPATCH_MAX_OFFERS` (5) o sin candidatos durante `DISPATCH_SEARCH_TIMEOUT` (120 s) responde `jornal.dispatch.assignment-failed.v1` y el core compensa el pago. Cada oferta sale como `jornal.dispatch.offer-created.v1` (para notificaciones).
- **Reserva atómica**: tabla `reservations` con el trabajador como clave y el pedido único: de dos aceptaciones simultáneas solo una inserción gana. Se libera cuando el pedido termina o se cancela.
- **Posición en vivo**: `PUT /locations/me` cada 5 a 10 s; se guarda en Redis (`geo:live:<región>` y una clave con TTL de 60 s) y sale como `jornal.dispatch.worker-location.v1` por el exchange no durable `jornal.live` (gana el último valor).
- **Seguridad**: dispatch valida el mismo JWT del core (HS256 compartido, respaldo de D10); la región sale del claim `region`.

| Ruta (gateway: `/subsystems/dispatch/...`) | Acceso | Descripción |
|---|---|---|
| `GET /offers/me` | WORKER | Ofertas pendientes con su plazo, distancia, categoría, dirección y hora |
| `POST /offers/{id}/accept` | WORKER | Acepta: reserva atómica y `worker-assigned` (409 `OFFER_EXPIRED` o `WORKER_BUSY`) |
| `POST /offers/{id}/reject` | WORKER | Rechaza: la oferta pasa al siguiente candidato |
| `PUT /locations/me` | WORKER | `{"latitude", "longitude"}`: posición en vivo |
| `GET /tracking/{requestId}` | cliente o trabajador del pedido | Estado, posición del trabajador y distancia al lugar |

### Notifications (subsistema `jornal-notifications`): avisos y tiempo real

Consume `jornal.request.status-changed.v1` (cliente y trabajador del pedido) y `jornal.dispatch.offer-created.v1` (trabajador), y reenvía la posición en vivo de `jornal.live`.

- **Contacto**: correo, teléfono, idioma y zona se piden al core de la región (`GET /internal/contacts/{id}` con el header `X-Internal-Token`, 3 intentos con espera creciente). El dato personal no se copia al subsistema. Si el core no responde, el mensaje se reintenta y después va a `jornal.dead-letter`. El gateway responde 404 a `/internal/**`.
- **Plantillas** (`templates/notifications_<idioma>.properties`): ICU MessageFormat con plurales CLDR (`{seconds, plural, one {# segundo} other {# segundos}}`) y números del idioma; la hora se escribe en la zona del destinatario (`jue, 26 nov 2026, 8:00 a. m. COT`). Clave `<plantilla>.<audiencia>.title|body`; si una audiencia no tiene plantilla, no se le avisa. Respaldo: inglés.
- **Canales**: en la app (WebSocket y bandeja), push simulado y correo (Mailpit) siempre; SMS simulado solo en avisos urgentes (oferta, asignado, en camino). Cada entrega queda en `notifications` una vez por evento, destinatario y canal (`SENT`, `FAILED` o `SKIPPED`).
- **WebSocket STOMP** en `/ws`: el JWT del core va en el header `Authorization` del CONNECT. Temas: `/topic/users.<id>` (solo el propio usuario) y `/topic/requests.<id>` (estado y posición en vivo, solo el cliente y el trabajador del pedido). Los clientes no pueden enviar mensajes.

| Ruta (gateway: `/subsystems/notifications/...`) | Acceso | Descripción |
|---|---|---|
| `GET /notifications/me?limit=20` | usuario autenticado | Bandeja de avisos, el más reciente primero |
| `POST /notifications/{id}/read` | dueño del aviso | Marca un aviso como leído |
| `GET /deliveries?requestId=` | interna (demostración) | Entregas del pedido por canal y estado, sin correo ni teléfono |
| `ws: /ws` | JWT en el CONNECT | WebSocket STOMP |

### Billing (subsistema `jornal-billing`): comprobantes, tasas y reporte

Consume `jornal.payment.captured.v1` (que trae los datos del pedido: líneas, impuestos con su tasa, tipo y prefijo de comprobante del perfil, idioma del país e identificación fiscal del comercio) y responde `jornal.billing.receipt-issued.v1`; con eso y la captura el core cierra el pedido en `COMPLETED`.

- **Comprobante por país** (`receipt_type` del perfil): `DIAN_E_INVOICE` (CO, `FE-`), `NFS_E` (BR, `NFSE-`), `SALES_RECEIPT` (US, `RCPT-`), `FACTURE` (FR, `FA-`) y `QUALIFIED_INVOICE` (JP, `INV-`). Numeración consecutiva por región y tipo (`receipt_sequences`, dentro de la transacción: sin huecos). Emisor por región en configuración (datos ficticios, `VERIFICAR`). Tabla `receipts` particionada por región.
- **Presentación**: montos con CLDR y la escala de la moneda del perfil, impuesto con su tasa e indicación de incluido o sumado, fecha y hora en la zona del lugar del trabajo, textos en el idioma del país (o `?lang=`).
- **Tasas de cambio** (P2): par, tasa, fuente y fecha. `RateProvider` es la interfaz del proveedor real; `SimulatedRateProvider` carga tasas de referencia contra USD al arrancar y cada día. El administrador puede cargar la tasa oficial a mano. La conversión usa la tasa directa, la inversa o la cruzada por USD, y el reporte dice cuál.

| Ruta (gateway: `/subsystems/billing/...`) | Acceso | Descripción |
|---|---|---|
| `GET /receipts/{requestId}?lang=` | cliente, trabajador del pedido o ADMIN | Comprobante en JSON con los valores ya formateados |
| `GET /receipts/{requestId}/html?lang=` | ídem | Comprobante en HTML imprimible |
| `GET /admin/receipts?region=` | ADMIN | Últimos comprobantes |
| `GET /admin/rates`, `POST /admin/rates/refresh`, `POST /admin/rates` | ADMIN | Ver, cargar del proveedor o cargar a mano (`base`, `quote`, `rate`, `source`, `asOf`) |
| `GET /admin/reports/revenue?currency=USD&from=&to=` | ADMIN | Ingresos por región en moneda local y convertidos, con la tasa usada (par, tasa, fuente, fecha y método) |

### Payments (subsistema `jornal-payments`)

Consume `jornal.request.created/repriced/capture-requested/cancelled` de la cola `payments.<región>` y responde con `jornal.payment.*` por su outbox. El flujo lo elige el medio de pago del perfil regional:

| Flujo | Medios (perfil) | Al crear el pedido | Al confirmar | Si se cancela |
|---|---|---|---|---|
| `AUTH_CAPTURE` | tarjeta, PayPal | autoriza (retiene) → `payment.authorized` | captura | anula la retención → `payment.voided` |
| `CHARGE_REFUND` | PSE, Nequi, Pix, PayPay | cobra de inmediato → `payment.authorized` | se marca capturado | reembolsa → `payment.refunded` |
| `ASYNC_VOUCHER` | boleto, konbini | emite voucher (pendiente); al pagarse → `payment.authorized`; si vence → `payment.failed` | se marca capturado | anula el voucher o reembolsa |

- **Idempotencia**: un cobro por pedido e intento (`UNIQUE (region, request_id, attempt)`) además del inbox; el proveedor recibe el id del pago como clave de idempotencia. Al recotizar, el intento anterior se anula o reembolsa antes de cobrar el nuevo.
- **Liquidación** al capturar (`ledger_entries`): `WORKER_PAYOUT` = subtotal − comisión (`commissionBps`), `TIP_PAYOUT` = 100 % de la propina, `PLATFORM_FEE` = comisión + desplazamiento + impuesto sumado. Reembolsos en negativo. Tablas `payments` y `ledger_entries` particionadas por región (LIST).
- **Interfaz real** (`jornal.payments.gateway.PaymentGateway`): `start(order)`, `capture(ref, amount)`, `cancel(ref)`, `refund(ref, amount)`, todas con clave de idempotencia. Un adaptador real por proveedor y flujo (por ejemplo Wompi o Mercado Pago para CO/BR, Stripe para US/FR, PayPay y konbini para JP) implementa estos cuatro métodos con el API del proveedor y recibe su webhook de confirmación; la lógica de pagos y la saga no cambian. En el prototipo, `SimulatedGateways` cubre los tres flujos.
- **Simulador** (variables `PAYMENTS_FAILURE_RATE`, `PAYMENTS_DECLINE_METHODS`, `PAYMENTS_LATENCY`, `PAYMENTS_VOUCHER_AUTO_PAY_AFTER`, `PAYMENTS_VOUCHER_EXPIRE_AFTER`) y en caliente con `PUT /simulator`.

| Ruta interna (gateway: `/subsystems/payments/...`) | Descripción |
|---|---|
| `GET /payments?requestId=` | Intentos de pago del pedido y su libro de movimientos |
| `GET /ledger/summary` | Totales por región, moneda y tipo (base del reporte de ingresos) |
| `GET /simulator` y `PUT /simulator` | Ver y cambiar rechazos, latencia y tiempos de vouchers |
| `POST /webhooks/vouchers/{paymentId}/paid` | Webhook simulado: el cliente pagó el voucher |
| `GET /status` | Colas, mensajes procesados y perfiles regionales recibidos |

Los vouchers se dan por pagados solos a los 10 s y vencen a los 90 s, por debajo de los 2 min que el core espera a pagos. Las rutas internas no tienen autenticación en el prototipo.

**Outbox de los subsistemas** (`jornal-common`): `SubsystemOutbox` guarda el mensaje en la tabla `outbox` en la misma transacción y `OutboxRelay` lo envía cada segundo (`FOR UPDATE SKIP LOCKED`, sirve con réplicas). Si RabbitMQ cae, los mensajes esperan.

### Observabilidad y consola

- **Id de correlación:** el gateway pone `X-Correlation-Id`, o respeta el que manda el cliente. Cada proceso lo guarda en el MDC y lo escribe en cada línea de log (`[cid=...]`), lo devuelve en la respuesta y lo pone como `correlationid` en cada mensaje. Los consumidores lo recuperan del mensaje. Para seguir un pedido en todos los procesos: `docker compose -f infra/docker-compose.yml logs | grep "cid=<id>"`.
- **Métricas para Prometheus** (todos los procesos exponen `/actuator/prometheus` solo dentro de la red interna; el gateway responde 404 a `/actuator/**` salvo la salud):

  | Métrica | Etiquetas | Qué mide |
  |---|---|---|
  | `jornal_requests` | `status`, `region` | Pedidos por estado (se refresca cada 15 s) |
  | `jornal_dispatch_assignment_time_seconds` | `region` | Tiempo desde que se pide trabajador hasta la aceptación (histograma) |
  | `jornal_dispatch_assignment_failed_total` | `region`, `reason` | Pedidos sin trabajador |
  | `jornal_payments_failures_total` | `region`, `method`, `reason` | Pagos rechazados o vouchers vencidos |
  | `jornal_messages_total` | `consumer`, `type`, `region`, `outcome` (`ok`, `duplicate`, `error`) | Mensajes procesados, descartados por el inbox y fallidos (cada reintento cuenta) |
  | `rabbitmq_detailed_queue_messages` | `queue` | Profundidad de cada cola (plugin de Prometheus de RabbitMQ) |

- **Grafana:** tablero «Jornal: sistema distribuido» provisionado desde `infra/grafana/dashboards/jornal.json`. Muestra:
  - servicios arriba o caídos;
  - pedidos por estado y región;
  - p50 y p95 de asignación;
  - fallas de pago;
  - asignaciones fallidas;
  - mensajes;
  - colas y mensajes muertos.
- **Consola del administrador** (ADMIN de la región):

  | Ruta | Descripción |
  |---|---|
  | `GET /admin/console` | Todo junto: tablero, `down` (servicios caídos), salud de cada servicio y colas |
  | `GET /admin/console/dashboard` | Pedidos por estado, creados, completados y cancelados hoy (en la zona del país) y trabajadores disponibles |
  | `GET /admin/console/services` | Salud de cada servicio de `CONSOLE_SERVICES` (`nombre=url` separados por coma); un servicio que no responde en 2 s aparece `DOWN` |
  | `GET /admin/console/queues` | Colas de RabbitMQ (API de gestión, `RABBITMQ_API`): mensajes, sin confirmar, consumidores, colas sin consumidor y mensajes muertos |

### Pruebas de falla y de localización (F11)

Cada script corre solo contra el sistema en Docker, falla con un mensaje claro si no se cumple lo esperado, deja el sistema como estaba (vuelve a arrancar lo que detuvo y cancela sus pedidos) y escribe su evidencia en `docs/evidencias/<script>.md` con la fecha, la versión (commit), lo observado y el resultado.

| Script | Cómo se provoca | Resultado esperado | Demuestra |
|---|---|---|---|
| `scripts/falla-pagos.sh` | `stop payments`, se crea un pedido y 30 s después `start` (`OUTAGE=30`) | El pedido espera en `PENDING_PAYMENT`, el mensaje en la cola `payments.co`; al volver se autoriza una sola vez | Colas durables, reintento e idempotencia |
| `scripts/falla-replica-pagos.sh` | `--scale payments=2`, proveedor simulado con 10 s de latencia, 4 pedidos y `docker kill` de una réplica | RabbitMQ reentrega lo que la réplica caída no confirmó; los 4 pedidos se autorizan una vez cada uno | Conmutación a réplica sin perder mensajes |
| `scripts/falla-nadie-acepta.sh` | Asignación con `DISPATCH_SEARCH_TIMEOUT=20s` (`SEARCH_TIMEOUT`); el único trabajador rechaza | `assignment-failed`, pago anulado, pedido `CANCELLED` y aviso al cliente | Compensación de la saga |
| `scripts/falla-broker.sh` | `stop rabbitmq` 15 s (`OUTAGE=15`) mientras se crea un pedido | El pedido se guarda; el evento espera en `event_publication` y sale al volver el broker | Outbox |
| `scripts/falla-region.sh [br]` | `stop core-br` | 503 `SERVICE_UNAVAILABLE` solo para BR; CO responde y su consola señala `core-br` caído | Aislamiento regional |
| `scripts/falla-concurrencia.sh` | Un solo trabajador, dos pedidos al mismo tiempo y 8 aceptaciones en paralelo | Una sola oferta, una sola aceptación (200) y una sola reserva; el otro pedido sigue buscando | Exclusión mutua |
| `scripts/falla-duplicado.sh` | Se reenvía dos veces por la API de RabbitMQ el `payment.authorized` real de un pedido | Core, notificaciones y facturación lo descartan en su inbox; el pedido no cambia y no hay avisos de más | Idempotencia |
| `scripts/prueba-localizacion.sh` | Prueba del core (`LocalizationReportTest`) con el reloj fijo a las 20:30 locales del 15/11/2026 | El pedido D4 en los 5 países: textos, montos, fecha y hora, dirección, teléfono, impuesto y comprobante, medios de pago y propina | Localización (sección 8) |

- **Pedido D4** de la prueba de localización: 2 jornadas de bodega + 1 recargo de desplazamiento + propina según el país (sugerida en CO, BR y FR; pedida en US; ausente en JP). Precios, impuestos y tarifas salen de los perfiles reales de la base. La salida queda en `docs/evidencias/prueba-localizacion.md` y `.csv`. No necesita el sistema arriba: Testcontainers levanta lo necesario.
- **Formatos:** `RegionFormatter` (módulo `region`) formatea montos y fechas con CLDR y las anulaciones del perfil (decimales, patrón de fecha y ciclo de hora), igual que hará el frontend con `Intl`; la prueba comprueba que los separadores de CLDR coinciden con los del perfil.
- **Datos de demostración:** `scripts/seed-demo.sh [región...]` deja en cada región que esté arriba `cliente@<país>.jornal.test`, `comercio@<país>.jornal.test` (con identificación fiscal válida para el país) y `trabajador@<país>.jornal.test` (verificado, con bodega, ubicación base y disponible), con la clave `Demo12345`. Se puede correr varias veces. Las demos y pruebas de falla retiran a ese trabajador para tener un único candidato; después de correrlas, vuelve a ejecutar `seed-demo.sh`.

### Sexto país solo con datos: Alemania (P2)

Agregar un país no exige código por país. Para Alemania (de-DE, EUR) todo lo del país es **dato**, en la migración `V18__germany`:
- el perfil (formatos, `dd.MM.yyyy`, h23, lunes, USt 19 % incluida, `RECHNUNG` con prefijo `RE`, propina opcional del 5 o 10 %, DSGVO);
- la dirección (calle y número, código postal de 5 dígitos, ciudad);
- los medios de pago (`CARD`, `PAYPAL` y `SEPA` con su flujo);
- los documentos y los feriados nacionales de 2026;
- el catálogo traducido al alemán y los precios en euros.

Lo demás es **despliegue**:
- el servicio `core-de` del compose (perfil `de`; mismo jar, `APP_REGION=DE`) y su base `jornal_de`;
- la ruta `/api/de/` del gateway;
- `de` en `MESSAGING_REGIONS`, para que los subsistemas consuman sus colas;
- el emisor de la factura en facturación (`JORNAL_BILLING_ISSUERS_DE_*`).

Hubo dos cambios de código, únicos y generales, para que esto fuera cierto:
- `Locales` acepta también los idiomas que declaran los perfiles regionales (`supported_locales`), no solo los 5 con textos traducidos. Lo que no esté traducido al alemán (errores, avisos, etiquetas del comprobante) sale en inglés.
- Cada core declara en RabbitMQ la topología de su propia región. Así un país nuevo no pierde mensajes aunque los subsistemas aún no lo tengan en su lista.

```bash
docker compose -f infra/docker-compose.yml up -d --build --wait   # imágenes con V18
scripts/demo-pais-nuevo.sh   # crea jornal_de si hace falta, levanta core-de y lleva un pedido en Berlín hasta COMPLETED
```

El script verifica en Docker (evidencia en `docs/evidencias/demo-pais-nuevo.md`):
- perfil y catálogo en alemán («Lager und Inventur», 140,00 €);
- un pedido D4 en Berlín con pago SEPA (`CHARGE_REFUND`), asignación, avisos y la factura `RE-00000001` por 303,00 € (USt 19 % incluida: 46,14 €);
- las colas `*.de` creadas solas.

Con las 5 regiones, Alemania se suma con `--profile full --profile de`. Lo prueba también `GermanyRegionIntegrationTest`.

## Migraciones

| Versión | Contenido |
|---|---|
| `V1__init` | Extensión PostGIS |
| `V2__users` | Tabla `users` |
| `V3__catalog` | Tabla `categories` y 8 categorías iniciales |
| `V4__workerprofile` | `worker_profiles` y `worker_profile_categories` |
| `V5__countries` | `countries` (CO habilitado; BR, US, FR, IT deshabilitados) |
| `V6__users_country_locale_consent` | País, locale y consentimiento en `users` |
| `V7__category_translations` | `unaccent` y `category_translations` (es-CO, en-US) |
| `V8__users_soft_delete` | `users.deleted_at` |
| `V9__users_verification_default` | Default de `verification_state` corregido a `PENDING` |
| `V10__region_profiles` | Japón; BR, US, FR y JP habilitados, IT deshabilitado. `region_profiles` con direcciones, impuestos, medios de pago, documentos y festivos de los 5 países (valores `VERIFICAR`) |
| `V11__catalog_i18n_prices` | Traducciones pt-BR, fr-FR y ja-JP; `category_prices` con precios de demostración por país |
| `V12__users_names_addresses_business` | `given_name` y `family_name`; `addresses` y `address_fields`; `business_profiles`; sin defaults de país en `users` |
| `V13__refresh_tokens` | `refresh_tokens` (hash, familia, vencimiento, revocación y reemplazo) |
| `V14__event_publication` | Outbox: tabla `event_publication` de Spring Modulith (esquema v2) |
| `V15__requests` | `service_requests` (con punto PostGIS generado), `request_lines`, `request_status_history`, `inbox` del core y `shedlock` |
| `V16__worker_base_location` | Ubicación base del trabajador (`base_latitude`, `base_longitude`) |
| `V17__request_ratings` | `request_ratings`: una calificación por pedido y sentido (1 a 5) |
| `V18__germany` | Sexto país solo con datos: Alemania (perfil de-DE y EUR, dirección, USt, medios de pago, documentos y feriados 2026, catálogo en alemán y precios en euros) |

La siguiente migración libre del core es `V19`. Los subsistemas usan `jornal-common/.../db/subsystem` (V1: `inbox` y `region_profile_cache`; V2: `outbox`) y sus migraciones propias empiezan en `V100` (pagos, asignación, notificaciones y facturación).

## Mensajería entre procesos

- Exchange topic durable `jornal.events`. Clave de enrutamiento `<región>.<type>`, por ejemplo `co.jornal.region.profile-updated.v1`.
- Una cola durable por subsistema y región (`payments.co`, `payments.br`, ...), enlazada con `<región>.#`. La topología está en `jornal.common.messaging.Topology` y la declaran todos los procesos al arrancar, así las colas existen aunque el consumidor esté caído.
- Sobre **CloudEvents 1.0** (JSON): `specversion`, `id`, `source` (`jornal-core-co`), `type`, `time` (UTC), `subject`, `region`, `correlationid`, `aggregateversion`, `data`.
- **Outbox**: el core publica `CloudEvent` como evento interno; Spring Modulith lo guarda en `event_publication` en la misma transacción del cambio y lo envía a RabbitMQ después del commit. Si RabbitMQ está caído, `OutboxResubmitter` lo reintenta cada 30 s.
- **Inbox**: cada subsistema registra el `id` de cada mensaje en `inbox` y descarta duplicados. Los handlers (`CloudEventHandler`) corren en la misma transacción. Tres reintentos con espera creciente; después, a `jornal.dead-letter`.
- Cada consumidor se enlaza solo a lo que le interesa: `core.<r>` a `jornal.payment.#`, `jornal.dispatch.#` y `jornal.billing.#`; `payments.<r>` a `jornal.request.#` y `jornal.region.#`; `dispatch.<r>` a `jornal.request.#`, `jornal.region.#` y `jornal.worker.#`; `billing.<r>` a `jornal.payment.#` y `jornal.region.#`; `notifications.<r>` a todo. Los tipos están en `jornal.common.events.EventTypes`.
- Si tu RabbitMQ de Docker es anterior a F4, sus colas conservan el enlace viejo `<región>.#`: bórralo desde la consola o reinicia con `docker compose -f infra/docker-compose.yml down -v`.

---

## Convenciones

**Base de datos**
- Las **migraciones son la fuente de verdad** del esquema; las entidades solo lo reflejan (`ddl-auto: validate`).
- Nunca se edita una migración ya ejecutada (Flyway falla con `checksum mismatch`): se crea otra encima. En desarrollo, `docker compose down -v` empieza de cero.
- Usar `INTEGER` y no `SMALLINT` para enteros mapeados a `int`.

**Módulos**
- Un paquete en minúscula por módulo bajo `com.jornal`; cada módulo es dueño de sus tablas.
- Un módulo **no** importa entidades, repositorios ni mappers de otro. Se comunican por contratos en la raíz (`UserApi`, `CatalogApi`, `WorkerApi`) o por eventos (`UserAccessChangedEvent`).
- Referencias entre módulos: `UUID` sin `FOREIGN KEY`.
- Con Spring Modulith, los subpaquetes (`controllers`, `services`, `dto`, `enums`, `listeners`) son internos.

**Plan de desarrollo** (`documento.md`)
- Prohibido escribir reglas por país en la lógica (`if`/`switch` sobre el país): todo va en el perfil regional.
- La hora actual sale siempre del bean `java.time.Clock`, nunca de `Instant.now()`.
- Tasas e impuestos se cargan con la marca `VERIFICAR` y su fuente. Datos personales de prueba, ficticios.

**Errores**
- Un `enum` por módulo (`ErrorUser`, `ErrorCatalog`, `ErrorWorker`, `ErrorRegion`) que implementa `ErrorCode`. Prefijos `USER`, `AUTH`, `CAT`, `WRK`, `REG`.
- `throw new ApiException(ErrorUser.USER002_USER_NOTFOUND, id)`; `GlobalExceptionHandler` lo convierte en `ProblemDetail` con `code`. Validaciones: `VALIDATION_ERROR` con detalle por campo. `ApiException.withFields(...)` agrega `errors` (campo → `REQUIRED`, `INVALID_FORMAT`, `TOO_LONG`, `UNKNOWN_FIELD`).
- Todo código nuevo necesita su clave `error.<CODE>` en los 5 `messages*.properties` (con `{0}` para argumentos; en francés usar el apóstrofo tipográfico ’).

**Capas**
- Un mapper MapStruct por entidad. Nunca se devuelven entidades JPA desde los controladores.
- Lógica en el servicio; controllers delgados con `@Valid`.
- Paginación con `PageResponse`, máximo 100. Las búsquedas escapan los comodines de `LIKE`.

---

## Estado actual

**Implementado**
- [x] Spring Boot 4.1.2, Java 21, Docker Compose (PostGIS y Redis), perfiles `dev` y `prod`
- [x] Flyway `V1` a `V12`
- [x] Spring Modulith y contratos `RegionApi`, `UserApi`, `CatalogApi`, `WorkerApi`
- [x] Errores centralizados con `ProblemDetail`
- [x] JWT: registro, login, roles, 401/403 en JSON; pruebas por rol
- [x] `users`: país, locale, teléfono E.164, consentimiento, cuenta propia, borrado seguro, administración, admin inicial
- [x] `catalog`: categorías con búsqueda, filtros y traducciones (escritura)
- [x] `workers`: perfil, categorías, disponibilidad con verificación, vista pública y reacción a eventos
- [x] **F0**: correcciones del estado inicial (claim `role`, orden por `createdIn`, rutas `/me`, renombres de API, paquetes en minúscula, `Clock`, `V9`)
- [x] **F1**: perfil regional de 5 países (`APP_REGION`), 5 idiomas con `Accept-Language` y errores traducidos, catálogo traducido con precios por país, nombre en partes con `nameOrder`, direcciones validadas por país, rol `BUSINESS` con identificación fiscal validada por estrategia, administración del perfil con versión y evento

**Verificado con Docker (24/11/2026)**
- [x] **F2 (P1)**: access token de 15 minutos con claim `region` validado, refresh tokens con rotación, detección de reuso y revocación (logout, cambio de contraseña, bloqueo y borrado), CORS y Swagger con Bearer
- [x] **F3**: Maven multimódulo, `jornal-common`, 4 subsistemas como procesos independientes, RabbitMQ con Outbox, CloudEvents e inbox, Dockerfile multietapa y `infra/docker-compose.yml` con 5 cores, gateway, Mailpit, Prometheus y Grafana
- [x] **F4**: módulo `requests` con carrito (cotizar), pedido idempotente, precios por perfil, máquina de estados con historial, saga orquestada con compensaciones, plazos con ShedLock y cola `core.<región>` con inbox
- [x] **F5**: subsistema de pagos con interfaz `PaymentGateway` y adaptadores simulados por flujo (autorizar y capturar, cobrar y reembolsar, voucher asíncrono), idempotencia por pedido e intento, libro de movimientos con liquidación, tablas particionadas por región, simulador configurable en caliente y outbox de subsistemas
- [x] **F6**: subsistema de asignación con proyección de trabajadores (`jornal.worker.updated.v1`), candidatos por categoría, radio y disponibilidad ordenados por distancia, carga del día y calificación, ofertas secuenciales con plazo, reserva atómica, posición en vivo en Redis GEO con TTL por el exchange no durable `jornal.live` y seguimiento para el cliente
- [x] **F7**: subsistema de notificaciones con plantillas ICU MessageFormat en 5 idiomas (plurales CLDR) y la hora en la zona del destinatario; contacto pedido por REST al core de la región con reintento (`/internal/contacts`, token interno, cerrado en el gateway); avisos en la app por WebSocket STOMP (JWT en el CONNECT y permiso por tema), bandeja `/notifications/me`, correo a Mailpit, y SMS y push simulados; posición en vivo reenviada al tema del pedido
- [x] **F8**: subsistema de facturación con comprobante por país (`receipt_type` del perfil), numeración consecutiva por región y tipo sin huecos, impuestos con su tasa, identificación fiscal del comercio, fecha en la zona del lugar, salida JSON y HTML imprimible en el idioma del país; calificaciones en ambos sentidos solo sobre `COMPLETED`; tasas de cambio (par, tasa, fuente y fecha) con proveedor simulado y carga manual, y reporte consolidado de ingresos en la moneda elegida
- [x] **F10**: id de correlación de punta a punta (gateway, MDC en cada línea de log y `correlationid` de cada mensaje), métricas de pedidos por estado y región, tiempo de asignación, fallas de pago, mensajes y colas; tablero de Grafana provisionado; consola del administrador `/admin/console` con tablero de la región, salud de cada servicio y colas
- [x] 110 pruebas en verde en una corrida completa (79 del core, 9 de pagos, 8 de asignación, 10 de notificaciones y 4 de facturación), con PostgreSQL, RabbitMQ y Redis reales
- [x] Verificado en Docker (24/11/2026): `scripts/demo-observabilidad.sh` y, de nuevo tras F10, `demo-mensajeria`, `demo-pagos`, `demo-asignacion`, `demo-notificaciones` y `demo-facturacion`
- [x] Verificado en Docker (24/11/2026): `scripts/demo-facturacion.sh` (pedido `COMPLETED` con `FE-00000001`, calificaciones y reporte en USD con la tasa COP/USD y su fecha; evidencia en `docs/evidencias`)
- [x] Verificado en Docker (24/11/2026): `scripts/demo-notificaciones.sh` (pedido completo: el cliente recibe 6 avisos en español y el trabajador 5 en inglés, en la app y por correo en Mailpit)
- [x] Verificado en Docker (24/11/2026): `scripts/demo-asignacion.sh` (pedido real ofrecido al trabajador a 1,3 km, aceptado, seguido en vivo hasta 0,2 km y capturado)
- [x] Verificado en Docker (24/11/2026): `scripts/demo-pagos.sh` (un pedido real se autoriza, se anula al cancelarlo y se rechaza con el simulador) y `scripts/demo-mensajeria.sh`
- [x] Sistema completo verificado con Docker (24/11/2026): las 5 regiones sanas detrás del gateway y `scripts/demo-mensajeria.sh` en verde (el cambio de perfil llega a pagos y el reenvío se procesa una vez)

- [x] **F11** verificado en Docker (24/11/2026): las 7 pruebas de falla (`falla-pagos`, `falla-replica-pagos`, `falla-nadie-acepta`, `falla-broker`, `falla-region`, `falla-concurrencia`, `falla-duplicado`) y la prueba de localización con el pedido D4 en los 5 países, cada una con su evidencia en `docs/evidencias`

**Pendiente de confirmar por el equipo**
- [ ] Valores marcados `VERIFICAR` en `region_profiles`, `region_tax_rules`, `region_holidays` y precios de `category_prices`, con fuentes oficiales vigentes

---

## Lo que falta

El orden y los criterios de terminado están en [`documento.md`](documento.md):

- **F2 (P2)** RS256 con JWKS y límite de intentos de login con Redis
- **F9** frontend (plan en `documento.md`)

---

## Decisiones

**Tomadas**
- Backend como monolito modular en Spring Boot, con extracción posterior a servicios.
- Un trabajador **debe estar verificado** para quedar disponible. La verificación es manual por ahora (un admin la cambia) y luego se integrará con una API de verificación.
- Comisión cobrada a los trabajadores como modelo de ingresos inicial.
- JWT firmado con HS256 con secreto, emisor y vigencia configurables por ambiente.

**Pendientes**
- ~~**Asignación del trabajador**~~: resuelta en `documento.md` (D7): oferta automática secuencial al mejor candidato con tiempo límite.
- **Modelo de ingresos**: posible tarifa de servicio al solicitante o suscripciones para empresas recurrentes, además de la comisión.
- **Marco legal laboral**: validar con un abogado laboral que el modelo no configure una relación laboral encubierta (el trabajador debe fijar su disponibilidad y poder rechazar trabajos libremente).
- **Protección de datos (Ley 1581 de 2012)**: qué datos se borran y cuáles se retienen (por ejemplo, soportes de pagos), y cómo se tratan los documentos de identidad.
- **Seguridad y confianza**: verificación de identidad, calificaciones mutuas, botón de reporte y política de cuentas suspendidas.

---

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| IntelliJ: `release version 5 not supported` | El IDE usa nivel 1.5 por defecto. Actualiza IntelliJ, recarga Maven y revisa **Settings > Compiler > Java Compiler** (Target bytecode version = 21). `mvn clean compile` por consola es la referencia |
| `FATAL: password authentication failed` (28P01) | (1) Otro PostgreSQL local ocupa el 5432: cambia `POSTGRES_PORT`. (2) El volumen guardó credenciales viejas: `docker compose down -v`. (3) Caracteres no ASCII en la clave |
| Spring Boot no detecta la base | La imagen es `postgis/postgis`: el servicio necesita la etiqueta `org.springframework.boot.service-connection: postgres` |
| `Flyway checksum mismatch` | Se editó una migración ya aplicada. En desarrollo: `docker compose down -v`. Después, nunca editar migraciones ejecutadas |
| `Dependency 'spring-modulith-...' not found` | Falta la propiedad `spring-modulith.version` o el BOM está mal ubicado (`dependencyManagement` va dentro de `project`, fuera de `dependencies`) |
| Falla al validar esquema por `event_publication` | `spring-modulith-starter-jpa` está activo sin su migración. Déjalo comentado hasta usar eventos persistentes |
| `No qualifying bean of type JwtEncoder` | El resource server solo crea el decoder: el bean `JwtEncoder` se define en `SecurityConfig` |
| `No property 'xxx' found for type '...'` | Typo en un método derivado del repositorio (`finBy`, `existBy`): los prefijos válidos son `findBy`, `existsBy`, `countBy`, `deleteBy` |
| `400 Failed to read request` al registrar | El cuerpo no se pudo leer: JSON mal formado o un valor de `rol` que no existe en el enum |
| `403` o rol incorrecto tras cambiar el rol en la base | El rol va dentro del token: vuelve a hacer login |
| `Unmapped target property` (MapStruct) | Campo de la entidad sin origen en el request: agregar `@Mapping(target = "...", ignore = true)` |
| `Cannot resolve symbol` tras agregar clases | Recarga Maven y revisa que la clase esté en la raíz del módulo, con el nombre exacto |

---

## Licencia

Por definir.