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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;
import pl.dybcio.ordered.catalog.dto.UpdateProductRequest;
import pl.dybcio.ordered.catalog.entity.Product;
import pl.dybcio.ordered.catalog.repository.ProductRepository;
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
class ProductControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductRepository productRepository;

  // Rejestracja daje tylko ROLE_USER — na potrzeby testu ręcznie dokładamy SELLER,
  // bo w apce na razie nie ma innego mechanizmu nadawania tej roli.
  private String registerSellerAndLogin(String email) {
    RegisterRequest registerRequest = new RegisterRequest(email, "haslo1234", "Jan", "Sprzedawca");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    User user = userRepository.findByEmail(email).orElseThrow();
    user.setRoles(Set.of(Role.USER, Role.SELLER));
    userRepository.save(user);

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
  void createProduct_asSeller_returns201() {
    String token = registerSellerAndLogin("seller1-" + System.nanoTime() + "@test.pl");
    CreateProductRequest request =
        new CreateProductRequest("Nowy produkt", "Opis produktu", new BigDecimal("49.99"), 10);

    ResponseEntity<ProductResponse> response =
        restTemplate.exchange(
            "/api/v1/products", HttpMethod.POST, authEntity(token, request), ProductResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().name()).isEqualTo("Nowy produkt");
  }

  @Test
  void updateProduct_owner_returns200AndUpdatesFields() {
    String token = registerSellerAndLogin("seller2-" + System.nanoTime() + "@test.pl");
    CreateProductRequest createRequest =
        new CreateProductRequest("Stara nazwa", "Stary opis", new BigDecimal("19.99"), 5);
    Long productId =
        restTemplate
            .exchange(
                "/api/v1/products",
                HttpMethod.POST,
                authEntity(token, createRequest),
                ProductResponse.class)
            .getBody()
            .id();

    UpdateProductRequest updateRequest =
        new UpdateProductRequest("Nowa nazwa", "Nowy opis", new BigDecimal("24.99"), 3);

    ResponseEntity<ProductResponse> response =
        restTemplate.exchange(
            "/api/v1/products/" + productId,
            HttpMethod.PUT,
            authEntity(token, updateRequest),
            ProductResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().name()).isEqualTo("Nowa nazwa");
    assertThat(response.getBody().price()).isEqualByComparingTo("24.99");
  }

  @Test
  void updateProduct_notOwner_returns403() {
    String ownerToken = registerSellerAndLogin("seller3-" + System.nanoTime() + "@test.pl");
    CreateProductRequest createRequest =
        new CreateProductRequest("Produkt", "Opis", new BigDecimal("10.00"), 5);
    Long productId =
        restTemplate
            .exchange(
                "/api/v1/products",
                HttpMethod.POST,
                authEntity(ownerToken, createRequest),
                ProductResponse.class)
            .getBody()
            .id();

    String intruderToken = registerSellerAndLogin("seller4-" + System.nanoTime() + "@test.pl");
    UpdateProductRequest updateRequest =
        new UpdateProductRequest("Zhackowana nazwa", "Opis", new BigDecimal("0.01"), 999);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/products/" + productId,
            HttpMethod.PUT,
            authEntity(intruderToken, updateRequest),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void deactivateProduct_owner_returns204AndHidesFromPublicList() {
    String token = registerSellerAndLogin("seller5-" + System.nanoTime() + "@test.pl");
    CreateProductRequest createRequest =
        new CreateProductRequest("Do usuniecia", "Opis", new BigDecimal("15.00"), 2);
    Long productId =
        restTemplate
            .exchange(
                "/api/v1/products",
                HttpMethod.POST,
                authEntity(token, createRequest),
                ProductResponse.class)
            .getBody()
            .id();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v1/products/" + productId,
            HttpMethod.DELETE,
            authEntity(token, null),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    Product product = productRepository.findById(productId).orElseThrow();
    assertThat(product.isActive()).isFalse();

    ResponseEntity<String> listResponse =
        restTemplate.getForEntity("/api/v1/products", String.class);
    assertThat(listResponse.getBody()).doesNotContain("Do usuniecia");
  }

  @Test
  void deactivateProduct_notOwner_returns403() {
    String ownerToken = registerSellerAndLogin("seller6-" + System.nanoTime() + "@test.pl");
    CreateProductRequest createRequest =
        new CreateProductRequest("Cudzy produkt", "Opis", new BigDecimal("15.00"), 2);
    Long productId =
        restTemplate
            .exchange(
                "/api/v1/products",
                HttpMethod.POST,
                authEntity(ownerToken, createRequest),
                ProductResponse.class)
            .getBody()
            .id();

    String intruderToken = registerSellerAndLogin("seller7-" + System.nanoTime() + "@test.pl");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/products/" + productId,
            HttpMethod.DELETE,
            authEntity(intruderToken, null),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void getAllProducts_publicAccess_worksWithoutToken() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
