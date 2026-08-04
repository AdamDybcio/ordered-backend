package pl.dybcio.ordered.pricing.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.pricing.entity.PriceHistory;
import pl.dybcio.ordered.pricing.repository.PriceHistoryRepository;

@Service
public class PricingService {

  private final PriceHistoryRepository priceHistoryRepository;

  public PricingService(PriceHistoryRepository priceHistoryRepository) {
    this.priceHistoryRepository = priceHistoryRepository;
  }

  @Transactional
  public void setPrice(Long productId, BigDecimal price) {
    priceHistoryRepository.save(new PriceHistory(productId, price));
  }

  @Transactional(readOnly = true)
  public BigDecimal getCurrentPrice(Long productId) {
    return priceHistoryRepository
        .findFirstByProductIdOrderByEffectiveFromDesc(productId)
        .map(PriceHistory::getPrice)
        .orElse(BigDecimal.ZERO);
  }
}
