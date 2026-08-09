package pl.dybcio.ordered.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.dto.UpdateProductRequest;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.service.StockService;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private StockService stockService;
  @Mock private PricingService pricingService;
  @Mock private UserRepository userRepository;

  @InjectMocks private ProductService productService;

  private User seller;
  private Product product;

  @BeforeEach
  void setUp() {
    seller = new User();
    seller.setId(1L);
    seller.setEmail("seller@test.pl");

    product = new Product();
    product.setId(10L);
    product.setName("Test product");
    product.setSeller(seller);
    product.setActive(true);
  }

  @Test
  void createProduct_happyPath_initializesStockAndPrice() {
    CreateProductRequest request =
        new CreateProductRequest("Nowy produkt", "Opis", new BigDecimal("29.99"), 15);

    when(userRepository.findByEmail("seller@test.pl")).thenReturn(Optional.of(seller));
    when(productRepository.save(any(Product.class)))
        .thenAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              p.setId(20L);
              return p;
            });
    when(pricingService.getCurrentPrice(20L)).thenReturn(new BigDecimal("29.99"));
    when(stockService.getQuantity(20L)).thenReturn(15);

    ProductResponse response = productService.createProduct(request, "seller@test.pl");

    assertThat(response.id()).isEqualTo(20L);
    assertThat(response.name()).isEqualTo("Nowy produkt");
    verify(stockService).initializeStock(20L, 15);
    verify(pricingService).setPrice(20L, new BigDecimal("29.99"));
  }

  @Test
  void createProduct_sellerNotFound_throwsIllegalStateException() {
    CreateProductRequest request =
        new CreateProductRequest("Nowy produkt", "Opis", new BigDecimal("29.99"), 15);
    when(userRepository.findByEmail("ghost@test.pl")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.createProduct(request, "ghost@test.pl"))
        .isInstanceOf(IllegalStateException.class);

    verify(productRepository, never()).save(any());
  }

  @Test
  void getAllProducts_returnsOnlyActiveProducts() {
    when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("19.99"));
    when(stockService.getQuantity(10L)).thenReturn(5);

    List<ProductResponse> result = productService.getAllProducts();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(10L);
    verify(productRepository).findByActiveTrue();
  }

  @Test
  void getProductsBySeller_returnsSellersProducts() {
    when(productRepository.findBySeller_Email("seller@test.pl")).thenReturn(List.of(product));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("19.99"));
    when(stockService.getQuantity(10L)).thenReturn(5);

    List<ProductResponse> result = productService.getProductsBySeller("seller@test.pl");

    assertThat(result).hasSize(1);
  }

  @Test
  void getProduct_found_returnsResponse() {
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("19.99"));
    when(stockService.getQuantity(10L)).thenReturn(5);

    ProductResponse response = productService.getProduct(10L);

    assertThat(response.id()).isEqualTo(10L);
  }

  @Test
  void getProduct_notFound_throwsProductNotFoundException() {
    when(productRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProduct(10L))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void updateProduct_owner_updatesFields() {
    UpdateProductRequest request =
        new UpdateProductRequest("Zmieniona nazwa", "Nowy opis", new BigDecimal("39.99"), 8);

    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("39.99"));
    when(stockService.getQuantity(10L)).thenReturn(8);

    ProductResponse response = productService.updateProduct(10L, request, "seller@test.pl", false);

    assertThat(response.name()).isEqualTo("Zmieniona nazwa");
    verify(pricingService).setPrice(10L, new BigDecimal("39.99"));
    verify(stockService).setQuantity(10L, 8);
  }

  @Test
  void updateProduct_notOwnerNotAdmin_throwsProductOwnershipException() {
    UpdateProductRequest request =
        new UpdateProductRequest("Zmieniona nazwa", "Nowy opis", new BigDecimal("39.99"), 8);
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.updateProduct(10L, request, "intruder@test.pl", false))
        .isInstanceOf(ProductOwnershipException.class);

    verify(productRepository, never()).save(any());
  }

  @Test
  void updateProduct_admin_canUpdateAnyProduct() {
    UpdateProductRequest request =
        new UpdateProductRequest("Zmieniona nazwa", "Nowy opis", new BigDecimal("39.99"), 8);
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    when(pricingService.getCurrentPrice(10L)).thenReturn(new BigDecimal("39.99"));
    when(stockService.getQuantity(10L)).thenReturn(8);

    ProductResponse response = productService.updateProduct(10L, request, "admin@test.pl", true);

    assertThat(response.name()).isEqualTo("Zmieniona nazwa");
  }

  @Test
  void deactivateProduct_owner_setsInactive() {
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    productService.deactivateProduct(10L, "seller@test.pl", false);

    assertThat(product.isActive()).isFalse();
    verify(productRepository).save(product);
  }

  @Test
  void deactivateProduct_notOwnerNotAdmin_throwsProductOwnershipException() {
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.deactivateProduct(10L, "intruder@test.pl", false))
        .isInstanceOf(ProductOwnershipException.class);

    verify(productRepository, never()).save(any());
  }
}
