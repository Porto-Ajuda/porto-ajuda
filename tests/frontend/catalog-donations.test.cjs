const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

const file = relative => readFile(resolve(__dirname, "../..", relative), "utf8");

test("donations category loads the public campaigns endpoint within the canonical catalog", async () => {
    const js = await file("public/components/catalogo.js");
    const html = await file("public/pages/catalogo.html");
    assert.match(js, /doacoes: \{[^\n]*endpoint: "\/donation-campaigns"/);
    assert.match(js, /location\.pathname !== "\/doacao-ong"/);
    assert.match(js, /next\.set\("categoria", "doacoes"\)/);
    assert.match(js, /next\.set\("campanha", campaignId\)/);
    assert.match(js, /campaignMoney\(item\.raisedNetCents\)/);
    assert.match(js, /campaignDialog\.open\(item, card\)/);
    assert.match(js, /createCampaignDonationDialog/);
    assert.doesNotMatch(html, /catalogo-campanha-dialog/);
    assert.doesNotMatch(html, /Campanhas verificadas em breve/);
    assert.match(html, /id="catalogo-filter-row"/);
    assert.match(html, /campaign-donation-dialog\.css/);
});

test("campaign detail and quote stay inside the accessible catalog dialog", async () => {
    const html = await file("public/pages/catalogo.html");
    const css = await file("public/styles/catalogo.css");
    const js = await file("public/components/campaign-donation-dialog.js");
    const home = await file("public/pages/Porto-Ajuda.html");
    const sharedCss = await file("public/styles/campaign-donation-dialog.css");
    assert.match(js, /<dialog id="\$\{ID\}" class="catalogo-campanha-dialog"/);
    assert.match(js, /aria-labelledby="catalogo-campanha-titulo"/);
    assert.match(js, /aria-label="Fechar detalhes da vaquinha"/);
    assert.match(js, /data-campaign="amount"/);
    assert.match(js, /fetchAutenticado\(`\/donation-campaigns\/\$\{quotedCampaign\.id\}\/quotes`/);
    assert.match(js, /\/login\?retorno=/);
    assert.match(js, /valorCentavos/);
    assert.match(js, /window\.addEventListener\("popstate"/);
    assert.match(js, /history\.back\(\)/);
    assert.match(js, /dialog\.addEventListener\("keydown"/);
    assert.match(html, /campaign-donation-dialog\.css/);
    assert.match(home, /campaign-donation-dialog\.css/);
    assert.match(sharedCss, /\.catalogo-campanha-dialog::backdrop/);
    assert.match(sharedCss, /@media\(max-width:600px\).*catalogo-campanha-dialog/s);
});

test("legacy donation links land in the catalog and management cards use theme variables", async () => {
    const pageController = await file("backend/src/main/java/com/portoajuda/aplicacao_osc/controller/PageController.java");
    const vercel = await file("vercel.json");
    const management = await file("public/styles/ong-profile.css");
    const home = await file("public/pages/Porto-Ajuda.html");
    assert.match(pageController, /redirect:\/catalogo\?categoria=doacoes&campanha=/);
    assert.match(vercel, /"source": "\/doacao-ong"[\s\S]*?"destination": "\/pages\/catalogo"/);
    assert.match(management, /\.campanha-gestao-card[^\n]*var\(--bg-card\)/);
    assert.doesNotMatch(management, /\.campanha-gestao-card\{[^}]*background:[^;}]*#fff/i);
    assert.match(management, /campanha-gestao-card__badge/);
    assert.match(management, /campanhas-gestao-lista\{display:grid;grid-template-columns:repeat\(auto-fill/);
    assert.match(management, /body\.alto-contraste \.campanha-gestao-card/);
    assert.match(home, /href="\/catalogo\?categoria=doacoes"/);
    assert.doesNotMatch(home, /href="\/doacao-ong"/);
});
