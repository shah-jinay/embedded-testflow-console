package com.etfc.ingestion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record IngestResultRequest(
    @NotBlank String caseName,
    @Min(1) int attemptNumber,
    @NotBlank String status,
    Long durationMs,
    String failureCategory,
    String failureMessage,
    String logReference) {}
