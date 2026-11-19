const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("public NGO, publication, and volunteer viewers open the shared CSRF-protected report form", async () => {
    const [report, home, profile, profileHtml, catalog, sharedApi] = await Promise.all([
        read("public/components/denunciar-conteudo.js"),
        read("public/components/conteudo-publico-modal.js"),
        read("public/components/ong-profile.js"),
        read("public/pages/ong-profile.html"),
        read("public/pages/catalogo.html"),
        read("public/components/api.js")
    ]);
    assert.match(profile, /data-report-type|dataset\.reportType = oportunidade \? "VOLUNTARIADO" : "PUBLICACAO"/);
    assert.match(profile, /dataset\.reportType = oportunidade \? "VOLUNTARIADO" : "PUBLICACAO"/);
    assert.match(profileHtml, /data-content-report[^>]+data-report-type="OSC"/);
    assert.match(home, /dataset\.reportType = tipo === "voluntariado" \? "VOLUNTARIADO" : "PUBLICACAO"/);
    assert.match(report, /fetchAutenticado\("\/denuncias"/);
    assert.match(report, /Você já possui uma denúncia pendente/);
    assert.match(report, /disponível 7 dias após a última análise/);
    assert.match(report, /knownConflict/);
    assert.match(sharedApi, /obterTokenCSRF/);
    assert.match(report, /dialogo\.showModal\(\)/);
    assert.match(report, /role="status"/);
    assert.match(catalog, /components\/denunciar-conteudo\.js/);
});

test("admin report UI scopes actions by existing permissions and safely renders report text", async () => {
    const [admin, html, styles] = await Promise.all([
        read("public/components/admin.js"),
        read("public/admin/index.html"),
        read("public/styles/admin.css")
    ]);
    assert.match(admin, /permission: "REPORT_READ"/);
    assert.match(admin, /authorization\.hasPermission\("REPORT_REVIEW"\)/);
    assert.match(admin, /authorization\.hasPermission\("REPORT_RESOLVE"\)/);
    assert.match(admin, /authorization\.hasPermission\("CONTENT_REACTIVATE"\)/);
    assert.match(admin, /explanation\.textContent = report\.justificativa/);
    assert.match(admin, /fetch\(`\$\{API_URL\}\/admin\/denuncias/);
    assert.match(html, /id="denuncias-admin"/);
    assert.match(html, /value="ACTIVE"/);
    assert.match(styles, /\.admin-report-detail__content[^\{]*\{[^}]*overflow:auto/s);
    assert.match(styles, /@media\(max-width:650px\)[^\{]*\{[^}]*admin-report-card/s);
});

test("admin report detail translates every stored reason and status without changing API values", async () => {
    const admin = await read("public/components/admin.js");
    for (const [value, label] of [
        ["FRAUD", "Fraude"], ["MISLEADING", "Informação enganosa"],
        ["INAPPROPRIATE", "Conteúdo inadequado"], ["PRIVACY", "Exposição de dados pessoais"], ["OTHER", "Outro"],
        ["PENDING", "Pendente"], ["DISMISSED", "Descartada"], ["UPHELD", "Procedente"]
    ]) {
        assert.match(admin, new RegExp(`${value}: "${label}"`));
    }
    assert.match(admin, /reason\.textContent = rotulosMotivoDenuncia\[report\.motivo\] \|\| "Outro motivo"/);
    assert.match(admin, /reportStatus\.textContent = ` · \$\{rotulosStatusDenuncia\[report\.status\] \|\| "Status não reconhecido"\}`/);
});

test("public report dialog requires a description and retains entered values after request errors", async () => {
    const [report, styles, request] = await Promise.all([
        read("public/components/denunciar-conteudo.js"),
        read("public/styles/denunciar-conteudo.css"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/dto/request/RequestContentReportDTO.java")
    ]);
    assert.match(report, /Descrição do motivo/);
    assert.match(report, /Descreva o que aconteceu para ajudar na análise\./);
    assert.match(report, /id="detalhes-denuncia-conteudo" required maxlength="1000"/);
    assert.match(report, /if \(!details \|\| details\.length > 1000\)/);
    assert.match(report, /status\.textContent = "Denúncia enviada para análise\."/);
    assert.match(report, /submit\.textContent = "Enviando…"/);
    assert.match(report, /finally \{\s*submit\.disabled = false;/);
    assert.match(request, /@NotBlank @Size\(max = 1000\) String justificativa/);
    assert.match(styles, /\.dialogo-denuncia-conteudo__cartao \.botao-primario/);
    assert.match(styles, /\.dialogo-denuncia-conteudo__cartao \.botao-secundario/);
});
