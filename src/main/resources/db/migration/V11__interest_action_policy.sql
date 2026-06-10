alter table user_interest
    add column interest_type varchar(32) not null default 'LIKE',
    add column heart_cost int not null default 1,
    add column notification_target bit not null default 0,
    add column express_decision varchar(32) not null default 'PENDING',
    add column express_decided_at datetime(6),
    add column chat_room_created bit not null default 0,
    add column match_id bigint;
