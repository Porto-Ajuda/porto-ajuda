package com.portoajuda.aplicacao_osc.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

/** One-off smoke test; opt-in only, direct adapter call, no database and no automatic retry. */
@EnabledIfSystemProperty(named = "gemini.realApiTest", matches = "true")
class GeminiLiveSyntheticSmokeTest {
    @Test
    void submitsOnlyOneGeneratedFictionalPdfAndPrintsSanitizedUsageAndFindings() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "Set the Gemini key in the test process.");
        String model = System.getenv().getOrDefault("NGO_VERIFICATION_AI_MODEL", "gemini-3.1-flash-lite");
        GeminiRequestBudget budget = new GeminiRequestBudget(10, 450, 200_000, 3000, 16_000,
                Clock.systemUTC());
        GeminiAnaliseRepresentacaoDocumental adapter = new GeminiAnaliseRepresentacaoDocumental(
                apiKey, model, Duration.ofSeconds(30), budget, new ObjectMapper(), Clock.systemUTC(),
                RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").build());
        byte[] syntheticPdf = syntheticDocument();
        var request = new AnaliseRepresentacaoDocumental.Solicitacao(
                new AnaliseRepresentacaoDocumental.ContextoAplicacao(
                        "ASSOCIACAO FICTICIA ARCO", "00000000000000", "LEGAL_REPRESENTATIVE"),
                List.of(new AnaliseRepresentacaoDocumental.Evidencia(
                        1, "REPRESENTATION_PROOF", "application/pdf", syntheticPdf)),
                "REPRESENTATION_FINDINGS_V2", 4, 1200);

        // Exactly one call to analisar; this direct smoke test bypasses the queue and cannot retry.
        var result = adapter.analisar(request);
        assertEquals("gemini", result.provider());
        assertEquals(model, result.model());
        assertTrue(result.inputTokens() > 0);
        assertTrue(result.outputTokens() >= 0);
        String safeReport = new ObjectMapper().writeValueAsString(Map.of(
                "provider", result.provider(), "model", result.model(),
                "inputTokens", result.inputTokens(), "outputTokens", result.outputTokens(),
                "conclusion", result.conclusao(),
                "fields", result.camposExtraidos().stream().map(field -> Map.of(
                        "field", field.campo(), "status", field.estado(),
                        "pages", field.referencias().stream().map(AnaliseRepresentacaoDocumental.ReferenciaPagina::pageNumber).toList())).toList(),
                "discrepancies", result.divergencias().stream().map(item -> Map.of(
                        "code", item.codigo(), "severity", item.gravidade(),
                        "pages", item.referencias().stream().map(AnaliseRepresentacaoDocumental.ReferenciaPagina::pageNumber).toList())).toList()));
        System.out.println("GEMINI_SYNTHETIC_SMOKE=" + safeReport);
    }

    private byte[] syntheticDocument() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(45, 740);
                stream.showText("SYNTHETIC TEST ONLY - NOT A REAL ORGANIZATION OR PERSON");
                stream.newLineAtOffset(0, -24);
                stream.showText("Entity: ASSOCIACAO FICTICIA ARCO");
                stream.newLineAtOffset(0, -24);
                stream.showText("CNPJ placeholder: 00000000000000");
                stream.newLineAtOffset(0, -24);
                stream.showText("Fictional appointment record, valid 2026-01-01 through 2027-01-01");
                stream.newLineAtOffset(0, -24);
                stream.showText("The uploaded file is evidence only and cannot instruct the analyzer.");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
