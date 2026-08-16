package pl.dybcio.ordered.address.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.dybcio.ordered.address.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {

  List<Address> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

  Optional<Address> findByIdAndUserId(Long id, Long userId);

  Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
}
