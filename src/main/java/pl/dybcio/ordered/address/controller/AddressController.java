package pl.dybcio.ordered.address.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.address.dto.AddressRequest;
import pl.dybcio.ordered.address.dto.AddressResponse;
import pl.dybcio.ordered.address.entity.Address;
import pl.dybcio.ordered.address.service.AddressService;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "User's saved delivery addresses")
public class AddressController {

  private final AddressService addressService;
  private final UserRepository userRepository;

  @Operation(summary = "List the logged-in user's saved addresses")
  @GetMapping
  public List<AddressResponse> list(Authentication authentication) {
    return addressService.listForUser(currentUserId(authentication)).stream()
        .map(AddressResponse::from)
        .toList();
  }

  @Operation(summary = "Get a single address (owner only)")
  @GetMapping("/{id}")
  public AddressResponse get(@PathVariable Long id, Authentication authentication) {
    return AddressResponse.from(addressService.getForUser(id, currentUserId(authentication)));
  }

  @Operation(summary = "Add a new address")
  @PostMapping
  public ResponseEntity<AddressResponse> create(
      @Valid @RequestBody AddressRequest request, Authentication authentication) {
    Address address = addressService.create(currentUserId(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(AddressResponse.from(address));
  }

  @Operation(summary = "Update an existing address (owner only)")
  @PutMapping("/{id}")
  public AddressResponse update(
      @PathVariable Long id,
      @Valid @RequestBody AddressRequest request,
      Authentication authentication) {
    return AddressResponse.from(addressService.update(id, currentUserId(authentication), request));
  }

  @Operation(summary = "Delete an address (owner only)")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
    addressService.delete(id, currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Mark an address as the default one")
  @PatchMapping("/{id}/default")
  public AddressResponse setDefault(@PathVariable Long id, Authentication authentication) {
    return AddressResponse.from(addressService.setDefault(id, currentUserId(authentication)));
  }

  private Long currentUserId(Authentication authentication) {
    String email = authentication.getName();
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    return user.getId();
  }
}
