const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

async function labels() {
    const source = await readFile(resolve(__dirname, "../../public/components/moderation-labels.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("moderation entity states and origins are rendered as Portuguese labels with safe fallbacks", async () => {
    const { labelModerationStatus, labelModerationOrigin } = await labels();
    assert.equal(labelModerationStatus("ATIVA"), "Ativa");
    assert.equal(labelModerationStatus("INATIVA"), "Inativa");
    assert.equal(labelModerationStatus("ATIVO"), "Ativo");
    assert.equal(labelModerationStatus("INATIVO"), "Inativo");
    assert.equal(labelModerationOrigin("ONG"), "Alterado pela ONG");
    assert.equal(labelModerationOrigin("PORTO_AJUDA"), "Alterado pela administração da plataforma");
    assert.equal(labelModerationOrigin("ADMINISTRATIVE"), "Alterado pela administração da plataforma");
    assert.equal(labelModerationOrigin("INDETERMINADA"), "Origem não informada");
    assert.equal(labelModerationStatus("NEW_INTERNAL_STATE"), "Status indisponível");
    assert.equal(labelModerationOrigin("NEW_INTERNAL_ORIGIN"), "Origem não informada");
});

test("administrative appeal details render translated subject types", async () => {
    const { labelModerationSubject } = await labels();
    assert.equal(labelModerationSubject("USER"), "Pessoa");
    assert.equal(labelModerationSubject("OSC"), "ONG");
    assert.equal(labelModerationSubject("PUBLICATION"), "Publicação");
    assert.equal(labelModerationSubject("VOLUNTEERING"), "Oportunidade de voluntariado");
    assert.equal(labelModerationSubject("FUTURE_TYPE"), "Registro");
    const admin = await readFile(resolve(__dirname, "../../public/components/admin.js"), "utf8");
    assert.match(admin, /labelModerationStatus\(item\.status\)/);
    assert.match(admin, /labelModerationOrigin\(item\.statusOrigin\)/);
    assert.match(admin, /labelModerationSubject\(item\.subjectType\)/);
});
