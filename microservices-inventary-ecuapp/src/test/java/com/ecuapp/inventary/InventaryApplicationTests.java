package com.ecuapp.inventary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
@SpringBootTest
@ActiveProfiles("test")
class InventaryApplicationTests {
 @Autowired JdbcTemplate jdbc;
 @Test void contextLoadsWithAllMigrationsAppliedAndMappingsValidated() {
  // Flyway migrates and Hibernate ddl-auto=validate checks every entity while the context starts.
  assertEquals("17",jdbc.queryForObject("select max(version::int)::text from flyway_schema_history where success",String.class));
  assertEquals(0,jdbc.queryForObject("select count(*) from flyway_schema_history where not success",Integer.class));
 }
}
