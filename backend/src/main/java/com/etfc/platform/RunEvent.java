package com.etfc.platform;

import com.etfc.ingestion.dto.TestRunResponse;
import java.time.Instant;
import java.util.UUID;

public record RunEvent(
    String type,
    UUID runId,
    String externalRunId,
    String deviceExternalId,
    String firmwareVersion,
    String suiteName,
    String status,
    String environment,
    Instant timestamp) {

  public static RunEvent of(String type, TestRunResponse run) {
    return new RunEvent(
        type,
        run.id(),
        run.externalRunId(),
        run.deviceExternalId(),
        run.firmwareVersion(),
        run.suiteName(),
        run.status(),
        run.environment(),
        Instant.now());
  }
}
