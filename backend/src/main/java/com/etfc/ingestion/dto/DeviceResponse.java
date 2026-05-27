package com.etfc.ingestion.dto;

import com.etfc.device.Device;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeviceResponse(
    UUID id,
    String externalDeviceId,
    String boardRevision,
    String mcuFamily,
    String environment,
    String status,
    Instant lastSeenAt,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public static DeviceResponse from(Device d) {
    return new DeviceResponse(
        d.getId(),
        d.getExternalDeviceId(),
        d.getBoardRevision(),
        d.getMcuFamily(),
        d.getEnvironment(),
        d.getStatus().name(),
        d.getLastSeenAt(),
        d.getMetadata(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}
