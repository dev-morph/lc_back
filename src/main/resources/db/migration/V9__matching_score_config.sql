create table matching_score_config (
    id bigint not null auto_increment,
    hobby_point_per_match int not null,
    hobby_max_point int not null,
    same_smoking_point int not null,
    same_drinking_point int not null,
    same_religion_point int not null,
    same_grade_point int not null,
    adjacent_grade_point int not null,
    allow_previous_auto_match boolean not null,
    updated_by_admin_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

insert into matching_score_config (
    id,
    hobby_point_per_match,
    hobby_max_point,
    same_smoking_point,
    same_drinking_point,
    same_religion_point,
    same_grade_point,
    adjacent_grade_point,
    allow_previous_auto_match,
    updated_by_admin_id,
    created_at,
    updated_at
) values (
    1,
    5,
    25,
    10,
    5,
    10,
    10,
    5,
    false,
    null,
    current_timestamp(6),
    current_timestamp(6)
);
