package ch.junto;

import ch.junto.bootstrap.BootstrapAdminCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JuntoApplication {
  public static void main(String[] args) {
    if (BootstrapAdminCommand.matches(args)) {
      System.exit(BootstrapAdminCommand.run(args));
    }
    SpringApplication.run(JuntoApplication.class, args);
  }
}
