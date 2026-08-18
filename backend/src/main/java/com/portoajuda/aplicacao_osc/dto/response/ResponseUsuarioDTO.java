package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.utils.Cpf;

import java.time.LocalDateTime;

public record ResponseUsuarioDTO(
        String cpf,
        String nome,
        String nomeSocial,
        String dataNascimento,
        String email,
        String telefone
) {}