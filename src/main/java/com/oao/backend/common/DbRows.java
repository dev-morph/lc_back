package com.oao.backend.common;

import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbRows {
  public final JdbcTemplate jdbc;

  public DbRows(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, n) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          var meta = rs.getMetaData();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getColumnLabel(i);
            String[] parts = name.toLowerCase(Locale.ROOT).split("_");
            StringBuilder key = new StringBuilder(parts[0]);
            for (int k = 1; k < parts.length; k++)
              key.append(Character.toUpperCase(parts[k].charAt(0))).append(parts[k].substring(1));
            Object value =
                "event_date_time".equalsIgnoreCase(name)
                    ? WallClockDateTimeJdbcType.read(rs, i)
                    : rs.getObject(i);
            if (value instanceof Timestamp t) value = t.toInstant().toString();
            if (value instanceof java.time.LocalDateTime t) value = t.toString();
            row.put(key.toString(), value);
          }
          return row;
        },
        args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다.");
    return rows.get(0);
  }

  public long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }
}
