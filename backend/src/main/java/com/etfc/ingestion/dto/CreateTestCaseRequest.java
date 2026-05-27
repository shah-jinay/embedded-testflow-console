package com.etfc.ingestion.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTestCaseRequest(
    @NotBlank String name,
    String expectedBehavior,
    Long timeoutMs,
    String criticality) {}
