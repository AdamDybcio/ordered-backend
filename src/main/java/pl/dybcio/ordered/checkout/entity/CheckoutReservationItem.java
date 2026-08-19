package pl.dybcio.ordered.checkout.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "checkout_reservation_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutReservationItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id", nullable = false)
  private CheckoutReservation reservation;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal subtotal;
}
