package com.etfc.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record CreateTestRunRequest(
    @NotBlank String externalRunId,
    @NotBlank String deviceExternalId,
    @NotBlank String firmwareVersion,
    @NotBlank String suiteName,
    String environment,
    Instant startedAt) {}
