package ch.junto.bootstrap;

import ch.junto.JuntoApplication;
import ch.junto.invite.AccessCodeService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The application context of the one-time bootstrap command, deliberately not the application's.
 *
 * <p>It declares only persistence: nothing here component-scans {@code ch.junto}, so no controller,
 * no security filter chain, no WebSocket broker and no scheduled job exists in this context, and
 * {@link BootstrapAdminService} is not a bean of the running application at all. That absence, not
 * a guard inside a request handler, is why bootstrap cannot be reached over HTTP.
 */
@EnableAutoConfiguration
@EntityScan(basePackageClasses = JuntoApplication.class)
@EnableJpaRepositories(basePackageClasses = JuntoApplication.class)
@Import({BootstrapAdminService.class, PostgresBootstrapLock.class, AccessCodeService.class})
class BootstrapContext {

  private static final Logger log = LoggerFactory.getLogger(BootstrapContext.class);
  private static final String NOT_CURRENT =
      "Refusing to bootstrap: the database schema is not current for this build. "
          + "Deploy the matching application version and let it migrate first.";
  private static final EnumSet<MigrationState> CURRENT_STATES =
      EnumSet.of(
          MigrationState.BASELINE,
          MigrationState.BASELINE_IGNORED,
          MigrationState.BELOW_BASELINE,
          MigrationState.SUCCESS,
          MigrationState.SUPERSEDED,
          MigrationState.AVAILABLE);

  /**
   * Replaces the startup migration with a verification. Bootstrap writes rows into a schema it
   * refuses to change: a database behind, ahead of, or inconsistent with this build is reported and
   * nothing is written. Spring Boot runs this before the entity manager is created, so it is also
   * the first thing the command can fail on.
   */
  @Bean
  FlywayMigrationStrategy bootstrapSchemaVerification() {
    return flyway -> {
      ValidateResult result;
      try {
        result = flyway.validateWithResult();
      } catch (FlywayException exception) {
        throw new BootstrapAbortedException(
            "Refusing to bootstrap: the database schema could not be verified. "
                + exception.getMessage());
      }

      if (!result.validationSuccessful) {
        throw new BootstrapAbortedException(
            NOT_CURRENT + " Flyway reported: " + result.getAllErrorMessages());
      }

      MigrationInfo[] migrations = flyway.info().all();
      boolean hasNonCurrentMigration =
          Arrays.stream(migrations)
              .map(MigrationInfo::getState)
              .anyMatch(state -> !CURRENT_STATES.contains(state));
      if (hasNonCurrentMigration) {
        throw new BootstrapAbortedException(NOT_CURRENT);
      }

      MigrationVersion bundled = highestVersion(migrations, BootstrapContext::isBundled);
      MigrationVersion applied =
          highestVersion(migrations, migration -> migration.getState().isApplied());
      if (bundled == null || applied == null || bundled.compareTo(applied) != 0) {
        throw new BootstrapAbortedException(
            NOT_CURRENT
                + " This build bundles migration version "
                + describe(bundled)
                + " while the schema history reports "
                + describe(applied)
                + ".");
      }

      log.info("Bootstrap: schema verified current at migration version {}", applied);
    };
  }

  /**
   * A schema history row may name a version this build knows nothing about, either because a newer
   * deployment applied it or because an operator baselined above it. Only a migration this build
   * actually carries on its classpath resolves to a physical script, so that is what decides which
   * version the command expects to find applied.
   */
  private static boolean isBundled(MigrationInfo migration) {
    String location = migration.getPhysicalLocation();
    return location != null && !location.isBlank();
  }

  private static MigrationVersion highestVersion(
      MigrationInfo[] migrations, Predicate<MigrationInfo> included) {
    return Arrays.stream(migrations)
        .filter(included)
        .map(MigrationInfo::getVersion)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  private static String describe(MigrationVersion version) {
    return version == null ? "none" : version.toString();
  }
}
