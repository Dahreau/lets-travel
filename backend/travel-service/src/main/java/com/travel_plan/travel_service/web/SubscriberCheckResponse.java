package com.travel_plan.travel_service.web;

// fix/audit-gaps : reponse du endpoint interne consomme par user-service (troubleshooting.md #38).
public record SubscriberCheckResponse(boolean subscriber) {
}
