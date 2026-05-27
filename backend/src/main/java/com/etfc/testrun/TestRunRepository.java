package com.etfc.testrun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestRunRepository
    extends JpaRepository<TestRun, UUID>, JpaSpecificationExecutor<TestRun> {

  Optional<TestRun> findByDeviceIdAndFirmwareBuildIdAndSuiteIdAndExternalRunId(
      UUID deviceId, UUID firmwareBuildId, UUID suiteId, String externalRunId);

  List<TestRun> findByDeviceIdOrderByStartedAtDesc(UUID deviceId);

  List<TestRun> findByFirmwareBuildIdOrderByStartedAtDesc(UUID firmwareBuildId);

  List<TestRun> findTop200ByFirmwareBuildVersionOrderByCreatedAtDesc(String version);

  @Override
  @EntityGraph(attributePaths = {"device", "firmwareBuild", "suite"})
  Page<TestRun> findAll(Specification<TestRun> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"device", "firmwareBuild", "suite"})
  Optional<TestRun> findById(UUID id);
}
