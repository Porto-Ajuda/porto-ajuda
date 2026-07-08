package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

public record RequestUsuarioDTO(
        @NotBlank(message = "O CPF é obrigatório")
        @CPF(message = "O CPF deve ser válido")
        String cpf,

        @NotBlank(message = "O nome é obrigatório")
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