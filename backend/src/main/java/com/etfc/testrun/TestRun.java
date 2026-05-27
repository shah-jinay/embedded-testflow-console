package com.etfc.testrun;

import com.etfc.device.Device;
import com.etfc.firmware.FirmwareBuild;
import com.etfc.testsuite.TestSuite;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "test_run")
@Getter
@NoArgsConstructor
public class TestRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Setter
  @Column(name = "external_run_id", nullable = false, length = 200)
  private String externalRunId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private Device device;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "firmware_build_id", nullable = false)
  private FirmwareBuild firmwareBuild;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "suite_id", nullable = false)
  private TestSuite suite;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private TestRunStatus status = TestRunStatus.QUEUED;

  @Setter
  @Column(name = "environment", length = 100)
  private String environment;

  @Setter
  @Column(name = "correlation_id", length = 100)
  private String correlationId;

  @Setter
  @Column(name = "started_at")
  private Instant startedAt;

  @Setter
  @Column(name = "completed_at")
  private Instant completedAt;

  @OneToMany(mappedBy = "run", fetch = FetchType.LAZY, cascade = CascadeType.ALL,
      orphanRemoval = true)
  private List<TestResult> results = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static TestRun create(String externalRunId, Device device, FirmwareBuild firmwareBuild,
      TestSuite suite, String environment, String correlationId) {
    var r = new TestRun();
    r.externalRunId = externalRunId;
    r.device = device;
    r.firmwareBuild = firmwareBuild;
    r.suite = suite;
    r.environment = environment;
    r.correlationId = correlationId;
    return r;
  }
}
