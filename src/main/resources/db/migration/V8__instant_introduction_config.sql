create table instant_introduction_config (
    id bigint not null auto_increment,
    first_usage_cost int not null,
    mid_tier_start_count int not null,
    mid_tier_end_count int not null,
    mid_tier_cost int not null,
    high_tier_start_count int not null,
    high_tier_cost int not null,
    usage_window_hours int not null,
    updated_by_admin_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

insert into instant_introduction_config (
    id,
    first_usage_cost,
    mid_tier_start_count,
    mid_tier_end_count,
    mid_tier_cost,
    high_tier_start_count,
    high_tier_cost,
    usage_window_hours,
    updated_by_admin_id,
    created_at,
    updated_at
) values (
    1,
    1,
    2,
    5,
    3,
    6,
    5,
    24,
    null,
    current_timestamp(6),
    current_timestamp(6)
);
