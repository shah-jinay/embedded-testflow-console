package com.etfc.ingestion;

import com.etfc.device.Device;
import com.etfc.device.DeviceRepository;
import com.etfc.firmware.FirmwareBuild;
import com.etfc.firmware.FirmwareBuildRepository;
import com.etfc.ingestion.dto.CreateDeviceRequest;
import com.etfc.ingestion.dto.CreateFirmwareBuildRequest;
import com.etfc.ingestion.dto.CreateTestCaseRequest;
import com.etfc.ingestion.dto.CreateTestRunRequest;
import com.etfc.ingestion.dto.CreateTestSuiteRequest;
import com.etfc.ingestion.dto.DeviceResponse;
import com.etfc.ingestion.dto.FirmwareBuildResponse;
import com.etfc.ingestion.dto.IngestResultRequest;
import com.etfc.ingestion.dto.IngestResultsRequest;
import com.etfc.ingestion.dto.TestCaseResponse;
import com.etfc.ingestion.dto.TestResultResponse;
import com.etfc.ingestion.dto.TestRunResponse;
import com.etfc.ingestion.dto.TestSuiteResponse;
import com.etfc.ingestion.dto.UpdateRunStatusRequest;
import com.etfc.platform.CorrelationIdFilter;
import com.etfc.platform.EntityNotFoundException;
import com.etfc.platform.RunEvent;
import com.etfc.platform.SseService;
import com.etfc.testrun.FailureCategory;
import com.etfc.testrun.TestResult;
import com.etfc.testrun.TestResultRepository;
import com.etfc.testrun.TestRun;
import com.etfc.testrun.TestRunRepository;
import com.etfc.testrun.TestRunStatus;
import com.etfc.testsuite.TestCase;
import com.etfc.testsuite.TestCaseRepository;
import com.etfc.testsuite.TestSuiteRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

  private final DeviceRepository deviceRepository;
  private final FirmwareBuildRepository firmwareBuildRepository;
  private final TestSuiteRepository testSuiteRepository;
  private final TestCaseRepository testCaseRepository;
  private final TestRunRepository testRunRepository;
  private final TestResultRepository testResultRepository;
  private final FailureCategoryTranslator failureCategoryTranslator;
  private final StatusTranslator statusTranslator;
  private final StatusTransitionValidator transitionValidator;
  private final MeterRegistry meterRegistry;
  private final SseService sseService;

  private Counter deviceCreatedCounter;
  private Counter deviceExistingCounter;
  private Counter firmwareCreatedCounter;
  private Counter firmwareExistingCounter;
  private Counter runCreatedCounter;
  private Counter runExistingCounter;
  private Counter resultCreatedCounter;
  private Counter resultUpdatedCounter;
  private Counter transitionErrorsCounter;

  @PostConstruct
  void initMetrics() {
    deviceCreatedCounter    = ingestionCounter("device",   "created");
    deviceExistingCounter   = ingestionCounter("device",   "existing");
    firmwareCreatedCounter  = ingestionCounter("firmware", "created");
    firmwareExistingCounter = ingestionCounter("firmware", "existing");
    runCreatedCounter       = ingestionCounter("run",      "created");
    runExistingCounter      = ingestionCounter("run",      "existing");
    resultCreatedCounter    = ingestionCounter("result",   "created");
    resultUpdatedCounter    = ingestionCounter("result",   "updated");
    transitionErrorsCounter = Counter.builder("etfc_transition_errors_total")
        .description("Invalid state transition attempts")
        .register(meterRegistry);
  }

  private Counter ingestionCounter(String entity, String outcome) {
    return Counter.builder("etfc_ingestion_total")
        .description("Ingestion operations by entity and outcome")
        .tag("entity", entity)
        .tag("outcome", outcome)
        .register(meterRegistry);
  }

  @Transactional
  public RegisterResult<TestSuiteResponse> registerSuite(CreateTestSuiteRequest req) {
    return testSuiteRepository
        .findByName(req.name())
        .map(existing -> {
          log.debug("Test suite already registered: {}", req.name());
          return RegisterResult.existing(TestSuiteResponse.from(existing));
        })
        .orElseGet(() -> {
          var suite = com.etfc.testsuite.TestSuite.create(
              req.name(), req.targetComponent(), req.owner(), req.severity(), req.description());
          log.info("Registered new test suite: {}", req.name());
          return RegisterResult.created(TestSuiteResponse.from(testSuiteRepository.save(suite)));
        });
  }

  @Transactional
  public RegisterResult<TestCaseResponse> registerCase(java.util.UUID suiteId, CreateTestCaseRequest req) {
    var suite = testSuiteRepository
        .findById(suiteId)
        .orElseThrow(() -> new EntityNotFoundException("TestSuite", suiteId));
    return testCaseRepository
        .findBySuiteIdAndName(suiteId, req.name())
        .map(existing -> {
          log.debug("Test case already registered: {}/{}", suite.getName(), req.name());
          return RegisterResult.existing(TestCaseResponse.from(existing));
        })
        .orElseGet(() -> {
          var tc = com.etfc.testsuite.TestCase.create(
              suite, req.name(), req.expectedBehavior(), req.timeoutMs(), req.criticality());
          log.info("Registered new test case: {}/{}", suite.getName(), req.name());
          return RegisterResult.created(TestCaseResponse.from(testCaseRepository.save(tc)));
        });
  }

  @Transactional
  public RegisterResult<DeviceResponse> registerDevice(CreateDeviceRequest req) {
    return deviceRepository
        .findByExternalDeviceId(req.externalDeviceId())
        .map(existing -> {
          log.debug("Device already registered: {}", req.externalDeviceId());
          deviceExistingCounter.increment();
          return RegisterResult.existing(DeviceResponse.from(existing));
        })
        .orElseGet(() -> {
          var device =
              Device.create(
                  req.externalDeviceId(), req.boardRevision(), req.mcuFamily(), req.environment());
          if (req.metadata() != null) {
            device.setMetadata(req.metadata());
          }
          log.info("Registered new device: {}", req.externalDeviceId());
          deviceCreatedCounter.increment();
          return RegisterResult.created(DeviceResponse.from(deviceRepository.save(device)));
        });
  }

  @Transactional
  public RegisterResult<FirmwareBuildResponse> registerFirmwareBuild(
      CreateFirmwareBuildRequest req) {
    return firmwareBuildRepository
        .findByVersion(req.version())
        .map(existing -> {
          log.debug("Firmware build already registered: {}", req.version());
          firmwareExistingCounter.increment();
          return RegisterResult.existing(FirmwareBuildResponse.from(existing));
        })
        .orElseGet(() -> {
          var fb =
              FirmwareBuild.create(
                  req.version(), req.branch(), req.commitHash(), req.releaseCandidate());
          log.info("Registered new firmware build: {}", req.version());
          firmwareCreatedCounter.increment();
          return RegisterResult.created(FirmwareBuildResponse.from(firmwareBuildRepository.save(fb)));
        });
  }

  @Transactional
  public RegisterResult<TestRunResponse> createTestRun(CreateTestRunRequest req) {
    var device =
        deviceRepository
            .findByExternalDeviceId(req.deviceExternalId())
            .orElseThrow(() -> new EntityNotFoundException("Device", req.deviceExternalId()));
    var firmware =
        firmwareBuildRepository
            .findByVersion(req.firmwareVersion())
            .orElseThrow(
                () -> new EntityNotFoundException("FirmwareBuild", req.firmwareVersion()));
    var suite =
        testSuiteRepository
            .findByName(req.suiteName())
            .orElseThrow(() -> new EntityNotFoundException("TestSuite", req.suiteName()));

    return testRunRepository
        .findByDeviceIdAndFirmwareBuildIdAndSuiteIdAndExternalRunId(
            device.getId(), firmware.getId(), suite.getId(), req.externalRunId())
        .map(existing -> {
          log.debug("Test run already exists: {}", req.externalRunId());
          runExistingCounter.increment();
          return RegisterResult.existing(TestRunResponse.from(existing));
        })
        .orElseGet(() -> {
          String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
          var run =
              TestRun.create(
                  req.externalRunId(), device, firmware, suite, req.environment(), correlationId);
          if (req.startedAt() != null) {
            run.setStartedAt(req.startedAt());
          }
          log.info("Created test run: {} for device {}", req.externalRunId(), req.deviceExternalId());
          runCreatedCounter.increment();
          RegisterResult<TestRunResponse> created =
              RegisterResult.created(TestRunResponse.from(testRunRepository.save(run)));
          sseService.broadcast(RunEvent.of("RUN_CREATED", created.data()));
          return created;
        });
  }

  @Transactional
  public TestRunResponse updateRunStatus(UUID runId, UpdateRunStatusRequest req) {
    var run =
        testRunRepository
            .findById(runId)
            .orElseThrow(() -> new EntityNotFoundException("TestRun", runId));

    TestRunStatus newStatus = statusTranslator.translate(req.status());

    try {
      transitionValidator.validate(run.getStatus(), newStatus);
    } catch (IllegalStateException e) {
      transitionErrorsCounter.increment();
      throw e;
    }

    run.setStatus(newStatus);

    if (req.completedAt() != null) {
      run.setCompletedAt(req.completedAt());
    } else if (transitionValidator.isTerminal(newStatus)) {
      run.setCompletedAt(Instant.now());
    }
    if (newStatus == TestRunStatus.RUNNING && run.getStartedAt() == null) {
      run.setStartedAt(Instant.now());
    }

    log.info("Test run {} transitioned to {}", runId, newStatus);
    TestRunResponse updated = TestRunResponse.from(testRunRepository.save(run));
    sseService.broadcast(RunEvent.of("RUN_STATUS_CHANGED", updated));
    return updated;
  }

  @Transactional
  public List<TestResultResponse> ingestResults(UUID runId, IngestResultsRequest req) {
    var run =
        testRunRepository
            .findById(runId)
            .orElseThrow(() -> new EntityNotFoundException("TestRun", runId));

    return req.results().stream().map(r -> upsertResult(run, r)).toList();
  }

  private TestResultResponse upsertResult(TestRun run, IngestResultRequest req) {
    UUID suiteId = run.getSuite().getId();
    TestCase testCase =
        testCaseRepository
            .findBySuiteIdAndName(suiteId, req.caseName())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Test case '"
                            + req.caseName()
                            + "' not found in suite "
                            + run.getSuite().getName()));

    TestRunStatus status = statusTranslator.translate(req.status());
    FailureCategory category = failureCategoryTranslator.translate(req.failureCategory());

    return testResultRepository
        .findByRunIdAndTestCaseIdAndAttemptNumber(run.getId(), testCase.getId(), req.attemptNumber())
        .map(existing -> {
          existing.setStatus(status);
          existing.setDurationMs(req.durationMs());
          existing.setFailureCategory(category);
          existing.setFailureMessage(req.failureMessage());
          existing.setLogReference(req.logReference());
          log.debug(
              "Updated existing result for case {} attempt {}", req.caseName(), req.attemptNumber());
          resultUpdatedCounter.increment();
          return TestResultResponse.from(testResultRepository.save(existing));
        })
        .orElseGet(() -> {
          var result =
              TestResult.create(
                  run,
                  testCase,
                  req.attemptNumber(),
                  status,
                  req.durationMs(),
                  category,
                  req.failureMessage(),
                  req.logReference());
          log.debug(
              "Ingested new result for case {} attempt {}", req.caseName(), req.attemptNumber());
          resultCreatedCounter.increment();
          return TestResultResponse.from(testResultRepository.save(result));
        });
  }
}
