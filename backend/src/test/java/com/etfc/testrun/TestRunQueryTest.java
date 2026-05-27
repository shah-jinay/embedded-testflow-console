package com.etfc.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.etfc.device.Device;
import com.etfc.device.DeviceRepository;
import com.etfc.firmware.FirmwareBuild;
import com.etfc.firmware.FirmwareBuildRepository;
import com.etfc.ingestion.FailureCategoryTranslator;
import com.etfc.ingestion.StatusTranslator;
import com.etfc.ingestion.StatusTransitionValidator;
import com.etfc.testsuite.TestCase;
import com.etfc.testsuite.TestCaseRepository;
import com.etfc.testsuite.TestSuite;
import com.etfc.testsuite.TestSuiteRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TestRunQueryTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired TestRunRepository testRunRepository;
  @Autowired TestResultRepository testResultRepository;
  @Autowired DeviceRepository deviceRepository;
  @Autowired FirmwareBuildRepository firmwareBuildRepository;
  @Autowired TestSuiteRepository testSuiteRepository;
  @Autowired TestCaseRepository testCaseRepository;
  @Autowired FailureCategoryTranslator failureCategoryTranslator;

  private Device deviceA;
  private Device deviceB;
  private FirmwareBuild fw284;
  private FirmwareBuild fw290;
  private TestSuite suiteA;
  private TestSuite suiteB;
  private TestCase caseA;
  private TestRun runA1;
  private TestRun runA2;
  private TestRun runB1;

  @BeforeEach
  void setUp() {
    testResultRepository.deleteAll();
    testRunRepository.deleteAll();
    deviceRepository.deleteAll();
    firmwareBuildRepository.deleteAll();
    testCaseRepository.deleteAll();
    testSuiteRepository.deleteAll();

    deviceA = deviceRepository.save(Device.create("DEV-QUERY-A", "rev-B", "STM32", "lab"));
    deviceB = deviceRepository.save(Device.create("DEV-QUERY-B", "rev-A", "NRF52", "ci"));
    fw284 = firmwareBuildRepository.save(FirmwareBuild.create("v2.8.4", "release", "aaa", true));
    fw290 = firmwareBuildRepository.save(FirmwareBuild.create("v2.9.0", "main", "bbb", false));
    suiteA = testSuiteRepository.save(TestSuite.create("PowerCycle", "power", "team", "HIGH", ""));
    suiteB = testSuiteRepository.save(TestSuite.create("CommValidation", "comms", "team", "HIGH", ""));
    caseA = testCaseRepository.save(TestCase.create(suiteA, "UART_LOOPBACK", "echo", 5000L, "CRITICAL"));

    Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

    runA1 = testRunRepository.save(
        TestRun.create("run-a1", deviceA, fw284, suiteA, "lab", "corr-a1"));
    runA1.setStatus(TestRunStatus.FAILED);
    runA1.setStartedAt(yesterday);
    runA1 = testRunRepository.save(runA1);

    runA2 = testRunRepository.save(
        TestRun.create("run-a2", deviceA, fw290, suiteA, "lab", "corr-a2"));
    runA2.setStatus(TestRunStatus.PASSED);
    runA2.setStartedAt(Instant.now());
    runA2 = testRunRepository.save(runA2);

    runB1 = testRunRepository.save(
        TestRun.create("run-b1", deviceB, fw284, suiteB, "ci", "corr-b1"));
    runB1.setStatus(TestRunStatus.FAILED);
    runB1.setStartedAt(yesterday);
    runB1 = testRunRepository.save(runB1);

    // Add a result with COMMUNICATION_TIMEOUT to runA1
    testResultRepository.save(
        TestResult.create(runA1, caseA, 1, TestRunStatus.FAILED, 5001L,
            FailureCategory.COMMUNICATION_TIMEOUT, "timeout", null));
  }

  @Test
  void no_filter_returns_all_runs_paginated() throws Exception {
    mockMvc.perform(get("/api/test-runs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void filter_by_device_id() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("deviceId", deviceA.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void filter_by_firmware_version() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("firmwareVersion", "v2.8.4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void filter_by_board_revision() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("boardRevision", "rev-B"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void filter_by_suite() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("suite", "CommValidation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void filter_by_status() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("status", "PASSED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].status").value("PASSED"));
  }

  @Test
  void filter_by_failure_category_uses_subquery() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("failureCategory", "COMMUNICATION_TIMEOUT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].externalRunId").value("run-a1"));
  }

  @Test
  void filter_by_environment() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("environment", "ci"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void filter_by_date_range_excludes_old_runs() throws Exception {
    String fromTs = Instant.now().minus(1, ChronoUnit.HOURS).toString();
    mockMvc.perform(get("/api/test-runs").param("from", fromTs))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1)); // only runA2 started recently
  }

  @Test
  void pagination_respects_size_param() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  void size_is_capped_at_200() throws Exception {
    mockMvc.perform(get("/api/test-runs").param("size", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(200));
  }

  @Test
  void run_detail_includes_results() throws Exception {
    mockMvc.perform(get("/api/test-runs/" + runA1.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correlationId").value("corr-a1"))
        .andExpect(jsonPath("$.results").isArray())
        .andExpect(jsonPath("$.results.length()").value(1))
        .andExpect(jsonPath("$.results[0].failureCategory").value("COMMUNICATION_TIMEOUT"));
  }

  @Test
  void unknown_run_id_returns_404() throws Exception {
    mockMvc.perform(get("/api/test-runs/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
