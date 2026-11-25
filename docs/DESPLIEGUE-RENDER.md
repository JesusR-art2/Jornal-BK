# Despliegue en Render (plan gratuito)

Guía para publicar el backend en Render con **una sola base PostgreSQL 18** (región Oregon). El escenario se probó
en local con `infra/docker-compose.render.yml` (PostgreSQL 18.6 + PostGIS 3.6, una base, un esquema por servicio,
sin gateway) y `scripts/verificar-render.mjs` (salud, CORS y flujo completo de vacantes con pago automático).

## 0. Opción rápida: Blueprint (`render.yaml`)

En Render: **New → Blueprint**, elija este repositorio y la rama. El archivo `render.yaml` crea de una vez la base
PostgreSQL (free), el Key Value (free) y los servicios `jornal-core-co`, `jornal-payments` y `jornal-notifications`
(Docker, free, Oregon). Render pide solo 4 valores:

| Variable (en jornal-core-co) | Valor |
|---|---|
| `SPRING_RABBITMQ_ADDRESSES` | La «AMQP URL» de CloudAMQP (`amqps://...`) |
| `CORS_ALLOWED_ORIGINS` | URL del frontend (por ejemplo `https://jornal-ft.onrender.com`) |
| `ADMIN_EMAIL` | Correo del primer administrador |
| `ADMIN_PASSWORD` | Su contraseña |

- La base llega como `DATABASE_URL` (`postgresql://...`) y la aplicación la traduce a JDBC
  (`jornal-common`, `DatabaseUrlEnvironmentPostProcessor`).
- `JWT_SECRET` e `INTERNAL_TOKEN` los genera Render; pagos y notificaciones copian del core el JWT, el token, la URL de
  RabbitMQ y los orígenes CORS (`fromService` con `envVarKey`).
- Notificaciones llama al core en `https://<host del core>.onrender.com` (`CORE_HOST` sale de `fromService`). Si Render
  le pone otro subdominio público al core, defina `CORE_URL_TEMPLATE` con la URL real en notificaciones.
- Si después cambia la URL del frontend, actualice `CORS_ALLOWED_ORIGINS` en el core y vuelva a desplegar notificaciones.

## 1. Servicios

| Servicio (docker-compose) | Qué hace | ¿Lo necesita el frontend? | En Render |
|---|---|---|---|
| `core-co` | API de Colombia: cuentas, catálogo, perfil regional, vacantes, pagos de vacantes, consola | **Sí** (todas las pantallas) | **Desplegar** |
| `notifications` | Bandeja de avisos, WebSocket `/ws` (mensaje emergente y avisos en vivo), correo | **Sí** (Mensajes, alertas) | **Desplegar** |
| `payments` | Pago automático al trabajador (y cobros del flujo de pedidos anterior) | Sí, si se usa el pago automático | **Desplegar** |
| `core-br`, `core-us`, `core-fr`, `core-jp`, `core-de` | Las demás regiones (mismo jar) | No para una demo de Colombia | Fuera |
| `dispatch` | Asignación automática del flujo de pedidos anterior | No (el flujo de vacantes no lo usa) | Fuera |
| `billing` | Comprobantes y tasas del flujo de pedidos anterior | No | Fuera |
| `gateway` (Nginx) | Entrada única `/api/{región}` y `/subsystems/...` | No: se reemplaza por URL fijas | Fuera |
| `translate` (LibreTranslate) | Traducción automática del contenido de vacantes | No (opcional; ~2 GB de RAM) | Fuera: `TRANSLATION_URL` vacío |
| `mailpit` | Correo de prueba | No | Fuera (SMTP real opcional) |
| `prometheus`, `grafana` | Métricas | No | Se quedan en local |
| `postgres`, `rabbitmq`, `redis` | Infraestructura | Sí | Base de Render, CloudAMQP y Key Value de Render |

Prometheus y Grafana solo **leen** `/actuator/prometheus`; ningún servicio depende de ellos para arrancar (la
simulación de Render corre sin ellos).

## 2. Base de datos: una sola base, un esquema por servicio

- **Hoy (Docker):** cada servicio tiene su base: `jornal_co` … `jornal_de` y `jornal_payments`, `jornal_dispatch`,
  `jornal_notifications`, `jornal_billing` (`infra/postgres/init-databases.sql`).
- **Render:** todos usan la misma `SPRING_DATASOURCE_URL` y cada uno su esquema con la variable **`DB_SCHEMA`**:
  `core_co`, `payments`, `notifications`.
  - Flyway crea el esquema y su tabla de historial ahí (`spring.flyway.schemas` / `default-schema`).
  - Cada conexión ejecuta `SET search_path TO <DB_SCHEMA>, public` (Hikari `connection-init-sql`), así JPA, JDBC,
    el Outbox de Spring Modulith y ShedLock usan su esquema sin cambiar código ni migraciones.
  - PostGIS y unaccent se crean en `public` antes de migrar (`spring.flyway.init-sqls` del core) y sirven a todos.
  - Se eligió `DB_SCHEMA` en lugar de `?currentSchema=` en la URL porque así la URL es idéntica en todos los
    servicios y `public` (PostGIS) sigue en la ruta de búsqueda.
  - Sin `DB_SCHEMA` todo queda en `public`, como antes (Docker local no cambia).
- **Compatibilidad con PostgreSQL 18:** Flyway 12.4.0 (`flyway-database-postgresql`) y el driver JDBC
  `org.postgresql:postgresql` 42.7.14. Probado contra PostgreSQL 18.6: las 21 migraciones del core y las de
  pagos y notificaciones se aplican sin advertencias de versión.
- **PostGIS en Render:** el core ejecuta `CREATE EXTENSION IF NOT EXISTS postgis SCHEMA public` al arrancar. Si el
  arranque fallara en ese paso, ejecútelo una vez desde la consola de la base (`psql` con la URL externa).

## 3. Broker de mensajes

El proyecto usa **RabbitMQ** (AMQP 0-9-1, Spring AMQP) con el exchange `jornal.events`, colas durables por
proceso y región, y cola de mensajes muertos. Render no lo ofrece; se propone **CloudAMQP**, plan gratuito
*Little Lemur* (RabbitMQ gestionado; revise sus límites de conexiones y mensajes vigentes).

- Conexión por variable: **`SPRING_RABBITMQ_ADDRESSES`**=`amqps://usuario:clave@host/vhost` (la «AMQP URL» que
  muestra CloudAMQP). Si no está, se usan `RABBITMQ_HOST/PORT/USER/PASSWORD` como en Docker.
- **`MESSAGING_REGIONS=co`** y **`MESSAGING_CONSUMERS=core,payments,notifications`**: solo se declaran las colas
  de lo desplegado (sin colas de `dispatch`/`billing` que nadie consumiría).

## 4. Cada servicio desplegable

- **Dockerfile:** uno por servicio, multietapa (Maven → JRE 21), contexto de construcción = **raíz del repositorio**:
  `jornal-core/Dockerfile`, `jornal-payments/Dockerfile`, `jornal-notifications/Dockerfile` (también existen los de
  `dispatch` y `billing`; `docker-compose.yml` usa estos mismos archivos).
- **Puerto:** `server.port=${PORT:...}`; Render define `PORT` (10000). En Docker local sigue siendo 8080.
- **Salud:** `/actuator/health` (público). Configure ese *Health Check Path* en Render.
- **Memoria:** la imagen limita la JVM para 512 MB (`JAVA_TOOL_OPTIONS`). Medido en la simulación: core ~365 MB,
  pagos ~190 MB, notificaciones ~225 MB.
- **CORS:** `CORS_ALLOWED_ORIGINS` en el core y en notificaciones (orígenes separados por coma; admite patrones como
  `https://*.onrender.com`). El WebSocket acepta cualquier origen y se autentica con el JWT en el CONNECT.

### Sin gateway: URL fijas de Render

No hay descubrimiento de servicios. En Docker el gateway Nginx enruta por nombre de contenedor
(`/api/co/...` → `core-co:8080`, `/subsystems/notifications/...` → `notifications:8080`). En Render no se despliega:

| Antes (gateway) | En Render |
|---|---|
| `/api/co/...` | `https://<core-co>.onrender.com/...` |
| `/subsystems/notifications/...` | `https://<notifications>.onrender.com/...` |
| WebSocket `/subsystems/notifications/ws` | `wss://<notifications>.onrender.com/ws` |
| notificaciones → core (`http://core-{region}:8080`) | `CORE_URL_TEMPLATE=https://<core-co>.onrender.com` |

- En el plan gratuito los servicios se comunican por sus URL públicas `https://…onrender.com`.
- La consulta interna `/internal/contacts` (notificaciones → core) ya no la bloquea el gateway; la protege el
  token `INTERNAL_TOKEN`, que debe ser largo, aleatorio e igual en el core y en notificaciones.
- **Frontend (siguiente paso):** necesitará dos variables (URL del core y de notificaciones) en lugar de las rutas
  `/api/{región}` y `/subsystems/...` del proxy de Vite, y mostrar solo Colombia.

### Plan gratuito: tenga en cuenta

- Los servicios se duermen tras ~15 minutos sin tráfico; la primera petición tarda cerca de un minuto. Mientras
  notificaciones o pagos duermen, sus mensajes esperan en CloudAMQP y se procesan al despertar.
- Sin SMTP configurado, los correos quedan como `FAILED` en el registro de entregas; la app sigue funcionando.
- Revise la vigencia de la base gratuita de Render (las bases gratuitas vencen tras un tiempo).

## 5. Tabla de servicios

Configuración común en Render para los tres: *Runtime* **Docker**, *Docker Build Context Directory* **`.`**,
*Health Check Path* **`/actuator/health`**, región **Oregon** (la misma de la base), instancia **Free**.

| Orden | Servicio | Dockerfile | Variables de entorno (sin valores secretos aquí) | Depende de |
|---|---|---|---|---|
| 1 | PostgreSQL 18 (Render) | — | — (ya creada) | — |
| 2 | Key Value (Render, compatible con Redis) | — | — | — |
| 3 | RabbitMQ (CloudAMQP, Little Lemur) | — | — | — |
| 4 | **core-co** | `jornal-core/Dockerfile` | `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DB_SCHEMA=core_co`, `APP_REGION=CO`, `SPRING_DATA_REDIS_URL`, `SPRING_RABBITMQ_ADDRESSES`, `MESSAGING_REGIONS=co`, `MESSAGING_CONSUMERS=core,payments,notifications`, `JWT_SECRET`, `JWT_ISSUER`, `INTERNAL_TOKEN`, `CORS_ALLOWED_ORIGINS`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `SERVER_FORWARD_HEADERS_STRATEGY=framework`; opcionales: `TRANSLATION_URL` (vacío), `CONSOLE_SERVICES` | PostgreSQL, Key Value, CloudAMQP |
| 5 | **payments** | `jornal-payments/Dockerfile` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DB_SCHEMA=payments`, `SPRING_RABBITMQ_ADDRESSES`, `MESSAGING_REGIONS=co`, `MESSAGING_CONSUMERS=core,payments,notifications`, `SERVER_FORWARD_HEADERS_STRATEGY=framework` | PostgreSQL, CloudAMQP (y el core para los mensajes) |
| 6 | **notifications** | `jornal-notifications/Dockerfile` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DB_SCHEMA=notifications`, `SPRING_RABBITMQ_ADDRESSES`, `MESSAGING_REGIONS=co`, `MESSAGING_CONSUMERS=core,payments,notifications`, `JWT_SECRET` y `JWT_ISSUER` (iguales al core), `INTERNAL_TOKEN` (igual al core), `CORE_URL_TEMPLATE` (URL pública del core), `CORS_ALLOWED_ORIGINS`, `SERVER_FORWARD_HEADERS_STRATEGY=framework`; opcionales: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM` | PostgreSQL, CloudAMQP, core-co (URL) |
| 7 | Frontend | — | URL del core y de notificaciones | core-co, notifications |
| 8 | Volver a core-co y notifications | — | Agregar la URL del frontend a `CORS_ALLOWED_ORIGINS` | Frontend |

Ejemplo con valores ficticios: `.env.render.example`.

## 6. Comprobar

- En local, antes de subir: `docker compose -f infra/docker-compose.render.yml up -d --build` y
  `PSQL_CONTAINER=jornal-render-db-1 node scripts/verificar-render.mjs`.
- En Render, ya desplegado:
  `CORE_URL=https://<core>.onrender.com NOTIFICATIONS_URL=https://<notifications>.onrender.com ORIGIN=https://<frontend>.onrender.com node scripts/verificar-render.mjs`
  (crea cuentas de prueba; el paso del pago automático solo corre en la simulación local).
