package com.travel_plan.user_service.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.user_service.domain.Role;
import org.junit.jupiter.api.Test;

class UserRequestTest {

    @Test
    void credentialsConsistentWhenBothMissing() {
        UserRequest request = userRequest(null, null);

        assertThat(request.isCredentialsConsistent()).isTrue();
    }

    @Test
    void credentialsConsistentWhenBothProvided() {
        UserRequest request = userRequest("traveler1", "secret");

        assertThat(request.isCredentialsConsistent()).isTrue();
    }

    @Test
    void credentialsInconsistentWhenOnlyUsernameProvided() {
        UserRequest request = userRequest("traveler1", "");

        assertThat(request.isCredentialsConsistent()).isFalse();
    }

    @Test
    void credentialsInconsistentWhenOnlyPasswordProvided() {
        UserRequest request = userRequest(" ", "secret");

        assertThat(request.isCredentialsConsistent()).isFalse();
    }

    private UserRequest userRequest(String username, String password) {
        return new UserRequest("Alice", "Doe", "alice@example.com", null, Role.TRAVELER, null, username, password);
    }
}
