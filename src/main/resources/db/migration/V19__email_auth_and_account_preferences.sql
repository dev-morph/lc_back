create table email_credential (
 user_id bigint primary key, email varchar(255) not null unique, password_hash varchar(255) not null,
 verified_at datetime(6) not null, updated_at datetime(6) not null
);
create table email_challenge (
 id varchar(36) primary key, email varchar(255) not null, password_hash varchar(255), user_id bigint,
 purpose varchar(24) not null, code_hash varchar(64) not null, attempts int not null default 0,
 expires_at datetime(6) not null, consumed_at datetime(6), created_at datetime(6) not null,
 index idx_email_challenge_email (email,created_at)
);
alter table user_account add column auth_version int not null default 0;
alter table user_account add column rejection_reason varchar(512);
alter table matching_profile add column personality_keywords varchar(255);
create table user_preferences (
 user_id bigint primary key, alimtalk_enabled boolean not null default false,
 message_notifications boolean not null default true, updated_at datetime(6) not null
);
