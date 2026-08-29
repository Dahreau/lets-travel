package com.travel_plan.user_service.web;

import com.travel_plan.user_service.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// username/password optionnels : permettent de provisionner un compte de connexion
// avec le profil (voir UserService.create()) - jamais utilises si role=ADMIN.
public record UserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        String phone,
        @NotNull Role role,
        @Valid AddressRequest address,
        String username,
        String password) {

    @AssertTrue(message = "password est obligatoire si un nom d'utilisateur est fourni, et inversement")
    public boolean isCredentialsConsistent() {
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        return hasUsername == hasPassword;
    }
}
