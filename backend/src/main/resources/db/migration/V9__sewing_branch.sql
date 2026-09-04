-- Where a model gets sewn.
--
-- Most models are sewn entirely at one branch, so the model carries that branch
-- and every size inherits it. When a model is split, the split is expressed by
-- assigning individual sizes to branches — "12 and 14 at Agamy, 16 at Smouha" —
-- rather than by typing piece quantities.
--
-- That makes cut_model_allocation fully derived: the pieces for a branch are the
-- layers multiplied by the pieces-per-layer of whichever sizes that branch sews.
-- Nobody types a branch quantity any more, so it cannot disagree with the marker.

alter table model
    add column sewing_branch_id bigint references branch (id);

comment on column model.sewing_branch_id is
    'Default branch this model is sewn at. Sizes inherit it unless individually reassigned.';

alter table cut_model_size
    add column branch_id bigint references branch (id);

comment on column cut_model_size.branch_id is
    'Branch sewing this particular size. Null means the model''s own sewing branch — '
    'the presence of overrides is what makes a model split across branches.';

create index idx_cut_model_size_branch on cut_model_size (branch_id);
