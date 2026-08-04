package pl.dybcio.ordered.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;

  @Mock private StockService stockService;

  @Mock private PricingService pricingService;

  @InjectMocks private ProductService productService;

  @Test
  void shouldCreateProductAndInitializeStockAndPrice() {
    CreateProductRequest request =
        new CreateProductRequest("Mysz bezprzewodowa", "2.4 GHz", new BigDecimal("129.99"), 20);

    Product savedProduct = new Product();
    savedProduct.setId(1L);
    savedProduct.setName(request.name());
    savedProduct.setDescription(request.description());

    when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
    when(pricingService.getCurrentPrice(1L)).thenReturn(new BigDecimal("129.99"));
    when(stockService.getQuantity(1L)).thenReturn(20);

    ProductResponse response = productService.createProduct(request);

    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.name()).isEqualTo("Mysz bezprzewodowa");
    assertThat(response.price()).isEqualByComparingTo(new BigDecimal("129.99"));
    assertThat(response.stockQuantity()).isEqualTo(20);

    verify(productRepository).save(any(Product.class));
    verify(stockService).initializeStock(eq(1L), eq(20));
    verify(pricingService).setPrice(eq(1L), eq(new BigDecimal("129.99")));
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
    Product product = new Product();
    product.setId(5L);
    product.setName("Klawiatura");
    product.setDescription("Mechaniczna");

    when(productRepository.findById(5L)).thenReturn(Optional.of(product));
    when(pricingService.getCurrentPrice(5L)).thenReturn(new BigDecimal("249.99"));
    when(stockService.getQuantity(5L)).thenReturn(15);

    ProductResponse response = productService.getProduct(5L);

    assertThat(response.name()).isEqualTo("Klawiatura");
    assertThat(response.price()).isEqualByComparingTo(new BigDecimal("249.99"));
    assertThat(response.stockQuantity()).isEqualTo(15);
  }
}
