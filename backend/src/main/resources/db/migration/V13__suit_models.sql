-- A suit: one model number sold as a set, cut as two garments.
--
-- Model 200 is the suit. "200 top" and "200 bottom" are its sub-models, each an
-- ordinary model with its own cuts, marker, pipeline and sales — because a top
-- or a bottom can be sold on its own, and a piece has to be sellable stock in
-- its own right for that to be possible.
--
-- The suit itself holds no pieces. How many suits are ready is read off its two
-- sub-models: pair them up, and whatever is left over is a loose top or bottom.
-- Nothing is stored twice, so nothing can disagree.
alter table model
    add column parent_model_id bigint references model (id),
    add column model_role varchar(16);

-- A sub-model is exactly the pair: a parent and the half it plays.
alter table model
    add constraint model_role_needs_parent
        check ((parent_model_id is null and model_role is null)
            or (parent_model_id is not null and model_role is not null));

alter table model
    add constraint model_role_known
        check (model_role is null or model_role in ('TOP', 'BOTTOM'));

-- A model cannot be its own half.
alter table model
    add constraint model_parent_not_self
        check (parent_model_id is null or parent_model_id <> id);

-- One top and one bottom per suit. A second top would make "how many suits"
-- ambiguous, which is the one question this structure exists to answer.
create unique index uq_model_one_role_per_parent
    on model (parent_model_id, model_role)
    where parent_model_id is not null;

create index idx_model_parent on model (parent_model_id);

comment on column model.parent_model_id is
    'The suit this model is half of. Null for an ordinary model and for the suit itself.';
comment on column model.model_role is
    'Which half of the suit: TOP or BOTTOM. Null unless parent_model_id is set.';
