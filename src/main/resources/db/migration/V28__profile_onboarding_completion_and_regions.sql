alter table user_account
    add column onboarding_completed_at datetime(6) null after approved_by_admin_id;

update user_account
set onboarding_completed_at = coalesce(approved_at, updated_at, created_at, current_timestamp)
where approval_status = 'APPROVED';

create table user_activity_region (
    user_id bigint not null,
    region_code varchar(64) not null,
    region_label varchar(100) not null,
    display_order int not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (user_id, region_code),
    unique key uk_user_activity_region_order (user_id, display_order),
    index idx_user_activity_region_code (region_code)
);
