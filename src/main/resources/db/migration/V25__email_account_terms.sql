-- Preserve the accepted historical versions while reflecting the new authentication options.
insert into terms_document (terms_type,title,version,content,is_required,effective_from,created_at,updated_at)
select terms_type,title,'1.1.0',
  replace(replace(replace(content,
    'Kakao OAuth를 통해 본 서비스에 가입하고','이메일 인증 또는 Kakao OAuth를 통해 본 서비스에 가입하고'),
    '회원가입은 Kakao OAuth 인증을 통해 진행됩니다.','회원가입은 이메일 인증 또는 Kakao OAuth 인증을 통해 진행됩니다. 계정 연결은 로그인한 회원이 직접 인증한 경우에만 이루어집니다.'),
    '카카오 계정 식별자, 이메일,','인증한 이메일, 암호화한 비밀번호, 카카오 계정 식별자(연결한 경우),'),
  is_required,'2026-09-17',current_timestamp(6),current_timestamp(6)
from terms_document where version='1.0.0';
