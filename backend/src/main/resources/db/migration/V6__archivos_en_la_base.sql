-- Contenido de los adjuntos cuando se guardan en la base (despliegues sin disco persistente, como
-- Vercel). La clave empieza por el id de la organización, así que Row Level Security también aplica.
create table stored_file (
    key          text primary key,
    content      bytea       not null,
    content_type text        not null,
    created_at   timestamptz not null default now()
);

alter table stored_file enable row level security;
alter table stored_file force row level security;
create policy por_organizacion on stored_file
    using (split_part(key, '/', 1) = current_setting('app.organization_id', true));

grant select, insert, delete on stored_file to opspilot_app;
