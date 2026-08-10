package pl.dybcio.ordered.messaging;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.messaging.entity.ProcessedEvent;
import pl.dybcio.ordered.messaging.repository.ProcessedEventRepository;
import pl.dybcio.ordered.order.event.OrderPlacedPayload;
import pl.dybcio.ordered.outbox.KafkaTopics;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedListener {

  private final ProcessedEventRepository processedEventRepository;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = KafkaTopics.ORDER_PLACED, groupId = "${spring.kafka.consumer.group-id}")
  @Transactional
  public void onOrderPlaced(ConsumerRecord<String, String> record) {
    String eventId = record.key();

    if (processedEventRepository.existsById(eventId)) {
      log.info("Event {} already processed, skipping (idempotency check)", eventId);
      return;
    }

    OrderPlacedPayload payload = objectMapper.readValue(record.value(), OrderPlacedPayload.class);
    log.info(
        "Processing OrderPlaced: orderId={}, buyerId={}, totalAmount={}",
        payload.orderId(),
        payload.buyerId(),
        payload.totalAmount());

    processedEventRepository.save(
        ProcessedEvent.builder()
            .id(eventId)
            .eventType("OrderPlaced")
            .processedAt(Instant.now())
            .build());
  }
}
