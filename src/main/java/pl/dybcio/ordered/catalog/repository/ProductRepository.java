package pl.dybcio.ordered.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.catalog.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {}
