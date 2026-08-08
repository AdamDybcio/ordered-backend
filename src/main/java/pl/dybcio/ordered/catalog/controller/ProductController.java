package pl.dybcio.ordered.catalog.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.service.ProductService;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @PreAuthorize("hasRole('SELLER')")
  @PostMapping
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request,
      @AuthenticationPrincipal UserDetails principal) {
    ProductResponse created = productService.createProduct(request, principal.getUsername());
    return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
  }

  @PreAuthorize("hasRole('SELLER')")
  @GetMapping("/mine")
  public List<ProductResponse> getMyProducts(@AuthenticationPrincipal UserDetails principal) {
    return productService.getProductsBySeller(principal.getUsername());
  }

  @GetMapping
  public List<ProductResponse> getAllProducts() {
    return productService.getAllProducts();
  }

  @GetMapping("/{id}")
  public ProductResponse getProduct(@PathVariable Long id) {
    return productService.getProduct(id);
  }
}
