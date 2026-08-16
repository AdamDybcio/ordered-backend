package pl.dybcio.ordered.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.address.dto.AddressRequest;
import pl.dybcio.ordered.address.entity.Address;
import pl.dybcio.ordered.address.repository.AddressRepository;
import pl.dybcio.ordered.user.entity.User;
import pl.dybcio.ordered.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

  @Mock private AddressRepository addressRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private AddressService addressService;

  private User user;
  private AddressRequest request;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(1L);

    request =
        new AddressRequest(
            "Dom", "Jan Kowalski", "123456789", "Długa", "12", "3", "Toruń", "87-100", "PL");
  }

  @Test
  void create_firstAddressForUser_isSetAsDefault() {
    when(addressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.empty());
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

    Address result = addressService.create(1L, request);

    assertThat(result.isDefault()).isTrue();
  }

  @Test
  void create_secondAddressForUser_isNotDefault() {
    Address existingDefault = Address.builder().id(1L).user(user).isDefault(true).build();

    when(addressRepository.findByUserIdAndIsDefaultTrue(1L))
        .thenReturn(Optional.of(existingDefault));
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

    Address result = addressService.create(1L, request);

    assertThat(result.isDefault()).isFalse();
  }

  @Test
  void getForUser_notOwner_throwsAddressNotFoundException() {
    when(addressRepository.findByIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> addressService.getForUser(1L, 999L))
        .isInstanceOf(AddressNotFoundException.class);
  }

  @Test
  void update_happyPath_overwritesFields() {
    Address existing =
        Address.builder().id(1L).user(user).label("Stary").city("Warszawa").isDefault(true).build();

    when(addressRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
    when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

    Address result = addressService.update(1L, 1L, request);

    assertThat(result.getLabel()).isEqualTo("Dom");
    assertThat(result.getCity()).isEqualTo("Toruń");
  }

  @Test
  void delete_defaultAddress_promotesNextOneToDefault() {
    Address toDelete = Address.builder().id(1L).user(user).isDefault(true).build();
    Address other = Address.builder().id(2L).user(user).isDefault(false).build();

    when(addressRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(toDelete));
    when(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(1L))
        .thenReturn(List.of(other));

    addressService.delete(1L, 1L);

    verify(addressRepository).delete(toDelete);
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    verify(addressRepository).save(captor.capture());
    assertThat(captor.getValue()).isEqualTo(other);
    assertThat(other.isDefault()).isTrue();
  }

  @Test
  void delete_nonDefaultAddress_doesNotTouchOtherAddresses() {
    Address toDelete = Address.builder().id(1L).user(user).isDefault(false).build();

    when(addressRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(toDelete));

    addressService.delete(1L, 1L);

    verify(addressRepository).delete(toDelete);
    verify(addressRepository, never()).findByUserIdOrderByIsDefaultDescCreatedAtDesc(any());
    verify(addressRepository, never()).save(any(Address.class));
  }

  @Test
  void setDefault_switchesDefaultFromPreviousToTarget() {
    Address current = Address.builder().id(1L).user(user).isDefault(true).build();
    Address target = Address.builder().id(2L).user(user).isDefault(false).build();

    when(addressRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(target));
    when(addressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(current));
    when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

    Address result = addressService.setDefault(2L, 1L);

    assertThat(current.isDefault()).isFalse();
    assertThat(result.isDefault()).isTrue();
    verify(addressRepository, times(2)).save(any(Address.class));
  }

  @Test
  void setDefault_addressNotFound_throws() {
    when(addressRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> addressService.setDefault(1L, 1L))
        .isInstanceOf(AddressNotFoundException.class);
  }
}
