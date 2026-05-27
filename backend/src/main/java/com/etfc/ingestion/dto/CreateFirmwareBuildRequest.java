package com.etfc.ingestion.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFirmwareBuildRequest(
    @NotBlank String version,
    String branch,
    String commitHash,
    boolean releaseCandidate) {}
