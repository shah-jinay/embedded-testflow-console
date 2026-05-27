package com.etfc.testrun;

import com.etfc.ingestion.dto.TestResultResponse;
import com.etfc.ingestion.dto.TestRunResponse;
import com.etfc.platform.EntityNotFoundException;
import com.etfc.platform.PagedResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/test-runs")
@RequiredArgsConstructor
public class TestRunQueryController {

  private static final int MAX_PAGE_SIZE = 200;
  private static final int EXPORT_CHUNK = 500;

  private final TestRunRepository testRunRepository;
  private final TestResultRepository testResultRepository;

  @GetMapping
  public ResponseEntity<PagedResponse<TestRunResponse>> list(
      @RequestParam(required = false) UUID deviceId,
      @RequestParam(required = false) String firmwareVersion,
      @RequestParam(required = false) String boardRevision,
      @RequestParam(required = false) String suite,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String failureCategory,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String environment,
      @PageableDefault(size = 50, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {

    if (pageable.getPageSize() > MAX_PAGE_SIZE) {
      pageable = PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    var filter = new TestRunFilter(deviceId, firmwareVersion, boardRevision, suite, status,
        failureCategory, from, to, environment);
    var page = testRunRepository
        .findAll(TestRunSpecification.withFilter(filter), pageable)
        .map(TestRunResponse::from);

    return ResponseEntity.ok(PagedResponse.from(page));
  }

  @GetMapping("/{runId}")
  public ResponseEntity<TestRunDetailResponse> detail(@PathVariable UUID runId) {
    TestRun run = testRunRepository.findById(runId)
        .orElseThrow(() -> new EntityNotFoundException("TestRun", runId));
    List<TestResult> results = testResultRepository.findByRunId(runId);
    return ResponseEntity.ok(TestRunDetailResponse.from(run, results));
  }

  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> export(
      @RequestParam(required = false) UUID deviceId,
      @RequestParam(required = false) String firmwareVersion,
      @RequestParam(required = false) String boardRevision,
      @RequestParam(required = false) String suite,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String failureCategory,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String environment,
      @RequestParam(defaultValue = "csv") String format) {

    var filter = new TestRunFilter(deviceId, firmwareVersion, boardRevision, suite, status,
        failureCategory, from, to, environment);
    Sort sort = Sort.by(Sort.Direction.DESC, "startedAt");

    StreamingResponseBody body = out -> {
      try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
        if ("json".equalsIgnoreCase(format)) {
          exportJson(writer, filter, sort);
        } else {
          exportCsv(writer, filter, sort);
        }
      }
    };

    boolean isJson = "json".equalsIgnoreCase(format);
    String filename = "test-runs." + (isJson ? "json" : "csv");
    MediaType contentType = isJson ? MediaType.APPLICATION_JSON : new MediaType("text", "csv");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    headers.setContentType(contentType);

    return ResponseEntity.ok().headers(headers).body(body);
  }

  private void exportCsv(OutputStreamWriter writer, TestRunFilter filter, Sort sort)
      throws IOException {
    writer.write("\"Run ID\",\"External Run ID\",\"Device\",\"Firmware\",\"Suite\","
        + "\"Status\",\"Environment\",\"Started At\",\"Completed At\"\n");
    int page = 0;
    Page<TestRun> chunk;
    do {
      chunk = testRunRepository.findAll(
          TestRunSpecification.withFilter(filter), PageRequest.of(page++, EXPORT_CHUNK, sort));
      for (TestRun run : chunk.getContent()) {
        writer.write(csvRow(run));
      }
      writer.flush();
    } while (chunk.hasNext());
  }

  private void exportJson(OutputStreamWriter writer, TestRunFilter filter, Sort sort)
      throws IOException {
    writer.write("[\n");
    int page = 0;
    boolean first = true;
    Page<TestRun> chunk;
    do {
      chunk = testRunRepository.findAll(
          TestRunSpecification.withFilter(filter), PageRequest.of(page++, EXPORT_CHUNK, sort));
      for (TestRun run : chunk.getContent()) {
        if (!first) writer.write(",\n");
        first = false;
        TestRunResponse r = TestRunResponse.from(run);
        writer.write(String.format(
            "  {\"id\":\"%s\",\"externalRunId\":%s,\"deviceExternalId\":%s,"
                + "\"firmwareVersion\":%s,\"suiteName\":%s,\"status\":%s,"
                + "\"environment\":%s,\"startedAt\":%s,\"completedAt\":%s}",
            r.id(),
            jsonStr(r.externalRunId()), jsonStr(r.deviceExternalId()),
            jsonStr(r.firmwareVersion()), jsonStr(r.suiteName()), jsonStr(r.status()),
            jsonStr(r.environment()),
            r.startedAt() != null ? "\"" + r.startedAt() + "\"" : "null",
            r.completedAt() != null ? "\"" + r.completedAt() + "\"" : "null"));
      }
      writer.flush();
    } while (chunk.hasNext());
    writer.write("\n]");
  }

  private static String csvRow(TestRun run) {
    return String.join(",",
        csvEsc(run.getId().toString()),
        csvEsc(run.getExternalRunId()),
        csvEsc(run.getDevice().getExternalDeviceId()),
        csvEsc(run.getFirmwareBuild().getVersion()),
        csvEsc(run.getSuite().getName()),
        csvEsc(run.getStatus().name()),
        csvEsc(run.getEnvironment()),
        csvEsc(run.getStartedAt() != null ? run.getStartedAt().toString() : ""),
        csvEsc(run.getCompletedAt() != null ? run.getCompletedAt().toString() : "")
    ) + "\n";
  }

  private static String csvEsc(String value) {
    if (value == null) return "\"\"";
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private static String jsonStr(String value) {
    if (value == null) return "null";
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
