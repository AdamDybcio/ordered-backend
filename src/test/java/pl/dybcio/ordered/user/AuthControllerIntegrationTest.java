package pl.dybcio.ordered.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.user.dto.LoginRequest;
import pl.dybcio.ordered.user.dto.LoginResponse;
import pl.dybcio.ordered.user.dto.RegisterRequest;
import pl.dybcio.ordered.user.dto.UserResponse;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void register_shouldReturn201_withHappyPath() {
    RegisterRequest request =
        new RegisterRequest("integration1@test.pl", "haslo1234", "Jan", "Testowy");

    ResponseEntity<UserResponse> response =
        restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().email()).isEqualTo("integration1@test.pl");
  }

  @Test
  void register_shouldReturn409_whenEmailAlreadyExists() {
    RegisterRequest request =
        new RegisterRequest("duplicate@test.pl", "haslo1234", "Jan", "Testowy");
    restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("Email already in use");
  }

  @Test
  void register_shouldReturn400_whenValidationFails() {
    RegisterRequest invalidRequest = new RegisterRequest("not-an-email", "short", "", "Testowy");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/auth/register", invalidRequest, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void login_shouldReturnToken_withCorrectCredentials() {
    RegisterRequest registerRequest =
        new RegisterRequest("login1@test.pl", "haslo1234", "Anna", "Nowak");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest("login1@test.pl", "haslo1234");
    ResponseEntity<LoginResponse> response =
        restTemplate.postForEntity("/api/v1/auth/login", loginRequest, LoginResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().token()).isNotBlank();
    assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
  }

  @Test
  void login_shouldReturn401_withWrongPassword() {
    RegisterRequest registerRequest =
        new RegisterRequest("login2@test.pl", "haslo1234", "Anna", "Nowak");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest("login2@test.pl", "zlehaslo");
    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/auth/login", loginRequest, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void login_shouldReturn401_whenUserDoesNotExist() {
    LoginRequest loginRequest = new LoginRequest("nieistnieje@test.pl", "cokolwiek");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/auth/login", loginRequest, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedEndpoint_shouldReturn401_withoutToken() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/orders", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedEndpoint_shouldPassAuthentication_withValidToken() {
    RegisterRequest registerRequest =
        new RegisterRequest("protected@test.pl", "haslo1234", "Jan", "Testowy");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest("protected@test.pl", "haslo1234");
    LoginResponse loginResponse =
        restTemplate.postForObject("/api/v1/auth/login", loginRequest, LoginResponse.class);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(loginResponse.token());
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/api/v1/orders", HttpMethod.GET, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void publicEndpoint_shouldRemainAccessible_withoutToken() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void nonExistentProtectedEndpoint_shouldReturn404_withValidToken_not401() {
    RegisterRequest registerRequest =
        new RegisterRequest("errortest@test.pl", "haslo1234", "Jan", "Testowy");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest("errortest@test.pl", "haslo1234");
    LoginResponse loginResponse =
        restTemplate.postForObject("/api/v1/auth/login", loginRequest, LoginResponse.class);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(loginResponse.token());
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/api/v1/nonexistent-path", HttpMethod.GET, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
