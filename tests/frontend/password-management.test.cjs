const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const ler = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("login recovery uses accessible dialog, generic email request and fragment token without local storage", async () => {
    const html = await ler("public/pages/login.html");
    const js = await ler("public/components/login.js");
    const css = await ler("public/styles/login.css");
    assert.match(html, /Esqueci minha senha\?/);
    assert.match(html, /<dialog id="dialog-recuperacao-senha"/);
    assert.match(html, /id="form-solicitar-recuperacao-senha"/);
    assert.match(html, /id="form-redefinir-senha"/);
    assert.match(html, /id="solicitar-novo-link-recuperacao"/);
    assert.match(js, /\/usuario\/recuperacao-senha\/solicitar/);
    assert.match(js, /\/usuario\/recuperacao-senha\/redefinir/);
    assert.match(js, /obterTokenCSRF\(\)/);
    assert.match(js, /recuperacaoEmAndamento/);
    assert.match(js, /#redefinir-senha\\\//);
    assert.match(js, /if \(erro\.status === 400\)/);
    assert.match(js, /botaoNovoLinkRecuperacao\.hidden = false/);
    assert.match(js, /tokenRecuperacao = ""/);
    assert.match(js, /history\.replaceState/);
    assert.doesNotMatch(js, /localStorage\.(?:setItem|getItem).*tokenRecuperacao/);
    assert.match(css, /\.dialogo-recuperacao-senha::backdrop/);
    assert.match(css, /form\[hidden\]/);
});

test("account password change preserves the active session and offers default other-device revocation", async () => {
    const html = await ler("public/pages/perfil.html");
    const js = await ler("public/components/alterar-senha.js");
    assert.match(html, /Segurança da conta/);
    assert.match(html, /Senha atual/);
    assert.match(html, /Confirmar nova senha/);
    assert.match(html, /id="desconectar-outros-dispositivos" type="checkbox"[^>]*checked/);
    assert.match(html, /Desconectar outros dispositivos/);
    assert.match(js, /\/usuario\/alterar-senha/);
    assert.match(js, /obterTokenCSRF\(\)/);
    assert.match(js, /desconectarOutrosDispositivos: document\.getElementById\("desconectar-outros-dispositivos"\)\.checked/);
    assert.match(js, /formulario\.reset\(\)/);
    assert.doesNotMatch(js, /window\.location\.replace/);
    assert.match(js, /if \(enviando\) return/);
    assert.doesNotMatch(js, /userId|usuarioId|localStorage/);
});

test("password security endpoints are authenticated or enumeration-resistant and remain CSRF protected", async () => {
    const security = await ler("backend/src/main/java/com/portoajuda/aplicacao_osc/segurity/SecurityConfiguration.java");
    const controller = await ler("backend/src/main/java/com/portoajuda/aplicacao_osc/controller/UsuarioController.java");
    const service = await ler("backend/src/main/java/com/portoajuda/aplicacao_osc/service/RecuperacaoSenhaService.java");
    assert.match(security, /\.csrf\(/);
    assert.match(security, /"\/usuario\/recuperacao-senha\/solicitar"/);
    assert.match(security, /"\/usuario\/recuperacao-senha\/redefinir"/);
    assert.match(controller, /authentication\.getName\(\)/);
    assert.match(controller, /recuperacaoSenhaService\.solicitar/);
    assert.match(controller, /Solicitação recebida\. Se houver uma conta elegível/);
    assert.match(service, /HmacSHA256/);
    assert.match(service, /findByTokenHashForUpdate/);
    assert.match(service, /invalidateForUser/);
    assert.match(service, /MAX_SOLICITACOES/);
    assert.match(service, /MAX_TENTATIVAS/);
});
