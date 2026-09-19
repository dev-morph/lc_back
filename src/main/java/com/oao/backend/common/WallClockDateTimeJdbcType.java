package com.oao.backend.common;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType;

/** DATETIME wall-clock values must not be converted through a JVM or connection time zone. */
public class WallClockDateTimeJdbcType extends LocalDateTimeJdbcType {
  private static Calendar utcCalendar() {
    return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
  }

  private static LocalDateTime fromTimestamp(Timestamp value) {
    return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
  }

  public static LocalDateTime read(ResultSet rs, int index) throws SQLException {
    return fromTimestamp(rs.getTimestamp(index, utcCalendar()));
  }

  @Override
  public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
    // MariaDB Connector/J 3.5 also applies preserveInstants when getObject(LocalDateTime.class)
    // reads a DATETIME (including getString). An explicit UTC calendar preserves its fields.
    return new BasicExtractor<X>(javaType, this) {
      @Override
      protected X doExtract(ResultSet rs, int index, WrapperOptions options) throws SQLException {
        return javaType.wrap(read(rs, index), options);
      }

      @Override
      protected X doExtract(CallableStatement statement, int index, WrapperOptions options)
          throws SQLException {
        return javaType.wrap(fromTimestamp(statement.getTimestamp(index, utcCalendar())), options);
      }

      @Override
      protected X doExtract(CallableStatement statement, String name, WrapperOptions options)
          throws SQLException {
        return javaType.wrap(fromTimestamp(statement.getTimestamp(name, utcCalendar())), options);
      }
    };
  }
}
