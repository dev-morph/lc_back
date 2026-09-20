package com.oao.backend.notification.service;

import com.oao.backend.common.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AlimtalkOutboxService {
  private final DbRows db;
  private final AlimtalkDeliveryClient delivery;
  private final org.springframework.transaction.support.TransactionTemplate transaction;
  private final boolean enabled;
  private final String key,
      secret,
      pfId,
      from,
      front,
      matchTemplate,
      expressTemplate,
      completedTemplate;

  public AlimtalkOutboxService(
      DbRows db,
      AlimtalkDeliveryClient delivery,
      org.springframework.transaction.PlatformTransactionManager manager,
      @Value("${oao.alimtalk.enabled:false}") boolean enabled,
      @Value("${oao.verification.message.solapi.api-key:}") String key,
      @Value("${oao.verification.message.solapi.api-secret:}") String secret,
      @Value("${oao.alimtalk.pf-id:}") String pfId,
      @Value("${oao.verification.message.solapi.from-number:}") String from,
      @Value("${oao.payment.frontend-url:http://localhost:3000}") String front,
      @Value("${oao.alimtalk.match-template:}") String matchTemplate,
      @Value("${oao.alimtalk.express-template:}") String expressTemplate,
      @Value("${oao.alimtalk.completed-template:}") String completedTemplate) {
    this.db = db;
    this.delivery = delivery;
    this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.enabled = enabled;
    this.key = key;
    this.secret = secret;
    this.pfId = pfId;
    this.from = from;
    this.front = front;
    this.matchTemplate = matchTemplate;
    this.expressTemplate = expressTemplate;
    this.completedTemplate = completedTemplate;
  }

  public void enqueue(Long user, String event, Long reference, String path) {
    if (event.equals("MESSAGE_RECEIVED")) return;
    String dedupe = event + ":" + user + ":" + reference;
    if (db.count("select count(*) from notification_outbox where dedupe_key=?", dedupe) > 0) return;
    boolean consent = consent(user);
    db.jdbc.update(
        "insert into"
            + " notification_outbox(user_id,event_type,reference_id,dedupe_key,link_path,status,next_attempt_at,created_at)"
            + " values (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        event,
        reference,
        dedupe,
        path,
        consent ? (configured() ? "PENDING" : "WAITING_CONFIGURATION") : "SKIPPED");
  }

  public boolean configured() {
    return enabled
        && !key.isBlank()
        && !secret.isBlank()
        && !pfId.isBlank()
        && !from.isBlank()
        && !matchTemplate.isBlank()
        && !expressTemplate.isBlank()
        && !completedTemplate.isBlank();
  }

  private boolean consent(Long user) {
    return db.count(
            "select count(*) from user_account u left join user_preferences p on u.id=p.user_id where"
                + " u.id=? and coalesce(p.alimtalk_enabled,true)=true and u.status='ACTIVE'",
            user)
        > 0;
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void dispatch() {
    if (!configured()) return;
    var rows =
        transaction.execute(
            status -> {
              db.jdbc.update(
                  "update notification_outbox set status='DELIVERY_UNKNOWN',failure_reason='발송 처리 중"
                      + " 서버가 중단되었습니다. 공급자 내역 확인 후 재시도해주세요.' where status='SENDING' and"
                      + " next_attempt_at<?",
                  java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600)));
              db.jdbc.update(
                  "update notification_outbox set status='PENDING' where"
                      + " status='WAITING_CONFIGURATION'");
              var claimed =
                  db.list(
                      "select * from notification_outbox where status='PENDING' and"
                          + " next_attempt_at<=CURRENT_TIMESTAMP order by id limit 10 for update");
              for (var row : claimed)
                db.jdbc.update(
                    "update notification_outbox set"
                        + " status='SENDING',attempts=attempts+1,next_attempt_at=CURRENT_TIMESTAMP"
                        + " where id=?",
                    row.get("id"));
              return claimed;
            });
    if (rows == null) return;
    // Claims commit before the network request. A process crash cannot silently re-send the same
    // message.
    for (var row : rows) {
      Long id = ((Number) row.get("id")).longValue(),
          user = ((Number) row.get("userId")).longValue();
      if (!consent(user) || !stillRelevant(row)) {
        finish(id, "SKIPPED", null, "수신 동의 또는 소개 상태가 변경되었습니다.");
        continue;
      }
      var contacts =
          db.list(
              "select phone_number from user_profile where user_id=? and phone_verified_at is not"
                  + " null",
              user);
      if (contacts.isEmpty()) {
        finish(id, "SKIPPED", null, "인증된 전화번호 없음");
        continue;
      }
      try {
        var result =
            delivery.send(
                key,
                secret,
                from,
                contacts.get(0).get("phoneNumber").toString(),
                pfId,
                template(row.get("eventType").toString()),
                front + row.get("linkPath"));
        finish(
            id,
            result.accepted() ? "SUBMITTED" : "FAILED",
            result.messageId(),
            result.accepted() ? null : "공급자가 발송 요청을 거절했습니다. 템플릿과 발신 정보를 확인해주세요.");
      } catch (Exception e) {
        finish(id, "DELIVERY_UNKNOWN", null, "발송 결과 확인 필요. 공급자 내역을 확인한 후 재시도하세요.");
      }
    }
  }

  private void finish(Long id, String status, String messageId, String reason) {
    transaction.executeWithoutResult(
        tx ->
            db.jdbc.update(
                "update notification_outbox set"
                    + " status=?,provider_message_id=?,failure_reason=?,sent_at=? where id=? and"
                    + " status='SENDING'",
                status,
                messageId,
                reason,
                "SUBMITTED".equals(status)
                    ? java.sql.Timestamp.from(java.time.Instant.now())
                    : null,
                id));
  }

  private boolean stillRelevant(Map<String, Object> row) {
    String event = row.get("eventType").toString();
    Object reference = row.get("referenceId");
    if (event.equals("MATCH_ARRIVED"))
      return db.count(
              "select count(*) from match_proposal where id=? and status='PENDING' and"
                  + " expires_at>CURRENT_TIMESTAMP",
              reference)
          > 0;
    if (event.equals("MATCH_COMPLETED"))
      return db.count(
              "select count(*) from match_proposal where id=? and status='ACCEPTED'", reference)
          > 0;
    return db.count(
            "select count(*) from user_interest where id=? and status='ACTIVE' and"
                + " express_decision='PENDING'",
            reference)
        > 0;
  }

  private String template(String type) {
    return switch (type) {
      case "MATCH_ARRIVED" -> matchTemplate;
      case "EXPRESS_RECEIVED" -> expressTemplate;
      default -> completedTemplate;
    };
  }

  public List<Map<String, Object>> logs() {
    return db.list("select * from notification_outbox order by id desc limit 200");
  }

  public void retry(Long id) {
    if (!configured())
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "알림톡 설정과 승인된 템플릿을 먼저 준비해주세요.");
    if (db.jdbc.update(
            "update notification_outbox set"
                + " status='PENDING',next_attempt_at=CURRENT_TIMESTAMP,failure_reason=null where"
                + " id=? and status in ('DELIVERY_UNKNOWN','FAILED','WAITING_CONFIGURATION')",
            id)
        == 0) throw new BusinessException(HttpStatus.CONFLICT, "재시도할 수 없는 알림입니다.");
  }
}
