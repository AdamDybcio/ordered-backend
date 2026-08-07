package pl.dybcio.ordered.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.user.dto.RegisterRequest;
import pl.dybcio.ordered.user.dto.UserResponse;
import pl.dybcio.ordered.user.service.UserService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    UserResponse response = userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
