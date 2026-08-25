package ch.junto.bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;

class PostgresBootstrapLock {

  private static final String LOCK_BOOTSTRAP = "SELECT pg_advisory_xact_lock(?, ?)";
  private static final int LOCK_NAMESPACE = 0x4A554E54;
  private static final int LOCK_RESOURCE = 0x424F4F54;

  private final JdbcTemplate jdbcTemplate;

  PostgresBootstrapLock(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  void acquire() {
    jdbcTemplate.queryForObject(LOCK_BOOTSTRAP, Object.class, LOCK_NAMESPACE, LOCK_RESOURCE);
  }
}
