package pl.dybcio.ordered.user.dto;

public record LoginResponse(String token, String tokenType) {
  public LoginResponse(String token) {
    this(token, "Bearer");
  }
}
