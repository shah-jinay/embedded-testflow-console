package com.etfc.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.etfc.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class DeviceRepositoryTest extends AbstractRepositoryTest {

  @Autowired
  DeviceRepository deviceRepository;

  @Autowired
  EntityManager em;

  @Test
  void saves_and_finds_by_external_id() {
    var device = Device.create("DEV-001", "rev-B", "STM32H7", "lab");
    deviceRepository.save(device);
    em.flush();
    em.clear();

    var found = deviceRepository.findByExternalDeviceId("DEV-001");
    assertThat(found).isPresent();
    assertThat(found.get().getBoardRevision()).isEqualTo("rev-B");
    assertThat(found.get().getMcuFamily()).isEqualTo("STM32H7");
    assertThat(found.get().getStatus()).isEqualTo(DeviceStatus.ACTIVE);
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void persists_jsonb_metadata() {
    var device = Device.create("DEV-002", "rev-A", "NRF52840", "ci");
    device.setMetadata(Map.of("location", "rack-3", "slot", 7));
    deviceRepository.save(device);
    em.flush();
    em.clear();

    var found = deviceRepository.findByExternalDeviceId("DEV-002").orElseThrow();
    assertThat(found.getMetadata()).containsEntry("location", "rack-3");
  }

  @Test
  void rejects_duplicate_external_device_id() {
    deviceRepository.save(Device.create("DEV-DUP", "rev-A", "NRF52", "lab"));
    em.flush();

    assertThatThrownBy(() -> {
      deviceRepository.save(Device.create("DEV-DUP", "rev-B", "NRF52", "lab"));
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void status_defaults_to_active() {
    var device = Device.create("DEV-003", null, null, null);
    deviceRepository.save(device);
    em.flush();
    em.clear();

    assertThat(deviceRepository.findById(device.getId()).orElseThrow().getStatus())
        .isEqualTo(DeviceStatus.ACTIVE);
  }
}
