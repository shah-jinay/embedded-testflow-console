package com.etfc.testrun;

import com.etfc.ingestion.dto.TestResultResponse;
import com.etfc.ingestion.dto.TestRunResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestRunDetailResponse(
    UUID id,
    String externalRunId,
    UUID deviceId,
    String deviceExternalId,
    String firmwareVersion,
    String suiteName,
    String status,
    String environment,
    String correlationId,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    List<TestResultResponse> results) {

  public static TestRunDetailResponse from(TestRun run, List<TestResult> results) {
    TestRunResponse base = TestRunResponse.from(run);
    return new TestRunDetailResponse(
        base.id(),
        base.externalRunId(),
        base.deviceId(),
        base.deviceExternalId(),
        base.firmwareVersion(),
        base.suiteName(),
        base.status(),
        base.environment(),
        base.correlationId(),
        base.startedAt(),
        base.completedAt(),
        base.createdAt(),
        base.updatedAt(),
        results.stream().map(TestResultResponse::from).toList());
  }
}
