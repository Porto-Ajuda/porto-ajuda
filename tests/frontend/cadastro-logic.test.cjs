const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");
async function importLogic(path) {
    const source = await read(path);
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("password validation recovers from a failed policy check, then a mismatch, in the same form", async () => {
    const { validarSenhasCadastro } = await importLogic("public/components/cadastro-logic.js");

    assert.deepEqual(validarSenhasCadastro("fraca", "fraca"), {
        erroSenha: "A senha precisa ter pelo menos 8 caracteres.",
        erroConfirmacao: ""
    });
    assert.deepEqual(validarSenhasCadastro("SenhaBoa1!", "diferente"), {
        erroSenha: "",
        erroConfirmacao: "As senhas não coincidem."
    });
    assert.deepEqual(validarSenhasCadastro("SenhaBoa1!", "SenhaBoa1!"), {
        erroSenha: "",
        erroConfirmacao: ""
    });

    const { criarTravaEnvioCadastro } = await importLogic("public/components/cadastro-logic.js");
    const trava = criarTravaEnvioCadastro();
    assert.equal(trava.tentarIniciar(), true);
    assert.equal(trava.tentarIniciar(), false, "envios paralelos precisam ser bloqueados");
    try {
        await Promise.reject(new Error("resposta de servidor simulada"));
    } catch {
        // A submissão termina em erro; o finally da interface deve liberar uma nova tentativa.
    } finally {
        trava.finalizar();
    }
    assert.equal(trava.tentarIniciar(), true, "a tentativa seguinte deve poder começar");
    trava.finalizar();

    const script = await read("public/components/cadastro.js");
    assert.match(script, /senhaCadastro\.addEventListener\("input",[\s\S]*?atualizarValidadeSenhas\(\)/);
    assert.match(script, /confirmarSenha\.addEventListener\("input", atualizarValidadeSenhas\)/);
    assert.match(script, /finally \{\s*travaEnvioCadastro\.finalizar\(\);\s*cad\.disabled = false;/);
    assert.match(script, /if \(!travaEnvioCadastro\.tentarIniciar\(\)\) return/);
});

test("CPF formatting accepts raw/formatted input and keeps the caret at the same digit in the middle", async () => {
    const { formatarCpfCadastro } = await importLogic("public/components/cadastro-logic.js");

    const pasted = formatarCpfCadastro("12345678901", 11);
    assert.equal(pasted.valor, "123.456.789-01");
    assert.equal(pasted.inicioSelecao, 14);

    const formatted = formatarCpfCadastro("123.456.789-01", 14);
    assert.equal(formatted.valor, "123.456.789-01");
    assert.equal(formatted.inicioSelecao, 14);

    const editedInMiddle = formatarCpfCadastro("123.956.789-01", 5);
    assert.equal(editedInMiddle.valor, "123.956.789-01");
    assert.equal(editedInMiddle.inicioSelecao, 5);

    const malformed = formatarCpfCadastro("123a456", 7);
    assert.equal(malformed.valido, false);
    assert.match(malformed.erro, /Use apenas números/);
});

test("registration email trims, completes a valid local part, and rejects incomplete addresses", async () => {
    const { normalizarEmailCadastro } = await importLogic("public/components/cadastro-logic.js");

    assert.deepEqual(normalizarEmailCadastro("  joana.silva  "), {
        valor: "joana.silva@gmail.com", valido: true, erro: "", completado: true
    });
    assert.deepEqual(normalizarEmailCadastro("  pessoa@exemplo.org.br  "), {
        valor: "pessoa@exemplo.org.br", valido: true, erro: "", completado: false
    });
    assert.equal(normalizarEmailCadastro("pessoa@intranet").valor, "pessoa@intranet");
    assert.deepEqual(normalizarEmailCadastro(""), {
        valor: "", valido: true, erro: "", completado: false
    });
    assert.equal(normalizarEmailCadastro("pessoa@").valido, false);
    assert.equal(normalizarEmailCadastro(".pessoa").valido, false);

    const [script, html] = await Promise.all([
        read("public/components/cadastro.js"),
        read("public/pages/login.html")
    ]);
    assert.match(html, /id="form-cadastro"[^>]*novalidate/);
    assert.match(html, /type="email"[^>]*id="email-cadastro"/);
    assert.match(script, /emailCadastro\.addEventListener\("blur",[\s\S]*normalizarEmailVisivel/);
    assert.match(script, /normalizarEmailVisivel\(\{ reportarErro: true \}\)/);
    assert.match(script, /email: emailCadastro\.value/);
});
