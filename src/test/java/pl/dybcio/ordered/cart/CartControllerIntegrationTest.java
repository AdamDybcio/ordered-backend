package pl.dybcio.ordered.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dybcio.ordered.cart.dto.AddToCartRequest;
import pl.dybcio.ordered.cart.dto.CartResponse;
import pl.dybcio.ordered.cart.dto.UpdateCartItemRequest;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;
import pl.dybcio.ordered.pricing.service.PricingService;
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
class CartControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private PricingService pricingService;

  private Long activeProductId;
  private Long inactiveProductId;

  @BeforeEach
  void setUp() {
    User seller =
        User.builder()
            .email("seller-" + System.nanoTime() + "@test.pl")
            .password("irrelevant-not-used-for-login")
            .firstName("Jan")
            .lastName("Sprzedawca")
            .build();
    seller = userRepository.save(seller);

    Product active = new Product();
    active.setName("Aktywny produkt");
    active.setSeller(seller);
    active.setActive(true);
    active = productRepository.save(active);
    activeProductId = active.getId();
    stockRepository.save(new Stock(activeProductId, 10));
    pricingService.setPrice(activeProductId, new BigDecimal("25.00"));

    Product inactive = new Product();
    inactive.setName("Wycofany produkt");
    inactive.setSeller(seller);
    inactive.setActive(false);
    inactive = productRepository.save(inactive);
    inactiveProductId = inactive.getId();
    stockRepository.save(new Stock(inactiveProductId, 10));
    pricingService.setPrice(inactiveProductId, new BigDecimal("15.00"));
  }

  private String registerAndLogin(String email) {
    RegisterRequest registerRequest = new RegisterRequest(email, "haslo1234", "Jan", "Testowy");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest(email, "haslo1234");
    LoginResponse loginResponse =
        restTemplate.postForObject("/api/v1/auth/login", loginRequest, LoginResponse.class);
    return loginResponse.token();
  }

  private HttpEntity<Object> authEntity(String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(body, headers);
  }

  @Test
  void addItem_happyPath_returns200WithItemInCart() {
    String token = registerAndLogin("buyer1-" + System.nanoTime() + "@test.pl");
    AddToCartRequest request = new AddToCartRequest(activeProductId, 2);

    ResponseEntity<CartResponse> response =
        restTemplate.exchange(
            "/api/v1/cart/items", HttpMethod.POST, authEntity(token, request), CartResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().items()).hasSize(1);
    assertThat(response.getBody().estimatedTotal()).isEqualByComparingTo("50.00");
  }

  @Test
  void addItem_sameProductTwice_mergesQuantity() {
    String token = registerAndLogin("buyer2-" + System.nanoTime() + "@test.pl");
    AddToCartRequest request = new AddToCartRequest(activeProductId, 2);

    restTemplate.exchange(
        "/api/v1/cart/items", HttpMethod.POST, authEntity(token, request), CartResponse.class);
    ResponseEntity<CartResponse> response =
        restTemplate.exchange(
            "/api/v1/cart/items", HttpMethod.POST, authEntity(token, request), CartResponse.class);

    assertThat(response.getBody().items()).hasSize(1);
    assertThat(response.getBody().items().get(0).quantity()).isEqualTo(4);
  }

  @Test
  void addItem_inactiveProduct_returns409() {
    String token = registerAndLogin("buyer3-" + System.nanoTime() + "@test.pl");
    AddToCartRequest request = new AddToCartRequest(inactiveProductId, 1);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/cart/items", HttpMethod.POST, authEntity(token, request), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void addItem_productNotFound_returns404() {
    String token = registerAndLogin("buyer4-" + System.nanoTime() + "@test.pl");
    AddToCartRequest request = new AddToCartRequest(999999L, 1);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/cart/items", HttpMethod.POST, authEntity(token, request), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void updateItem_happyPath_returns200WithNewQuantity() {
    String token = registerAndLogin("buyer5-" + System.nanoTime() + "@test.pl");
    restTemplate.exchange(
        "/api/v1/cart/items",
        HttpMethod.POST,
        authEntity(token, new AddToCartRequest(activeProductId, 1)),
        CartResponse.class);

    ResponseEntity<CartResponse> response =
        restTemplate.exchange(
            "/api/v1/cart/items/" + activeProductId,
            HttpMethod.PATCH,
            authEntity(token, new UpdateCartItemRequest(5)),
            CartResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void updateItem_itemNotInCart_returns404() {
    String token = registerAndLogin("buyer6-" + System.nanoTime() + "@test.pl");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/cart/items/" + activeProductId,
            HttpMethod.PATCH,
            authEntity(token, new UpdateCartItemRequest(5)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void removeItem_happyPath_returns204AndEmptiesCart() {
    String token = registerAndLogin("buyer7-" + System.nanoTime() + "@test.pl");
    restTemplate.exchange(
        "/api/v1/cart/items",
        HttpMethod.POST,
        authEntity(token, new AddToCartRequest(activeProductId, 1)),
        CartResponse.class);

    ResponseEntity<Void> deleteResponse =
        restTemplate.exchange(
            "/api/v1/cart/items/" + activeProductId,
            HttpMethod.DELETE,
            authEntity(token, null),
            Void.class);

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<CartResponse> cartResponse =
        restTemplate.exchange(
            "/api/v1/cart", HttpMethod.GET, authEntity(token, null), CartResponse.class);
    assertThat(cartResponse.getBody().items()).isEmpty();
  }

  @Test
  void clearCart_returns204AndEmptiesCart() {
    String token = registerAndLogin("buyer8-" + System.nanoTime() + "@test.pl");
    restTemplate.exchange(
        "/api/v1/cart/items",
        HttpMethod.POST,
        authEntity(token, new AddToCartRequest(activeProductId, 3)),
        CartResponse.class);

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/cart", HttpMethod.DELETE, authEntity(token, null), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void getCart_withoutToken_returns401() {
    ResponseEntity<String> response =
        restTemplate.exchange("/api/v1/cart", HttpMethod.GET, HttpEntity.EMPTY, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
