-- Sizes and the categories they group into.
--
-- A lookup table rather than an enum so an admin can edit it, though changes are
-- expected to be rare. Categories exist so reporting can ask "which models are in
-- progress in the اولادي range" without naming every size.

create table size_category (
    id         bigserial primary key,
    code       varchar(32)  not null,
    name_ar    varchar(128) not null,
    name_en    varchar(128),
    note       varchar(512),
    sort_order integer      not null default 0,
    active     boolean      not null default true,
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    created_by varchar(64),
    updated_at timestamptz  not null default now(),
    updated_by varchar(64),
    constraint size_category_code_key unique (code),
    constraint size_category_id_key unique (id)
);

create table garment_size (
    id               bigserial primary key,
    size_category_id bigint      not null references size_category (id),
    code             varchar(32) not null,
    name_ar          varchar(64),
    sort_order       integer     not null default 0,
    active           boolean     not null default true,
    version          bigint      not null default 0,
    created_at       timestamptz not null default now(),
    created_by       varchar(64),
    updated_at       timestamptz not null default now(),
    updated_by       varchar(64),
    constraint garment_size_code_key unique (code)
);

create index idx_garment_size_category on garment_size (size_category_id);

-- The marker: how many pieces of each size one layer yields, for one model on one
-- cut. Total pieces = (layers across the cut's rolls) x pieces_per_layer, so the
-- allocation quantity is derived from this rather than typed in.
--
-- Deliberately keyed on cut + model, not cut + model + branch: this is the
-- physical layout on the table, and one layout serves whichever branches the
-- resulting pieces are later distributed to.
create table cut_model_size (
    id               bigserial primary key,
    cut_id           bigint      not null references cut (id) on delete cascade,
    model_id         bigint      not null references model (id),
    garment_size_id  bigint      not null references garment_size (id),
    pieces_per_layer integer     not null,
    version          bigint      not null default 0,
    created_at       timestamptz not null default now(),
    created_by       varchar(64),
    updated_at       timestamptz not null default now(),
    updated_by       varchar(64),
    constraint cut_model_size_pieces_positive check (pieces_per_layer > 0),
    constraint cut_model_size_key unique (cut_id, model_id, garment_size_id)
);

create index idx_cut_model_size_cut_model on cut_model_size (cut_id, model_id);
create index idx_cut_model_size_size on cut_model_size (garment_size_id);

insert into size_category (code, name_ar, name_en, note, sort_order, created_by, updated_by)
values ('BOYS',     'اولادي',        'Boys',           null,                          10, 'flyway', 'flyway'),
       ('IN_BETWEEN', 'مقاسات المحيره', 'In-between',     'بين الاولادي والرجالي',        20, 'flyway', 'flyway'),
       ('MENS',     'رجالي',         'Mens',           null,                          30, 'flyway', 'flyway'),
       ('SPECIAL',  'مقاسات خاصة',   'Special sizes',  null,                          40, 'flyway', 'flyway');

insert into garment_size (size_category_id, code, sort_order, created_by, updated_by)
select c.id, v.code, v.sort_order, 'flyway', 'flyway'
from (values
        ('BOYS',       '6',      10),
        ('BOYS',       '8',      20),
        ('BOYS',       '10',     30),
        ('IN_BETWEEN', '12',     10),
        ('IN_BETWEEN', '14',     20),
        ('IN_BETWEEN', '16',     30),
        ('MENS',       'L',      10),
        ('MENS',       'XL',     20),
        ('MENS',       'XXL',    30),
        ('SPECIAL',    'XXXL',   10),
        ('SPECIAL',    'XXXXL',  20),
        ('SPECIAL',    'XXXXXL', 30)
     ) as v (category_code, code, sort_order)
join size_category c on c.code = v.category_code;
