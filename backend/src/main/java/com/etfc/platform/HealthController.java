package com.etfc.platform;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

  private static final String BUILD_SHA =
      System.getenv().getOrDefault("GIT_SHA", "dev");

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of(
        "status", "UP",
        "build", BUILD_SHA,
        "time", Instant.now().toString());
  }
}
