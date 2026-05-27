package com.etfc.firmware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.etfc.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class FirmwareBuildRepositoryTest extends AbstractRepositoryTest {

  @Autowired
  FirmwareBuildRepository firmwareBuildRepository;

  @Autowired
  EntityManager em;

  @Test
  void saves_and_finds_by_version() {
    var fb = FirmwareBuild.create("v2.8.4", "release/2.8", "abc1234", true);
    firmwareBuildRepository.save(fb);
    em.flush();
    em.clear();

    var found = firmwareBuildRepository.findByVersion("v2.8.4");
    assertThat(found).isPresent();
    assertThat(found.get().getBranch()).isEqualTo("release/2.8");
    assertThat(found.get().getCommitHash()).isEqualTo("abc1234");
    assertThat(found.get().isReleaseCandidate()).isTrue();
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void rejects_duplicate_version() {
    firmwareBuildRepository.save(FirmwareBuild.create("v2.8.4-dup", "main", "aaa", false));
    em.flush();

    assertThatThrownBy(() -> {
      firmwareBuildRepository.save(FirmwareBuild.create("v2.8.4-dup", "main", "bbb", false));
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void exists_by_version_returns_false_for_unknown() {
    assertThat(firmwareBuildRepository.existsByVersion("v9.9.9-nonexistent")).isFalse();
  }
}
