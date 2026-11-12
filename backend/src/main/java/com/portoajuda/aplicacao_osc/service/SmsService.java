package com.portoajuda.aplicacao_osc.service;

import org.springframework.stereotype.Service;

/** Fachada de entrega SMS. Geração e validação de códigos pertencem ao chamador. */
@Service
public class SmsService {

    private final SmsMaisClient smsMaisClient;

    public SmsService(SmsMaisClient smsMaisClient) {
        this.smsMaisClient = smsMaisClient;
    }

    public SmsMaisSendResult enviarSms(String telefone, String mensagem, String externalId) {
        validarMensagem(mensagem);
        return smsMaisClient.enviar(normalizarTelefoneBrasileiro(telefone), mensagem, externalId);
    }

    private void validarMensagem(String mensagem) {
        if (mensagem == null || mensagem.isBlank()
                || mensagem.codePointCount(0, mensagem.length()) > 160) {
            throw SmsDeliveryException.invalidRequest(
                    "A mensagem SMS deve conter de 1 a 160 caracteres.");
        }
    }

    static String normalizarTelefoneBrasileiro(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            throw SmsDeliveryException.invalidRequest("O telefone de destino é obrigatório.");
        }

        String valor = telefone.trim();
        if (!valor.matches("\\+?[0-9\\s().-]+")) {
            throw SmsDeliveryException.invalidRequest("O telefone de destino está em formato inválido.");
        }

        String digitos = valor.replaceAll("[^0-9]", "");
        if (digitos.length() == 13 && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (!digitos.matches("[1-9][0-9]9[0-9]{8}")) {
            throw SmsDeliveryException.invalidRequest(
                    "O telefone deve conter DDD e número brasileiro válido em formato nacional.");
        }

        return "55" + digitos;
    }
}
