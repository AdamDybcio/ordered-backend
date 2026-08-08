package pl.dybcio.ordered.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.service.StockService;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.Role;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;

  @Mock private StockService stockService;

  @Mock private PricingService pricingService;

  @Mock private UserRepository userRepository;

  @InjectMocks private ProductService productService;

  private User buildSeller() {
    return User.builder()
        .id(7L)
        .email("seller@example.com")
        .password("hashed")
        .firstName("Jan")
        .lastName("Sprzedawca")
        .roles(Set.of(Role.USER, Role.SELLER))
        .build();
  }

  @Test
  void shouldCreateProductAndInitializeStockAndPrice() {
    CreateProductRequest request =
        new CreateProductRequest("Mysz bezprzewodowa", "2.4 GHz", new BigDecimal("129.99"), 20);

    User seller = buildSeller();

    Product savedProduct = new Product();
    savedProduct.setId(1L);
    savedProduct.setName(request.name());
    savedProduct.setDescription(request.description());
    savedProduct.setSeller(seller);

    when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
    when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
    when(pricingService.getCurrentPrice(1L)).thenReturn(new BigDecimal("129.99"));
    when(stockService.getQuantity(1L)).thenReturn(20);

    ProductResponse response = productService.createProduct(request, "seller@example.com");

    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.name()).isEqualTo("Mysz bezprzewodowa");
    assertThat(response.price()).isEqualByComparingTo(new BigDecimal("129.99"));
    assertThat(response.stockQuantity()).isEqualTo(20);
    assertThat(response.sellerId()).isEqualTo(7L);

    verify(productRepository).save(any(Product.class));
    verify(stockService).initializeStock(eq(1L), eq(20));
    verify(pricingService).setPrice(eq(1L), eq(new BigDecimal("129.99")));
  }

  @Test
  void shouldThrowExceptionWhenSellerNotFound() {
    CreateProductRequest request =
        new CreateProductRequest("Mysz", "Opis", new BigDecimal("10.00"), 5);

    when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.createProduct(request, "ghost@example.com"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost@example.com");
  }

  @Test
  void shouldThrowExceptionWhenProductNotFound() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProduct(99L))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  void shouldComposeProductResponseFromThreeSources() {
    User seller = buildSeller();

    Product product = new Product();
    product.setId(5L);
    product.setName("Klawiatura");
    product.setDescription("Mechaniczna");
    product.setSeller(seller);

    when(productRepository.findById(5L)).thenReturn(Optional.of(product));
    when(pricingService.getCurrentPrice(5L)).thenReturn(new BigDecimal("249.99"));
    when(stockService.getQuantity(5L)).thenReturn(15);

    ProductResponse response = productService.getProduct(5L);

    assertThat(response.name()).isEqualTo("Klawiatura");
    assertThat(response.price()).isEqualByComparingTo(new BigDecimal("249.99"));
    assertThat(response.stockQuantity()).isEqualTo(15);
    assertThat(response.sellerId()).isEqualTo(7L);
  }

  @Test
  void shouldReturnOnlyProductsBelongingToSeller() {
    User seller = buildSeller();

    Product product1 = new Product();
    product1.setId(1L);
    product1.setName("Produkt A");
    product1.setSeller(seller);

    Product product2 = new Product();
    product2.setId(2L);
    product2.setName("Produkt B");
    product2.setSeller(seller);

    when(productRepository.findBySeller_Email("seller@example.com"))
        .thenReturn(List.of(product1, product2));
    when(pricingService.getCurrentPrice(1L)).thenReturn(new BigDecimal("10.00"));
    when(pricingService.getCurrentPrice(2L)).thenReturn(new BigDecimal("20.00"));
    when(stockService.getQuantity(1L)).thenReturn(3);
    when(stockService.getQuantity(2L)).thenReturn(4);

    List<ProductResponse> responses = productService.getProductsBySeller("seller@example.com");

    assertThat(responses).hasSize(2);
    assertThat(responses)
        .extracting(ProductResponse::name)
        .containsExactly("Produkt A", "Produkt B");
    assertThat(responses).allMatch(r -> r.sellerId().equals(7L));
  }
}
