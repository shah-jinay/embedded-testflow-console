package com.etfc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers Postgres instance for all repository integration tests.
 * The container is started once per JVM and reused across test classes.
 * Each test method runs in a transaction that is rolled back after completion.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Rollback
public abstract class AbstractRepositoryTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");
}
