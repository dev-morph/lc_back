alter table matching_schedule_config
    add column run_times varchar(255) not null default '09:00';

update matching_schedule_config
set run_times = '09:00'
where run_times is null or run_times = '';
