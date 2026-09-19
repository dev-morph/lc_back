package com.oao.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseTimeConfiguration {
  @Bean
  static BeanPostProcessor databaseTimeZone() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String name) {
        if (bean instanceof HikariDataSource source
            && source.getJdbcUrl() != null
            && source.getJdbcUrl().startsWith("jdbc:mariadb:")) {
          // JDBC timestamps, JPA Instants and SQL CURRENT_TIMESTAMP share UTC even
          // when the application host is in Seoul and the database host is not.
          source.addDataSourceProperty("connectionTimeZone", "UTC");
          source.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
          source.addDataSourceProperty("preserveInstants", "true");
        } else if (bean instanceof HikariDataSource source
            && source.getJdbcUrl() != null
            && source.getJdbcUrl().startsWith("jdbc:h2:")) {
          source.setConnectionInitSql("SET TIME ZONE 'UTC'");
        }
        return bean;
      }
    };
  }
}
