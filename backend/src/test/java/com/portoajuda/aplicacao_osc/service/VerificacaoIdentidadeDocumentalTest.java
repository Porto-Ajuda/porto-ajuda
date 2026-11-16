package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Campo;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.CampoExtraido;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Conclusao;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Divergencia;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.EstadoCampo;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Gravidade;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.ReferenciaPagina;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Resultado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerificacaoIdentidadeDocumentalTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T02:00:00Z"), ZoneId.of("UTC"));
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Map<Long, String> LEGAL_DOC = Map.of(1L, "REPRESENTATION_PROOF");
    private static final Map<Long, String> DELEGATE_DOCS = Map.of(
            1L, "REPRESENTATION_PROOF", 2L, "DELEGATION_AUTHORIZATION");

    @Test
    void completeLegalDocumentMatchesCivilOrSocialNameButIsNotIndependentVerification() {
        var result = result("LEGAL_REPRESENTATIVE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "Ana Souza", 1, EstadoCampo.ENCONTRADO),
                field(Campo.VALIDADE, "2027-05-30", 1, EstadoCampo.ENCONTRADO)), List.of());

        var assessment = assess("LEGAL_REPRESENTATIVE", "Ana Maria de Souza", "Ana Souza", result, LEGAL_DOC, CLOCK, SAO_PAULO);

        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.MATCHES_REGISTERED_DATA,
                assessment.identityComparison());
        assertEquals(VerificacaoIdentidadeDocumental.ApparentValidity.APPARENTLY_CURRENT,
                assessment.apparentValidity());
        assertEquals(VerificacaoIdentidadeDocumental.DelegationEvidenceChain.NOT_APPLICABLE,
                assessment.delegationEvidenceChain());
        assertEquals(2027, assessment.expiryDate().getYear());
    }

    @Test
    void civilNameWithAccentsAndReorderedTokensMatchesRegisteredCivilName() {
        var result = result("LEGAL_REPRESENTATIVE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "Silva, João da", 1, EstadoCampo.ENCONTRADO)), List.of());

        var assessment = assess("LEGAL_REPRESENTATIVE", "João da Silva", null, result, LEGAL_DOC, CLOCK, SAO_PAULO);

        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.MATCHES_REGISTERED_DATA,
                assessment.identityComparison());
    }

    @Test
    void differentNameIsAReviewMismatchAndNotAnIdentityOrFraudVerification() {
        var result = result("LEGAL_REPRESENTATIVE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "Pessoa Fictícia B", 1, EstadoCampo.ENCONTRADO)), List.of());

        var assessment = assess("LEGAL_REPRESENTATIVE", "Pessoa Fictícia A", null, result, LEGAL_DOC, CLOCK, SAO_PAULO);

        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.MISMATCH, assessment.identityComparison());
        assertNotEquals(VerificacaoIdentidadeDocumental.IdentityComparison.INDEPENDENTLY_VERIFIED,
                assessment.identityComparison());
    }

    @Test
    void partialAmbiguousOrConflictingNamesRemainInsufficient() {
        var partial = result("LEGAL_REPRESENTATIVE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "Maria", 1, EstadoCampo.ENCONTRADO)), List.of());
        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.INSUFFICIENT_EVIDENCE,
                assess("LEGAL_REPRESENTATIVE", "Maria Fictícia Sobrenome", null, partial, LEGAL_DOC, CLOCK, SAO_PAULO)
                        .identityComparison());

        var conflict = result("LEGAL_REPRESENTATIVE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "Ana Souza", 1, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_LEGAL, "Bia Lima", 1, EstadoCampo.ENCONTRADO)), List.of());
        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.INSUFFICIENT_EVIDENCE,
                assess("LEGAL_REPRESENTATIVE", "Ana Souza", null, conflict, LEGAL_DOC, CLOCK, SAO_PAULO)
                        .identityComparison());
    }

    @Test
    void absentIdentityAndMissingRegisteredNameNeverBecomeVerified() {
        var noField = result("LEGAL_REPRESENTATIVE", List.of(), List.of());
        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.INSUFFICIENT_EVIDENCE,
                assess("LEGAL_REPRESENTATIVE", "Nome Fictício", null, noField, LEGAL_DOC, CLOCK, SAO_PAULO)
                        .identityComparison());
        var found = result("LEGAL_REPRESENTATIVE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "Nome Fictício", 1, EstadoCampo.ENCONTRADO)), List.of());
        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.INSUFFICIENT_EVIDENCE,
                assess("LEGAL_REPRESENTATIVE", null, null, found, LEGAL_DOC, CLOCK, SAO_PAULO)
                        .identityComparison());
    }

    @Test
    void delegateRequiresACompleteDocumentaryChainAndMatchingApplicant() {
        var result = result("AUTHORIZED_DELEGATE", List.of(
                field(Campo.NOME_ENTIDADE, "Associação Fictícia", 1, EstadoCampo.ENCONTRADO),
                field(Campo.CNPJ, "00000000000000", 1, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_LEGAL, "João de Souza", 1, EstadoCampo.ENCONTRADO),
                field(Campo.DELEGADO_AUTORIZADO, "Ana Maria Souza", 2, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_AUTORIZANTE, "João de Souza", 2, EstadoCampo.ENCONTRADO),
                field(Campo.ESCOPO_DELEGACAO, "Assinar atos de gestão", 2, EstadoCampo.ENCONTRADO),
                field(Campo.VALIDADE, "2026-11-14", 2, EstadoCampo.ENCONTRADO)), List.of());

        var assessment = assess("AUTHORIZED_DELEGATE", "Ana Maria de Souza", "Ana Souza", result,
                DELEGATE_DOCS, CLOCK, SAO_PAULO);

        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.MATCHES_REGISTERED_DATA,
                assessment.identityComparison());
        assertEquals(VerificacaoIdentidadeDocumental.DelegationEvidenceChain.CONSISTENT_DOCUMENTARY_CHAIN,
                assessment.delegationEvidenceChain());
        assertEquals(VerificacaoIdentidadeDocumental.ApparentValidity.APPARENTLY_CURRENT,
                assessment.apparentValidity(), "The expiry day remains current through the configured local date.");
    }

    @Test
    void delegateWithoutAuthorizerOrScopeHasIncompleteChainAndExpiredDateIsDetectedByInjectedClock() {
        var missing = result("AUTHORIZED_DELEGATE", List.of(
                field(Campo.DELEGADO_AUTORIZADO, "Ana Souza", 2, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_LEGAL, "João Souza", 1, EstadoCampo.ENCONTRADO),
                field(Campo.VALIDADE, "2026-11-13", 2, EstadoCampo.ENCONTRADO)), List.of());
        var missingAssessment = assess("AUTHORIZED_DELEGATE", "Ana Souza", null, missing,
                DELEGATE_DOCS, CLOCK, SAO_PAULO);
        assertEquals(VerificacaoIdentidadeDocumental.DelegationEvidenceChain.INCOMPLETE,
                missingAssessment.delegationEvidenceChain());
        assertEquals(VerificacaoIdentidadeDocumental.ApparentValidity.EXPIRED,
                missingAssessment.apparentValidity());

        var expired = result("AUTHORIZED_DELEGATE", List.of(
                field(Campo.DELEGADO_AUTORIZADO, "Ana Souza", 2, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_AUTORIZANTE, "João Souza", 2, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_LEGAL, "João Souza", 1, EstadoCampo.ENCONTRADO),
                field(Campo.ESCOPO_DELEGACAO, "Gestão", 2, EstadoCampo.ENCONTRADO),
                field(Campo.VALIDADE, "2026-11-13", 2, EstadoCampo.ENCONTRADO)), List.of());
        assertEquals(VerificacaoIdentidadeDocumental.ApparentValidity.EXPIRED,
                assess("AUTHORIZED_DELEGATE", "Ana Souza", null, expired, DELEGATE_DOCS, CLOCK, SAO_PAULO)
                        .apparentValidity());
    }

    @Test
    void authorizerMismatchAcrossDocumentsIsConflictingNotAnAutomaticFraudFinding() {
        var result = result("AUTHORIZED_DELEGATE", List.of(
                field(Campo.REPRESENTANTE_LEGAL, "João Souza", 1, EstadoCampo.ENCONTRADO),
                field(Campo.DELEGADO_AUTORIZADO, "Ana Souza", 2, EstadoCampo.ENCONTRADO),
                field(Campo.REPRESENTANTE_AUTORIZANTE, "Outra Pessoa", 2, EstadoCampo.ENCONTRADO),
                field(Campo.ESCOPO_DELEGACAO, "Gestão", 2, EstadoCampo.ENCONTRADO),
                field(Campo.VALIDADE, "2027-01-01", 2, EstadoCampo.ENCONTRADO)), List.of());

        var assessment = assess("AUTHORIZED_DELEGATE", "Ana Souza", null, result, DELEGATE_DOCS, CLOCK, SAO_PAULO);

        assertEquals(VerificacaoIdentidadeDocumental.IdentityComparison.MATCHES_REGISTERED_DATA,
                assessment.identityComparison());
        assertEquals(VerificacaoIdentidadeDocumental.DelegationEvidenceChain.CONFLICTING,
                assessment.delegationEvidenceChain());
    }

    private VerificacaoIdentidadeDocumental.Assessment assess(String type, String legal, String social,
            Resultado result, Map<Long, String> evidenceTypes, Clock clock, ZoneId zone) {
        return VerificacaoIdentidadeDocumental.assess(type, legal, social, result, evidenceTypes, clock, zone);
    }

    private Resultado result(String type, List<CampoExtraido> fields, List<Divergencia> discrepancies) {
        return new Resultado(CLOCK.instant(), Conclusao.EVIDENCIA_COMPATIVEL, fields, discrepancies,
                "gemini", "synthetic-test-model", 10, 5);
    }

    private CampoExtraido field(Campo field, String value, long evidenceId, EstadoCampo state) {
        return new CampoExtraido(field, state, value, List.of(new ReferenciaPagina(evidenceId, 1)));
    }
}
