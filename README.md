# LoveCatcher backend

Java 21 · Spring Boot 4 · MariaDB · Flyway. 가입 심사, 매칭, 실시간 채팅, 이메일/카카오 계정, 토스 결제, 알림톡 운영 API.

## 실행

`.env.example`을 `.env`로 복사하고 DB와 필요한 제공업체 설정을 채운 뒤 실행합니다.

```sh
./gradlew bootRun
```

운영에서는 개발 도구를 끄고 SOLAPI SMS 공급자와 HTTPS 도메인을 설정합니다. 서류 저장소 `private-verification`은 정적 파일로 공개하지 않습니다.

## 검증

```sh
./gradlew test bootJar
```

기본 테스트는 H2 메모리 DB, 제공업체 mock을 사용합니다. `WorkflowIntegrationTests`는 비어 있는 격리 MariaDB에서도 실행할 수 있습니다. 테스트가 샘플 레코드를 만들기 때문에 운영 DB를 지정하면 안 됩니다.

```sh
OAO_TEST_DB_URL=jdbc:mariadb://127.0.0.1:3309/oao_qa \
OAO_TEST_DB_DRIVER=org.mariadb.jdbc.Driver \
OAO_TEST_DB_USER=oao_qa OAO_TEST_DB_PASSWORD=qa-only \
OAO_TEST_FLYWAY=true OAO_TEST_DDL_MODE=validate \
./gradlew test --tests com.oao.backend.WorkflowIntegrationTests --rerun-tasks
```

브라우저용 격리 미리보기: `./gradlew localPreview` (127.0.0.1:8087). 테스트 소스만 포함하고 배포 JAR에서는 제외됩니다.

자세한 기능 현황과 설정: [구현 현황](../docs/IMPLEMENTATION_STATUS.md), [외부 연동](../docs/EXTERNAL_SETUP.md), [작업 계획](../features/043_COMPLETE_APPLICATION_AND_WHITE_MODE_PLAN.md).
