package ch.junto.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadinessCheckTest {

  @Test
  void readinessFailureIsCachedUntilExpiry() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet resultSet = mock(ResultSet.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    ResultSet columns = mock(ResultSet.class);
    ResultSet keys = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.getMetaData()).thenReturn(metaData);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(metaData.getColumns(null, null, "chat_rate_limits", null)).thenReturn(columns);
    when(metaData.getPrimaryKeys(null, null, "chat_rate_limits")).thenReturn(keys);
    when(columns.next()).thenReturn(true, true, true, true, false);
    when(columns.getString("COLUMN_NAME"))
        .thenReturn("activity_id", "user_id", "window_started_at", "request_count");
    when(keys.next()).thenReturn(true, true, false);
    when(keys.getString("COLUMN_NAME")).thenReturn("activity_id", "user_id");

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider());

    assertThat(readiness.isReady()).isFalse();
    assertThat(readiness.isReady()).isFalse();

    verify(dataSource).getConnection();
    verify(statement).setQueryTimeout(1);
    verify(statement).executeQuery(anyString());

    Thread.sleep(1100);

    assertThat(readiness.isReady()).isFalse();
    verify(dataSource, times(2)).getConnection();
  }

  @Test
  void readinessProbeBoundsQueryTimeoutAndChecksFlywayLazily() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet resultSet = mock(ResultSet.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    ResultSet columns = mock(ResultSet.class);
    ResultSet keys = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.getMetaData()).thenReturn(metaData);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(metaData.getColumns(null, null, "chat_rate_limits", null)).thenReturn(columns);
    when(metaData.getPrimaryKeys(null, null, "chat_rate_limits")).thenReturn(keys);
    when(columns.next()).thenReturn(true, true, true, true, false);
    when(columns.getString("COLUMN_NAME"))
        .thenReturn("activity_id", "user_id", "window_started_at", "request_count");
    when(keys.next()).thenReturn(true, true, false);
    when(keys.getString("COLUMN_NAME")).thenReturn("activity_id", "user_id");

    ObjectProvider<Flyway> flywayProvider = mock(ObjectProvider.class);
    when(flywayProvider.getIfAvailable()).thenReturn(null);

    ReadinessCheck readiness = new ReadinessCheck(dataSource, flywayProvider);

    assertThat(readiness.isReady()).isFalse();
    verify(statement).setQueryTimeout(1);
  }

  private static ObjectProvider<Flyway> flywayProvider() {
    @SuppressWarnings("unchecked")
    ObjectProvider<Flyway> flywayProvider = mock(ObjectProvider.class);
    when(flywayProvider.getIfAvailable()).thenReturn(null);
    return flywayProvider;
  }
}
