package pl.dybcio.ordered.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;
import pl.dybcio.ordered.order.dto.OrderItemRequest;
import pl.dybcio.ordered.order.dto.OrderRequest;
import pl.dybcio.ordered.order.dto.OrderResponse;
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
class OrderControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private PricingService pricingService;

  private Long productId;

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

    Product product = new Product();
    product.setName("Testowy produkt");
    product.setSeller(seller);
    product = productRepository.save(product);
    productId = product.getId();

    stockRepository.save(new Stock(productId, 5));
    pricingService.setPrice(productId, new BigDecimal("19.99"));
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
  void placeOrder_happyPath_returns201AndDecrementsStock() {
    String token = registerAndLogin("buyer1-" + System.nanoTime() + "@test.pl");
    OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 2)));

    ResponseEntity<OrderResponse> response =
        restTemplate.exchange(
            "/api/v1/orders", HttpMethod.POST, authEntity(token, request), OrderResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().items()).hasSize(1);
    assertThat(response.getBody().totalAmount()).isEqualByComparingTo("39.98");

    Stock stock = stockRepository.findById(productId).orElseThrow();
    assertThat(stock.getQuantity()).isEqualTo(3);
  }

  @Test
  void placeOrder_insufficientStock_returns409AndLeavesStockUnchanged() {
    String token = registerAndLogin("buyer2-" + System.nanoTime() + "@test.pl");
    OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 999)));

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/orders", HttpMethod.POST, authEntity(token, request), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    Stock stock = stockRepository.findById(productId).orElseThrow();
    assertThat(stock.getQuantity()).isEqualTo(5);
  }

  @Test
  void placeOrder_withoutToken_returns401() {
    OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 1)));

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/orders", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void getOrder_ownOrder_returns200() {
    String token = registerAndLogin("buyer3-" + System.nanoTime() + "@test.pl");
    OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 1)));
    OrderResponse placed =
        restTemplate
            .exchange(
                "/api/v1/orders", HttpMethod.POST, authEntity(token, request), OrderResponse.class)
            .getBody();

    ResponseEntity<OrderResponse> response =
        restTemplate.exchange(
            "/api/v1/orders/" + placed.id(),
            HttpMethod.GET,
            authEntity(token, null),
            OrderResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().id()).isEqualTo(placed.id());
  }

  @Test
  void getOrder_otherUsersOrder_returns404() {
    String ownerToken = registerAndLogin("owner-" + System.nanoTime() + "@test.pl");
    OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 1)));
    OrderResponse placed =
        restTemplate
            .exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                authEntity(ownerToken, request),
                OrderResponse.class)
            .getBody();

    String intruderToken = registerAndLogin("intruder-" + System.nanoTime() + "@test.pl");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/orders/" + placed.id(),
            HttpMethod.GET,
            authEntity(intruderToken, null),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void listMyOrders_returnsPlacedOrder() {
    String token = registerAndLogin("buyer4-" + System.nanoTime() + "@test.pl");
    OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 1)));
    restTemplate.exchange(
        "/api/v1/orders", HttpMethod.POST, authEntity(token, request), OrderResponse.class);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/orders?page=0&size=10", HttpMethod.GET, authEntity(token, null), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"totalElements\":1");
  }
}
