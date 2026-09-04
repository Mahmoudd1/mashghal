-- Models, cutting runs, and the many-to-many allocation between them.
--
-- A cut is a physical cutting run that can be split across several models, and
-- a model can (rarely) draw from more than one main cut. cut_model_allocation
-- is therefore the source of truth for planned quantity — there is deliberately
-- no planned_quantity column on model and no model_id on cut.
--
-- Flagged pieces are tracked against the model's pipeline, never against a cut:
-- one cutting run feeds several models, so a cut-level defect count could not be
-- attributed to any single model's piece counts.

create table model (
    id           bigserial primary key,
    model_number varchar(64)  not null,
    name_ar      varchar(128) not null,
    name_en      varchar(128),
    note         varchar(512),
    active       boolean      not null default true,
    version      bigint       not null default 0,
    created_at   timestamptz  not null default now(),
    created_by   varchar(64),
    updated_at   timestamptz  not null default now(),
    updated_by   varchar(64),
    constraint model_number_key unique (model_number)
);

create table cut (
    id                 bigserial primary key,
    cut_number         varchar(64) not null,
    cut_type           varchar(16) not null,
    parent_main_cut_id bigint,
    -- Mirrors the parent's cut_type so the database itself can require that a
    -- secondary or derby cut hangs off a MAIN cut and nothing else.
    parent_cut_type    varchar(16),
    branch_id          bigint      not null references branch (id),
    status             varchar(16) not null default 'OPEN',
    cut_date           date        not null,
    -- One cutting run lays out one fabric type; every roll on it must match.
    fabric_type_id     bigint references fabric_type (id),
    -- Length of fabric laid out per layer; a recorded measurement, not derived.
    cut_length         numeric(12, 3),
    model_description  varchar(512),
    label_ar           varchar(128),
    label_en           varchar(128),
    note               varchar(512),
    version            bigint      not null default 0,
    created_at         timestamptz not null default now(),
    created_by         varchar(64),
    updated_at         timestamptz not null default now(),
    updated_by         varchar(64),
    constraint cut_number_key unique (cut_number),
    constraint cut_type_check check (cut_type in ('MAIN', 'SECONDARY', 'DERBY')),
    constraint cut_status_check check (status in ('OPEN', 'CLOSED')),
    constraint cut_length_positive check (cut_length is null or cut_length > 0),
    -- A MAIN cut stands alone; SECONDARY and DERBY must name their MAIN cut.
    constraint cut_parent_required check (
        (cut_type = 'MAIN' and parent_main_cut_id is null)
        or (cut_type in ('SECONDARY', 'DERBY') and parent_main_cut_id is not null)
    ),
    constraint cut_parent_type_paired check (
        (parent_main_cut_id is null and parent_cut_type is null)
        or (parent_main_cut_id is not null and parent_cut_type = 'MAIN')
    ),
    -- Target for the composite foreign key below.
    constraint cut_id_type_key unique (id, cut_type),
    constraint cut_parent_is_main
        foreign key (parent_main_cut_id, parent_cut_type) references cut (id, cut_type)
);

create index idx_cut_parent on cut (parent_main_cut_id);
create index idx_cut_branch on cut (branch_id);
create index idx_cut_type_status on cut (cut_type, status);

comment on column cut.branch_id is 'Where the cutting run physically happened; pieces may be allocated to a different branch for sewing.';
comment on column cut.status is 'A cut accepts allocations only while OPEN.';

-- "This cut contributed N pieces to this model, for production at this branch."
create table cut_model_allocation (
    id                 bigserial primary key,
    cut_id             bigint      not null references cut (id) on delete cascade,
    model_id           bigint      not null references model (id),
    branch_id          bigint      not null references branch (id),
    quantity_allocated integer     not null,
    note               varchar(512),
    version            bigint      not null default 0,
    created_at         timestamptz not null default now(),
    created_by         varchar(64),
    updated_at         timestamptz not null default now(),
    updated_by         varchar(64),
    constraint cut_model_allocation_quantity_positive check (quantity_allocated > 0),
    -- One row per cut + model + branch; a repeat allocation adjusts the existing row.
    constraint cut_model_allocation_key unique (cut_id, model_id, branch_id)
);

create index idx_cut_model_allocation_model on cut_model_allocation (model_id, branch_id);
create index idx_cut_model_allocation_cut on cut_model_allocation (cut_id);

-- One roll's use by one cutting run.
--
-- This is where the roll consumption lifecycle lives. `done` says whether the cut
-- finished the roll:
--   * done  -> the roll closes, and the batch roll count drops by one
--   * !done -> the roll stays open with remaining_after on it, and only the
--              weight moves; the roll count is untouched
-- weight_consumed is always weight_at_start - remaining_after, so the two
-- dimensions can never drift apart.
create table cut_roll (
    id              bigserial primary key,
    cut_id          bigint         not null references cut (id) on delete cascade,
    fabric_roll_id  bigint         not null references fabric_roll (id),
    layers          integer        not null,
    -- Unusable leftover from cutting this roll on this run.
    defect_weight   numeric(14, 3) not null default 0,
    weight_at_start numeric(14, 3) not null,
    remaining_after numeric(14, 3) not null default 0,
    weight_consumed numeric(14, 3) not null,
    done            boolean        not null default true,
    note            varchar(512),
    version         bigint         not null default 0,
    created_at      timestamptz    not null default now(),
    created_by      varchar(64),
    updated_at      timestamptz    not null default now(),
    updated_by      varchar(64),
    constraint cut_roll_layers_positive check (layers > 0),
    constraint cut_roll_defect_non_negative check (defect_weight >= 0),
    constraint cut_roll_weights_non_negative
        check (weight_at_start > 0 and remaining_after >= 0 and weight_consumed > 0),
    constraint cut_roll_weight_balances check (weight_consumed = weight_at_start - remaining_after),
    -- Finishing a roll leaves nothing on it.
    constraint cut_roll_done_leaves_nothing check (not done or remaining_after = 0),
    constraint cut_roll_key unique (cut_id, fabric_roll_id)
);

create index idx_cut_roll_cut on cut_roll (cut_id);
create index idx_cut_roll_roll on cut_roll (fabric_roll_id);
