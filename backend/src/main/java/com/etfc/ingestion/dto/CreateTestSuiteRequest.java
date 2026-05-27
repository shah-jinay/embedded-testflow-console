package com.etfc.ingestion.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTestSuiteRequest(
    @NotBlank String name,
    String targetComponent,
    String owner,
    String severity,
    String description) {}
