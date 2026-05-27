package com.etfc.platform;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    return ResponseEntity.badRequest()
        .body(
            ErrorEnvelope.of(
                "VALIDATION_FAILED", "Request validation failed", correlationId(), details));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorEnvelope> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorEnvelope.of("NOT_FOUND", ex.getMessage(), correlationId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorEnvelope> handleInvalidTransition(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ErrorEnvelope.of("INVALID_TRANSITION", ex.getMessage(), correlationId()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorEnvelope> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
        .body(ErrorEnvelope.of("INVALID_REQUEST", ex.getMessage(), correlationId()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> handleGeneric(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ErrorEnvelope.of(
                "INTERNAL_ERROR", "An unexpected error occurred", correlationId()));
  }

  private String correlationId() {
    return MDC.get(CorrelationIdFilter.MDC_KEY);
  }
}
