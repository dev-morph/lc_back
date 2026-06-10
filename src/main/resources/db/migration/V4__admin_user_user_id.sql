alter table admin_user
    add column if not exists user_id bigint;

update admin_user au
join oauth_account oa on oa.email = au.email
set au.user_id = oa.user_id
where au.user_id is null;

delete from admin_user
where user_id is null;

alter table admin_user
    drop foreign key if exists fk_admin_user_user_account;

alter table admin_user
    drop index if exists uk_admin_user_user_id;

alter table admin_user
    modify column user_id bigint not null;

alter table admin_user
    add constraint uk_admin_user_user_id unique (user_id);

alter table admin_user
    add constraint fk_admin_user_user_account
        foreign key (user_id) references user_account (id);
