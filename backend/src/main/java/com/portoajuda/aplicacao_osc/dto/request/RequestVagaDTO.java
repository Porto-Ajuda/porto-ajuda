package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.util.Set;

public record RequestVagaDTO(
        @Min(1)
        int quantidadeVagas,

        @NotBlank(message = "Definir a fim de evitar dúvidas")
        @Pattern(regexp = "(?i)PRESENCIAL|REMOTO|HIBRIDO")
        String modalidadeVoluntariado,

        @NotBlank(message = "Importante definir para filtrar candidatos")
        String requisitos,

        @NotBlank(message = "Importante definir para evitar dúvidas")
        @Size(max = 100)
        String cargaHoraria,

        @NotBlank(message = "Obrigatório definir cidade")
        @Size(max = 100)
        String cidade,

        @NotBlank(message = "Obrigatório definir bairro")
        @Size(max = 100)
        String bairro,

        @Size(max = 100)
        String rua,

        @Size(max = 100)
        String numero,

        @Size(max = 100)
        String complemento,

        @NotNull(message = "Obrigatório definir início da ação")
        LocalDate dataInicio,

        @NotNull(message = "Obrigatório definir fim da ação, mesmo que não exato")
        LocalDate dataFim,

        @Pattern(regexp = "(?i)ABERTA|ENCERRADA|CANCELADA")
        String status
) {}
