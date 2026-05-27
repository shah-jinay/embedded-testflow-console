package com.etfc.firmware;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmwareBuildRepository extends JpaRepository<FirmwareBuild, UUID> {

  Optional<FirmwareBuild> findByVersion(String version);

  boolean existsByVersion(String version);
}
