package pl.dybcio.ordered.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.dto.UpdateProductRequest;
import pl.dybcio.ordered.catalog.service.ProductService;
import pl.dybcio.ordered.history.service.BrowsingHistoryService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product Catalog - View and manage your seller's offerings")
public class ProductController {

  private final ProductService productService;
  private final BrowsingHistoryService browsingHistoryService;
  private final UserRepository userRepository;

  public ProductController(
      ProductService productService,
      BrowsingHistoryService browsingHistoryService,
      UserRepository userRepository) {
    this.productService = productService;
    this.browsingHistoryService = browsingHistoryService;
    this.userRepository = userRepository;
  }

  @Operation(summary = "Create a new product (SELLER only)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Product created",
        content = @Content(schema = @Schema(implementation = ProductResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PreAuthorize("hasRole('SELLER')")
  @PostMapping
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request,
      @AuthenticationPrincipal UserDetails principal) {
    ProductResponse created = productService.createProduct(request, principal.getUsername());
    return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
  }

  @Operation(summary = "List of products of the logged-in seller")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = ProductResponse.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PreAuthorize("hasRole('SELLER')")
  @GetMapping("/mine")
  public List<ProductResponse> getMyProducts(@AuthenticationPrincipal UserDetails principal) {
    return productService.getProductsBySeller(principal.getUsername());
  }

  @Operation(summary = "List of all active products (public)")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = ProductResponse.class)))
  @SecurityRequirements
  @GetMapping
  public Page<ProductResponse> getAllProducts(@ParameterObject Pageable pageable) {
    return productService.getAllProducts(pageable);
  }

  @Operation(summary = "Get product by ID (public)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = ProductResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Product not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @SecurityRequirements
  @GetMapping("/{id}")
  public ProductResponse getProduct(
      @Parameter(description = "Product ID") @PathVariable Long id, Authentication authentication) {
    ProductResponse product = productService.getProduct(id);
    recordViewIfAuthenticated(authentication, id);
    return product;
  }

  private void recordViewIfAuthenticated(Authentication authentication, Long productId) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return;
    }
    User user = userRepository.findByEmail(authentication.getName()).orElse(null);
    if (user != null) {
      browsingHistoryService.recordView(user.getId(), productId);
    }
  }

  @Operation(summary = "Update product (SELLER or ADMIN only)")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = ProductResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Product not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
  @PutMapping("/{id}")
  public ProductResponse updateProduct(
      @PathVariable Long id,
      @Valid @RequestBody UpdateProductRequest request,
      @AuthenticationPrincipal UserDetails principal,
      Authentication authentication) {
    return productService.updateProduct(
        id, request, principal.getUsername(), isAdmin(authentication));
  }

  @Operation(summary = "Deactivate product - soft delete (owner-SELLER or ADMIN)")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Product deactivated"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Product not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivateProduct(
      @PathVariable Long id,
      @AuthenticationPrincipal UserDetails principal,
      Authentication authentication) {
    productService.deactivateProduct(id, principal.getUsername(), isAdmin(authentication));
    return ResponseEntity.noContent().build();
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
