package pl.dybcio.ordered.user;

import static org.assertj.core.api.Assertions.assertThat;

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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  private String registerAndLogin(String email) {
    RegisterRequest registerRequest = new RegisterRequest(email, "haslo1234", "Anna", "Kowalska");
    restTemplate.postForEntity("/api/v1/auth/register", registerRequest, UserResponse.class);

    LoginRequest loginRequest = new LoginRequest(email, "haslo1234");
    LoginResponse loginResponse =
        restTemplate.postForObject("/api/v1/auth/login", loginRequest, LoginResponse.class);
    return loginResponse.token();
  }

  @Test
  void getCurrentUser_authenticated_returnsOwnProfile() {
    String email = "me-" + System.nanoTime() + "@test.pl";
    String token = registerAndLogin(email);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<UserResponse> response =
        restTemplate.exchange("/api/v1/users/me", HttpMethod.GET, entity, UserResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().email()).isEqualTo(email);
    assertThat(response.getBody().firstName()).isEqualTo("Anna");
  }

  @Test
  void getCurrentUser_noToken_returns401() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/me", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
