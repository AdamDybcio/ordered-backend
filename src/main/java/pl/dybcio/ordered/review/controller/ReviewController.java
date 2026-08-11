package pl.dybcio.ordered.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.review.dto.ReviewRequest;
import pl.dybcio.ordered.review.dto.ReviewResponse;
import pl.dybcio.ordered.review.entity.Review;
import pl.dybcio.ordered.review.service.ReviewService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product reviews (MongoDB)")
public class ReviewController {

  private final ReviewService reviewService;
  private final UserRepository userRepository;

  @Operation(summary = "Add a review for a purchased product")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(
        responseCode = "403",
        description = "User has not purchased this product",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "User already reviewed this product",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping
  public ResponseEntity<ReviewResponse> addReview(
      @Valid @RequestBody ReviewRequest request, Authentication authentication) {
    User user = currentUser(authentication);
    Review review = reviewService.addReview(user.getId(), user.getEmail(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(review));
  }

  @Operation(summary = "List reviews for a product (public, paginated)")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = ReviewResponse.class)))
  @SecurityRequirements
  @GetMapping("/product/{productId}")
  public Page<ReviewResponse> getReviewsForProduct(
      @PathVariable Long productId, @ParameterObject Pageable pageable) {
    return reviewService.getReviewsForProduct(productId, pageable).map(ReviewResponse::from);
  }

  private User currentUser(Authentication authentication) {
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
  }
}
