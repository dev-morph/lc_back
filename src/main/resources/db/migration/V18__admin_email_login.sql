alter table admin_user
    add column if not exists password_hash varchar(255);

alter table admin_user
    add column if not exists last_login_at datetime(6);

alter table admin_user
    drop foreign key if exists fk_admin_user_user_account;

alter table admin_user
    drop index if exists uk_admin_user_user_id;

alter table admin_user
    modify column user_id bigint null;
