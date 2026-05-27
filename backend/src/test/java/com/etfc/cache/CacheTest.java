package com.etfc.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.etfc.dashboard.DashboardCacheService;
import com.etfc.dashboard.DashboardFilter;
import com.etfc.dashboard.DashboardQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"etfc.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class CacheTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  // Started in static initializer so host/port are available for @DynamicPropertySource
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
  @Autowired CacheManager cacheManager;
  @SpyBean DashboardQueryService dashboardQueryService;

  @BeforeEach
  void clearCache() {
    var cache = cacheManager.getCache(DashboardCacheService.CACHE_NAME);
    if (cache != null) cache.clear();
  }

  @Test
  void second_request_with_same_params_hits_cache() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk());
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk());

    // Both MockMvc calls go through the controller → DashboardCacheService.
    // The first misses the cache and calls the query service; the second hits the cache.
    verify(dashboardQueryService, times(1)).getSummary(any(DashboardFilter.class));
  }

  @Test
  void different_filter_params_produce_separate_cache_entries() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk());
    mockMvc.perform(get("/api/dashboard/summary").param("environment", "lab"))
        .andExpect(status().isOk());

    // Two distinct filter combos → two cache misses → two service calls
    verify(dashboardQueryService, times(2)).getSummary(any(DashboardFilter.class));
  }
}
