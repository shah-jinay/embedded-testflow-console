package com.etfc.ingestion;

import com.etfc.ingestion.dto.CreateTestCaseRequest;
import com.etfc.ingestion.dto.CreateTestSuiteRequest;
import com.etfc.ingestion.dto.TestCaseResponse;
import com.etfc.ingestion.dto.TestSuiteResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-suites")
@RequiredArgsConstructor
public class TestSuiteIngestionController {

  private final IngestionService ingestionService;

  @PostMapping
  public ResponseEntity<TestSuiteResponse> registerSuite(
      @Valid @RequestBody CreateTestSuiteRequest request) {
    RegisterResult<TestSuiteResponse> result = ingestionService.registerSuite(request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.data());
  }

  @PostMapping("/{suiteId}/cases")
  public ResponseEntity<TestCaseResponse> registerCase(
      @PathVariable UUID suiteId, @Valid @RequestBody CreateTestCaseRequest request) {
    RegisterResult<TestCaseResponse> result = ingestionService.registerCase(suiteId, request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.data());
  }
}
