package com.portoajuda.aplicacao_osc.service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the durable queue only when a concrete provider adapter and the separate AI flag are configured. */
@Component
@ConditionalOnProperties(value = {
        @ConditionalOnProperty(prefix = "app.ngo-verification.ai-analysis", name = "enabled", havingValue = "true"),
        @ConditionalOnProperty(prefix = "app.ngo-verification.ai-analysis", name = "worker-enabled", havingValue = "true")
})
public class VerificacaoAnaliseWorker {
    private final VerificacaoAnaliseOrchestrator orchestrator;
    private final ObjectProvider<AnaliseRepresentacaoDocumental> providers;
    private final ExecutorService executor;
    private final UUID workerId = UUID.randomUUID();

    public VerificacaoAnaliseWorker(VerificacaoAnaliseOrchestrator orchestrator,
                                   ObjectProvider<AnaliseRepresentacaoDocumental> providers,
                                   ExecutorService ngoVerificationAiExecutor) {
        this.orchestrator = orchestrator;
        this.providers = providers;
        this.executor = ngoVerificationAiExecutor;
    }

    @Scheduled(fixedDelayString = "${app.ngo-verification.ai-analysis.poll-ms:5000}")
    public void pollOneJob() {
        AnaliseRepresentacaoDocumental provider = providers.getIfAvailable();
        if (provider != null) orchestrator.processNext(provider, workerId, executor);
    }
}
