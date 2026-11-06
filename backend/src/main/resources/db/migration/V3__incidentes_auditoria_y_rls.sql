-- La aplicación se conecta con un usuario que puede migrar, pero cada conexión de la app cambia a
-- opspilot_app: sin privilegios de dueño, así que Row Level Security siempre se aplica.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'opspilot_app') then
        create role opspilot_app nologin noinherit nobypassrls;
    end if;
end
$$;
grant opspilot_app to current_user;
grant usage on schema public to opspilot_app;

create table ticket (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    number          integer     not null,
    title           text        not null check (length(title) between 3 and 200),
    description     text        not null check (length(description) <= 10000),
    category        text        not null check (category in ('HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'OTHER')),
    priority        text        not null check (priority in ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    status          text        not null check (status in ('NEW', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    requester_id    uuid        not null references app_user (id),
    assignee_id     uuid references app_user (id),
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    resolved_at     timestamptz,
    closed_at       timestamptz,
    version         bigint      not null default 0,
    unique (organization_id, number)
);
create index ticket_org_status_idx on ticket (organization_id, status);
create index ticket_org_assignee_idx on ticket (organization_id, assignee_id);
create index ticket_org_requester_idx on ticket (organization_id, requester_id);

-- Numeración por organización (ACME-1, ACME-2...) sin huecos por carreras: un UPDATE ... RETURNING atómico.
create table ticket_counter (
    organization_id uuid primary key references organization (id) on delete cascade,
    last_number     integer not null
);

create table ticket_comment (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    ticket_id       uuid        not null references ticket (id) on delete cascade,
    author_id       uuid        not null references app_user (id),
    body            text        not null check (length(body) between 1 and 5000),
    internal        boolean     not null,
    created_at      timestamptz not null
);
create index ticket_comment_ticket_idx on ticket_comment (ticket_id, created_at);

create table audit_event (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    actor_id        uuid references app_user (id),
    entity_type     text        not null,
    entity_id       uuid        not null,
    action          text        not null,
    details         jsonb       not null default '{}',
    occurred_at     timestamptz not null
);
create index audit_event_entity_idx on audit_event (organization_id, entity_type, entity_id, occurred_at);
create index audit_event_org_time_idx on audit_event (organization_id, occurred_at desc);

-- Aislamiento por organización en la propia base: si el código olvida filtrar, igual no ve otra empresa.
-- Sin organización activa en la transacción, current_setting devuelve null y no se ve ninguna fila.
alter table ticket enable row level security;
alter table ticket force row level security;
create policy por_organizacion on ticket
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table ticket_counter enable row level security;
alter table ticket_counter force row level security;
create policy por_organizacion on ticket_counter
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table ticket_comment enable row level security;
alter table ticket_comment force row level security;
create policy por_organizacion on ticket_comment
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table audit_event enable row level security;
alter table audit_event force row level security;
create policy por_organizacion on audit_event
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

grant select, insert, update, delete on all tables in schema public to opspilot_app;
-- La auditoría solo se agrega: la app no puede modificarla ni borrarla.
revoke update, delete on audit_event from opspilot_app;
revoke all on flyway_schema_history from opspilot_app;
alter default privileges in schema public grant select, insert, update, delete on tables to opspilot_app;
