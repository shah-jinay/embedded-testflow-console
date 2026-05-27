package com.etfc.firmware;

import com.etfc.platform.EntityNotFoundException;
import com.etfc.testrun.TestRun;
import com.etfc.testrun.TestRunRepository;
import com.etfc.testrun.TestRunStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/firmware-builds")
@RequiredArgsConstructor
public class FirmwareQueryController {

  private final FirmwareBuildRepository firmwareBuildRepository;
  private final TestRunRepository testRunRepository;

  @GetMapping("/{version}/validation-summary")
  public ResponseEntity<FirmwareValidationSummaryResponse> validationSummary(
      @PathVariable String version) {

    FirmwareBuild fb =
        firmwareBuildRepository
            .findByVersion(version)
            .orElseThrow(() -> new EntityNotFoundException("FirmwareBuild", version));

    List<TestRun> runs = testRunRepository.findTop200ByFirmwareBuildVersionOrderByCreatedAtDesc(version);

    List<FirmwareValidationSummaryResponse.Breakdown> byDevice =
        buildBreakdown(runs, run -> run.getDevice().getExternalDeviceId());

    List<FirmwareValidationSummaryResponse.Breakdown> bySuite =
        buildBreakdown(runs, run -> run.getSuite().getName());

    return ResponseEntity.ok(FirmwareValidationSummaryResponse.from(fb, byDevice, bySuite));
  }

  private List<FirmwareValidationSummaryResponse.Breakdown> buildBreakdown(
      List<TestRun> runs, java.util.function.Function<TestRun, String> keyFn) {
    Map<String, List<TestRun>> grouped = runs.stream().collect(Collectors.groupingBy(keyFn));
    return grouped.entrySet().stream()
        .map(e -> {
          String name = e.getKey();
          List<TestRun> g = e.getValue();
          long total = g.size();
          long passed = g.stream().filter(r -> r.getStatus() == TestRunStatus.PASSED).count();
          long failed =
              g.stream()
                  .filter(
                      r ->
                          r.getStatus() == TestRunStatus.FAILED
                              || r.getStatus() == TestRunStatus.TIMED_OUT)
                  .count();
          return new FirmwareValidationSummaryResponse.Breakdown(name, total, passed, failed);
        })
        .sorted((a, b) -> Long.compare(b.total(), a.total()))
        .toList();
  }
}
