package pl.dybcio.ordered.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pl.dybcio.ordered.order.entity.DeliveryAddress;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;

public record OrderResponse(
    Long id,
    OrderStatus status,
    BigDecimal totalAmount,
    Instant createdAt,
    List<OrderItemResponse> items,
    DeliveryAddressResponse deliveryAddress) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getTotalAmount(),
        order.getCreatedAt(),
        order.getItems().stream().map(OrderItemResponse::from).toList(),
        DeliveryAddressResponse.from(order.getDeliveryAddress()));
  }

  public record DeliveryAddressResponse(
      String recipientName,
      String phone,
      String street,
      String buildingNumber,
      String apartmentNumber,
      String city,
      String postalCode,
      String country) {

    public static DeliveryAddressResponse from(DeliveryAddress a) {
      if (a == null) return null;
      return new DeliveryAddressResponse(
          a.getRecipientName(),
          a.getPhone(),
          a.getStreet(),
          a.getBuildingNumber(),
          a.getApartmentNumber(),
          a.getCity(),
          a.getPostalCode(),
          a.getCountry());
    }
  }
}
