package pl.dybcio.ordered.catalog.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.dto.UpdateProductRequest;
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
  public Page<ProductResponse> getAllProducts(Pageable pageable) {
    return productRepository.findByActiveTrue(pageable).map(this::toResponse);
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

  @Transactional
  public ProductResponse updateProduct(
      Long id, UpdateProductRequest request, String requesterEmail, boolean isAdmin) {
    Product product =
        productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    assertOwnership(product, requesterEmail, isAdmin);

    product.setName(request.name());
    product.setDescription(request.description());
    product.setUpdatedAt(LocalDateTime.now());
    Product saved = productRepository.save(product);

    if (request.price() != null) {
      pricingService.setPrice(saved.getId(), request.price());
    }
    if (request.stockQuantity() != null) {
      stockService.setQuantity(saved.getId(), request.stockQuantity());
    }

    return toResponse(saved);
  }

  @Transactional
  public void deactivateProduct(Long id, String requesterEmail, boolean isAdmin) {
    Product product =
        productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    assertOwnership(product, requesterEmail, isAdmin);
    product.setActive(false);
    productRepository.save(product);
  }

  private void assertOwnership(Product product, String requesterEmail, boolean isAdmin) {
    if (isAdmin) {
      return;
    }
    if (!product.getSeller().getEmail().equals(requesterEmail)) {
      throw new ProductOwnershipException(
          "User %s is not the owner of product %d".formatted(requesterEmail, product.getId()));
    }
  }
}
