package com.etfc.testsuite;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_suite")
@Getter
@NoArgsConstructor
public class TestSuite {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Setter
  @Column(name = "name", nullable = false, unique = true, length = 200)
  private String name;

  @Setter
  @Column(name = "target_component", length = 200)
  private String targetComponent;

  @Setter
  @Column(name = "owner", length = 200)
  private String owner;

  @Setter
  @Column(name = "severity", length = 50)
  private String severity;

  @Setter
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @OneToMany(mappedBy = "suite", fetch = FetchType.LAZY, cascade = CascadeType.ALL,
      orphanRemoval = true)
  private List<TestCase> cases = new ArrayList<>();

  public static TestSuite create(String name, String targetComponent, String owner,
      String severity, String description) {
    var s = new TestSuite();
    s.name = name;
    s.targetComponent = targetComponent;
    s.owner = owner;
    s.severity = severity;
    s.description = description;
    return s;
  }
}
