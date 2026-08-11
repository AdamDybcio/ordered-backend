package pl.dybcio.ordered.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pl.dybcio.ordered.history.entity.BrowsingHistoryEntry;
import pl.dybcio.ordered.history.repository.BrowsingHistoryRepository;

@ExtendWith(MockitoExtension.class)
class BrowsingHistoryServiceTest {

  @Mock private BrowsingHistoryRepository browsingHistoryRepository;

  @InjectMocks private BrowsingHistoryService browsingHistoryService;

  private static final Long USER_ID = 1L;
  private static final Long PRODUCT_ID = 10L;

  @Test
  void recordView_savesEntryWithUserAndProductAndCurrentTimestamp() {
    Instant before = Instant.now();

    browsingHistoryService.recordView(USER_ID, PRODUCT_ID);

    ArgumentCaptor<BrowsingHistoryEntry> captor =
        ArgumentCaptor.forClass(BrowsingHistoryEntry.class);
    verify(browsingHistoryRepository).save(captor.capture());

    BrowsingHistoryEntry saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
    assertThat(saved.getViewedAt()).isNotNull();
    assertThat(saved.getViewedAt()).isAfterOrEqualTo(before.minusSeconds(1));
    assertThat(saved.getViewedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void getHistoryForUser_delegatesToRepositoryWithCorrectSortOrder() {
    BrowsingHistoryEntry entry =
        BrowsingHistoryEntry.builder()
            .id("abc123")
            .userId(USER_ID)
            .productId(PRODUCT_ID)
            .viewedAt(Instant.now())
            .build();
    Pageable pageable = PageRequest.of(0, 10);

    when(browsingHistoryRepository.findByUserIdOrderByViewedAtDesc(USER_ID, pageable))
        .thenReturn(List.of(entry));

    List<BrowsingHistoryEntry> result = browsingHistoryService.getHistoryForUser(USER_ID, pageable);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getProductId()).isEqualTo(PRODUCT_ID);
    verify(browsingHistoryRepository).findByUserIdOrderByViewedAtDesc(eq(USER_ID), eq(pageable));
  }

  @Test
  void getHistoryForUser_noHistory_returnsEmptyList() {
    Pageable pageable = PageRequest.of(0, 10);
    when(browsingHistoryRepository.findByUserIdOrderByViewedAtDesc(USER_ID, pageable))
        .thenReturn(List.of());

    List<BrowsingHistoryEntry> result = browsingHistoryService.getHistoryForUser(USER_ID, pageable);

    assertThat(result).isEmpty();
  }
}
