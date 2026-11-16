
package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

public record RequestAtualizarPerfilDTO(

        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,

        @Size(max = 100, message = "O nome social deve ter no máximo 100 caracteres") String nomeSocial,

        @Size(max = 15, message = "O telefone deve ter no máximo 15 caracteres") String telefone,

        @Pattern(regexp = "^(\\d{5}-?\\d{3})?$", message = "Informe um CEP válido") String cep

) {
    public RequestAtualizarPerfilDTO(String nomeSocial, String telefone, String cep) {
        this(null, nomeSocial, telefone, cep);
    }
}
