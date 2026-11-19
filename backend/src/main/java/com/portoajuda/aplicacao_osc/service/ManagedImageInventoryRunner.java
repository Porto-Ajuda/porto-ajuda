package com.portoajuda.aplicacao_osc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit one-shot operator command; no inventory or deletion runs during normal startup. */
@Component
@ConditionalOnProperty(name = "managed-images.inventory.enabled", havingValue = "true")
public class ManagedImageInventoryRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ManagedImageInventoryRunner.class);
    private final ManagedImageOrphanInventoryService inventory;
    private final boolean apply;

    public ManagedImageInventoryRunner(ManagedImageOrphanInventoryService inventory,
            @org.springframework.beans.factory.annotation.Value("${managed-images.inventory.apply:false}") boolean apply) {
        this.inventory = inventory;
        this.apply = apply;
    }

    @Override public void run(ApplicationArguments args) {
        ManagedImageOrphanInventoryService.Inventory result = inventory.dryRun();
        log.info("Managed image inventory: managed={}, referenced={}, recent={}, ambiguous={}, eligible={}",
                result.managedObjects(), result.referenced(), result.recentOrphans(), result.ambiguous(), result.eligibleOrphans());
        if (apply) {
            ManagedImageOrphanInventoryService.ApplyResult deletion = inventory.deleteEligible(result);
            log.info("Managed image cleanup result: deleted={}, skippedAfterRecheck={}, failed={}",
                    deletion.deleted(), deletion.skippedAfterRecheck(), deletion.failed());
        }
    }
}
