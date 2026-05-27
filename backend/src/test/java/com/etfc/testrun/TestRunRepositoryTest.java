package com.etfc.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.etfc.AbstractRepositoryTest;
import com.etfc.device.Device;
import com.etfc.device.DeviceRepository;
import com.etfc.firmware.FirmwareBuild;
import com.etfc.firmware.FirmwareBuildRepository;
import com.etfc.testsuite.TestSuite;
import com.etfc.testsuite.TestSuiteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TestRunRepositoryTest extends AbstractRepositoryTest {

  @Autowired
  TestRunRepository testRunRepository;

  @Autowired
  DeviceRepository deviceRepository;

  @Autowired
  FirmwareBuildRepository firmwareBuildRepository;

  @Autowired
  TestSuiteRepository testSuiteRepository;

  @Autowired
  EntityManager em;

  private Device device;
  private FirmwareBuild firmware;
  private TestSuite suite;

  @BeforeEach
  void setUp() {
    device = deviceRepository.save(Device.create("DEV-RUN-001", "rev-B", "STM32H7", "lab"));
    firmware = firmwareBuildRepository.save(FirmwareBuild.create("v1.0.0", "main", "abc", false));
    suite = testSuiteRepository.save(
        TestSuite.create("PowerCycleValidation", "power", "team-hw", "HIGH", "Power cycle tests"));
    em.flush();
  }

  @Test
  void saves_and_finds_by_identity() {
    var run = TestRun.create("ext-run-1", device, firmware, suite, "lab", "corr-001");
    testRunRepository.save(run);
    em.flush();
    em.clear();

    var found = testRunRepository.findByDeviceIdAndFirmwareBuildIdAndSuiteIdAndExternalRunId(
        device.getId(), firmware.getId(), suite.getId(), "ext-run-1");

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(TestRunStatus.QUEUED);
    assertThat(found.get().getCorrelationId()).isEqualTo("corr-001");
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void rejects_duplicate_identity_tuple() {
    testRunRepository.save(TestRun.create("ext-dup", device, firmware, suite, "lab", "c1"));
    em.flush();

    assertThatThrownBy(() -> {
      testRunRepository.save(TestRun.create("ext-dup", device, firmware, suite, "lab", "c2"));
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void allows_same_external_run_id_on_different_device() {
    var device2 = deviceRepository.save(Device.create("DEV-RUN-002", "rev-A", "NRF52", "lab"));
    em.flush();

    testRunRepository.save(TestRun.create("ext-shared", device, firmware, suite, "lab", "c1"));
    testRunRepository.save(TestRun.create("ext-shared", device2, firmware, suite, "lab", "c2"));
    em.flush();

    assertThat(testRunRepository.findByDeviceIdOrderByStartedAtDesc(device.getId())).hasSize(1);
    assertThat(testRunRepository.findByDeviceIdOrderByStartedAtDesc(device2.getId())).hasSize(1);
  }

  @Test
  void status_defaults_to_queued() {
    var run = testRunRepository.save(
        TestRun.create("ext-q1", device, firmware, suite, "ci", null));
    em.flush();
    em.clear();

    assertThat(testRunRepository.findById(run.getId()).orElseThrow().getStatus())
        .isEqualTo(TestRunStatus.QUEUED);
  }
}
