package pl.dybcio.ordered.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.history.entity.BrowsingHistoryEntry;
import pl.dybcio.ordered.history.repository.BrowsingHistoryRepository;
import pl.dybcio.ordered.user.dto.LoginRequest;
import pl.dybcio.ordered.user.dto.LoginResponse;
import pl.dybcio.ordered.user.dto.RegisterRequest;
import pl.dybcio.ordered.user.dto.UserResponse;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BrowsingHistoryIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private BrowsingHistoryRepository browsingHistoryRepository;

  private Long productId;

  @BeforeEach
  void setUp() {
    User seller =
        User.builder()
            .email("history-seller-" + System.nanoTime() + "@test.pl")
            .password("irrelevant")
            .firstName("Jan")
            .lastName("Sprzedawca")
            .build();
    seller = userRepository.save(seller);

    Product product = new Product();
    product.setName("Produkt do historii");
    product.setSeller(seller);
    product = productRepository.save(product);
    productId = product.getId();
  }

  private String registerAndLogin(String email) {
    RegisterRequest registerRequest = new RegisterRequest(email, "haslo1234", "Jan", "Testowy");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest(email, "haslo1234");
    LoginResponse loginResponse =
        restTemplate.postForObject("/api/v1/auth/login", loginRequest, LoginResponse.class);
    return loginResponse.token();
  }

  private HttpEntity<Object> authEntity(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(headers);
  }

  @Test
  void viewingProduct_asAuthenticatedUser_recordsHistoryAsynchronously() {
    String email = "history-buyer1-" + System.nanoTime() + "@test.pl";
    String token = registerAndLogin(email);
    User buyer = userRepository.findByEmail(email).orElseThrow();

    restTemplate.exchange(
        "/api/v1/products/" + productId, HttpMethod.GET, authEntity(token), String.class);

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<BrowsingHistoryEntry> history =
                  browsingHistoryRepository.findByUserIdOrderByViewedAtDesc(
                      buyer.getId(), PageRequest.of(0, 10));
              assertThat(history).hasSize(1);
              assertThat(history.get(0).getProductId()).isEqualTo(productId);
            });
  }

  @Test
  void viewingProduct_asAnonymousUser_doesNotThrowAndDoesNotRecordHistory() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/products/" + productId, String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
  }

  @Test
  void getMyHistory_returnsRecordedViews() {
    String email = "history-buyer2-" + System.nanoTime() + "@test.pl";
    String token = registerAndLogin(email);

    restTemplate.exchange(
        "/api/v1/products/" + productId, HttpMethod.GET, authEntity(token), String.class);

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              ResponseEntity<String> response =
                  restTemplate.exchange(
                      "/api/v1/browsing-history?page=0&size=10",
                      HttpMethod.GET,
                      authEntity(token),
                      String.class);
              assertThat(response.getBody()).contains("\"productId\":" + productId);
            });
  }

  @Test
  void getMyHistory_withoutToken_returns401() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/browsing-history", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }
}
