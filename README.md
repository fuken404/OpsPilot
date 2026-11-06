# OpsPilot

Plataforma de gestión de incidentes de TI para varias empresas: tickets, responsables, prioridades y acuerdos de nivel de servicio (SLA), con aislamiento de datos por organización y auditoría.

> En construcción por fases. Esta es la **fase 1: base**. Ver [Plan](#plan).

## Arquitectura

Monolito modular: un solo despliegue, con módulos que no pueden usar las clases internas de otros. Spring Modulith lo verifica en cada build.

| Módulo | Responsabilidad |
|---|---|
| `identity` | Usuarios de la aplicación, creados en su primer ingreso |
| `organizations` | Organizaciones, miembros, roles e invitaciones |
| `shared` | Seguridad, errores y reloj |

**Decisiones**

- **Keycloak autentica; la aplicación autoriza.** El token solo dice quién es la persona. Su rol en cada organización vive en la base de datos, así que puede ser técnico en una empresa y cliente en otra.
- **Una sola puerta de permisos.** `OrganizationAccess` responde 404 a quien no es miembro, para no revelar qué organizaciones existen, y 403 a quien no tiene el rol.
- **Invitaciones seguras.** Se aceptan en el primer ingreso solo si el correo está verificado; si no, alguien podría registrarse con un correo ajeno.
- **Concurrencia.** Cambiar o quitar administradores bloquea la organización durante la transacción: dos administradores que se quitan el rol a la vez no dejan la organización sin ninguno. Hay una prueba que lo reproduce con dos hilos.
- **Audiencia del token.** La API rechaza tokens del mismo realm emitidos para otra aplicación.
- **Reloj inyectado.** Prepara el motor de SLA para probarse sin esperar.

## Stack

Java 25 · Spring Boot 4.1 · Spring Security (OAuth 2.0 / OIDC) · Spring Modulith · JPA · Flyway · PostgreSQL 17 · Keycloak 26 · Angular 22 (Signals, standalone, zoneless) · Angular Material · Vitest · JUnit 5 · Testcontainers · Docker Compose · GitHub Actions

## Correr en local

Necesita Docker, Java 25 y Node 22.22 o superior.

```bash
docker compose up -d                                       # Postgres, Keycloak, Valkey y Mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
cd frontend && npm install && npx ng serve                 # http://localhost:4200
```

| Servicio | URL |
|---|---|
| Aplicación | http://localhost:4200 |
| API y documentación | http://localhost:8081/api/docs/ui |
| Keycloak (admin / admin) | http://localhost:8180 |
| Mailpit | http://localhost:8025 |

**Cuentas de demostración** (contraseña `demo-opspilot`, solo para el entorno local):

| Usuario | Acme Logística | Nimbus Salud |
|---|---|---|
| `ana.admin` | Administradora | Administradora |
| `tomas.tecnico` | Técnico | — |
| `carla.cliente` | Cliente | — |
| `lucia.cliente` | — | Cliente |

## Pruebas

```bash
cd backend && ./mvnw verify          # incluye Testcontainers: necesita Docker
cd frontend && npx ng test --watch=false
```

## Plan

1. **Base** ✅ Identidad, organizaciones, roles, invitaciones, Docker Compose y CI.
2. **Núcleo de incidentes:** tickets, estados, asignación, comentarios, auditoría y Row Level Security en Postgres.
3. **Motor de SLA:** políticas por prioridad, calendario laboral con festivos, pausas, alertas y escalamiento.
4. **Tiempo real:** WebSockets por organización y tablero con métricas.
5. **Notificaciones y adjuntos:** outbox transaccional, correos y archivos.
6. **Publicación:** Vercel, demo pública, Playwright y OpenAPI.
