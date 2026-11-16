package com.portoajuda.aplicacao_osc.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Conservative process-local guard below the observed free-tier Gemini limits. */
@Component
public class GeminiRequestBudget {
    private static final int PROVIDER_MAX_RPM = 15;
    private static final int PROVIDER_MAX_RPD = 500;
    private static final long PROVIDER_MAX_TPM = 250_000;

    private final int requestsPerMinute;
    private final int requestsPerDay;
    private final long inputTokensPerMinute;
    private final int tokensPerPdfPageEstimate;
    private final int tokensPerImageEstimate;
    private final Clock clock;
    private final Deque<Reservation> reservations = new ArrayDeque<>();

    @Autowired
    public GeminiRequestBudget(
            @Value("${app.ngo-verification.ai-analysis.gemini.requests-per-minute:10}") int requestsPerMinute,
            @Value("${app.ngo-verification.ai-analysis.gemini.requests-per-day:450}") int requestsPerDay,
            @Value("${app.ngo-verification.ai-analysis.gemini.input-tokens-per-minute:200000}") long inputTokensPerMinute,
            @Value("${app.ngo-verification.ai-analysis.gemini.estimated-tokens-per-pdf-page:3000}") int tokensPerPdfPageEstimate,
            @Value("${app.ngo-verification.ai-analysis.gemini.estimated-tokens-per-image:16000}") int tokensPerImageEstimate) {
        this(requestsPerMinute, requestsPerDay, inputTokensPerMinute,
                tokensPerPdfPageEstimate, tokensPerImageEstimate, Clock.systemUTC());
    }

    GeminiRequestBudget(int requestsPerMinute, int requestsPerDay, long inputTokensPerMinute,
                        int tokensPerPdfPageEstimate, int tokensPerImageEstimate, Clock clock) {
        if (requestsPerMinute < 1 || requestsPerMinute > PROVIDER_MAX_RPM
                || requestsPerDay < 1 || requestsPerDay > PROVIDER_MAX_RPD
                || inputTokensPerMinute < 1 || inputTokensPerMinute > PROVIDER_MAX_TPM
                || tokensPerPdfPageEstimate < 1 || tokensPerImageEstimate < 1 || clock == null) {
            throw new IllegalArgumentException("Configuração de limites Gemini inválida.");
        }
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerDay = requestsPerDay;
        this.inputTokensPerMinute = inputTokensPerMinute;
        this.tokensPerPdfPageEstimate = tokensPerPdfPageEstimate;
        this.tokensPerImageEstimate = tokensPerImageEstimate;
        this.clock = clock;
    }

    public synchronized Reservation reserve(long estimatedInputTokens) {
        Instant now = clock.instant();
        reservations.removeIf(reservation -> reservation.createdAt().isBefore(now.minus(Duration.ofDays(1))));
        long minuteTokens = 0;
        int minuteRequests = 0;
        int dailyRequests = reservations.size();
        for (Reservation reservation : reservations) {
            if (!reservation.createdAt().isBefore(now.minusSeconds(60))) {
                minuteRequests++;
                minuteTokens = Math.addExact(minuteTokens, reservation.inputTokens());
            }
        }
        if (estimatedInputTokens < 1 || estimatedInputTokens > inputTokensPerMinute
                || minuteRequests >= requestsPerMinute || dailyRequests >= requestsPerDay
                || minuteTokens + estimatedInputTokens > inputTokensPerMinute) {
            throw new AnaliseRepresentacaoDocumental.FalhaProvedor("PROVIDER_QUOTA_EXHAUSTED", false);
        }
        Reservation reservation = new Reservation(now, estimatedInputTokens);
        reservations.addLast(reservation);
        return reservation;
    }

    public synchronized void recordActual(Reservation reservation, long actualInputTokens) {
        if (reservation == null || actualInputTokens < 0) return;
        Reservation current = reservations.stream().filter(item -> item.id().equals(reservation.id()))
                .findFirst().orElse(null);
        if (current != null) {
            reservations.remove(current);
            reservations.addLast(new Reservation(current.id(), current.createdAt(), actualInputTokens));
        }
    }

    public long estimateInputTokens(int pages, int imageCount, int promptCharacters) {
        if (pages < 0 || imageCount < 0 || promptCharacters < 0) {
            throw new IllegalArgumentException("Estimativa de entrada inválida.");
        }
        return Math.addExact(Math.addExact(Math.multiplyExact((long) pages, tokensPerPdfPageEstimate),
                Math.multiplyExact((long) imageCount, tokensPerImageEstimate)), (promptCharacters + 3L) / 4L);
    }

    public record Reservation(java.util.UUID id, Instant createdAt, long inputTokens) {
        private Reservation(Instant createdAt, long inputTokens) {
            this(java.util.UUID.randomUUID(), createdAt, inputTokens);
        }
    }
}
