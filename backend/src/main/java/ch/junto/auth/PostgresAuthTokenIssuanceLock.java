package ch.junto.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-specific transaction lock for the delete/insert/deliver sequence.
 *
 * <p>The partial unique index {@code idx_auth_tokens_one_unused_per_email} is
 * the final database invariant. The advisory lock additionally makes
 * concurrent replacements wait instead of surfacing a unique-key failure, and
 * keeps delivery ordered with the transaction that makes its token current.
 */
@Repository
public class PostgresAuthTokenIssuanceLock implements AuthTokenIssuanceLock {

    private static final String LOCK_NORMALIZED_EMAIL =
        "SELECT pg_advisory_xact_lock(hashtext(?))";

    private final JdbcTemplate jdbcTemplate;

    public PostgresAuthTokenIssuanceLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void acquire(String normalizedEmail) {
        jdbcTemplate.queryForObject(LOCK_NORMALIZED_EMAIL, Object.class, normalizedEmail);
    }
}
