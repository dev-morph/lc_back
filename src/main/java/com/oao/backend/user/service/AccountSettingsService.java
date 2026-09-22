package com.oao.backend.user.service;

import com.oao.backend.common.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSettingsService {
  private final DbRows db;
  private final VerificationReviewService reviews;

  public AccountSettingsService(DbRows db, VerificationReviewService reviews) {
    this.db = db;
    this.reviews = reviews;
  }

  public Map<String, Object> settings(Long id) {
    var row =
        db.one(
            "select id as user_id,name,approval_status,rejection_reason from user_account where"
                + " id=?",
            id);
    var emails = db.list("select email from email_credential where user_id=?", id);
    row.put("email", emails.isEmpty() ? null : emails.get(0).get("email"));
    row.put(
        "kakaoConnected", db.count("select count(*) from oauth_account where user_id=?", id) > 0);
    var matching =
        db.list(
            "select matching_enabled,dating_style,personality_keywords,no_response_count from"
                + " matching_profile where user_id=?",
            id);
    if (!matching.isEmpty()) row.putAll(matching.get(0));
    else row.put("matchingEnabled", true);
    var pref =
        db.list(
            "select alimtalk_enabled,message_notifications from user_preferences where user_id=?",
            id);
    row.put("alimtalkEnabled", true);
    row.put("messageNotifications", true);
    if (!pref.isEmpty()) row.putAll(pref.get(0));
    row.put(
        "approvedPhotoCount",
        db.count(
            "select count(*) from profile_photo where user_id=? and review_status='APPROVED'", id));
    return row;
  }

  @Transactional
  public void update(
      Long id,
      boolean enabled,
      String style,
      List<String> keywords,
      boolean alimtalk,
      boolean messages) {
    db.jdbc.queryForList("select id from user_account where id=? for update", id);
    // This records the member's preference. MatchingPolicyService and
    // MatchingCandidateService enforce approval, age, phone, photos and profile readiness.
    ensureMatchingProfile(id, enabled);
    db.jdbc.update(
        "update matching_profile set"
            + " matching_enabled=?,updated_at=CURRENT_TIMESTAMP"
            + " where user_id=?",
        enabled,
        id);
    updateMatchingDetails(id, style, keywords);
    if (db.count("select count(*) from user_preferences where user_id=?", id) == 0)
      db.jdbc.update(
          "insert into user_preferences(user_id,alimtalk_enabled,message_notifications,updated_at)"
              + " values (?,?,?,CURRENT_TIMESTAMP)",
          id,
          alimtalk,
          messages);
    else
      db.jdbc.update(
          "update user_preferences set"
              + " alimtalk_enabled=?,message_notifications=?,updated_at=CURRENT_TIMESTAMP where"
              + " user_id=?",
          alimtalk,
          messages,
          id);
  }

  public Map<String, Object> matchingDetails(Long id) {
    var rows = db.list("select dating_style,personality_keywords from matching_profile where user_id=?", id);
    if (!rows.isEmpty()) return rows.getFirst();
    Map<String, Object> empty = new LinkedHashMap<>();
    empty.put("datingStyle", null);
    empty.put("personalityKeywords", null);
    return empty;
  }

  @Transactional
  public Map<String, Object> updateMatchingDetails(Long id, String style, List<String> keywords) {
    db.jdbc.queryForList("select id from user_account where id=? for update", id);
    ensureMatchingProfile(id, true);
    if (style != null) {
      if (style.length() > 1000) throw bad("연애 스타일은 1,000자 이하로 입력해주세요.");
      db.jdbc.update("update matching_profile set dating_style=?,updated_at=CURRENT_TIMESTAMP where user_id=?", style.trim(), id);
    }
    if (keywords != null) {
      var unique = keywords.stream().map(k -> k == null ? "" : k.trim()).filter(k -> !k.isBlank()).distinct().toList();
      if (unique.size() > 3 || unique.stream().anyMatch(k -> k.length() > 30 || k.contains(",")))
        throw bad("성격 키워드는 각 30자 이하로 최대 3개 입력해주세요.");
      db.jdbc.update("update matching_profile set personality_keywords=?,updated_at=CURRENT_TIMESTAMP where user_id=?", String.join(",", unique), id);
    }
    return matchingDetails(id);
  }

  private void ensureMatchingProfile(Long id, boolean enabled) {
    if (db.count("select count(*) from matching_profile where user_id=?", id) == 0)
      db.jdbc.update(
          "insert into matching_profile(user_id,matching_enabled,auto_match_count,s_grade_guaranteed_match_count,no_response_count,created_at,updated_at)"
              + " values (?,?,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          id, enabled);
  }

  @Transactional
  public void withdraw(Long id) {
    db.jdbc.queryForList("select id from user_account where id=? for update", id);
    if (db.count(
            "select count(*) from payment_transaction where user_id=? and status in"
                + " ('READY','CONFIRMING','REFUND_PENDING')",
            id)
        > 0) throw bad("진행 중인 결제를 먼저 완료 또는 취소해주세요.");
    reviews.deleteUserFiles(id);
    db.jdbc.update(
        "update user_account set"
            + " status='DELETED',name=null,birth_date=null,gender=null,auth_version=auth_version+1,deleted_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        id);
    db.jdbc.update(
        "update chat_room set status='INACTIVE' where match_id in (select id from match_proposal"
            + " where user_a_id=? or user_b_id=?)",
        id,
        id);
    db.jdbc.update(
        "update match_proposal set status='CLOSED',closed_at=CURRENT_TIMESTAMP where (user_a_id=?"
            + " or user_b_id=?) and status in ('PENDING','ACCEPTED')",
        id,
        id);
    db.jdbc.update(
        "update chat_message set content='탈퇴한 회원의 메시지입니다.',deleted_at=CURRENT_TIMESTAMP where"
            + " sender_user_id=?",
        id);
    db.jdbc.update(
        "delete from user_interest where sender_user_id=? or receiver_user_id=?", id, id);
    for (String table :
        List.of(
            "email_credential",
            "oauth_account",
            "user_profile",
            "user_hobby",
            "user_personality_keyword",
            "matching_profile",
            "user_preferences",
            "app_notification",
            "phone_verification_code",
            "notification_outbox",
            "instant_intro_usage_window",
            "user_terms_agreement"))
      db.jdbc.update("delete from " + table + " where user_id=?", id);
    db.jdbc.update("delete from email_challenge where user_id=?", id);
    db.jdbc.update(
        "delete from premium_intro_request_keyword where premium_intro_request_id in (select id"
            + " from premium_intro_request where user_id=?)",
        id);
    db.jdbc.update(
        "delete from block_relation where blocker_user_id=? or blocked_user_id=?", id, id);
    db.jdbc.update(
        "update premium_intro_request set"
            + " appearance_preference_text=null,preferred_job_groups=null,important_point_text=null,status='CANCELED'"
            + " where user_id=?",
        id);
    db.jdbc.update(
        "update meeting_application set application_status='CANCELLED' where user_id=?", id);
  }

  private BusinessException bad(String m) {
    return new BusinessException(HttpStatus.BAD_REQUEST, m);
  }
}
