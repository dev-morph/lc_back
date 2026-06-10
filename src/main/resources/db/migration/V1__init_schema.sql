create table user_account (
    id bigint not null auto_increment,
    status varchar(32) not null,
    approval_status varchar(32) not null,
    grade varchar(8),
    gender varchar(16),
    birth_date date,
    approved_at datetime(6),
    approved_by_admin_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6),
    primary key (id)
);

create table user_verification_document (
    id bigint not null auto_increment,
    user_id bigint not null,
    document_type varchar(64) not null,
    file_url varchar(512) not null,
    review_status varchar(32) not null,
    rejection_reason varchar(512),
    reviewed_by_admin_id bigint,
    reviewed_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_user_verification_document_user_id (user_id)
);

create table user_grade_history (
    id bigint not null auto_increment,
    user_id bigint not null,
    previous_grade varchar(8),
    new_grade varchar(8) not null,
    changed_by_admin_id bigint not null,
    reason varchar(512),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_user_grade_history_user_id (user_id)
);

create table oauth_account (
    id bigint not null auto_increment,
    user_id bigint not null,
    provider varchar(32) not null,
    provider_user_id varchar(128) not null,
    email varchar(255),
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_oauth_account_provider_user (provider, provider_user_id),
    index idx_oauth_account_user_id (user_id)
);

create table admin_user (
    id bigint not null auto_increment,
    email varchar(255) not null,
    name varchar(100) not null,
    role varchar(64) not null,
    status varchar(32) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_admin_user_email (email)
);

create table user_profile (
    id bigint not null auto_increment,
    user_id bigint not null,
    height_cm int,
    mbti varchar(8),
    job varchar(100),
    education varchar(100),
    religion varchar(64),
    activity_region varchar(64),
    smoking_status varchar(32),
    drinking_status varchar(32),
    phone_number varchar(32),
    phone_verified_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_user_profile_user_id (user_id)
);

create table profile_photo (
    id bigint not null auto_increment,
    user_id bigint not null,
    image_url varchar(512) not null,
    display_order int not null,
    review_status varchar(32) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_profile_photo_user_id (user_id)
);

create table matching_profile (
    id bigint not null auto_increment,
    user_id bigint not null,
    job_intro varchar(1000),
    dating_style varchar(1000),
    matching_enabled bit not null,
    last_auto_matched_at datetime(6),
    auto_match_count int not null default 0,
    s_grade_guaranteed_match_count int not null default 0,
    no_response_count int not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_matching_profile_user_id (user_id),
    index idx_matching_profile_enabled (matching_enabled)
);

create table personality_keyword (
    id bigint not null auto_increment,
    name varchar(64) not null,
    primary key (id),
    unique key uk_personality_keyword_name (name)
);

create table user_personality_keyword (
    user_id bigint not null,
    keyword_id bigint not null,
    primary key (user_id, keyword_id)
);

create table hobby (
    id bigint not null auto_increment,
    category varchar(64) not null,
    name varchar(64) not null,
    primary key (id),
    unique key uk_hobby_name (name)
);

create table user_hobby (
    user_id bigint not null,
    hobby_id bigint not null,
    primary key (user_id, hobby_id)
);

create table match_proposal (
    id bigint not null auto_increment,
    match_type varchar(32) not null,
    user_a_id bigint not null,
    user_b_id bigint not null,
    requested_by_user_id bigint,
    status varchar(32) not null,
    matched_reason varchar(1000),
    is_s_grade_guaranteed bit not null,
    matched_at datetime(6),
    expires_at datetime(6),
    user_a_decision varchar(32) not null,
    user_b_decision varchar(32) not null,
    user_a_decided_at datetime(6),
    user_b_decided_at datetime(6),
    accepted_at datetime(6),
    rejected_at datetime(6),
    expired_at datetime(6),
    closed_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_match_proposal_user_a_id (user_a_id),
    index idx_match_proposal_user_b_id (user_b_id),
    index idx_match_proposal_status (status)
);

create table heart_wallet (
    id bigint not null auto_increment,
    user_id bigint not null,
    balance int not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_heart_wallet_user_id (user_id)
);

create table heart_transaction (
    id bigint not null auto_increment,
    user_id bigint not null,
    transaction_type varchar(32) not null,
    amount int not null,
    balance_after int not null,
    reference_type varchar(64),
    reference_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_heart_transaction_user_id (user_id)
);

create table heart_product (
    id bigint not null auto_increment,
    name varchar(100) not null,
    heart_amount int not null,
    price decimal(12, 2) not null,
    status varchar(32) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
);

create table payment_transaction (
    id bigint not null auto_increment,
    user_id bigint not null,
    heart_product_id bigint not null,
    provider varchar(64) not null,
    provider_transaction_id varchar(128) not null,
    amount decimal(12, 2) not null,
    status varchar(32) not null,
    approved_at datetime(6),
    failed_reason varchar(512),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_payment_transaction_provider_tx (provider, provider_transaction_id),
    index idx_payment_transaction_user_id (user_id)
);

create table instant_intro_usage_window (
    id bigint not null auto_increment,
    user_id bigint not null,
    window_started_at datetime(6) not null,
    window_expires_at datetime(6) not null,
    usage_count int not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_instant_intro_usage_window_user_id (user_id)
);

create table premium_intro_request (
    id bigint not null auto_increment,
    user_id bigint not null,
    status varchar(32) not null,
    min_age int,
    max_age int,
    min_height_cm int,
    max_height_cm int,
    appearance_weight int,
    spec_weight int,
    appearance_preference_text varchar(2000),
    preferred_job_groups varchar(1000),
    important_point_text varchar(2000),
    assigned_admin_id bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_premium_intro_request_user_id (user_id),
    index idx_premium_intro_request_status (status)
);

create table premium_intro_request_keyword (
    premium_intro_request_id bigint not null,
    keyword_id bigint not null,
    primary key (premium_intro_request_id, keyword_id)
);

create table chat_room (
    id bigint not null auto_increment,
    match_id bigint not null,
    status varchar(32) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_chat_room_match_id (match_id)
);

create table chat_message (
    id bigint not null auto_increment,
    chat_room_id bigint not null,
    sender_user_id bigint not null,
    message_type varchar(32) not null,
    content text,
    read_at datetime(6),
    deleted_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_chat_message_room_created (chat_room_id, created_at)
);

create table notification_log (
    id bigint not null auto_increment,
    user_id bigint not null,
    channel varchar(32) not null,
    template_code varchar(128),
    target_type varchar(64),
    target_id bigint,
    status varchar(32) not null,
    sent_at datetime(6),
    failed_reason varchar(512),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    index idx_notification_log_user_id (user_id)
);

create table report (
    id bigint not null auto_increment,
    reporter_user_id bigint not null,
    reported_user_id bigint not null,
    target_type varchar(64) not null,
    target_id bigint,
    reason varchar(1000) not null,
    status varchar(32) not null,
    created_at datetime(6) not null,
    primary key (id),
    index idx_report_reported_user_id (reported_user_id)
);

create table block_relation (
    id bigint not null auto_increment,
    blocker_user_id bigint not null,
    blocked_user_id bigint not null,
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_block_relation_pair (blocker_user_id, blocked_user_id)
);
