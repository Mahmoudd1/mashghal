-- Baseline schema: cross-cutting tables that every later module builds on.
-- Domain tables (fabric, model, cut, pipeline counts) arrive in V3+.

-- Reusable audit columns are repeated per table rather than inherited, so each
-- table stays independently readable in psql and in migrations.

create table branch (
    id          bigserial primary key,
    code        varchar(32)  not null unique,
    name_ar     varchar(128) not null,
    name_en     varchar(128),
    sort_order  integer      not null default 0,
    active      boolean      not null default true,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    created_by  varchar(64),
    updated_at  timestamptz  not null default now(),
    updated_by  varchar(64)
);

comment on table branch is 'Manufacturing sites. Lookup table so more branches can be added without a schema change.';

create table pipeline_stage (
    id           bigserial primary key,
    code         varchar(32)  not null unique,
    name_ar      varchar(128) not null,
    name_en      varchar(128),
    sequence_no  integer      not null unique,
    terminal     boolean      not null default false,
    active       boolean      not null default true,
    version      bigint       not null default 0,
    created_at   timestamptz  not null default now(),
    created_by   varchar(64),
    updated_at   timestamptz  not null default now(),
    updated_by   varchar(64)
);

comment on column pipeline_stage.sequence_no is 'Pipeline order. Seeded with gaps (100/200/300/400) so stages can be inserted between existing ones.';

create table app_user (
    id            bigserial primary key,
    username      varchar(64)  not null unique,
    password_hash varchar(255) not null,
    display_name  varchar(128) not null,
    role          varchar(32)  not null,
    enabled       boolean      not null default true,
    version       bigint       not null default 0,
    created_at    timestamptz  not null default now(),
    created_by    varchar(64),
    updated_at    timestamptz  not null default now(),
    updated_by    varchar(64),
    constraint app_user_role_check check (role in ('ADMIN', 'DATA_ENTRY'))
);

-- Append-only trail for stage transitions, roll allocations and flag actions.
-- Generic on purpose: entity_type + entity_id + details keeps one table usable
-- by every module instead of one audit table per feature.
create table audit_event (
    id          bigserial primary key,
    occurred_at timestamptz  not null default now(),
    username    varchar(64)  not null,
    action      varchar(64)  not null,
    entity_type varchar(64)  not null,
    entity_id   bigint,
    branch_id   bigint references branch (id),
    quantity    numeric(12, 3),
    note        varchar(512),
    details     jsonb
);

create index idx_audit_event_occurred_at on audit_event (occurred_at desc);
create index idx_audit_event_entity on audit_event (entity_type, entity_id);
create index idx_audit_event_branch on audit_event (branch_id);
