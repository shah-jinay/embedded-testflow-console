package com.etfc.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateDeviceRequest(
    @NotBlank String externalDeviceId,
    String boardRevision,
    String mcuFamily,
    String environment,
    Map<String, Object> metadata) {}
