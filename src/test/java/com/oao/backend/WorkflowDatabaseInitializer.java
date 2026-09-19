package com.oao.backend;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Comparator;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Runs the real migration resources with only MariaDB-specific DDL adapted for H2. This checks
 * application contracts; it does not replace a MariaDB migration rehearsal.
 */
public class WorkflowDatabaseInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext context) {
    var env = context.getEnvironment();
    if (!env.getRequiredProperty("spring.datasource.url").startsWith("jdbc:h2:")) return;
    try (var connection =
        DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"), "sa", "")) {
      var resources =
          new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
      Arrays.sort(resources, Comparator.comparingInt(WorkflowDatabaseInitializer::version));
      for (var resource : resources) {
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);
        if (version(resource) == 4) {
          // The legacy email-to-admin backfill uses UPDATE JOIN. There are no pre-existing admins
          // in this fixture.
          sql = "alter table admin_user add column user_id bigint;";
        }
        sql =
            sql.replaceAll("(?is)alter table \\w+\\s+drop (foreign key|index) if exists \\w+;", "");
        sql = sql.replaceAll("(?i)\\s+after \\w+", "");
        sql = sql.replaceAll("(?i)modify (?:column )?", "alter column ");
        sql = sql.replace("b'1'", "true").replace("b'0'", "false");
        if (version(resource) == 11) {
          sql = sql.replaceAll(",\\s*add column", "; alter table user_interest add column");
        }
        ScriptUtils.executeSqlScript(
            connection,
            new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8), resource.getFilename()));
      }
      connection
          .createStatement()
          .executeUpdate("update matching_schedule_config set enabled=false");
    } catch (Exception e) {
      throw new IllegalStateException("Could not initialize isolated workflow database", e);
    }
  }

  private static int version(Resource resource) {
    return Integer.parseInt(resource.getFilename().split("__")[0].substring(1));
  }
}
