package pl.dybcio.ordered.address.service;

public class AddressNotFoundException extends RuntimeException {
  public AddressNotFoundException(Long id) {
    super("Address not found: " + id);
  }
}
