package pl.dybcio.ordered.catalog.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.service.StockService;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final StockService stockService;
  private final PricingService pricingService;
  private final UserRepository userRepository;

  public ProductService(
      ProductRepository productRepository,
      StockService stockService,
      PricingService pricingService,
      UserRepository userRepository) {
    this.productRepository = productRepository;
    this.stockService = stockService;
    this.pricingService = pricingService;
    this.userRepository = userRepository;
  }

  @Transactional
  public ProductResponse createProduct(CreateProductRequest request, String sellerEmail) {
    User seller =
        userRepository
            .findByEmail(sellerEmail)
            .orElseThrow(
                () -> new IllegalStateException("Authenticated seller not found: " + sellerEmail));

    Product product = new Product();
    product.setName(request.name());
    product.setDescription(request.description());
    product.setSeller(seller);

    Product saved = productRepository.save(product);

    stockService.initializeStock(saved.getId(), request.stockQuantity());
    pricingService.setPrice(saved.getId(), request.price());

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> getAllProducts() {
    return productRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> getProductsBySeller(String sellerEmail) {
    return productRepository.findBySeller_Email(sellerEmail).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse getProduct(Long id) {
    Product product =
        productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    return toResponse(product);
  }

  private ProductResponse toResponse(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getDescription(),
        pricingService.getCurrentPrice(product.getId()),
        stockService.getQuantity(product.getId()),
        product.getSeller().getId());
  }
}
