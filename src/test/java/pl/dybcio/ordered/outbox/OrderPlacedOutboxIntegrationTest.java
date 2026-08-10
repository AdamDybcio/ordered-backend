package pl.dybcio.ordered.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
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
import org.testcontainers.kafka.ConfluentKafkaContainer;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.messaging.repository.ProcessedEventRepository;
import pl.dybcio.ordered.order.dto.OrderItemRequest;
import pl.dybcio.ordered.order.dto.OrderRequest;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;
import pl.dybcio.ordered.user.dto.LoginRequest;
import pl.dybcio.ordered.user.dto.LoginResponse;
import pl.dybcio.ordered.user.dto.RegisterRequest;
import pl.dybcio.ordered.user.dto.UserResponse;
import pl.dybcio.ordered.user.entity.Role;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OrderPlacedOutboxIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.7.1");

  @org.springframework.test.context.DynamicPropertySource
  static void kafkaProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private ProcessedEventRepository processedEventRepository;

  private String registerAndLogin(String email, Set<Role> roles) {
    RegisterRequest registerRequest = new RegisterRequest(email, "haslo1234", "Jan", "Testowy");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    if (roles != null) {
      User user = userRepository.findByEmail(email).orElseThrow();
      user.setRoles(roles);
      userRepository.save(user);
    }

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
  void placeOrder_publishesEventAndConsumerProcessesIt() {
    String sellerEmail = "seller-" + System.nanoTime() + "@test.pl";
    String sellerToken = registerAndLogin(sellerEmail, Set.of(Role.USER, Role.SELLER));

    CreateProductRequest createRequest =
        new CreateProductRequest("Produkt testowy", "Opis", new BigDecimal("29.99"), 10);
    Long productId =
        restTemplate
            .exchange(
                "/api/v1/products",
                HttpMethod.POST,
                authEntity(sellerToken, createRequest),
                ProductResponse.class)
            .getBody()
            .id();

    String buyerToken = registerAndLogin("buyer-" + System.nanoTime() + "@test.pl", null);
    OrderRequest orderRequest =
        new OrderRequest(java.util.List.of(new OrderItemRequest(productId, 2)));

    ResponseEntity<OrderResponse> orderResponse =
        restTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            authEntity(buyerToken, orderRequest),
            OrderResponse.class);

    assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long orderId = orderResponse.getBody().id();

    assertThat(outboxEventRepository.findAll())
        .anyMatch(
            e ->
                e.getAggregateId().equals(orderId.toString())
                    && e.getEventType().equals("OrderPlaced"));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var event =
                  outboxEventRepository.findAll().stream()
                      .filter(e -> e.getAggregateId().equals(orderId.toString()))
                      .findFirst()
                      .orElseThrow();
              assertThat(event.getPublishedAt()).isNotNull();
            });

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(processedEventRepository.findAll()).isNotEmpty());
  }
}
