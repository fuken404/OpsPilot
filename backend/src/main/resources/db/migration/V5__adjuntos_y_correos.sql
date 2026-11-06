create table ticket_attachment (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    ticket_id       uuid        not null references ticket (id) on delete cascade,
    uploader_id     uuid        not null references app_user (id),
    filename        text        not null check (length(filename) between 1 and 200),
    content_type    text        not null,
    size_bytes      bigint      not null check (size_bytes > 0),
    sha256          text        not null,
    -- Dónde está el archivo. Nunca deriva del nombre que mandó el usuario.
    storage_key     text        not null unique,
    internal        boolean     not null,
    created_at      timestamptz not null
);
create index ticket_attachment_ticket_idx on ticket_attachment (ticket_id, created_at);

-- Bandeja de salida: los correos se escriben en la misma transacción del cambio y un despachador
-- los envía después, con reintentos. Si el servidor de correo cae, no se pierde ninguno.
create table email_outbox (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    ticket_id       uuid references ticket (id) on delete cascade,
    recipient       text        not null,
    subject         text        not null,
    body            text        not null,
    status          text        not null check (status in ('PENDING', 'SENT', 'FAILED')),
    attempts        integer     not null default 0,
    next_attempt_at timestamptz not null,
    last_error      text,
    created_at      timestamptz not null,
    sent_at         timestamptz
);
create index email_outbox_pending_idx on email_outbox (organization_id, next_attempt_at) where status = 'PENDING';

alter table ticket_attachment enable row level security;
alter table ticket_attachment force row level security;
create policy por_organizacion on ticket_attachment
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

alter table email_outbox enable row level security;
alter table email_outbox force row level security;
create policy por_organizacion on email_outbox
    using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

grant select, insert, update, delete on ticket_attachment, email_outbox to opspilot_app;
-- Los adjuntos quedan como evidencia: la app no los edita.
revoke update on ticket_attachment from opspilot_app;
