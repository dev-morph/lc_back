package com.oao.backend.user.service;

import com.oao.backend.common.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {
  private final DbRows db;

  public ModerationService(DbRows db) {
    this.db = db;
  }

  public boolean blocked(Long a, Long b) {
    return db.count(
            "select count(*) from block_relation where (blocker_user_id=? and blocked_user_id=?) or"
                + " (blocker_user_id=? and blocked_user_id=?)",
            a,
            b,
            b,
            a)
        > 0;
  }

  public boolean excluded(Long a, Long b) {
    return blocked(a, b)
        || db.count(
                "select count(*) from report where status='PENDING' and ((reporter_user_id=? and"
                    + " reported_user_id=?) or (reporter_user_id=? and reported_user_id=?))",
                a,
                b,
                b,
                a)
            > 0;
  }

  public void requireAllowed(Long a, Long b) {
    if (db.count("select count(*) from user_account where id in (?,?) and status='ACTIVE'", a, b)
            != 2
        || excluded(a, b))
      throw new BusinessException(HttpStatus.FORBIDDEN, "신고 또는 차단 관계에서는 이용할 수 없습니다.");
  }

  @Transactional
  public void block(Long a, Long b) {
    validateTarget(a, b);
    db.jdbc.queryForList("select id from user_account where id=? for update", a);
    if (!blockedBy(a, b))
      db.jdbc.update(
          "insert into block_relation(blocker_user_id,blocked_user_id,created_at) values"
              + " (?,?,CURRENT_TIMESTAMP)",
          a,
          b);
    closePair(a, b);
  }

  private boolean blockedBy(Long a, Long b) {
    return db.count(
            "select count(*) from block_relation where blocker_user_id=? and blocked_user_id=?",
            a,
            b)
        > 0;
  }

  @Transactional
  public void report(Long a, Long b, String reason, String type, Long target) {
    validateTarget(a, b);
    if (reason == null || reason.trim().length() < 5 || reason.length() > 1000)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "신고 사유를 5~1000자로 입력해주세요.");
    if (!Set.of("PROFILE", "CHAT_MESSAGE").contains(type))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "신고 대상을 확인해주세요.");
    if ("CHAT_MESSAGE".equals(type)
        && (target == null
            || db.count(
                    "select count(*) from chat_message cm join chat_room cr on"
                        + " cr.id=cm.chat_room_id join match_proposal mp on mp.id=cr.match_id where"
                        + " cm.id=? and cm.sender_user_id=? and (mp.user_a_id=? or mp.user_b_id=?)",
                    target,
                    b,
                    a,
                    a)
                == 0))
      throw new BusinessException(HttpStatus.FORBIDDEN, "참여한 대화의 메시지만 신고할 수 있습니다.");
    if (db.count(
            "select count(*) from report where reporter_user_id=? and reported_user_id=? and"
                + " status='PENDING'",
            a,
            b)
        > 0) return;
    db.jdbc.update(
        "insert into"
            + " report(reporter_user_id,reported_user_id,target_type,target_id,reason,status,created_at)"
            + " values (?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP)",
        a,
        b,
        type,
        target,
        reason.trim());
    if (type.equals("CHAT_MESSAGE"))
      db.jdbc.update(
          "update report set message_snapshot=(select content from chat_message where id=?) where"
              + " reporter_user_id=? and reported_user_id=? and status='PENDING'",
          target,
          a,
          b);
    closePair(a, b);
  }

  public List<Map<String, Object>> blocks(Long id) {
    return db.list(
        "select b.blocked_user_id,u.name,b.created_at from block_relation b join user_account u on"
            + " u.id=b.blocked_user_id where b.blocker_user_id=? order by b.created_at desc",
        id);
  }

  public void unblock(Long a, Long b) {
    db.jdbc.update(
        "delete from block_relation where blocker_user_id=? and blocked_user_id=?", a, b);
  }

  public List<Map<String, Object>> reports() {
    return db.list(
        "select r.*,u.name as reported_name,coalesce(r.message_snapshot,cm.content) as"
            + " message_content from report r left join user_account u on u.id=r.reported_user_id"
            + " left join chat_message cm on r.target_type='CHAT_MESSAGE' and r.target_id=cm.id"
            + " order by r.created_at desc limit 200");
  }

  @Transactional
  public void resolve(Long id, String status, String note, boolean suspend, Long admin) {
    if (!Set.of("RESOLVED", "DISMISSED").contains(status))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "처리 상태를 확인해주세요.");
    var report = db.one("select * from report where id=? for update", id);
    db.jdbc.update(
        "update report set"
            + " status=?,resolution_note=?,resolved_by_admin_id=?,resolved_at=CURRENT_TIMESTAMP"
            + " where id=?",
        status,
        note,
        admin,
        id);
    if (suspend)
      db.jdbc.update(
          "update user_account set status='SUSPENDED',auth_version=auth_version+1 where id=?",
          report.get("reportedUserId"));
  }

  private void validateTarget(Long a, Long b) {
    if (a.equals(b)
        || db.count("select count(*) from user_account where id=? and status<>'DELETED'", b) == 0)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "대상 회원을 확인해주세요.");
  }

  private void closePair(Long a, Long b) {
    db.jdbc.update(
        "update user_interest set status='CANCELED' where (sender_user_id=? and receiver_user_id=?)"
            + " or (sender_user_id=? and receiver_user_id=?)",
        a,
        b,
        b,
        a);
    db.jdbc.update(
        "update chat_room set status='INACTIVE' where match_id in (select id from match_proposal"
            + " where (user_a_id=? and user_b_id=?) or (user_a_id=? and user_b_id=?))",
        a,
        b,
        b,
        a);
    db.jdbc.update(
        "update match_proposal set status='CLOSED',closed_at=CURRENT_TIMESTAMP where status in"
            + " ('PENDING','ACCEPTED') and ((user_a_id=? and user_b_id=?) or (user_a_id=? and"
            + " user_b_id=?))",
        a,
        b,
        b,
        a);
  }
}
