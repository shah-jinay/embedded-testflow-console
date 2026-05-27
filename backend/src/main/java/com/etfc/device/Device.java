package com.etfc.device;

import com.etfc.firmware.FirmwareBuild;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "device")
@Getter
@NoArgsConstructor
public class Device {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Setter
  @Column(name = "external_device_id", nullable = false, unique = true, length = 200)
  private String externalDeviceId;

  @Setter
  @Column(name = "board_revision", length = 50)
  private String boardRevision;

  @Setter
  @Column(name = "mcu_family", length = 100)
  private String mcuFamily;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_firmware_build_id")
  private FirmwareBuild currentFirmwareBuild;

  @Setter
  @Column(name = "environment", length = 100)
  private String environment;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private DeviceStatus status = DeviceStatus.ACTIVE;

  @Setter
  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Setter
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> metadata = new HashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static Device create(String externalDeviceId, String boardRevision,
      String mcuFamily, String environment) {
    var d = new Device();
    d.externalDeviceId = externalDeviceId;
    d.boardRevision = boardRevision;
    d.mcuFamily = mcuFamily;
    d.environment = environment;
    return d;
  }
}
