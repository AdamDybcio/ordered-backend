package pl.dybcio.ordered.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.catalog.service.ProductNotFoundException;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;
import pl.dybcio.ordered.order.dto.OrderItemRequest;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductRepository productRepository;
  @Mock private StockRepository stockRepository;
  @Mock private PricingService pricingService;
  @Mock private UserRepository userRepository;

  @InjectMocks private OrderService orderService;

  private User buyer;
  private Product product;

  @BeforeEach
  void setUp() {
    buyer = new User();
    buyer.setId(1L);

    product = new Product();
    product.setId(10L);
    product.setName("Test product");
  }

  @Test
  void placeOrder_happyPath_createsOrderAndDecrementsStock() {
    Stock stock = new Stock(10L, 5);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(stockRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(stock));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("19.99"));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.placeOrder(1L, List.of(new OrderItemRequest(10L, 2)));

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getTotalAmount()).isEqualByComparingTo("39.98");
    assertThat(stock.getQuantity()).isEqualTo(3);
    verify(stockRepository).save(stock);
    verify(orderRepository).save(any(Order.class));
  }

  @Test
  void placeOrder_insufficientStock_throwsAndDoesNotPersistAnything() {
    Stock stock = new Stock(10L, 1);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(stockRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(stock));

    assertThatThrownBy(() -> orderService.placeOrder(1L, List.of(new OrderItemRequest(10L, 5))))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("10");

    verify(stockRepository, never()).save(any(Stock.class));
    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void placeOrder_mergesDuplicateProductIds_locksAndDecrementsOnce() {
    Stock stock = new Stock(10L, 10);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(stockRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(stock));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("10.00"));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    List<OrderItemRequest> items =
        List.of(new OrderItemRequest(10L, 2), new OrderItemRequest(10L, 3));

    Order result = orderService.placeOrder(1L, items);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);
    assertThat(stock.getQuantity()).isEqualTo(5);
    verify(stockRepository, times(1)).findByProductIdForUpdate(10L);
  }

  @Test
  void placeOrder_emptyItems_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> orderService.placeOrder(1L, List.of()))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(userRepository);
  }

  @Test
  void placeOrder_userNotFound_throwsIllegalStateException() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.placeOrder(1L, List.of(new OrderItemRequest(10L, 1))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void placeOrder_productNotFound_throwsProductNotFoundException() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(productRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.placeOrder(1L, List.of(new OrderItemRequest(10L, 1))))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void getOrderForUser_ownOrder_returnsOrder() {
    Order order = Order.builder().id(100L).buyer(buyer).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    Order result = orderService.getOrderForUser(100L, 1L, false);

    assertThat(result).isEqualTo(order);
  }

  @Test
  void getOrderForUser_otherUsersOrder_nonAdmin_throwsNotFound() {
    Order order = Order.builder().id(100L).buyer(buyer).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.getOrderForUser(100L, 999L, false))
        .isInstanceOf(OrderNotFoundException.class);
  }

  @Test
  void getOrderForUser_otherUsersOrder_admin_canAccess() {
    Order order = Order.builder().id(100L).buyer(buyer).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    Order result = orderService.getOrderForUser(100L, 999L, true);

    assertThat(result).isEqualTo(order);
  }

  @Test
  void getOrderForUser_notFound_throwsOrderNotFoundException() {
    when(orderRepository.findById(100L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getOrderForUser(100L, 1L, false))
        .isInstanceOf(OrderNotFoundException.class);
  }
}
