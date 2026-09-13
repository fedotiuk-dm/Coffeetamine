package ua.coffeetamine.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ua.coffeetamine.CoffeetamineApplication;

import org.junit.jupiter.api.BeforeEach;

/**
 * Single inheritance point for every {@code *IT.java} class. Boots the full Spring context once
 * (the Spring context cache reuses the same context across IT classes when the configuration is
 * identical) and inherits {@link ContainersConfig} for Postgres.
 *
 * <p>Subclasses get: {@link MockMvc}, {@link EntityManager} for ad-hoc queries / flushes, a {@link
 * TransactionTemplate} for arranging multi-step fixtures, and an autowired {@link TestDataFactory}
 * for the usual seed helpers.
 *
 * <p><strong>Isolation:</strong> {@code @Transactional} on the class makes Spring's {@code
 * TransactionalTestExecutionListener} wrap each test method in a transaction and ROLLBACK at the
 * end — every test sees a clean schema (Liquibase-applied) but no leftover rows from siblings.
 * Tests that depend on a real commit (async {@code @ApplicationModuleListener} observing data,
 * etc.) MUST override with {@code @Commit} or {@code @Transactional(propagation = NEVER)} on the
 * method.
 *
 * <p>Auth: see {@link TestAuthSupport} — JWTs are synthetic. The MVC profile is {@code
 * integration-test} (see {@code application-integration-test.yml}).
 */
@SpringBootTest(
    classes = {CoffeetamineApplication.class, TestDataFactory.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Transactional
public abstract class BaseIntegrationTest extends ContainersConfig {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected TestDataFactory testData;
  @Autowired private PlatformTransactionManager txManager;

  @PersistenceContext protected EntityManager entityManager;

  protected TransactionTemplate tx;

  @BeforeEach
  void initTransactionTemplate() {
    this.tx = new TransactionTemplate(txManager);
  }

  protected void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
