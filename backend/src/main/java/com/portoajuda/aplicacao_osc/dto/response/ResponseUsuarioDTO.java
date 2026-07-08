package com.portoajuda.aplicacao_osc.dto.response;

import com.portoajuda.aplicacao_osc.utils.Email;

public record ResponseUsuarioDTO(
        Integer id,
        String nome,
        String nomeSocial,
        String email,
        String telefone
) {}