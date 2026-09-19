package com.oao.backend.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserLocks {
  private final JdbcTemplate db;

  public UserLocks(JdbcTemplate db) {
    this.db = db;
  }

  public void lock(Long user) {
    if (db.queryForList("select id from user_account where id=? for update", user).isEmpty())
      throw new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found.");
  }

  public void pair(Long a, Long b) {
    lock(Math.min(a, b));
    if (!a.equals(b)) lock(Math.max(a, b));
  }
}
