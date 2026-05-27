package com.etfc.device;

import com.etfc.ingestion.dto.DeviceResponse;
import com.etfc.ingestion.dto.TestRunResponse;
import com.etfc.testrun.TestRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeviceDetailResponse(
    UUID id,
    String externalDeviceId,
    String boardRevision,
    String mcuFamily,
    String environment,
    String status,
    String currentFirmwareVersion,
    Instant lastSeenAt,
    Map<String, Object> metadata,
    long totalRuns,
    long failedRuns,
    List<TestRunResponse> recentRuns,
    Instant createdAt,
    Instant updatedAt) {

  public static DeviceDetailResponse from(
      Device device, long totalRuns, long failedRuns, List<TestRun> recentRuns) {
    DeviceResponse base = DeviceResponse.from(device);
    String fwVersion =
        device.getCurrentFirmwareBuild() != null
            ? device.getCurrentFirmwareBuild().getVersion()
            : null;
    return new DeviceDetailResponse(
        base.id(),
        base.externalDeviceId(),
        base.boardRevision(),
        base.mcuFamily(),
        base.environment(),
        base.status(),
        fwVersion,
        base.lastSeenAt(),
        base.metadata(),
        totalRuns,
        failedRuns,
        recentRuns.stream().map(TestRunResponse::from).toList(),
        base.createdAt(),
        base.updatedAt());
  }
}
