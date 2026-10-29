package com.portoajuda.aplicacao_osc.dto.request;

public record RequestUpdatePostDTO(
        Integer id,
        RequestPostDTO post,
        RequestVagaDTO vaga
) {}