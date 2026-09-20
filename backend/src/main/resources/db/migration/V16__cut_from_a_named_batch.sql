-- Cutting a named batch, by colour, instead of oldest-batch-first.
--
-- Derby is bought and asked for by colour: "the navy from the 12/07 batch". A
-- run that lays it out takes it off that batch and no other, so which purchase
-- it came from is a fact about the run rather than something to infer — the
-- oldest-first guess would charge the wrong colour and the wrong money. The
-- same choice is offered to a secondary run, which otherwise spends the fabric
-- its main cut already took off the shelf.
--
-- A derby run also has no marker: the ribbing it yields is not counted in
-- pieces, so its whole record is a colour, a batch and a weight. Layers and
-- rolls become optional for that one case.

alter table cut
    add column fabric_intake_id bigint references fabric_intake (id),
    add column fabric_color_id bigint references fabric_color (id);

create index idx_cut_fabric_intake on cut (fabric_intake_id);

comment on column cut.fabric_intake_id is
    'The batch this run was cut from, when it names one instead of drawing oldest-first.';
comment on column cut.fabric_color_id is
    'Which colour of that batch. Null only while the batch has no colour breakdown.';

alter table cut
    add constraint cut_named_batch_shape check (
        fabric_color_id is null or fabric_intake_id is not null
    );

alter table cut drop constraint cut_summary_totals_present;
alter table cut drop constraint cut_summary_totals_sane;

alter table cut
    add constraint cut_summary_totals_present check (
        (entry_mode = 'DETAILED'
             and total_rolls is null and reused_rolls is null
             and total_weight is null and waste_weight is null and total_layers is null)
        or (entry_mode = 'SUMMARY'
             and total_weight is not null and waste_weight is not null
             -- Only a derby run may go without a layout: it yields ribbing, and
             -- ribbing is weighed, not counted.
             and (cut_type = 'DERBY'
                  or (total_rolls is not null and reused_rolls is not null
                      and total_layers is not null)))
    ),
    add constraint cut_summary_totals_sane check (
        entry_mode = 'DETAILED'
        or (total_weight > 0
            -- The waste is part of the weight taken off the shelf, never on top.
            and waste_weight >= 0 and waste_weight <= total_weight
            and (total_rolls is null or total_rolls > 0)
            and (total_layers is null or total_layers > 0)
            and (reused_rolls is null
                 or (total_rolls is not null
                     and reused_rolls >= 0 and reused_rolls <= total_rolls)))
    );
