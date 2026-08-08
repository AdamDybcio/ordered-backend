package pl.dybcio.ordered.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.security.JwtService;
import pl.dybcio.ordered.user.entity.Role;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProductControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtService jwtService;
  @Autowired private PasswordEncoder passwordEncoder;

  private UserDetails toUserDetails(User user) {
    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .authorities(
            user.getRoles().stream().map(role -> "ROLE_" + role.name()).toArray(String[]::new))
        .build();
  }

  @Test
  void shouldCreateAndRetrieveProduct() {
    User seller =
        User.builder()
            .email("seller@example.com")
            .password(passwordEncoder.encode("irrelevant"))
            .firstName("Jan")
            .lastName("Sprzedawca")
            .roles(Set.of(Role.USER, Role.SELLER))
            .build();
    userRepository.save(seller);

    String token = jwtService.generateToken(toUserDetails(seller));

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    CreateProductRequest request =
        new CreateProductRequest("Testowy produkt", "Opis testowy", new BigDecimal("99.99"), 10);

    ResponseEntity<ProductResponse> createResponse =
        restTemplate.postForEntity(
            "/api/v1/products", new HttpEntity<>(request, headers), ProductResponse.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ProductResponse createdProduct = createResponse.getBody();
    assertThat(createdProduct).isNotNull();
    assertThat(createdProduct.id()).isNotNull();
    assertThat(createdProduct.name()).isEqualTo("Testowy produkt");

    Long createdId = createdProduct.id();

    ResponseEntity<ProductResponse> getResponse =
        restTemplate.getForEntity("/api/v1/products/" + createdId, ProductResponse.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().price()).isEqualByComparingTo(new BigDecimal("99.99"));
  }

  @Test
  void shouldReturn403_whenUserWithoutSellerRole() {
    User plainUser =
        User.builder()
            .email("buyer@example.com")
            .password(passwordEncoder.encode("irrelevant"))
            .firstName("Anna")
            .lastName("Kupujaca")
            .roles(Set.of(Role.USER))
            .build();
    userRepository.save(plainUser);

    String token = jwtService.generateToken(toUserDetails(plainUser));

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    CreateProductRequest request =
        new CreateProductRequest("Nieautoryzowany produkt", "Opis", new BigDecimal("10.00"), 1);

    ResponseEntity<ProductResponse> response =
        restTemplate.postForEntity(
            "/api/v1/products", new HttpEntity<>(request, headers), ProductResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldReturn401_whenNoToken() {
    CreateProductRequest request =
        new CreateProductRequest("Produkt bez tokenu", "Opis", new BigDecimal("5.00"), 1);

    ResponseEntity<ProductResponse> response =
        restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
