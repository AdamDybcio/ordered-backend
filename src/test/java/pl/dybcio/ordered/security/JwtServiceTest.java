package pl.dybcio.ordered.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

  private JwtService jwtService;

  private static final String SECRET =
      "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha";
  private static final long EXPIRATION_MS = 3_600_000L;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(SECRET, EXPIRATION_MS);
  }

  @Test
  void generateToken_shouldContainCorrectUsername() {
    UserDetails userDetails =
        User.withUsername("test@example.com")
            .password("irrelevant")
            .authorities("ROLE_USER")
            .build();

    String token = jwtService.generateToken(userDetails);

    assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
  }

  @Test
  void isTokenValid_shouldReturnTrue_forMatchingUserAndUnexpiredToken() {
    UserDetails userDetails =
        User.withUsername("test@example.com")
            .password("irrelevant")
            .authorities("ROLE_USER")
            .build();
    String token = jwtService.generateToken(userDetails);

    assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
  }

  @Test
  void isTokenValid_shouldReturnFalse_forDifferentUser() {
    UserDetails original =
        User.withUsername("test@example.com")
            .password("irrelevant")
            .authorities("ROLE_USER")
            .build();
    UserDetails different =
        User.withUsername("other@example.com")
            .password("irrelevant")
            .authorities("ROLE_USER")
            .build();
    String token = jwtService.generateToken(original);

    assertThat(jwtService.isTokenValid(token, different)).isFalse();
  }

  @Test
  void isTokenValid_shouldReturnFalse_forExpiredToken() throws InterruptedException {
    JwtService shortLivedJwtService = new JwtService(SECRET, 1L);
    UserDetails userDetails =
        User.withUsername("test@example.com")
            .password("irrelevant")
            .authorities("ROLE_USER")
            .build();
    String token = shortLivedJwtService.generateToken(userDetails);

    Thread.sleep(50);

    assertThat(shortLivedJwtService.isTokenValid(token, userDetails)).isFalse();
  }
}
