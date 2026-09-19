package com.oao.backend.matching.service;

import com.oao.backend.common.*;
import com.oao.backend.user.service.ModerationService;
import java.sql.Timestamp;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingPolicyService {
  private final DbRows db;
  private final ModerationService moderation;
  private final int interval, guarantee;

  public MatchingPolicyService(
      DbRows db,
      ModerationService moderation,
      @Value("${oao.matching.auto-match-interval-hours:48}") int interval,
      @Value("${oao.matching.s-grade-guaranteed-auto-match-count:2}") int guarantee) {
    this.db = db;
    this.moderation = moderation;
    this.interval = interval;
    this.guarantee = guarantee;
  }

  public boolean eligible(Long id) {
    return db.count(
                "select count(*) from matching_profile m join user_profile p on p.user_id=m.user_id"
                    + " join user_account u on u.id=m.user_id where u.status='ACTIVE' and"
                    + " u.approval_status='APPROVED' and m.user_id=? and m.matching_enabled=true"
                    + " and p.phone_verified_at is not null",
                id)
            > 0
        && db.count(
                "select count(*) from profile_photo where user_id=? and review_status='APPROVED'",
                id)
            >= 2;
  }

  public boolean due(Long id) {
    return db.count(
            "select count(*) from matching_profile where user_id=? and (last_auto_matched_at is"
                + " null or last_auto_matched_at<=?)",
            id,
            Timestamp.from(Instant.now().minusSeconds(interval * 3600L)))
        > 0;
  }

  public boolean needsGuarantee(Long id) {
    return db.count(
            "select count(*) from matching_profile where user_id=? and"
                + " s_grade_guaranteed_match_count<?",
            id,
            guarantee)
        > 0;
  }

  public boolean pairAllowed(Long a, Long b) {
    return !moderation.excluded(a, b);
  }

  public void requirePair(Long a, Long b) {
    moderation.requireAllowed(a, b);
    if (db.count("select count(*) from user_account where id in (?,?) and status='ACTIVE'", a, b)
        != 2)
      throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "이용할 수 없는 회원입니다.");
  }

  public void lockRun() {
    db.jdbc.queryForList("select id from matching_run_lock where id=1 for update");
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  @Transactional
  public void expire() {
    var rows =
        db.jdbc.queryForList(
            "select id,user_a_id,user_b_id,user_a_decision,user_b_decision from match_proposal"
                + " where status='PENDING' and expires_at<=? for update",
            Timestamp.from(Instant.now()));
    for (var m : rows) {
      if (db.jdbc.update(
              "update match_proposal set"
                  + " status='EXPIRED',expired_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP"
                  + " where id=? and status='PENDING'",
              m.get("id"))
          == 0) continue;
      for (String side : new String[] {"a", "b"})
        if ("PENDING".equals(m.get("user_" + side + "_decision")))
          db.jdbc.update(
              "update matching_profile set no_response_count=no_response_count+1 where user_id=?",
              m.get("user_" + side + "_id"));
    }
  }
}
