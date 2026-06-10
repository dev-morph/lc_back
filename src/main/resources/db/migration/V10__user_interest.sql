create table user_interest (
    id bigint not null auto_increment,
    sender_user_id bigint not null,
    receiver_user_id bigint not null,
    status varchar(32) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_user_interest_sender_receiver (sender_user_id, receiver_user_id),
    index idx_user_interest_receiver_status (receiver_user_id, status),
    index idx_user_interest_sender_status (sender_user_id, status)
);
