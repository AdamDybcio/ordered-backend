package pl.dybcio.ordered.order.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.dybcio.ordered.order.dto.OrderItemRequest;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.service.PaymentService;

@Service
@RequiredArgsConstructor
public class OrderPlacementOrchestrator {

  private final OrderService orderService;
  private final PaymentService paymentService;

  public Order placeOrderWithPayment(Long buyerId, List<OrderItemRequest> items) {
    Order order = orderService.placeOrder(buyerId, items);
    PaymentResult result = paymentService.charge(order);
    return orderService.applyPaymentResult(order.getId(), result);
  }
}
