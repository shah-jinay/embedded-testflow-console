package com.etfc.ingestion;

import com.etfc.ingestion.dto.CreateTestRunRequest;
import com.etfc.ingestion.dto.IngestResultsRequest;
import com.etfc.ingestion.dto.TestResultResponse;
import com.etfc.ingestion.dto.TestRunResponse;
import com.etfc.ingestion.dto.UpdateRunStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-runs")
@RequiredArgsConstructor
public class TestRunIngestionController {

  private final IngestionService ingestionService;

  @PostMapping
  public ResponseEntity<TestRunResponse> create(@Valid @RequestBody CreateTestRunRequest request) {
    RegisterResult<TestRunResponse> result = ingestionService.createTestRun(request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.data());
  }

  @PatchMapping("/{runId}/status")
  public ResponseEntity<TestRunResponse> updateStatus(
      @PathVariable UUID runId, @Valid @RequestBody UpdateRunStatusRequest request) {
    return ResponseEntity.ok(ingestionService.updateRunStatus(runId, request));
  }

  @PostMapping("/{runId}/results")
  public ResponseEntity<List<TestResultResponse>> ingestResults(
      @PathVariable UUID runId, @Valid @RequestBody IngestResultsRequest request) {
    List<TestResultResponse> results = ingestionService.ingestResults(runId, request);
    return ResponseEntity.ok(results);
  }
}
