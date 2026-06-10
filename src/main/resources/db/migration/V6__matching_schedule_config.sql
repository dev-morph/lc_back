create table matching_schedule_config (
    id bigint not null auto_increment,
    enabled bit not null,
    cron_expression varchar(128) not null,
    timezone varchar(64) not null,
    updated_by_admin_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

insert into matching_schedule_config (
    enabled,
    cron_expression,
    timezone,
    updated_by_admin_id,
    created_at,
    updated_at
)
values (
    true,
    '0 0 9 * * *',
    'Asia/Seoul',
    null,
    now(6),
    now(6)
);
