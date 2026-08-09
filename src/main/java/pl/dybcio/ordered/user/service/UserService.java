package pl.dybcio.ordered.user.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final UserDetailsServiceImpl userDetailsService;

  @Transactional
  public UserResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyTakenException(request.email());
    }

    User user =
        User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .firstName(request.firstName())
            .lastName(request.lastName())
            .roles(Set.of(Role.USER))
            .build();

    User saved = userRepository.save(user);

    return new UserResponse(
        saved.getId(), saved.getEmail(), saved.getFirstName(), saved.getLastName());
  }

  public LoginResponse login(LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password()));

    UserDetailsImpl userDetails =
        (UserDetailsImpl) userDetailsService.loadUserByUsername(request.email());
    String token = jwtService.generateToken(userDetails);

    return new LoginResponse(token);
  }

  @Transactional(readOnly = true)
  public UserResponse getByEmail(String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
  }
}
