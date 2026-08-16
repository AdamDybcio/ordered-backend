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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderItem;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductRepository productRepository;
  @Mock private PricingService pricingService;
  @Mock private UserRepository userRepository;
  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private StockService stockService;
  @Mock private CartRepository cartRepository;
  @Mock private CartService cartService;
  @Mock private AddressRepository addressRepository;

  @InjectMocks private OrderService orderService;

  private User buyer;
  private Product product;
  private Address address;

  @BeforeEach
  void setUp() {
    buyer = new User();
    buyer.setId(1L);

    product = new Product();
    product.setId(10L);
    product.setName("Test product");

    address =
        Address.builder()
            .id(50L)
            .user(buyer)
            .recipientName("Jan Kowalski")
            .street("Długa")
            .buildingNumber("12")
            .city("Toruń")
            .postalCode("87-100")
            .country("PL")
            .build();
  }

  private Cart cartWith(CartItem... items) {
    Cart cart = Cart.builder().id(500L).user(buyer).build();
    for (CartItem item : items) {
      cart.getItems().add(item);
    }
    return cart;
  }

  @Test
  void placeOrderFromCart_happyPath_createsOrderDecrementsStockAndClearsCart() {
    Stock stock = new Stock(10L, 5);
    CartItem cartItem = CartItem.builder().id(1L).product(product).quantity(2).build();
    Cart cart = cartWith(cartItem);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(address));
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(stockService.getQuantity(10L)).thenReturn(5);
    when(stockService.decrementForOrder(10L, 2))
        .thenAnswer(
            inv -> {
              stock.setQuantity(3);
              return stock;
            });
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("19.99"));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            inv -> {
              Order o = inv.getArgument(0);
              o.setId(100L);
              return o;
            });
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    Order result = orderService.placeOrderFromCart(1L, 50L);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getTotalAmount()).isEqualByComparingTo("39.98");
    assertThat(result.getDeliveryAddress().getCity()).isEqualTo("Toruń");
    assertThat(stock.getQuantity()).isEqualTo(3);
    verify(cartService).clearCart(1L);
  }

  @Test
  void placeOrderFromCart_mergesDuplicateProductIdsAcrossItems() {
    Stock stock = new Stock(10L, 10);
    CartItem itemA = CartItem.builder().id(1L).product(product).quantity(2).build();
    CartItem itemB = CartItem.builder().id(2L).product(product).quantity(3).build();
    Cart cart = cartWith(itemA, itemB);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(address));
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(stockService.getQuantity(10L)).thenReturn(10);
    when(stockService.decrementForOrder(10L, 5))
        .thenAnswer(
            inv -> {
              stock.setQuantity(5);
              return stock;
            });
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("10.00"));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            inv -> {
              Order o = inv.getArgument(0);
              o.setId(100L);
              return o;
            });
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    Order result = orderService.placeOrderFromCart(1L, 50L);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);
    verify(stockService, times(1)).decrementForOrder(10L, 5);
  }

  @Test
  void placeOrderFromCart_emptyCart_throwsEmptyCartException() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(address));
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cartWith()));

    assertThatThrownBy(() -> orderService.placeOrderFromCart(1L, 50L))
        .isInstanceOf(EmptyCartException.class);

    verify(orderRepository, never()).save(any(Order.class));
    verifyNoInteractions(stockService);
  }

  @Test
  void placeOrderFromCart_noCartAtAll_throwsEmptyCartException() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(address));
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.placeOrderFromCart(1L, 50L))
        .isInstanceOf(EmptyCartException.class);
  }

  @Test
  void placeOrderFromCart_addressNotOwnedByUser_throwsAddressNotFoundException() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.placeOrderFromCart(1L, 50L))
        .isInstanceOf(AddressNotFoundException.class);

    verifyNoInteractions(cartRepository);
  }

  @Test
  void placeOrderFromCart_insufficientStock_throwsAndDoesNotPersistOrder() {
    Stock stock = new Stock(10L, 1);
    CartItem cartItem = CartItem.builder().id(1L).product(product).quantity(5).build();
    Cart cart = cartWith(cartItem);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(address));
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(stockService.getQuantity(10L)).thenReturn(1);
    when(stockService.decrementForOrder(10L, 5)).thenReturn(stock);

    assertThatThrownBy(() -> orderService.placeOrderFromCart(1L, 50L))
        .isInstanceOf(InsufficientStockException.class);

    verify(orderRepository, never()).save(any(Order.class));
    verify(cartService, never()).clearCart(any());
  }

  @Test
  void placeOrderFromCart_userNotFound_throwsIllegalStateException() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.placeOrderFromCart(1L, 50L))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(addressRepository, cartRepository);
  }

  @Test
  void placeOrderFromCart_productDeletedAfterAddedToCart_throwsProductNotFoundException() {
    CartItem cartItem = CartItem.builder().id(1L).product(product).quantity(1).build();
    Cart cart = cartWith(cartItem);

    when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
    when(addressRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(address));
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.placeOrderFromCart(1L, 50L))
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

  @Test
  void updateStatus_adminCanConfirmPendingOrder() {
    Order order = Order.builder().id(100L).buyer(buyer).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.updateStatus(100L, 999L, true, OrderStatus.CONFIRMED);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderRepository).save(order);
  }

  @Test
  void updateStatus_buyerCanCancelOwnPendingOrder() {
    Order order = Order.builder().id(100L).buyer(buyer).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.updateStatus(100L, 1L, false, OrderStatus.CANCELLED);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void updateStatus_buyerCannotConfirmOwnOrder_throwsNotAllowed() {
    Order order = Order.builder().id(100L).buyer(buyer).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateStatus(100L, 1L, false, OrderStatus.CONFIRMED))
        .isInstanceOf(OrderStatusChangeNotAllowedException.class);

    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateStatus_sellerCanShipOrderContainingTheirProduct() {
    User seller = new User();
    seller.setId(55L);
    product.setSeller(seller);

    OrderItem item =
        OrderItem.builder()
            .product(product)
            .quantity(1)
            .unitPrice(new BigDecimal("10.00"))
            .subtotal(new BigDecimal("10.00"))
            .build();

    Order order = Order.builder().id(100L).buyer(buyer).status(OrderStatus.CONFIRMED).build();
    order.addItem(item);

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.updateStatus(100L, 55L, false, OrderStatus.SHIPPED);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
  }

  @Test
  void updateStatus_sellerCannotShipOrderWithoutTheirProduct_throwsNotAllowed() {
    User seller = new User();
    seller.setId(55L);
    product.setSeller(seller);

    OrderItem item =
        OrderItem.builder()
            .product(product)
            .quantity(1)
            .unitPrice(new BigDecimal("10.00"))
            .subtotal(new BigDecimal("10.00"))
            .build();

    Order order = Order.builder().id(100L).buyer(buyer).status(OrderStatus.CONFIRMED).build();
    order.addItem(item);

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateStatus(100L, 999L, false, OrderStatus.SHIPPED))
        .isInstanceOf(OrderStatusChangeNotAllowedException.class);

    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateStatus_invalidTransition_throwsInvalidOrderStatusTransitionException() {
    Order order = Order.builder().id(100L).buyer(buyer).status(OrderStatus.DELIVERED).build();
    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateStatus(100L, 1L, false, OrderStatus.CANCELLED))
        .isInstanceOf(InvalidOrderStatusTransitionException.class);

    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void updateStatus_orderNotFound_throwsOrderNotFoundException() {
    when(orderRepository.findById(100L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.updateStatus(100L, 1L, true, OrderStatus.CONFIRMED))
        .isInstanceOf(OrderNotFoundException.class);
  }
}
