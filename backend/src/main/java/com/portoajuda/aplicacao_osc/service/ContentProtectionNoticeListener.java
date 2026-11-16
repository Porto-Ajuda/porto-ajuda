package com.portoajuda.aplicacao_osc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContentProtectionNoticeListener {
    private static final Logger log = LoggerFactory.getLogger(ContentProtectionNoticeListener.class);
    private final EmailService email;

    public ContentProtectionNoticeListener(EmailService email) { this.email = email; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyOwner(ContentProtectionActivatedEvent event) {
        try {
            email.enviarAvisoProtecaoConteudo(event.emailResponsavel(), event.nomeOrganizacao());
        } catch (RuntimeException failure) {
            // The committed protection is not rolled back if delivery is temporarily unavailable.
            log.warn("Não foi possível entregar a notificação de proteção preventiva.");
        }
    }
}
