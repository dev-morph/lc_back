create table stored_file_cleanup (
 id bigint auto_increment primary key,
 file_path varchar(1024) not null,
 attempts int not null default 0,
 last_error varchar(512),
 created_at datetime(6) not null,
 next_attempt_at datetime(6) not null
);
