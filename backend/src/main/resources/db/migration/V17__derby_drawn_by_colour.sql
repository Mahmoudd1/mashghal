-- Derby is drawn by colour, not from one named batch.
--
-- V16 had a run name the purchase it came off, because derby leaves the shelf by
-- colour and guessing at the oldest batch charges the wrong money. Naming the
-- batch answered that, and asked too much: the person knows the colour and the
-- weight, not which of three purchases the last 12 kg of navy came out of.
--
-- So the walk is the same one regular fabric takes — oldest batch first, spilling
-- into the next as each runs out — down a narrower shelf: only the batches that
-- hold the colour, each giving at most what it holds of it. The colour stays on
-- the cut; the batch it named does not, because there is no longer one batch.

alter table cut drop constraint cut_named_batch_shape;
drop index idx_cut_fabric_intake;
alter table cut drop column fabric_intake_id;

comment on column cut.fabric_color_id is
    'The colour this run was cut in. Its weight is drawn from the batches holding that colour, oldest first.';
