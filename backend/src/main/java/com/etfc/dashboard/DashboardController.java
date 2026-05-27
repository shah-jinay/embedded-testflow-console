package com.etfc.dashboard;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardCacheService dashboardCacheService;

  @GetMapping("/summary")
  public ResponseEntity<DashboardSummaryResponse> summary(
      @RequestParam(required = false) String firmwareVersion,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String environment) {

    var filter = new DashboardFilter(firmwareVersion, from, to, environment);
    return ResponseEntity.ok(dashboardCacheService.getSummary(filter));
  }
}
