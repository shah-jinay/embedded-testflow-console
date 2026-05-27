package com.etfc.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record UpdateRunStatusRequest(@NotBlank String status, Instant completedAt) {}
