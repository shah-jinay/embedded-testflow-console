package com.etfc.ingestion;

import com.etfc.ingestion.dto.CreateDeviceRequest;
import com.etfc.ingestion.dto.DeviceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceIngestionController {

  private final IngestionService ingestionService;

  @PostMapping
  public ResponseEntity<DeviceResponse> register(@Valid @RequestBody CreateDeviceRequest request) {
    RegisterResult<DeviceResponse> result = ingestionService.registerDevice(request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(result.data());
  }
}
