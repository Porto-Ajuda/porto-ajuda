package com.portoajuda.aplicacao_osc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${MAIL_TO}")
    private String emailDestino;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Envio utilizado pelo formulário de contato
    public void enviarEmail(
            String nome,
            String email,
            String assunto,
            String mensagem) {

        SimpleMailMessage emailMensagem = new SimpleMailMessage();

        emailMensagem.setTo(emailDestino);
        emailMensagem.setSubject("Contato Porto Ajuda - " + assunto);

        emailMensagem.setText(
                "Nova mensagem recebida pelo site Porto Ajuda.\n\n" +
                        "Nome: " + nome + "\n" +
                        "E-mail: " + email + "\n" +
                        "Assunto: " + assunto + "\n\n" +
                        "Mensagem:\n" + mensagem);

        mailSender.send(emailMensagem);
    }

    // Envio utilizado na confirmação de e-mail
    public void enviarConfirmacaoEmail(
            String nome,
            String emailUsuario,
            String token,
            String appBaseUrl) {

        String linkConfirmacao = appBaseUrl
                + "/usuario/confirmar-email?token="
                + token;

        SimpleMailMessage emailMensagem = new SimpleMailMessage();

        emailMensagem.setTo(emailUsuario);
        emailMensagem.setSubject(
                "Confirme seu e-mail - Porto Ajuda");

        emailMensagem.setText(
                "Olá, " + nome + "!\n\n" +
                        "Recebemos seu cadastro no Porto Ajuda.\n\n" +
                        "Para confirmar seu endereço de e-mail, acesse o link:\n\n" +
                        linkConfirmacao + "\n\n" +
                        "Este link expira em 2 horas.\n\n" +
                        "Se você não realizou esse cadastro, ignore esta mensagem.\n\n" +
                        "Equipe Porto Ajuda");

        mailSender.send(emailMensagem);
    }
}