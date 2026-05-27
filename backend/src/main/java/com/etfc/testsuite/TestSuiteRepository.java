package com.etfc.testsuite;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID> {

  Optional<TestSuite> findByName(String name);

  boolean existsByName(String name);
}
