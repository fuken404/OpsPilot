# OpsPilot

Plataforma de gestión de incidentes de TI para varias empresas: tickets, responsables, prioridades y acuerdos de nivel de servicio (SLA), con aislamiento de datos por organización y auditoría.

> En construcción por fases. Van las fases 1 a 4: **base**, **núcleo de incidentes**, **motor de SLA** y **tiempo real con tablero**. Ver [Plan](#plan).

## Arquitectura

Monolito modular: un solo despliegue, con módulos que no pueden usar las clases internas de otros. Spring Modulith lo verifica en cada build.

| Módulo | Responsabilidad |
|---|---|
| `identity` | Usuarios de la aplicación, creados en su primer ingreso |
| `organizations` | Organizaciones, miembros, roles e invitaciones |
| `incidents` | Tickets, estados, asignación y comentarios |
| `sla` | Políticas, relojes de respuesta y resolución, calendario laboral y barrido |
| `realtime` | WebSocket por organización y difusión entre instancias con Redis |
| `dashboard` | Métricas del equipo (solo lectura) |
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
- **SLA en horario laboral.** Cada ticket tiene dos relojes: primera respuesta y resolución. Cuentan de lunes a viernes de 8:00 a 18:00 (hora de Colombia) sin festivos; lo crítico cuenta las 24 horas. Los festivos se calculan, no se escriben a mano: fijos, trasladados al lunes (Ley Emiliani) y los que dependen de la Pascua.
- **Pausas sin errores acumulados.** El vencimiento siempre es inicio + objetivo + minutos pausados, medidos con el mismo reloj. Esperar al cliente pausa la resolución; si el cliente reabre un ticket resuelto, el tiempo que estuvo resuelto no cuenta. Cambiar la prioridad solo recalcula.
- **Barrido idempotente.** Avisa los relojes con el 80 % consumido y escala los vencidos a los administradores. Cada aviso queda marcado en el reloj y las filas se toman con `FOR UPDATE SKIP LOCKED`: dos barridos a la vez no duplican alertas (hay una prueba con dos hilos). Corre una transacción por organización, así que Row Level Security sigue aplicando.
- **Quién dispara el barrido.** En local, un `@Scheduled` dentro de la app (perfil `demo`). En Vercel la API se apaga sin tráfico, así que lo llama GitHub Actions cada 5 minutos con un secreto propio (`X-Sweep-Token`).
- **Tiempo real después del commit.** Los eventos salen con `@TransactionalEventListener`: nadie ve un cambio que luego se deshizo. Solo dicen qué cambió (ticket, acción, estado); la pantalla vuelve a pedir los datos a la API, que aplica los permisos de siempre.
- **Quién recibe qué.** El equipo recibe todo lo de su organización; un cliente solo lo de sus propios tickets, y ni el aviso de una nota interna. Otra organización no recibe nada. Una prueba lo verifica con cuatro conexiones reales.
- **WebSocket sin token en la URL.** El navegador no puede mandar `Authorization` al abrir un WebSocket y poner el token en la URL lo deja en los logs. Por eso el token va en el primer mensaje, se valida igual que en la API y la conexión se cierra cuando vence; el cliente se reconecta con espera creciente.
- **Varias instancias.** Con `REALTIME_BROKER=redis`, cada evento pasa por un canal de Redis (Valkey en local, Upstash en producción) y cada instancia lo entrega a sus conexiones. La prueba de tiempo real corre contra Redis real.
- **Tablero con SQL directo.** Un módulo de solo lectura que no toca las entidades de los demás; Row Level Security filtra igual. Paleta validada para daltonismo y tabla alternativa para lectores de pantalla.
- **Reloj inyectado.** Las pruebas adelantan el tiempo en vez de esperar horas.

## Stack

Java 25 · Spring Boot 4.1 · Spring Security (OAuth 2.0 / OIDC) · Spring Modulith · WebSocket · JPA · Flyway · PostgreSQL 17 · Redis (Valkey) · Keycloak 26 · Angular 22 (Signals, standalone, zoneless) · Angular Material · Vitest · JUnit 5 · Testcontainers · Docker Compose · GitHub Actions

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
3. **Motor de SLA** ✅ Políticas por prioridad, calendario laboral con festivos, pausas, alertas y escalamiento.
4. **Tiempo real** ✅ WebSockets por organización, difusión con Redis y tablero con métricas.
5. **Notificaciones y adjuntos:** outbox transaccional, correos y archivos.
6. **Publicación:** Vercel, demo pública, Playwright y OpenAPI.
