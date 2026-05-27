package com.etfc.device;

import com.etfc.testrun.TestRunStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

  Optional<Device> findByExternalDeviceId(String externalDeviceId);

  boolean existsByExternalDeviceId(String externalDeviceId);

  @Query("SELECT COUNT(r) FROM TestRun r WHERE r.device.id = :deviceId")
  long countRunsByDeviceId(@Param("deviceId") UUID deviceId);

  @Query(
      """
      SELECT COUNT(r) FROM TestRun r
      WHERE r.device.id = :deviceId
        AND r.status IN (:failedStatuses)
      """)
  long countFailedRunsByDeviceId(
      @Param("deviceId") UUID deviceId,
      @Param("failedStatuses") java.util.List<TestRunStatus> failedStatuses);
}
