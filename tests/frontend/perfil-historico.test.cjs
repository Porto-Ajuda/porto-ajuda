const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");

async function carregarLogica() {
    const source = await read("public/components/perfil-historico-logic.js");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("profile history maps authenticated opportunity records without presenting applications as completed work", async () => {
    const { calcularVisibilidadeHistorico, criarRegistroHistorico, mapearCandidaturaHistorico, normalizarPaginaCandidaturas } = await carregarLogica();
    const page = normalizarPaginaCandidaturas({
        content: [{
            id: 19, postId: 30, oscId: 7, tituloOportunidade: "Mutirão sintético", nomeOsc: "ONG sintética",
            dataCandidatura: "2026-11-01", status: "APROVADA", mensagem: "Não deve ser copiada para o histórico",
            modalidade: "PRESENCIAL", cidade: "Santos", bairro: "Centro", dataInicio: "2026-11-10",
            dataFim: "2026-11-20", oportunidadePublica: true
        }],
        number: 0, totalPages: 1, totalElements: 1
    });

    assert.equal(page.content.length, 1);
    assert.equal(page.content[0].tipo, "voluntariado");
    assert.equal(page.content[0].titulo, "Mutirão sintético");
    assert.equal(page.content[0].statusLabel, "Aprovada");
    assert.equal(page.content[0].modalidadeLabel, "Presencial");
    assert.equal(page.content[0].link, "/?candidaturaPost=30&candidaturaOsc=7");
    assert.deepEqual(page.availableTypes, ["voluntariado"]);
    assert.equal(Object.hasOwn(page.content[0], "mensagem"), false);

    class ElementoFalso {
        constructor(tag) { this.tag = tag; this.children = []; this.dataset = {}; this.textContent = ""; }
        append(...children) { this.children.push(...children); }
    }
    const documentoFalso = {
        createElement: tag => new ElementoFalso(tag),
        createTextNode: textContent => ({ textContent })
    };
    const rendered = criarRegistroHistorico(documentoFalso, page.content[0]);
    assert.equal(rendered.tag, "li");
    assert.equal(rendered.dataset.tipo, "voluntariado");
    assert.equal(rendered.children[0].children[0].textContent, "Mutirão sintético");
    assert.equal(rendered.children[0].children[1].textContent, "Aprovada");
    assert.equal(rendered.children[1].textContent, "ONG: ONG sintética");
    assert.match(rendered.children[2].textContent, /Candidatura enviada em/);
    assert.equal(rendered.children.filter(child => /Candidatura enviada em/.test(child.textContent)).length, 1);
    assert.equal(rendered.children.at(-1).href, "/?candidaturaPost=30&candidaturaOsc=7");

    const closed = mapearCandidaturaHistorico({ id: 20, dataCandidatura: "2026-10-10", status: "DESISTIU", oportunidadePublica: false });
    assert.equal(closed.statusLabel, "Cancelada");
    assert.equal(closed.link, null);

    assert.deepEqual(calcularVisibilidadeHistorico(0, []), {
        hasRecords: false, availableTypes: [], showTypeFilters: false
    });
    assert.deepEqual(calcularVisibilidadeHistorico(4, ["voluntariado"]), {
        hasRecords: true, availableTypes: ["voluntariado"], showTypeFilters: false
    });
    assert.deepEqual(calcularVisibilidadeHistorico(4, ["voluntariado", "doacao", "inventado"]), {
        hasRecords: true, availableTypes: ["voluntariado", "doacao"], showTypeFilters: true
    });
});

test("profile history keeps real filters, uses the owner-scoped paged API and exposes explicit loading/error/unavailable states", async () => {
    const [history, filters, page, controller, service] = await Promise.all([
        read("public/components/perfil-historico.js"),
        read("public/components/filtros-perfil.js"),
        read("public/pages/perfil.html"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/controller/CandidaturaVoluntariadoController.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/service/CandidaturaVoluntariadoService.java")
    ]);

    assert.match(history, /fetchAutenticado\(`\/usuario\/candidaturas\?page=\$\{page\}&size=\$\{PAGE_SIZE\}`,/);
    assert.match(page, /Carregar mais candidaturas/);
    assert.match(filters, /porto:historico-perfil-atualizado/);
    assert.match(page, /data-filtro="tudo"/);
    assert.match(page, /data-filtro="doacao"/);
    assert.match(page, /data-filtro="voluntariado"/);
    assert.match(page, /id="registro-carregando"/);
    assert.match(page, /id="registro-erro"/);
    assert.match(page, /id="registro-doacoes-indisponivel"/);
    assert.match(page, /data-activity-history-content/);
    assert.match(page, /id="registro-sem-atividades"/);
    assert.match(history, /areaHistorico\.hidden = !visibility\.hasRecords/);
    assert.match(filters, /grupoFiltrosTipo\.hidden = tiposDisponiveis\.length <= 1/);
    assert.doesNotMatch(page, /Instituto Esperança|Campanha do Agasalho|R\$ 1\.800|12 horas/);
    assert.match(controller, /@AuthenticationPrincipal Usuario usuario/);
    assert.match(controller, /candidaturaService\.listarMinhas\(usuario, pageable\)/);
    assert.match(service, /findAllByUsuario_IdOrderByDataCandidaturaDesc\(usuario\.getId\(\), paginacao\)/);
    assert.doesNotMatch(history, /usuarioId|userId/);
});
