package ua.coffeetamine.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers used by Spring integration tests in this JVM.
 *
 * <p>The container lives as a static field so the test context customizer sees one shared instance
 * across cached Spring contexts. Spring Boot's {@link ServiceConnection} support takes care of
 * starting it and wiring the datasource and Liquibase connection details into the environment.
 */
public abstract class ContainersConfig {

  @ServiceConnection
  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4")
          .withDatabaseName("coffeetamine_test")
          .withUsername("test")
          .withPassword("test");

  protected ContainersConfig() {}
}
