const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");

const read = file => readFile(resolve(__dirname, "../..", file), "utf8");

test("registration accepts only canonical or conventionally formatted CPF before digit submission", async () => {
    const script = await read("public/components/cadastro.js");
    const validator = script.match(/function validarCPF\(valor\) \{[\s\S]*?\n    \}/)?.[0] ?? "";
    assert.match(validator, /\^\[0-9\]\{3\}\\\.\[0-9\]\{3\}\\\.\[0-9\]\{3\}-\[0-9\]\{2\}\$/);
    assert.match(validator, /\^\[0-9\]\{11\}\$/);
    assert.doesNotMatch(validator, /somenteNumeros\(valor\)/);
    assert.match(script, /Use apenas números ou a pontuação padrão do CPF/);
    assert.match(script, /prefixoPontuadoValido\.test\(entrada\)/);
    assert.match(script, /cpf:\s*somenteNumeros\(cpf\.value\)/);
});

test("profile displays the immutable registered email without enabling its editor", async () => {
    const html = await read("public/pages/perfil.html");
    assert.match(html, /id="email"[^>]*disabled/);
    assert.match(html, /O e-mail da conta não pode ser alterado\./);
});
