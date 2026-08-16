package pl.dybcio.ordered.review;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dybcio.ordered.address.dto.AddressRequest;
import pl.dybcio.ordered.address.dto.AddressResponse;
import pl.dybcio.ordered.cart.dto.AddToCartRequest;
import pl.dybcio.ordered.cart.dto.CartResponse;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.dto.PlaceOrderRequest;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.pricing.service.PricingService;
import pl.dybcio.ordered.review.dto.ReviewRequest;
import pl.dybcio.ordered.review.repository.ReviewRepository;
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
class ReviewControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private PricingService pricingService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private ReviewRepository reviewRepository;

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

  /** Dodaje productId do koszyka usera i zakłada dla niego adres, zwraca addressId. */
  private Long prepareCartAndAddress(String token, Long productId, int quantity) {
    restTemplate.exchange(
        "/api/v1/cart/items",
        HttpMethod.POST,
        authEntity(token, new AddToCartRequest(productId, quantity)),
        CartResponse.class);

    AddressRequest addressRequest =
        new AddressRequest(
            "Dom", "Jan Kowalski", "123456789", "Długa", "12", "3", "Toruń", "87-100", "PL");
    AddressResponse address =
        restTemplate
            .exchange(
                "/api/v1/addresses",
                HttpMethod.POST,
                authEntity(token, addressRequest),
                AddressResponse.class)
            .getBody();
    return address.id();
  }

  private Long placeAndDeliverOrder(String buyerToken) {
    Long addressId = prepareCartAndAddress(buyerToken, productId, 1);

    OrderResponse placed =
        restTemplate
            .exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                authEntity(buyerToken, new PlaceOrderRequest(addressId)),
                OrderResponse.class)
            .getBody();

    Order order = orderRepository.findById(placed.id()).orElseThrow();
    order.setStatus(OrderStatus.DELIVERED);
    orderRepository.save(order);

    return placed.id();
  }

  @Test
  void addReview_afterDeliveredOrder_returns201() {
    String token = registerAndLogin("buyer1-" + System.nanoTime() + "@test.pl");
    placeAndDeliverOrder(token);

    ReviewRequest request = new ReviewRequest(productId, 5, "Świetny produkt!");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/reviews", HttpMethod.POST, authEntity(token, request), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            reviewRepository.findByProductId(
                productId, org.springframework.data.domain.PageRequest.of(0, 10)))
        .isNotNull();
  }

  @Test
  void addReview_withoutPurchase_returns403() {
    String token = registerAndLogin("buyer2-" + System.nanoTime() + "@test.pl");
    ReviewRequest request = new ReviewRequest(productId, 5, "Nie kupiłem tego");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/reviews", HttpMethod.POST, authEntity(token, request), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void addReview_pendingOrderOnly_returns403() {
    String token = registerAndLogin("buyer3-" + System.nanoTime() + "@test.pl");
    Long addressId = prepareCartAndAddress(token, productId, 1);
    restTemplate.exchange(
        "/api/v1/orders",
        HttpMethod.POST,
        authEntity(token, new PlaceOrderRequest(addressId)),
        OrderResponse.class);

    ReviewRequest reviewRequest = new ReviewRequest(productId, 4, "Jeszcze nie dostałem");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/reviews", HttpMethod.POST, authEntity(token, reviewRequest), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void addReview_duplicateReview_returns409() {
    String token = registerAndLogin("buyer4-" + System.nanoTime() + "@test.pl");
    placeAndDeliverOrder(token);

    ReviewRequest request = new ReviewRequest(productId, 5, "Pierwsza recenzja");
    restTemplate.exchange(
        "/api/v1/reviews", HttpMethod.POST, authEntity(token, request), String.class);

    ReviewRequest secondRequest = new ReviewRequest(productId, 3, "Druga próba");
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/reviews", HttpMethod.POST, authEntity(token, secondRequest), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void getReviewsForProduct_isPublic_returns200() {
    String token = registerAndLogin("buyer5-" + System.nanoTime() + "@test.pl");
    placeAndDeliverOrder(token);
    ReviewRequest request = new ReviewRequest(productId, 5, "Super!");
    restTemplate.exchange(
        "/api/v1/reviews", HttpMethod.POST, authEntity(token, request), String.class);

    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/api/v1/reviews/product/" + productId + "?page=0&size=10", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"totalElements\":1");
  }

  @Test
  void addReview_withoutToken_returns401() {
    ReviewRequest request = new ReviewRequest(productId, 5, "Anonimowy");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/reviews", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
