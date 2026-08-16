package pl.dybcio.ordered.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.cart.entity.Cart;
import pl.dybcio.ordered.cart.entity.CartItem;
import pl.dybcio.ordered.cart.repository.CartItemRepository;
import pl.dybcio.ordered.cart.repository.CartRepository;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.catalog.service.ProductNotActiveException;
import pl.dybcio.ordered.catalog.service.ProductNotFoundException;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

  @Mock private CartRepository cartRepository;
  @Mock private CartItemRepository cartItemRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private CartService cartService;

  private User user;
  private Product product;
  private Cart cart;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(1L);

    product = new Product();
    product.setId(10L);
    product.setName("Testowy produkt");
    product.setActive(true);

    cart = Cart.builder().id(100L).user(user).build();
  }

  @Test
  void getOrCreateCart_existingCart_returnsIt() {
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

    Cart result = cartService.getOrCreateCart(1L);

    assertThat(result).isEqualTo(cart);
    verify(cartRepository, never()).save(any(Cart.class));
  }

  @Test
  void getOrCreateCart_noCart_createsAndPersistsNew() {
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(cartRepository.save(any(Cart.class)))
        .thenAnswer(
            inv -> {
              Cart c = inv.getArgument(0);
              c.setId(200L);
              return c;
            });

    Cart result = cartService.getOrCreateCart(1L);

    assertThat(result.getId()).isEqualTo(200L);
    verify(cartRepository).save(any(Cart.class));
  }

  @Test
  void addItem_newProduct_createsCartItem() {
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.empty());

    cartService.addItem(1L, 10L, 2);

    verify(cartItemRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                item -> item.getProduct().equals(product) && item.getQuantity() == 2));
  }

  @Test
  void addItem_existingProduct_mergesQuantity() {
    CartItem existing = CartItem.builder().id(5L).cart(cart).product(product).quantity(3).build();

    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.of(existing));

    cartService.addItem(1L, 10L, 2);

    assertThat(existing.getQuantity()).isEqualTo(5);
    verify(cartItemRepository).save(existing);
  }

  @Test
  void addItem_productNotFound_throwsAndDoesNotSave() {
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cartService.addItem(1L, 10L, 1))
        .isInstanceOf(ProductNotFoundException.class);

    verify(cartItemRepository, never()).save(any(CartItem.class));
  }

  @Test
  void addItem_inactiveProduct_throwsAndDoesNotSave() {
    product.setActive(false);

    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> cartService.addItem(1L, 10L, 1))
        .isInstanceOf(ProductNotActiveException.class)
        .hasMessageContaining("10");

    verify(cartItemRepository, never()).save(any(CartItem.class));
    verify(cartItemRepository, never()).findByCartIdAndProductId(any(), any());
  }

  @Test
  void updateItemQuantity_existingItem_updatesQuantity() {
    CartItem existing = CartItem.builder().id(5L).cart(cart).product(product).quantity(1).build();

    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.of(existing));

    cartService.updateItemQuantity(1L, 10L, 7);

    assertThat(existing.getQuantity()).isEqualTo(7);
    verify(cartItemRepository).save(existing);
  }

  @Test
  void updateItemQuantity_itemNotFound_throws() {
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cartService.updateItemQuantity(1L, 10L, 7))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void removeItem_existingItem_deletesIt() {
    CartItem existing = CartItem.builder().id(5L).cart(cart).product(product).quantity(1).build();

    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.of(existing));

    cartService.removeItem(1L, 10L);

    verify(cartItemRepository).delete(existing);
  }

  @Test
  void removeItem_itemNotFound_throws() {
    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
    when(cartItemRepository.findByCartIdAndProductId(100L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cartService.removeItem(1L, 10L))
        .isInstanceOf(CartItemNotFoundException.class);
  }

  @Test
  void clearCart_removesAllItemsAndSaves() {
    CartItem item = CartItem.builder().id(5L).cart(cart).product(product).quantity(1).build();
    cart.getItems().add(item);

    when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

    cartService.clearCart(1L);

    assertThat(cart.getItems()).isEmpty();
    verify(cartRepository).save(cart);
  }
}
