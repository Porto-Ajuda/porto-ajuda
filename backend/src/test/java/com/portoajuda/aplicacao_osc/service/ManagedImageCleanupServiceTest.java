package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ManagedImageCleanupServiceTest {
    private final SupabaseStorageService storage = mock(SupabaseStorageService.class);
    private final ManagedImageReferenceService references = mock(ManagedImageReferenceService.class);
    private final ManagedImageCleanupService cleanup = new ManagedImageCleanupService(storage, references);
    private static final String URL = "https://storage.test/storage/v1/object/public/images/oscs/5/posts/8/00000000-0000-0000-0000-000000000001.png";

    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void replacementCleanupWaitsForCommitAndRechecksSharedReferences() {
        TransactionSynchronizationManager.initSynchronization();
        when(storage.managedImageKey(URL)).thenReturn("oscs/5/posts/8/00000000-0000-0000-0000-000000000001.png");
        when(references.isReferenced(URL)).thenReturn(true);
        cleanup.afterCommitIfUnreferenced(URL);
        verifyNoInteractions(references);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(references).isReferenced(URL);
        verify(storage, never()).removerImagemGerenciadaUrl(anyString());
    }

    @Test
    void replacementCleanupDoesNotDeleteUntilDatabaseReferenceIsGone() {
        TransactionSynchronizationManager.initSynchronization();
        when(storage.managedImageKey(URL)).thenReturn("oscs/5/posts/8/00000000-0000-0000-0000-000000000001.png");
        when(references.isReferenced(URL)).thenReturn(false);
        cleanup.afterCommitIfUnreferenced(URL);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(storage).removerImagemGerenciadaUrl(URL);
    }

    @Test
    void rollbackCleanupOnlyRunsAfterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        when(storage.managedImageKey(URL)).thenReturn("oscs/5/posts/8/00000000-0000-0000-0000-000000000001.png");
        when(references.isReferenced(URL)).thenReturn(false);
        cleanup.afterRollback(URL);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).removerImagemGerenciadaUrl(URL);
    }

    @Test
    void externalOrUnmanagedUrlsAreNeverScheduledForDeletion() {
        TransactionSynchronizationManager.initSynchronization();
        when(storage.managedImageKey("https://cdn.invalid/shared.png")).thenReturn(null);
        cleanup.afterCommitIfUnreferenced("https://cdn.invalid/shared.png");
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verify(storage).managedImageKey("https://cdn.invalid/shared.png");
        verifyNoInteractions(references);
    }
}
