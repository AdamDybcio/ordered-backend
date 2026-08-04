package pl.dybcio.ordered.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.inventory.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {}
