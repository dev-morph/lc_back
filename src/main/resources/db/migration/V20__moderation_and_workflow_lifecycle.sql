alter table profile_photo add column rejection_reason varchar(512);
alter table report add column resolution_note varchar(1000);
alter table report add column resolved_by_admin_id bigint;
alter table report add column resolved_at datetime(6);
alter table premium_intro_request add column match_id bigint;
alter table premium_intro_request add column admin_note varchar(1000);
create table matching_run_lock (id bigint primary key);
insert into matching_run_lock(id) values (1);
