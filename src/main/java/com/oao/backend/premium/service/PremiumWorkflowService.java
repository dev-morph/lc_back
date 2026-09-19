package com.oao.backend.premium.service;

import com.oao.backend.common.*;
import com.oao.backend.matching.service.AdminMatchingOperationsService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumWorkflowService {
  private final DbRows db;
  private final AdminMatchingOperationsService matches;
  private final com.oao.backend.matching.service.MatchingPolicyService policy;

  public PremiumWorkflowService(
      DbRows db,
      AdminMatchingOperationsService matches,
      com.oao.backend.matching.service.MatchingPolicyService policy) {
    this.db = db;
    this.matches = matches;
    this.policy = policy;
  }

  public List<Map<String, Object>> keywords() {
    return db.list("select id,name from personality_keyword order by id");
  }

  public List<Map<String, Object>> list(Long user) {
    var requests =
        user == null
            ? db.list(
                "select p.*,u.name from premium_intro_request p join user_account u on"
                    + " u.id=p.user_id order by p.id desc limit 200")
            : db.list("select * from premium_intro_request where user_id=? order by id desc", user);
    for (var request : requests)
      request.put(
          "keywords",
          db
              .list(
                  "select k.name from premium_intro_request_keyword r join personality_keyword k on"
                      + " k.id=r.keyword_id where r.premium_intro_request_id=?",
                  request.get("id"))
              .stream()
              .map(k -> k.get("name"))
              .toList());
    return requests;
  }

  public Map<String, Object> detail(Long id, Long user) {
    return user == null
        ? db.one("select * from premium_intro_request where id=?", id)
        : db.one("select * from premium_intro_request where id=? and user_id=?", id, user);
  }

  @Transactional
  public void cancel(Long id, Long user) {
    if (db.jdbc.update(
            "update premium_intro_request set status='CANCELED',updated_at=CURRENT_TIMESTAMP where"
                + " id=? and user_id=? and status in ('REQUESTED','IN_REVIEW')",
            id,
            user)
        == 0) throw new BusinessException(HttpStatus.CONFLICT, "취소할 수 없는 신청입니다.");
  }

  public List<Map<String, Object>> candidates(Long id) {
    var r = detail(id, null);
    return db
        .list(
            "select u.id,u.name,u.birth_date,u.gender,u.grade,p.height_cm,p.job,p.activity_region"
                + " from user_account u join user_profile p on p.user_id=u.id join matching_profile"
                + " m on m.user_id=u.id where u.status='ACTIVE' and u.approval_status='APPROVED'"
                + " and m.matching_enabled=true and u.id<>? and u.gender<>(select gender from"
                + " user_account where id=?) and p.height_cm between ? and ? order by u.id limit"
                + " 200",
            r.get("userId"),
            r.get("userId"),
            r.get("minHeightCm"),
            r.get("maxHeightCm"))
        .stream()
        .filter(
            u -> {
              Long candidate = ((Number) u.get("id")).longValue();
              if (!policy.eligible(candidate)
                  || !policy.pairAllowed(((Number) r.get("userId")).longValue(), candidate))
                return false;
              Long applicant = ((Number) r.get("userId")).longValue();
              if (db.count(
                      "select count(*) from match_proposal where ((user_a_id=? and user_b_id=?) or"
                          + " (user_a_id=? and user_b_id=?)) and (status='ACCEPTED' or"
                          + " (status='PENDING' and expires_at>CURRENT_TIMESTAMP))",
                      applicant,
                      candidate,
                      candidate,
                      applicant)
                  > 0) return false;
              Object date = u.get("birthDate");
              if (date == null) return false;
              int age =
                  java.time.Period.between(
                          java.time.LocalDate.parse(date.toString()),
                          java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
                      .getYears();
              return age >= ((Number) r.get("minAge")).intValue()
                  && age <= ((Number) r.get("maxAge")).intValue();
            })
        .toList();
  }

  @Transactional
  public void update(Long id, String status, String note, Long counterpart, Long admin) {
    var request = db.one("select * from premium_intro_request where id=? for update", id);
    if (!Set.of("REQUESTED", "IN_REVIEW").contains(request.get("status")))
      throw new BusinessException(HttpStatus.CONFLICT, "이미 처리된 신청입니다.");
    if (!Set.of("IN_REVIEW", "CANCELED", "MATCHED").contains(status))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "처리 상태를 확인해주세요.");
    Long matchId = null;
    if ("MATCHED".equals(status)) {
      if (counterpart == null)
        throw new BusinessException(HttpStatus.BAD_REQUEST, "상대 회원을 선택해주세요.");
      if (candidates(id).stream().noneMatch(c -> ((Number) c.get("id")).longValue() == counterpart))
        throw new BusinessException(HttpStatus.BAD_REQUEST, "신청한 나이·키 조건에 맞는 상대를 선택해주세요.");
      var match =
          matches.createManualMatch(
              new AdminMatchingOperationsService.ManualMatchCommand(
                  ((Number) request.get("userId")).longValue(),
                  counterpart,
                  "프리미엄 소개 신청을 바탕으로 추천했어요."),
              admin);
      matchId = match.matchId();
    }
    db.jdbc.update(
        "update premium_intro_request set"
            + " status=?,admin_note=?,assigned_admin_id=?,match_id=?,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        status,
        note,
        admin,
        matchId,
        id);
  }
}
