package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String username, Role role, UUID userId, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(), account.getUsername(), account.getRole(), account.getUserId(), account.getCreatedAt());
    }
}
