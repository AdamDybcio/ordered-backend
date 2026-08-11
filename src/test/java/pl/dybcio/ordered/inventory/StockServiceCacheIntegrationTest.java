package pl.dybcio.ordered.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.config.CacheNames;
import pl.dybcio.ordered.inventory.service.StockService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockServiceCacheIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

  @Autowired private StockService stockService;
  @Autowired private CacheManager cacheManager;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;

  private Long createProduct() {
    User seller =
        User.builder()
            .email("cache-test-seller-" + System.nanoTime() + "@test.pl")
            .password("irrelevant")
            .firstName("Jan")
            .lastName("Testowy")
            .build();
    seller = userRepository.save(seller);

    Product product = new Product();
    product.setName("Cache test product");
    product.setSeller(seller);
    product = productRepository.save(product);

    return product.getId();
  }

  @Test
  void getQuantity_populatesCacheAfterFirstCall() {
    Long productId = createProduct();
    stockService.initializeStock(productId, 20);

    Integer result = stockService.getQuantity(productId);
    assertThat(result).isEqualTo(20);

    Cache cache = cacheManager.getCache(CacheNames.PRODUCT_STOCK);
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(cache.get(productId)).isNotNull());
  }

  @Test
  void decrementForOrder_evictsStaleEntryFromCache() {
    Long productId = createProduct();
    stockService.initializeStock(productId, 10);
    stockService.getQuantity(productId);

    Cache cache = cacheManager.getCache(CacheNames.PRODUCT_STOCK);
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(cache.get(productId)).isNotNull());

    stockService.decrementForOrder(productId, 3);

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(cache.get(productId)).isNull());
  }
}
