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

  @Transactional
  public void setQuantity(Long productId, Integer quantity) {
    Stock stock =
        stockRepository
            .findById(productId)
            .orElseThrow(
                () -> new IllegalStateException("Missing stock record for product " + productId));
    stock.setQuantity(quantity);
    stockRepository.save(stock);
  }
}
