package pl.dybcio.ordered.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.redis.testcontainers.RedisContainer;
import com.stripe.exception.CardException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;
import pl.dybcio.ordered.payment.service.StripePaymentService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripePaymentIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

  @Autowired private StripePaymentService stripePaymentService;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  private CircuitBreaker circuitBreaker;

  @BeforeEach
  void setUp() {
    circuitBreaker = circuitBreakerRegistry.circuitBreaker("stripePayments");
    circuitBreaker.reset();
    paymentRepository.deleteAll();
  }

  private Order testOrder() {
    User buyer =
        userRepository.save(
            User.builder()
                .email("payment-test-" + System.nanoTime() + "@test.pl")
                .password("irrelevant")
                .firstName("Jan")
                .lastName("Testowy")
                .build());

    Order order =
        Order.builder()
            .buyer(buyer)
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("99.99"))
            .build();

    return orderRepository.save(order);
  }

  @Test
  void charge_success_marksPaymentSucceeded() {
    PaymentIntent fakeIntent = new PaymentIntent();
    fakeIntent.setId("pi_test_123");

    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      mocked
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenReturn(fakeIntent);

      PaymentResult result = stripePaymentService.charge(testOrder());

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getPayment().getStripePaymentIntentId()).isEqualTo("pi_test_123");
    }
  }

  @Test
  void charge_exhaustsRetries_thenFallsBackToPendingRetry() {
    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      mocked
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenThrow(mock(CardException.class));

      PaymentResult result = stripePaymentService.charge(testOrder());

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
      mocked.verify(
          () ->
              PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)),
          Mockito.times(3));
    }
  }

  @Test
  void repeatedFailures_openCircuitBreaker_thenShortCircuitsWithoutCallingStripe() {
    AtomicInteger callCount = new AtomicInteger();

    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      mocked
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenAnswer(
              inv -> {
                callCount.incrementAndGet();
                throw mock(CardException.class);
              });

      for (int i = 0; i < 6; i++) {
        stripePaymentService.charge(testOrder());
      }

      assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

      int callsWhileClosed = callCount.get();
      stripePaymentService.charge(testOrder());

      assertThat(callCount.get())
          .as("Stripe nie powinien być wywołany ponownie przy otwartym CB")
          .isEqualTo(callsWhileClosed);
    }
  }

  @Test
  void whenCircuitForcedOpen_fallbackTriggeredWithoutHittingStripe() {
    circuitBreaker.transitionToOpenState();

    try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
      PaymentResult result = stripePaymentService.charge(testOrder());

      assertThat(result.isSuccess()).isFalse();
      mocked.verifyNoInteractions();
    }
  }
}
