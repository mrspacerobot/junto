package ch.junto.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
    Statement statement = schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"));

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider());

    assertThat(readiness.isReady()).isFalse();
    assertThat(readiness.isReady()).isFalse();

    verify(dataSource).getConnection();
    verify(statement).setQueryTimeout(1);

    Thread.sleep(1100);

    assertThat(readiness.isReady()).isFalse();
    verify(dataSource, times(2)).getConnection();
  }

  @Test
  void readinessProbesTheNewestShippedMigrationsAndChecksFlywayLazily() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Statement statement = schemaWithChatRateLimitKey(dataSource, List.of("activity_id", "user_id"));
    ObjectProvider<Flyway> flywayProvider = flywayProvider();

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider);

    assertThat(readiness.isReady()).isFalse();

    verify(statement).setQueryTimeout(1);
    verify(flywayProvider).getIfAvailable();
    ArgumentCaptor<String> probes = ArgumentCaptor.forClass(String.class);
    verify(statement, times(3)).executeQuery(probes.capture());
    assertThat(probes.getAllValues())
        .anyMatch(probe -> probe.contains("chat_rate_limits"))
        .anyMatch(probe -> probe.contains("access_codes"))
        .anyMatch(probe -> probe.contains("access_code_redemptions"));
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

  private static Statement schemaWithChatRateLimitKey(
      DataSource dataSource, List<String> keyColumns) throws SQLException {
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet resultSet = mock(ResultSet.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    ResultSet keys = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.getMetaData()).thenReturn(metaData);
    when(connection.getSchema()).thenReturn("public");
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(metaData.getPrimaryKeys(null, "public", "chat_rate_limits")).thenReturn(keys);
    AtomicInteger keyCursor = new AtomicInteger();
    when(keys.next()).thenAnswer(invocation -> keyCursor.getAndIncrement() < keyColumns.size());
    when(keys.getString("COLUMN_NAME"))
        .thenAnswer(invocation -> keyColumns.get(keyCursor.get() - 1));
    return statement;
  }

  private static ObjectProvider<Flyway> flywayProvider() {
    @SuppressWarnings("unchecked")
    ObjectProvider<Flyway> flywayProvider = mock(ObjectProvider.class);
    when(flywayProvider.getIfAvailable()).thenReturn(null);
    return flywayProvider;
  }
}
