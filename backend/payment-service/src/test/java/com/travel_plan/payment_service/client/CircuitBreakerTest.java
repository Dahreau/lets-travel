package com.travel_plan.payment_service.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

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
    void allowsATrialRequestOnceOpenDurationHasElapsed() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(5), Clock.systemUTC());

        breaker.recordFailure();
        assertThat(breaker.allowsRequest()).isFalse();

        Thread.sleep(20);

        assertThat(breaker.allowsRequest()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void trialFailureInHalfOpenReopensImmediately() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(5), Clock.systemUTC());

        breaker.recordFailure();
        Thread.sleep(20);
        breaker.allowsRequest();
        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    void trialSuccessInHalfOpenClosesTheCircuit() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofMillis(5), Clock.systemUTC());

        breaker.recordFailure();
        Thread.sleep(20);
        breaker.allowsRequest();
        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsRequest()).isTrue();
    }
}
