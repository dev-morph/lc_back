package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAuthService {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final EmailDeliveryService mail;
  private final UserAccountRepository users;
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;

  public EmailAuthService(
      JdbcTemplate db,
      PasswordEncoder encoder,
      EmailDeliveryService mail,
      UserAccountRepository users) {
    this.db = db;
    this.encoder = encoder;
    this.mail = mail;
    this.users = users;
    dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public Map<String, Object> start(String rawEmail, String password, String purpose, Long userId) {
    String email = rawEmail.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("REGISTER", "RESET", "LINK").contains(purpose)) throw bad("잘못된 인증 요청입니다.");
    if (!purpose.equals("RESET")) validatePassword(password);
    if (purpose.equals("LINK") && userId == null) throw bad("먼저 로그인해주세요.");
    var found = db.queryForList("select user_id from email_credential where email=?", email);
    if (!purpose.equals("RESET") && !found.isEmpty())
      throw bad("이미 등록된 이메일입니다. 로그인 또는 비밀번호 재설정을 이용해주세요.");
    if (purpose.equals("LINK")
        && db.queryForObject(
                "select count(*) from email_credential where user_id=?", Integer.class, userId)
            > 0) throw bad("이미 이메일이 연결되어 있습니다.");
    String id = UUID.randomUUID().toString();
    if (purpose.equals("RESET") && found.isEmpty())
      return Map.of("challengeId", id, "message", "등록된 이메일이면 인증번호가 발송됩니다.");
    Long target = null;
    if (purpose.equals("RESET")) target = ((Number) found.get(0).get("user_id")).longValue();
    else if (purpose.equals("LINK")) target = userId;
    if (db.queryForObject(
            "select count(*) from email_challenge where email=? and created_at>?",
            Integer.class,
            email,
            Timestamp.from(Instant.now().minusSeconds(60)))
        > 0) throw bad("인증번호는 1분 후 다시 요청해주세요.");
    if (db.queryForObject(
            "select count(*) from email_challenge where email=? and created_at>?",
            Integer.class,
            email,
            Timestamp.from(Instant.now().minusSeconds(86400)))
        >= 10) throw bad("오늘 인증 요청 횟수를 초과했습니다.");
    String code = String.format("%06d", random.nextInt(1000000));
    db.update(
        "update email_challenge set consumed_at=CURRENT_TIMESTAMP where email=? and purpose=? and"
            + " consumed_at is null",
        email,
        purpose);
    db.update(
        "insert into email_challenge"
            + " (id,email,password_hash,user_id,purpose,code_hash,expires_at,created_at) values"
            + " (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
        id,
        email,
        purpose.equals("RESET") ? null : encoder.encode(password),
        target,
        purpose,
        hash(id + code),
        Timestamp.from(Instant.now().plusSeconds(900)));
    mail.sendCode(email, code);
    return Map.of("challengeId", id, "message", "인증번호를 이메일로 보냈습니다. 15분 안에 입력해주세요.");
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public Long verify(String id, String code, String password, Long currentUser) {
    var rows = db.queryForList("select * from email_challenge where id=? for update", id);
    if (rows.isEmpty()) throw bad("인증번호가 올바르지 않거나 만료되었습니다.");
    var row = rows.get(0);
    if (row.get("consumed_at") != null
        || ((Timestamp) row.get("expires_at")).toInstant().isBefore(Instant.now())
        || ((Number) row.get("attempts")).intValue() >= 5) throw bad("인증번호가 만료되었습니다. 다시 요청해주세요.");
    db.update("update email_challenge set attempts=attempts+1 where id=?", id);
    if (!MessageDigest.isEqual(
        hash(id + code).getBytes(StandardCharsets.UTF_8),
        row.get("code_hash").toString().getBytes(StandardCharsets.UTF_8)))
      throw bad("인증번호가 올바르지 않습니다.");
    String purpose = row.get("purpose").toString(), email = row.get("email").toString();
    Long userId = row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue();
    if (purpose.equals("LINK") && !Objects.equals(userId, currentUser))
      throw bad("이메일 연결을 요청한 계정으로 로그인해주세요.");
    if (purpose.equals("RESET")) {
      validatePassword(password);
      db.update(
          "update email_credential set password_hash=?,updated_at=CURRENT_TIMESTAMP where"
              + " user_id=?",
          encoder.encode(password),
          userId);
      db.update("update user_account set auth_version=auth_version+1 where id=?", userId);
    } else {
      if (db.queryForObject(
              "select count(*) from email_credential where email=?", Integer.class, email)
          > 0) throw bad("이미 등록된 이메일입니다.");
      if (userId == null) userId = users.saveAndFlush(UserAccount.createPending()).getId();
      db.update(
          "insert into email_credential(user_id,email,password_hash,verified_at,updated_at) values"
              + " (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          userId,
          email,
          row.get("password_hash"));
    }
    db.update(
        "update email_challenge set consumed_at=CURRENT_TIMESTAMP,user_id=? where id=?",
        userId,
        id);
    return userId;
  }

  public Long login(String email, String password) {
    var rows =
        db.queryForList(
            "select user_id,password_hash from email_credential where email=?",
            email.trim().toLowerCase(Locale.ROOT));
    boolean valid =
        encoder.matches(
            password, rows.isEmpty() ? dummyHash : rows.get(0).get("password_hash").toString());
    if (!valid || rows.isEmpty())
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요.");
    Long id = ((Number) rows.get(0).get("user_id")).longValue();
    var user = users.findById(id).orElseThrow(() -> bad("계정을 찾을 수 없습니다."));
    if (user.getStatus() != UserAccount.UserStatus.ACTIVE)
      throw new BusinessException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
    return id;
  }

  public static void validatePassword(String value) {
    if (value == null
        || value.length() < 10
        || value.getBytes(StandardCharsets.UTF_8).length > 72
        || !value.matches("(?s).*[A-Za-z].*")
        || !value.matches("(?s).*[0-9].*"))
      throw bad("비밀번호는 영문과 숫자를 포함한 10자 이상, 72바이트 이하로 입력해주세요.");
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BusinessException bad(String text) {
    return new BusinessException(HttpStatus.BAD_REQUEST, text);
  }
}
