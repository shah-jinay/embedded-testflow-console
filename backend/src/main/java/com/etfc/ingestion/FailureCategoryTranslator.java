package com.etfc.ingestion;

import com.etfc.testrun.FailureCategory;
import org.springframework.stereotype.Component;

/**
 * Single authoritative point where raw failure strings become FailureCategory enum values.
 * Unknown strings map to UNCLASSIFIED rather than propagating errors to callers.
 */
@Component
public class FailureCategoryTranslator {

  public FailureCategory translate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return FailureCategory.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return FailureCategory.UNCLASSIFIED;
    }
  }
}
