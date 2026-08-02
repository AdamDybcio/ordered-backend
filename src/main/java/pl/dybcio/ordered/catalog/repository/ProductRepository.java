package pl.dybcio.ordered.catalog.repository;

import pl.dybcio.ordered.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
