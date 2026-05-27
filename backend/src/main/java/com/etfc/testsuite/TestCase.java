package com.etfc.testsuite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_case")
@Getter
@NoArgsConstructor
public class TestCase {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "suite_id", nullable = false)
  private TestSuite suite;

  @Setter
  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Setter
  @Column(name = "expected_behavior", columnDefinition = "TEXT")
  private String expectedBehavior;

  @Setter
  @Column(name = "timeout_ms")
  private Long timeoutMs;

  @Setter
  @Column(name = "criticality", length = 50)
  private String criticality;

  public static TestCase create(TestSuite suite, String name, String expectedBehavior,
      Long timeoutMs, String criticality) {
    var tc = new TestCase();
    tc.suite = suite;
    tc.name = name;
    tc.expectedBehavior = expectedBehavior;
    tc.timeoutMs = timeoutMs;
    tc.criticality = criticality;
    return tc;
  }
}
