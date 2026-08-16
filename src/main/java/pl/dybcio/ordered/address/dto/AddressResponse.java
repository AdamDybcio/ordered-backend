package pl.dybcio.ordered.address.dto;

import pl.dybcio.ordered.address.entity.Address;

public record AddressResponse(
    Long id,
    String label,
    String recipientName,
    String phone,
    String street,
    String buildingNumber,
    String apartmentNumber,
    String city,
    String postalCode,
    String country,
    boolean isDefault) {

  public static AddressResponse from(Address a) {
    return new AddressResponse(
        a.getId(),
        a.getLabel(),
        a.getRecipientName(),
        a.getPhone(),
        a.getStreet(),
        a.getBuildingNumber(),
        a.getApartmentNumber(),
        a.getCity(),
        a.getPostalCode(),
        a.getCountry(),
        a.isDefault());
  }
}
