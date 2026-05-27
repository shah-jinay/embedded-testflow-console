package com.etfc.ingestion;

import com.etfc.testrun.TestRunStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Enforces the TestRun state machine. Terminal states (PASSED, FAILED, TIMED_OUT)
 * have no outgoing transitions. Any attempt to leave them is rejected.
 */
@Component
public class StatusTransitionValidator {

  private static final Map<TestRunStatus, Set<TestRunStatus>> VALID =
      Map.of(
          TestRunStatus.QUEUED,
          EnumSet.of(TestRunStatus.RUNNING, TestRunStatus.BLOCKED),
          TestRunStatus.RUNNING,
          EnumSet.of(
              TestRunStatus.PASSED,
              TestRunStatus.FAILED,
              TestRunStatus.TIMED_OUT,
              TestRunStatus.BLOCKED,
              TestRunStatus.NEEDS_REVIEW),
          TestRunStatus.NEEDS_REVIEW,
          EnumSet.of(TestRunStatus.PASSED, TestRunStatus.FAILED),
          TestRunStatus.BLOCKED,
          EnumSet.of(TestRunStatus.QUEUED, TestRunStatus.FAILED));

  public void validate(TestRunStatus current, TestRunStatus next) {
    Set<TestRunStatus> allowed = VALID.getOrDefault(current, Set.of());
    if (!allowed.contains(next)) {
      throw new IllegalStateException(
          "Invalid status transition "
              + current
              + " → "
              + next
              + ". Allowed from "
              + current
              + ": "
              + allowed);
    }
  }

  public boolean isTerminal(TestRunStatus status) {
    return status == TestRunStatus.PASSED
        || status == TestRunStatus.FAILED
        || status == TestRunStatus.TIMED_OUT;
  }
}
