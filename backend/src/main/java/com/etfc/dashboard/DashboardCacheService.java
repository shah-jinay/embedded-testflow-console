package com.etfc.dashboard;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardCacheService {

  public static final String CACHE_NAME = "dashboard-summary";

  private final DashboardQueryService queryService;
  private final CacheManager cacheManager;
  private final MeterRegistry meterRegistry;

  private Counter cacheHitsCounter;

  @PostConstruct
  void init() {
    cacheHitsCounter =
        Counter.builder("etfc_cache_hits_total")
            .description("Dashboard summary cache hits")
            .register(meterRegistry);
  }

  public DashboardSummaryResponse getSummary(DashboardFilter filter) {
    String key = buildKey(filter);
    Cache cache = safeGetCache();

    if (cache != null) {
      try {
        DashboardSummaryResponse cached = cache.get(key, DashboardSummaryResponse.class);
        if (cached != null) {
          cacheHitsCounter.increment();
          return cached;
        }
      } catch (Exception ex) {
        log.warn("Cache read failed, proceeding without cache: {}", ex.getMessage());
      }
    }

    DashboardSummaryResponse result = queryService.getSummary(filter);

    if (cache != null) {
      try {
        cache.put(key, result);
      } catch (Exception ex) {
        log.warn("Cache write failed: {}", ex.getMessage());
      }
    }

    return result;
  }

  private Cache safeGetCache() {
    try {
      return cacheManager.getCache(CACHE_NAME);
    } catch (Exception ex) {
      log.warn("Could not access cache '{}': {}", CACHE_NAME, ex.getMessage());
      return null;
    }
  }

  private String buildKey(DashboardFilter f) {
    return "fw="
        + orEmpty(f.firmwareVersion())
        + ",env="
        + orEmpty(f.environment())
        + ",from="
        + orEmpty(f.from())
        + ",to="
        + orEmpty(f.to());
  }

  private static String orEmpty(Object v) {
    return v == null ? "" : v.toString();
  }
}
