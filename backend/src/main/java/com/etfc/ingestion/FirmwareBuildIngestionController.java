package com.etfc.ingestion;

import com.etfc.ingestion.dto.CreateFirmwareBuildRequest;
import com.etfc.ingestion.dto.FirmwareBuildResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/firmware-builds")
@RequiredArgsConstructor
public class FirmwareBuildIngestionController {

  private final IngestionService ingestionService;

  @PostMapping
  public ResponseEntity<FirmwareBuildResponse> register(
      @Valid @RequestBody CreateFirmwareBuildRequest request) {
    RegisterResult<FirmwareBuildResponse> result = ingestionService.registerFirmwareBuild(request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.data());
  }
}
