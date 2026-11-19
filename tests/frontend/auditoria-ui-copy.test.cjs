const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("confirmed public copy and legacy NGO access instructions now match the user account flow", async () => {
    const [login, home, header, tour, legacy] = await Promise.all([
        read("public/pages/login.html"), read("public/pages/Porto-Ajuda.html"),
        read("public/components/header_footer.js"), read("public/components/tour.js"),
        read("public/pages/login-osc.html")
    ]);
    assert.match(login, /Ainda não possui uma conta\?/);
    assert.doesNotMatch(login, /Ainda n\?o possui/);
    assert.match(home, />\s*ONGs\s*</);
    assert.doesNotMatch(home, /ONG's/);
    assert.doesNotMatch(header, /ONG's Regionais/);
    assert.match(header, /ONGs da região/);
    assert.doesNotMatch(tour, /ONG's em destaque/);
    assert.doesNotMatch(legacy, /id="form-login-osc"|login-cnpj|login-senha|login-osc\.js/);
    assert.match(legacy, /href="\.\.\/login"/);
});

test("known user-facing transport failures do not expose raw HTTP status text", async () => {
    const files = ["public/components/perfil.js", "public/components/contato.js", "public/components/acesso-admin.js",
        "public/components/confirmacao-email.js", "public/components/logout.js", "public/components/login.js",
        "public/components/catalogo.js", "public/components/minhas-candidaturas.js", "public/components/candidaturas-ong.js",
        "public/components/home-showcases.js", "public/components/proximity.js", "public/components/auth.js"];
    const sources = await Promise.all(files.map(read));
    assert.equal(sources.some(source => /HTTP \$\{(?:response|resposta|status)\.status\}/.test(source)), false);
    const access = sources[2];
    assert.doesNotMatch(access, /AUTHENTICATION REQUIRED|LEVEL 01|SYS:\/\/UNKNOWN|Sua presen\w+ nesta \w+ foi registrada/);
    const accessibility = await read("public/components/acessibilidade.js");
    assert.match(accessibility, /if \(resposta\.status === 401\) return false/,
        "anonymous local accessibility preferences are a normal fallback, not a console error");
});
