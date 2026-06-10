create table app_notification (
    id bigint primary key auto_increment,
    user_id bigint not null,
    notification_type varchar(50) not null,
    title varchar(120) not null,
    body varchar(500) not null,
    icon_type varchar(40) not null,
    target_type varchar(40),
    target_id bigint,
    link_url varchar(255),
    actor_user_id bigint,
    read_at timestamp null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_app_notification_user_created (user_id, created_at),
    index idx_app_notification_user_read (user_id, read_at)
);
