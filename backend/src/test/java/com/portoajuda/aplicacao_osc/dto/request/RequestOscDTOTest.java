package com.portoajuda.aplicacao_osc.dto.request;

import com.portoajuda.aplicacao_osc.enums.CausaSocial;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestOscDTOTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void rejeitaCnpjFormatoInvalidoECamposObrigatoriosAusentes() {
        RequestOscDTO pedido = new RequestOscDTO(
                "123",
                CausaSocial.EDUCACAO,
                Set.of(CausaSocial.SAUDE),
                null,
                Set.of(),
                "",
                "",
                "",
                1800,
                "invalido",
                null, null, null, null, null, null,
                "cidade", "Brasil", "São Paulo", "São Paulo", "01000-000",
                "Centro", "Rua Exemplo", "10", null,
                Set.of(), null, null, false, false, null, true,
                null, null, null, null, null);

        Set<String> camposInvalidos = validator.validate(pedido).stream()
                .map(violacao -> violacao.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(camposInvalidos.contains("cnpj"));
        assertTrue(camposInvalidos.contains("nome"));
        assertTrue(camposInvalidos.contains("descricaoCurta"));
        assertTrue(camposInvalidos.contains("email"));
    }

    @Test
    void rejeitaCausaSocialForaDoEnumNoContratoJson() {
        var mapper = JsonMapper.builder().build();

        org.junit.jupiter.api.Assertions.assertThrows(
                JacksonException.class,
                () -> mapper.readValue("{\"causaPrincipal\":\"CAUSA_INEXISTENTE\"}", RequestOscDTO.class));
    }

    @Test
    void recebeCausasSeparadasEDeduplicaCausasEOdsNoPayloadJson() throws Exception {
        var mapper = JsonMapper.builder().build();

        RequestOscDTO pedido = mapper.readValue("""
                {
                  "causaPrincipal": "EDUCACAO",
                  "causasSecundarias": ["SAUDE", "CULTURA", "SAUDE"],
                  "ods": [3, 3, 17]
                }
                """, RequestOscDTO.class);

        assertEquals(CausaSocial.EDUCACAO, pedido.causaPrincipal());
        assertEquals(Set.of(CausaSocial.SAUDE, CausaSocial.CULTURA), pedido.causasSecundarias());
        assertEquals(Set.of(3, 17), pedido.ods());
    }
}
