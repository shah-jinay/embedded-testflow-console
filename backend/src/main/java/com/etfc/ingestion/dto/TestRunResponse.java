package com.etfc.ingestion.dto;

import com.etfc.testrun.TestRun;
import java.time.Instant;
import java.util.UUID;

public record TestRunResponse(
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
    Instant updatedAt) {

  public static TestRunResponse from(TestRun run) {
    return new TestRunResponse(
        run.getId(),
        run.getExternalRunId(),
        run.getDevice().getId(),
        run.getDevice().getExternalDeviceId(),
        run.getFirmwareBuild().getVersion(),
        run.getSuite().getName(),
        run.getStatus().name(),
        run.getEnvironment(),
        run.getCorrelationId(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getCreatedAt(),
        run.getUpdatedAt());
  }
}
