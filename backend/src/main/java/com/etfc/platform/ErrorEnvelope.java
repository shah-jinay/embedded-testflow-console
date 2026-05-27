package com.etfc.platform;

import java.util.List;

public record ErrorEnvelope(ErrorDetail error) {

  public record ErrorDetail(
      String code, String message, String correlationId, List<String> details) {}

  public static ErrorEnvelope of(String code, String message, String correlationId) {
    return new ErrorEnvelope(new ErrorDetail(code, message, correlationId, List.of()));
  }

  public static ErrorEnvelope of(
      String code, String message, String correlationId, List<String> details) {
    return new ErrorEnvelope(new ErrorDetail(code, message, correlationId, details));
  }
}
