const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

async function carregarLogica() {
    const source = await readFile(resolve(__dirname, "../../public/components/gerenciar-ong-home-logic.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("membership selector maps server roles/statuses and links only to a validated NGO id", async () => {
    const { normalizarOngsMinhas, rotaGerenciamentoOng } = await carregarLogica();
    const organizations = normalizarOngsMinhas([
        { id: 42, nome: "ONG sintética", cargo: "EDITOR", status: "ATIVA", arquivada: false },
        { id: 43, nome: "ONG restrita", cargo: "MEMBRO", status: "INATIVA", arquivada: true },
        { id: "../../admin", nome: "entrada inválida", cargo: "SUPER_ADMIN" }
    ]);

    assert.deepEqual(organizations, [
        { id: 42, nome: "ONG sintética", cargo: "Editor", status: "Ativa", arquivada: false },
        { id: 43, nome: "ONG restrita", cargo: "Membro", status: "Arquivada", arquivada: true }
    ]);
    assert.equal(rotaGerenciamentoOng(42, "conteudo"), "/ong-profile?id=42#gestao-conteudo");
    assert.equal(rotaGerenciamentoOng(42, "ong"), "/ong-profile?id=42#informacoes");
    assert.equal(rotaGerenciamentoOng("/admin", "conteudo"), null);
    assert.throws(() => normalizarOngsMinhas({ content: [] }), /organizações vinculadas/);
});
