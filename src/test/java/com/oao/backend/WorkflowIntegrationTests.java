package com.oao.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oao.backend.auth.*;
import com.oao.backend.chat.service.*;
import com.oao.backend.common.*;
import com.oao.backend.matching.service.*;
import com.oao.backend.meeting.service.*;
import com.oao.backend.payment.service.*;
import com.oao.backend.user.service.*;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=${OAO_TEST_DB_URL:jdbc:h2:mem:workflows;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
      "spring.datasource.driver-class-name=${OAO_TEST_DB_DRIVER:org.h2.Driver}",
      "spring.datasource.username=${OAO_TEST_DB_USER:sa}",
      "spring.datasource.password=${OAO_TEST_DB_PASSWORD:}",
      "spring.flyway.enabled=${OAO_TEST_FLYWAY:false}",
      "spring.jpa.hibernate.ddl-auto=${OAO_TEST_DDL_MODE:none}",
      "oao.upload.root-dir=${java.io.tmpdir}/oao-workflow-tests/public",
      "oao.dev-tools.enabled=false",
      "oao.alimtalk.enabled=false",
      "oao.cors.allowed-origins=http://localhost:3000",
      "oao.admin-bootstrap.enabled=false",
      "spring.config.import=",
      "oao.admin.bootstrap.email=",
      "oao.admin.bootstrap.password="
    })
@ContextConfiguration(initializers = WorkflowDatabaseInitializer.class)
class WorkflowIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired com.oao.backend.user.repository.UserAccountRepository users;
  @Autowired DbRows rows;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired ProfileIntroPhotoService profilePhotos;
  @Autowired UserApprovalService approvals;
  @Autowired com.oao.backend.premium.service.PremiumIntroductionService premium;
  @Autowired com.oao.backend.premium.service.PremiumWorkflowService premiumWorkflow;
  @Autowired com.oao.backend.config.WebSocketSecurity webSockets;
  @Autowired EmailAuthService email;
  @Autowired TossPaymentService payments;
  @Autowired AccountSettingsService settings;
  @Autowired MatchingPolicyService policy;
  @Autowired ModerationService moderation;
  @Autowired VerificationReviewService reviews;
  @Autowired ProfileOnboardingService onboarding;
  @Autowired UserProfileService profiles;
  @Autowired ChatService chat;
  @Autowired ChatMessageActions messages;
  @Autowired MeetingLifecycleService meetings;
  @Autowired AdminMeetingService adminMeetings;
  @Autowired MeetingService meetingViews;
  @Autowired com.oao.backend.terms.service.TermsService terms;
  @Autowired com.oao.backend.meeting.repository.MeetingEventRepository meetingEvents;
  @Autowired KakaoAccountService kakao;
  @Autowired AutoMatchingService autoMatching;
  @MockitoBean EmailDeliveryService delivery;
  @MockitoBean TossPaymentsClient toss;
  @LocalServerPort int port;
  final Map<String, String> codes = new ConcurrentHashMap<>();
  long user, other;

  @BeforeEach
  void setup() {
    // Fixtures use new IDs in each test, avoiding cross-test state and real accounts/providers.
    user = member("여름", "FEMALE");
    other = member("가을", "MALE");
    doAnswer(
            call -> {
              codes.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(delivery)
        .sendCode(anyString(), anyString());
    when(toss.clientKey()).thenReturn("test_ck_fixture");
    when(toss.frontend()).thenReturn("http://localhost:3000");
    when(toss.getByOrder(anyString())).thenReturn(null);
    when(toss.confirm(anyString(), anyString(), anyLong()))
        .thenAnswer(c -> provider(c.getArgument(0), c.getArgument(1), c.getArgument(2), "DONE"));
  }

  @Test
  void termsEffectiveTodayAreVisibleOnTheirEffectiveDate() {
    String version = "qa-" + System.nanoTime();
    var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
    db.update(
        "insert into"
            + " terms_document(terms_type,title,version,content,is_required,effective_from,created_at,updated_at)"
            + " values ('SERVICE_TERMS','시행일 확인',?,'오늘 시행하는"
            + " 약관',true,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        version,
        today);
    try {
      assertThat(terms.currentRequiredTerms())
          .anySatisfy(
              document -> {
                assertThat(document.getVersion()).isEqualTo(version);
                assertThat(document.getEffectiveFrom()).isEqualTo(today);
              });
    } finally {
      db.update("delete from terms_document where version=?", version);
    }
  }

  @Test
  void meetingWallClockTimeSurvivesJpaAndJdbcEdits() {
    var expected =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(7).atTime(14, 30);
    var event =
        meetingEvents.saveAndFlush(
            com.oao.backend.meeting.domain.MeetingEvent.create(
                "오후의 모임", "일정을 확인하는 테스트 모임", "/fixture.jpg", expected, 0, 8, null));
    var stored =
        db.queryForObject(
            "select cast(event_date_time as char(40)) from meeting_event where id=?",
            (rs, n) -> java.time.LocalDateTime.parse(rs.getString(1).strip().replace(' ', 'T')),
            event.getId());
    assertThat(stored).isEqualTo(expected);
    assertThat(meetingViews.findMeeting(event.getId(), user).eventDateTime()).isEqualTo(expected);
    var edited = expected.plusHours(2);
    meetings.update(event.getId(), "수정된 모임", "수정된 테스트 일정입니다.", edited, 0, 8, "OPEN");
    assertThat(meetingViews.findMeeting(event.getId(), user).eventDateTime()).isEqualTo(edited);
    assertThat(
            rows.one("select event_date_time from meeting_event where id=?", event.getId())
                .get("eventDateTime"))
        .isEqualTo(edited.toString());
  }

  @Test
  void databaseAndJdbcUseTheSameInstant() {
    Instant databaseNow =
        db.queryForObject("select CURRENT_TIMESTAMP", Timestamp.class).toInstant();
    assertThat(java.time.Duration.between(databaseNow, Instant.now()).abs().toSeconds())
        .isLessThan(5);
    assertThat(
            java.time.Duration.between(
                    users.findById(user).orElseThrow().getCreatedAt(), Instant.now())
                .abs()
                .toSeconds())
        .isLessThan(5);
    String address = address();
    String id =
        email.start(address, "GoodPass1234", "REGISTER", null).get("challengeId").toString();
    assertThat(
            count(
                "select count(*) from email_challenge where id=? and expires_at>CURRENT_TIMESTAMP"
                    + " and expires_at<?",
                id,
                Timestamp.from(Instant.now().plusSeconds(901))))
        .isEqualTo(1);
  }

  @Test
  void alimtalkDefaultsOnButPreservesExplicitOptOut() {
    assertThat(settings.settings(user).get("alimtalkEnabled")).isEqualTo(true);
    settings.update(user, false, null, List.of(), false, true);
    assertThat(settings.settings(user).get("alimtalkEnabled")).isEqualTo(false);
    db.update("insert into user_preferences(user_id,message_notifications,updated_at) values (?,true,CURRENT_TIMESTAMP)", other);
    assertThat(settings.settings(other).get("alimtalkEnabled")).isEqualTo(true);
  }

  @Test
  void uncertainNotificationDoesNotResendUntilAnAdministratorRetries() throws Exception {
    eligible(user);
    // The dispatcher processes all users; isolate its queue from earlier test fixtures.
    db.update("delete from notification_outbox");
    // No preference row: verify that the new default really permits delivery.
    long proposal = match("PENDING");
    var client = mock(com.oao.backend.notification.service.AlimtalkDeliveryClient.class);
    var outbox =
        new com.oao.backend.notification.service.AlimtalkOutboxService(
            rows,
            client,
            transactionManager,
            true,
            "key",
            "secret",
            "pf",
            "from",
            "https://example.test",
            "match",
            "express",
            "completed");
    outbox.enqueue(user, "MATCH_ARRIVED", proposal, "/home");
    outbox.enqueue(user, "MATCH_ARRIVED", proposal, "/home");
    assertThat(count("select count(*) from notification_outbox where user_id=?", user))
        .isEqualTo(1);
    when(client.send(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
        .thenAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              assertThat(
                      db.queryForObject(
                          "select status from notification_outbox where user_id=?",
                          String.class,
                          user))
                  .isEqualTo("SENDING");
              throw new java.io.IOException("Response lost after submitting");
            });
    outbox.dispatch();
    outbox.dispatch();
    assertThat(
            db.queryForObject(
                "select status from notification_outbox where user_id=?", String.class, user))
        .isEqualTo("DELIVERY_UNKNOWN");
    verify(client, times(1))
        .send(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString());
    long id = count("select id from notification_outbox where user_id=?", user);
    outbox.retry(id);
    settings.update(user, false, null, List.of(), false, true);
    outbox.dispatch();
    assertThat(
            db.queryForObject(
                "select status from notification_outbox where id=?", String.class, id))
        .isEqualTo("SKIPPED");
    verifyNoMoreInteractions(client);
  }

  @Test
  void photoReplacementPausesMatchingAndRejectsDisguisedFiles() {
    eligible(user);
    var invalid =
        new MockMultipartFile("photos", "fake.png", "image/png", "<html>wrong</html>".getBytes());
    assertThatThrownBy(
            () ->
                profilePhotos.saveIntroPhoto(
                    user, "함께 즐거운 시간을 보내고 싶어요.", List.of(invalid, invalid), null))
        .isInstanceOf(BusinessException.class);
    byte[] png = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0};
    var photo = new MockMultipartFile("photos", "profile.png", "image/png", png);
    profilePhotos.saveIntroPhoto(user, "함께 즐거운 시간을 보내고 싶어요.", List.of(photo, photo), null);
    assertThat(
            count(
                "select count(*) from matching_profile where user_id=? and matching_enabled=true",
                user))
        .isZero();
    assertThat(
            count(
                "select count(*) from profile_photo where user_id=? and review_status='PENDING'",
                user))
        .isEqualTo(2);
  }

  @Test
  void accountRejectionPersistsReasonAndApprovalClearsIt() {
    eligible(user);
    approvals.reject(user, "프로필 정보를 다시 확인해주세요.");
    assertThat(settings.settings(user).get("rejectionReason")).isEqualTo("프로필 정보를 다시 확인해주세요.");
    assertThat(policy.eligible(user)).isFalse();
    approvals.approve(user, com.oao.backend.user.domain.UserAccount.MemberGrade.S, 1L, "재심사 승인");
    assertThat(settings.settings(user).get("rejectionReason")).isNull();
  }

  @Test
  void webSocketRejectsOtherRoomsAndRevokesAnExistingSubscription() {
    long roomId = room();
    var principal =
        new KakaoPrincipal(
            user,
            "test:" + user,
            null,
            "여름",
            com.oao.backend.user.domain.UserAccount.ApprovalStatus.APPROVED,
            com.oao.backend.user.domain.UserAccount.MemberGrade.S,
            Map.of(),
            List.of());
    var auth =
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken
            .authenticated(principal, null, List.of());
    var inbound = webSockets.inbound();
    var connect =
        org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(
            org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
    connect.setSessionId("fixture-" + user);
    connect.setUser(auth);
    connect.setLeaveMutable(true);
    inbound.preSend(
        org.springframework.messaging.support.MessageBuilder.createMessage(
            new byte[0], connect.getMessageHeaders()),
        null);
    var subscribe =
        org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(
            org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
    subscribe.setSessionId("fixture-" + user);
    subscribe.setDestination("/topic/users." + other + ".notifications");
    subscribe.setLeaveMutable(true);
    assertThatThrownBy(
            () ->
                inbound.preSend(
                    org.springframework.messaging.support.MessageBuilder.createMessage(
                        new byte[0], subscribe.getMessageHeaders()),
                    null))
        .isInstanceOf(BusinessException.class);
    var outgoing =
        org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
            org.springframework.messaging.simp.SimpMessageType.MESSAGE);
    outgoing.setSessionId("fixture-" + user);
    outgoing.setDestination("/topic/chat.rooms." + roomId);
    var message =
        org.springframework.messaging.support.MessageBuilder.createMessage(
            new byte[0], outgoing.getMessageHeaders());
    assertThat(webSockets.outbound().preSend(message, null)).isNotNull();
    db.update("update user_account set auth_version=auth_version+1 where id=?", user);
    assertThat(webSockets.outbound().preSend(message, null)).isNull();
  }

  @Test
  void premiumKeepsKeywordsAndCreatesOneEligibleManualIntroduction() {
    eligible(user);
    eligible(other);
    var command =
        new com.oao.backend.premium.service.PremiumIntroductionService
            .CreatePremiumIntroductionCommand(
            25, 40, 150, 200, 40, 60, "밝은 인상", "선호 없음", "서로 배려하는 관계", List.of(1L, 2L));
    long request = premium.create(user, command).getId();
    assertThat(premiumWorkflow.list(user).get(0).get("keywords")).isEqualTo(List.of("다정한", "솔직한"));
    assertThatThrownBy(() -> premium.create(user, command)).isInstanceOf(BusinessException.class);
    premiumWorkflow.update(request, "IN_REVIEW", "조건 확인 중", null, 1L);
    assertThat(premiumWorkflow.candidates(request))
        .anyMatch(row -> ((Number) row.get("id")).longValue() == other);
    premiumWorkflow.update(request, "MATCHED", "조건에 맞는 소개 완료", other, 1L);
    assertThat(premiumWorkflow.detail(request, user).get("matchId")).isNotNull();
    assertThat(premiumWorkflow.candidates(request))
        .noneMatch(row -> ((Number) row.get("id")).longValue() == other);
    assertThatThrownBy(() -> premiumWorkflow.update(request, "MATCHED", "다시 처리", other, 1L))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void emailRequiresProofAndChallengeCannotBeReused() {
    String address = address();
    String challenge =
        email
            .start(address.toUpperCase(), "GoodPass1234", "REGISTER", null)
            .get("challengeId")
            .toString();
    assertThat(count("select count(*) from email_credential where email=?", address)).isZero();
    assertThatThrownBy(() -> email.verify(challenge, "wrong!", null, null))
        .isInstanceOf(BusinessException.class);
    assertThat(count("select attempts from email_challenge where id=?", challenge)).isEqualTo(1);
    Long id = email.verify(challenge, codes.get(address), null, null);
    assertThat(email.login(address, "GoodPass1234")).isEqualTo(id);
    assertThatThrownBy(() -> email.verify(challenge, codes.get(address), null, null))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> email.login(address, "BadPass1234"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void verificationLocksOutAfterFiveFailures() {
    String address = address();
    String challenge =
        email.start(address, "GoodPass1234", "REGISTER", null).get("challengeId").toString();
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> email.verify(challenge, "wrong!", null, null))
          .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> email.verify(challenge, codes.get(address), null, null))
        .isInstanceOf(BusinessException.class);
    assertThat(count("select attempts from email_challenge where id=?", challenge)).isEqualTo(5);
  }

  @Test
  void expiredCodeDoesNotCreateAccount() {
    String address = address();
    String id =
        email.start(address, "GoodPass1234", "REGISTER", null).get("challengeId").toString();
    db.update(
        "update email_challenge set expires_at=? where id=?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    assertThatThrownBy(() -> email.verify(id, codes.get(address), null, null))
        .isInstanceOf(BusinessException.class);
    assertThat(count("select count(*) from email_credential where email=?", address)).isZero();
  }

  @Test
  void emailLinkIsBoundToRequestingAccount() {
    String address = address();
    String id = email.start(address, "GoodPass1234", "LINK", user).get("challengeId").toString();
    assertThatThrownBy(() -> email.verify(id, codes.get(address), null, other))
        .isInstanceOf(BusinessException.class);
    assertThat(email.verify(id, codes.get(address), null, user)).isEqualTo(user);
    assertThat(email.login(address, "GoodPass1234")).isEqualTo(user);
  }

  @Test
  void registerDoesNotAttachToAnExistingSession() {
    String address = address();
    String id =
        email.start(address, "GoodPass1234", "REGISTER", user).get("challengeId").toString();
    assertThat(email.verify(id, codes.get(address), null, user)).isNotEqualTo(user);
  }

  @Test
  void passwordResetInvalidatesOldPasswordAndSessionVersion() {
    String address = address();
    String start = email.start(address, "GoodPass1234", "LINK", user).get("challengeId").toString();
    email.verify(start, codes.get(address), null, user);
    db.update(
        "update email_challenge set created_at=? where id=?",
        Timestamp.from(Instant.now().minusSeconds(61)),
        start);
    String reset = email.start(address, null, "RESET", null).get("challengeId").toString();
    email.verify(reset, codes.get(address), "NextPass1234", null);
    assertThat(email.login(address, "NextPass1234")).isEqualTo(user);
    assertThatThrownBy(() -> email.login(address, "GoodPass1234"))
        .isInstanceOf(BusinessException.class);
    assertThat(count("select auth_version from user_account where id=?", user)).isEqualTo(1);
  }

  @Test
  void repeatedAndConcurrentConfirmationCreditsOnlyOnce() throws Exception {
    var prepared = payments.prepare(user, product(), null, "/settings");
    String order = prepared.get("orderId").toString();
    long amount = ((Number) prepared.get("amount")).longValue();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> payments.confirm(user, "key-" + order, order, amount));
      var second = pool.submit(() -> payments.confirm(user, "key-" + order, order, amount));
      assertThat(first.get(10, TimeUnit.SECONDS).get("status")).isEqualTo("APPROVED");
      assertThat(second.get(10, TimeUnit.SECONDS).get("status")).isEqualTo("APPROVED");
    }
    assertThat(count("select balance from heart_wallet where user_id=?", user)).isEqualTo(1);
    assertThat(
            count(
                "select count(*) from heart_transaction where user_id=? and"
                    + " transaction_type='CHARGE'",
                user))
        .isEqualTo(1);
    verify(toss, times(1)).confirm(anyString(), eq(order), eq(amount));
  }

  @Test
  void paymentRejectsOtherUserWrongAmountAndForgedProviderResponse() {
    var p = payments.prepare(user, product(), null, "//evil.example");
    String order = p.get("orderId").toString();
    long amount = ((Number) p.get("amount")).longValue();
    assertThatThrownBy(() -> payments.confirm(other, ("key-" + user), order, amount))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> payments.confirm(user, ("key-" + user), order, amount + 1))
        .isInstanceOf(BusinessException.class);
    verify(toss, never()).confirm(anyString(), anyString(), anyLong());
    when(toss.confirm(anyString(), anyString(), anyLong()))
        .thenReturn(provider(("key-" + user), "different-order", amount, "DONE"));
    assertThatThrownBy(() -> payments.confirm(user, ("key-" + user), order, amount))
        .isInstanceOf(BusinessException.class);
    assertThat(count("select count(*) from heart_transaction where user_id=?", user)).isZero();
    assertThat(
            db.queryForObject(
                "select return_path from payment_transaction where order_id=?",
                String.class,
                order))
        .isEqualTo("/home");
  }

  @Test
  void refundReversesCreditOnceAndChecksUnspentBalance() {
    var p = payments.prepare(user, product(), null, "/home");
    String order = p.get("orderId").toString();
    long amount = ((Number) p.get("amount")).longValue();
    payments.confirm(user, "key-" + order, order, amount);
    long id = count("select id from payment_transaction where order_id=?", order);
    db.update("update heart_wallet set balance=0 where user_id=?", user);
    assertThatThrownBy(() -> payments.refund(id, "사용자 요청")).isInstanceOf(BusinessException.class);
    verify(toss, never()).cancel(anyString(), anyString(), anyString());
    db.update("update heart_wallet set balance=1 where user_id=?", user);
    when(toss.cancel(anyString(), eq(order), anyString()))
        .thenReturn(provider("key-" + order, order, amount, "CANCELED"));
    payments.refund(id, "사용자 요청");
    payments.refund(id, "사용자 요청");
    assertThat(count("select balance from heart_wallet where user_id=?", user)).isZero();
    assertThat(
            count(
                "select count(*) from heart_transaction where user_id=? and"
                    + " transaction_type='REFUND'",
                user))
        .isEqualTo(1);
  }

  @Test
  void webhookReadsProviderInsteadOfTrustingCaller() {
    var p = payments.prepare(user, product(), null, "/home");
    String order = p.get("orderId").toString();
    long amount = ((Number) p.get("amount")).longValue();
    when(toss.get(("key-" + user))).thenReturn(provider(("key-" + user), order, amount, "READY"));
    payments.webhook(("key-" + user), order);
    assertThat(count("select count(*) from heart_transaction where user_id=?", user)).isZero();
    when(toss.get(("key-" + user))).thenReturn(provider(("key-" + user), order, amount, "DONE"));
    payments.webhook(("key-" + user), order);
    payments.webhook(("key-" + user), order);
    assertThat(count("select balance from heart_wallet where user_id=?", user)).isEqualTo(1);
  }

  @Test
  void matchingRequiresReviewAndRespectsInterval() {
    assertThat(policy.eligible(user)).isFalse();
    eligible(user);
    assertThat(policy.eligible(user)).isTrue();
    assertThat(policy.due(user)).isTrue();
    assertThat(policy.needsGuarantee(user)).isTrue();
    db.update(
        "update matching_profile set"
            + " last_auto_matched_at=CURRENT_TIMESTAMP,s_grade_guaranteed_match_count=2 where"
            + " user_id=?",
        user);
    assertThat(policy.due(user)).isFalse();
    assertThat(policy.needsGuarantee(user)).isFalse();
    db.update(
        "update matching_profile set last_auto_matched_at=? where user_id=?",
        Timestamp.from(Instant.now().minusSeconds(49 * 3600)),
        user);
    assertThat(policy.due(user)).isTrue();
  }

  @Test
  void expiryCountsOnlyNonResponderOnce() {
    eligible(user);
    eligible(other);
    long match = match("PENDING");
    db.update(
        "update match_proposal set expires_at=?,user_a_decision='ACCEPTED' where id=?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        match);
    policy.expire();
    policy.expire();
    assertThat(count("select no_response_count from matching_profile where user_id=?", user))
        .isZero();
    assertThat(count("select no_response_count from matching_profile where user_id=?", other))
        .isEqualTo(1);
    assertThat(
            db.queryForObject("select status from match_proposal where id=?", String.class, match))
        .isEqualTo("EXPIRED");
  }

  @Test
  void blockClosesExistingConversationAndExcludesPair() {
    long room = room();
    moderation.block(user, other);
    moderation.block(user, other);
    assertThat(policy.pairAllowed(user, other)).isFalse();
    assertThatThrownBy(() -> chat.room(room, other)).isInstanceOf(BusinessException.class);
    assertThat(
            count(
                "select count(*) from block_relation where blocker_user_id=? and blocked_user_id=?",
                user,
                other))
        .isEqualTo(1);
  }

  @Test
  void readReceiptsAndDeletionEnforceSenderOwnership() {
    long room = room();
    long message = message(room, user, "안녕하세요");
    messages.read(room, other);
    assertThat(
            db.queryForObject(
                "select read_at from chat_message where id=?", Timestamp.class, message))
        .isNotNull();
    assertThatThrownBy(() -> messages.delete(room, message, other))
        .isInstanceOf(BusinessException.class);
    messages.delete(room, message, user);
    assertThat(
            db.queryForObject("select content from chat_message where id=?", String.class, message))
        .isNull();
  }

  @Test
  void reportPreservesMessageEvidenceAndRejectsUnrelatedMessage() {
    long room = room();
    long message = message(room, other, "신고할 메시지 증거");
    assertThatThrownBy(
            () ->
                moderation.report(
                    member("외부인", "FEMALE"), other, "신고 사유입니다", "CHAT_MESSAGE", message))
        .isInstanceOf(BusinessException.class);
    moderation.report(user, other, "신고 사유입니다", "CHAT_MESSAGE", message);
    db.update("update chat_message set content=null where id=?", message);
    assertThat(
            db.queryForObject(
                "select message_snapshot from report where reporter_user_id=?", String.class, user))
        .isEqualTo("신고할 메시지 증거");
  }

  @Test
  void verificationFilesArePrivateAndRejectionStopsMatching() {
    eligible(user);
    assertThatThrownBy(
            () ->
                reviews.upload(
                    user,
                    "IDENTITY",
                    new MockMultipartFile(
                        "file", "bad.pdf", "application/pdf", "not a pdf".getBytes())))
        .isInstanceOf(BusinessException.class);
    reviews.upload(
        user,
        "EMPLOYMENT",
        new MockMultipartFile(
            "file", "proof.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes()));
    long id = count("select id from user_verification_document where user_id=?", user);
    assertThatThrownBy(() -> reviews.file(id, other, false)).isInstanceOf(BusinessException.class);
    assertThat(reviews.file(id, user, false).exists()).isTrue();
    reviews.review(id, false, "REJECTED", "다시 촬영해주세요", 1L);
    assertThat(policy.eligible(user)).isFalse();
    reviews.delete(user, id);
    assertThat(count("select count(*) from user_verification_document where id=?", id)).isZero();
  }

  @Test
  void optionalDocumentsStillRequireIntroPhotos() {
    eligible(user);
    db.update("delete from profile_photo where user_id=?", user);
    assertThatThrownBy(() -> onboarding.complete(user))
        .isInstanceOf(BusinessException.class).hasMessageContaining("소개와 프로필 사진");
  }

  @Test
  void onboardingAllowsOptionalDocumentsAndKeepsActualSubmissionStatus() {
    eligible(user);

    var initial = onboarding.status(user);
    assertThat(initial.profileCompleted()).isTrue();
    assertThat(initial.introPhotoCompleted()).isTrue();
    assertThat(initial.onboardingCompleted()).isFalse();
    db.update("update user_account set approval_status='PENDING' where id=?", user);
    var withoutDocuments = onboarding.complete(user);
    assertThat(withoutDocuments.onboardingCompleted()).isTrue();
    assertThat(withoutDocuments.employmentDocumentSubmitted()).isFalse();
    assertThat(withoutDocuments.educationDocumentSubmitted()).isFalse();
    assertThat(db.queryForObject("select approval_status from user_account where id=?", String.class, user)).isEqualTo("PENDING");
    assertThat(policy.eligible(user)).isFalse();

    var proof =
        new MockMultipartFile(
            "file", "proof.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes());
    reviews.upload(user, "EMPLOYMENT", proof);
    long rejectedEmployment =
        count(
            "select max(id) from user_verification_document where user_id=? and document_type='EMPLOYMENT'",
            user);
    reviews.review(rejectedEmployment, false, "REJECTED", "직업 서류를 다시 제출해주세요.", 1L);
    assertThat(onboarding.status(user).employmentDocumentSubmitted()).isFalse();
    db.update("update user_account set onboarding_completed_at=null where id=?", user);
    assertThat(onboarding.complete(user).onboardingCompleted()).isTrue();

    reviews.upload(user, "EMPLOYMENT", proof);
    long approvedEmployment =
        count(
            "select max(id) from user_verification_document where user_id=? and document_type='EMPLOYMENT'",
            user);
    reviews.review(approvedEmployment, false, "APPROVED", null, 1L);
    reviews.upload(user, "EMPLOYMENT", proof);
    long latestRejectedEmployment =
        count(
            "select max(id) from user_verification_document where user_id=? and document_type='EMPLOYMENT'",
            user);
    reviews.review(latestRejectedEmployment, false, "REJECTED", "최신 서류가 흐립니다.", 1L);
    assertThat(onboarding.status(user).employmentDocumentSubmitted()).isFalse();

    reviews.upload(user, "EMPLOYMENT", proof);
    db.update("update user_account set onboarding_completed_at=null where id=?", user);
    var oneDocument = onboarding.complete(user);
    assertThat(oneDocument.onboardingCompleted()).isTrue();
    assertThat(oneDocument.employmentDocumentSubmitted()).isTrue();
    assertThat(oneDocument.educationDocumentSubmitted()).isFalse();
    reviews.upload(user, "EDUCATION", proof);

    assertThat(onboarding.complete(user).onboardingCompleted()).isTrue();
    assertThat(onboarding.complete(user).educationDocumentSubmitted()).isTrue();
    assertThat(
            db.queryForObject(
                "select onboarding_completed_at is not null from user_account where id=?",
                Boolean.class,
                user))
        .isTrue();
    assertThat(onboarding.complete(user).onboardingCompleted()).isTrue();
  }

  @Test
  void profileStoresOrderedStructuredActivityRegions() {
    var regions =
        List.of(
            new UserProfileService.ActivityRegionValue("SEOUL_MAPO", "서울 마포구"),
            new UserProfileService.ActivityRegionValue("SEOUL_YEONGDEUNGPO", "서울 영등포구"));
    var command =
        new UserProfileService.ProfileUpdateCommand(
            "여름",
            java.time.LocalDate.of(1995, 1, 1),
            com.oao.backend.user.domain.UserAccount.Gender.FEMALE,
            "서비스 기획자",
            165,
            "AVERAGE",
            "NON_SMOKER",
            "SOCIAL",
            "NONE",
            "ENFP",
            "대학교 졸업",
            "서울 마포구",
            regions,
            List.of("헬스"));

    var saved = profiles.updateProfile(user, command);

    assertThat(saved.activityRegion()).isEqualTo("서울 마포구");
    assertThat(saved.activityRegions()).containsExactlyElementsOf(regions);
    assertThat(
            db.queryForObject(
                "select region_label from user_activity_region where user_id=? and display_order=1",
                String.class,
                user))
        .isEqualTo("서울 영등포구");
  }

  @Test
  void matchingPreferenceDoesNotBypassProfileEligibility() {
    settings.update(user, null, null, List.of(), false, true);
    assertThat(settings.settings(user).get("matchingEnabled")).isEqualTo(true);
    assertThat(policy.eligible(user)).isFalse();
    assertThatThrownBy(() -> settings.setMatchingEnabled(user, true)).isInstanceOf(BusinessException.class);
    db.update("delete from matching_profile where user_id=?", user);
    eligible(user);
    settings.update(user, true, "함께 성장", List.of("다정함", "성실함", "유머"), false, true);
    assertThat(settings.settings(user).get("personalityKeywords")).isEqualTo("다정함,성실함,유머");
    settings.update(user, false, "함께 성장", List.of("다정함"), false, true);
    assertThat(policy.eligible(user)).isFalse();
  }

  @Test
  void anonymousAndForgedIdentityHeadersCannotReadPrivateSettings() throws Exception {
    var client = HttpClient.newHttpClient();
    String url = "http://localhost:" + port + "/me/settings";
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(url))
                        .header("X-User-Id", Long.toString(user))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void newMatchingPreferenceSurvivesFirstPhotosButApprovalStillGatesMatching() {
    db.update("update user_account set approval_status='PENDING' where id=?", user);
    assertThat(settings.settings(user).get("matchingEnabled")).isEqualTo(true);
    assertThat(com.oao.backend.matching.domain.MatchingProfile.create(user).isMatchingEnabled()).isTrue();
    var photo = new MockMultipartFile("photos", "profile.png", "image/png",
        new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0});
    profilePhotos.saveIntroPhoto(user, "함께 즐거운 시간을 보내고 싶어요.", List.of(photo, photo), null);
    assertThat(settings.settings(user).get("matchingEnabled")).isEqualTo(true);
    settings.update(user, null, null, null, false, true);
    assertThat(policy.eligible(user)).isFalse();
    db.update("insert into user_profile(user_id,phone_verified_at,created_at,updated_at) values (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", user);
    db.update("update user_account set approval_status='APPROVED' where id=?", user);
    assertThat(policy.eligible(user)).isFalse();
    db.update("update profile_photo set review_status='APPROVED' where user_id=?", user);
    assertThat(policy.eligible(user)).isTrue();

    settings.update(other, false, null, null, true, true);
    profilePhotos.saveIntroPhoto(other, "함께 즐거운 시간을 보내고 싶어요.", List.of(photo, photo), null);
    assertThat(settings.settings(other).get("matchingEnabled")).isEqualTo(false);
  }

  @Test
  void profileMatchingDetailsAndAccountPreferencesDoNotOverwriteEachOther() {
    settings.update(user, false, "함께 성장", List.of("다정한", "솔직한", "활동적인"), false, false);
    settings.updateMatchingDetails(user, "서로 응원하는 연애", null);
    assertThat(settings.matchingDetails(user).get("personalityKeywords")).isEqualTo("다정한,솔직한,활동적인");
    settings.updateMatchingDetails(user, null, List.of(" 다정한 ", "다정한", "긍정적인", "차분한"));
    assertThat(settings.matchingDetails(user).get("datingStyle")).isEqualTo("서로 응원하는 연애");
    assertThat(settings.settings(user)).containsEntry("matchingEnabled", false)
        .containsEntry("alimtalkEnabled", false).containsEntry("messageNotifications", false);
    settings.update(user, true, null, null, true, false);
    assertThat(settings.matchingDetails(user)).containsEntry("datingStyle", "서로 응원하는 연애")
        .containsEntry("personalityKeywords", "다정한,긍정적인,차분한");
    assertThatThrownBy(() -> settings.updateMatchingDetails(user, "a".repeat(1001), null))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> settings.updateMatchingDetails(user, "저장되면 안 되는 값", List.of("하나", "둘", "셋", "넷")))
        .isInstanceOf(BusinessException.class);
    assertThat(settings.matchingDetails(user).get("datingStyle")).isEqualTo("서로 응원하는 연애");
    settings.updateMatchingDetails(user, "", List.of());
    assertThat(settings.matchingDetails(user)).containsEntry("datingStyle", "").containsEntry("personalityKeywords", "");
  }

  @Test
  void automaticMatchingNeedsRelationshipDetailsEvenWithDefaultPreferenceOn() {
    db.update("update matching_profile set matching_enabled=false");
    eligible(user);
    eligible(other);
    db.update("update matching_profile set dating_style=null,personality_keywords=null where user_id=?", user);
    assertThat(settings.settings(user)).containsEntry("matchingEnabled", true).containsEntry("matchingProfileComplete", false);
    assertThat(autoMatching.runAutoMatching().createdCount()).isZero();
    assertThatThrownBy(() -> settings.setMatchingEnabled(user, true)).isInstanceOf(BusinessException.class);
    settings.updateMatchingDetails(user, "서로 응원하는 연애", List.of("다정한", "솔직한"));
    assertThatThrownBy(() -> settings.setMatchingEnabled(user, true)).isInstanceOf(BusinessException.class);
    settings.updateMatchingDetails(user, null, List.of("다정한", "솔직한", "차분한"));
    assertThat(settings.setMatchingEnabled(user, true)).containsEntry("matchingEnabled", true).containsEntry("matchingProfileComplete", true);
    assertThat(autoMatching.runAutoMatching().createdCount()).isEqualTo(1);
    settings.setMatchingEnabled(user, false);
    settings.update(user, null, null, null, false, false);
    assertThat(settings.settings(user)).containsEntry("matchingEnabled", false).containsEntry("alimtalkEnabled", false);
    settings.updateMatchingDetails(user, "", null);
    assertThatThrownBy(() -> settings.update(user, true, null, null, true, true)).isInstanceOf(BusinessException.class);
    assertThat(settings.settings(user)).containsEntry("matchingEnabled", false);
  }

  @Test
  void withdrawalRemovesCredentialsAndClosesConversations() {
    eligible(user);
    long room = room();
    String address = address();
    String start = email.start(address, "GoodPass1234", "LINK", user).get("challengeId").toString();
    email.verify(start, codes.get(address), null, user);
    settings.withdraw(user);
    assertThatThrownBy(() -> email.login(address, "GoodPass1234"))
        .isInstanceOf(BusinessException.class);
    assertThat(db.queryForObject("select status from user_account where id=?", String.class, user))
        .isEqualTo("DELETED");
    assertThatThrownBy(() -> chat.room(room, other)).isInstanceOf(BusinessException.class);
    assertThat(count("select count(*) from user_profile where user_id=?", user)).isZero();
  }

  @Test
  void kakaoLinkRequiresExplicitIdentityAndCannotMergeOtherMembers() {
    String address = address();
    String start = email.start(address, "GoodPass1234", "LINK", user).get("challengeId").toString();
    email.verify(start, codes.get(address), null, user);
    String provider = "kakao-" + UUID.randomUUID();
    long independent = kakao.resolveVerifiedIdentity(provider, address, null).getId();
    assertThat(independent).isNotEqualTo(user);
    assertThatThrownBy(() -> kakao.resolveVerifiedIdentity(provider, address, user))
        .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    String newProvider = "kakao-" + UUID.randomUUID();
    assertThat(kakao.resolveVerifiedIdentity(newProvider, address, user).getId()).isEqualTo(user);
    assertThat(kakao.resolveVerifiedIdentity(newProvider, address, null).getId()).isEqualTo(user);
    assertThatThrownBy(
            () -> kakao.resolveVerifiedIdentity("another-" + UUID.randomUUID(), address, user))
        .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
  }

  @Test
  void recoveryFindsAnApprovedOrderWithoutALocallySavedProviderKey() {
    var p = payments.prepare(user, product(), null, "/home");
    String order = p.get("orderId").toString();
    long amount = ((Number) p.get("amount")).longValue();
    when(toss.getByOrder(order))
        .thenReturn(provider(("recovered-key-" + user), order, amount, "DONE"));
    long id = count("select id from payment_transaction where order_id=?", order);
    assertThat(payments.reconcile(id, user).get("status")).isEqualTo("APPROVED");
    assertThat(count("select balance from heart_wallet where user_id=?", user)).isEqualTo(1);
  }

  @Test
  void abandonedOrderCanBeCancelledAndLateApprovalIsRefunded() {
    var p = payments.prepare(user, product(), null, "/home");
    String order = p.get("orderId").toString();
    long amount = ((Number) p.get("amount")).longValue();
    long id = count("select id from payment_transaction where order_id=?", order);
    payments.cancelUnpaid(id, user);
    assertThatThrownBy(() -> payments.confirm(user, ("late-key-" + user), order, amount))
        .isInstanceOf(BusinessException.class);
    when(toss.get(("late-key-" + user)))
        .thenReturn(provider(("late-key-" + user), order, amount, "DONE"));
    when(toss.cancel(eq(("late-key-" + user)), eq(order), anyString()))
        .thenReturn(provider(("late-key-" + user), order, amount, "CANCELED"));
    payments.webhook(("late-key-" + user), order);
    assertThat(count("select count(*) from heart_transaction where user_id=?", user)).isZero();
    assertThat(
            db.queryForObject(
                "select refunded_at from payment_transaction where id=?", Timestamp.class, id))
        .isNotNull();
  }

  @Test
  void autoMatchingWaitsForGuaranteedGradeAndWillNotRunAgainBefore48Hours() {
    db.update("update matching_profile set matching_enabled=false");
    eligible(user);
    eligible(other);
    db.update("update user_account set grade='A' where id=?", other);
    assertThat(autoMatching.runAutoMatching().createdCount()).isZero();
    db.update("update user_account set grade='S' where id=?", other);
    assertThat(autoMatching.runAutoMatching().createdCount()).isEqualTo(1);
    assertThat(
            count(
                "select s_grade_guaranteed_match_count from matching_profile where user_id=?",
                user))
        .isEqualTo(1);
    assertThat(autoMatching.runAutoMatching().createdCount()).isZero();
  }

  @Test
  void meetingApprovalReservesCapacityAndFreeAdmissionNeedsNoCheckout() {
    long event = event(0, 1);
    long a = application(event, user);
    long b = application(event, other);
    adminMeetings.changeApplicationStatus(
        event,
        a,
        com.oao.backend.meeting.domain.MeetingApplication.MeetingApplicationStatus.APPROVED,
        "승인");
    assertThat(
            db.queryForObject(
                "select payment_status from meeting_application where id=?", String.class, a))
        .isEqualTo("PAID");
    assertThatThrownBy(
            () ->
                adminMeetings.changeApplicationStatus(
                    event,
                    b,
                    com.oao.backend.meeting.domain.MeetingApplication.MeetingApplicationStatus
                        .APPROVED,
                    "승인"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void meetingPaymentCanOnlyConfirmAnApprovedApplicationAndCannotBeManuallyOverridden() {
    long event = event(10000, 10);
    long a = application(event, user);
    assertThatThrownBy(() -> payments.prepare(user, null, a, "/meetings/" + event))
        .isInstanceOf(BusinessException.class);
    adminMeetings.changeApplicationStatus(
        event,
        a,
        com.oao.backend.meeting.domain.MeetingApplication.MeetingApplicationStatus.APPROVED,
        "승인");
    var p = payments.prepare(user, null, a, "/meetings/" + event);
    String order = p.get("orderId").toString();
    payments.confirm(user, "meeting-" + order, order, 10000);
    assertThat(count("select count(*) from heart_transaction where user_id=?", user)).isZero();
    assertThat(
            db.queryForObject(
                "select payment_status from meeting_application where id=?", String.class, a))
        .isEqualTo("PAID");
    assertThatThrownBy(
            () ->
                adminMeetings.changePaymentStatus(
                    event,
                    a,
                    com.oao.backend.meeting.domain.MeetingApplication.MeetingPaymentStatus.REFUNDED,
                    "수동 변경"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> meetings.cancel(event, user)).isInstanceOf(BusinessException.class);
  }

  @Autowired org.springframework.session.jdbc.JdbcIndexedSessionRepository sessions;
  @Autowired com.oao.backend.admin.service.AdminAuthService adminAuth;
  @Autowired com.oao.backend.user.repository.AdminUserRepository admins;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

  @Test
  void emailLoginUsesTheSameFailureForUnknownEmailAndWrongPassword() {
    String registered = address();
    String start = email.start(registered, "GoodPass1234", "LINK", user).get("challengeId").toString();
    email.verify(start, codes.get(registered), null, user);

    assertThatThrownBy(() -> email.login(address(), "GoodPass1234"))
        .isInstanceOf(BusinessException.class)
        .extracting("status", "message")
        .containsExactly(org.springframework.http.HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요.");
    assertThatThrownBy(() -> email.login(registered, "WrongPass1234"))
        .isInstanceOf(BusinessException.class)
        .extracting("status", "message")
        .containsExactly(org.springframework.http.HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요.");
  }

  @Test
  void persistentMemberSessionSurvivesRepositoryRecreationAndRenewsCookie() throws Exception {
    String address = address();
    String start = email.start(address, "GoodPass1234", "LINK", user).get("challengeId").toString();
    email.verify(start, codes.get(address), null, user);
    var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String base = "http://localhost:" + port;
    var response = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/email/login"))
        .header("Origin", "http://localhost:3000").header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + address + "\",\"password\":\"GoodPass1234\"}"))
        .build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("set-cookie").toString())
        .contains("Max-Age=2592000", "HttpOnly", "SameSite=Lax");
    String encoded = cookies.getCookieStore().getCookies().stream()
        .filter(c -> c.getName().equals("OAO_SESSION")).findFirst().orElseThrow().getValue().replace("\"", "");
    String id = new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
    // A fresh repository has no in-memory state from the login request.
    @SuppressWarnings("unchecked")
    org.springframework.session.SessionRepository<org.springframework.session.Session> fresh =
        (org.springframework.session.SessionRepository<org.springframework.session.Session>)
        (org.springframework.session.SessionRepository<?>) new org.springframework.session.jdbc.JdbcIndexedSessionRepository(db, new org.springframework.transaction.support.TransactionTemplate(transactionManager));
    var restored = fresh.findById(id);
    assertThat(restored).isNotNull();
    assertThat(restored.getMaxInactiveInterval()).isEqualTo(java.time.Duration.ofDays(30));
    var context = (org.springframework.security.core.context.SecurityContext) restored.getAttribute("SPRING_SECURITY_CONTEXT");
    assertThat(((KakaoPrincipal) context.getAuthentication().getPrincipal()).getUserId()).isEqualTo(user);
    var access = client.send(HttpRequest.newBuilder(URI.create(base + "/me/settings")).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(access.statusCode()).isEqualTo(200);
    assertThat(access.headers().allValues("set-cookie").toString()).contains("Max-Age=2592000");
    restored = fresh.findById(id);
    restored.setLastAccessedTime(Instant.now().minusSeconds(30L * 86400 + 1));
    fresh.save(restored);
    assertThat(client.send(HttpRequest.newBuilder(URI.create(base + "/me/settings")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
  }

  @Test
  void administratorExpiryDoesNotExtendWithMemberActivityAndPasswordChangesRevokeAccess() {
    var admin = admins.saveAndFlush(com.oao.backend.user.domain.AdminUser.createEmailAdmin(
        address(), "테스트 관리자", "SUPER_ADMIN", encoder.encode("AdminPass1234")));
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    adminAuth.login(admin.getEmail(), "AdminPass1234", request);
    assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(43200);
    assertThat(adminAuth.findCurrentAdminOrNull(request)).isNotNull();
    PersistentSessionPolicy.memberSignedIn(request);
    request.getSession().setAttribute("OAO_ADMIN_LAST_ACCESS", System.currentTimeMillis() - 43200001L);
    assertThat(adminAuth.findCurrentAdminOrNull(request)).isNull();
    assertThat(request.getSession().getAttribute(PersistentSessionPolicy.MEMBER_SESSION)).isEqualTo(true);
    assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(2592000);
    adminAuth.login(admin.getEmail(), "AdminPass1234", request);
    db.update("update admin_user set password_hash=? where id=?", encoder.encode("ChangedPass1234"), admin.getId());
    assertThat(adminAuth.findCurrentAdminOrNull(request)).isNull();
  }

  @Test
  void httpsCookieIsHostOnlySecureAndKakaoLoginUsesMemberLifetime() throws Exception {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setSecure(true);
    request.setServerName("api.oao365.com");
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    var principal = new KakaoPrincipal(user, "kakao-fixture", null, "테스트",
        com.oao.backend.user.domain.UserAccount.ApprovalStatus.APPROVED,
        com.oao.backend.user.domain.UserAccount.MemberGrade.S, Map.of(), List.of());
    var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    new OAuth2LoginSuccessHandler("https://www.oao365.com/oauth/success")
        .onAuthenticationSuccess(request, response, authentication);
    assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(2592000);
    var cookieResponse = new org.springframework.mock.web.MockHttpServletResponse();
    new PersistentSessionPolicy().cookieSerializer().writeCookieValue(
        new org.springframework.session.web.http.CookieSerializer.CookieValue(request, cookieResponse, request.getSession().getId()));
    assertThat(cookieResponse.getHeader("Set-Cookie"))
        .contains("Secure", "HttpOnly", "SameSite=Lax", "Max-Age=2592000")
        .doesNotContain("Domain=");
  }

  @Test
  void administratorSessionIsPersistentAndLogoutRemovesIt() throws Exception {
    var admin = admins.saveAndFlush(com.oao.backend.user.domain.AdminUser.createEmailAdmin(
        address(), "테스트 관리자", "SUPER_ADMIN", encoder.encode("AdminPass1234")));
    var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String base = "http://localhost:" + port;
    var login = client.send(HttpRequest.newBuilder(URI.create(base + "/admin/auth/login"))
        .header("Origin", "http://localhost:3000").header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + admin.getEmail() + "\",\"password\":\"AdminPass1234\"}"))
        .build(), HttpResponse.BodyHandlers.ofString());
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(login.headers().allValues("set-cookie").toString()).contains("Max-Age=43200", "HttpOnly");
    var check = HttpRequest.newBuilder(URI.create(base + "/admin/auth/me")).GET().build();
    assertThat(client.send(check, HttpResponse.BodyHandlers.ofString()).body()).contains("\"admin\":true");
    var logout = client.send(HttpRequest.newBuilder(URI.create(base + "/admin/auth/logout"))
        .header("Origin", "http://localhost:3000").POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(logout.statusCode()).isEqualTo(200);
    assertThat(logout.headers().allValues("set-cookie").toString()).contains("Max-Age=0");
    assertThat(client.send(check, HttpResponse.BodyHandlers.ofString()).body()).contains("\"admin\":false");
  }

  @Test
  void browserSessionRejectsCrossOriginMutationAndExpiresAfterReset() throws Exception {
    String address = address();
    String start = email.start(address, "GoodPass1234", "LINK", user).get("challengeId").toString();
    email.verify(start, codes.get(address), null, user);
    var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
    var client = HttpClient.newBuilder().cookieHandler(cookies).build();
    String base = "http://localhost:" + port;
    var login =
        HttpRequest.newBuilder(URI.create(base + "/auth/email/login"))
            .header("Origin", "http://localhost:3000")
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"" + address + "\",\"password\":\"GoodPass1234\"}"))
            .build();
    assertThat(client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(200);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "/me/settings")).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "/auth/logout"))
                        .header("Origin", "https://untrusted.example")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(403);
    db.update("update user_account set auth_version=auth_version+1 where id=?", user);
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(base + "/me/settings")).GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
  }

  long member(String name, String gender) {
    db.update(
        "insert into"
            + " user_account(name,status,approval_status,grade,gender,birth_date,created_at,updated_at)"
            + " values"
            + " (?,'ACTIVE','APPROVED','S',?,'1995-01-01',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        name,
        gender);
    return count("select max(id) from user_account");
  }

  void eligible(long id) {
    db.update(
        "insert into user_profile(user_id,phone_number,phone_verified_at,created_at,updated_at)"
            + " values (?,'01012345678',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id);
    db.update(
        "insert into"
            + " matching_profile(user_id,matching_enabled,dating_style,personality_keywords,created_at,updated_at)"
            + " values (?,true,'함께 성장','다정함,성실함,유머',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id);
    db.update(
        "update user_profile set"
            + " height_cm=170,job='개발자',education='대졸',activity_region='서울',body_type='AVERAGE',smoking_status='NON_SMOKER',drinking_status='SOCIAL',religion='NONE',mbti='ENFP'"
            + " where user_id=?",
        id);
    db.update("update matching_profile set job_intro='산책과 대화를 좋아합니다.' where user_id=?", id);
    db.update("insert into user_hobby(user_id,hobby_id) values (?,1)", id);
    for (int i = 0; i < 2; i++)
      db.update(
          "insert into"
              + " profile_photo(user_id,image_url,display_order,review_status,created_at,updated_at)"
              + " values"
              + " (?,'/uploads/fixture.jpg',?,'APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          id,
          i);
  }

  long match(String status) {
    db.update(
        "insert into"
            + " match_proposal(match_type,user_a_id,user_b_id,status,is_s_grade_guaranteed,user_a_decision,user_b_decision,matched_at,expires_at,created_at,updated_at)"
            + " values"
            + " ('AUTO',?,?,?,false,'PENDING','PENDING',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        other,
        status,
        Timestamp.from(Instant.now().plusSeconds(86400)));
    return count("select max(id) from match_proposal");
  }

  long room() {
    long match = match("ACCEPTED");
    db.update(
        "insert into chat_room(match_id,status,created_at,updated_at) values"
            + " (?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        match);
    return count("select max(id) from chat_room");
  }

  long message(long room, long sender, String content) {
    db.update(
        "insert into"
            + " chat_message(chat_room_id,sender_user_id,message_type,content,created_at,updated_at)"
            + " values (?,?,'TEXT',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        room,
        sender,
        content);
    return count("select max(id) from chat_message");
  }

  long count(String sql, Object... args) {
    return db.queryForObject(sql, Long.class, args);
  }

  long product() {
    return count("select min(id) from heart_product");
  }

  long event(int price, int capacity) {
    db.update(
        "insert into"
            + " meeting_event(title,description,image_url,event_date_time,price_amount,capacity,status,created_at,updated_at)"
            + " values ('함께하는 모임','즐거운 모임"
            + " 설명입니다','/fixture.png',?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        java.time.LocalDateTime.now().plusDays(7),
        price,
        capacity,
        "OPEN");
    return count("select max(id) from meeting_event");
  }

  long application(long event, long id) {
    db.update(
        "insert into"
            + " meeting_application(meeting_event_id,user_id,application_status,payment_status,created_at,updated_at)"
            + " values (?,?,'APPLIED','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        event,
        id);
    return count("select max(id) from meeting_application");
  }

  String address() {
    return UUID.randomUUID() + "@example.test";
  }

  static Map<String, Object> provider(String key, String order, long amount, String status) {
    return Map.of(
        "paymentKey",
        key,
        "orderId",
        order,
        "totalAmount",
        amount,
        "currency",
        "KRW",
        "status",
        status);
  }
}
