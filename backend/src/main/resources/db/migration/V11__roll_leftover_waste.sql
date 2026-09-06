-- Fabric left on a roll when the roll is finished.
--
-- Finishing a roll used to book its whole balance as consumed: weight_used was
-- not even asked for, so a roll weighed at 50 kg and cut down to 45 kg recorded
-- 50 kg of consumption and no loss. The 5 kg nobody cut simply vanished into the
-- consumption figure, which is why every batch reported almost no waste.
--
-- The leftover now has its own column on both sides:
--   * cut_roll.waste_weight    -- what this run threw away when it closed the roll
--   * fabric_intake.wasted_quantity -- the batch's running total of the same
--
-- weight_consumed therefore means what it says — fabric that went into the cut —
-- and the batch's remaining quantity stops counting fabric that is already gone.

alter table cut_roll
    add column waste_weight numeric(14, 3) not null default 0;

alter table fabric_intake
    add column wasted_quantity numeric(14, 3) not null default 0;

-- The balance check has to make room for the new column: what came off the roll
-- is now what was cut plus what was thrown away.
alter table cut_roll
    drop constraint cut_roll_weight_balances;

alter table cut_roll
    add constraint cut_roll_weight_balances
        check (weight_consumed + waste_weight = weight_at_start - remaining_after);

alter table cut_roll
    add constraint cut_roll_waste_non_negative check (waste_weight >= 0);

-- Only a finished roll gives up its leftover; an open one keeps it as
-- remaining_after, to be cut another day.
alter table cut_roll
    add constraint cut_roll_waste_only_when_done check (done or waste_weight = 0);

-- Consumed and wasted both come out of the same purchase, so it is their sum
-- that cannot exceed it.
alter table fabric_intake
    drop constraint fabric_intake_consumed_quantity_range;

alter table fabric_intake
    add constraint fabric_intake_consumed_quantity_range
        check (consumed_quantity >= 0
               and wasted_quantity >= 0
               and consumed_quantity + wasted_quantity <= total_quantity);

comment on column cut_roll.waste_weight is
    'Fabric left on the roll when this run finished it: off the batch, but never cut.';

comment on column fabric_intake.wasted_quantity is
    'Running total of roll leftovers thrown away, kept apart from consumed_quantity.';
