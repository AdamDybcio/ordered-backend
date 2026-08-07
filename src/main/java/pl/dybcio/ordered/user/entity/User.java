package pl.dybcio.ordered.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role")
  @Builder.Default
  private Set<Role> roles = new HashSet<>();

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private boolean enabled;

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    this.enabled = true;
    if (this.roles == null || this.roles.isEmpty()) {
      this.roles = new HashSet<>(Set.of(Role.USER));
    }
  }
}
