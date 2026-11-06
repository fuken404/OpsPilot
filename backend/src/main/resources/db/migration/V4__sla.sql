-- Políticas de SLA por organización y prioridad. Si una organización no tiene fila, rigen los valores por defecto.
create table sla_policy (
    id                 uuid primary key,
    organization_id    uuid    not null references organization (id) on delete cascade,
    priority           text    not null check (priority in ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    response_minutes   integer not null check (response_minutes between 1 and 100000),
    resolution_minutes integer not null check (resolution_minutes between 1 and 1000000),
    business_hours     boolean not null,
    unique (organization_id, priority)
);

-- Un reloj por ticket y compromiso: primera respuesta y resolución.
create table sla_clock (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    ticket_id       uuid        not null references ticket (id) on delete cascade,
    kind            text        not null check (kind in ('RESPONSE', 'RESOLUTION')),
    target_minutes  integer     not null,
    business_hours  boolean     not null,
    started_at      timestamptz not null,
    paused_since    timestamptz,
    paused_minutes  bigint      not null default 0,
    due_at          timestamptz not null,
    warn_at         timestamptz not null,
    met_at          timestamptz,
    stopped_at      timestamptz,
    warned_at       timestamptz,
    breached_at     timestamptz,
    version         bigint      not null default 0,
    unique (ticket_id, kind)
);
-- Lo que el barrido revisa: relojes corriendo, ordenados por vencimiento.
create index sla_clock_running_idx on sla_clock (organization_id, warn_at)
    where met_at is null and stopped_at is null and paused_since is null;

alter table sla_policy enable row level security;
alter table sla_policy force row level security;
create policy por_organizacion on sla_policy
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table sla_clock enable row level security;
alter table sla_clock force row level security;
create policy por_organizacion on sla_clock
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

grant select, insert, update, delete on sla_policy, sla_clock to opspilot_app;
