package com.etfc.device;

import com.etfc.ingestion.dto.DeviceResponse;
import com.etfc.platform.EntityNotFoundException;
import com.etfc.platform.PagedResponse;
import com.etfc.testrun.TestRun;
import com.etfc.testrun.TestRunRepository;
import com.etfc.testrun.TestRunStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceQueryController {

  private static final List<TestRunStatus> FAILED_STATUSES =
      List.of(TestRunStatus.FAILED, TestRunStatus.TIMED_OUT);

  private final DeviceRepository deviceRepository;
  private final TestRunRepository testRunRepository;

  @GetMapping
  public ResponseEntity<PagedResponse<DeviceResponse>> list(
      @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    var page = deviceRepository.findAll(pageable).map(DeviceResponse::from);
    return ResponseEntity.ok(PagedResponse.from(page));
  }

  @GetMapping("/{deviceId}")
  public ResponseEntity<DeviceDetailResponse> detail(@PathVariable UUID deviceId) {
    Device device =
        deviceRepository
            .findById(deviceId)
            .orElseThrow(() -> new EntityNotFoundException("Device", deviceId));

    long totalRuns = deviceRepository.countRunsByDeviceId(deviceId);
    long failedRuns = deviceRepository.countFailedRunsByDeviceId(deviceId, FAILED_STATUSES);
    List<TestRun> recentRuns =
        testRunRepository.findByDeviceIdOrderByStartedAtDesc(deviceId).stream()
            .limit(10)
            .toList();

    return ResponseEntity.ok(DeviceDetailResponse.from(device, totalRuns, failedRuns, recentRuns));
  }
}
