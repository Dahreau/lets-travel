package com.travel_plan.user_service.web;

import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Role role,
        AddressResponse address,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        AddressResponse addressResponse = user.getAddress() == null ? null : AddressResponse.from(user.getAddress());
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                addressResponse,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
