const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");
async function importLogic(path) {
    const source = await read(path);
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("moderation visibility retains zero-score history and hides truly empty personal records", async () => {
    const { possuiDadosDeModeracao, possuiHistoricoModeracaoOng } = await importLogic("public/components/moderation-profile-logic.js");
    assert.equal(possuiDadosDeModeracao({ score: 0, pointsHistory: [], appeals: [], temporaryRestrictionActive: false }), false);
    assert.equal(possuiDadosDeModeracao({ score: 0, pointsHistory: [{ delta: -2 }], appeals: [] }), true);
    assert.equal(possuiDadosDeModeracao({ score: 0, pointsHistory: [], appeals: [{ status: "ACCEPTED" }] }), true);
    assert.equal(possuiDadosDeModeracao({ score: 0, pointsHistory: [], appeals: [], permanentRestrictionActive: true }), true);
    assert.equal(possuiHistoricoModeracaoOng({ score: 0, hasPointHistory: true }), true);
    assert.equal(possuiHistoricoModeracaoOng({ score: 0, pointsHistory: [], appeals: [], temporaryRestrictionActive: true }), true);
    assert.equal(possuiHistoricoModeracaoOng({ score: 0, hasPointHistory: false, pointsHistory: [], appeals: [] }), false);
});

test("profile separates the NGO accordion, keeps it collapsed, and omits empty history/appeal rows", async () => {
    const [html, script, css] = await Promise.all([
        read("public/pages/perfil.html"),
        read("public/components/moderation-profile.js"),
        read("public/styles/perfil.css")
    ]);
    const personalEnd = html.indexOf("</section>", html.indexOf('id="moderacao-perfil-usuario"'));
    const ngoAccordion = html.indexOf('id="moderacao-organizacoes-usuario"');
    assert.ok(personalEnd >= 0 && ngoAccordion > personalEnd);
    assert.match(html.slice(ngoAccordion, ngoAccordion + 230), /\shidden>/);
    assert.match(script, /section\.hidden = !visible/);
    assert.match(script, /if \(historyEntries\.length\)/);
    assert.match(script, /if \(appeals\.length\)/);
    assert.doesNotMatch(script, /if \(!list\.children\.length\)|Nenhum recurso enviado\./);
    assert.doesNotMatch(script, /\bc\(0xb7\)/);
    assert.match(script, /void carregarResumoOrganizacoes\(organizations, state\)/);
    assert.match(css, /\.moderation-profile__organizations > summary \{ display:flex; width:100%; min-height:50px/);
});

test("profile photo removal reports actionable failures and confirms uncertain responses from the owner profile", async () => {
    const { mensagemErroRemocaoFoto, fotoRemovidaNoPerfil } = await importLogic("public/components/perfil-foto-logic.js");
    assert.match(mensagemErroRemocaoFoto(403), /recusou a solicitação/);
    assert.match(mensagemErroRemocaoFoto(404), /não reconhece a remoção/);
    assert.match(mensagemErroRemocaoFoto(503), /Confira o perfil/);
    assert.match(mensagemErroRemocaoFoto(0), /Confira sua conexão/);
    assert.equal(fotoRemovidaNoPerfil({ fotoPerfilUrl: null }), true);
    assert.equal(fotoRemovidaNoPerfil({ fotoPerfilUrl: "https://images.invalid/legacy.jpg" }), false);
    const [profile, controller, service, header] = await Promise.all([
        read("public/components/perfil.js"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/controller/UsuarioController.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/service/UsuarioService.java"),
        read("public/components/header_footer.js")
    ]);
    assert.match(profile, /fetchAutenticado\("\/usuario\/foto-perfil", \{ method: "DELETE" \}\)/);
    assert.match(profile, /fetchAutenticado\("\/usuario\/perfil"/);
    assert.match(profile, /perfil:atualizado/);
    assert.match(controller, /@DeleteMapping\("\/foto-perfil"\)/);
    assert.match(controller, /usuarioService\.removerFotoPerfil\(Integer\.valueOf\(authentication\.getName\(\)\)\)/);
    assert.match(service, /usuario\.setFotoPerfilUrl\(null\)/);
    assert.match(service, /afterCommitIfUnreferenced\(anterior\)/);
    assert.match(header, /perfil:atualizado/);
});

test("profile accessibility controls use the shared applied font-scale preferences", async () => {
    const { TAMANHOS_FONTE, normalizarTamanhoFonte, nivelTamanhoFonte } = await importLogic("public/components/acessibilidade-logic.js");
    assert.deepEqual(TAMANHOS_FONTE, { pequeno: 90, medio: 100, grande: 120, "extra-grande": 140 });
    assert.equal(normalizarTamanhoFonte(140), 140);
    assert.equal(normalizarTamanhoFonte(999), 150);
    assert.equal(nivelTamanhoFonte(120), "grande");
    const [profile, accessibility, css] = await Promise.all([
        read("public/components/perfil.js"),
        read("public/components/acessibilidade.js"),
        read("public/styles/ajuste-imagem.css")
    ]);
    assert.match(profile, /definirTamanhoFonte\(tamanho,\s*\{ persistir: false \}\)/);
    assert.match(profile, /definirTamanhoFonte\(tamanhoFonte,\s*\{ persistir: false \}\)/);
    assert.match(accessibility, /document\.documentElement\.style\.fontSize/);
    assert.match(accessibility, /export function definirTamanhoFonte/);
    assert.match(accessibility, /input\[name="acss-tamanho-letra"\].*opcao\.checked/s);
    assert.match(css, /height: fit-content/);
    assert.match(css, /position: fixed;\s+inset: 0;\s+transform: none/);
    assert.match(css, /max-height: calc\(100dvh - 24px\)/);
    assert.match(css, /overflow-wrap: anywhere/);
});

test("activity tabs change availability only after valid owner-scoped totals and preserve a visible fallback", async () => {
    const [history, applications, tabs, html] = await Promise.all([
        read("public/components/perfil-historico.js"),
        read("public/components/minhas-candidaturas.js"),
        read("public/components/abas-perfil.js"),
        read("public/pages/perfil.html")
    ]);
    assert.match(history, /definirAbaDisponivel\("aba-registro", visibility\.hasRecords\)/);
    assert.match(history, /if \(!response\.ok\) throw new Error/);
    assert.match(applications, /definirAbaDisponivel\("aba-candidaturas", totalElementos > 0\)/);
    assert.match(applications, /if \(!Number\.isSafeInteger\(totalElementos\) \|\| totalElementos < 0\)/);
    assert.match(applications, /void carregar\(0\)/);
    assert.match(applications, /porto:application-created/);
    assert.match(tabs, /const fallback = Array\.from\(document\.querySelectorAll\("\.abas-perfil \.aba:not\(\[hidden\]\)"\)\)/);
    assert.match(tabs, /const visiveis = abas\.filter\(aba => !aba\.hidden\)/);
    assert.match(html, /id="aba-acss"[\s\S]*?id="aba-configuracoes"/);
    assert.doesNotMatch(html, /id="aba-acss"[^>]*hidden|id="aba-configuracoes"[^>]*hidden/);
});

test("toasts support stacked status/error roles, close controls, and automatic versus persistent lifetimes", async () => {
    const [toast, css] = await Promise.all([
        read("public/components/notificacoes.js"),
        read("public/styles/notificacoes.css")
    ]);
    assert.match(toast, /display|append\(toast\)/);
    assert.match(toast, /role", tipo === "erro" \? "alert" : "status"/);
    assert.match(toast, /aria-live", tipo === "erro" \? "assertive" : "polite"/);
    assert.match(toast, /tipo !== "erro"/);
    assert.match(toast, /Fechar notificação/);
    assert.match(css, /position: fixed/);
    assert.match(css, /#porto-ajuda-toast-region/);
    assert.match(css, /prefers-reduced-motion: reduce/);
});

test("profile status copy no longer contains the email restriction and toasts use stacked accessible semantics", async () => {
    const [html, toast, css, confirmation] = await Promise.all([
        read("public/pages/perfil.html"),
        read("public/components/notificacoes.js"),
        read("public/styles/notificacoes.css"),
        read("public/components/confirm-modal.js")
    ]);
    assert.match(html, /id="email"[^>]*disabled/);
    assert.doesNotMatch(html, /O e-mail da conta não pode ser alterado|The account email cannot be changed/i);
    assert.doesNotMatch(html, /id="estado-(?:edicao-perfil|logout|exclusao-conta)"/);
    assert.match(toast, /porto-ajuda-toast-region/);
    assert.match(toast, /setAttribute\("role", tipo === "erro" \? "alert" : "status"\)/);
    assert.match(toast, /tipo !== "erro"/);
    assert.match(toast, /aria-label", "Fechar notificação"/);
    assert.match(css, /position: fixed/);
    assert.match(css, /display: grid/);
    assert.match(css, /prefers-reduced-motion: reduce/);
    assert.match(confirmation, /notificarToast/);
});

test("voice assistance is presented below VLibras as a site switch with retained conditional options", async () => {
    const [html, support] = await Promise.all([
        read("public/pages/perfil.html"),
        read("public/components/support-assistant.js")
    ]);
    const libras = html.indexOf('id="acss-libras"');
    const voice = html.indexOf('id="preferencia-assistencia-voz"');
    const font = html.indexOf('name="acss-tamanho-letra"');
    const options = html.slice(html.indexOf('id="preferencias-voz-opcoes"'), font);
    assert.ok(libras >= 0 && libras < voice && voice < font);
    assert.match(html, /preferencia-assistencia-voz"[^>]*role="switch"/);
    assert.match(html, /aria-labelledby="preferencias-voz-titulo"/);
    assert.match(html, /class="acss-configuracao" aria-labelledby="preferencias-voz-titulo"/);
    assert.match(options, /id="preferencia-leitura-voz"/);
    assert.match(options, /id="preferencia-evitar-leitor-tela"/);
    assert.match(options, /id="preferencia-configurar-voz"/);
    assert.match(html, /id="preferencias-voz-opcoes" class="preferencias-voz-opcoes" hidden/);
    assert.match(support, /profileVoiceOptions\.hidden = !voicePreference\.enabled/);
    assert.match(support, /preferencia-evitar-leitor-tela/);
    assert.match(support, /persistVoicePreference\(\)/);
});
