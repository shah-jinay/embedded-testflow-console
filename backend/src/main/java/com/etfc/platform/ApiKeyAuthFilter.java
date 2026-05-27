package com.etfc.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Component
@ConditionalOnProperty(name = "etfc.security.api-key")
@Order(HIGHEST_PRECEDENCE + 2)
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  private static final List<String> PERMIT = List.of(
      "/api/health",
      "/api/events/**",
      "/v3/api-docs",
      "/v3/api-docs/**",
      "/swagger-ui.html",
      "/swagger-ui/**",
      "/actuator/**"
  );

  @Value("${etfc.security.api-key}")
  private String configuredKey;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String path = request.getRequestURI();

    for (String pattern : PERMIT) {
      if (MATCHER.match(pattern, path)) {
        chain.doFilter(request, response);
        return;
      }
    }

    String header = request.getHeader("Authorization");
    String key = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;

    if (!configuredKey.equals(key)) {
      log.warn("Unauthorized request: {} {} from {}", request.getMethod(), path, request.getRemoteAddr());
      String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
      if (correlationId == null) correlationId = "";
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write(
          "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid API key\","
              + "\"correlationId\":\"" + correlationId + "\",\"details\":[]}}");
      return;
    }

    chain.doFilter(request, response);
  }
}
