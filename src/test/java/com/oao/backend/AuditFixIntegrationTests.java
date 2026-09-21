package com.oao.backend;

import static org.assertj.core.api.Assertions.*;
import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.service.MinimumAgePolicy;
import java.time.*;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=${OAO_AUDIT_DB_URL:jdbc:h2:mem:auditfix;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.driver-class-name=${OAO_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${OAO_TEST_DB_USER:sa}", "spring.datasource.password=${OAO_TEST_DB_PASSWORD:}",
    "spring.flyway.enabled=${OAO_TEST_FLYWAY:false}", "spring.jpa.hibernate.ddl-auto=none",
    "spring.config.import=", "oao.admin-bootstrap.enabled=false", "oao.dev-tools.enabled=false",
    "oao.alimtalk.enabled=false", "oao.admin.bootstrap.email=", "oao.admin.bootstrap.password="
})
@ContextConfiguration(initializers = WorkflowDatabaseInitializer.class)
class AuditFixIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired UserAccountRepository users;
  @Autowired PlatformTransactionManager transactions;
  @Autowired com.oao.backend.interest.service.UserInterestService interests;
  @Autowired com.oao.backend.matching.service.MatchingPolicyService policy;
  @Autowired com.oao.backend.matching.service.MatchDecisionService decisions;
  @Autowired com.oao.backend.user.service.UserApprovalService approvals;

  @Test void ageBoundaryUsesSeoulDateAndRejectsMissingFutureAndUnderage() {
    var before = new MinimumAgePolicy(Clock.fixed(Instant.parse("2026-09-21T14:59:59Z"), ZoneOffset.UTC));
    var birthday = new MinimumAgePolicy(Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneOffset.UTC));
    assertThat(before.eligible(LocalDate.of(2007, 9, 22))).isFalse();
    assertThat(birthday.eligible(LocalDate.of(2007, 9, 22))).isTrue();
    assertThat(birthday.eligible(LocalDate.of(2007, 9, 23))).isFalse();
    assertThat(birthday.eligible(null)).isFalse();
    assertThat(birthday.eligible(LocalDate.of(2030, 1, 1))).isFalse();
    assertThatThrownBy(() -> birthday.requireEligible(LocalDate.of(2020, 1, 1)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("만 19세");
    var leap = new MinimumAgePolicy(Clock.fixed(Instant.parse("2024-02-29T03:00:00Z"), ZoneOffset.UTC));
    assertThat(leap.latestBirthDate()).isEqualTo(LocalDate.of(2005, 2, 28));
  }

  @Test void dateOnlySurvivesJpaAndRawSqlAcrossTimeZonesAndTransactions() {
    TimeZone original = TimeZone.getDefault();
    var tx = new TransactionTemplate(transactions);
    try {
      for (String zone : new String[] {"UTC", "Asia/Seoul"}) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        for (LocalDate birth : new LocalDate[] {LocalDate.of(1995, 5, 20), LocalDate.of(2000, 2, 29), LocalDate.of(1999, 12, 31)}) {
          Long id = tx.execute(s -> {
            var user = UserAccount.createPending();
            user.updateBasicInfo("날짜테스트", birth);
            return users.saveAndFlush(user).getId();
          });
          for (int repeat = 0; repeat < 3; repeat++) {
            assertThat(db.queryForObject("select birth_date from user_account where id=?", LocalDate.class, id)).isEqualTo(birth);
            LocalDate read = tx.execute(s -> {
              var user = users.findById(id).orElseThrow();
              LocalDate value = user.getBirthDate();
              user.updateBasicInfo("날짜테스트", value);
              users.flush();
              return value;
            });
            assertThat(read).as("JVM %s repeat %s", zone, repeat).isEqualTo(birth);
          }
        }
      }
    } finally { TimeZone.setDefault(original); }
  }

  @Test void everyMutualIntentCombinationReusesRecommendationAndDoesNotChargeTwice() {
    for (var first : com.oao.backend.interest.domain.UserInterest.InterestType.values()) {
      for (var second : com.oao.backend.interest.domain.UserInterest.InterestType.values()) {
        long a = member("FEMALE"), b = member("MALE");
        long original = proposal(a, b);
        long duplicate = proposal(a, b);
        assertThat(interests.send(a, b, first, null).chatRoomCreated()).isFalse();
        var connected = interests.send(b, a, second, null);
        assertThat(connected.chatRoomCreated()).isTrue();
        assertThat(connected.matchId()).isEqualTo(original);
        assertThat(db.queryForObject("select status from match_proposal where id=?", String.class, original)).isEqualTo("ACCEPTED");
        assertThat(db.queryForObject("select status from match_proposal where id=?", String.class, duplicate)).isEqualTo("CLOSED");
        assertThat(db.queryForObject("select match_type from match_proposal where id=?", String.class, original)).isEqualTo("AUTO");
        var retry = interests.send(b, a, first, null);
        assertThat(retry.spentHearts()).isZero();
        assertThat(retry.chatRoomId()).isEqualTo(connected.chatRoomId());
        assertThat(db.queryForObject("select count(*) from heart_transaction where user_id in (?,?)", Long.class, a, b)).isEqualTo(2);
        assertThat(db.queryForObject("select balance from heart_wallet where user_id=?", Integer.class, a)).isEqualTo(first.name().equals("LIKE") ? 96 : 95);
        db.update("update match_proposal set expires_at='2000-01-01' where id in (?,?)", original, duplicate);
        policy.expire();
        assertThat(db.queryForObject("select no_response_count from matching_profile where user_id=?", Integer.class, a)).isZero();
      }
    }
  }

  @Test void underageAndMissingDatesCannotBeApprovedOrConnected() {
    for (LocalDate birth : new LocalDate[] {LocalDate.of(2020, 1, 1), null}) {
      long a = member("FEMALE"), b = member("MALE");
      long match = proposal(a, b);
      db.update("update user_account set birth_date=? where id=?", birth, a);
      assertThat(policy.eligible(a)).isFalse();
      assertThatThrownBy(() -> interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.LIKE, null)).isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> decisions.accept(match, b)).isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> approvals.approve(a, UserAccount.MemberGrade.S, null, "fixture")).isInstanceOf(BusinessException.class);
    }
  }

  @Test void simultaneousMutualSendsCreateOneRoomAndTwoLedgerEntries() throws Exception {
    long a = member("FEMALE"), b = member("MALE");
    long match = proposal(a, b);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> { start.await(); return interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.LIKE, null); });
      var second = pool.submit(() -> { start.await(); return interests.send(b, a, com.oao.backend.interest.domain.UserInterest.InterestType.EXPRESS, null); });
      start.countDown();
      var one = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
      var two = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(one.chatRoomCreated() ^ two.chatRoomCreated()).isTrue();
      assertThat(db.queryForObject("select count(*) from chat_room where match_id=?", Long.class, match)).isEqualTo(1);
      assertThat(db.queryForObject("select count(*) from heart_transaction where user_id in (?,?)", Long.class, a, b)).isEqualTo(2);
    }
  }

  @Test void publicRecipientAcceptanceIsFreeIdempotentAndRejectsOldConsent() {
    long a = member("FEMALE"), b = member("MALE");
    long original = proposal(a, b);
    var sent = interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.EXPRESS, null);
    var accepted = interests.acceptExpress(sent.profile().interestId(), b);
    assertThat(accepted.matchId()).isEqualTo(original);
    assertThat(accepted.spentHearts()).isZero();
    assertThat(interests.acceptExpress(sent.profile().interestId(), b).chatRoomId()).isEqualTo(accepted.chatRoomId());
    assertThat(db.queryForObject("select balance from heart_wallet where user_id=?", Integer.class, b)).isEqualTo(100);

    long c = member("FEMALE"), d = member("MALE");
    var rejected = interests.send(c, d, com.oao.backend.interest.domain.UserInterest.InterestType.EXPRESS, null);
    interests.rejectExpress(rejected.profile().interestId(), d);
    assertThat(interests.send(d, c, com.oao.backend.interest.domain.UserInterest.InterestType.LIKE, null).chatRoomCreated()).isFalse();
    assertThatThrownBy(() -> interests.acceptExpress(rejected.profile().interestId(), d)).isInstanceOf(BusinessException.class);
  }

  private long member(String gender) {
    var u = UserAccount.createPending();
    u.updateBasicInfo("가상회원", LocalDate.of(1995, 5, 20));
    u.updateGender(UserAccount.Gender.valueOf(gender));
    u.approve(UserAccount.MemberGrade.S, null);
    long id = users.saveAndFlush(u).getId();
    db.update("insert into user_profile(user_id,phone_verified_at,created_at,updated_at) values (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
    db.update("insert into matching_profile(user_id,matching_enabled,created_at,updated_at) values (?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
    for (int i = 0; i < 2; i++) db.update("insert into profile_photo(user_id,image_url,display_order,review_status,created_at,updated_at) values (?,'/fixture.jpg',?,'APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, i);
    db.update("insert into heart_wallet(user_id,balance,created_at,updated_at) values (?,100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
    return id;
  }

  @Test void expiredConsentIsHiddenAndNeedsAFreshExplicitAction() {
    long a = member("FEMALE"), b = member("MALE");
    long original = proposal(a, b);
    interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.LIKE, null);
    db.update("update user_interest set updated_at='2000-01-01' where sender_user_id=? and receiver_user_id=?", a, b);
    db.update("update match_proposal set expires_at='2001-01-01' where id=?", original);
    assertThat(interests.sent(a)).isEmpty();
    assertThat(interests.send(b, a, com.oao.backend.interest.domain.UserInterest.InterestType.EXPRESS, null).chatRoomCreated()).isFalse();
    var fresh = interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.LIKE, null);
    assertThat(fresh.chatRoomCreated()).isTrue();
    assertThat(fresh.matchId()).isNotEqualTo(original);
    assertThat(fresh.spentHearts()).isEqualTo(4);
    assertThat(interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.LIKE, null).spentHearts()).isZero();
  }

  private long proposal(long a, long b) {
    db.update("insert into match_proposal(match_type,user_a_id,user_b_id,status,is_s_grade_guaranteed,user_a_decision,user_b_decision,matched_at,expires_at,created_at,updated_at) values ('AUTO',?,?,'PENDING',false,'PENDING','PENDING',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", a, b, java.sql.Timestamp.from(Instant.now().plusSeconds(86400)));
    return db.queryForObject("select max(id) from match_proposal", Long.class);
  }
}
