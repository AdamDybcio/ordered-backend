package pl.dybcio.ordered.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.dybcio.ordered.security.JwtService;
import pl.dybcio.ordered.security.UserDetailsImpl;
import pl.dybcio.ordered.security.UserDetailsServiceImpl;
import pl.dybcio.ordered.user.dto.LoginRequest;
import pl.dybcio.ordered.user.dto.LoginResponse;
import pl.dybcio.ordered.user.dto.RegisterRequest;
import pl.dybcio.ordered.user.dto.UserResponse;
import pl.dybcio.ordered.user.entity.Role;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtService jwtService;
  @Mock private UserDetailsServiceImpl userDetailsService;

  @InjectMocks private UserService userService;

  @Test
  void register_shouldSaveUserWithHashedPassword_whenEmailNotTaken() {
    RegisterRequest request = new RegisterRequest("new@test.pl", "plaintext123", "Jan", "Kowalski");

    when(userRepository.existsByEmail("new@test.pl")).thenReturn(false);
    when(passwordEncoder.encode("plaintext123")).thenReturn("hashed-password");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              u.setId(1L);
              return u;
            });

    UserResponse response = userService.register(request);

    assertThat(response.email()).isEqualTo("new@test.pl");
    assertThat(response.id()).isEqualTo(1L);

    verify(userRepository)
        .save(
            argThat(
                user ->
                    user.getPassword().equals("hashed-password")
                        && user.getRoles().equals(Set.of(Role.USER))));
  }

  @Test
  void register_shouldThrow_whenEmailAlreadyTaken() {
    RegisterRequest request = new RegisterRequest("taken@test.pl", "pass1234", "Jan", "Kowalski");
    when(userRepository.existsByEmail("taken@test.pl")).thenReturn(true);

    assertThatThrownBy(() -> userService.register(request))
        .isInstanceOf(EmailAlreadyTakenException.class)
        .hasMessageContaining("taken@test.pl");

    verify(userRepository, never()).save(any());
  }

  @Test
  void login_shouldReturnToken_whenCredentialsValid() {
    LoginRequest request = new LoginRequest("user@test.pl", "correctPassword");
    User user =
        User.builder()
            .id(1L)
            .email("user@test.pl")
            .password("hashed")
            .roles(Set.of(Role.USER))
            .build();
    UserDetailsImpl userDetails = new UserDetailsImpl(user);

    when(userDetailsService.loadUserByUsername("user@test.pl")).thenReturn(userDetails);
    when(jwtService.generateToken(userDetails)).thenReturn("fake-jwt-token");

    LoginResponse response = userService.login(request);

    assertThat(response.token()).isEqualTo("fake-jwt-token");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    verify(authenticationManager).authenticate(any());
  }

  @Test
  void login_shouldPropagateException_whenCredentialsInvalid() {
    LoginRequest request = new LoginRequest("user@test.pl", "wrongPassword");
    doThrow(new BadCredentialsException("Bad credentials"))
        .when(authenticationManager)
        .authenticate(any());

    assertThatThrownBy(() -> userService.login(request))
        .isInstanceOf(BadCredentialsException.class);

    verifyNoInteractions(jwtService);
  }
}
