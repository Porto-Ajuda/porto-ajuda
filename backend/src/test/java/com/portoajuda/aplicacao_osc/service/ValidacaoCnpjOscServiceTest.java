package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.dto.response.ConsultaCnpjDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidacaoCnpjOscServiceTest {

    private static final String CNPJ = "11222333000181";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private CnpjaService cnpjaService;

    @Test
    void preservaElegibilidadeDeNaturezaJuridicaJaAceita() throws Exception {
        when(cnpjaService.consultarCnpj(CNPJ)).thenReturn(resposta("3999"));

        ConsultaCnpjDTO resultado = new ValidacaoCnpjOscService(cnpjaService).validar(CNPJ);

        assertTrue(resultado.elegivel());
    }

    @Test
    void rejeitaNaturezaJuridicaForaDaListaPermitida() throws Exception {
        when(cnpjaService.consultarCnpj(CNPJ)).thenReturn(resposta("9999"));

        ConsultaCnpjDTO resultado = new ValidacaoCnpjOscService(cnpjaService).validar(CNPJ);

        assertFalse(resultado.elegivel());
    }

    private JsonNode resposta(String codigoNatureza) throws Exception {
        return JSON.readTree("""
                {
                  "taxId": "%s",
                  "founded": "2010-01-01",
                  "status": { "text": "Ativa" },
                  "company": {
                    "name": "Organização Exemplo",
                    "nature": { "id": "%s", "text": "Natureza de teste" }
                  }
                }
                """.formatted(CNPJ, codigoNatureza));
    }
}
