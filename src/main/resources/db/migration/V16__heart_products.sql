alter table heart_product
    add column display_discount_rate int not null default 100;

alter table heart_product
    add column sort_order int not null default 0;

alter table heart_product
    add column recommended boolean not null default false;

insert into heart_product (
    name,
    heart_amount,
    price,
    status,
    display_discount_rate,
    sort_order,
    recommended,
    created_at,
    updated_at
) values
    ('하트 1개', 1, 3000, 'ACTIVE', 100, 1, false, current_timestamp(6), current_timestamp(6)),
    ('하트 2개', 2, 5700, 'ACTIVE', 95, 2, false, current_timestamp(6), current_timestamp(6)),
    ('하트 3개', 3, 8100, 'ACTIVE', 90, 3, false, current_timestamp(6), current_timestamp(6)),
    ('하트 4개', 4, 10200, 'ACTIVE', 85, 4, false, current_timestamp(6), current_timestamp(6)),
    ('하트 5개', 5, 12000, 'ACTIVE', 80, 5, true, current_timestamp(6), current_timestamp(6)),
    ('하트 10개', 10, 24000, 'ACTIVE', 80, 6, false, current_timestamp(6), current_timestamp(6)),
    ('하트 20개', 20, 45000, 'ACTIVE', 75, 7, false, current_timestamp(6), current_timestamp(6)),
    ('하트 50개', 50, 105000, 'ACTIVE', 50, 8, false, current_timestamp(6), current_timestamp(6));
