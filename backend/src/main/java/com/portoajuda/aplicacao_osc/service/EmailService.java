
package com.portoajuda.aplicacao_osc.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

        private final Resend resend;
        private final String remetente;
        private final String emailDestino;

        public EmailService(
                        @Value("${RESEND_API_KEY}") String apiKey,
                        @Value("${MAIL_FROM}") String remetente,
                        @Value("${MAIL_TO:}") String emailDestino) {

                this.resend = new Resend(apiKey);
                this.remetente = remetente;
                this.emailDestino = emailDestino;
        }

        // Envio utilizado pelo formulário de contato
        public void enviarEmail(
                        String nome,
                        String email,
                        String assunto,
                        String mensagem) {

                if (emailDestino == null || emailDestino.isBlank()) {
                        throw new IllegalStateException(
                                        "A variável MAIL_TO não está configurada.");
                }

                String texto = "Nova mensagem recebida pelo site Porto Ajuda.\n\n" +
                                "Nome: " + nome + "\n" +
                                "E-mail: " + email + "\n" +
                                "Assunto: " + assunto + "\n\n" +
                                "Mensagem:\n" + mensagem;

                enviar(
                                emailDestino,
                                "Contato Porto Ajuda - " + assunto,
                                texto);
        }

        // Envio utilizado na confirmação de e-mail
        public void enviarConfirmacaoEmail(
                        String nome,
                        String emailUsuario,
                        String token,
                        String appBaseUrl) {

                String linkConfirmacao = appBaseUrl
                                + "/usuario/confirmar-email?token="
                                + java.net.URLEncoder.encode(
                                                token,
                                                java.nio.charset.StandardCharsets.UTF_8);

                String texto = "Olá, " + nome + "!\n\n" +
                                "Recebemos seu cadastro no Porto Ajuda.\n\n" +
                                "Para confirmar seu endereço de e-mail, acesse:\n\n" +
                                linkConfirmacao + "\n\n" +
                                "Este link expira em 15 minutos.\n\n" +
                                "Se você não realizou esse cadastro, ignore esta mensagem.\n\n" +
                                "Equipe Porto Ajuda";

                enviar(
                                emailUsuario,
                                "Confirme seu e-mail - Porto Ajuda",
                                texto);
        }

        /** Link de recuperação com credencial de uso único; o conteúdo nunca é registrado. */
        public void enviarRecuperacaoSenha(String emailUsuario, String link) {
                String texto = "Recebemos uma solicitação para redefinir a senha da sua conta Porto Ajuda.\n\n"
                                + "Para escolher uma nova senha, acesse o link seguro abaixo. Ele expira em 20 minutos e só pode ser usado uma vez:\n\n"
                                + link + "\n\nSe você não solicitou a recuperação, ignore esta mensagem.";
                enviar(emailUsuario, "Recuperação de senha - Porto Ajuda", texto);
        }

        /** Envia somente o OTP já gerado pelo backend; não registre o conteúdo da mensagem. */
        public void enviarCodigoAutenticacaoAdministrativa(String emailUsuario, String codigo) {
                String texto = "Seu código de verificação administrativa do Porto Ajuda é "
                                + codigo
                                + ". Ele expira em 5 minutos. Não compartilhe este código."
                                + " Se você não iniciou esta tentativa, ignore esta mensagem.";
                enviar(emailUsuario, "Código de segurança - Porto Ajuda", texto);
        }

        /** Alerta sem credenciais, OTPs, telefone, IP ou payloads de autenticação. */
        public void enviarAlertaIncidenteAutenticacaoAdmin(
                        String emailDestinatario,
                        String incidenteId,
                        java.time.Instant ocorreuEm,
                        String assuntoIncidente) {
                String texto = "Um incidente de autenticação administrativa foi registrado no Porto Ajuda.\n\n"
                                + "Referência: " + incidenteId + "\n"
                                + "Tipo: " + assuntoIncidente + "\n"
                                + "Horário UTC: " + ocorreuEm + "\n\n"
                                + "Nenhuma senha ou código de verificação é incluído neste alerta.";
                enviar(emailDestinatario, "Alerta de segurança administrativa - Porto Ajuda", texto);
        }

        private void enviar(
                        String destinatario,
                        String assunto,
                        String texto) {

                CreateEmailOptions params = CreateEmailOptions.builder()
                                .from(remetente)
                                .to(destinatario)
                                .subject(assunto)
                                .text(texto)
                                .build();

                try {
                        CreateEmailResponse resposta = resend.emails().send(params);

                        if (resposta == null || resposta.getId() == null) {
                                throw new IllegalStateException(
                                                "O Resend não retornou um ID para o e-mail.");
                        }

                } catch (ResendException e) {
                        throw new IllegalStateException(
                                        "Falha ao enviar e-mail pelo Resend.", e);
                }
        }
}
