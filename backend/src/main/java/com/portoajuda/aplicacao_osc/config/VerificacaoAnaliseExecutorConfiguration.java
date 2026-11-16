package com.portoajuda.aplicacao_osc.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${app.ngo-verification.ai-analysis.enabled:false}' == 'true' && "
        + "('${app.ngo-verification.ai-analysis.worker-enabled:false}' == 'true' || "
        + "'${app.ngo-verification.ai-analysis.evaluation-worker-enabled:false}' == 'true')")
public class VerificacaoAnaliseExecutorConfiguration {
    @Bean(name = "ngoVerificationAiExecutor", destroyMethod = "shutdownNow")
    ExecutorService ngoVerificationAiExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "ngo-verification-ai-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), threads, new ThreadPoolExecutor.AbortPolicy());
    }
}
