-- The model a cut is for.
--
-- Nearly every cut is a new model, so the model number is entered on the cut
-- itself and the model is created from it. Recording that link here means the
-- size breakdown already knows which model it is for, instead of asking again.
--
-- It stays nullable, and a cut can still feed further models through the size
-- breakdown — this names the main one, it does not restrict the cut to it.

alter table cut
    add column primary_model_id bigint references model (id);

create index idx_cut_primary_model on cut (primary_model_id);

comment on column cut.primary_model_id is
    'The model this cut was opened for. Further models can still be added via the size breakdown.';
