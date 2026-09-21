-- A run cut in several colours: one line per colour.
--
-- V17 let a derby run name the one colour it was cut in. A run is not always one
-- colour: 5 kg of navy and 3 of black go on the same table for the same model,
-- and each has to come off the batches that hold it. So the colour moves off the
-- cut onto lines of its own — the colour, the weight of it, and the rolls that
-- weight came off — and each line takes the oldest-first walk down its own
-- colour's batches.
--
-- The cut keeps its totals, as the sum of its lines, so everything that reads a
-- summary cut's weight and rolls goes on reading them from the same place.

create table cut_color_line (
    id              bigserial primary key,
    cut_id          bigint         not null references cut (id) on delete cascade,
    fabric_color_id bigint         not null references fabric_color (id),
    weight          numeric(14, 3) not null,
    total_rolls     integer        not null default 0,
    -- Of those rolls, how many an earlier run had already opened: they are off
    -- their batch already, so only the rest count against it here.
    reused_rolls    integer        not null default 0,
    version         bigint         not null default 0,
    created_at      timestamptz    not null default now(),
    created_by      varchar(64),
    updated_at      timestamptz    not null default now(),
    updated_by      varchar(64),
    -- One line per colour: two lines of navy would be one line typed twice.
    constraint cut_color_line_key unique (cut_id, fabric_color_id),
    constraint cut_color_line_amounts check (
        weight > 0 and total_rolls >= 0
        and reused_rolls >= 0 and reused_rolls <= total_rolls
    )
);

create index idx_cut_color_line_cut on cut_color_line (cut_id);

comment on table cut_color_line is
    'One colour of a run cut by colour: its weight and rolls, drawn down that colour''s batches oldest first.';

-- A run that named one colour becomes a run with one line.
insert into cut_color_line (cut_id, fabric_color_id, weight, total_rolls, reused_rolls)
select id, fabric_color_id, total_weight, coalesce(total_rolls, 0), coalesce(reused_rolls, 0)
from cut
where fabric_color_id is not null;

-- A draw now says which colour it was for. Two colours can come off the same
-- batch — one purchase holding navy and black — and each colour's headroom on
-- that batch is counted from its own draws.
alter table cut_fabric_draw
    add column fabric_color_id bigint references fabric_color (id);

update cut_fabric_draw d
set fabric_color_id = c.fabric_color_id
from cut c
where c.id = d.cut_id
  and c.fabric_color_id is not null;

alter table cut_fabric_draw drop constraint cut_fabric_draw_key;
alter table cut_fabric_draw
    add constraint cut_fabric_draw_key unique nulls not distinct (cut_id, fabric_intake_id, fabric_color_id);

create index idx_cut_fabric_draw_color on cut_fabric_draw (fabric_intake_id, fabric_color_id);

alter table cut drop column fabric_color_id;
