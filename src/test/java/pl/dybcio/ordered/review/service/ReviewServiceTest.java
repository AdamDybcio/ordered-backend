package pl.dybcio.ordered.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pl.dybcio.ordered.order.repository.OrderItemRepository;
import pl.dybcio.ordered.review.dto.ReviewRequest;
import pl.dybcio.ordered.review.entity.Review;
import pl.dybcio.ordered.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock private ReviewRepository reviewRepository;
  @Mock private OrderItemRepository orderItemRepository;

  @InjectMocks private ReviewService reviewService;

  private static final Long USER_ID = 1L;
  private static final String USER_EMAIL = "buyer@test.pl";
  private static final Long PRODUCT_ID = 10L;

  private ReviewRequest request;

  @BeforeEach
  void setUp() {
    request = new ReviewRequest(PRODUCT_ID, 5, "Świetny produkt!");
  }

  @Test
  void addReview_happyPath_savesReview() {
    when(orderItemRepository.existsPurchaseByBuyerAndProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
    when(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(false);
    when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

    Review result = reviewService.addReview(USER_ID, USER_EMAIL, request);

    assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
    assertThat(result.getUserId()).isEqualTo(USER_ID);
    assertThat(result.getUserEmail()).isEqualTo(USER_EMAIL);
    assertThat(result.getRating()).isEqualTo(5);
    assertThat(result.getComment()).isEqualTo("Świetny produkt!");
    assertThat(result.getCreatedAt()).isNotNull();
    verify(reviewRepository).save(any(Review.class));
  }

  @Test
  void addReview_userDidNotPurchase_throwsProductNotPurchasedException() {
    when(orderItemRepository.existsPurchaseByBuyerAndProduct(USER_ID, PRODUCT_ID))
        .thenReturn(false);

    assertThatThrownBy(() -> reviewService.addReview(USER_ID, USER_EMAIL, request))
        .isInstanceOf(ProductNotPurchasedException.class)
        .hasMessageContaining(PRODUCT_ID.toString());

    verify(reviewRepository, never()).save(any(Review.class));
  }

  @Test
  void addReview_alreadyReviewed_throwsDuplicateReviewException() {
    when(orderItemRepository.existsPurchaseByBuyerAndProduct(USER_ID, PRODUCT_ID)).thenReturn(true);
    when(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);

    assertThatThrownBy(() -> reviewService.addReview(USER_ID, USER_EMAIL, request))
        .isInstanceOf(DuplicateReviewException.class)
        .hasMessageContaining(PRODUCT_ID.toString());

    verify(reviewRepository, never()).save(any(Review.class));
  }

  @Test
  void getReviewsForProduct_returnsPagedResults() {
    Review review =
        Review.builder()
            .id("abc123")
            .productId(PRODUCT_ID)
            .userId(USER_ID)
            .userEmail(USER_EMAIL)
            .rating(4)
            .comment("Dobry produkt")
            .createdAt(Instant.now())
            .build();
    Page<Review> page = new PageImpl<>(java.util.List.of(review));

    when(reviewRepository.findByProductId(PRODUCT_ID, PageRequest.of(0, 10))).thenReturn(page);

    Page<Review> result = reviewService.getReviewsForProduct(PRODUCT_ID, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getRating()).isEqualTo(4);
  }
}
