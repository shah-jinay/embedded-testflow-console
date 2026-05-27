package com.etfc.dashboard;

import com.etfc.testrun.TestRunStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardQueryService {

  private static final int TOP_N = 10;
  private static final int RECENT_N = 5;
  private static final int ACTIVITY_N = 10;

  private final MeterRegistry meterRegistry;

  @PersistenceContext
  private EntityManager em;

  private Timer queryTimer;

  @PostConstruct
  void initMetrics() {
    queryTimer = Timer.builder("etfc_dashboard_query_duration_seconds")
        .description("Time taken to compute dashboard summary from database")
        .register(meterRegistry);
  }

  public DashboardSummaryResponse getSummary(DashboardFilter filter) {
    return queryTimer.record(() -> doGetSummary(filter));
  }

  private DashboardSummaryResponse doGetSummary(DashboardFilter filter) {
    Map<TestRunStatus, Long> counts = statusCounts(filter);

    long passed     = counts.getOrDefault(TestRunStatus.PASSED,      0L);
    long failed     = counts.getOrDefault(TestRunStatus.FAILED,      0L);
    long timedOut   = counts.getOrDefault(TestRunStatus.TIMED_OUT,   0L);
    long blocked    = counts.getOrDefault(TestRunStatus.BLOCKED,     0L);
    long needsReview= counts.getOrDefault(TestRunStatus.NEEDS_REVIEW,0L);
    long queued     = counts.getOrDefault(TestRunStatus.QUEUED,      0L);
    long running    = counts.getOrDefault(TestRunStatus.RUNNING,     0L);
    long total      = passed + failed + timedOut + blocked + needsReview + queued + running;

    double passRate = total == 0 ? 0.0 : (double) passed / total;

    return new DashboardSummaryResponse(
        total, passed, failed, timedOut, blocked, needsReview, passRate,
        failureRateBySuite(filter),
        failureCountByFirmware(filter),
        recentFailingDevices(filter),
        slowestSuites(filter),
        latestActivity(filter));
  }

  @SuppressWarnings("unchecked")
  private Map<TestRunStatus, Long> statusCounts(DashboardFilter f) {
    String sql = """
        SELECT tr.status, COUNT(tr.id)
        FROM test_run tr
        JOIN firmware_build fb ON fb.id = tr.firmware_build_id
        WHERE (CAST(:fw  AS text) IS NULL OR fb.version     = CAST(:fw  AS text))
          AND (CAST(:env AS text) IS NULL OR tr.environment = CAST(:env AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        GROUP BY tr.status
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("fw",   f.firmwareVersion());
    q.setParameter("env",  f.environment());
    q.setParameter("from", toTs(f.from()));
    q.setParameter("to",   toTs(f.to()));

    Map<TestRunStatus, Long> result = new EnumMap<>(TestRunStatus.class);
    for (Object[] row : (List<Object[]>) q.getResultList()) {
      result.put(TestRunStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private List<DashboardSummaryResponse.SuiteFailureRate> failureRateBySuite(DashboardFilter f) {
    String sql = """
        SELECT ts.name, COUNT(tr.id)
        FROM test_run tr
        JOIN test_suite ts ON ts.id = tr.suite_id
        JOIN firmware_build fb ON fb.id = tr.firmware_build_id
        WHERE tr.status IN ('FAILED', 'TIMED_OUT')
          AND (CAST(:fw  AS text) IS NULL OR fb.version     = CAST(:fw  AS text))
          AND (CAST(:env AS text) IS NULL OR tr.environment = CAST(:env AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        GROUP BY ts.name
        ORDER BY COUNT(tr.id) DESC
        LIMIT :limit
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("fw",    f.firmwareVersion());
    q.setParameter("env",   f.environment());
    q.setParameter("from",  toTs(f.from()));
    q.setParameter("to",    toTs(f.to()));
    q.setParameter("limit", TOP_N);

    return ((List<Object[]>) q.getResultList()).stream()
        .map(row -> {
          String name      = (String) row[0];
          long   failCount = ((Number) row[1]).longValue();
          long   total     = totalRunsForSuite(name, f);
          double rate      = total == 0 ? 0.0 : (double) failCount / total;
          return new DashboardSummaryResponse.SuiteFailureRate(name, total, failCount, rate);
        })
        .toList();
  }

  @SuppressWarnings("unchecked")
  private long totalRunsForSuite(String suiteName, DashboardFilter f) {
    String sql = """
        SELECT COUNT(tr.id)
        FROM test_run tr
        JOIN test_suite ts ON ts.id = tr.suite_id
        JOIN firmware_build fb ON fb.id = tr.firmware_build_id
        WHERE ts.name = :name
          AND (CAST(:fw  AS text) IS NULL OR fb.version     = CAST(:fw  AS text))
          AND (CAST(:env AS text) IS NULL OR tr.environment = CAST(:env AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("name", suiteName);
    q.setParameter("fw",   f.firmwareVersion());
    q.setParameter("env",  f.environment());
    q.setParameter("from", toTs(f.from()));
    q.setParameter("to",   toTs(f.to()));
    return ((Number) q.getSingleResult()).longValue();
  }

  @SuppressWarnings("unchecked")
  private List<DashboardSummaryResponse.FirmwareFailureCount> failureCountByFirmware(
      DashboardFilter f) {
    String sql = """
        SELECT fb.version, COUNT(tr.id)
        FROM test_run tr
        JOIN firmware_build fb ON fb.id = tr.firmware_build_id
        WHERE tr.status IN ('FAILED', 'TIMED_OUT')
          AND (CAST(:env AS text) IS NULL OR tr.environment = CAST(:env AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        GROUP BY fb.version
        ORDER BY COUNT(tr.id) DESC
        LIMIT :limit
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("env",   f.environment());
    q.setParameter("from",  toTs(f.from()));
    q.setParameter("to",    toTs(f.to()));
    q.setParameter("limit", TOP_N);

    return ((List<Object[]>) q.getResultList()).stream()
        .map(row -> new DashboardSummaryResponse.FirmwareFailureCount(
            (String) row[0], ((Number) row[1]).longValue()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<DashboardSummaryResponse.FailingDeviceSummary> recentFailingDevices(
      DashboardFilter f) {
    String sql = """
        SELECT d.id, d.external_device_id,
               MAX(tr.completed_at) AS last_failed_at,
               COUNT(tr.id)         AS failure_count
        FROM test_run tr
        JOIN device d ON tr.device_id = d.id
        WHERE tr.status IN ('FAILED', 'TIMED_OUT')
          AND (CAST(:fw   AS text) IS NULL OR tr.firmware_build_id IN
               (SELECT id FROM firmware_build WHERE version = CAST(:fw AS text)))
          AND (CAST(:env  AS text) IS NULL OR tr.environment = CAST(:env  AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        GROUP BY d.id, d.external_device_id
        ORDER BY last_failed_at DESC NULLS LAST
        LIMIT :limit
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("fw",    f.firmwareVersion());
    q.setParameter("env",   f.environment());
    q.setParameter("from",  toTs(f.from()));
    q.setParameter("to",    toTs(f.to()));
    q.setParameter("limit", RECENT_N);

    return ((List<Object[]>) q.getResultList()).stream()
        .map(row -> new DashboardSummaryResponse.FailingDeviceSummary(
            UUID.fromString(row[0].toString()),
            (String) row[1],
            toInstantSafe(row[2]),
            ((Number) row[3]).longValue()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<DashboardSummaryResponse.SuiteDurationSummary> slowestSuites(DashboardFilter f) {
    String sql = """
        SELECT ts.name,
               AVG(EXTRACT(EPOCH FROM (tr.completed_at - tr.started_at)) * 1000) AS avg_ms
        FROM test_run tr
        JOIN test_suite ts ON tr.suite_id = ts.id
        WHERE tr.completed_at IS NOT NULL AND tr.started_at IS NOT NULL
          AND (CAST(:fw   AS text) IS NULL OR tr.firmware_build_id IN
               (SELECT id FROM firmware_build WHERE version = CAST(:fw AS text)))
          AND (CAST(:env  AS text) IS NULL OR tr.environment = CAST(:env  AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        GROUP BY ts.name
        ORDER BY avg_ms DESC NULLS LAST
        LIMIT :limit
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("fw",    f.firmwareVersion());
    q.setParameter("env",   f.environment());
    q.setParameter("from",  toTs(f.from()));
    q.setParameter("to",    toTs(f.to()));
    q.setParameter("limit", RECENT_N);

    return ((List<Object[]>) q.getResultList()).stream()
        .map(row -> new DashboardSummaryResponse.SuiteDurationSummary(
            (String) row[0],
            row[1] != null ? ((Number) row[1]).doubleValue() : 0.0))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<DashboardSummaryResponse.ActivityEntry> latestActivity(DashboardFilter f) {
    String sql = """
        SELECT tr.id, d.external_device_id, ts.name, tr.status, tr.started_at
        FROM test_run tr
        JOIN device d      ON d.id  = tr.device_id
        JOIN test_suite ts ON ts.id = tr.suite_id
        JOIN firmware_build fb ON fb.id = tr.firmware_build_id
        WHERE (CAST(:fw  AS text) IS NULL OR fb.version     = CAST(:fw  AS text))
          AND (CAST(:env AS text) IS NULL OR tr.environment = CAST(:env AS text))
          AND (CAST(:from AS timestamptz) IS NULL OR tr.started_at >= CAST(:from AS timestamptz))
          AND (CAST(:to   AS timestamptz) IS NULL OR tr.started_at <= CAST(:to   AS timestamptz))
        ORDER BY tr.created_at DESC
        LIMIT :limit
        """;
    var q = em.createNativeQuery(sql);
    q.setParameter("fw",    f.firmwareVersion());
    q.setParameter("env",   f.environment());
    q.setParameter("from",  toTs(f.from()));
    q.setParameter("to",    toTs(f.to()));
    q.setParameter("limit", ACTIVITY_N);

    return ((List<Object[]>) q.getResultList()).stream()
        .map(row -> new DashboardSummaryResponse.ActivityEntry(
            UUID.fromString(row[0].toString()),
            (String) row[1],
            (String) row[2],
            (String) row[3],
            toInstantSafe(row[4])))
        .toList();
  }

  private static Timestamp toTs(Instant instant) {
    return instant != null ? Timestamp.from(instant) : null;
  }

  /** Handles Instant, OffsetDateTime, or legacy Timestamp returned by JDBC/Hibernate. */
  private static Instant toInstantSafe(Object obj) {
    if (obj == null)                    return null;
    if (obj instanceof Instant i)       return i;
    if (obj instanceof OffsetDateTime o) return o.toInstant();
    if (obj instanceof Timestamp ts)    return ts.toInstant();
    return null;
  }
}
