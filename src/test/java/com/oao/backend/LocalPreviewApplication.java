package com.oao.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.oao.backend.auth.EmailDeliveryService;
import java.nio.file.*;
import java.time.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Explicit, local-only browser QA harness. This test source is excluded from bootJar. */
public class LocalPreviewApplication {
  public static void main(String[] args) {
    new SpringApplicationBuilder(OaoBackApplication.class, Fixtures.class)
        .initializers(new WorkflowDatabaseInitializer())
        .run(
            "--server.address=127.0.0.1",
            "--server.port=8087",
            "--spring.config.import=optional:file:.env[.properties]",
            "--spring.datasource.url=jdbc:h2:mem:preview;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.flyway.enabled=false",
            "--spring.jpa.hibernate.ddl-auto=none",
            "--spring.docker.compose.enabled=false",
            "--oao.upload.root-dir=/tmp/oao-preview/public",
            "--oao.dev-tools.enabled=true",
            "--oao.dev-tools.secret=preview-local-only",
            "--oao.auth.oauth-success-redirect-url=http://127.0.0.1:3000/oauth/success",
            "--oao.payment.frontend-url=http://127.0.0.1:3000",
            "--oao.admin.bootstrap.email=admin.preview@example.test",
            "--oao.admin.bootstrap.password=AdminPass1234",
            "--oao.email.from=",
            "--oao.payment.toss.secret-key=",
            "--oao.payment.toss.client-key=",
            "--oao.alimtalk.enabled=false",
            "--oao.verification.message.provider=mock",
            "--oao.verification.message.dev-code-response-enabled=true");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixtures {
    @Bean
    @Primary
    EmailDeliveryService previewMail() {
      var mail = mock(EmailDeliveryService.class);
      doAnswer(
              c -> {
                System.out.println(
                    "LOCAL PREVIEW EMAIL CODE: " + c.getArgument(0) + " -> " + c.getArgument(1));
                return null;
              })
          .when(mail)
          .sendCode(anyString(), anyString());
      return mail;
    }

    @Bean
    ApplicationRunner previewMembers(JdbcTemplate db, PasswordEncoder encoder) {
      return args -> {
        Path dir = Path.of("/tmp/oao-preview/public");
        Files.createDirectories(dir);
        for (String asset : new String[] {"meeting-cafe", "member-1", "member-2", "member-3"}) {
          try (var source =
              LocalPreviewApplication.class.getResourceAsStream("/preview/" + asset + ".jpg")) {
            if (source == null) throw new IllegalStateException("Missing preview asset: " + asset);
            Files.copy(source, dir.resolve(asset + ".jpg"), StandardCopyOption.REPLACE_EXISTING);
          }
        }
        for (int i = 1; i <= 3; i++) {
          db.update(
              "insert into"
                  + " user_account(id,name,status,approval_status,grade,gender,birth_date,created_at,updated_at)"
                  + " values"
                  + " (?,?,'ACTIVE','APPROVED','S',?,'1995-05-20',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
              i,
              i == 1 ? "여름" : i == 2 ? "가을" : "겨울",
              i == 1 ? "FEMALE" : "MALE");
          db.update(
              "insert into"
                  + " user_profile(user_id,height_cm,job,education,activity_region,body_type,smoking_status,drinking_status,religion,mbti,phone_number,phone_verified_at,created_at,updated_at)"
                  + " values (?,175,'디자이너','대학교 졸업','서울"
                  + " 강남구','AVERAGE','NON_SMOKER','SOCIAL','NONE','ENFP','01012345678',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
              i);
          db.update(
              "insert into"
                  + " matching_profile(user_id,job_intro,dating_style,personality_keywords,matching_enabled,created_at,updated_at)"
                  + " values (?,'일상 속 작은 즐거움을 함께 나누고 싶어요.','서로의 하루를 응원하는"
                  + " 연애','다정한,솔직한,활동적인',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
              i);
          for (int n = 1; n <= 2; n++)
            db.update(
                "insert into"
                    + " profile_photo(user_id,image_url,display_order,review_status,created_at,updated_at)"
                    + " values (?,?,?,'APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                i,
                "/uploads/member-" + i + ".jpg",
                n);
          db.update("insert into user_hobby(user_id,hobby_id) values (?,1),(?,8)", i, i);
          db.update(
              "insert into email_credential(user_id,email,password_hash,verified_at,updated_at)"
                  + " values (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
              i,
              i == 1 ? "demo@example.test" : "demo" + i + "@example.test",
              encoder.encode("TestPass1234"));
          db.update(
              "insert into heart_wallet(user_id,balance,created_at,updated_at) values"
                  + " (?,10,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
              i);
          db.update(
              "insert into"
                  + " user_terms_agreement(user_id,terms_document_id,terms_type,version,agreed,agreed_at,created_at,updated_at)"
                  + " select"
                  + " ?,id,terms_type,version,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP"
                  + " from terms_document",
              i);
        }
        db.update(
            "insert into"
                + " match_proposal(id,match_type,user_a_id,user_b_id,status,is_s_grade_guaranteed,user_a_decision,user_b_decision,matched_at,expires_at,created_at,updated_at)"
                + " values"
                + " (1,'AUTO',1,2,'ACCEPTED',true,'ACCEPTED','ACCEPTED',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'AUTO',1,3,'PENDING',true,'PENDING','PENDING',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            java.sql.Timestamp.from(Instant.now().plusSeconds(86400)),
            java.sql.Timestamp.from(Instant.now().plusSeconds(86400)));
        db.update(
            "insert into chat_room(id,match_id,status,created_at,updated_at) values"
                + " (1,1,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update(
            "insert into"
                + " chat_message(chat_room_id,sender_user_id,message_type,content,created_at,updated_at)"
                + " values (1,2,'TEXT','안녕하세요! 만나서 반가워요.',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update(
            "insert into"
                + " meeting_event(id,title,description,image_url,event_date_time,price_amount,capacity,status,created_at,updated_at)"
                + " values (1,'주말의 작은 대화','따뜻한 차 한 잔과 함께 서로를 알아가는"
                + " 시간입니다.','/uploads/meeting-cafe.jpg',?,15000,8,'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            LocalDate.now(ZoneId.of("Asia/Seoul"))
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY))
                .atTime(14, 0));
        System.out.println(
            "LOCAL PREVIEW READY: demo@example.test / TestPass1234 (isolated H2; external payments"
                + " disabled)");
      };
    }
  }
}
