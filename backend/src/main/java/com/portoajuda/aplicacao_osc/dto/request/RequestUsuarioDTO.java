package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

public record RequestUsuarioDTO(
        @NotBlank(message = "O CPF é obrigatório")
        @Pattern(regexp = "^(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})$",
                message = "Informe o CPF com 11 dígitos, com ou sem pontuação")
        @CPF(message = "O CPF deve ser válido")
        String cpf,

        @Size(min = 8, max = 8)
        String cep,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        String nomeSocial,

        @NotNull(message = "Obrigatório definir data de nascimento")
        @Past
        LocalDate dataNascimento,

        @NotBlank(message = "Obrigatório definir um email")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Necessário definir gênero")
        String genero,

        String telefone,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "Mínimo de 8 caracteres")
        String senha
) {}
