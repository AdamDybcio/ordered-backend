package pl.dybcio.ordered.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import pl.dybcio.ordered.address.dto.AddressRequest;
import pl.dybcio.ordered.address.dto.AddressResponse;
import pl.dybcio.ordered.user.dto.LoginRequest;
import pl.dybcio.ordered.user.dto.LoginResponse;
import pl.dybcio.ordered.user.dto.RegisterRequest;
import pl.dybcio.ordered.user.dto.UserResponse;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AddressControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

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

  private AddressRequest sampleRequest(String label) {
    return new AddressRequest(
        label, "Jan Kowalski", "123456789", "Długa", "12", "3", "Toruń", "87-100", "PL");
  }

  @Test
  void createAddress_firstOne_isReturnedAsDefault() {
    String token = registerAndLogin("addr1-" + System.nanoTime() + "@test.pl");

    ResponseEntity<AddressResponse> response =
        restTemplate.exchange(
            "/api/v1/addresses",
            HttpMethod.POST,
            authEntity(token, sampleRequest("Dom")),
            AddressResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().isDefault()).isTrue();
  }

  @Test
  void createAddress_secondOne_isNotDefault() {
    String token = registerAndLogin("addr2-" + System.nanoTime() + "@test.pl");
    restTemplate.exchange(
        "/api/v1/addresses",
        HttpMethod.POST,
        authEntity(token, sampleRequest("Dom")),
        AddressResponse.class);

    ResponseEntity<AddressResponse> response =
        restTemplate.exchange(
            "/api/v1/addresses",
            HttpMethod.POST,
            authEntity(token, sampleRequest("Praca")),
            AddressResponse.class);

    assertThat(response.getBody().isDefault()).isFalse();
  }

  @Test
  void listAddresses_returnsOnlyOwnAddresses() {
    String tokenA = registerAndLogin("addr3-" + System.nanoTime() + "@test.pl");
    String tokenB = registerAndLogin("addr4-" + System.nanoTime() + "@test.pl");

    restTemplate.exchange(
        "/api/v1/addresses",
        HttpMethod.POST,
        authEntity(tokenA, sampleRequest("Dom A")),
        AddressResponse.class);
    restTemplate.exchange(
        "/api/v1/addresses",
        HttpMethod.POST,
        authEntity(tokenB, sampleRequest("Dom B")),
        AddressResponse.class);

    ResponseEntity<AddressResponse[]> response =
        restTemplate.exchange(
            "/api/v1/addresses", HttpMethod.GET, authEntity(tokenA, null), AddressResponse[].class);

    List<AddressResponse> addresses = List.of(response.getBody());
    assertThat(addresses).hasSize(1);
    assertThat(addresses.get(0).label()).isEqualTo("Dom A");
  }

  @Test
  void getAddress_otherUsersAddress_returns404() {
    String ownerToken = registerAndLogin("addr5-" + System.nanoTime() + "@test.pl");
    AddressResponse owned =
        restTemplate
            .exchange(
                "/api/v1/addresses",
                HttpMethod.POST,
                authEntity(ownerToken, sampleRequest("Dom")),
                AddressResponse.class)
            .getBody();

    String intruderToken = registerAndLogin("addr6-" + System.nanoTime() + "@test.pl");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/addresses/" + owned.id(),
            HttpMethod.GET,
            authEntity(intruderToken, null),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void updateAddress_happyPath_returns200WithUpdatedFields() {
    String token = registerAndLogin("addr7-" + System.nanoTime() + "@test.pl");
    AddressResponse created =
        restTemplate
            .exchange(
                "/api/v1/addresses",
                HttpMethod.POST,
                authEntity(token, sampleRequest("Dom")),
                AddressResponse.class)
            .getBody();

    AddressRequest update =
        new AddressRequest(
            "Nowy dom", "Anna Nowak", "987654321", "Krótka", "1", null, "Gdańsk", "80-001", "PL");

    ResponseEntity<AddressResponse> response =
        restTemplate.exchange(
            "/api/v1/addresses/" + created.id(),
            HttpMethod.PUT,
            authEntity(token, update),
            AddressResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().city()).isEqualTo("Gdańsk");
  }

  @Test
  void deleteAddress_defaultOne_promotesRemainingToDefault() {
    String token = registerAndLogin("addr8-" + System.nanoTime() + "@test.pl");
    AddressResponse first =
        restTemplate
            .exchange(
                "/api/v1/addresses",
                HttpMethod.POST,
                authEntity(token, sampleRequest("Dom")),
                AddressResponse.class)
            .getBody();
    restTemplate.exchange(
        "/api/v1/addresses",
        HttpMethod.POST,
        authEntity(token, sampleRequest("Praca")),
        AddressResponse.class);

    restTemplate.exchange(
        "/api/v1/addresses/" + first.id(), HttpMethod.DELETE, authEntity(token, null), Void.class);

    ResponseEntity<AddressResponse[]> response =
        restTemplate.exchange(
            "/api/v1/addresses", HttpMethod.GET, authEntity(token, null), AddressResponse[].class);

    List<AddressResponse> remaining = List.of(response.getBody());
    assertThat(remaining).hasSize(1);
    assertThat(remaining.get(0).isDefault()).isTrue();
  }

  @Test
  void setDefault_switchesDefaultToTargetAddress() {
    String token = registerAndLogin("addr9-" + System.nanoTime() + "@test.pl");
    restTemplate.exchange(
        "/api/v1/addresses",
        HttpMethod.POST,
        authEntity(token, sampleRequest("Dom")),
        AddressResponse.class);
    AddressResponse second =
        restTemplate
            .exchange(
                "/api/v1/addresses",
                HttpMethod.POST,
                authEntity(token, sampleRequest("Praca")),
                AddressResponse.class)
            .getBody();

    ResponseEntity<AddressResponse> response =
        restTemplate.exchange(
            "/api/v1/addresses/" + second.id() + "/default",
            HttpMethod.PATCH,
            authEntity(token, null),
            AddressResponse.class);

    assertThat(response.getBody().isDefault()).isTrue();
  }

  @Test
  void createAddress_withoutToken_returns401() {
    ResponseEntity<String> response =
        restTemplate.postForEntity("/api/v1/addresses", sampleRequest("Dom"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
