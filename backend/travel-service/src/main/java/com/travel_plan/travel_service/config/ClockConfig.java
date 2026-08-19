package com.travel_plan.travel_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Bean unique injectable partout ou une notion de "maintenant" est necessaire (ex: SubscriptionService
// pour le cutoff d'annulation a 3 jours). Deux interets : (1) satisfait la regle Sonar qui demande un
// ZoneId/Clock explicite plutot qu'un appel nu a .now() - un Instant/LocalDate.now() sans zone explicite
// depend implicitement du fuseau du serveur, ce qui peut varier selon l'environnement de deploiement ;
// (2) rend le temps injectable/remplaçable par un Clock fixe dans les tests, sans dependre de l'horloge
// systeme au moment ou les tests tournent. UTC choisi explicitement pour que "aujourd'hui" soit calcule
// de façon coherente cote serveur, independamment du fuseau de la machine hote.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
