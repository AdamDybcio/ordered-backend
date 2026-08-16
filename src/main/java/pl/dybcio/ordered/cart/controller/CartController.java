package pl.dybcio.ordered.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.cart.dto.AddToCartRequest;
import pl.dybcio.ordered.cart.dto.CartItemResponse;
import pl.dybcio.ordered.cart.dto.CartResponse;
import pl.dybcio.ordered.cart.dto.UpdateCartItemRequest;
import pl.dybcio.ordered.cart.entity.Cart;
import pl.dybcio.ordered.cart.service.CartService;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Logged-in user's shopping cart")
public class CartController {

  private final CartService cartService;
  private final PricingService pricingService;
  private final UserRepository userRepository;

  @Operation(summary = "Get the current user's cart")
  @GetMapping
  public CartResponse getCart(Authentication authentication) {
    return toResponse(cartService.getOrCreateCart(currentUserId(authentication)));
  }

  @Operation(summary = "Add a product to the cart (merges quantity if already present)")
  @PostMapping("/items")
  public CartResponse addItem(
      @Valid @RequestBody AddToCartRequest request, Authentication authentication) {
    Cart cart =
        cartService.addItem(currentUserId(authentication), request.productId(), request.quantity());
    return toResponse(cart);
  }

  @Operation(summary = "Set the exact quantity of a cart item")
  @PatchMapping("/items/{productId}")
  public CartResponse updateItem(
      @PathVariable Long productId,
      @Valid @RequestBody UpdateCartItemRequest request,
      Authentication authentication) {
    Cart cart =
        cartService.updateItemQuantity(
            currentUserId(authentication), productId, request.quantity());
    return toResponse(cart);
  }

  @Operation(summary = "Remove a product from the cart")
  @DeleteMapping("/items/{productId}")
  public ResponseEntity<Void> removeItem(
      @PathVariable Long productId, Authentication authentication) {
    cartService.removeItem(currentUserId(authentication), productId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Empty the cart")
  @DeleteMapping
  public ResponseEntity<Void> clear(Authentication authentication) {
    cartService.clearCart(currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  private CartResponse toResponse(Cart cart) {
    var items =
        cart.getItems().stream()
            .map(
                item -> {
                  BigDecimal price = pricingService.getCurrentPrice(item.getProduct().getId());
                  return CartItemResponse.from(item, price);
                })
            .toList();

    BigDecimal total =
        items.stream()
            .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new CartResponse(items, total);
  }

  private Long currentUserId(Authentication authentication) {
    String email = authentication.getName();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    return user.getId();
  }
}
