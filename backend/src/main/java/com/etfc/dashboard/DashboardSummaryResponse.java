package com.etfc.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardSummaryResponse(
    long totalRuns,
    long passedRuns,
    long failedRuns,
    long timedOutRuns,
    long blockedRuns,
    long needsReviewRuns,
    double passRate,
    List<SuiteFailureRate> failureRateBySuite,
    List<FirmwareFailureCount> failureCountByFirmware,
    List<FailingDeviceSummary> recentFailingDevices,
    List<SuiteDurationSummary> slowestSuites,
    List<ActivityEntry> latestActivity) {

  public record SuiteFailureRate(String suiteName, long total, long failed, double failureRate) {}

  public record FirmwareFailureCount(String version, long failureCount) {}

  public record FailingDeviceSummary(
      UUID deviceId, String externalDeviceId, Instant lastFailedAt, long failureCount) {}

  public record SuiteDurationSummary(String suiteName, double avgDurationMs) {}

  public record ActivityEntry(
      UUID runId,
      String deviceExternalId,
      String suiteName,
      String status,
      Instant startedAt) {}
}
