package pl.dybcio.ordered.order.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.catalog.service.ProductNotFoundException;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;
import pl.dybcio.ordered.order.dto.OrderItemRequest;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderItem;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final StockRepository stockRepository;
  private final PricingService pricingService;
  private final UserRepository userRepository;

  @Transactional
  public Order placeOrder(Long buyerId, List<OrderItemRequest> requestedItems) {
    if (requestedItems == null || requestedItems.isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one item");
    }

    User buyer =
        userRepository
            .findById(buyerId)
            .orElseThrow(
                () -> new IllegalStateException("Authenticated user not found: " + buyerId));

    Map<Long, Integer> mergedQuantities =
        requestedItems.stream()
            .collect(
                Collectors.toMap(
                    OrderItemRequest::productId,
                    OrderItemRequest::quantity,
                    Integer::sum,
                    () -> new TreeMap<>(Comparator.naturalOrder())));

    Order order = Order.builder().buyer(buyer).status(OrderStatus.PENDING).build();

    BigDecimal total = BigDecimal.ZERO;

    for (Map.Entry<Long, Integer> entry : mergedQuantities.entrySet()) {
      Long productId = entry.getKey();
      int quantity = entry.getValue();

      Product product =
          productRepository
              .findById(productId)
              .orElseThrow(() -> new ProductNotFoundException(productId));

      Stock stock =
          stockRepository
              .findByProductIdForUpdate(productId)
              .orElseThrow(
                  () -> new IllegalStateException("Missing stock record for product " + productId));

      if (stock.getQuantity() < quantity) {
        throw new InsufficientStockException(productId, quantity, stock.getQuantity());
      }

      stock.setQuantity(stock.getQuantity() - quantity);
      stock.setUpdatedAt(LocalDateTime.now());
      stockRepository.save(stock);

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
    return orderRepository.save(order);
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
          OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
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
}
