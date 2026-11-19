package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagedImageOrphanInventoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final String OLD = "oscs/6/posts/11/00000000-0000-0000-0000-000000000011.png";
    private static final String RECENT = "usuarios/17/foto-perfil-00000000-0000-0000-0000-000000000017.jpg";
    private static final String REFERENCED = "oscs/6/logo-00000000-0000-0000-0000-000000000006.png";
    private static final String LEGACY = "oscs/6/logo-old-file.png";
    private final SupabaseStorageService storage = mock(SupabaseStorageService.class);
    private final ManagedImageReferenceService references = mock(ManagedImageReferenceService.class);
    private final ManagedImageOrphanInventoryService service = new ManagedImageOrphanInventoryService(
            storage, references, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));

    @Test
    void inventoryIncludesRetainedReferencesEnforcesGraceAndReportsAmbiguousNames() {
        when(storage.listarImagensGerenciadas()).thenReturn(List.of(
                new SupabaseStorageService.ManagedStoredObject(OLD, NOW.minus(Duration.ofDays(8))),
                new SupabaseStorageService.ManagedStoredObject(RECENT, NOW.minus(Duration.ofDays(2))),
                new SupabaseStorageService.ManagedStoredObject(REFERENCED, NOW.minus(Duration.ofDays(90))),
                new SupabaseStorageService.ManagedStoredObject(LEGACY, NOW.minus(Duration.ofDays(90)))));
        when(storage.isManagedImageKey(OLD)).thenReturn(true);
        when(storage.isManagedImageKey(RECENT)).thenReturn(true);
        when(storage.isManagedImageKey(REFERENCED)).thenReturn(true);
        when(storage.isManagedImageKey(LEGACY)).thenReturn(false);
        when(storage.urlPublicaDaImagemGerenciada(OLD)).thenReturn("url:old");
        when(storage.urlPublicaDaImagemGerenciada(RECENT)).thenReturn("url:recent");
        when(storage.urlPublicaDaImagemGerenciada(REFERENCED)).thenReturn("url:referenced");
        when(references.allReferencedUrls()).thenReturn(Set.of("url:referenced"));

        var result = service.dryRun();

        assertEquals(3, result.managedObjects());
        assertEquals(1, result.referenced());
        assertEquals(1, result.recentOrphans());
        assertEquals(1, result.ambiguous());
        assertEquals(1, result.eligibleOrphans());
        assertEquals(OLD, result.eligible().getFirst().key());
        verify(storage, never()).removerImagemGerenciadaKey(anyString());
    }

    @Test
    void deletionRechecksReferenceImmediatelyBeforeRemovingEligibleObject() {
        when(storage.isManagedImageKey(OLD)).thenReturn(true);
        when(storage.urlPublicaDaImagemGerenciada(OLD)).thenReturn("url:old");
        when(references.isReferenced("url:old")).thenReturn(true);
        var result = service.deleteEligible(new ManagedImageOrphanInventoryService.Inventory(1, 0, 0, 0,
                List.of(new ManagedImageOrphanInventoryService.Candidate(OLD, NOW.minus(Duration.ofDays(8))))));
        assertEquals(new ManagedImageOrphanInventoryService.ApplyResult(0, 1, 0), result);
        verify(storage, never()).removerImagemGerenciadaKey(anyString());
    }
}
