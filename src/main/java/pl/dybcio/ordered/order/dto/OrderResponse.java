package pl.dybcio.ordered.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;

public record OrderResponse(
    Long id,
    OrderStatus status,
    BigDecimal totalAmount,
    Instant createdAt,
    List<OrderItemResponse> items) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getTotalAmount(),
        order.getCreatedAt(),
        order.getItems().stream().map(OrderItemResponse::from).toList());
  }
}
