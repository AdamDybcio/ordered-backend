package pl.dybcio.ordered.order.controller;

import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.dybcio.ordered.order.dto.OrderRequest;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.service.OrderService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;
  private final UserRepository userRepository;

  @PostMapping
  public ResponseEntity<OrderResponse> placeOrder(
      @Valid @RequestBody OrderRequest request, Authentication authentication) {
    Long buyerId = currentUserId(authentication);
    Order order = orderService.placeOrder(buyerId, request.items());
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
  }

  @GetMapping("/{id}")
  public OrderResponse getOrder(@PathVariable Long id, Authentication authentication) {
    Long userId = currentUserId(authentication);
    Order order = orderService.getOrderForUser(id, userId, isAdmin(authentication));
    return OrderResponse.from(order);
  }

  @GetMapping
  public Page<OrderResponse> listMyOrders(Authentication authentication, Pageable pageable) {
    Long userId = currentUserId(authentication);
    return orderService.listOrdersForUser(userId, pageable).map(OrderResponse::from);
  }

  private Long currentUserId(Authentication authentication) {
    String email = authentication.getName();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    return user.getId();
  }

  private boolean isAdmin(Authentication authentication) {
    Set<String> authorities =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());
    return authorities.contains("ROLE_ADMIN");
  }
}
