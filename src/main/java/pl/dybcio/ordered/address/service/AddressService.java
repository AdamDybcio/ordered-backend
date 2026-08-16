package pl.dybcio.ordered.address.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.address.dto.AddressRequest;
import pl.dybcio.ordered.address.entity.Address;
import pl.dybcio.ordered.address.repository.AddressRepository;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AddressService {

  private final AddressRepository addressRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<Address> listForUser(Long userId) {
    return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
  }

  @Transactional(readOnly = true)
  public Address getForUser(Long id, Long userId) {
    return addressRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new AddressNotFoundException(id));
  }

  @Transactional
  public Address create(Long userId, AddressRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new IllegalStateException("Authenticated user not found: " + userId));

    boolean isFirstAddress = addressRepository.findByUserIdAndIsDefaultTrue(userId).isEmpty();

    Address address =
        Address.builder()
            .user(user)
            .label(request.label())
            .recipientName(request.recipientName())
            .phone(request.phone())
            .street(request.street())
            .buildingNumber(request.buildingNumber())
            .apartmentNumber(request.apartmentNumber())
            .city(request.city())
            .postalCode(request.postalCode())
            .country(request.country() != null ? request.country() : "PL")
            .isDefault(isFirstAddress)
            .build();

    return addressRepository.save(address);
  }

  @Transactional
  public Address update(Long id, Long userId, AddressRequest request) {
    Address address = getForUser(id, userId);
    address.setLabel(request.label());
    address.setRecipientName(request.recipientName());
    address.setPhone(request.phone());
    address.setStreet(request.street());
    address.setBuildingNumber(request.buildingNumber());
    address.setApartmentNumber(request.apartmentNumber());
    address.setCity(request.city());
    address.setPostalCode(request.postalCode());
    if (request.country() != null) {
      address.setCountry(request.country());
    }
    return addressRepository.save(address);
  }

  @Transactional
  public void delete(Long id, Long userId) {
    Address address = getForUser(id, userId);
    addressRepository.delete(address);

    if (address.isDefault()) {
      addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
          .findFirst()
          .ifPresent(
              next -> {
                next.setDefault(true);
                addressRepository.save(next);
              });
    }
  }

  @Transactional
  public Address setDefault(Long id, Long userId) {
    Address target = getForUser(id, userId);

    addressRepository
        .findByUserIdAndIsDefaultTrue(userId)
        .filter(current -> !current.getId().equals(id))
        .ifPresent(
            current -> {
              current.setDefault(false);
              addressRepository.save(current);
            });

    target.setDefault(true);
    return addressRepository.save(target);
  }
}
