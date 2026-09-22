package com.oao.backend.user.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Public certification is tied to the latest document and the actual profile value. */
@Service
public class VerificationBadgeService {
  private final JdbcTemplate db;
  public VerificationBadgeService(JdbcTemplate db) { this.db = db; }
  public record Badges(boolean employmentVerified, boolean educationVerified) {
    public static final Badges NONE = new Badges(false, false);
  }
  public Badges forUser(Long user) { return forUsers(List.of(user)).getOrDefault(user, Badges.NONE); }
  public Map<Long, Badges> forUsers(Collection<Long> users) {
    Map<Long, Badges> result = new HashMap<>();
    if (users.isEmpty()) return result;
    new NamedParameterJdbcTemplate(db).query("""
        select d.user_id,d.document_type,d.subject_value,p.job,p.education
        from user_verification_document d join user_profile p on p.user_id=d.user_id
        where d.user_id in (:users) and d.document_type in ('EMPLOYMENT','EDUCATION')
          and d.review_status='APPROVED' and d.invalidated_at is null and d.subject_value is not null
          and d.id=(select max(x.id) from user_verification_document x
                    where x.user_id=d.user_id and x.document_type=d.document_type)
        """, Map.of("users", users), (org.springframework.jdbc.core.RowCallbackHandler) row -> {
      long id=row.getLong("user_id"); boolean employment=row.getString("document_type").equals("EMPLOYMENT");
      String current=row.getString(employment ? "job" : "education");
      if (current == null || current.isBlank() || !current.trim().equals(row.getString("subject_value"))) return;
      var old=result.getOrDefault(id, Badges.NONE);
      result.put(id, new Badges(employment || old.employmentVerified(), !employment || old.educationVerified()));
    });
    return result;
  }
  public String subject(Long user, String type) {
    if (!Set.of("EMPLOYMENT", "EDUCATION").contains(type)) return null;
    String column=type.equals("EMPLOYMENT") ? "job" : "education";
    var rows=db.queryForList("select " + column + " from user_profile where user_id=?", String.class, user);
    return rows.isEmpty() || rows.getFirst()==null ? null : rows.getFirst().trim();
  }
  public void invalidateChanged(Long user, String job, String education) {
    invalidate(user, "EMPLOYMENT", job);
    invalidate(user, "EDUCATION", education);
  }
  private void invalidate(Long user, String type, String next) {
    if (!Objects.equals(subject(user, type), next)) {
      db.update("update user_verification_document set invalidated_at=CURRENT_TIMESTAMP where user_id=? and document_type=? and invalidated_at is null", user, type);
    }
  }
}
