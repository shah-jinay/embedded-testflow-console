package com.etfc.ingestion;

/**
 * Wraps an ingestion outcome so controllers can distinguish 201 (created) from 200 (existing)
 * without leaking that decision into the service layer.
 */
public record RegisterResult<T>(T data, boolean created) {

  public static <T> RegisterResult<T> created(T data) {
    return new RegisterResult<>(data, true);
  }

  public static <T> RegisterResult<T> existing(T data) {
    return new RegisterResult<>(data, false);
  }
}
