package com.portoajuda.aplicacao_osc.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ManagedImageOrphanInventoryService {
    private final SupabaseStorageService storage;
    private final ManagedImageReferenceService references;
    private final Clock clock;
    private final Duration gracePeriod;

    public ManagedImageOrphanInventoryService(SupabaseStorageService storage,
            ManagedImageReferenceService references, Clock clock,
            @Value("${managed-images.orphan-grace:PT168H}") Duration gracePeriod) {
        this.storage = storage;
        this.references = references;
        this.clock = clock;
        this.gracePeriod = gracePeriod.isNegative() ? Duration.ZERO : gracePeriod;
    }

    /** Read-only by default; contains no object paths or user identifiers in the summary. */
    public Inventory dryRun() {
        Instant cutoff = clock.instant().minus(gracePeriod);
        Set<String> referencedUrls = references.allReferencedUrls();
        int total = 0, referenced = 0, recent = 0, ambiguous = 0;
        List<Candidate> eligible = new ArrayList<>();
        for (SupabaseStorageService.ManagedStoredObject object : storage.listarImagensGerenciadas()) {
            if (object == null || object.key() == null) { ambiguous++; continue; }
            if (!object.key().startsWith("usuarios/") && !object.key().startsWith("oscs/")) continue;
            if (!storage.isManagedImageKey(object.key())) { ambiguous++; continue; }
            total++;
            String url = storage.urlPublicaDaImagemGerenciada(object.key());
            if (url == null) { ambiguous++; continue; }
            if (referencedUrls.contains(url)) { referenced++; continue; }
            if (object.createdAt() == null) { ambiguous++; continue; }
            if (object.createdAt().isAfter(cutoff)) { recent++; continue; }
            eligible.add(new Candidate(object.key(), object.createdAt()));
        }
        return new Inventory(total, referenced, recent, ambiguous, List.copyOf(eligible));
    }

    /** Destructive operation is explicit and rechecks every reference immediately before deleting. */
    public ApplyResult deleteEligible(Inventory inventory) {
        int deleted = 0, skipped = 0, failed = 0;
        Instant cutoff = clock.instant().minus(gracePeriod);
        for (Candidate candidate : inventory.eligible()) {
            if (candidate.createdAt() == null || candidate.createdAt().isAfter(cutoff)
                    || !storage.isManagedImageKey(candidate.key())) { skipped++; continue; }
            String url = storage.urlPublicaDaImagemGerenciada(candidate.key());
            try {
                if (references.isReferenced(url)) { skipped++; continue; }
                storage.removerImagemGerenciadaKey(candidate.key());
                deleted++;
            } catch (RuntimeException error) { failed++; }
        }
        return new ApplyResult(deleted, skipped, failed);
    }

    public record Candidate(String key, Instant createdAt) { }
    public record Inventory(int managedObjects, int referenced, int recentOrphans, int ambiguous,
                            List<Candidate> eligible) {
        public int eligibleOrphans() { return eligible == null ? 0 : eligible.size(); }
    }
    public record ApplyResult(int deleted, int skippedAfterRecheck, int failed) { }
}
