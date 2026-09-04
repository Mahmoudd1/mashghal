-- Per-model, per-branch piece counts and the movements that change them.
--
-- The invariant this schema exists to protect:
--     for every model + branch,
--     sum(model_branch_stage_count.piece_count) == sum(cut_model_allocation.quantity_allocated)
--
-- Counts are never edited directly. They change only through movements, each of
-- which conserves the total, so the invariant holds by construction rather than
-- by periodic repair.

create table model_branch_stage_count (
    id            bigserial primary key,
    model_id      bigint      not null references model (id),
    branch_id     bigint      not null references branch (id),
    stage_id      bigint      not null references pipeline_stage (id),
    piece_count   integer     not null default 0,
    flagged_count integer     not null default 0,
    version       bigint      not null default 0,
    created_at    timestamptz not null default now(),
    created_by    varchar(64),
    updated_at    timestamptz not null default now(),
    updated_by    varchar(64),
    constraint model_branch_stage_count_key unique (model_id, branch_id, stage_id),
    constraint mbsc_piece_count_non_negative check (piece_count >= 0),
    -- Flagged pieces are a tag on pieces inside a stage, never a stage of their own.
    constraint mbsc_flagged_within_count check (flagged_count >= 0 and flagged_count <= piece_count)
);

create index idx_mbsc_model on model_branch_stage_count (model_id, branch_id);
create index idx_mbsc_branch_stage on model_branch_stage_count (branch_id, stage_id);

comment on column model_branch_stage_count.flagged_count is
    'Defective pieces among piece_count. Only set at RECEIVED or later — defects are found at receiving inspection.';

-- Append-only history of every piece movement, including receiving actions.
-- Carries the business date the move happened on, which is not the same as the
-- timestamp the row was written.
create table stage_movement (
    id            bigserial primary key,
    model_id      bigint      not null references model (id),
    branch_id     bigint      not null references branch (id),
    from_stage_id bigint references pipeline_stage (id),
    to_stage_id   bigint references pipeline_stage (id),
    quantity      integer     not null,
    movement_date date        not null,
    reason        varchar(64) not null,
    note          varchar(512),
    created_at    timestamptz not null default now(),
    created_by    varchar(64) not null,
    constraint stage_movement_quantity_positive check (quantity > 0),
    -- A null from_stage seeds pieces into the pipeline (a new allocation); a null
    -- to_stage removes them (an allocation reduced). Both null is meaningless.
    constraint stage_movement_endpoints check (from_stage_id is not null or to_stage_id is not null)
);

create index idx_stage_movement_model on stage_movement (model_id, branch_id, movement_date desc);
create index idx_stage_movement_date on stage_movement (movement_date desc);

comment on column stage_movement.reason is
    'ALLOCATION_ADDED, ALLOCATION_REDUCED, STAGE_MOVE, RECEIVING, SALE.';

-- Append-only history of defect flagging, kept separate from movements because
-- flagging changes a tag inside a stage rather than moving pieces between stages.
create table piece_flag_event (
    id         bigserial primary key,
    model_id   bigint      not null references model (id),
    branch_id  bigint      not null references branch (id),
    stage_id   bigint      not null references pipeline_stage (id),
    action     varchar(16) not null,
    quantity   integer     not null,
    reason     varchar(512),
    event_date date        not null,
    created_at timestamptz not null default now(),
    created_by varchar(64) not null,
    constraint piece_flag_event_quantity_positive check (quantity > 0),
    constraint piece_flag_event_action_check check (action in ('FLAG', 'UNFLAG'))
);

create index idx_piece_flag_event_model on piece_flag_event (model_id, branch_id, event_date desc);
