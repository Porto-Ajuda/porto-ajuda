package com.portoajuda.aplicacao_osc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Deletes only server-managed image URLs, after commit/reference checks or after rollback. */
@Service
public class ManagedImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ManagedImageCleanupService.class);
    private final SupabaseStorageService storage;
    private final ManagedImageReferenceService references;

    public ManagedImageCleanupService(SupabaseStorageService storage, ManagedImageReferenceService references) {
        this.storage = storage;
        this.references = references;
    }

    public void afterRollback(String uploadedUrl) {
        register(uploadedUrl, false);
    }

    public void afterCommitIfUnreferenced(String oldUrl) {
        register(oldUrl, true);
    }

    private void register(String url, boolean committedOnly) {
        if (storage.managedImageKey(url) == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("A limpeza de imagem exige uma transação ativa.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (committedOnly != (status == STATUS_COMMITTED)) return;
                try {
                    if (!references.isReferenced(url)) storage.removerImagemGerenciadaUrl(url);
                } catch (RuntimeException error) {
                    // The URL and user/content identifiers are deliberately omitted.
                    log.warn("Não foi possível concluir a limpeza de uma imagem gerenciada; ela pode ser revisada pelo inventário.");
                }
            }
        });
    }
}
