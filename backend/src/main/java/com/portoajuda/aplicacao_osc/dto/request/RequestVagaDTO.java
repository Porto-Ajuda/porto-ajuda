package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.br.CNPJ;

import java.time.LocalDate;
import java.util.Set;

public record RequestVagaDTO(
        int quantidadeVagas,

        @NotBlank(message = "Definir a fim de evitar dúvidas")
        String modalidadeVoluntariado,

        @NotBlank(message = "Importante definir para filtrar candidatos")
        String requisitos,

        @NotBlank(message = "Importante definir para evitar dúvidas")
        String cargaHoraria,

        @NotBlank(message = "Obrigatório definir cidade")
        String cidade,

        @NotBlank(message = "Obrigatório definir bairro")
        String bairro,

        String rua,

        String numero,

        String complemento,

        @NotNull(message = "Obrigatório definir início da ação")
        LocalDate dataInicio,

        @NotNull(message = "Obrigatório definir fim da ação, mesmo que não exato")
        LocalDate dataFim,

        String status
) {}