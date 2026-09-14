-- Recording a cut from its totals instead of roll by roll.
--
-- The detailed way builds a cut from one row per physical roll, and every
-- figure on it — layers, weight, waste — is the sum of those rows. That is the
-- honest record when someone is at the table weighing rolls as they go. A cut
-- written up afterwards from a paper sheet has only the totals, and until now
-- there was no way to enter it: a cut with no rolls consumed nothing, so the
-- fabric left the floor and the stock figures never noticed.
--
-- Summary mode stores those totals on the cut itself and draws the fabric from
-- the batches oldest-first. `entry_mode` says which kind of record a cut is, and
-- it is fixed once the cut carries data — the two cannot be mixed without
-- double-counting the same fabric.

alter table cut
    add column entry_mode varchar(16) not null default 'DETAILED',
    -- All null on a DETAILED cut, all present on a SUMMARY one.
    add column total_rolls integer,
    add column reused_rolls integer,
    add column total_weight numeric(14, 3),
    add column waste_weight numeric(14, 3),
    add column total_layers integer;

alter table cut
    add constraint cut_entry_mode_check check (entry_mode in ('DETAILED', 'SUMMARY')),
    -- A summary cut is nothing without its totals; a detailed one must not carry
    -- them, or the same fabric would be counted from both sides.
    add constraint cut_summary_totals_present check (
        (entry_mode = 'DETAILED'
             and total_rolls is null and reused_rolls is null
             and total_weight is null and waste_weight is null and total_layers is null)
        or (entry_mode = 'SUMMARY'
             and total_rolls is not null and reused_rolls is not null
             and total_weight is not null and waste_weight is not null
             and total_layers is not null)
    ),
    add constraint cut_summary_totals_sane check (
        entry_mode = 'DETAILED'
        or (total_rolls > 0
            and reused_rolls >= 0 and reused_rolls <= total_rolls
            and total_weight > 0
            -- The waste is part of the weight taken off the shelf, never on top.
            and waste_weight >= 0 and waste_weight <= total_weight
            and total_layers > 0)
    );

-- Where a summary cut's fabric actually came from.
--
-- The oldest-first allocation is worked out once and written down, never
-- recomputed. Editing or deleting the cut has to give the fabric back to the
-- very batches it was taken from, and by then other cuts have drawn on those
-- batches — re-running the allocation would return it somewhere else and
-- quietly corrupt the stock. This is also the only path from a summary cut back
-- to the fabric it used, which the costing report needs.
create table cut_fabric_draw (
    id               bigserial primary key,
    cut_id           bigint         not null references cut (id) on delete cascade,
    fabric_intake_id bigint         not null references fabric_intake (id),
    -- Fabric that became garments, and fabric binned with the rolls. Their sum
    -- is what left this batch.
    weight_consumed  numeric(14, 3) not null,
    waste_weight     numeric(14, 3) not null default 0,
    roll_count       integer        not null default 0,
    version          bigint         not null default 0,
    created_at       timestamptz    not null default now(),
    created_by       varchar(64),
    updated_at       timestamptz    not null default now(),
    updated_by       varchar(64),
    constraint cut_fabric_draw_key unique (cut_id, fabric_intake_id),
    constraint cut_fabric_draw_amounts check (
        weight_consumed >= 0 and waste_weight >= 0 and roll_count >= 0
        and (weight_consumed + waste_weight) > 0
    )
);

create index idx_cut_fabric_draw_cut on cut_fabric_draw (cut_id);
create index idx_cut_fabric_draw_intake on cut_fabric_draw (fabric_intake_id);

comment on table cut_fabric_draw is
    'What a summary cut took from each batch, oldest first. Written once, replayed to reverse.';
