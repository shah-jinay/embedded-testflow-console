package com.etfc.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.etfc.device.Device;
import com.etfc.device.DeviceRepository;
import com.etfc.firmware.FirmwareBuild;
import com.etfc.firmware.FirmwareBuildRepository;
import com.etfc.testsuite.TestSuite;
import com.etfc.testsuite.TestSuiteRepository;
import com.etfc.testrun.TestRun;
import com.etfc.testrun.TestRunRepository;
import com.etfc.testrun.TestRunStatus;
import java.time.Instant;
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
class DashboardQueryTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired TestRunRepository testRunRepository;
  @Autowired DeviceRepository deviceRepository;
  @Autowired FirmwareBuildRepository firmwareBuildRepository;
  @Autowired TestSuiteRepository testSuiteRepository;

  @BeforeEach
  void setUp() {
    testRunRepository.deleteAll();
    deviceRepository.deleteAll();
    firmwareBuildRepository.deleteAll();
    testSuiteRepository.deleteAll();
  }

  @Test
  void empty_database_returns_zeros() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRuns").value(0))
        .andExpect(jsonPath("$.passedRuns").value(0))
        .andExpect(jsonPath("$.failedRuns").value(0))
        .andExpect(jsonPath("$.passRate").value(0.0))
        .andExpect(jsonPath("$.failureRateBySuite").isArray())
        .andExpect(jsonPath("$.failureRateBySuite.length()").value(0))
        .andExpect(jsonPath("$.latestActivity").isArray());
  }

  @Test
  void counts_runs_by_status_correctly() throws Exception {
    var device = deviceRepository.save(Device.create("DASH-DEV-A", "rev-B", "STM32", "lab"));
    var fw = firmwareBuildRepository.save(FirmwareBuild.create("v1.0.0", "main", "abc", false));
    var suite = testSuiteRepository.save(TestSuite.create("Suite-A", "comp", "team", "HIGH", ""));

    saveRun("r1", device, fw, suite, TestRunStatus.PASSED);
    saveRun("r2", device, fw, suite, TestRunStatus.PASSED);
    saveRun("r3", device, fw, suite, TestRunStatus.FAILED);
    saveRun("r4", device, fw, suite, TestRunStatus.TIMED_OUT);

    mockMvc.perform(get("/api/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRuns").value(4))
        .andExpect(jsonPath("$.passedRuns").value(2))
        .andExpect(jsonPath("$.failedRuns").value(1))
        .andExpect(jsonPath("$.timedOutRuns").value(1))
        .andExpect(jsonPath("$.passRate").value(0.5));
  }

  @Test
  void firmware_version_filter_scopes_summary() throws Exception {
    var device = deviceRepository.save(Device.create("DASH-DEV-B", "rev-B", "STM32", "lab"));
    var fw1 = firmwareBuildRepository.save(FirmwareBuild.create("v2.0.0", "main", "ccc", false));
    var fw2 = firmwareBuildRepository.save(FirmwareBuild.create("v3.0.0", "main", "ddd", false));
    var suite = testSuiteRepository.save(TestSuite.create("Suite-B", "comp", "team", "HIGH", ""));

    saveRun("r1", device, fw1, suite, TestRunStatus.PASSED);
    saveRun("r2", device, fw2, suite, TestRunStatus.FAILED);

    mockMvc.perform(get("/api/dashboard/summary").param("firmwareVersion", "v2.0.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRuns").value(1))
        .andExpect(jsonPath("$.passedRuns").value(1))
        .andExpect(jsonPath("$.failedRuns").value(0));
  }

  @Test
  void failure_count_by_firmware_lists_firmware_with_most_failures() throws Exception {
    var device = deviceRepository.save(Device.create("DASH-DEV-C", "rev-B", "STM32", "lab"));
    var fw1 = firmwareBuildRepository.save(FirmwareBuild.create("v4.0.0", "main", "eee", false));
    var fw2 = firmwareBuildRepository.save(FirmwareBuild.create("v4.1.0", "main", "fff", false));
    var suite = testSuiteRepository.save(TestSuite.create("Suite-C", "comp", "team", "HIGH", ""));

    saveRun("r1", device, fw1, suite, TestRunStatus.FAILED);
    saveRun("r2", device, fw1, suite, TestRunStatus.FAILED);
    saveRun("r3", device, fw2, suite, TestRunStatus.FAILED);

    mockMvc.perform(get("/api/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failureCountByFirmware[0].version").value("v4.0.0"))
        .andExpect(jsonPath("$.failureCountByFirmware[0].failureCount").value(2));
  }

  @Test
  void latest_activity_returns_most_recent_runs() throws Exception {
    var device = deviceRepository.save(Device.create("DASH-DEV-D", "rev-B", "STM32", "lab"));
    var fw = firmwareBuildRepository.save(FirmwareBuild.create("v5.0.0", "main", "ggg", false));
    var suite = testSuiteRepository.save(TestSuite.create("Suite-D", "comp", "team", "HIGH", ""));

    for (int i = 1; i <= 3; i++) {
      saveRun("act-r" + i, device, fw, suite, TestRunStatus.PASSED);
    }

    mockMvc.perform(get("/api/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latestActivity").isArray())
        .andExpect(jsonPath("$.latestActivity.length()").value(3));
  }

  // ── helper ───────────────────────────────────────────────────────────────

  private TestRun saveRun(String extId, Device d, FirmwareBuild fw, TestSuite s,
      TestRunStatus status) {
    TestRun run = TestRun.create(extId, d, fw, s, "lab", null);
    run.setStatus(status);
    run.setStartedAt(Instant.now());
    if (status == TestRunStatus.PASSED || status == TestRunStatus.FAILED
        || status == TestRunStatus.TIMED_OUT) {
      run.setCompletedAt(Instant.now());
    }
    return testRunRepository.save(run);
  }
}
