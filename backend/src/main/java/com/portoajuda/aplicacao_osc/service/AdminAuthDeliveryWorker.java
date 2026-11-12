package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.Channel;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls a database-backed outbox; PostgreSQL row locks coordinate all app instances. */
@Component
public class AdminAuthDeliveryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuthDeliveryWorker.class);
    private static final long BASE_RETRY_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long MAX_RETRY_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final AdminAuthDeliveryQueueService queue;
    private final EmailService emailService;
    private final SmsService smsService;
    private final LongSupplier nanoTime;
    private int consecutivePersistenceFailures;
    private long nextPersistenceAttemptNanos;

    @Autowired
    public AdminAuthDeliveryWorker(
            AdminAuthDeliveryQueueService queue,
            EmailService emailService,
            SmsService smsService) {
        this(queue, emailService, smsService, System::nanoTime);
    }

    AdminAuthDeliveryWorker(
            AdminAuthDeliveryQueueService queue,
            EmailService emailService,
            SmsService smsService,
            LongSupplier nanoTime) {
        this.queue = queue;
        this.emailService = emailService;
        this.smsService = smsService;
        this.nanoTime = nanoTime;
    }

    @Scheduled(fixedDelayString = "${admin-auth.delivery-poll-ms:1000}")
    public void processNextDelivery() {
        if (nanoTime.getAsLong() < nextPersistenceAttemptNanos) {
            return;
        }

        final AdminAuthDeliveryQueueService.ClaimedDelivery delivery;
        try {
            queue.expireStaleDeliveries();
            delivery = queue.claimNext();
            persistenceRecovered();
        } catch (RuntimeException persistenceFailure) {
            backoffAfterPersistenceFailure("claim");
            return;
        }
        if (delivery == null) {
            return;
        }

        boolean delivered = false;
        try {
            if (delivery.channel() == Channel.EMAIL) {
                emailService.enviarCodigoAutenticacaoAdministrativa(
                        delivery.destination(), delivery.code());
            } else {
                smsService.enviarSms(
                        delivery.destination(),
                        "Porto Ajuda: seu código de acesso administrativo é " + delivery.code()
                                + ". Expira em 5 minutos. Não compartilhe.",
                        delivery.challengeId() + ":sms");
            }
            delivered = true;
        } catch (RuntimeException ignoredDeliveryFailure) {
            // Provider exceptions can contain destination or message details; log no exception data.
            LOGGER.warn("Falha de entrega OTP pelo canal {}; detalhes sensíveis omitidos.", delivery.channel());
        }

        try {
            queue.finish(delivery.deliveryId(), delivered);
            persistenceRecovered();
        } catch (RuntimeException persistenceFailure) {
            // On rollback the PROCESSING lease and encrypted payload remain available for recovery.
            backoffAfterPersistenceFailure("finish");
        }
    }

    private void backoffAfterPersistenceFailure(String operation) {
        consecutivePersistenceFailures = Math.min(consecutivePersistenceFailures + 1, 6);
        long multiplier = 1L << (consecutivePersistenceFailures - 1);
        long delay = Math.min(MAX_RETRY_NANOS, BASE_RETRY_NANOS * multiplier);
        nextPersistenceAttemptNanos = nanoTime.getAsLong() + delay;
        LOGGER.warn("Falha temporária na persistência do worker administrativo durante {}; "
                        + "novos registros permanecem no banco e a próxima tentativa ocorrerá em {}s.",
                operation, TimeUnit.NANOSECONDS.toSeconds(delay));
    }

    private void persistenceRecovered() {
        consecutivePersistenceFailures = 0;
        nextPersistenceAttemptNanos = 0;
    }
}
