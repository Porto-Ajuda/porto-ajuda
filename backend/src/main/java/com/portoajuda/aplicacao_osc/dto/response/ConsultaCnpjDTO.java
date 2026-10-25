
package com.portoajuda.aplicacao_osc.dto.response;

public record ConsultaCnpjDTO(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String dataAbertura,
        String situacaoCadastral,
        String naturezaJuridica,
        boolean elegivel,
        String motivo
) {}
