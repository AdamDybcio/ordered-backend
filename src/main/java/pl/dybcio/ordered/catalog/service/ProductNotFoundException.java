package pl.dybcio.ordered.catalog.service;

public class ProductNotFoundException extends RuntimeException {
  public ProductNotFoundException(Long id) {
    super("Nie znaleziono produktu o id: " + id);
  }
}
