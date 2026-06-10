create table phone_verification_code (
    id bigint not null auto_increment,
    user_id bigint not null,
    phone_number varchar(32) not null,
    code_hash varchar(128) not null,
    status varchar(32) not null,
    expires_at datetime(6) not null,
    verified_at datetime(6),
    attempt_count int not null,
    resend_count int not null,
    last_sent_at datetime(6) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

create index idx_phone_verification_user_status on phone_verification_code (user_id, status);
create index idx_phone_verification_phone_status on phone_verification_code (phone_number, status);
