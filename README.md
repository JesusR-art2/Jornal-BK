# Jornal Backend

Plataforma tipo Uber para que personas, tiendas o empresas soliciten un ayudante o trabajador para una tarea puntual, sin contrato formal (trabajo informal por jornal). El backend está pensado como un **monolito modular** que pueda evolucionar a un sistema distribuido.

> **Estado:** MVP en desarrollo. Última actualización: 24 de noviembre de 2026.
> Existen la infraestructura, la seguridad con JWT y los módulos `region`, `users`, `catalog` y `workers`. El plan por fases para llevarlo a sistema distribuido está en [`documento.md`](documento.md) y el estado de partida en [`docs/ESTADO-ACTUAL.md`](docs/ESTADO-ACTUAL.md). Fases cerradas: **F0 (correcciones)**, **F1 (perfil regional y localización)**, **F2 P1 (sesión segura)**, **F3 (procesos, regiones e infraestructura)**, **F4 (pedidos y saga)** y **F5 (pagos)**.

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

En Windows: `mvnw.cmd ...`, o ejecuta `JornalBackendApplication` desde IntelliJ (módulo `jornal-core`, perfil `dev`, directorio de trabajo `jornal-core`).

Spring Boot ejecuta `docker compose up` con el `docker-compose.yaml` de la raíz (`spring.docker.compose.file: ../docker-compose.yaml`), espera a que PostgreSQL, Redis y RabbitMQ estén sanos, configura las conexiones y Flyway aplica las migraciones. En `dev` no necesitas levantar los contenedores a mano. Consola de RabbitMQ: `http://localhost:15672` (guest/guest).

### Sistema distribuido completo (5 regiones + 4 subsistemas)

```bash
docker compose -f infra/docker-compose.yml up -d --build --wait                  # CO y BR + subsistemas
docker compose -f infra/docker-compose.yml --profile full up -d --build --wait   # las 5 regiones
scripts/demo-mensajeria.sh co                                                    # demostración del flujo de mensajes
scripts/demo-pagos.sh                                                            # pedido real: autoriza, anula y rechazo simulado
docker compose -f infra/docker-compose.yml --profile full down                   # apagar (conserva datos)
```

| Servicio | Dirección |
|---|---|
| Gateway (entrada única) | `http://localhost:8000/api/{co,br,us,fr,jp}/...` o `http://co.jornal.localhost:8000/...` |
| Estado de un subsistema | `http://localhost:8000/subsystems/payments/status` (también dispatch, notifications, billing) |
| Swagger de una región | `http://localhost:8000/api/co/swagger-ui.html` |
| RabbitMQ (consola) | `http://localhost:15673` (jornal / jornal-dev-password) |
| Mailpit | `http://localhost:8025` |
| Prometheus / Grafana | `http://localhost:9090` / `http://localhost:3000` (admin / admin) |
| PostgreSQL | `localhost:55432` (bases `jornal_co` ... `jornal_jp` y una por subsistema) |

Administrador de cada región: `admin@{co,br,us,fr,jp}.jornal.test` / `Admin12345`. Si una región está apagada, el gateway responde `503 SERVICE_UNAVAILABLE` solo para ella. Cada JVM corre con `-Xmx256m` (unos 420 MB por core).

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

- Cada instancia atiende **un país**: `APP_REGION` (`CO`, `BR`, `US`, `FR` o `JP`; por defecto `CO`). Solo registra usuarios de ese país y usa su perfil regional. Ejemplo: `APP_REGION=JP ./mvnw spring-boot:run`.
- El idioma de cada respuesta sale del header `Accept-Language` (es-CO, pt-BR, en-US, fr-FR, ja-JP). Si falta o no está soportado, se usa el idioma del perfil y luego en-US. Los mensajes de error se traducen; el `code` no cambia.

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

Pendiente (P2 de la fase F2): firma RS256 con JWKS para que los subsistemas validen el token sin el secreto (D10; respaldo: HS256 compartido) y límite de intentos de login con Redis (`AUTH002`). Un access token ya emitido vive hasta 15 minutos aunque la cuenta se bloquee.

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
├── jornal-dispatch/               # subsistema (esqueleto)
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
| `PUT /workers/me` | WORKER | Crear o reemplazar el perfil: `bio`, `workRadiusKm` (1 a 100) y `categoryIds` (1 a 5, activas) |
| `PATCH /workers/me/availability` | WORKER | `{"available": true}` o `false`; para activarla hay que estar verificado y tener una categoría activa |
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

La siguiente migración libre del core es `V16`. Los subsistemas usan `jornal-common/.../db/subsystem` (V1: `inbox` y `region_profile_cache`); sus migraciones propias empezarán en `V100`.

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
- [x] 84 pruebas en verde (75 del core y 9 de pagos), con PostgreSQL y RabbitMQ reales
- [x] Verificado en Docker (24/11/2026): `scripts/demo-pagos.sh` (un pedido real se autoriza, se anula al cancelarlo y se rechaza con el simulador) y `scripts/demo-mensajeria.sh`
- [x] Sistema completo verificado con Docker (24/11/2026): las 5 regiones sanas detrás del gateway y `scripts/demo-mensajeria.sh` en verde (el cambio de perfil llega a pagos y el reenvío se procesa una vez)

**Pendiente de confirmar por el equipo**
- [ ] Valores marcados `VERIFICAR` en `region_profiles`, `region_tax_rules`, `region_holidays` y precios de `category_prices`, con fuentes oficiales vigentes

---

## Lo que falta

El orden y los criterios de terminado están en [`documento.md`](documento.md):

- **F2 (P2)** RS256 con JWKS y límite de intentos de login con Redis
- **F3 (pendiente menor)** TLS de desarrollo en el gateway (hoy HTTP en el puerto 8000)
- **F4** pedidos y saga · **F5** pagos · **F6** asignación y seguimiento · **F7** notificaciones
- **F8** facturación y calificaciones · **F9** frontend · **F10** observabilidad · **F11** pruebas de falla y de localización

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