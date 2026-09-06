package ch.junto.migration;

import java.util.Arrays;
import java.util.EnumSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;

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
    MigrationInfo[] migrations = flyway.info().all();
    if (Arrays.stream(migrations)
        .map(MigrationInfo::getState)
        .anyMatch(state -> !CURRENT_STATES.contains(state))) {
      return false;
    }

    MigrationVersion highestResolvedVersion =
        Arrays.stream(migrations)
            .filter(migration -> migration.getState() != MigrationState.BASELINE)
            .map(MigrationInfo::getVersion)
            .filter(version -> version != null)
            .max(MigrationVersion::compareTo)
            .orElse(null);

    return Arrays.stream(migrations)
        .filter(migration -> migration.getState() == MigrationState.BASELINE)
        .allMatch(
            baseline ->
                highestResolvedVersion != null
                    && baseline.getVersion() != null
                    && baseline.getVersion().compareTo(highestResolvedVersion) <= 0);
  }
}
