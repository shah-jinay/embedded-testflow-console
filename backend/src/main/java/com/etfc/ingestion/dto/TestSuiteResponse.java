package com.etfc.ingestion.dto;

import com.etfc.testsuite.TestSuite;
import java.util.UUID;

public record TestSuiteResponse(
    UUID id,
    String name,
    String targetComponent,
    String owner,
    String severity,
    String description) {

  public static TestSuiteResponse from(TestSuite s) {
    return new TestSuiteResponse(
        s.getId(), s.getName(), s.getTargetComponent(), s.getOwner(), s.getSeverity(), s.getDescription());
  }
}
