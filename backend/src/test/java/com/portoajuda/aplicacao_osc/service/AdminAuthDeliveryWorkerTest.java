package com.portoajuda.aplicacao_osc.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portoajuda.aplicacao_osc.entity.AdminAuthDelivery.Channel;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthDeliveryWorkerTest {

    @Mock private AdminAuthDeliveryQueueService queue;
    @Mock private EmailService emailService;
    @Mock private SmsService smsService;

    @Test
    void indisponibilidadeDoBancoAplicaBackoffEVoltaAConsultarSemPerderFila() {
        AtomicLong clock = new AtomicLong(100L);
        AdminAuthDeliveryWorker worker = new AdminAuthDeliveryWorker(
                queue, emailService, smsService, clock::get);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"))
                .doNothing()
                .when(queue).expireStaleDeliveries();
        when(queue.claimNext()).thenReturn(null);

        worker.processNextDelivery();
        worker.processNextDelivery();
        verify(queue, org.mockito.Mockito.times(1)).expireStaleDeliveries();
        verify(queue, never()).claimNext();

        clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        worker.processNextDelivery();

        verify(queue, org.mockito.Mockito.times(2)).expireStaleDeliveries();
        verify(queue).claimNext();
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
    }

    @Test
    void falhaNoEmailMarcaEntregaComoFalhaSemRegistrarCodigo() {
        UUID deliveryId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(queue.claimNext()).thenReturn(new AdminAuthDeliveryQueueService.ClaimedDelivery(
                deliveryId, challengeId, Channel.EMAIL, "admin@example.invalid", "246810"));
        org.mockito.Mockito.doThrow(new IllegalStateException("sanitized provider failure"))
                .when(emailService).enviarCodigoAutenticacaoAdministrativa("admin@example.invalid", "246810");

        new AdminAuthDeliveryWorker(queue, emailService, smsService).processNextDelivery();

        verify(queue).finish(deliveryId, false);
        verify(smsService, never()).enviarSms(anyString(), anyString(), anyString());
    }

    @Test
    void falhaNoSmsMarcaEntregaComoFalhaSemRegistrarCodigo() {
        UUID deliveryId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(queue.claimNext()).thenReturn(new AdminAuthDeliveryQueueService.ClaimedDelivery(
                deliveryId, challengeId, Channel.SMS, "5511912345678", "135790"));
        org.mockito.Mockito.doThrow(new IllegalStateException("sanitized provider failure"))
                .when(smsService).enviarSms(eq("5511912345678"), anyString(), eq(challengeId + ":sms"));

        new AdminAuthDeliveryWorker(queue, emailService, smsService).processNextDelivery();

        verify(queue).finish(deliveryId, false);
        verify(emailService, never()).enviarCodigoAutenticacaoAdministrativa(anyString(), anyString());
    }

    @Test
    void entregaAceitaConfirmaOutboxSomenteAposRetornoDoProvedor() {
        UUID deliveryId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(queue.claimNext()).thenReturn(new AdminAuthDeliveryQueueService.ClaimedDelivery(
                deliveryId, challengeId, Channel.SMS, "5511912345678", "135790"));
        when(smsService.enviarSms(eq("5511912345678"), anyString(), eq(challengeId + ":sms")))
                .thenReturn(new SmsMaisSendResult("provider-id", "request-id"));

        new AdminAuthDeliveryWorker(queue, emailService, smsService).processNextDelivery();

        verify(queue).finish(deliveryId, true);
    }
}
