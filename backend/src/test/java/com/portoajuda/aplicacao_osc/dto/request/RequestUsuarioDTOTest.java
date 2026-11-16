package com.portoajuda.aplicacao_osc.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestUsuarioDTOTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsFormattedAndCanonicalCpfAndLegitimateAccentedName() {
        assertTrue(validator.validate(valid("012.345.678-90", "José d'Ávila-Souza", LocalDate.of(1990, 1, 1))).isEmpty());
        assertTrue(validator.validate(valid("01234567890", "Maria da Conceição", LocalDate.of(1990, 1, 1))).isEmpty());
    }

    @Test
    void rejectsMalformedOrInvalidCpfBlankNameAndFutureBirthDate() {
        assertFalse(validator.validate(valid("abc01234567890xyz", "Nome Válido", LocalDate.of(1990, 1, 1))).isEmpty());
        assertFalse(validator.validate(valid("01234567891", "Nome Válido", LocalDate.of(1990, 1, 1))).isEmpty());
        assertTrue(validator.validate(valid("01234567890", "   ", LocalDate.of(1990, 1, 1))).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("nome")));
        assertTrue(validator.validate(valid("01234567890", "Nome Válido", LocalDate.now().plusDays(1))).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("dataNascimento")));
    }

    private RequestUsuarioDTO valid(String cpf, String nome, LocalDate nascimento) {
        return new RequestUsuarioDTO(cpf, null, nome, null, nascimento,
                "cpf-test@example.invalid", "PREFIRO_NAO_INFORMAR", null, "SenhaValida1!");
    }
}
