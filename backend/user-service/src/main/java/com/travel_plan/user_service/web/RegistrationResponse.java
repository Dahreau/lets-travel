package com.travel_plan.user_service.web;

public record RegistrationResponse(UserResponse user, String registrationToken) {
}
