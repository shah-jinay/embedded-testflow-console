package com.etfc.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "etfc.rate-limit.enabled=true",
      "etfc.rate-limit.limit=3",
      "etfc.rate-limit.window-seconds=60"
    })
@AutoConfigureMockMvc
@Testcontainers
class RateLimitTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static final GenericContainer<?> REDIS;

  static {
    REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    REDIS.start();
  }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired MockMvc mockMvc;
  @Autowired StringRedisTemplate redisTemplate;

  @BeforeEach
  void flushRedis() {
    redisTemplate.execute((RedisCallback<Void>) conn -> {
      conn.serverCommands().flushDb();
      return null;
    });
  }

  @Test
  void fourth_request_returns_429_with_retry_after() throws Exception {
    // Requests 1–3 pass through (201 on first create, 200 on duplicate)
    for (int i = 1; i <= 3; i++) {
      mockMvc
          .perform(
              post("/api/devices")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"externalDeviceId":"RL-DEV-%d","boardRevision":"rv1",\
                      "mcuFamily":"STM32","environment":"lab"}
                      """
                          .formatted(i)))
          .andExpect(status().is2xxSuccessful());
    }

    // Request 4 is rate-limited
    mockMvc
        .perform(
            post("/api/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"externalDeviceId":"RL-DEV-4","boardRevision":"rv1",\
                    "mcuFamily":"STM32","environment":"lab"}
                    """))
        .andExpect(status().is(429))
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
  }

  @Test
  void rate_limit_counter_resets_for_different_endpoint_group() throws Exception {
    // Fill up the 'devices' bucket
    for (int i = 1; i <= 3; i++) {
      mockMvc
          .perform(
              post("/api/devices")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"externalDeviceId":"RL-FW-DEV-%d","boardRevision":"rv1",\
                      "mcuFamily":"STM32","environment":"lab"}
                      """
                          .formatted(i)))
          .andExpect(status().is2xxSuccessful());
    }

    // firmware-builds uses a separate rate-limit bucket — should still be allowed
    mockMvc
        .perform(
            post("/api/firmware-builds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":"v99.0.0","branch":"main",\
                    "commitHash":"aabbcc","releaseCandidate":false}
                    """))
        .andExpect(status().is2xxSuccessful());
  }
}
