package pl.dybcio.ordered.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.dybcio.ordered.user.entity.Role;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private UserDetailsServiceImpl userDetailsService;

  @Test
  void loadUserByUsername_shouldReturnUserDetails_whenUserExists() {
    User user =
        User.builder()
            .id(1L)
            .email("test@test.pl")
            .password("hashed")
            .roles(Set.of(Role.USER))
            .enabled(true)
            .build();
    when(userRepository.findByEmail("test@test.pl")).thenReturn(Optional.of(user));

    UserDetailsImpl result = userDetailsService.loadUserByUsername("test@test.pl");

    assertThat(result.getUsername()).isEqualTo("test@test.pl");
    assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  void loadUserByUsername_shouldThrow_whenUserNotFound() {
    when(userRepository.findByEmail("nope@test.pl")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nope@test.pl"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
