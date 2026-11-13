# Jornal Backend

Plataforma tipo Uber para que personas, tiendas o empresas soliciten un ayudante o trabajador para una tarea puntual, sin contrato formal (trabajo informal por jornal). El backend está pensado como un **monolito modular** que pueda evolucionar a un sistema distribuido.

> **Estado:** MVP en desarrollo. Última actualización: 13 de noviembre de 2026.
> Ya existen la infraestructura, la seguridad con JWT y los módulos `Users` y `Catalog`. El módulo `Workers` está en construcción y `Requests` es el siguiente. Ver [Estado actual](#estado-actual) y [Lo que falta](#lo-que-falta).

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
# 1. Registrar
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@jornal.com","password":"Clave12345","name":"Prueba","rol":"WORKER"}'

# 2. Login (devuelve accessToken, tokenType y expiresIn)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@jornal.com","password":"Clave12345"}'

# 3. Usar el token
curl http://localhost:8080/users/me -H "Authorization: Bearer <accessToken>"
```

Respuestas esperadas: `201` al registrar, `409` con `USER001_EMAIL_REGISTERED` si el email ya existe, `400` con `VALIDATION_ERROR` si el cuerpo es inválido, `401` si el token falta o es inválido.

### 5. Crear el primer administrador

Un usuario no puede registrarse como `ADMIN` por el endpoint público. Opciones:

- **Desarrollo:** registra un usuario normal y promuévelo en la base (ajusta el nombre de la tabla), luego **vuelve a hacer login** porque el rol viaja dentro del token:

  ```sql
  update users set rol = 'ADMIN' where email = 'tu@correo.com';
  ```

- **Migración con variables de entorno:** una migración que inserta el admin solo si `ADMIN_PASSWORD_HASH` está definido (placeholders de Flyway `admin_email` y `admin_password_hash`). Nunca guardes el hash real en el repositorio. Las variables deben estar definidas **antes** de la primera ejecución, porque una migración versionada corre una sola vez.

- **Recomendado para producción (pendiente):** un `ApplicationRunner` que cree el admin al arrancar si no existe, calculando el hash con el mismo `PasswordEncoder`.

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
- **Token JWT** firmado con HS256. Claims: `sub` (id del usuario), `role`, `email`, `name`, `iss`, `iat`, `exp`. El emisor y la vigencia salen de la configuración (`jornal.security.jwt.*`), no del código.
- **Validación**: el resource server de Spring Security valida firma, expiración y emisor. El claim `role` se convierte en la autoridad `ROLE_<rol>`.
- **Autorización por rol** con `@PreAuthorize` (por ejemplo `hasRole('ADMIN')`).
- **Errores 401 y 403** devueltos como `ProblemDetail` con `code` (`UNAUTHORIZED`, `ACCESS_DENIED`).
- **Rutas públicas**: `/auth/**`, `/actuator/health`, Swagger y `/v3/api-docs/**`. Todo lo demás exige token.
- El login responde con el mismo error ante email inexistente, clave incorrecta o usuario desactivado, y compara siempre contra un hash para no revelar qué emails existen por tiempos de respuesta.

Límites conocidos del diseño actual:

- Un token ya emitido sigue válido hasta que expire, aunque se cambie la contraseña o se desactive la cuenta. Se cierra con tokens de acceso cortos y refresh tokens revocables (pendiente).
- No hay limitación de intentos en `/auth/login`.
- Los datos del token (email, nombre) pueden quedar desactualizados. Para decisiones importantes se consulta la base por el `id`.

---

## Estructura del proyecto

```
jornal-backend
├── docker-compose.yaml            # PostGIS + Redis para desarrollo
├── .env                           # credenciales locales (NO se versiona)
├── .env.example                   # plantilla sin valores reales
├── pom.xml
└── src
    ├── main
    │   ├── java/com/jornal
    │   │   ├── JornalBackendApplication.java
    │   │   ├── Shared                           # transversal
    │   │   │   ├── ApiException.java            # excepción única de negocio
    │   │   │   ├── ErrorCode.java               # interfaz: name(), status(), message()
    │   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice (ProblemDetail)
    │   │   │   ├── RestSecurityErrorHandlers.java  # 401 y 403 en JSON
    │   │   │   ├── SecurityConfig.java          # PasswordEncoder, JWT, filtros
    │   │   │   ├── JwtProperties.java           # secreto, emisor, vigencia
    │   │   │   └── PageResponse.java            # respuesta paginada estable
    │   │   ├── Users                            # cuentas, autenticación, verificación
    │   │   │   ├── UserApi.java                 # contrato público para otros módulos
    │   │   │   ├── UserSummary.java             # id, name, rol, verified
    │   │   │   ├── UserAccessChangedEvent.java  # evento: cuenta elegible o no
    │   │   │   ├── Rol.java
    │   │   │   ├── User.java, UserRepository.java, UserMapper.java
    │   │   │   ├── Controllers                  # AuthController, UserController, AdminUserController
    │   │   │   ├── Services                     # UserService, AuthService, JwtService
    │   │   │   ├── DTO
    │   │   │   └── enums                        # ErrorUser, VerificationState
    │   │   ├── Catalog                          # categorías de trabajo
    │   │   │   ├── CatalogApi.java              # contrato público
    │   │   │   ├── CategorySummary.java
    │   │   │   ├── Category.java, CategoryRepository.java, CategoryMapper.java
    │   │   │   ├── Controllers                  # CategoryController, AdminCategoryController
    │   │   │   ├── Services                     # CategoryService
    │   │   │   ├── DTO
    │   │   │   └── enums                        # ErrorCatalog
    │   │   └── Workers                          # perfil del trabajador (en construcción)
    │   │       ├── WorkerProfile.java, WorkerProfileRepository.java, WorkerProfileMapper.java
    │   │       ├── Controllers                  # WorkerController
    │   │       ├── Services                     # WorkerService
    │   │       ├── Listeners                    # WorkerEventListener
    │   │       ├── DTO
    │   │       └── enums                        # ErrorWorker
    │   └── resources
    │       ├── application.yaml
    │       ├── application-dev.yaml
    │       ├── application-prod.yaml
    │       └── db/migration                     # carpetas db/migration (no "db.migration")
    └── test
        └── java/com/jornal
            └── ModularidadTest.java             # verifica límites entre módulos
```

---

## Módulos y endpoints

Todos los endpoints exigen token salvo los marcados como públicos.

### Auth (`Users`)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `POST /auth/register` | público | Crear cuenta (no permite el rol `ADMIN`) |
| `POST /auth/login` | público | Devuelve el JWT |

### Users

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /users/me` | autenticado | Ver la propia cuenta |
| `PATCH /users/me` | autenticado | Editar nombre y teléfono |
| `PUT /users/me/password` | autenticado | Cambiar la contraseña (exige la actual) |
| `GET /admin/users?rol=&page=&size=` | ADMIN | Listar con paginación y filtro por rol |
| `GET /admin/users/{id}` | ADMIN | Ver un usuario |
| `POST /admin/users/{id}/deactivate` y `/activate` | ADMIN | Bloquear y reactivar |
| `PATCH /admin/users/{id}/verification` | ADMIN | Cambiar el estado de verificación |

Pendientes de `Users`: borrado seguro de cuenta (`POST /users/me/deletion` y `DELETE /admin/users/{id}`, ver [Lo que falta](#lo-que-falta)).

### Catalog

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /categories?q=` | autenticado | Categorías activas; `q` busca por nombre o slug (contiene, sin distinguir mayúsculas) |
| `GET /admin/categories?q=&active=&page=&size=` | ADMIN | Listado con filtros y paginación |
| `GET /admin/categories/{id}` | ADMIN | Ver una categoría |
| `POST /admin/categories` | ADMIN | Crear (slug único e inmutable) |
| `PUT /admin/categories/{id}` | ADMIN | Editar nombre y descripción |
| `POST /admin/categories/{id}/deactivate` y `/activate` | ADMIN | Ocultar y mostrar |

La migración del catálogo incluye 8 categorías iniciales (aseo, mudanzas, construcción, pintura, jardinería, eventos, bodega, mensajería).

### Workers (en construcción)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /workers/me` | WORKER | Ver el propio perfil |
| `PUT /workers/me` | WORKER | Crear o reemplazar el perfil (bio, radio en km, 1 a 5 categorías) |
| `PATCH /workers/me/availability` | WORKER | Activar o desactivar la disponibilidad |
| `GET /workers/{userId}` | autenticado | Vista pública: nombre, bio, calificación y categorías |

Reglas: solo un usuario con rol de trabajador y cuenta activa puede tener perfil; las categorías deben existir y estar activas; para quedar disponible el trabajador debe estar **verificado** y tener al menos una categoría activa; la vista pública solo muestra trabajadores activos y verificados. Si la cuenta deja de ser elegible (desverificada, desactivada o eliminada), la disponibilidad se apaga automáticamente por un evento de `Users`.

---

## Migraciones

| Versión | Contenido |
|---|---|
| `V1__init` | Extensión PostGIS |
| `V2__users` | Tabla de usuarios |
| `V3__catalog` | Tabla `categories` y categorías iniciales |
| `V4` o `V5` (opcional) | Seed del administrador (si se usa) |
| siguiente número libre | `worker_profiles` y `worker_profile_categories` |

Antes de aplicar el borrado seguro se necesita otra migración que agregue `deleted_at` a la tabla de usuarios.

---

## Convenciones

**Base de datos**
- Las **migraciones son la fuente de verdad** del esquema; las entidades solo lo reflejan. `spring.jpa.hibernate.ddl-auto: validate`.
- Flujo: escribir la migración, luego la entidad, y arrancar para validar.
- Nunca se edita una migración ya ejecutada (Flyway falla con `checksum mismatch`): se crea otra encima. En desarrollo, `docker compose down -v` empieza de cero.
- Cambios compatibles hacia atrás (expandir, migrar, contraer).
- Usar `INTEGER` y no `SMALLINT` para enteros mapeados a `int`, o la validación de Hibernate falla.

**Módulos (preparados para separarse en servicios)**
- Un paquete por módulo bajo `com.jornal`; cada módulo es dueño de sus tablas.
- Un módulo **no** importa entidades, repositorios ni mappers de otro. Se comunican por contratos públicos en la raíz del módulo (`UserApi`, `CatalogApi`) o por eventos (`UserAccessChangedEvent`).
- Las referencias entre módulos son un `UUID` sin `FOREIGN KEY`. Dentro de un módulo sí hay claves foráneas.
- Con Spring Modulith, los subpaquetes (`Services`, `DTO`, `enums`...) son internos; solo lo que está en la raíz del módulo es público. Los tipos usados en contratos públicos (como `Rol`) deben estar en la raíz.
- Los eventos entre módulos son síncronos (`@EventListener` dentro de la misma transacción). Al separar servicios pasarán a `@ApplicationModuleListener` o Kafka.

**Errores**
- Un `enum` por módulo (`ErrorUser`, `ErrorCatalog`, `ErrorWorker`) que implementa `ErrorCode` y define status HTTP y mensaje.
- Se lanza `throw new ApiException(ErrorUser.USER001_EMAIL_REGISTERED)`, o con argumentos: `new ApiException(ErrorUser.USER002_USER_NOTFOUND, id)`.
- `GlobalExceptionHandler` lo convierte en `ProblemDetail` (RFC 9457) con un campo `code` estable para el frontend. Las validaciones devuelven `VALIDATION_ERROR` con el detalle por campo.
- Prefijos: `USER`, `CAT`, `WRK`.

**Mapeo y capas**
- Un mapper MapStruct por entidad, dentro de su módulo. Nunca se devuelven entidades JPA desde los controladores.
- La lógica de negocio va en el servicio, no en el mapper ni en el controller.
- Los controllers son delgados: validan con `@Valid`, llaman un método del servicio y devuelven el DTO.

**Seguridad**
- Nada sensible en el token (solo id, rol, email y nombre).
- Los listados paginados usan `PageResponse` y limitan el tamaño máximo (100).
- Las búsquedas por texto escapan los comodines de `LIKE`.

---

## Estado actual

**Implementado**
- [x] Proyecto Spring Boot 4.1.2 con Java 21, Docker Compose (PostGIS y Redis) con `.env`, perfiles `dev` y `prod`
- [x] Flyway con migraciones `V1` a `V3`
- [x] Spring Modulith (`starter-core` y `starter-test`) y contratos públicos `UserApi` y `CatalogApi`
- [x] Manejo centralizado de errores (`ApiException`, `ErrorCode`, `GlobalExceptionHandler`)
- [x] Seguridad con JWT: registro, login, roles, 401 y 403 en JSON, issuer y vigencia configurables
- [x] `Users`: cuenta propia (ver, editar, cambiar contraseña), administración (listar, ver, activar, desactivar, verificación manual)
- [x] `Catalog`: categorías con búsqueda y filtros por texto y estado, administración completa

**En curso**
- [ ] `Workers`: código entregado (entidad, servicio, controller, listener, DTOs, errores). Falta crear la migración, compilar y probar el flujo completo.
- [ ] Cambios en `Users` que `Workers` necesita: campo `verified` en `UserSummary`, evento `UserAccessChangedEvent` y `publishAccess` llamado desde `activate`, `deactivate`, `updateVerification` y el borrado.

**Pendiente de verificar**
- [ ] `ModularidadTest` en verde con los tres módulos
- [ ] Creación del primer administrador por alguna de las vías descritas

---

## Lo que falta

**Corto plazo**
1. Terminar y probar `Workers` (verificar que un trabajador sin verificar no pueda quedar disponible, que desverificarlo apague su disponibilidad y que la vista pública solo muestre verificados).
2. **Borrado seguro de usuarios**: borrado lógico con anonimización (`deleted_at`, email reemplazado por uno único e inválido, nombre y teléfono borrados, contraseña invalidada), confirmación de contraseña, bloqueo para administradores y evento para otros módulos. Excluir eliminados de los listados.
3. Renombrar los paquetes a minúscula (`users`, `shared`, `catalog`, `workers`) y revisar que los tipos públicos estén en la raíz de cada módulo.
4. Normalizar nombres (`password_hash` a `passwordHash` en la entidad con `@Column(name = "password_hash")`, código `INVALID_CREDENTIALS` con prefijo de módulo).
5. `.env.example`, `.gitignore` revisado y `ApplicationRunner` para crear el primer admin.

**Seguridad y autenticación**
6. **Refresh tokens** con tabla propia, rotación y revocación en logout; bajar el access token a 10 a 15 minutos.
7. Límite de intentos en `/auth/login`.
8. Configuración CORS para la app móvil y web.
9. Botón de autorización con Bearer en Swagger.

**Módulos del MVP**
10. **`Requests` (solicitudes)**: máquina de estados `PUBLICADA → ASIGNADA → EN_CURSO → COMPLETADA` (más `CANCELADA` y `EXPIRADA`) y quién ejecuta cada transición. Requiere definir antes el modo de asignación (ver [Decisiones](#decisiones)).
11. **`Matching`**: búsqueda de trabajadores disponibles cercanos con PostGIS (`ST_DWithin`) y ubicación en vivo con Redis GEO.
12. **`Ratings`**: calificaciones en ambos sentidos; actualiza `rating_avg` y `rating_count` del perfil.
13. **`Payments`**: pasarela (Wompi, PayU o Mercado Pago) detrás de una interfaz propia, retención del pago y cobro de la comisión.
14. **`Notifications`** (push y SMS) y tiempo real con WebSockets.
15. **Verificación de identidad**: integrar una API de verificación de cédula y, si hace falta, cédula profesional o experiencia. El resultado debe terminar en el estado de verificación de `Users` (o en un módulo `Verification` que lo actualice) sin cambiar `Workers`. Guardar el mínimo de datos personales.

**Infraestructura y calidad**
16. Pruebas: unitarias de servicios, integración con Testcontainers (imagen `postgis/postgis` como sustituto compatible de `postgres`) y pruebas de seguridad por rol.
17. Spring Modulith `starter-jpa` con su tabla `event_publication` cuando haya eventos que no puedan perderse (pagos, asignaciones).
18. Kafka al extraer módulos como servicios independientes.
19. Resilience4j (llamadas externas) y ShedLock (tareas programadas como expirar solicitudes sin aceptar).
20. `Dockerfile`, compose de producción, pipeline de CI/CD y observabilidad (Prometheus y Grafana).
21. Caché en Redis del catálogo cuando haya tráfico real.

---

## Decisiones

**Tomadas**
- Backend como monolito modular en Spring Boot, con extracción posterior a servicios.
- Un trabajador **debe estar verificado** para quedar disponible. La verificación es manual por ahora (un admin la cambia) y luego se integrará con una API de verificación.
- Comisión cobrada a los trabajadores como modelo de ingresos inicial.
- JWT firmado con HS256 con secreto, emisor y vigencia configurables por ambiente.

**Pendientes**
- **Asignación del trabajador**: estilo Uber (la solicitud se envía a los cercanos y el primero que acepta se queda con ella) o por **postulaciones** (los trabajadores se postulan y el solicitante elige). Cambia el modelo de datos (la segunda opción agrega una entidad `Postulacion`), así que debe decidirse antes de la migración de `Requests`.
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