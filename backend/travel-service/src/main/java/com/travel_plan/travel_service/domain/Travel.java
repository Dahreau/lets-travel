package com.travel_plan.travel_service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "travels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Travel {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String title;

    // Anciennement "ownerId" : un voyage est desormais une offre creee et geree
    // par un Travel Manager, pas la propriete d'un seul traveler (qui s'y abonne
    // via Subscription, entite a part - voir docs/nouveautes-vs-travel-plan.md).
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelStatus status;

    // Volontairement nullable : les voyages crees avant l'introduction du prix n'en ont pas
    // retroactivement (voir migration V4). payment-service refuse de facturer un voyage tant
    // que price/currency ne sont pas renseignes (TravelPriceNotSetException). TravelRequest,
    // lui, impose les deux pour toute creation/modification a partir de maintenant.
    private BigDecimal price;

    private String currency;

    @Builder.Default
    @OneToMany(mappedBy = "travel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<Destination> destinations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "travel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Transportation> transportations = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public long getDurationDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
