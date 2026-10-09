package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contato")
@RequiredArgsConstructor
public class ContatoController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<?> enviarMensagem(
            @Valid @RequestBody ContatoRequest contato) {

        try {

            emailService.enviarEmail(
                    contato.getNome(),
                    contato.getEmail(),
                    contato.getAssunto(),
                    contato.getMensagem()
            );

            return ResponseEntity.ok(
                    new MensagemResponse(
                            "Mensagem enviada com sucesso!"
                    )
            );

        } catch (Exception e) {

            System.err.println(
                    "Erro ao enviar e-mail: " + e.getMessage()
            );

            return ResponseEntity.internalServerError().body(
                    new MensagemResponse(
                            "Não foi possível enviar a mensagem."
                    )
            );
        }
    }

    @Data
    public static class ContatoRequest {

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        private String nome;

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
        private String email;

        @NotBlank(message = "O assunto é obrigatório.")
        @Size(max = 150, message = "O assunto deve ter no máximo 150 caracteres.")
        private String assunto;

        @NotBlank(message = "A mensagem é obrigatória.")
        @Size(max = 5000, message = "A mensagem deve ter no máximo 5000 caracteres.")
        private String mensagem;
    }

    public record MensagemResponse(String mensagem) {
    }
}