alter table user_interest
    add column express_message varchar(100);

create table chat_room_member_state (
    id bigint not null auto_increment,
    chat_room_id bigint not null,
    user_id bigint not null,
    left_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_chat_room_member_state_room_user (chat_room_id, user_id),
    index idx_chat_room_member_state_user_left (user_id, left_at)
);
