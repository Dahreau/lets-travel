package com.travel_plan.payment_service.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    // Horloge manuelle : avance sur commande plutot que d'attendre un vrai Thread.sleep()
    // (flaky et lent) pour tester le passage OPEN -> HALF_OPEN une fois openDuration ecoulee.
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    @Test
    void staysClosedAndAllowsRequestsBelowFailureThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30), Clock.systemUTC());

        breaker.recordFailure();
        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsRequest()).isTrue();
    }

    @Test
    void opensAfterReachingFailureThresholdAndBlocksRequests() {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30), Clock.systemUTC());

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    void successResetsFailureCountAndClosesTheCircuit() {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30), Clock.systemUTC());

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void allowsATrialRequestOnceOpenDurationHasElapsed() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(5), clock);

        breaker.recordFailure();
        assertThat(breaker.allowsRequest()).isFalse();

        clock.advance(Duration.ofMillis(10));

        assertThat(breaker.allowsRequest()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void trialFailureInHalfOpenReopensImmediately() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(5), clock);

        breaker.recordFailure();
        clock.advance(Duration.ofMillis(10));
        breaker.allowsRequest();
        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    void trialSuccessInHalfOpenClosesTheCircuit() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(5), clock);

        breaker.recordFailure();
        clock.advance(Duration.ofMillis(10));
        breaker.allowsRequest();
        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsRequest()).isTrue();
    }
}
