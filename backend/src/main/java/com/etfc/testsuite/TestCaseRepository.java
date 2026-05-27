package com.etfc.testsuite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

  Optional<TestCase> findBySuiteIdAndName(UUID suiteId, String name);

  List<TestCase> findBySuiteId(UUID suiteId);
}
