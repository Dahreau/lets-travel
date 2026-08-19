package com.travel_plan.travel_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

// Abonnement d'un Traveler a un Travel. Vit dans travel-service (meme base que Travel,
// donc vraie relation JPA) - contrairement a Travel.managerId/travelerId qui referencent
// user-service par UUID simple (pattern cross-service deja utilise ailleurs), travelerId
// ici reste un UUID nu car il pointe, lui, vers user-service (pas de FK possible entre bases).
// Voir docs/nouveautes-vs-travel-plan.md.
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_id", nullable = false)
    private Travel travel;

    @Column(name = "traveler_id", nullable = false)
    private UUID travelerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
