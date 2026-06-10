create table terms_document (
    id bigint not null auto_increment,
    terms_type varchar(64) not null,
    title varchar(100) not null,
    version varchar(32) not null,
    content longtext not null,
    is_required bit not null,
    effective_from date not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_terms_document_type_version (terms_type, version),
    index idx_terms_document_current (terms_type, is_required, effective_from)
);

create table user_terms_agreement (
    id bigint not null auto_increment,
    user_id bigint not null,
    terms_document_id bigint not null,
    terms_type varchar(64) not null,
    version varchar(32) not null,
    agreed bit not null,
    agreed_at datetime(6) not null,
    ip_address varchar(64),
    user_agent varchar(512),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_user_terms_agreement_user_document (user_id, terms_document_id),
    index idx_user_terms_agreement_user_id (user_id),
    index idx_user_terms_agreement_terms_document_id (terms_document_id),
    index idx_user_terms_agreement_user_type (user_id, terms_type)
);

insert into terms_document (
    terms_type,
    title,
    version,
    content,
    is_required,
    effective_from,
    created_at,
    updated_at
) values (
    'SERVICE_TERMS',
    'LoveCatcher 이용약관',
    '1.0.0',
    '제1조 목적 본 약관은 LoveCatcher가 제공하는 웹사이트 및 모바일 웹 서비스 이용과 관련하여 회사와 회원 사이의 권리, 의무 및 책임사항을 정하는 것을 목적으로 합니다.

제2조 용어의 정의 회원이란 Kakao OAuth를 통해 본 서비스에 가입하고 회사가 제공하는 매칭, 프로필, 커뮤니티 등 기능을 이용하는 자를 말합니다. 서비스란 회사가 제공하는 소개, 매칭, 프로필 검수, 알림 및 기타 부가 기능을 말합니다.

제3조 약관의 게시와 개정 회사는 본 약관의 내용을 회원이 쉽게 확인할 수 있도록 서비스 화면에 게시합니다. 회사는 관련 법령을 위반하지 않는 범위에서 약관을 개정할 수 있으며, 중요한 변경이 있는 경우 시행일 전 서비스 화면 등을 통해 공지합니다.

제4조 회원가입 및 계정 회원가입은 Kakao OAuth 인증을 통해 진행됩니다. 회사는 가입 신청 후 프로필 정보와 증빙자료를 확인할 수 있으며, 서비스 품질과 안전을 위해 가입 승인을 보류하거나 거절할 수 있습니다.

제5조 서비스 이용 회원은 회사가 정한 운영 정책에 따라 매칭 기능을 이용할 수 있습니다. 매칭 결과와 추천은 회원의 입력 정보, 활동 상태, 승인 상태 및 서비스 운영 기준에 따라 달라질 수 있습니다.

제6조 회원의 의무 회원은 정확한 정보를 제공해야 하며, 타인의 정보 도용, 허위 프로필 작성, 부적절한 대화, 서비스 운영 방해 행위를 해서는 안 됩니다. 회원의 위반 행위가 확인되는 경우 회사는 이용 제한 또는 탈퇴 처리를 할 수 있습니다.

제7조 서비스 변경 및 중단 회사는 안정적인 서비스 제공을 위해 기능을 변경하거나 일시 중단할 수 있습니다. 중대한 서비스 중단이 발생하는 경우 가능한 범위에서 사전에 안내합니다.

제8조 준거법 및 분쟁 해결 본 약관은 대한민국 법령에 따라 해석되며, 서비스 이용과 관련한 분쟁은 관련 법령과 회사의 운영 정책에 따라 해결합니다.',
    b'1',
    '2026-05-30',
    current_timestamp(6),
    current_timestamp(6)
);

insert into terms_document (
    terms_type,
    title,
    version,
    content,
    is_required,
    effective_from,
    created_at,
    updated_at
) values (
    'PRIVACY_COLLECTION',
    '개인정보 수집 및 이용 동의',
    '1.0.0',
    '1. 수집 항목 회사는 Kakao OAuth 식별자, 이메일, 닉네임, 성별, 생년월일, 직업, 학력, 활동지역, 프로필 사진, 자기소개, 매칭 정보, 서비스 이용 기록을 수집할 수 있습니다. 이메일은 Kakao 계정 설정에 따라 제공되지 않을 수 있습니다.

2. 수집 및 이용 목적 수집한 정보는 회원 식별, 가입 승인, 프로필 검수, 매칭 추천, 부정 이용 방지, 고객 문의 처리, 서비스 품질 개선을 위해 이용합니다.

3. 보유 및 이용 기간 개인정보는 회원 탈퇴 또는 수집 및 이용 목적 달성 시까지 보유합니다. 다만 관련 법령에 따라 보관이 필요한 정보는 정해진 기간 동안 별도 보관 후 파기합니다.

4. 프로필 사진 및 증빙자료 처리 프로필 사진과 증빙자료는 가입 승인 및 서비스 신뢰도 검토를 위해 사용됩니다. 증빙자료는 다른 회원에게 공개되지 않으며 검수 목적 외 사용을 제한합니다.

5. 동의 거부 권리 회원은 개인정보 수집 및 이용에 대한 동의를 거부할 수 있습니다. 다만 필수 항목에 대한 동의를 거부하는 경우 회원가입 및 서비스 이용이 제한될 수 있습니다.',
    b'1',
    '2026-05-30',
    current_timestamp(6),
    current_timestamp(6)
);
