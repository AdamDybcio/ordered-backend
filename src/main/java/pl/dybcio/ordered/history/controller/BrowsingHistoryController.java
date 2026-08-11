package pl.dybcio.ordered.history.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.dybcio.ordered.history.dto.BrowsingHistoryResponse;
import pl.dybcio.ordered.history.service.BrowsingHistoryService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/browsing-history")
@RequiredArgsConstructor
@Tag(name = "Browsing History", description = "User's product view history (MongoDB, TTL 90 days)")
public class BrowsingHistoryController {

  private final BrowsingHistoryService browsingHistoryService;
  private final UserRepository userRepository;

  @Operation(summary = "Get the logged-in user's recent browsing history")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = BrowsingHistoryResponse.class)))
  @GetMapping
  public List<BrowsingHistoryResponse> getMyHistory(
      Authentication authentication, @ParameterObject Pageable pageable) {
    User user = currentUser(authentication);
    return browsingHistoryService.getHistoryForUser(user.getId(), pageable).stream()
        .map(BrowsingHistoryResponse::from)
        .toList();
  }

  private User currentUser(Authentication authentication) {
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
  }
}
