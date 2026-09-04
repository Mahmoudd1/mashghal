-- Fabric master data and stock.
--
-- Stock is tracked per INTAKE BATCH, not per individual roll. A purchase is
-- aggregate — "cotton, 26/08, 200 rolls, 2000 kg, 44.50/kg" — and the colour
-- breakdown is a soft count added later that need not sum to the total. Per-roll
-- rows would have to invent a weight per roll that the purchase never stated.
--
-- Each fabric type has two independent stock pools: its regular rolls, and an
-- optional derby. Which pool an intake belongs to is decided by derby_id.

create table fabric_type (
    id         bigserial primary key,
    name_ar    varchar(128) not null,
    name_en    varchar(128),
    unit       varchar(16)  not null,
    active     boolean      not null default true,
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    created_by varchar(64),
    updated_at timestamptz  not null default now(),
    updated_by varchar(64),
    constraint fabric_type_name_ar_key unique (name_ar),
    constraint fabric_type_unit_check check (unit in ('KG', 'LENGTH')),
    -- Target for the composite foreign keys that keep colours, derbies and
    -- intakes all pinned to the same fabric type.
    constraint fabric_type_id_key unique (id)
);

create table fabric_color (
    id             bigserial primary key,
    fabric_type_id bigint       not null references fabric_type (id),
    name_ar        varchar(128) not null,
    name_en        varchar(128),
    active         boolean      not null default true,
    version        bigint       not null default 0,
    created_at     timestamptz  not null default now(),
    created_by     varchar(64),
    updated_at     timestamptz  not null default now(),
    updated_by     varchar(64),
    constraint fabric_color_name_per_type_key unique (fabric_type_id, name_ar),
    constraint fabric_color_id_type_key unique (id, fabric_type_id)
);

create index idx_fabric_color_type on fabric_color (fabric_type_id);

-- At most one derby per fabric type, ever. Topped up by further intakes rather
-- than by creating a second derby.
create table derby (
    id             bigserial primary key,
    fabric_type_id bigint      not null references fabric_type (id),
    note           varchar(512),
    version        bigint      not null default 0,
    created_at     timestamptz not null default now(),
    created_by     varchar(64),
    updated_at     timestamptz not null default now(),
    updated_by     varchar(64),
    constraint derby_one_per_fabric_type unique (fabric_type_id),
    constraint derby_id_type_key unique (id, fabric_type_id)
);

comment on table derby is 'Optional second stock pool for a fabric type. Only DERBY cuts may draw from it.';

-- One purchase, on one date, at one price.
create table fabric_intake (
    id                bigserial primary key,
    fabric_type_id    bigint         not null references fabric_type (id),
    -- Null means the regular pool; set means this batch tops up the derby.
    derby_id          bigint,
    intake_date       date           not null,
    total_rolls       integer        not null,
    total_quantity    numeric(14, 3) not null,
    price_per_unit    numeric(12, 3) not null,
    consumed_rolls    integer        not null default 0,
    consumed_quantity numeric(14, 3) not null default 0,
    note              varchar(512),
    version           bigint         not null default 0,
    created_at        timestamptz    not null default now(),
    created_by        varchar(64),
    updated_at        timestamptz    not null default now(),
    updated_by        varchar(64),
    constraint fabric_intake_rolls_positive check (total_rolls > 0),
    constraint fabric_intake_quantity_positive check (total_quantity > 0),
    constraint fabric_intake_price_non_negative check (price_per_unit >= 0),
    constraint fabric_intake_consumed_rolls_range
        check (consumed_rolls >= 0 and consumed_rolls <= total_rolls),
    constraint fabric_intake_consumed_quantity_range
        check (consumed_quantity >= 0 and consumed_quantity <= total_quantity),
    -- A derby batch can only belong to that same fabric type's derby.
    constraint fabric_intake_derby_matches_type
        foreign key (derby_id, fabric_type_id) references derby (id, fabric_type_id)
);

create index idx_fabric_intake_type_date on fabric_intake (fabric_type_id, intake_date desc);
create index idx_fabric_intake_derby on fabric_intake (derby_id);
create index idx_fabric_intake_remaining on fabric_intake (fabric_type_id)
    where consumed_rolls < total_rolls;

comment on column fabric_intake.price_per_unit is
    'Per kg or per metre, matching the fabric type unit. Recorded per batch because the same fabric costs differently on different dates.';
comment on column fabric_intake.derby_id is 'Null for regular stock; set when this batch tops up the fabric type''s derby.';

-- Soft colour breakdown of a batch, added after the fact.
-- Deliberately NOT constrained to sum to total_rolls: the user assigns colours as
-- they learn them, and a partial breakdown must still save. The service reports
-- the shortfall as a warning.
create table fabric_intake_color (
    id               bigserial primary key,
    fabric_intake_id bigint         not null references fabric_intake (id) on delete cascade,
    fabric_color_id  bigint         not null references fabric_color (id),
    roll_count       integer        not null,
    quantity         numeric(14, 3),
    version          bigint         not null default 0,
    created_at       timestamptz    not null default now(),
    created_by       varchar(64),
    updated_at       timestamptz    not null default now(),
    updated_by       varchar(64),
    constraint fabric_intake_color_rolls_positive check (roll_count > 0),
    constraint fabric_intake_color_quantity_positive check (quantity is null or quantity > 0),
    constraint fabric_intake_color_key unique (fabric_intake_id, fabric_color_id)
);

create index idx_fabric_intake_color_intake on fabric_intake_color (fabric_intake_id);

comment on column fabric_intake_color.quantity is 'Optional weight/length for this colour; the breakdown is valid without it.';

-- An individual roll, materialised when it first reaches the cutting table.
--
-- Intake is aggregate ("200 rolls, 2000 kg") and states no per-roll weight, so a
-- roll record is only created when someone actually weighs one to cut it. That
-- keeps intake honest while still giving a partly-used roll a lasting identity
-- that a later cut can pick up again.
--
-- Two counters move independently on the parent batch:
--   * consumed_quantity grows every time weight is taken off this roll;
--   * consumed_rolls grows exactly once, when the roll is finally closed.
create table fabric_roll (
    id               bigserial primary key,
    fabric_intake_id bigint         not null references fabric_intake (id),
    fabric_color_id  bigint references fabric_color (id),
    label            varchar(64),
    initial_weight   numeric(14, 3) not null,
    remaining_weight numeric(14, 3) not null,
    closed           boolean        not null default false,
    version          bigint         not null default 0,
    created_at       timestamptz    not null default now(),
    created_by       varchar(64),
    updated_at       timestamptz    not null default now(),
    updated_by       varchar(64),
    constraint fabric_roll_initial_positive check (initial_weight > 0),
    constraint fabric_roll_remaining_range
        check (remaining_weight >= 0 and remaining_weight <= initial_weight),
    -- A closed roll holds nothing; an open roll still has something on it.
    constraint fabric_roll_closed_is_empty
        check ((closed and remaining_weight = 0) or (not closed and remaining_weight > 0))
);

create index idx_fabric_roll_intake on fabric_roll (fabric_intake_id);
create index idx_fabric_roll_open on fabric_roll (fabric_intake_id, fabric_color_id) where not closed;

comment on column fabric_roll.initial_weight is 'Weighed at the cutting table on first use, not at intake.';
comment on column fabric_roll.closed is 'Set once a cut marks the roll done. This is what decrements the batch roll count.';
