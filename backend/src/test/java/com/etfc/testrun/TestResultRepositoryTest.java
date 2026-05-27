package com.etfc.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.etfc.AbstractRepositoryTest;
import com.etfc.device.Device;
import com.etfc.device.DeviceRepository;
import com.etfc.firmware.FirmwareBuild;
import com.etfc.firmware.FirmwareBuildRepository;
import com.etfc.testsuite.TestCase;
import com.etfc.testsuite.TestCaseRepository;
import com.etfc.testsuite.TestSuite;
import com.etfc.testsuite.TestSuiteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TestResultRepositoryTest extends AbstractRepositoryTest {

  @Autowired
  TestResultRepository testResultRepository;

  @Autowired
  TestRunRepository testRunRepository;

  @Autowired
  TestCaseRepository testCaseRepository;

  @Autowired
  DeviceRepository deviceRepository;

  @Autowired
  FirmwareBuildRepository firmwareBuildRepository;

  @Autowired
  TestSuiteRepository testSuiteRepository;

  @Autowired
  EntityManager em;

  private TestRun run;
  private TestCase testCase;

  @BeforeEach
  void setUp() {
    var device = deviceRepository.save(Device.create("DEV-RES-001", "rev-B", "STM32", "lab"));
    var firmware = firmwareBuildRepository.save(
        FirmwareBuild.create("v2.0.0", "main", "def456", false));
    var suite = testSuiteRepository.save(
        TestSuite.create("CommValidation", "comms", "team-fw", "HIGH", "Comms tests"));
    testCase = testCaseRepository.save(
        TestCase.create(suite, "UART_LOOPBACK", "expect echo", 5000L, "CRITICAL"));
    run = testRunRepository.save(
        TestRun.create("ext-res-1", device, firmware, suite, "lab", "corr-res-1"));
    em.flush();
  }

  @Test
  void saves_passed_result_and_finds_by_run() {
    var result = TestResult.create(run, testCase, 1, TestRunStatus.PASSED, 1200L, null, null, null);
    testResultRepository.save(result);
    em.flush();
    em.clear();

    var results = testResultRepository.findByRunId(run.getId());
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getStatus()).isEqualTo(TestRunStatus.PASSED);
    assertThat(results.get(0).getDurationMs()).isEqualTo(1200L);
    assertThat(results.get(0).getFailureCategory()).isNull();
  }

  @Test
  void saves_failed_result_with_failure_category() {
    var result = TestResult.create(run, testCase, 1, TestRunStatus.FAILED, 5001L,
        FailureCategory.COMMUNICATION_TIMEOUT, "No response within 5000ms", "s3://logs/run-1");
    testResultRepository.save(result);
    em.flush();
    em.clear();

    var found = testResultRepository
        .findByRunIdAndTestCaseIdAndAttemptNumber(run.getId(), testCase.getId(), 1)
        .orElseThrow();
    assertThat(found.getFailureCategory()).isEqualTo(FailureCategory.COMMUNICATION_TIMEOUT);
    assertThat(found.getFailureMessage()).isEqualTo("No response within 5000ms");
    assertThat(found.getLogReference()).isEqualTo("s3://logs/run-1");
  }

  @Test
  void allows_retry_as_separate_attempt_number() {
    testResultRepository.save(
        TestResult.create(run, testCase, 1, TestRunStatus.FAILED, 5001L,
            FailureCategory.FLAKY_TEST, "flaky", null));
    testResultRepository.save(
        TestResult.create(run, testCase, 2, TestRunStatus.PASSED, 800L, null, null, null));
    em.flush();

    assertThat(testResultRepository.findByRunId(run.getId())).hasSize(2);
  }

  @Test
  void rejects_duplicate_run_case_attempt_tuple() {
    testResultRepository.save(
        TestResult.create(run, testCase, 1, TestRunStatus.PASSED, 100L, null, null, null));
    em.flush();

    assertThatThrownBy(() -> {
      testResultRepository.save(
          TestResult.create(run, testCase, 1, TestRunStatus.FAILED, 200L,
              FailureCategory.UNCLASSIFIED, "dup", null));
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void finds_results_by_failure_category() {
    testResultRepository.save(
        TestResult.create(run, testCase, 1, TestRunStatus.FAILED, 5001L,
            FailureCategory.DEVICE_UNAVAILABLE, "offline", null));
    em.flush();

    var results = testResultRepository.findByFailureCategory(FailureCategory.DEVICE_UNAVAILABLE);
    assertThat(results).hasSize(1);
  }
}
