package pl.dybcio.ordered.config;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Configuration
@Profile("load-test-seed")
@RequiredArgsConstructor
public class LoadTestSeeder {

  private static final String SEEDER_EMAIL = "load-test-seller@ordered.local";
  private static final int PRODUCT_COUNT = 20;
  private static final int STOCK_PER_PRODUCT = 1000;

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final StockRepository stockRepository;
  private final PricingService pricingService;

  @Bean
  public CommandLineRunner seedLoadTestData() {
    return args -> {
      if (userRepository.findByEmail(SEEDER_EMAIL).isPresent()) {
        System.out.println("Load-test data already seeded, skipping.");
        return;
      }

      User seller =
          User.builder()
              .email(SEEDER_EMAIL)
              .password("not-used-for-login")
              .firstName("Load")
              .lastName("Seller")
              .build();
      seller = userRepository.save(seller);

      for (int i = 1; i <= PRODUCT_COUNT; i++) {
        Product product = new Product();
        product.setName("Load test product " + i);
        product.setSeller(seller);
        product = productRepository.save(product);

        stockRepository.save(new Stock(product.getId(), STOCK_PER_PRODUCT));
        pricingService.setPrice(product.getId(), new BigDecimal("19.99"));
      }

      System.out.println("Seeded " + PRODUCT_COUNT + " load-test products.");
    };
  }
}
