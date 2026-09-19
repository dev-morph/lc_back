package com.oao.backend.payment.service;

import com.oao.backend.common.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TossPaymentService {
  private final DbRows db;
  private final TossPaymentsClient toss;
  private final UserLocks locks;

  public TossPaymentService(DbRows db, TossPaymentsClient toss, UserLocks locks) {
    this.db = db;
    this.toss = toss;
    this.locks = locks;
  }

  @Transactional
  public Map<String, Object> prepare(Long user, Long product, Long application, String returnPath) {
    toss.requireConfigured();
    locks.lock(user);
    long amount;
    String name;
    int hearts = 0;
    if ((product == null) == (application == null)) throw bad("구매할 상품을 선택해주세요.");
    if (product != null) {
      var p = db.one("select * from heart_product where id=? and status='ACTIVE'", product);
      amount = ((Number) p.get("price")).longValue();
      hearts = ((Number) p.get("heartAmount")).intValue();
      name = p.get("name").toString();
    } else {
      var a =
          db.one(
              "select a.*,e.title,e.price_amount,e.status as event_status from meeting_application"
                  + " a join meeting_event e on e.id=a.meeting_event_id where a.id=? and"
                  + " a.user_id=? for update",
              application,
              user);
      if (!"APPROVED".equals(a.get("applicationStatus"))
          || !"PENDING".equals(a.get("paymentStatus"))
          || "CANCELLED".equals(a.get("eventStatus"))) throw bad("승인된 미결제 모임 신청만 결제할 수 있습니다.");
      amount = ((Number) a.get("priceAmount")).longValue();
      name = a.get("title").toString();
    }
    if (amount <= 0) throw bad("결제 금액을 확인해주세요.");
    String order = UUID.randomUUID().toString();
    String customer =
        "lc_" + com.oao.backend.auth.EmailAuthService.hash("customer:" + user).substring(0, 40);
    String path = safeReturn(returnPath);
    db.jdbc.update(
        "insert into"
            + " payment_transaction(user_id,heart_product_id,provider,amount,status,order_id,order_name,heart_amount_snapshot,meeting_application_id,return_path,customer_key,created_at,updated_at)"
            + " values"
            + " (?,?,'TOSS_PAYMENTS',?,'READY',?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        product,
        amount,
        order,
        name,
        hearts,
        application,
        path,
        customer);
    return Map.of(
        "orderId",
        order,
        "orderName",
        name,
        "amount",
        amount,
        "customerKey",
        customer,
        "clientKey",
        toss.clientKey(),
        "successUrl",
        toss.frontend() + "/payments/toss/success",
        "failUrl",
        toss.frontend() + "/payments/toss/fail");
  }

  @Transactional
  public Map<String, Object> confirm(Long user, String key, String order, long amount) {
    locks.lock(user);
    var tx =
        db.one(
            "select * from payment_transaction where order_id=? and user_id=? for update",
            order,
            user);
    validateAmount(tx, amount);
    if ("APPROVED".equals(tx.get("status"))) {
      if (!key.equals(tx.get("providerTransactionId"))) throw bad("결제 정보가 일치하지 않습니다.");
      return result(tx, user);
    }
    if ("CANCELED".equals(tx.get("status"))) throw bad("취소된 결제입니다.");
    if (tx.get("meetingApplicationId") != null) {
      var application =
          db.one(
              "select application_status,payment_status from meeting_application where id=? for"
                  + " update",
              tx.get("meetingApplicationId"));
      if (!"APPROVED".equals(application.get("applicationStatus"))
          || !"PENDING".equals(application.get("paymentStatus")))
        throw bad("이미 결제되었거나 취소된 모임 신청입니다.");
    }
    var payment = toss.confirm(key, order, amount);
    if (!key.equals(payment.get("paymentKey"))) throw bad("결제 키가 일치하지 않습니다.");
    validateProvider(tx, payment);
    if (!"DONE".equals(payment.get("status"))) throw bad("결제 승인이 완료되지 않았습니다.");
    approve(tx, payment);
    return result(db.one("select * from payment_transaction where order_id=?", order), user);
  }

  @Transactional
  public void fail(Long user, String order, String reason) {
    locks.lock(user);
    db.jdbc.update(
        "update payment_transaction set"
            + " status='FAILED',failed_reason=?,updated_at=CURRENT_TIMESTAMP where order_id=? and"
            + " user_id=? and status='READY'",
        trim(reason, 500),
        order,
        user);
  }

  public List<Map<String, Object>> list(Long user) {
    return user == null
        ? db.list(
            "select p.*,u.name as user_name from payment_transaction p left join user_account u on"
                + " u.id=p.user_id order by p.id desc limit 200")
        : db.list(
            "select"
                + " id,order_id,order_name,amount,status,heart_amount_snapshot,meeting_application_id,created_at,failed_reason,refunded_at"
                + " from payment_transaction where user_id=? order by id desc limit 100",
            user);
  }

  @Transactional
  public Map<String, Object> reconcile(Long id, Long requester) {
    Long user = owner(id, requester);
    locks.lock(user);
    var tx = db.one("select * from payment_transaction where id=? for update", id);
    if (!"TOSS_PAYMENTS".equals(tx.get("provider"))) return result(tx, user);
    var payment =
        tx.get("providerTransactionId") == null
            ? toss.getByOrder(tx.get("orderId").toString())
            : toss.get(tx.get("providerTransactionId").toString());
    if (payment != null) synchronize(tx, payment);
    return result(db.one("select * from payment_transaction where id=?", id), user);
  }

  @Transactional
  public void cancelUnpaid(Long id, Long requester) {
    Long user = owner(id, requester);
    locks.lock(user);
    var tx = db.one("select * from payment_transaction where id=? for update", id);
    if ("CANCELED".equals(tx.get("status"))) return;
    if (!Set.of("READY", "FAILED").contains(tx.get("status")))
      throw bad("결제 완료된 주문은 운영자에게 환불을 요청해주세요.");
    var payment = toss.getByOrder(tx.get("orderId").toString());
    if (payment != null) {
      validateProvider(tx, payment);
      if (!Set.of("READY", "ABORTED", "EXPIRED", "CANCELED").contains(payment.get("status")))
        throw bad("승인 처리 중이거나 완료된 결제입니다. 상태 확인 후 다시 진행해주세요.");
    }
    db.jdbc.update(
        "update payment_transaction set status='CANCELED',refund_reason='미결제 주문"
            + " 취소',updated_at=CURRENT_TIMESTAMP where id=?",
        id);
  }

  @Transactional
  public void webhook(String key, String order) {
    if (key == null || order == null) return;
    var rows =
        db.list(
            "select id,user_id from payment_transaction where order_id=? and"
                + " provider='TOSS_PAYMENTS'",
            order);
    if (rows.isEmpty()) return;
    Long user = ((Number) rows.get(0).get("userId")).longValue();
    locks.lock(user);
    var tx = db.one("select * from payment_transaction where order_id=? for update", order);
    var payment = toss.get(key);
    if (payment == null || !key.equals(payment.get("paymentKey"))) throw bad("결제 키가 일치하지 않습니다.");
    synchronize(tx, payment);
  }

  private Long owner(Long id, Long requester) {
    Long user =
        ((Number) db.one("select user_id from payment_transaction where id=?", id).get("userId"))
            .longValue();
    if (requester != null && !requester.equals(user))
      throw new BusinessException(HttpStatus.FORBIDDEN, "본인 결제만 확인할 수 있습니다.");
    return user;
  }

  private void synchronize(Map<String, Object> tx, Map<String, Object> payment) {
    validateProvider(tx, payment);
    String key = String.valueOf(payment.get("paymentKey"));
    if (tx.get("providerTransactionId") != null && !key.equals(tx.get("providerTransactionId")))
      throw bad("결제 정보가 일치하지 않습니다.");
    String remote = String.valueOf(payment.get("status"));
    if ("DONE".equals(remote) && !"APPROVED".equals(tx.get("status"))) {
      boolean fulfillable = !"CANCELED".equals(tx.get("status"));
      if (tx.get("meetingApplicationId") != null) {
        var a =
            db.one(
                "select application_status,payment_status from meeting_application where id=? for"
                    + " update",
                tx.get("meetingApplicationId"));
        fulfillable &=
            "APPROVED".equals(a.get("applicationStatus"))
                && "PENDING".equals(a.get("paymentStatus"));
      }
      if (fulfillable) approve(tx, payment);
      else {
        var cancelled = toss.cancel(key, tx.get("orderId").toString(), "취소된 주문의 지연 승인 자동 환불");
        validateProvider(tx, cancelled);
        if (!"CANCELED".equals(cancelled.get("status"))) throw bad("지연 승인 환불 결과를 확인해주세요.");
        db.jdbc.update(
            "update payment_transaction set"
                + " status='CANCELED',provider_transaction_id=?,refund_reason='취소된 주문의 지연 승인 자동"
                + " 환불',refunded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP where id=?",
            key,
            tx.get("id"));
      }
    }
    if ("CANCELED".equals(remote) && "APPROVED".equals(tx.get("status")))
      reverse(tx, "결제 제공자에서 취소됨", false);
    if (Set.of("ABORTED", "EXPIRED").contains(remote) && "READY".equals(tx.get("status")))
      db.jdbc.update(
          "update payment_transaction set status='FAILED',failed_reason='승인이 취소되거나 만료된"
              + " 결제입니다.',updated_at=CURRENT_TIMESTAMP where id=?",
          tx.get("id"));
    if ("PARTIAL_CANCELED".equals(remote))
      db.jdbc.update(
          "update payment_transaction set failed_reason='부분 취소가 확인되었습니다. 관리자 정산 확인이 필요합니다.' where"
              + " id=?",
          tx.get("id"));
  }

  @Transactional
  public void refund(Long id, String reason) {
    if (reason == null || reason.isBlank()) throw bad("환불 사유를 입력해주세요.");
    var first = db.one("select user_id from payment_transaction where id=?", id);
    Long user = ((Number) first.get("userId")).longValue();
    locks.lock(user);
    var tx = db.one("select * from payment_transaction where id=? for update", id);
    if ("CANCELED".equals(tx.get("status"))) return;
    if (!"APPROVED".equals(tx.get("status")) || !"TOSS_PAYMENTS".equals(tx.get("provider")))
      throw bad("승인된 토스 결제만 환불할 수 있습니다.");
    int hearts = ((Number) tx.get("heartAmountSnapshot")).intValue();
    if (hearts > 0 && balance(user) < hearts) throw bad("환불할 하트가 부족합니다. 사용 내역을 확인해주세요.");
    var payment =
        toss.cancel(
            tx.get("providerTransactionId").toString(), tx.get("orderId").toString(), reason);
    validateProvider(tx, payment);
    if (!"CANCELED".equals(payment.get("status"))) throw bad("전체 취소 완료를 확인하지 못했습니다.");
    reverse(tx, reason, true);
  }

  private void approve(Map<String, Object> tx, Map<String, Object> payment) {
    Long user = ((Number) tx.get("userId")).longValue();
    Long id = ((Number) tx.get("id")).longValue();
    String key = payment.get("paymentKey").toString();
    if (db.count(
            "select count(*) from payment_transaction where provider='TOSS_PAYMENTS' and"
                + " provider_transaction_id=? and id<>?",
            key,
            id)
        > 0) throw bad("이미 처리된 결제입니다.");
    int hearts = ((Number) tx.get("heartAmountSnapshot")).intValue();
    if (hearts > 0) {
      ensureWallet(user);
      db.jdbc.update(
          "update heart_wallet set balance=balance+?,updated_at=CURRENT_TIMESTAMP where user_id=?",
          hearts,
          user);
      ledger(user, hearts, id, "CHARGE");
    } else {
      Object application = tx.get("meetingApplicationId");
      var a = db.one("select * from meeting_application where id=? for update", application);
      if ("PAID".equals(a.get("paymentStatus")) || !"APPROVED".equals(a.get("applicationStatus")))
        throw bad("모임 신청 상태가 변경되었습니다. 운영자에게 결제 확인을 요청해주세요.");
      db.jdbc.update(
          "update meeting_application set payment_status='PAID',confirmed_at=CURRENT_TIMESTAMP"
              + " where id=?",
          application);
    }
    db.jdbc.update(
        "update payment_transaction set"
            + " status='APPROVED',provider_transaction_id=?,approved_at=CURRENT_TIMESTAMP,failed_reason=null,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        key,
        id);
  }

  private void reverse(Map<String, Object> tx, String reason, boolean checked) {
    Long user = ((Number) tx.get("userId")).longValue();
    int hearts = ((Number) tx.get("heartAmountSnapshot")).intValue();
    if (hearts > 0) {
      ensureWallet(user);
      db.jdbc.update(
          "update heart_wallet set balance=balance-?,updated_at=CURRENT_TIMESTAMP where user_id=?",
          hearts,
          user);
      ledger(user, -hearts, ((Number) tx.get("id")).longValue(), "REFUND");
      if (balance(user) < 0)
        db.jdbc.update("update matching_profile set matching_enabled=false where user_id=?", user);
    } else
      db.jdbc.update(
          "update meeting_application set"
              + " payment_status='REFUNDED',application_status='CANCELLED',confirmed_at=null where"
              + " id=?",
          tx.get("meetingApplicationId"));
    db.jdbc.update(
        "update payment_transaction set"
            + " status='CANCELED',refund_reason=?,refunded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        trim(reason, 500),
        tx.get("id"));
  }

  private void ledger(Long user, int amount, Long reference, String type) {
    db.jdbc.update(
        "insert into"
            + " heart_transaction(user_id,transaction_type,amount,balance_after,reference_type,reference_id,created_at,updated_at)"
            + " values (?,?,?,?,'TOSS_PAYMENT',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        type,
        amount,
        balance(user),
        reference);
  }

  private void ensureWallet(Long user) {
    if (db.count("select count(*) from heart_wallet where user_id=?", user) == 0)
      db.jdbc.update(
          "insert into heart_wallet(user_id,balance,created_at,updated_at) values"
              + " (?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          user);
  }

  private int balance(Long user) {
    var rows = db.list("select balance from heart_wallet where user_id=?", user);
    return rows.isEmpty() ? 0 : ((Number) rows.get(0).get("balance")).intValue();
  }

  private Map<String, Object> result(Map<String, Object> tx, Long user) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("orderId", tx.get("orderId"));
    result.put("status", tx.get("status"));
    result.put("balance", balance(user));
    result.put("chargedHearts", tx.get("heartAmountSnapshot"));
    result.put("returnTo", tx.get("returnPath"));
    return result;
  }

  public static String safeReturn(String p) {
    return p != null
            && p.startsWith("/")
            && !p.startsWith("//")
            && !p.contains("\\")
            && !p.contains("\r")
            && !p.contains("\n")
            && p.length() <= 512
        ? p
        : "/home";
  }

  private void validateAmount(Map<String, Object> tx, long amount) {
    if (((Number) tx.get("amount")).longValue() != amount) throw bad("결제 금액이 일치하지 않습니다.");
  }

  private void validateProvider(Map<String, Object> tx, Map<String, Object> payment) {
    if (payment == null
        || !(payment.get("paymentKey") instanceof String paymentKey)
        || paymentKey.isBlank()
        || !Objects.equals(tx.get("orderId"), payment.get("orderId"))
        || !(payment.get("totalAmount") instanceof Number n)
        || new BigDecimal(tx.get("amount").toString()).compareTo(new BigDecimal(n.toString())) != 0
        || !"KRW".equals(payment.get("currency"))) throw bad("결제 승인 정보를 확인할 수 없습니다.");
  }

  private String trim(String value, int max) {
    return value == null ? "" : value.substring(0, Math.min(value.length(), max));
  }

  private BusinessException bad(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, message);
  }
}
