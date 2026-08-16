package pl.dybcio.ordered.address.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.dybcio.ordered.user.entity.User;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String label;

  @Column(name = "recipient_name", nullable = false)
  private String recipientName;

  @Column(length = 30)
  private String phone;

  @Column(nullable = false)
  private String street;

  @Column(name = "building_number", nullable = false, length = 20)
  private String buildingNumber;

  @Column(name = "apartment_number", length = 20)
  private String apartmentNumber;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(name = "postal_code", nullable = false, length = 20)
  private String postalCode;

  @Column(nullable = false, length = 2)
  @Builder.Default
  private String country = "PL";

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private boolean isDefault = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
