package ua.coffeetamine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Coffeetamine bootstrap. Located at the {@code ua.coffeetamine} root so Spring Boot auto-scans
 * every feature module ({@code ua.coffeetamine.user}, {@code ua.coffeetamine.presence}, …) without
 * explicit {@code @ComponentScan}, {@code @EntityScan} or {@code @EnableJpaRepositories}
 * declarations.
 *
 * <p>Adding a new module: 1) declare it in {@code main/pom.xml} dependencies, 2) put its code under
 * {@code ua.coffeetamine.<module>}, 3) add Liquibase changelog include in {@code
 * db/changelog/coffeetamine-changelog.yaml}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("ua.coffeetamine")
@EnableAsync
@EnableScheduling
public class CoffeetamineApplication {

  static void main(String[] args) {
    SpringApplication.run(CoffeetamineApplication.class, args);
  }
}
