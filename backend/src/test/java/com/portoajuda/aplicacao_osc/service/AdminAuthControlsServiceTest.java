package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.repository.AdminAuthControlRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthControlsServiceTest {
    @Mock AdminAuthControlRepository repository;
    @Mock AdminAuthCodeSecurity security;
    @InjectMocks AdminAuthControlsService service;

    @Test
    void readsFreshLockStateAsScalarAfterAtomicUpsertWithoutEntityCache() {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        when(security.hashIdentifier("control:START_IP", "subject")).thenReturn("hashed-control-key");
        when(repository.findLockedUntilByControlKey("hashed-control-key"))
                .thenReturn(Optional.of(now.plusSeconds(60)), Optional.of(now.plusSeconds(60)));
        when(repository.findCounterByControlKey("hashed-control-key")).thenReturn(Optional.of(4));

        assertTrue(service.isLocked("START_IP", "subject", now));
        int count = service.increment("START_IP", "subject", now, Duration.ofMinutes(15),
                4, Duration.ofMinutes(15), true);

        assertEquals(4, count);
        verify(repository).increment(eq("hashed-control-key"), eq("START_IP"), eq(now),
                eq(now.minus(Duration.ofMinutes(15))), eq(true), eq(4), any(Instant.class));
        assertTrue(service.isLocked("START_IP", "subject", now));
    }

    @Test
    void expiredLocksAndOldBucketsCanBePurgedWithoutDeletingActiveLocks() {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        when(security.hashIdentifier("control:START_IP", "subject")).thenReturn("hashed-control-key");
        when(repository.findLockedUntilByControlKey("hashed-control-key")).thenReturn(Optional.of(now.minusSeconds(1)));
        when(repository.deleteExpired(now.minus(Duration.ofDays(30)), now)).thenReturn(1);

        assertFalse(service.isLocked("START_IP", "subject", now));
        assertEquals(1, service.deleteExpired(now.minus(Duration.ofDays(30)), now));
        verify(repository).deleteExpired(now.minus(Duration.ofDays(30)), now);
    }
}
