package com.etfc.testrun;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

public final class TestRunSpecification {

  private TestRunSpecification() {}

  public static Specification<TestRun> withFilter(TestRunFilter f) {
    return (root, query, cb) -> {
      query.distinct(true);
      var predicates = new ArrayList<>();

      // Joins are created lazily and reused within this closure.
      var deviceJoin =
          (f.deviceId() != null || f.boardRevision() != null)
              ? root.join("device", JoinType.INNER)
              : null;
      var firmwareJoin =
          (f.firmwareVersion() != null) ? root.join("firmwareBuild", JoinType.INNER) : null;
      var suiteJoin =
          (f.suite() != null) ? root.join("suite", JoinType.INNER) : null;

      if (f.deviceId() != null) {
        predicates.add(cb.equal(deviceJoin.get("id"), f.deviceId()));
      }
      if (f.boardRevision() != null) {
        predicates.add(cb.equal(deviceJoin.get("boardRevision"), f.boardRevision()));
      }
      if (f.firmwareVersion() != null) {
        predicates.add(cb.equal(firmwareJoin.get("version"), f.firmwareVersion()));
      }
      if (f.suite() != null) {
        predicates.add(cb.equal(suiteJoin.get("name"), f.suite()));
      }
      if (f.status() != null) {
        try {
          predicates.add(
              cb.equal(root.get("status"), TestRunStatus.valueOf(f.status().toUpperCase())));
        } catch (IllegalArgumentException ignored) {
          // Unknown status — return no results
          predicates.add(cb.disjunction());
        }
      }
      if (f.failureCategory() != null) {
        try {
          var category = FailureCategory.valueOf(f.failureCategory().toUpperCase());
          Subquery<TestResult> sub = query.subquery(TestResult.class);
          var r = sub.from(TestResult.class);
          sub.select(r)
              .where(
                  cb.equal(r.get("run"), root),
                  cb.equal(r.get("failureCategory"), category));
          predicates.add(cb.exists(sub));
        } catch (IllegalArgumentException ignored) {
          predicates.add(cb.disjunction());
        }
      }
      if (f.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), f.from()));
      }
      if (f.to() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), f.to()));
      }
      if (f.environment() != null) {
        predicates.add(cb.equal(root.get("environment"), f.environment()));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }
}
