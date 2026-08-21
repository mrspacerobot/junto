package ch.junto.bootstrap;

import ch.junto.JuntoApplication;
import ch.junto.invite.AccessCodeService;
import java.util.Arrays;
import java.util.EnumSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
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
@Import({BootstrapAdminService.class, AccessCodeService.class})
class BootstrapContext {

  private static final Logger log = LoggerFactory.getLogger(BootstrapContext.class);
  private static final EnumSet<MigrationState> NON_CURRENT_STATES =
      EnumSet.of(
          MigrationState.PENDING,
          MigrationState.ABOVE_TARGET,
          MigrationState.MISSING_SUCCESS,
          MigrationState.MISSING_FAILED,
          MigrationState.FAILED,
          MigrationState.UNDONE,
          MigrationState.FUTURE_SUCCESS,
          MigrationState.FUTURE_FAILED);

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
            "Refusing to bootstrap: the database schema is not current for this build. "
                + "Deploy the matching application version and let it migrate first. Flyway reported: "
                + result.getAllErrorMessages());
      }

      boolean hasNonCurrentMigration =
          Arrays.stream(flyway.info().all())
              .map(MigrationInfo::getState)
              .anyMatch(NON_CURRENT_STATES::contains);
      if (hasNonCurrentMigration) {
        throw new BootstrapAbortedException(
            "Refusing to bootstrap: the database schema is not current for this build. "
                + "Deploy the matching application version and let it migrate first.");
      }

      log.info(
          "Bootstrap: schema verified current at migration version {}", currentVersion(flyway));
    };
  }

  private static String currentVersion(Flyway flyway) {
    MigrationInfo current = flyway.info().current();
    return current == null ? "none" : String.valueOf(current.getVersion());
  }
}
