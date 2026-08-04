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

@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final StockService stockService;
  private final PricingService pricingService;

  public ProductService(
      ProductRepository productRepository,
      StockService stockService,
      PricingService pricingService) {
    this.productRepository = productRepository;
    this.stockService = stockService;
    this.pricingService = pricingService;
  }

  @Transactional
  public ProductResponse createProduct(CreateProductRequest request) {
    Product product = new Product();
    product.setName(request.name());
    product.setDescription(request.description());

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
        stockService.getQuantity(product.getId()));
  }
}
