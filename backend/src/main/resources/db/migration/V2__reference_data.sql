-- Reference data that the application's rules depend on. This is structural,
-- not demo data: branches and stages are seeded here, while the browsable demo
-- dataset ships separately (phase 7).

insert into branch (code, name_ar, name_en, sort_order, created_by, updated_by)
values ('AGAMY',  'العجمي', 'Agamy',  10, 'flyway', 'flyway'),
       ('SMOUHA', 'سموحة',  'Smouha', 20, 'flyway', 'flyway');

insert into pipeline_stage (code, name_ar, name_en, sequence_no, terminal, created_by, updated_by)
values ('CUTTING',  'قص',     'Cutting',  100, false, 'flyway', 'flyway'),
       ('SEWING',   'خياطة',  'Sewing',   200, false, 'flyway', 'flyway'),
       ('RECEIVED', 'مستلم',  'Received', 300, false, 'flyway', 'flyway'),
       ('SOLD',     'مباع',   'Sold',     400, true,  'flyway', 'flyway');
