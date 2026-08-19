package com.travel_plan.auth_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

// Anciennement "Admin" : generalise pour porter les identifiants de connexion
// des 3 roles (TRAVELER, TRAVEL_MANAGER, ADMIN), pas seulement les admins.
// Le profil metier (nom, email, adresse...) reste dans user-service ; userId
// fait juste le lien entre les deux, comme Payment.ownerId le fait ailleurs.
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Null pour le compte ADMIN par defaut (AdminSeeder) : il n'a pas de fiche
    // User associee dans user-service. Obligatoire pour TRAVELER/TRAVEL_MANAGER.
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
