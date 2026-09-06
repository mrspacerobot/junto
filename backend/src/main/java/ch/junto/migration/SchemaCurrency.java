package ch.junto.migration;

import java.util.Arrays;
import java.util.EnumSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;

public final class SchemaCurrency {

  private static final EnumSet<MigrationState> CURRENT_STATES =
      EnumSet.of(
          MigrationState.BASELINE,
          MigrationState.BASELINE_IGNORED,
          MigrationState.BELOW_BASELINE,
          MigrationState.SUCCESS,
          MigrationState.SUPERSEDED,
          MigrationState.AVAILABLE);

  private SchemaCurrency() {}

  public static boolean hasOnlyCurrentMigrations(Flyway flyway) {
    return Arrays.stream(flyway.info().all())
        .map(MigrationInfo::getState)
        .allMatch(CURRENT_STATES::contains);
  }
}
