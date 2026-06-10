create table meeting_event (
    id bigint not null auto_increment,
    title varchar(120) not null,
    description longtext not null,
    image_url varchar(512) not null,
    event_date_time datetime(6) not null,
    price_amount int not null,
    capacity int not null,
    status varchar(32) not null,
    created_by_admin_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_meeting_event_status_date (status, event_date_time),
    index idx_meeting_event_date (event_date_time)
);

create table meeting_application (
    id bigint not null auto_increment,
    meeting_event_id bigint not null,
    user_id bigint not null,
    application_status varchar(32) not null,
    payment_status varchar(32) not null,
    confirmed_at datetime(6),
    admin_note varchar(512),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_meeting_application_event_user (meeting_event_id, user_id),
    index idx_meeting_application_user_status (user_id, application_status, payment_status),
    index idx_meeting_application_event_status (meeting_event_id, application_status, payment_status)
);
