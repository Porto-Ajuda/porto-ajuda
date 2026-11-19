const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const ler = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("public volunteer viewer exposes participation only for available vacancy records", async () => {
    const script = await ler("public/components/conteudo-publico-modal.js");
    const html = await ler("public/pages/Porto-Ajuda.html");
    assert.match(script, /vaga\.status === "ABERTA"/);
    assert.match(script, /Number\(item\.vaga\.quantidadeVagas\) > 0/);
    assert.match(script, /encerramentoLocal >= hojeLocal/);
    assert.match(script, /window\.conteudoVoluntariadoAtual/);
    assert.match(script, /botaoCandidatar\.hidden = true/);
    assert.match(script, /porto:volunteer-viewer-open/);
    assert.match(await ler("public/components/candidaturas.js"), /\/voluntariado\/vagas\/\$\{encodeURIComponent\(item\.vaga\.id\)\}\/disponibilidade/);
    assert.match(html, /id="btn-modal-candidatar"[^>]+hidden/);
    assert.match(html, /id="dialogo-candidatura"/);
});

test("application submission confirms session and sends one CSRF-protected request without automatic retry", async () => {
    const source = await ler("public/components/candidaturas.js");
    assert.match(source, /fetchAutenticado\("\/usuario\/perfil"/);
    assert.match(source, /obterTokenCSRF\(\)/);
    assert.match(source, /method: "POST"/);
    assert.match(source, /\/voluntariado\/vagas\/\$\{encodeURIComponent\(oportunidadeConfirmada\.vaga\.id\)\}\/candidaturas/);
    assert.match(source, /if \(emEnvio \|\| !oportunidadeConfirmada/);
    assert.match(source, /window\.location\.assign\(`\/login\?retorno=/);
    assert.match(source, /\/minha-candidatura/);
    assert.match(source, /body: JSON\.stringify\(\{ mensagem: null \}\)/);
});

test("my applications UI requests only the authenticated user's page and offers pending cancellation", async () => {
    const source = await ler("public/components/minhas-candidaturas.js");
    const profile = await ler("public/pages/perfil.html");
    assert.match(profile, /id="aba-candidaturas"/);
    assert.match(profile, /id="painel-candidaturas"/);
    assert.match(source, /fetchAutenticado\(`\/usuario\/candidaturas\?page=/);
    assert.match(source, /item\.status === "PENDENTE"/);
    assert.match(source, /\/usuario\/candidaturas\/\$\{encodeURIComponent\(id\)\}\/cancelar/);
    assert.match(source, /item\.oportunidadePublica/);
});

test("application decisions and cancellation use the shared styled confirmation modal", async () => {
    const manager = await ler("public/components/candidaturas-ong.js");
    const mine = await ler("public/components/minhas-candidaturas.js");
    const modal = await ler("public/components/confirm-modal.js");
    const css = await ler("public/styles/candidaturas.css");
    assert.match(manager, /import \{ confirmarAcao \} from "\.\/confirm-modal\.js"/);
    assert.match(manager, /textoConfirmar: aprovado \? "Aprovar" : "Rejeitar"/);
    assert.match(mine, /textoConfirmar: "Cancelar candidatura"/);
    assert.doesNotMatch(manager, /window\.confirm/);
    assert.doesNotMatch(mine, /window\.confirm/);
    assert.match(modal, /export function confirmarAcao/);
    assert.match(modal, /modal\.showModal\(\)/);
    assert.match(modal, /evento\.key !== "Escape"/);
    assert.match(css, /\.modal-confirmacao-osc::backdrop/);
    assert.match(css, /\.modal-confirmacao-acoes \.btn-modal-confirmar/);
});

test("my application metadata is labeled and keeps the existing card and status badge", async () => {
    const source = await ler("public/components/minhas-candidaturas.js");
    const css = await ler("public/styles/candidaturas.css");
    const html = await ler("public/pages/perfil.html");
    assert.match(source, /linhaMeta\("Modalidade"/);
    assert.match(source, /linhaMeta\("Cidade"/);
    assert.match(source, /linhaMeta\("Bairro"/);
    assert.match(source, /item\.dataInicio\) details\.append\(linhaMeta/);
    assert.match(source, /item\.dataFim\) details\.append\(linhaMeta/);
    assert.match(source, /candidatura-usuario-card__data/);
    assert.match(source, /candidatura-status/);
    assert.match(css, /\.candidatura-usuario-card__metadados/);
    assert.match(html, /class="candidaturas-usuario__titulo"/);
});

test("donation amount buttons have styles in the stylesheet loaded by their page", async () => {
    const html = await ler("public/pages/apoie.html");
    const css = await ler("public/styles/apoie.css");
    assert.match(html, /href="\.\.\/styles\/apoie\.css"/);
    assert.match(css, /\.valor-pix,\s*#botao-outro/);
    assert.match(css, /\.valor-pix\.ativo/);
});

test("NGO applicant management reflects backend decision permission and uses specific NGO-scoped APIs", async () => {
    const source = await ler("public/components/candidaturas-ong.js");
    assert.match(source, /\/osc\/\$\{section\.dataset\.oscId \|\| getOscId\(\)\}\/candidaturas/);
    assert.match(source, /item\.status === "PENDENTE" && item\.podeDecidir/);
    assert.match(source, /body: JSON\.stringify\(\{ status \}\)/);
    assert.match(source, /response\.status === 409/);
    assert.match(source, /Atualize a lista para confirmar o estado antes de repetir/);
    assert.match(source, /\[401, 403, 404, 409\]\.includes\(response\.status\)/);
    assert.match(source, /await load\(button\.closest\("\[data-candidaturas-ong\]"\)\)/);
});

test("management can filter existing candidate flow by opportunity and records actual completion", async () => {
    const manager = await ler("public/components/candidaturas-ong.js");
    const profile = await ler("public/components/ong-profile.js");
    const html = await ler("public/pages/ong-profile.html");
    const migration = await ler("backend/src/main/resources/db/manual/20261121_volunteer_completion_dashboard.sql");
    assert.match(profile, /openOpportunityCandidates\(/);
    assert.match(manager, /query\.set\("vagaId", section\.dataset\.vagaId\)/);
    assert.match(manager, /CANCELADA_PELA_ONG/);
    assert.match(html, /id="aba-dashboard"[^>]+hidden/);
    assert.match(html, /id="dialogo-conclusao-voluntariado"/);
    assert.match(profile, /dataInicioReal: start, dataFimReal: end, participantes/);
    assert.match(profile, /canceledPendingCount/);
    assert.match(profile, /fetchAutenticado\(`\/osc\/\$\{oscAtual\.id\}\/candidaturas\/\$\{row\.dataset\.conclusaoApplication\}\/certificado`/);
    assert.match(migration, /certificado_object_key/);
    assert.match(migration, /CANCELADA_PELA_ONG/);
});

test("candidate management opens a filtered accessible modal in-place and refreshes slot counts", async () => {
    const manager = await ler("public/components/candidaturas-ong.js");
    const profile = await ler("public/components/ong-profile.js");
    const css = await ler("public/styles/candidaturas.css");
    assert.match(manager, /export function openOpportunityCandidates/);
    assert.match(manager, /dialog\.showModal\(\)/);
    assert.match(manager, /data-resumo-aceitos/);
    assert.match(manager, /data-resumo-disponiveis/);
    assert.match(manager, /data-resumo-pendentes/);
    assert.match(manager, /dialog\.close\(\)/);
    assert.match(manager, /trigger\.focus\(\)/);
    assert.match(manager, /requestedVacancy !== section\.dataset\.vagaId/);
    assert.match(manager, /onChanged\(\{ accepted, pending, available/);
    assert.match(manager, /porto:volunteer-capacity-changed/);
    assert.match(profile, /onFinalize: \(\) => abrirDialogoConclusao\(item\)/);
    assert.match(profile, /data-vagas-disponiveis/);
    assert.match(css, /\.dialogo-candidatos-oportunidade::backdrop/);
    assert.match(css, /\.candidatos-modal-resumo/);
});

test("completed candidates receive their persisted hours and private certificate link in their own history", async () => {
    const source = await ler("public/components/minhas-candidaturas.js");
    assert.match(source, /CONCLUIDA: "Concluída"/);
    assert.match(source, /Horas efetivamente realizadas/);
    assert.match(source, /usuario\/candidaturas\/\$\{encodeURIComponent\(item\.id\)\}\/certificado/);
});

test("applicant cancellation refreshes stale state after a 404 or 409 conflict", async () => {
    const source = await ler("public/components/minhas-candidaturas.js");
    assert.match(source, /response\.status === 404 \|\| response\.status === 409\) await carregar\(pagina\)/);
    assert.match(source, /A candidatura .+recebeu uma decis/);
});
