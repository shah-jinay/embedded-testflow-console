package com.etfc.testrun;

import java.time.Instant;
import java.util.UUID;

public record TestRunFilter(
    UUID deviceId,
    String firmwareVersion,
    String boardRevision,
    String suite,
    String status,
    String failureCategory,
    Instant from,
    Instant to,
    String environment) {}
