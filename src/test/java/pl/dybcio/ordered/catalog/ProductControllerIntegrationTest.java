package pl.dybcio.ordered.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.catalog.dto.CreateProductRequest;
import pl.dybcio.ordered.catalog.dto.ProductResponse;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProductControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldCreateAndRetrieveProduct() {
    CreateProductRequest request =
        new CreateProductRequest("Testowy produkt", "Opis testowy", new BigDecimal("99.99"), 10);

    ResponseEntity<ProductResponse> createResponse =
        restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ProductResponse createdProduct = createResponse.getBody();
    assertThat(createdProduct).isNotNull();
    assertThat(createdProduct.id()).isNotNull();
    assertThat(createdProduct.name()).isEqualTo("Testowy produkt");

    Long createdId = createdProduct.id();

    ResponseEntity<ProductResponse> getResponse =
        restTemplate.getForEntity("/api/v1/products/" + createdId, ProductResponse.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().price()).isEqualByComparingTo(new BigDecimal("99.99"));
  }
}
