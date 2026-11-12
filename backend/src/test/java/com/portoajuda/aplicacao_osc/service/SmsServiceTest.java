package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class SmsServiceTest {

    private final SmsMaisClient client = mock(SmsMaisClient.class);
    private final SmsService service = new SmsService(client);

    @Test
    void normalizaTelefoneBrasileiroParaFormatoExigidoPeloProvedor() {
        service.enviarSms("(11) 91234-5678", "Mensagem de teste", "id-teste-1");

        verify(client).enviar("5511912345678", "Mensagem de teste", "id-teste-1");
    }

    @Test
    void aceitaTelefoneComDDI55SemAlterarDigitos() {
        service.enviarSms("+55 (11) 91234-5678", "Mensagem de teste", "id-teste-2");

        verify(client).enviar("5511912345678", "Mensagem de teste", "id-teste-2");
    }

    @Test
    void aceitaDdd55EmTelefoneNacionalSemConfundirComPrefixoDoPais() {
        service.enviarSms("55912345678", "Mensagem de teste", "id-teste-ddd55");

        verify(client).enviar("5555912345678", "Mensagem de teste", "id-teste-ddd55");
    }

    @Test
    void rejeitaTelefoneInvalidoSemChamarOCliente() {
        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> service.enviarSms("12345", "Mensagem de teste", "id-teste-3"));

        assertEquals(SmsDeliveryException.Reason.INVALID_REQUEST, error.getReason());
        verifyNoInteractions(client);
    }

    @Test
    void rejeitaTelefoneFixoOuCelularSemNonoDigito() {
        assertThrows(
                SmsDeliveryException.class,
                () -> service.enviarSms("(11) 2345-6789", "Mensagem de teste", "id-fixo"));
        assertThrows(
                SmsDeliveryException.class,
                () -> service.enviarSms("(11) 81234-5678", "Mensagem de teste", "id-sem-nono-digito"));

        verifyNoInteractions(client);
    }

    @Test
    void rejeitaMensagemMaiorQueLimiteDoProvedorSemChamarOCliente() {
        SmsDeliveryException error = assertThrows(
                SmsDeliveryException.class,
                () -> service.enviarSms("11912345678", "x".repeat(161), "id-teste-4"));

        assertEquals(SmsDeliveryException.Reason.INVALID_REQUEST, error.getReason());
        verifyNoInteractions(client);
    }
}
