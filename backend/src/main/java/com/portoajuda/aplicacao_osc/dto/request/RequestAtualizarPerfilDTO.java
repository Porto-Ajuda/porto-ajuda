
package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestAtualizarPerfilDTO(

        @Size(max = 100, message = "O nome social deve ter no máximo 100 caracteres") String nomeSocial,

        @Size(max = 15, message = "O telefone deve ter no máximo 15 caracteres") String telefone,

        @Pattern(regexp = "^(\\d{5}-?\\d{3})?$", message = "Informe um CEP válido") String cep

) {
}
