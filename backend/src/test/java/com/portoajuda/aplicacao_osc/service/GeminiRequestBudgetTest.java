package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class GeminiRequestBudgetTest {
    @Test
    void localBudgetCapsRequestsTokensAndDayBelowObservedFreeTierCeilings() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-13T12:00:00Z"));
        GeminiRequestBudget budget = new GeminiRequestBudget(1, 2, 100, 100, 100, clock);
        budget.reserve(80);
        var minuteLimit = assertThrows(AnaliseRepresentacaoDocumental.FalhaProvedor.class,
                () -> budget.reserve(1));
        assertEquals("PROVIDER_QUOTA_EXHAUSTED", minuteLimit.codigo());

        clock.advance(Duration.ofSeconds(61));
        budget.reserve(80);
        var dayLimit = assertThrows(AnaliseRepresentacaoDocumental.FalhaProvedor.class,
                () -> budget.reserve(1));
        assertEquals("PROVIDER_QUOTA_EXHAUSTED", dayLimit.codigo());
    }

    @Test
    void concurrentReservationsCannotExceedConfiguredRequestBudget() throws Exception {
        GeminiRequestBudget budget = new GeminiRequestBudget(10, 450, 200_000, 3000, 16_000,
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC));
        try (var pool = Executors.newFixedThreadPool(20)) {
            List<Callable<Boolean>> calls = java.util.stream.IntStream.range(0, 20)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        try { budget.reserve(100); return true; }
                        catch (AnaliseRepresentacaoDocumental.FalhaProvedor limited) { return false; }
                    }).toList();
            long accepted = pool.invokeAll(calls).stream().filter(future -> {
                try { return future.get(); } catch (Exception failure) { throw new RuntimeException(failure); }
            }).count();
            assertEquals(10, accepted);
        }
    }

    @Test
    void actualTokenUsageReplacesConservativeReservation() {
        GeminiRequestBudget budget = new GeminiRequestBudget(2, 10, 100, 100, 100,
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC));
        var first = budget.reserve(90);
        budget.recordActual(first, 20);
        assertDoesNotThrow(() -> budget.reserve(80));
    }

    @Test
    void configCannotExceedKnownProviderCaps() {
        Clock clock = Clock.systemUTC();
        assertThrows(IllegalArgumentException.class, () -> new GeminiRequestBudget(16, 450, 200_000, 1, 1, clock));
        assertThrows(IllegalArgumentException.class, () -> new GeminiRequestBudget(10, 501, 200_000, 1, 1, clock));
        assertThrows(IllegalArgumentException.class, () -> new GeminiRequestBudget(10, 450, 250_001, 1, 1, clock));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
