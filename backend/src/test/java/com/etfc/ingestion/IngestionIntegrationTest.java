package com.etfc.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.etfc.platform.CorrelationIdFilter;
import com.etfc.testsuite.TestCase;
import com.etfc.testsuite.TestCaseRepository;
import com.etfc.testsuite.TestSuite;
import com.etfc.testsuite.TestSuiteRepository;
import com.etfc.testrun.TestResultRepository;
import com.etfc.testrun.TestRunRepository;
import com.etfc.device.DeviceRepository;
import com.etfc.firmware.FirmwareBuildRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngestionIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TestSuiteRepository testSuiteRepository;
  @Autowired TestCaseRepository testCaseRepository;
  @Autowired TestRunRepository testRunRepository;
  @Autowired TestResultRepository testResultRepository;
  @Autowired DeviceRepository deviceRepository;
  @Autowired FirmwareBuildRepository firmwareBuildRepository;

  private TestSuite suite;
  private TestCase testCase;

  @BeforeEach
  void setUp() {
    // Clean mutable data between tests; suites/cases persist for the class.
    testResultRepository.deleteAll();
    testRunRepository.deleteAll();
    deviceRepository.deleteAll();
    firmwareBuildRepository.deleteAll();

    suite =
        testSuiteRepository
            .findByName("PowerCycleValidation")
            .orElseGet(
                () ->
                    testSuiteRepository.save(
                        TestSuite.create(
                            "PowerCycleValidation", "power", "team-hw", "HIGH", "desc")));
    testCase =
        testCaseRepository
            .findBySuiteIdAndName(suite.getId(), "UART_LOOPBACK")
            .orElseGet(
                () ->
                    testCaseRepository.save(
                        TestCase.create(suite, "UART_LOOPBACK", "expect echo", 5000L, "CRITICAL")));
  }

  // ── (a) Successful ingest ─────────────────────────────────────────────────

  @Test
  void registers_device_and_returns_201() throws Exception {
    mockMvc
        .perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("externalDeviceId", "DEV-A", "boardRevision", "rev-B",
                    "mcuFamily", "STM32H7", "environment", "lab"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.externalDeviceId", is("DEV-A")))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.id", notNullValue()));
  }

  @Test
  void registers_firmware_and_returns_201() throws Exception {
    mockMvc
        .perform(
            post("/api/firmware-builds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("version", "v1.0.0", "branch", "main",
                    "commitHash", "abc1234", "releaseCandidate", false))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version", is("v1.0.0")));
  }

  @Test
  void creates_test_run_and_returns_201() throws Exception {
    registerDevice("DEV-FULL");
    registerFirmware("v1.0.0");

    mockMvc
        .perform(
            post("/api/test-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "externalRunId", "run-001",
                    "deviceExternalId", "DEV-FULL",
                    "firmwareVersion", "v1.0.0",
                    "suiteName", "PowerCycleValidation",
                    "environment", "lab"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("QUEUED")))
        .andExpect(jsonPath("$.suiteName", is("PowerCycleValidation")))
        .andExpect(jsonPath("$.correlationId", notNullValue()));
  }

  @Test
  void full_run_lifecycle_ingests_results() throws Exception {
    registerDevice("DEV-LC");
    registerFirmware("v1.0.0");
    String runId = createRun("DEV-LC", "v1.0.0", "run-lc-001");

    patchStatus(runId, "RUNNING");

    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK",
                    "attemptNumber", 1,
                    "status", "PASSED",
                    "durationMs", 1200))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].caseName", is("UART_LOOPBACK")))
        .andExpect(jsonPath("$[0].status", is("PASSED")))
        .andExpect(jsonPath("$[0].failureCategory").doesNotExist());

    patchStatus(runId, "PASSED");
  }

  // ── (b) Duplicate submission is a no-op and returns existing record ───────

  @Test
  void duplicate_device_registration_returns_200_with_same_id() throws Exception {
    MvcResult first =
        mockMvc
            .perform(
                post("/api/devices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("externalDeviceId", "DEV-DUP"))))
            .andExpect(status().isCreated())
            .andReturn();

    String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/devices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("externalDeviceId", "DEV-DUP"))))
            .andExpect(status().isOk())
            .andReturn();

    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
    assertThat(secondId).isEqualTo(firstId);
    assertThat(deviceRepository.count()).isEqualTo(1);
  }

  @Test
  void duplicate_firmware_registration_returns_200_with_same_id() throws Exception {
    MvcResult first =
        mockMvc
            .perform(
                post("/api/firmware-builds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("version", "v-dup"))))
            .andExpect(status().isCreated())
            .andReturn();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/firmware-builds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("version", "v-dup"))))
            .andExpect(status().isOk())
            .andReturn();

    String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
    String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
    assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  void duplicate_test_run_returns_200_with_same_id() throws Exception {
    registerDevice("DEV-RDUP");
    registerFirmware("v2.0.0");

    String firstId = createRun("DEV-RDUP", "v2.0.0", "run-dup-001");

    MvcResult second =
        mockMvc
            .perform(
                post("/api/test-runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                        "externalRunId", "run-dup-001",
                        "deviceExternalId", "DEV-RDUP",
                        "firmwareVersion", "v2.0.0",
                        "suiteName", "PowerCycleValidation"))))
            .andExpect(status().isOk())
            .andReturn();

    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
    assertThat(secondId).isEqualTo(firstId);
    assertThat(testRunRepository.count()).isEqualTo(1);
  }

  @Test
  void duplicate_result_submission_upserts_in_place() throws Exception {
    registerDevice("DEV-RESDUP");
    registerFirmware("v3.0.0");
    String runId = createRun("DEV-RESDUP", "v3.0.0", "run-resdup");
    patchStatus(runId, "RUNNING");

    // First ingest
    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK", "attemptNumber", 1, "status", "FAILED",
                    "failureCategory", "COMMUNICATION_TIMEOUT", "durationMs", 5001))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status", is("FAILED")));

    // Second ingest — same (runId, caseName, attemptNumber), corrected status
    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK", "attemptNumber", 1, "status", "PASSED",
                    "durationMs", 1100))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status", is("PASSED")));

    // Only one record in DB — upserted, not duplicated
    assertThat(testResultRepository.count()).isEqualTo(1);
  }

  // ── (c) Partial / out-of-order updates merge correctly ───────────────────

  @Test
  void out_of_order_attempt_numbers_are_stored_independently() throws Exception {
    registerDevice("DEV-OOO");
    registerFirmware("v4.0.0");
    String runId = createRun("DEV-OOO", "v4.0.0", "run-ooo");
    patchStatus(runId, "RUNNING");

    // Attempt 2 arrives first
    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK", "attemptNumber", 2, "status", "PASSED",
                    "durationMs", 800))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].attemptNumber", is(2)));

    // Then attempt 1
    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK", "attemptNumber", 1, "status", "FAILED",
                    "failureCategory", "FLAKY_TEST", "durationMs", 5001))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].attemptNumber", is(1)));

    assertThat(testResultRepository.count()).isEqualTo(2);
  }

  @Test
  void invalid_status_transition_returns_422() throws Exception {
    registerDevice("DEV-TR");
    registerFirmware("v5.0.0");
    String runId = createRun("DEV-TR", "v5.0.0", "run-tr");

    // QUEUED → PASSED is not a valid transition
    mockMvc
        .perform(
            patch("/api/test-runs/" + runId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "PASSED"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code", is("INVALID_TRANSITION")));
  }

  // ── (d) Unknown failure strings fall back to UNCLASSIFIED ────────────────

  @Test
  void unknown_failure_category_becomes_unclassified() throws Exception {
    registerDevice("DEV-UNC");
    registerFirmware("v6.0.0");
    String runId = createRun("DEV-UNC", "v6.0.0", "run-unc");
    patchStatus(runId, "RUNNING");

    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK",
                    "attemptNumber", 1,
                    "status", "FAILED",
                    "failureCategory", "TOTALLY_UNKNOWN_CATEGORY_XYZ",
                    "durationMs", 9999))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].failureCategory", is("UNCLASSIFIED")));
  }

  @Test
  void null_failure_category_is_stored_as_null() throws Exception {
    registerDevice("DEV-NULLCAT");
    registerFirmware("v6.1.0");
    String runId = createRun("DEV-NULLCAT", "v6.1.0", "run-nullcat");
    patchStatus(runId, "RUNNING");

    mockMvc
        .perform(
            post("/api/test-runs/" + runId + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("results", List.of(Map.of(
                    "caseName", "UART_LOOPBACK",
                    "attemptNumber", 1,
                    "status", "PASSED",
                    "durationMs", 500))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].failureCategory").doesNotExist());
  }

  // ── Correlation ID propagation ────────────────────────────────────────────

  @Test
  void correlation_id_is_echoed_when_provided() throws Exception {
    String corrId = "test-corr-12345";
    mockMvc
        .perform(
            post("/api/devices")
                .header(CorrelationIdFilter.HEADER, corrId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("externalDeviceId", "DEV-CORR"))))
        .andExpect(status().isCreated())
        .andExpect(header().string(CorrelationIdFilter.HEADER, corrId));
  }

  @Test
  void correlation_id_is_generated_when_absent() throws Exception {
    mockMvc
        .perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("externalDeviceId", "DEV-NOCORR"))))
        .andExpect(status().isCreated())
        .andExpect(header().exists(CorrelationIdFilter.HEADER));
  }

  @Test
  void missing_required_field_returns_400_with_error_envelope() throws Exception {
    mockMvc
        .perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
        .andExpect(jsonPath("$.error.correlationId", notNullValue()));
  }

  @Test
  void unknown_device_reference_on_run_creation_returns_404() throws Exception {
    registerFirmware("v7.0.0");
    mockMvc
        .perform(
            post("/api/test-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "externalRunId", "run-nodev",
                    "deviceExternalId", "NONEXISTENT-DEV",
                    "firmwareVersion", "v7.0.0",
                    "suiteName", "PowerCycleValidation"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void registerDevice(String externalId) throws Exception {
    mockMvc.perform(
        post("/api/devices")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("externalDeviceId", externalId))));
  }

  private void registerFirmware(String version) throws Exception {
    mockMvc.perform(
        post("/api/firmware-builds")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("version", version))));
  }

  private String createRun(String deviceId, String firmware, String externalRunId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/test-runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                        "externalRunId", externalRunId,
                        "deviceExternalId", deviceId,
                        "firmwareVersion", firmware,
                        "suiteName", "PowerCycleValidation"))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void patchStatus(String runId, String status) throws Exception {
    mockMvc.perform(
        patch("/api/test-runs/" + runId + "/status")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("status", status))));
  }

  private String json(Object obj) throws Exception {
    return objectMapper.writeValueAsString(obj);
  }
}
