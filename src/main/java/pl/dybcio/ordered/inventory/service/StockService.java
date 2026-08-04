package pl.dybcio.ordered.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.inventory.entity.Stock;
import pl.dybcio.ordered.inventory.repository.StockRepository;

@Service
public class StockService {

  private final StockRepository stockRepository;

  public StockService(StockRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Transactional
  public void initializeStock(Long productId, Integer quantity) {
    stockRepository.save(new Stock(productId, quantity));
  }

  @Transactional(readOnly = true)
  public Integer getQuantity(Long productId) {
    return stockRepository.findById(productId).map(Stock::getQuantity).orElse(0);
  }
}
