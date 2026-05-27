package com.etfc.ingestion;

import com.etfc.testrun.TestRunStatus;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Translates raw status strings from test runners to the internal TestRunStatus enum.
 * Unlike FailureCategoryTranslator, unknown statuses are rejected — callers must use
 * valid values so that the state machine remains consistent.
 */
@Component
public class StatusTranslator {

  public TestRunStatus translate(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("status is required");
    }
    try {
      return TestRunStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown status '"
              + raw
              + "'. Valid values: "
              + Arrays.toString(TestRunStatus.values()));
    }
  }
}
