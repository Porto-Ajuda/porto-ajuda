package com.portoajuda.aplicacao_osc.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResponsePerfilUsuarioDTO(
                Integer id,
                String nome,
                String nomeSocial,
                String email,
                String telefone,
                String cep,
                LocalDate dataNascimento,
                LocalDateTime dataCriacao) {
}