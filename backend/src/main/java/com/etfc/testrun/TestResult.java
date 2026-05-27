package com.etfc.testrun;

import com.etfc.testsuite.TestCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "test_result")
@Getter
@NoArgsConstructor
public class TestResult {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id", nullable = false)
  private TestRun run;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "case_id", nullable = false)
  private TestCase testCase;

  @Setter
  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber = 1;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private TestRunStatus status;

  @Setter
  @Column(name = "duration_ms")
  private Long durationMs;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "failure_category", length = 100)
  private FailureCategory failureCategory;

  @Setter
  @Column(name = "failure_message", columnDefinition = "TEXT")
  private String failureMessage;

  @Setter
  @Column(name = "log_reference", length = 500)
  private String logReference;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static TestResult create(TestRun run, TestCase testCase, int attemptNumber,
      TestRunStatus status, Long durationMs, FailureCategory failureCategory,
      String failureMessage, String logReference) {
    var r = new TestResult();
    r.run = run;
    r.testCase = testCase;
    r.attemptNumber = attemptNumber;
    r.status = status;
    r.durationMs = durationMs;
    r.failureCategory = failureCategory;
    r.failureMessage = failureMessage;
    r.logReference = logReference;
    return r;
  }
}
