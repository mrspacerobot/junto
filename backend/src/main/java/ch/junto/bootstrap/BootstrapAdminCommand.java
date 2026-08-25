package ch.junto.bootstrap;

import java.util.Arrays;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The one-time production bootstrap, run as {@code java -jar app.jar bootstrap-admin}.
 *
 * <p>The administrator address and the initial access code are read from the environment at
 * execution time, never from committed configuration, a migration, or an HTTP request, and never
 * from the command line where a process listing would expose them. The operator removes both
 * variables once the command has succeeded.
 *
 * <p>The command starts a persistence-only context (see {@link BootstrapContext}), writes, and
 * exits. No web server, security filter chain, or scheduler is ever created.
 */
public final class BootstrapAdminCommand {

  public static final String COMMAND = "bootstrap-admin";

  static final String EMAIL_VARIABLE = "JUNTO_BOOTSTRAP_ADMIN_EMAIL";
  static final String ACCESS_CODE_VARIABLE = "JUNTO_BOOTSTRAP_ACCESS_CODE";

  static final int SUCCESS = 0;
  static final int FAILED = 1;
  static final int USAGE = 2;

  private static final String[] REQUIRED_PROPERTIES = {
    "--spring.main.web-application-type=none",
    "--spring.flyway.enabled=true",
    "--app.scheduling.enabled=false"
  };

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminCommand.class);

  private BootstrapAdminCommand() {}

  public static boolean matches(String[] args) {
    return args.length > 0 && COMMAND.equals(args[0]);
  }

  public static int run(String[] args) {
    return run(args, System::getenv);
  }

  static int run(String[] args, UnaryOperator<String> environment, String... additionalProperties) {
    if (args.length != 1) {
      return usage("takes no arguments");
    }

    String email = environment.apply(EMAIL_VARIABLE);
    String accessCode = environment.apply(ACCESS_CODE_VARIABLE);
    if (isBlank(email) || isBlank(accessCode)) {
      return usage("requires both variables to be set");
    }

    SpringApplication application = new SpringApplication(BootstrapContext.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setBannerMode(Banner.Mode.OFF);

    try (ConfigurableApplicationContext context =
        application.run(properties(additionalProperties))) {
      context.getBean(BootstrapAdminService.class).bootstrap(email, accessCode);
    } catch (RuntimeException exception) {
      BootstrapAbortedException aborted = abortedCause(exception);
      if (aborted == null) {
        log.error(
            "Bootstrap failed before completing; no committed changes were made (failure type={})",
            exception.getClass().getSimpleName());
        return FAILED;
      }
      log.error(aborted.getMessage());
      return FAILED;
    }

    log.info(
        "Bootstrap: complete. Remove {} and {} from the environment now.",
        EMAIL_VARIABLE,
        ACCESS_CODE_VARIABLE);
    return SUCCESS;
  }

  private static String[] properties(String[] additionalProperties) {
    String[] properties =
        Arrays.copyOf(
            REQUIRED_PROPERTIES, REQUIRED_PROPERTIES.length + additionalProperties.length);
    System.arraycopy(
        additionalProperties,
        0,
        properties,
        REQUIRED_PROPERTIES.length,
        additionalProperties.length);
    return properties;
  }

  private static int usage(String problem) {
    log.error(
        "Usage: java -jar app.jar {} ({}). Supply the administrator address in {} and the initial"
            + " access code in {}, then remove both once the command reports success.",
        COMMAND,
        problem,
        EMAIL_VARIABLE,
        ACCESS_CODE_VARIABLE);
    return USAGE;
  }

  private static BootstrapAbortedException abortedCause(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof BootstrapAbortedException aborted) {
        return aborted;
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
