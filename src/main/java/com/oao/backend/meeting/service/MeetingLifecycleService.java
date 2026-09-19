package com.oao.backend.meeting.service;

import com.oao.backend.common.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingLifecycleService {
  private final DbRows db;
  private final UserLocks locks;

  public MeetingLifecycleService(DbRows db, UserLocks locks) {
    this.db = db;
    this.locks = locks;
  }

  public Map<String, Object> application(Long meeting, Long user) {
    var rows =
        db.list(
            "select * from meeting_application where meeting_event_id=? and user_id=?",
            meeting,
            user);
    return rows.isEmpty() ? Map.of() : rows.get(0);
  }

  @Transactional
  public void cancel(Long meeting, Long user) {
    locks.lock(user);
    var app =
        db.one(
            "select * from meeting_application where meeting_event_id=? and user_id=? for update",
            meeting,
            user);
    if ("PAID".equals(app.get("paymentStatus")))
      throw bad("결제한 모임은 운영자에게 환불을 요청해주세요. 환불 후 신청이 취소됩니다.");
    if (db.count(
            "select count(*) from payment_transaction where meeting_application_id=? and"
                + " status='READY'",
            app.get("id"))
        > 0) throw bad("진행 중인 결제가 있습니다. 결제 내역에서 상태를 먼저 확인해주세요.");
    db.jdbc.update(
        "update meeting_application set"
            + " application_status='CANCELLED',confirmed_at=null,updated_at=CURRENT_TIMESTAMP where"
            + " id=?",
        app.get("id"));
  }

  @Transactional
  public void update(
      Long id,
      String title,
      String description,
      LocalDateTime date,
      int price,
      int capacity,
      String status) {
    var event = db.one("select * from meeting_event where id=? for update", id);
    if (capacity
        < db.count(
            "select count(*) from meeting_application where meeting_event_id=? and"
                + " application_status='APPROVED'",
            id)) throw bad("승인된 인원보다 정원을 줄일 수 없습니다.");
    if (price != ((Number) event.get("priceAmount")).intValue()
        && db.count(
                "select count(*) from meeting_application where meeting_event_id=? and"
                    + " (payment_status='PAID' or id in(select meeting_application_id from"
                    + " payment_transaction where status='READY'))",
                id)
            > 0) throw bad("결제가 진행되거나 완료된 모임은 참가비를 바꿀 수 없습니다.");
    if (!Set.of("OPEN", "CLOSED", "DRAFT", "CANCELLED").contains(status))
      throw bad("모임 상태를 확인해주세요.");
    if ("CANCELLED".equals(status)
        && db.count(
                "select count(*) from meeting_application where meeting_event_id=? and"
                    + " payment_status='PAID'",
                id)
            > 0) throw bad("결제된 참가자의 환불을 먼저 처리해주세요.");
    db.jdbc.update(
        "update meeting_event set"
            + " title=?,description=?,event_date_time=?,price_amount=?,capacity=?,status=?,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        title,
        description,
        date,
        price,
        capacity,
        status,
        id);
    if ("CANCELLED".equals(status))
      db.jdbc.update(
          "update meeting_application set application_status='CANCELLED',confirmed_at=null where"
              + " meeting_event_id=?",
          id);
  }

  @Transactional
  public void delete(Long id) {
    db.one("select id from meeting_event where id=? for update", id);
    if (db.count("select count(*) from meeting_application where meeting_event_id=?", id) > 0)
      throw bad("신청 기록이 있는 모임은 삭제 대신 취소해주세요.");
    db.jdbc.update("delete from meeting_event where id=?", id);
  }

  private BusinessException bad(String m) {
    return new BusinessException(HttpStatus.BAD_REQUEST, m);
  }
}
