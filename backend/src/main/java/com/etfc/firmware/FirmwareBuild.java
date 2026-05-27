package com.etfc.firmware;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "firmware_build")
@Getter
@NoArgsConstructor
public class FirmwareBuild {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Setter
  @Column(name = "version", nullable = false, unique = true, length = 100)
  private String version;

  @Setter
  @Column(name = "branch", length = 200)
  private String branch;

  @Setter
  @Column(name = "commit_hash", length = 40)
  private String commitHash;

  @Setter
  @Column(name = "is_release_candidate", nullable = false)
  private boolean releaseCandidate = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static FirmwareBuild create(
      String version, String branch, String commitHash, boolean releaseCandidate) {
    var fb = new FirmwareBuild();
    fb.version = version;
    fb.branch = branch;
    fb.commitHash = commitHash;
    fb.releaseCandidate = releaseCandidate;
    return fb;
  }
}
