package com.etfc.platform;

public class EntityNotFoundException extends RuntimeException {

  public EntityNotFoundException(String type, Object id) {
    super(type + " not found: " + id);
  }
}
