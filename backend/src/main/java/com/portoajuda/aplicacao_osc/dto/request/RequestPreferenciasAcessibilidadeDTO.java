
package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RequestPreferenciasAcessibilidadeDTO(

    @NotNull
    @Min(80)
    @Max(150)
    Integer tamanhoFonte,

    @NotBlank
    @Pattern(regexp = "escuro|claro")
    String tema,

    @NotNull
    Boolean altoContraste,

    @NotNull
    Boolean libras,

    /** Optional for backwards compatibility; persisted only on an explicit profile save. */
    @Size(max = 14)
    Set<String> tiposOng

) {}
