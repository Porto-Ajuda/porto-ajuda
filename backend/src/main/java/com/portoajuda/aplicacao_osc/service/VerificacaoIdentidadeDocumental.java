package com.portoajuda.aplicacao_osc.service;

import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Campo;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.CampoExtraido;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.EstadoCampo;
import com.portoajuda.aplicacao_osc.service.AnaliseRepresentacaoDocumental.Resultado;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Compares extracted names to locally registered names; it never verifies identity or authority. */
final class VerificacaoIdentidadeDocumental {
    private static final String LEGAL = "LEGAL_REPRESENTATIVE";
    private static final String DELEGATE = "AUTHORIZED_DELEGATE";
    private static final String REPRESENTATION_PROOF = "REPRESENTATION_PROOF";
    private static final String DELEGATION_AUTHORIZATION = "DELEGATION_AUTHORIZATION";
    private static final Set<String> NAME_PARTICLES = Set.of("da", "das", "de", "do", "dos", "e");

    private VerificacaoIdentidadeDocumental() { }

    static Assessment assess(String representationType, String applicantLegalName, String applicantSocialName,
                             Resultado result, Map<Long, String> evidenceTypes, Clock clock, ZoneId zone) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(evidenceTypes, "evidenceTypes");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(zone, "zone");
        if (!LEGAL.equals(representationType) && !DELEGATE.equals(representationType)) {
            return new Assessment(IdentityComparison.INSUFFICIENT_EVIDENCE,
                    DelegationEvidenceChain.NOT_APPLICABLE, ApparentValidity.UNVERIFIABLE, null);
        }

        String identityField = LEGAL.equals(representationType)
                ? "REPRESENTANTE_LEGAL" : "DELEGADO_AUTORIZADO";
        String identityEvidenceType = LEGAL.equals(representationType)
                ? REPRESENTATION_PROOF : DELEGATION_AUTHORIZATION;
        List<String> registeredNames = java.util.Arrays.asList(applicantLegalName, applicantSocialName).stream()
                .filter(Objects::nonNull).map(String::trim).filter(name -> !name.isBlank()).distinct().toList();
        NameEvidence identityEvidence = nameEvidence(result, identityField, identityEvidenceType, evidenceTypes);
        IdentityComparison identity = compareApplicantName(identityEvidence, registeredNames);

        String validityEvidenceType = LEGAL.equals(representationType)
                ? REPRESENTATION_PROOF : DELEGATION_AUTHORIZATION;
        Validity validity = assessValidity(result, validityEvidenceType, evidenceTypes, clock, zone);
        DelegationEvidenceChain chain = DELEGATE.equals(representationType)
                ? assessDelegateChain(result, identity, validity, evidenceTypes)
                : DelegationEvidenceChain.NOT_APPLICABLE;
        return new Assessment(identity, chain, validity.status(), validity.expiryDate());
    }

    private static IdentityComparison compareApplicantName(NameEvidence evidence, List<String> registeredNames) {
        if (evidence.conflicting() || evidence.names().isEmpty() || registeredNames.isEmpty()) {
            return IdentityComparison.INSUFFICIENT_EVIDENCE;
        }
        List<String> normalizedEvidence = evidence.names().stream().map(VerificacaoIdentidadeDocumental::normalize)
                .filter(value -> !value.isBlank()).distinct().toList();
        if (normalizedEvidence.size() != 1) return IdentityComparison.INSUFFICIENT_EVIDENCE;
        String extracted = normalizedEvidence.getFirst();
        List<String> normalizedRegistered = registeredNames.stream().map(VerificacaoIdentidadeDocumental::normalize)
                .filter(value -> !value.isBlank()).distinct().toList();
        if (normalizedRegistered.stream().anyMatch(name -> sameCompleteName(extracted, name))) {
            return IdentityComparison.MATCHES_REGISTERED_DATA;
        }
        if (normalizedRegistered.stream().anyMatch(name -> isPartialName(extracted, name))) {
            return IdentityComparison.INSUFFICIENT_EVIDENCE;
        }
        return IdentityComparison.MISMATCH;
    }

    private static NameEvidence nameEvidence(Resultado result, String fieldName, String expectedDocumentType,
                                             Map<Long, String> evidenceTypes) {
        List<CampoExtraido> fields = fields(result, fieldName);
        if (fields.isEmpty() && "REPRESENTANTE_LEGAL".equals(fieldName)) {
            // Read-only compatibility for V1 summaries/provider responses.
            fields = fields(result, "REPRESENTANTE");
        }
        if (fields.isEmpty()) return new NameEvidence(List.of(), false);
        List<String> names = new ArrayList<>();
        boolean conflicting = false;
        for (CampoExtraido field : fields) {
            if (field.estado() == EstadoCampo.CONFLITANTE) {
                conflicting = true;
                continue;
            }
            if (field.estado() != EstadoCampo.ENCONTRADO || field.extractedValue() == null
                    || field.extractedValue().isBlank()) continue;
            boolean correctlyReferenced = field.referencias().stream()
                    .anyMatch(reference -> expectedDocumentType.equals(evidenceTypes.get(reference.evidenceId())));
            if (!correctlyReferenced) continue;
            names.add(field.extractedValue());
        }
        Set<String> distinct = names.stream().map(VerificacaoIdentidadeDocumental::normalize)
                .filter(value -> !value.isBlank()).collect(Collectors.toSet());
        if (distinct.size() > 1) conflicting = true;
        return new NameEvidence(List.copyOf(names), conflicting);
    }

    private static Validity assessValidity(Resultado result, String expectedDocumentType,
                                           Map<Long, String> evidenceTypes, Clock clock, ZoneId zone) {
        List<CampoExtraido> fields = fields(result, "VALIDADE");
        List<LocalDate> dates = new ArrayList<>();
        for (CampoExtraido field : fields) {
            if (field.estado() != EstadoCampo.ENCONTRADO || field.extractedValue() == null
                    || field.referencias().stream().noneMatch(reference ->
                    expectedDocumentType.equals(evidenceTypes.get(reference.evidenceId())))) continue;
            try {
                dates.add(LocalDate.parse(field.extractedValue().trim()));
            } catch (DateTimeParseException ignored) {
                return new Validity(ApparentValidity.UNVERIFIABLE, null);
            }
        }
        List<LocalDate> distinct = dates.stream().distinct().toList();
        if (distinct.size() != 1) return new Validity(ApparentValidity.UNVERIFIABLE, null);
        LocalDate expiry = distinct.getFirst();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return new Validity(expiry.isBefore(today) ? ApparentValidity.EXPIRED : ApparentValidity.APPARENTLY_CURRENT,
                expiry);
    }

    private static DelegationEvidenceChain assessDelegateChain(Resultado result, IdentityComparison applicantIdentity,
                                                               Validity validity, Map<Long, String> evidenceTypes) {
        if (applicantIdentity == IdentityComparison.MISMATCH) return DelegationEvidenceChain.CONFLICTING;
        if (applicantIdentity != IdentityComparison.MATCHES_REGISTERED_DATA
                || validity.status() != ApparentValidity.APPARENTLY_CURRENT) {
            return DelegationEvidenceChain.INCOMPLETE;
        }

        NameEvidence legalRepresentative = nameEvidence(result, "REPRESENTANTE_LEGAL", REPRESENTATION_PROOF, evidenceTypes);
        NameEvidence authorizer = nameEvidence(result, "REPRESENTANTE_AUTORIZANTE", DELEGATION_AUTHORIZATION, evidenceTypes);
        if (legalRepresentative.conflicting() || authorizer.conflicting()) return DelegationEvidenceChain.CONFLICTING;
        if (legalRepresentative.names().size() != 1 || authorizer.names().size() != 1
                || !sameCompleteName(normalize(legalRepresentative.names().getFirst()),
                        normalize(authorizer.names().getFirst()))) {
            if (!legalRepresentative.names().isEmpty() && !authorizer.names().isEmpty()) {
                return DelegationEvidenceChain.CONFLICTING;
            }
            return DelegationEvidenceChain.INCOMPLETE;
        }
        boolean scopePresent = hasFoundFieldFrom(result, "ESCOPO_DELEGACAO", DELEGATION_AUTHORIZATION, evidenceTypes);
        boolean ngoNamePresent = hasFoundFieldFrom(result, "NOME_ENTIDADE", REPRESENTATION_PROOF, evidenceTypes);
        boolean cnpjPresent = hasFoundFieldFrom(result, "CNPJ", REPRESENTATION_PROOF, evidenceTypes);
        return scopePresent && ngoNamePresent && cnpjPresent
                ? DelegationEvidenceChain.CONSISTENT_DOCUMENTARY_CHAIN
                : DelegationEvidenceChain.INCOMPLETE;
    }

    private static boolean hasFoundFieldFrom(Resultado result, String fieldName, String documentType,
                                             Map<Long, String> evidenceTypes) {
        return fields(result, fieldName).stream().anyMatch(field -> field.estado() == EstadoCampo.ENCONTRADO
                && field.referencias().stream().anyMatch(reference -> documentType.equals(evidenceTypes.get(reference.evidenceId()))));
    }

    private static List<CampoExtraido> fields(Resultado result, String name) {
        return result.camposExtraidos().stream().filter(field -> field.campo().name().equals(name)).toList();
    }

    private static String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
        return Arrays.stream(decomposed.split("\\s+"))
                .filter(token -> !token.isBlank() && !NAME_PARTICLES.contains(token))
                .sorted(Comparator.naturalOrder()).collect(Collectors.joining(" "));
    }

    private static boolean sameCompleteName(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }

    private static boolean isPartialName(String extracted, String registered) {
        List<String> left = tokens(extracted);
        List<String> right = tokens(registered);
        if (left.size() < 2 || right.size() < 2) return true;
        return (right.containsAll(left) || left.containsAll(right)) && !left.equals(right);
    }

    private static List<String> tokens(String normalized) {
        return Arrays.stream(normalized.split("\\s+")).filter(token -> !token.isBlank()).toList();
    }

    enum IdentityComparison { MATCHES_REGISTERED_DATA, MISMATCH, INSUFFICIENT_EVIDENCE, INDEPENDENTLY_VERIFIED }
    enum DelegationEvidenceChain { NOT_APPLICABLE, CONSISTENT_DOCUMENTARY_CHAIN, INCOMPLETE, CONFLICTING }
    enum ApparentValidity { NOT_APPLICABLE, APPARENTLY_CURRENT, EXPIRED, UNVERIFIABLE }
    record Assessment(IdentityComparison identityComparison, DelegationEvidenceChain delegationEvidenceChain,
                      ApparentValidity apparentValidity, LocalDate expiryDate) { }
    private record NameEvidence(List<String> names, boolean conflicting) { }
    private record Validity(ApparentValidity status, LocalDate expiryDate) { }
}
