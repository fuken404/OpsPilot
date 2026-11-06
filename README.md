# OpsPilot

Plataforma de gestión de incidentes de TI para varias empresas: tickets, responsables, prioridades y acuerdos de nivel de servicio (SLA), con aislamiento de datos por organización y auditoría.

> En construcción por fases. Van la **fase 1: base** y la **fase 2: núcleo de incidentes**. Ver [Plan](#plan).

## Arquitectura

Monolito modular: un solo despliegue, con módulos que no pueden usar las clases internas de otros. Spring Modulith lo verifica en cada build.

| Módulo | Responsabilidad |
|---|---|
| `identity` | Usuarios de la aplicación, creados en su primer ingreso |
| `organizations` | Organizaciones, miembros, roles e invitaciones |
| `incidents` | Tickets, estados, asignación y comentarios |
| `audit` | Registro de solo agregar con cada cambio |
| `shared` | Seguridad, errores, reloj y organización activa |

**Decisiones**

- **Keycloak autentica; la aplicación autoriza.** El token solo dice quién es la persona. Su rol en cada organización vive en la base de datos, así que puede ser técnico en una empresa y cliente en otra.
- **Una sola puerta de permisos.** `OrganizationAccess` responde 404 a quien no es miembro, para no revelar qué organizaciones existen, y 403 a quien no tiene el rol.
- **Invitaciones seguras.** Se aceptan en el primer ingreso solo si el correo está verificado; si no, alguien podría registrarse con un correo ajeno.
- **Concurrencia.** Cambiar o quitar administradores bloquea la organización durante la transacción: dos administradores que se quitan el rol a la vez no dejan la organización sin ninguno. Hay una prueba que lo reproduce con dos hilos.
- **Audiencia del token.** La API rechaza tokens del mismo realm emitidos para otra aplicación.
- **Row Level Security.** La app se conecta con un rol sin privilegios (`opspilot_app`) y cada transacción fija su organización. Postgres filtra tickets, comentarios y auditoría por sí solo: si una consulta olvida el filtro, no ve otra empresa, y sin organización activa no ve nada. Flyway migra con el usuario dueño por una conexión aparte.
- **Estados con reglas.** Un flujo explícito (`TicketWorkflow`) define qué cambios existen y quién los hace: el equipo mueve el ticket; el cliente solo cancela antes de que lo tomen y confirma o reabre la solución. Si el cliente responde mientras se espera su respuesta, el ticket vuelve a estar en curso.
- **Bloqueo optimista.** Cada cambio lleva la versión que la persona tenía en pantalla. Si dos técnicos toman el mismo ticket a la vez, gana uno y el otro recibe 409; una prueba lo reproduce con dos hilos.
- **Notas internas.** El cliente nunca recibe las notas del equipo ni el historial, y crea con prioridad media: la prioridad define el SLA y la decide el equipo.
- **Auditoría en la misma transacción.** Si el cambio se guarda, su registro también; si falla, ninguno. El rol de la app no puede editar ni borrar la tabla de auditoría.
- **Numeración sin huecos por organización** (Acme #1, #2…) con un contador que se bloquea mientras dura la transacción.
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
2. **Núcleo de incidentes** ✅ Tickets, estados, asignación, comentarios, auditoría y Row Level Security en Postgres.
3. **Motor de SLA:** políticas por prioridad, calendario laboral con festivos, pausas, alertas y escalamiento.
4. **Tiempo real:** WebSockets por organización y tablero con métricas.
5. **Notificaciones y adjuntos:** outbox transaccional, correos y archivos.
6. **Publicación:** Vercel, demo pública, Playwright y OpenAPI.
