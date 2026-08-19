package com.travel_plan.user_service.web;

import com.travel_plan.user_service.domain.Address;
import java.util.UUID;

public record AddressResponse(UUID id, String street, String city, String postalCode, String country) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(), address.getStreet(), address.getCity(), address.getPostalCode(),
                address.getCountry());
    }
}
