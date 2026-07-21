package com.portoajuda.aplicacao_osc.dto.response;

public record ResponseLoginDTO(
        String token,
        ResponseUsuarioDTO responseUsuarioDTO
)
{}
