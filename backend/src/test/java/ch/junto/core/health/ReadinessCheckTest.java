package ch.junto.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ReadinessCheckTest {

  @Test
  void readinessFailureIsCachedUntilExpiry() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    SchemaFixture schema =
        schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"));

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider());

    assertThat(readiness.isReady()).isFalse();
    assertThat(readiness.isReady()).isFalse();

    verify(dataSource).getConnection();
    verify(schema.statement()).setQueryTimeout(1);

    Thread.sleep(1100);

    assertThat(readiness.isReady()).isFalse();
    verify(dataSource, times(2)).getConnection();
  }

  @Test
  void readinessProbesTheNewestShippedMigrationsAndChecksFlywayLazily() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    SchemaFixture schema =
        schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"));
    ObjectProvider<Flyway> flywayProvider = flywayProvider();

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider);

    assertThat(readiness.isReady()).isFalse();

    verify(schema.connection()).setNetworkTimeout(any(Executor.class), eq(1000));
    verify(schema.statement()).setQueryTimeout(1);
    verify(schema.metaData()).getColumns(null, "public", "auth_tokens", "invite_code");
    verify(flywayProvider).getIfAvailable();
    ArgumentCaptor<String> probes = ArgumentCaptor.forClass(String.class);
    verify(schema.statement(), times(3)).executeQuery(probes.capture());
    assertThat(probes.getAllValues())
        .anyMatch(probe -> probe.contains("chat_rate_limits"))
        .anyMatch(probe -> probe.contains("access_codes") && probe.contains("created_at"))
        .anyMatch(probe -> probe.contains("access_code_redemptions"));
  }

  @Test
  void narrowedInviteCodeColumnIsNotReady() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"), 12);
    ObjectProvider<Flyway> flywayProvider = flywayProvider();

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider);

    assertThat(readiness.isReady()).isFalse();
    verify(flywayProvider, never()).getIfAvailable();
  }

  @Test
  void similarlyNamedInviteCodeColumnIsNotReady() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"), 64, "invitexcode");
    ObjectProvider<Flyway> flywayProvider = flywayProvider();

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider);

    assertThat(readiness.isReady()).isFalse();
    verify(flywayProvider, never()).getIfAvailable();
  }

  @Test
  void flywayValidationUsesNetworkBoundedConnections() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    SchemaFixture schema =
        schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"));
    Flyway configured = Flyway.configure().dataSource(dataSource).load();

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider(configured));

    assertThat(readiness.isReady()).isFalse();
    verify(dataSource, atLeast(2)).getConnection();
    verify(schema.connection(), atLeast(2)).setNetworkTimeout(any(Executor.class), eq(1000));
  }

  @Test
  void requiredChatRateLimitKeyDriftIsNotReady() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    schemaWithChatRateLimitKey(dataSource, List.of("activity_id"));
    ObjectProvider<Flyway> flywayProvider = flywayProvider();

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider);

    assertThat(readiness.isReady()).isFalse();
    verify(flywayProvider, never()).getIfAvailable();
  }

  @Test
  void stalledDatabaseTimesOutAndTheAbandonedProbeIsCancelled() throws Exception {
    CountDownLatch probeStarted = new CountDownLatch(1);
    CountDownLatch probeInterrupted = new CountDownLatch(1);
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection())
        .thenAnswer(
            invocation -> {
              probeStarted.countDown();
              try {
                Thread.sleep(Duration.ofMinutes(1).toMillis());
              } catch (InterruptedException e) {
                probeInterrupted.countDown();
              }
              throw new SQLException("the database never answered");
            });

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider());

    long startedAt = System.nanoTime();
    assertThat(readiness.isReady()).isFalse();
    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
        .isBetween(Duration.ofSeconds(2), Duration.ofSeconds(20));
    assertThat(probeStarted.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(probeInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
  }

  private static SchemaFixture schemaWithChatRateLimitKey(
      DataSource dataSource, List<String> keyColumns) throws SQLException {
    return schemaWithChatRateLimitKey(dataSource, keyColumns, 64, "invite_code");
  }

  private static SchemaFixture schemaWithChatRateLimitKey(
      DataSource dataSource, List<String> keyColumns, int inviteCodeLength) throws SQLException {
    return schemaWithChatRateLimitKey(dataSource, keyColumns, inviteCodeLength, "invite_code");
  }

  private static SchemaFixture schemaWithChatRateLimitKey(
      DataSource dataSource, List<String> keyColumns, int inviteCodeLength, String inviteCodeColumn)
      throws SQLException {
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet resultSet = mock(ResultSet.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    ResultSet keys = mock(ResultSet.class);
    ResultSet inviteCode = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.getMetaData()).thenReturn(metaData);
    when(connection.getSchema()).thenReturn("public");
    when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(metaData.getDatabaseMajorVersion()).thenReturn(16);
    when(metaData.getDatabaseMinorVersion()).thenReturn(0);
    when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost/junto");
    when(metaData.getUserName()).thenReturn("junto");
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(metaData.getPrimaryKeys(null, "public", "chat_rate_limits")).thenReturn(keys);
    when(metaData.getColumns(null, "public", "auth_tokens", "invite_code")).thenReturn(inviteCode);
    AtomicInteger keyCursor = new AtomicInteger();
    when(keys.next()).thenAnswer(invocation -> keyCursor.getAndIncrement() < keyColumns.size());
    when(keys.getString("COLUMN_NAME"))
        .thenAnswer(invocation -> keyColumns.get(keyCursor.get() - 1));
    when(inviteCode.next()).thenReturn(true, false);
    when(inviteCode.getString("TABLE_SCHEM")).thenReturn("public");
    when(inviteCode.getString("TABLE_NAME")).thenReturn("auth_tokens");
    when(inviteCode.getString("COLUMN_NAME")).thenReturn(inviteCodeColumn);
    when(inviteCode.getInt("COLUMN_SIZE")).thenReturn(inviteCodeLength);
    return new SchemaFixture(connection, statement, metaData);
  }

  private static ObjectProvider<Flyway> flywayProvider() {
    return flywayProvider(null);
  }

  private static ObjectProvider<Flyway> flywayProvider(Flyway flyway) {
    @SuppressWarnings("unchecked")
    ObjectProvider<Flyway> flywayProvider = mock(ObjectProvider.class);
    when(flywayProvider.getIfAvailable()).thenReturn(flyway);
    return flywayProvider;
  }

  private record SchemaFixture(
      Connection connection, Statement statement, DatabaseMetaData metaData) {}
}
