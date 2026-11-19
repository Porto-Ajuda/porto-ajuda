const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");

const read = file => readFile(resolve(__dirname, "../..", file), "utf8");

test("registration validates conventional CPF and sends its eleven canonical digits", async () => {
    const script = await read("public/components/cadastro.js");
    const validator = script.match(/function validarCPF\(valor\) \{[\s\S]*?\n    \}/)?.[0] ?? "";
    assert.match(validator, /\^\[0-9\]\{3\}\\\.\[0-9\]\{3\}\\\.\[0-9\]\{3\}-\[0-9\]\{2\}\$/);
    assert.match(validator, /\^\[0-9\]\{11\}\$/);
    assert.doesNotMatch(validator, /somenteNumeros\(valor\)/);
    assert.match(script, /formatarCpfCadastro\(/);
    assert.match(script, /cpf\.setSelectionRange\(resultado\.inicioSelecao, resultado\.fimSelecao\)/);
    assert.match(script, /cpf:\s*somenteNumeros\(cpf\.value\)/);
});

test("profile keeps the registered email visible and read-only without explanatory copy", async () => {
    const html = await read("public/pages/perfil.html");
    assert.match(html, /id="email"[^>]*disabled/);
    assert.doesNotMatch(html, /O e-mail da conta não pode ser alterado|The account email cannot be changed/i);
});
