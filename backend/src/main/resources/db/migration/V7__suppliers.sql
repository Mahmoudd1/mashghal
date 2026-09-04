-- Who fabric was bought from.
--
-- A supplier is attached to the intake batch, not to the fabric type: the same
-- fabric is bought from different suppliers at different times and prices, so
-- "which suppliers provide cotton" is answered by the purchase history rather
-- than by a fixed link. That also makes average-price-per-supplier fall out of
-- the data already being recorded.

create table supplier (
    id         bigserial primary key,
    name_ar    varchar(128) not null,
    name_en    varchar(128),
    phone      varchar(64),
    note       varchar(512),
    active     boolean      not null default true,
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    created_by varchar(64),
    updated_at timestamptz  not null default now(),
    updated_by varchar(64),
    constraint supplier_name_ar_key unique (name_ar)
);

-- Nullable on purpose: a batch recorded before the paperwork catches up must
-- still save, the same way a colour breakdown can be filled in later. Reports
-- group the unattributed batches rather than hiding them.
alter table fabric_intake
    add column supplier_id bigint references supplier (id);

create index idx_fabric_intake_supplier on fabric_intake (supplier_id);

comment on column fabric_intake.supplier_id is
    'Who this batch was bought from. Null until known; never blocks recording a purchase.';
