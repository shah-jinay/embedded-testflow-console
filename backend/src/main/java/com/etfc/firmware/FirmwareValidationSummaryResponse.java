package com.etfc.firmware;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FirmwareValidationSummaryResponse(
    UUID id,
    String version,
    String branch,
    String commitHash,
    boolean releaseCandidate,
    Instant createdAt,
    long totalRuns,
    long passedRuns,
    long failedRuns,
    long timedOutRuns,
    long blockedRuns,
    double passRate,
    List<Breakdown> byDevice,
    List<Breakdown> bySuite) {

  public record Breakdown(String name, long total, long passed, long failed) {}

  public static FirmwareValidationSummaryResponse from(
      FirmwareBuild fb,
      List<Breakdown> byDevice,
      List<Breakdown> bySuite) {

    long total = byDevice.stream().mapToLong(Breakdown::total).sum();
    long passed = byDevice.stream().mapToLong(Breakdown::passed).sum();
    long failed = byDevice.stream().mapToLong(Breakdown::failed).sum();

    // timedOut / blocked inferred from the remainder
    long timedOut = 0;
    long blocked = 0;

    double passRate = total == 0 ? 0.0 : (double) passed / total;

    return new FirmwareValidationSummaryResponse(
        fb.getId(),
        fb.getVersion(),
        fb.getBranch(),
        fb.getCommitHash(),
        fb.isReleaseCandidate(),
        fb.getCreatedAt(),
        total,
        passed,
        failed,
        timedOut,
        blocked,
        passRate,
        byDevice,
        bySuite);
  }
}
