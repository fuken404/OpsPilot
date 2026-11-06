create table app_user (
    id           uuid primary key,
    subject      text        not null unique,
    email        text        not null,
    display_name text        not null,
    created_at   timestamptz not null
);
create index app_user_email_idx on app_user (lower(email));

create table organization (
    id         uuid primary key,
    name       text        not null,
    slug       text        not null unique,
    created_at timestamptz not null
);

create table membership (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    user_id         uuid        not null references app_user (id) on delete cascade,
    role            text        not null check (role in ('ADMIN', 'TECHNICIAN', 'CUSTOMER')),
    created_at      timestamptz not null,
    version         bigint      not null default 0,
    unique (organization_id, user_id)
);
create index membership_user_idx on membership (user_id);

create table invitation (
    id              uuid primary key,
    organization_id uuid        not null references organization (id) on delete cascade,
    email           text        not null check (email = lower(email)),
    role            text        not null check (role in ('ADMIN', 'TECHNICIAN', 'CUSTOMER')),
    created_at      timestamptz not null,
    unique (organization_id, email)
);
create index invitation_email_idx on invitation (email);
