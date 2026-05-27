package com.etfc.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "etfc.rate-limit.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> WATCHED_PREFIXES =
      Set.of("/api/devices", "/api/firmware-builds", "/api/test-runs");

  private static final String LUA =
      """
      local current = redis.call('INCR', KEYS[1])
      if current == 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[2])
      end
      if current > tonumber(ARGV[1]) then
        return redis.call('TTL', KEYS[1])
      else
        return 0
      end
      """;

  @Value("${etfc.rate-limit.limit:100}")
  private long limit;

  @Value("${etfc.rate-limit.window-seconds:60}")
  private long windowSeconds;

  private final StringRedisTemplate redis;
  private final RedisScript<Long> script;
  private final Counter rateLimitedCounter;
  private final ObjectMapper objectMapper;

  public RateLimitFilter(
      StringRedisTemplate redis, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
    this.redis = redis;
    this.script = new DefaultRedisScript<>(LUA, Long.class);
    this.rateLimitedCounter =
        Counter.builder("etfc_rate_limited_total")
            .description("Requests rejected by the token-bucket rate limiter")
            .register(meterRegistry);
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    if (!isWatched(req)) {
      chain.doFilter(req, res);
      return;
    }

    String key = "rl:" + req.getRemoteAddr() + ":" + group(req);
    Long ttl;
    try {
      ttl =
          redis.execute(
              script,
              List.of(key),
              String.valueOf(limit),
              String.valueOf(windowSeconds));
    } catch (Exception ex) {
      log.warn("Rate-limiter Redis error, failing open: {}", ex.getMessage());
      chain.doFilter(req, res);
      return;
    }

    if (ttl != null && ttl > 0) {
      rateLimitedCounter.increment();
      String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
      res.setStatus(429);
      res.setHeader("Retry-After", String.valueOf(ttl));
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write(envelope("RATE_LIMITED", "Too many requests", correlationId));
      return;
    }

    chain.doFilter(req, res);
  }

  private boolean isWatched(HttpServletRequest req) {
    String method = req.getMethod();
    if (!"POST".equals(method) && !"PATCH".equals(method)) return false;
    String path = req.getRequestURI();
    return WATCHED_PREFIXES.stream().anyMatch(path::startsWith);
  }

  private String group(HttpServletRequest req) {
    String path = req.getRequestURI();
    if (path.startsWith("/api/devices")) return "devices";
    if (path.startsWith("/api/firmware-builds")) return "firmware";
    return "runs";
  }

  private String envelope(String code, String message, String correlationId) {
    try {
      return objectMapper.writeValueAsString(ErrorEnvelope.of(code, message, correlationId));
    } catch (JsonProcessingException ex) {
      return "{\"error\":{\"code\":\"" + code + "\"}}";
    }
  }
}
