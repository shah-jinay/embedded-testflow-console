package com.etfc.dashboard;

import java.time.Instant;

public record DashboardFilter(
    String firmwareVersion, Instant from, Instant to, String environment) {}
