package com.etfc.ingestion.dto;

import com.etfc.firmware.FirmwareBuild;
import java.time.Instant;
import java.util.UUID;

public record FirmwareBuildResponse(
    UUID id,
    String version,
    String branch,
    String commitHash,
    boolean releaseCandidate,
    Instant createdAt) {

  public static FirmwareBuildResponse from(FirmwareBuild fb) {
    return new FirmwareBuildResponse(
        fb.getId(),
        fb.getVersion(),
        fb.getBranch(),
        fb.getCommitHash(),
        fb.isReleaseCandidate(),
        fb.getCreatedAt());
  }
}
