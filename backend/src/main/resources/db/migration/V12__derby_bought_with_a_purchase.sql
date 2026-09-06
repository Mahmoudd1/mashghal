-- Where a derby came from.
--
-- Derby is normally bought together with the fabric it belongs to: same day,
-- same supplier, same price. It is still its own batch, because only DERBY cuts
-- may draw on it, but it is no longer anonymous — it now names the fabric
-- purchase it arrived with.
--
-- Nullable on purpose: a derby bought separately, from another supplier,
-- belongs to the fabric *type* and to no particular purchase of it.
alter table fabric_intake
    add column parent_intake_id bigint references fabric_intake (id);

create index idx_fabric_intake_parent on fabric_intake (parent_intake_id);

-- One derby per fabric purchase. A second one would be a separate purchase of
-- its own, not another child of the same batch.
create unique index uq_fabric_intake_one_derby_per_parent
    on fabric_intake (parent_intake_id)
    where parent_intake_id is not null;

-- Only a derby batch may name a parent, and it may never name itself.
alter table fabric_intake
    add constraint fabric_intake_parent_is_derby
        check (parent_intake_id is null or derby_id is not null);

comment on column fabric_intake.parent_intake_id is
    'The fabric purchase this derby was bought with. Null when bought separately.';
