-- The pipeline learns about sizes.
--
-- Size 6 can be sewn while 8 and 10 are still on the cutting table, and until
-- now there was nowhere to write that down: a stage count was one number per
-- model, per branch, per stage. Sizes existed only on the cut marker, which is
-- the plan, not the state.
--
-- Nullable, not required. A count with no size is one recorded before sizes
-- were tracked, and a movement with no size is history that predates them.
-- Refusing to represent that would mean deleting it.
alter table model_branch_stage_count
    add column garment_size_id bigint references garment_size (id);

alter table stage_movement
    add column garment_size_id bigint references garment_size (id);

alter table piece_flag_event
    add column garment_size_id bigint references garment_size (id);

-- The old key is one row per model, branch and stage — which is exactly what
-- the split below stops being true. It goes first, or the split collides with it.
alter table model_branch_stage_count
    drop constraint model_branch_stage_count_key;

-- Existing counts are split across the sizes their model's marker calls for,
-- in proportion to the pieces each size yields per layer. The remainder goes to
-- the largest size, so a split always adds back up to what it started from.
--
-- This is a redistribution, not a measurement: it says what the marker implies,
-- not what was counted on the floor. It is right for this project because the
-- rows it touches are seeded demo data. Anything whose model has no marker keeps
-- a null size and is left alone.
create temporary table sized_counts on commit drop as
with weights as (
    select cms.model_id, cms.garment_size_id, sum(cms.pieces_per_layer)::numeric as weight
    from cut_model_size cms
    group by cms.model_id, cms.garment_size_id
),
totals as (
    select model_id, sum(weight) as total_weight
    from weights
    group by model_id
),
base as (
    select c.id as source_id,
           c.model_id, c.branch_id, c.stage_id,
           w.garment_size_id,
           c.piece_count, c.flagged_count,
           floor(c.piece_count * w.weight / t.total_weight)::int as pieces,
           floor(c.flagged_count * w.weight / t.total_weight)::int as flagged,
           row_number() over (partition by c.id order by w.weight desc, w.garment_size_id) as rn
    from model_branch_stage_count c
      join weights w on w.model_id = c.model_id
      join totals t on t.model_id = c.model_id
    where c.garment_size_id is null
)
select source_id, model_id, branch_id, stage_id, garment_size_id,
       pieces + case when rn = 1
                     then piece_count - sum(pieces) over (partition by source_id)
                     else 0 end as piece_count,
       flagged + case when rn = 1
                      then flagged_count - sum(flagged) over (partition by source_id)
                      else 0 end as flagged_count
from base;

delete from model_branch_stage_count
where id in (select distinct source_id from sized_counts);

insert into model_branch_stage_count (model_id, branch_id, stage_id, garment_size_id, piece_count, flagged_count)
select model_id, branch_id, stage_id, garment_size_id, piece_count, flagged_count
from sized_counts
where piece_count > 0 or flagged_count > 0;

-- One row per model, branch, stage and size. NULLS NOT DISTINCT so the unsized
-- remainder is a single bucket rather than a row per insert.
alter table model_branch_stage_count
    add constraint model_branch_stage_count_key
        unique nulls not distinct (model_id, branch_id, stage_id, garment_size_id);

create index idx_mbsc_size on model_branch_stage_count (garment_size_id);
create index idx_stage_movement_size on stage_movement (garment_size_id);

comment on column model_branch_stage_count.garment_size_id is
    'Which size these pieces are. Null for counts recorded before sizes were tracked.';

-- The allocation is derived from the marker, and the marker is per size. Keeping
-- the allocation per size too is what lets a change to one size be pushed to the
-- pipeline as that size's delta: without it, "size 6 went from 100 to 120" is
-- indistinguishable from "size 10 did", and the pipeline cannot be told which
-- row to move.
alter table cut_model_allocation
    add column garment_size_id bigint references garment_size (id);

alter table cut_model_allocation
    drop constraint if exists cut_model_allocation_key;

-- Split existing allocations the same way, and for the same reason: they are
-- derived figures, so the marker is their authority.
create temporary table sized_allocations on commit drop as
with weights as (
    select cms.cut_id, cms.model_id, cms.garment_size_id, sum(cms.pieces_per_layer)::numeric as weight
    from cut_model_size cms
    group by cms.cut_id, cms.model_id, cms.garment_size_id
),
totals as (
    select cut_id, model_id, sum(weight) as total_weight
    from weights
    group by cut_id, model_id
),
base as (
    select a.id as source_id, a.cut_id, a.model_id, a.branch_id, a.note,
           w.garment_size_id, a.quantity_allocated,
           floor(a.quantity_allocated * w.weight / t.total_weight)::int as quantity,
           row_number() over (partition by a.id order by w.weight desc, w.garment_size_id) as rn
    from cut_model_allocation a
      join weights w on w.cut_id = a.cut_id and w.model_id = a.model_id
      join totals t on t.cut_id = a.cut_id and t.model_id = a.model_id
    where a.garment_size_id is null
)
select source_id, cut_id, model_id, branch_id, garment_size_id, note,
       quantity + case when rn = 1
                       then quantity_allocated - sum(quantity) over (partition by source_id)
                       else 0 end as quantity_allocated
from base;

delete from cut_model_allocation
where id in (select distinct source_id from sized_allocations);

insert into cut_model_allocation (cut_id, model_id, branch_id, garment_size_id, quantity_allocated, note)
select cut_id, model_id, branch_id, garment_size_id, quantity_allocated, note
from sized_allocations
where quantity_allocated > 0;

alter table cut_model_allocation
    add constraint cut_model_allocation_key
        unique nulls not distinct (cut_id, model_id, branch_id, garment_size_id);

comment on column cut_model_allocation.garment_size_id is
    'Which size these pieces are. Null on an allocation entered by hand, with no marker behind it.';
