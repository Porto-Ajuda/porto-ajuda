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

    public void enviarEmail(
            String nome,
            String email,
            String assunto,
            String mensagem) {

        SimpleMailMessage emailMensagem = new SimpleMailMessage();

        // Quem vai receber
        emailMensagem.setTo(emailDestino);

        // Assunto
        emailMensagem.setSubject(
                "Contato Porto Ajuda - " + assunto
        );

        // Conteúdo
        emailMensagem.setText(
                "Nova mensagem recebida pelo site Porto Ajuda.\n\n" +

                "Nome: " + nome + "\n" +
                "E-mail: " + email + "\n" +
                "Assunto: " + assunto + "\n\n" +

                "Mensagem:\n" +
                mensagem
        );

        // Envia
        mailSender.send(emailMensagem);
    }
}