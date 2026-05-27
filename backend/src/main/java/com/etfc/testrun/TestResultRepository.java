package com.etfc.testrun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

  Optional<TestResult> findByRunIdAndTestCaseIdAndAttemptNumber(
      UUID runId, UUID caseId, int attemptNumber);

  List<TestResult> findByRunId(UUID runId);

  List<TestResult> findByFailureCategory(FailureCategory failureCategory);
}
