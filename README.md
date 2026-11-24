# Jornal Backend

Plataforma tipo Uber para que personas, tiendas o empresas soliciten un ayudante o trabajador para una tarea puntual, sin contrato formal (trabajo informal por jornal). El backend está pensado como un **monolito modular** que pueda evolucionar a un sistema distribuido.

> **Estado:** MVP en desarrollo. Última actualización: 24 de noviembre de 2026.
> Existen la infraestructura, la seguridad con JWT y los módulos `region`, `users`, `catalog` y `workers`. El plan por fases para llevarlo a sistema distribuido está en [`documento.md`](documento.md) y el estado de partida en [`docs/ESTADO-ACTUAL.md`](docs/ESTADO-ACTUAL.md). Fases cerradas: **F0 (correcciones)** y **F1 (perfil regional y localización)**.

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
| Modularidad | Spring Modulith 2.1.1 (`starter-core` y `starter-test`) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Observabilidad | Actuator + Micrometer (Prometheus) |
| Pruebas | Spring Boot Test, Testcontainers (dependencias listas, pruebas pendientes) |
| Entorno local | Docker Compose integrado con Spring Boot |

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

### 2. Levantar la aplicación

Con Docker Desktop abierto:

```bash
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`, o ejecuta `JornalBackendApplication` desde IntelliJ con el perfil `dev`.

Spring Boot ejecuta `docker compose up` por ti, espera a que PostgreSQL y Redis estén sanos, configura las conexiones y Flyway aplica las migraciones. La primera vez tarda más porque descarga las imágenes. En `dev` no necesitas levantar los contenedores a mano ni configurar el datasource.

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
- **Token JWT** firmado con HS256. Claims: `sub` (id del usuario), `role`, `email`, `name`, `iss`, `iat`, `exp`. El emisor y la vigencia salen de la configuración (`jornal.security.jwt.*`). La hora sale del bean `Clock` (`TimeConfig`).
- **Validación**: el resource server valida firma, expiración y emisor. El claim `role` se convierte en la autoridad `ROLE_<rol>`.
- **Autorización por rol** con `@PreAuthorize` (por ejemplo `hasRole('ADMIN')`). `SecurityRolesTest` lo prueba con tokens reales.
- **Errores 401 y 403** como `ProblemDetail` con `code` (`UNAUTHORIZED`, `ACCESS_DENIED`).
- **Rutas públicas**: `/auth/**`, `GET /countries`, `/actuator/health`, Swagger y `/v3/api-docs/**`. Todo lo demás exige token.
- El login responde con el mismo error ante email inexistente, clave incorrecta o usuario desactivado, y compara siempre contra un hash para no revelar qué emails existen por tiempos de respuesta.

Límites conocidos (se cierran en la fase F2 del plan):

- Un token emitido sigue válido hasta que expire aunque se cambie la contraseña o se desactive la cuenta. Faltan refresh tokens revocables y un access token corto.
- No hay límite de intentos en `/auth/login`, ni CORS, ni botón Bearer en Swagger.

---

## Estructura del proyecto

```
jornal-backend
├── docker-compose.yaml            # PostGIS + Redis para desarrollo
├── .env / .env.example            # credenciales locales (.env NO se versiona)
├── documento.md                   # plan de desarrollo por fases (F0 a F11)
├── docs/ESTADO-ACTUAL.md          # estado de partida del backend
├── pom.xml
└── src
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
    │   │   └── workers       # raíz: WorkerApi, WorkerSummary, WorkerProfile, WorkerService
    │   │       ├── controllers   # WorkerController
    │   │       ├── listeners     # WorkerEventListener
    │   │       ├── dto
    │   │       └── enums         # ErrorWorker
    │   └── resources
    │       ├── application.yaml, application-dev.yaml, application-prod.yaml
    │       ├── messages*.properties  # errores en en (respaldo), es, pt, fr y ja
    │       └── db/migration      # V1 a V12
    └── test/java/com/jornal
        ├── ModularidadTest.java              # límites entre módulos
        ├── NoCountryConditionalsTest.java    # falla si hay literales de país en el código
        ├── SecurityRolesTest.java            # 200/401/403 por rol con JWT reales, errores traducidos
        ├── LocalizationIntegrationTest.java  # criterio de F1 en CO con PostgreSQL real (Docker)
        ├── JapanRegionIntegrationTest.java   # la misma app con APP_REGION=JP (Docker)
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
| `POST /auth/login` | público | Devuelve `{accessToken, tokenType, expiresIn}` |
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

La siguiente migración libre es `V13`.

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
- [x] 55 pruebas en verde: arranque completo, migraciones V1 a V12, F1 en CO y en JP con PostgreSQL real

**Pendiente de confirmar por el equipo**
- [ ] Valores marcados `VERIFICAR` en `region_profiles`, `region_tax_rules`, `region_holidays` y precios de `category_prices`, con fuentes oficiales vigentes

---

## Lo que falta

El orden y los criterios de terminado están en [`documento.md`](documento.md):

- **F2** seguridad distribuida (CORS, refresh tokens, claim `region`, RS256)
- **F3** multi-módulo Maven, 5 cores regionales, RabbitMQ con Outbox, subsistemas
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