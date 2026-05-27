package com.etfc.ingestion.dto;

import com.etfc.testrun.TestResult;
import java.time.Instant;
import java.util.UUID;

public record TestResultResponse(
    UUID id,
    UUID runId,
    UUID caseId,
    String caseName,
    int attemptNumber,
    String status,
    Long durationMs,
    String failureCategory,
    String failureMessage,
    String logReference,
    Instant createdAt,
    Instant updatedAt) {

  public static TestResultResponse from(TestResult r) {
    return new TestResultResponse(
        r.getId(),
        r.getRun().getId(),
        r.getTestCase().getId(),
        r.getTestCase().getName(),
        r.getAttemptNumber(),
        r.getStatus().name(),
        r.getDurationMs(),
        r.getFailureCategory() != null ? r.getFailureCategory().name() : null,
        r.getFailureMessage(),
        r.getLogReference(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
