package pl.dybcio.ordered.order.dto;

import java.math.BigDecimal;
import pl.dybcio.ordered.order.entity.OrderItem;

public record OrderItemResponse(
    Long productId,
    String productName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal) {

  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(
        item.getProduct().getId(),
        item.getProduct().getName(),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getSubtotal());
  }
}
