package pl.dybcio.ordered.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @NotBlank @Size(max = 50) String label,
    @NotBlank String recipientName,
    @Size(max = 30) String phone,
    @NotBlank String street,
    @NotBlank @Size(max = 20) String buildingNumber,
    @Size(max = 20) String apartmentNumber,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 20) String postalCode,
    @Size(min = 2, max = 2) String country) {}
