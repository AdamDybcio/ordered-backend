package pl.dybcio.ordered.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.dto.PlaceOrderRequest;
import pl.dybcio.ordered.order.dto.UpdateOrderStatusRequest;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.service.OrderPlacementOrchestrator;
import pl.dybcio.ordered.order.service.OrderService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Placing and managing orders")
public class OrderController {

  private final OrderService orderService;
  private final OrderPlacementOrchestrator orderPlacementOrchestrator;
  private final UserRepository userRepository;

  @Operation(summary = "Place a new order")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        content = @Content(schema = @Schema(implementation = OrderResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Cart is empty",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(
        responseCode = "404",
        description = "One of the products does not exist",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Address not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "502",
        description = "Payment provider unavailable",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping
  public ResponseEntity<OrderResponse> placeOrder(
      @Valid @RequestBody PlaceOrderRequest request, Authentication authentication) {
    Long buyerId = currentUserId(authentication);
    Order order = orderPlacementOrchestrator.placeOrderWithPayment(buyerId, request.addressId());
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
  }

  @Operation(summary = "Get order by ID (owner only or ADMIN)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = OrderResponse.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(
        responseCode = "404",
        description = "Order not found or no access (intentionally 404, not 403)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping("/{id}")
  public OrderResponse getOrder(@PathVariable Long id, Authentication authentication) {
    Long userId = currentUserId(authentication);
    Order order = orderService.getOrderForUser(id, userId, isAdmin(authentication));
    return OrderResponse.from(order);
  }

  @Operation(summary = "List of orders for the logged-in user (paginated)")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = OrderResponse.class)))
  @GetMapping
  public Page<OrderResponse> listMyOrders(
      Authentication authentication, @ParameterObject Pageable pageable) {
    Long userId = currentUserId(authentication);
    return orderService.listOrdersForUser(userId, pageable).map(OrderResponse::from);
  }

  @Operation(
      summary = "Update order status",
      description =
          "Buyer can cancel their own order (PENDING/CONFIRMED). "
              + "Seller can mark as shipped (CONFIRMED → SHIPPED), "
              + "if they sell at least one of the products in the order. ADMIN can change freely.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = OrderResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error (missing status)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(
        responseCode = "403",
        description = "No permissions to perform this status change",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Order not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Prohibited transition between statuses",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PatchMapping("/{id}/status")
  public OrderResponse updateStatus(
      @PathVariable Long id,
      @Valid @RequestBody UpdateOrderStatusRequest request,
      Authentication authentication) {
    Long userId = currentUserId(authentication);
    Order order = orderService.updateStatus(id, userId, isAdmin(authentication), request.status());
    return OrderResponse.from(order);
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
