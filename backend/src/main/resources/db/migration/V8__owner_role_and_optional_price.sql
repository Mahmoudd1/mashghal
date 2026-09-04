-- Two related changes.
--
-- 1. Price becomes optional. Whoever records a purchase on the floor often does
--    not know what was paid; the owner fills it in afterwards. A batch with no
--    price is a normal, complete record — not a draft.
--
-- 2. OWNER joins the roles, above ADMIN. Price and everything derived from it is
--    owner-only, so a manager can run master data without seeing what the
--    business pays. Existing administrators are promoted, because today's admin
--    account is the owner's own.

alter table fabric_intake
    alter column price_per_unit drop not null;

comment on column fabric_intake.price_per_unit is
    'Unit price, in the fabric type''s unit. Null until the owner records it. Visible to OWNER only.';

alter table app_user
    drop constraint app_user_role_check;

alter table app_user
    add constraint app_user_role_check check (role in ('OWNER', 'ADMIN', 'DATA_ENTRY'));

update app_user
set role = 'OWNER'
where role = 'ADMIN';
