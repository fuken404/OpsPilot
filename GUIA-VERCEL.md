# Publicar OpsPilot en Vercel

Todo en un solo proyecto de Vercel con tres servicios, enrutados por dominio:

| Dominio | Servicio | Qué es |
|---|---|---|
| `opspilot.fuken404.dev` | `web` | Angular, estático |
| `api.opspilot.fuken404.dev` | `api` | Spring Boot en contenedor |
| `auth.opspilot.fuken404.dev` | `auth` | Keycloak en contenedor |

Base de datos en **Neon** y Redis en **Upstash**, ambos gratis y sin tarjeta.

## 1. Neon (PostgreSQL)

1. <https://neon.tech> → **Sign up** con GitHub.
2. **Create project:** nombre `opspilot`, Postgres **17**, región **AWS US East (N. Virginia)**.
3. Menú **Databases** → **New database** dos veces: `opspilot` y `keycloak` (dueño: el que trae, `neondb_owner`).
4. **Connect** → desactiva **Connection pooling** → copia **host**, **usuario** y **contraseña**.
   - Sin pooling: la app cambia de rol por conexión para Row Level Security, y el pooler no lo conserva.

## 2. Upstash (Redis)

1. <https://upstash.com> → **Sign up** con GitHub → **Redis** → **Create database**.
2. Nombre `opspilot`, región **us-east-1**, plan **Free**.
3. En la base, copia la URL **`rediss://default:…@….upstash.io:6379`** (con dos `s`).

## 3. Vercel

1. **Add New → Project** → importa `fuken404/OpsPilot`.
2. **Root Directory:** déjalo en la raíz (`./`). El archivo `vercel.json` define los tres servicios.
3. Antes de **Deploy**, abre **Environment Variables** y agrega:

| Nombre | Valor |
|---|---|
| `PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | `demo` |
| `DATABASE_URL` | `jdbc:postgresql://HOST_DE_NEON/opspilot?sslmode=require` |
| `DATABASE_USERNAME` | usuario de Neon |
| `DATABASE_PASSWORD` | contraseña de Neon |
| `KC_DB_URL` | `jdbc:postgresql://HOST_DE_NEON/keycloak?sslmode=require` |
| `KC_DB_USERNAME` | usuario de Neon |
| `KC_DB_PASSWORD` | contraseña de Neon |
| `KC_HOSTNAME` | `https://auth.opspilot.fuken404.dev` |
| `OIDC_ISSUER_URI` | `https://auth.opspilot.fuken404.dev/realms/opspilot` |
| `ALLOWED_ORIGINS` | `https://opspilot.fuken404.dev` |
| `APP_URL` | `https://opspilot.fuken404.dev` |
| `REDIS_URL` | la URL `rediss://…` de Upstash |
| `MAIL_TRANSPORT` | `log` |
| `ATTACHMENTS_STORAGE` | `database` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `4` |

4. **Deploy.** Los tres servicios tardan unos minutos la primera vez.
5. **Settings → Domains** → agrega los tres dominios: `opspilot.fuken404.dev`, `api.opspilot.fuken404.dev` y `auth.opspilot.fuken404.dev`.

## 4. Cloudflare (DNS de fuken404.dev)

Para cada dominio, un **CNAME** con el valor que muestra Vercel, con la **nube gris (DNS only)**:

| Nombre | Destino |
|---|---|
| `opspilot` | el de Vercel |
| `api.opspilot` | el de Vercel |
| `auth.opspilot` | el de Vercel |

## 5. Comprobar

- <https://api.opspilot.fuken404.dev/actuator/health> → `{"status":"UP"}` (la primera vez puede tardar unos 15 s).
- <https://auth.opspilot.fuken404.dev/realms/opspilot> → JSON del realm.
- <https://opspilot.fuken404.dev> → portada; **Probar la demo** con `ana.admin` / `demo-opspilot`.

## Límites del plan gratuito

- Los contenedores se apagan a los 5 minutos sin tráfico: el primer ingreso tarda unos segundos. La portada los despierta en segundo plano.
- El plan Hobby incluye 4 horas de CPU y 360 GB-hora de memoria al mes; si se superan, Vercel pausa el proyecto 30 días. En **Usage** se ve el consumo.
- La consola de administración de Keycloak no se publica (`/admin` redirige a la app).
