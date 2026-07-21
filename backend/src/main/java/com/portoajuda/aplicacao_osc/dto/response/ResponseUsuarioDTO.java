package com.portoajuda.aplicacao_osc.dto.response;

public record ResponseUsuarioDTO(
        Integer id,
        String nome,
        String nomeSocial,
        String email,
        String telefone
) {}