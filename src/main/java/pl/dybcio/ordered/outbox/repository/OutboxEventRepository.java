package pl.dybcio.ordered.outbox.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {
  List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
