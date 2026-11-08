# Jornal Backend

Plataforma tipo Uber para que personas, tiendas o empresas soliciten un ayudante o trabajador para una tarea puntual, sin contrato formal (trabajo informal por jornal). El backend está pensado como un **monolito modular** que pueda evolucionar a un sistema distribuido.

> Estado: **en desarrollo temprano (MVP)**. Hoy existen la infraestructura base y el módulo de usuarios. Ver [Estado actual](#estado-actual) y [Lo que falta](#lo-que-falta).

---

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje / build | Java 21, Maven |
| Framework | Spring Boot 4.1.2 |
| Base de datos | PostgreSQL 16 + PostGIS (geolocalización) |
| Cache / geo en vivo | Redis 7 |
| Migraciones | Flyway |
| Seguridad | Spring Security (JWT con resource server, pendiente de activar) |
| Mapeo de objetos | MapStruct + Lombok |
| Modularidad | Spring Modulith 2.1.1 (`starter-core` y `starter-test`) |
| Documentación API | springdoc-openapi (Swagger UI) |
| Observabilidad | Actuator + Micrometer (Prometheus) |
| Pruebas | Spring Boot Test, Testcontainers |
| Entorno local | Docker Compose (integrado con Spring Boot) |

---

## Requisitos

- **JDK 21**
- **Maven 3.9+** (o el wrapper `mvnw` incluido)
- **Docker Desktop** abierto y corriendo
- IntelliJ IDEA reciente (las versiones antiguas pueden no interpretar bien Boot 4 y mostrar errores falsos de compilación)

---

## Cómo correrlo

### 1. Clonar y crear el archivo `.env`

El archivo `.env` **no se sube a git**. Crea uno en la raíz a partir de `.env.example`:

```env
POSTGRES_DB=jornalDb
POSTGRES_USER=jornalAdmin
POSTGRES_PASSWORD=<una_clave_sin_caracteres_especiales>
POSTGRES_PORT=5432

REDIS_PORT=6379
```

Recomendaciones:

- Usa solo letras y números en las claves (evita `ñ`, tildes y `$`).
- Si ya tienes un PostgreSQL instalado en tu máquina usando el puerto 5432, cambia `POSTGRES_PORT` (por ejemplo a `5433`). Spring Boot toma el puerto publicado desde Docker Compose, así que no hay que tocar nada más.

### 2. Levantar la aplicación

Con Docker Desktop abierto:

```bash
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`, o ejecuta `JornalBackendApplication` desde IntelliJ con el perfil `dev`.

Al arrancar, Spring Boot ejecuta `docker compose up` por ti, espera a que PostgreSQL y Redis estén sanos, configura las conexiones automáticamente y Flyway aplica las migraciones. La primera vez tarda más porque descarga las imágenes.

> No necesitas levantar los contenedores a mano ni configurar el datasource en `dev`.

### 3. Comprobar que funciona

- Contenedores: `docker compose ps` (deben estar `running` o `healthy`).
- Salud de la app: `http://localhost:8080/actuator/health`
- Documentación de la API: `http://localhost:8080/swagger-ui.html`

Probar el registro de un usuario (el endpoint depende de que `AuthController` esté creado, ver [Estado actual](#estado-actual)):

```bash
curl.exe -X POST http://localhost:8080/auth/registro -H "Content-Type: application/json" -d "{\"email\":\"test@jornal.com\",\"password\":\"Clave12345\",\"name\":\"Prueba\",\"rol\":\"TRABAJADOR\"}"
```

Repetir la misma petición debe devolver `409` con el código `USER001_EMAIL_REGISTERED`.

### Comandos útiles de Docker

```bash
docker compose ps                 # estado de los contenedores
docker compose logs -f postgres   # logs de la base
docker compose down               # detener (conserva datos)
docker compose down -v            # detener y BORRAR datos (volumen pgdata)
```

---

## Perfiles de Spring

| Archivo | Uso |
|---|---|
| `application.yaml` | Configuración común. Perfil por defecto: `dev` (variable `SPRING_PROFILES_ACTIVE`) |
| `application-dev.yaml` | Desarrollo local con Docker Compose, logs detallados |
| `application-prod.yaml` | Producción: todo por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, etc.), Swagger desactivado |

Cambiar de perfil:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
java -jar app.jar --spring.profiles.active=prod
```

En producción **no** se usa Docker Compose de desarrollo ni secretos dentro del repositorio.

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
    │   │   ├── Shared                      # código transversal
    │   │   │   ├── Errors
    │   │   │   │   ├── ApiException.java       # excepción única de negocio
    │   │   │   │   └── ErrorCode.java          # interfaz: name(), status(), message()
    │   │   │   ├── GlobalExceptionHandler.java # @RestControllerAdvice (ProblemDetail)
    │   │   │   └── SecurityConfig.java         # PasswordEncoder y reglas de acceso
    │   │   └── Users                       # módulo de usuarios
    │   │       ├── DTO
    │   │       │   ├── RegisterRequest.java
    │   │       │   └── UserResponse.java
    │   │       ├── enums
    │   │       │   ├── ErrorUser.java          # errores del módulo (implements ErrorCode)
    │   │       │   ├── Rol.java
    │   │       │   └── VerificationState.java
    │   │       ├── User.java                   # entidad JPA
    │   │       ├── UserMapper.java             # MapStruct
    │   │       ├── UserRepository.java
    │   │       └── UserService.java
    │   └── resources
    │       ├── application.yaml
    │       ├── application-dev.yaml
    │       ├── application-prod.yaml
    │       └── db/migration                # migraciones Flyway
    │           ├── V1__init.sql                # extensión PostGIS
    │           └── V2__...sql                  # tabla de usuarios
    └── test
        └── java/com/jornal
            └── ModularidadTest.java        # verifica los límites entre módulos
```

---

## Convenciones del proyecto

**Base de datos**
- Las **migraciones son la fuente de verdad** del esquema; las entidades solo lo reflejan.
- `spring.jpa.hibernate.ddl-auto: validate`: Hibernate no crea ni modifica tablas, solo valida que coincidan con las entidades.
- Flujo para cada cambio: escribir la migración → escribir/ajustar la entidad → arrancar y validar.
- Nunca editar una migración ya ejecutada; se crea una nueva encima. Cambios en pasos compatibles hacia atrás (expandir → migrar → contraer).

**Módulos (preparados para separarse en servicios)**
- Un paquete por módulo bajo `com.jornal`. Cada módulo es dueño de sus tablas.
- Un módulo **no** importa entidades, repositorios ni mappers de otro: se comunican por su API pública o por eventos.
- Las referencias entre módulos son un `UUID` simple, sin `FOREIGN KEY`. Dentro de un módulo sí se usan claves foráneas.

**Errores**
- Un `enum` por módulo (ej. `ErrorUser`) que implementa `ErrorCode` y define status HTTP y mensaje.
- Se lanza `throw new ApiException(ErrorUser.USER001_EMAIL_REGISTERED)`, o con argumentos: `new ApiException(ErrorUser.USER002_USER_NOTFOUND, id)`.
- `GlobalExceptionHandler` convierte todo en `ProblemDetail` (RFC 9457) con un campo `code` estable para el frontend.

**Mapeo**
- Un mapper MapStruct por entidad/agregado, dentro de su módulo. Nunca se devuelven entidades JPA desde los controladores, siempre DTOs.
- La lógica de negocio (hashear contraseñas, calcular comisiones, decidir estados) va en el servicio, no en el mapper.

---

## Estado actual

- [x] Proyecto Spring Boot 4.1.2 con Java 21 compilando (Maven)
- [x] Docker Compose con PostGIS y Redis, credenciales por `.env`
- [x] Conexión a PostgreSQL verificada desde la aplicación
- [x] Perfiles `dev` y `prod`
- [x] Spring Modulith (`starter-core` y `starter-test`)
- [x] Manejo centralizado de errores (`ApiException`, `ErrorCode`, `GlobalExceptionHandler`)
- [x] Módulo `Users`: entidad, repositorio, mapper, servicio, DTOs y enums
- [ ] Por verificar: migración `V2` de usuarios aplicada y coincidiendo con la entidad
- [ ] Por verificar: endpoint `POST /auth/registro` (controlador) probado de punta a punta

---

## Lo que falta

**Corto plazo (para cerrar el módulo de usuarios)**
1. Controlador de autenticación (`/auth/registro`) y pruebas del flujo completo.
2. **Login con JWT**: `POST /auth/login`, emisión del token, activación del resource server y obtención del usuario autenticado en los controladores.
3. Renombrar los paquetes a minúscula (`users`, `shared`) y aplanar subpaquetes para que Spring Modulith permita compartir enums y DTOs entre módulos.
4. Normalizar el nombre del campo `password_hash` a `passwordHash` en la entidad (con `@Column(name = "password_hash")`).

**Módulos del MVP**
5. `catalogo`: categorías de trabajo (aseo, mudanza, cargue, construcción...).
6. `PerfilTrabajador`: descripción, radio de trabajo, disponibilidad, calificación promedio, categorías que ofrece.
7. `solicitudes`: máquina de estados `PUBLICADA → ASIGNADA → EN_CURSO → COMPLETADA` (más `CANCELADA` y `EXPIRADA`), con quién puede ejecutar cada transición.
8. `matching`: búsqueda de trabajadores cercanos con PostGIS (`ST_DWithin`) y ubicación en vivo con Redis GEO.
9. `calificaciones` (en ambos sentidos).
10. `pagos`: pasarela (Wompi, PayU o Mercado Pago) detrás de una interfaz propia, retención del pago y cobro de la comisión.
11. `notificaciones` (push/SMS) y tiempo real con WebSockets.

**Infraestructura y calidad**
12. Spring Modulith `starter-jpa` + migración de la tabla `event_publication` cuando haya eventos que no puedan perderse (pagos, asignaciones).
13. Kafka al extraer módulos como servicios independientes.
14. Pruebas de integración con Testcontainers (usar la imagen `postgis/postgis` como sustituto compatible de `postgres`).
15. `Dockerfile`, compose de producción y pipeline de CI.
16. Resilience4j y ShedLock cuando existan llamadas externas y tareas programadas (expirar solicitudes sin aceptar).

---

## Decisiones pendientes

- **Asignación del trabajador**: estilo Uber (la solicitud se envía a los cercanos y el primero que acepta se queda con ella) vs. **postulaciones** (los trabajadores se postulan y el solicitante elige). Esto cambia el modelo de datos (la segunda opción añade una entidad `Postulacion`) y conviene decidirlo antes de escribir la migración de `solicitudes`.
- **Modelo de ingresos**: comisión a los trabajadores (planteada inicialmente) y posible tarifa de servicio al solicitante o suscripciones para empresas recurrentes.
- **Marco legal laboral**: validar con un abogado laboral que el modelo no configure una relación laboral encubierta (el trabajador debe poder fijar su disponibilidad y rechazar trabajos libremente).
- **Seguridad y confianza**: verificación de identidad, calificaciones mutuas y botón de reporte.

---

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| IntelliJ: `release version 5 not supported` | El IDE usa nivel 1.5 por defecto. Actualiza IntelliJ, recarga Maven y revisa **Settings > Compiler > Java Compiler** (Target bytecode version = 21). `mvn clean compile` por consola es la referencia |
| `FATAL: password authentication failed` (28P01) | (1) Otro PostgreSQL local ocupa el 5432: cambia `POSTGRES_PORT`. (2) El volumen guardó credenciales viejas: `docker compose down -v`. (3) Caracteres no ASCII en la clave |
| Spring Boot no detecta la base | La imagen es `postgis/postgis`; el servicio necesita la etiqueta `org.springframework.boot.service-connection: postgres` en el compose |
| `Dependency 'spring-modulith-...' not found` | Falta la propiedad `spring-modulith.version` o el BOM está mal ubicado. `dependencyManagement` va dentro de `project`, fuera de `dependencies` |
| Falla al validar esquema por `event_publication` | `spring-modulith-starter-jpa` está activo sin su migración. Déjalo comentado hasta usar eventos persistentes |
| `Flyway checksum mismatch` | Se editó una migración ya aplicada. En desarrollo: `docker compose down -v`. Después, nunca editar migraciones ejecutadas |
| `Unmapped target property` (MapStruct) | Campo de la entidad sin origen en el request: agregar `@Mapping(target = "...", ignore = true)` |

---

## Licencia

Por definir.