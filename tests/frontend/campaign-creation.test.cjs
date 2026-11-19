const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

async function moneyModule() {
    const source = await readFile(resolve(__dirname, "../../public/components/campaign-money.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("Brazilian campaign goals convert to exact integer cents", async () => {
    const { campaignCurrencyToCents, campaignMoney } = await moneyModule();
    assert.equal(campaignCurrencyToCents("R$ 30.000,00"), 3_000_000);
    assert.equal(campaignCurrencyToCents("30000,25"), 3_000_025);
    assert.equal(campaignCurrencyToCents("30.000"), 3_000_000);
    assert.equal(campaignCurrencyToCents("R$ 30.000,001"), null);
    assert.equal(campaignMoney(3_000_000), "R$ 30.000,00");
});

test("campaign editor uses the shared content dialog and crop preview controls", async () => {
    const html = await readFile(resolve(__dirname, "../../public/pages/ong-profile.html"), "utf8");
    const start = html.indexOf('<dialog id="dialogo-campanha-doacao"');
    const end = html.indexOf("</dialog>", start) + "</dialog>".length;
    assert.ok(start >= 0 && end > start);
    const dialog = html.slice(start, end);
    assert.match(dialog, /class="dialogo-conteudo-osc"/);
    assert.match(dialog, /class="form-dialogo-conteudo-osc"/);
    assert.match(dialog, /Criar vaquinha/);
    assert.match(dialog, /Meta da ONG \(R\$\)/);
    assert.match(dialog, /Valor que a ONG pretende arrecadar, sem incluir as taxas\./);
    assert.match(dialog, /Publicar quando salvar/);
    assert.match(dialog, /data-image-adjustment/);
    assert.match(dialog, /imagem-conteudo-preview/);
    assert.match(dialog, /Salvar vaquinha/);
    assert.doesNotMatch(dialog, /<select/);
    const script = await readFile(resolve(__dirname, "../../public/components/campanhas-gestao-osc.js"), "utf8");
    assert.match(script, /item \? "Editar vaquinha" : "Criar vaquinha"/);
});
