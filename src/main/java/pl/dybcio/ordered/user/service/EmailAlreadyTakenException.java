package pl.dybcio.ordered.user.service;

public class EmailAlreadyTakenException extends RuntimeException {
  public EmailAlreadyTakenException(String email) {
    super("Email already in use: " + email);
  }
}
