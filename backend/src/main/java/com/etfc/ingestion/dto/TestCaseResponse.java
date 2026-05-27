package com.etfc.ingestion.dto;

import com.etfc.testsuite.TestCase;
import java.util.UUID;

public record TestCaseResponse(
    UUID id,
    UUID suiteId,
    String name,
    String expectedBehavior,
    Long timeoutMs,
    String criticality) {

  public static TestCaseResponse from(TestCase tc) {
    return new TestCaseResponse(
        tc.getId(),
        tc.getSuite().getId(),
        tc.getName(),
        tc.getExpectedBehavior(),
        tc.getTimeoutMs(),
        tc.getCriticality());
  }
}
