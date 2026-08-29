package com.travel_plan.payment_service.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// Coupe les appels vers un service en panne prolongee au lieu de le marteler de requetes
// vouees a echouer : s'ouvre apres failureThreshold echecs consecutifs, retente un seul
// appel (HALF_OPEN) une fois openDuration ecoulee.
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile Instant openedAt = Instant.MIN;

    public CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public boolean allowsRequest() {
        if (state.get() != State.OPEN) {
            return true;
        }
        if (Instant.now(clock).isAfter(openedAt.plus(openDuration))) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
            return true;
        }
        return false;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    // Un echec pendant l'essai HALF_OPEN rouvre immediatement, sans repasser par le seuil.
    public void recordFailure() {
        if (state.get() == State.HALF_OPEN || consecutiveFailures.incrementAndGet() >= failureThreshold) {
            state.set(State.OPEN);
            openedAt = Instant.now(clock);
        }
    }

    public State state() {
        return state.get();
    }
}
