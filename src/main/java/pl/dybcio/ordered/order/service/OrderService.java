package pl.dybcio.ordered.order.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.address.entity.Address;
import pl.dybcio.ordered.address.repository.AddressRepository;
import pl.dybcio.ordered.address.service.AddressNotFoundException;
import pl.dybcio.ordered.cart.entity.Cart;
import pl.dybcio.ordered.cart.entity.CartItem;
import pl.dybcio.ordered.cart.repository.CartRepository;
import pl.dybcio.ordered.cart.service.CartService;
import pl.dybcio.ordered.cart.service.EmptyCartException;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.catalog.service.ProductNotFoundException;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.service.StockService;
import pl.dybcio.ordered.order.entity.DeliveryAddress;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderItem;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.event.OrderPlacedPayload;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final PricingService pricingService;
  private final UserRepository userRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final tools.jackson.databind.ObjectMapper objectMapper;
  private final StockService stockService;
  private final CartRepository cartRepository;
  private final CartService cartService;
  private final AddressRepository addressRepository;

  @Transactional
  public Order placeOrderFromCart(Long buyerId, Long addressId) {
    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(
                () -> new IllegalStateException("Authenticated user not found: " + buyerId));

    Address address =
        addressRepository
            .findByIdAndUserId(addressId, buyerId)
            .orElseThrow(() -> new AddressNotFoundException(addressId));

    Cart cart =
        cartRepository.findByUserId(buyerId).orElseThrow(() -> new EmptyCartException(buyerId));

    if (cart.getItems().isEmpty()) {
      throw new EmptyCartException(buyerId);
    }

    Map<Long, Integer> mergedQuantities =
        cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getProduct().getId(), CartItem::getQuantity, Integer::sum));

    Order order =
        Order.builder()
            .buyer(buyer)
            .status(OrderStatus.PENDING)
            .deliveryAddress(DeliveryAddress.from(address))
            .build();

    BigDecimal total = BigDecimal.ZERO;

    for (Map.Entry<Long, Integer> entry : mergedQuantities.entrySet()) {
      Long productId = entry.getKey();
      int quantity = entry.getValue();

      Product product =
          productRepository
              .findById(productId)
              .orElseThrow(() -> new ProductNotFoundException(productId));

      int quantityBefore = stockService.getQuantity(productId);
      Stock stock = stockService.decrementForOrder(productId, quantity);

      if (stock.getQuantity() == quantityBefore) {
        throw new InsufficientStockException(productId, quantity, quantityBefore);
      }

      BigDecimal unitPrice = pricingService.getCurrentPrice(productId);
      BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
      total = total.add(subtotal);

      order.addItem(
          OrderItem.builder()
              .product(product)
              .quantity(quantity)
              .unitPrice(unitPrice)
              .subtotal(subtotal)
              .build());
    }

    order.setTotalAmount(total);
    Order savedOrder = orderRepository.save(order);

    OrderPlacedPayload payload = OrderPlacedPayload.from(savedOrder);
    outboxEventRepository.save(
        OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(savedOrder.getId().toString())
            .eventType("OrderPlaced")
            .payload(objectMapper.writeValueAsString(payload))
            .build());

    cartService.clearCart(buyerId);

    return savedOrder;
  }

  @Transactional(readOnly = true)
  public Order getOrderForUser(Long orderId, Long requestingUserId, boolean isAdmin) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

    if (!isAdmin && !order.getBuyer().getId().equals(requestingUserId)) {
      throw new OrderNotFoundException(orderId);
    }

    return order;
  }

  @Transactional(readOnly = true)
  public Page<Order> listOrdersForUser(Long buyerId, Pageable pageable) {
    return orderRepository.findByBuyerId(buyerId, pageable);
  }

  private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          OrderStatus.PENDING,
              Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_PENDING),
          OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
          OrderStatus.CONFIRMED, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
          OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
          OrderStatus.DELIVERED, Set.of(),
          OrderStatus.CANCELLED, Set.of());

  @Transactional
  public Order updateStatus(
      Long orderId, Long actingUserId, boolean isAdmin, OrderStatus newStatus) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

    authorizeStatusChange(order, actingUserId, isAdmin, newStatus);

    Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
    if (!allowed.contains(newStatus)) {
      throw new InvalidOrderStatusTransitionException(order.getStatus(), newStatus);
    }

    order.setStatus(newStatus);
    return orderRepository.save(order);
  }

  private void authorizeStatusChange(
      Order order, Long actingUserId, boolean isAdmin, OrderStatus newStatus) {
    if (isAdmin) {
      return;
    }

    boolean isBuyer = order.getBuyer().getId().equals(actingUserId);
    if (isBuyer && newStatus == OrderStatus.CANCELLED) {
      return;
    }

    boolean isSellerInOrder =
        order.getItems().stream()
            .anyMatch(item -> item.getProduct().getSeller().getId().equals(actingUserId));
    if (isSellerInOrder && newStatus == OrderStatus.SHIPPED) {
      return;
    }

    throw new OrderStatusChangeNotAllowedException(
        "User %d is not allowed to change order %d to %s"
            .formatted(actingUserId, order.getId(), newStatus));
  }

  @Transactional
  public Order applyPaymentResult(Long orderId, PaymentResult result) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    order.setStatus(result.isSuccess() ? OrderStatus.CONFIRMED : OrderStatus.PAYMENT_PENDING);
    return orderRepository.save(order);
  }

  @Transactional
  public Order cancelDueToPaymentFailure(Long orderId) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    order.setStatus(OrderStatus.CANCELLED);
    return orderRepository.save(order);
  }
}
