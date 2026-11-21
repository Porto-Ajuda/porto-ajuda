const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

async function loadModule(relativePath) {
    const source = await readFile(resolve(__dirname, "../../public/components", relativePath), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("category selection uses the shared taxonomy and all structured API cause fields", async () => {
    const { CAUSE_TAXONOMY, causeCategoriesForNgo } = await loadModule("cause-taxonomy.js");
    const { filtrarOngs } = await loadModule("proximity-filter.js");
    const ngo = {
        id: 1,
        causaPrincipal: "SAUDE",
        causasSecundarias: ["ANIMAIS"],
        causas: ["SAUDE", "ANIMAIS"]
    };
    const categories = causeCategoriesForNgo(ngo);

    assert.equal(CAUSE_TAXONOMY.length, 14);
    assert.deepEqual(categories, ["saude", "animais"]);
    assert.deepEqual(filtrarOngs([{ ...ngo, categorias: categories }], "animais", ""), [
        { ...ngo, categorias: categories }
    ]);
});

test("accent, punctuation and spaces do not affect NGO name searches", async () => {
    const { filtrarOngs, normalizarTextoBusca } = await loadModule("proximity-filter.js");
    const name = "Casa da Vó Benedita";
    const ngo = { id: 8, nome: name, categorias: [] };
    const queries = [
        "CÁSÁDÃVÔBÉNÉDÍTÂ",
        "casa da vo benedita",
        "CASA-DA-VO-BENEDITA",
        "Casa, da Vó Benedita",
        "casadavobenedita"
    ];

    queries.forEach(query => {
        assert.deepEqual(filtrarOngs([ngo], "", query), [ngo]);
    });
    assert.deepEqual(
        normalizarTextoBusca("CASA---  da,  Vó!!! Benedita"),
        normalizarTextoBusca("casadavobenedita")
    );
    assert.equal(ngo.nome, name);
});

test("category filters include NGOs associated with multiple causes and ignore missing category data", async () => {
    const { filtrarOngs } = await loadModule("proximity-filter.js");
    const ongs = [
        { id: 1, nome: "Rede Solidária", categorias: ["alimentacao", "assistencia-social"] },
        { id: 2, nome: "Grupo local", categorias: [] },
        { id: 3, nome: "Sem dados" }
    ];

    assert.deepEqual(filtrarOngs(ongs, "assistencia-social", "").map(ong => ong.id), [1]);
    assert.deepEqual(filtrarOngs(ongs, "alimentacao", "").map(ong => ong.id), [1]);
});

test("only categories represented by eligible NGOs are offered, and Todos is always present", async () => {
    const { CAUSE_TAXONOMY } = await loadModule("cause-taxonomy.js");
    const { categoriasParaFiltro } = await loadModule("proximity-filter.js");
    const result = categoriasParaFiltro([
        { id: 1, categorias: ["saude"] }
    ], CAUSE_TAXONOMY);

    assert.deepEqual(result.categorias.map(categoria => categoria.id), ["", "saude"]);
    assert.equal(result.categorias[0].label, "Todos");
    assert.deepEqual(categoriasParaFiltro([], CAUSE_TAXONOMY).categorias.map(c => c.id), [""]);
});

test("available categories use the complete NGO dataset, not search results", async () => {
    const { readFile } = require("node:fs/promises");
    const source = await readFile(resolve(__dirname, "../../public/components/proximity.js"), "utf8");
    const start = source.indexOf("function aplicarFiltros()");
    const end = source.indexOf("function criarCartao", start);
    const filterHandler = source.slice(start, end);

    assert.match(source, /categoriasParaFiltro\(ongsCarregadas, CAUSE_TAXONOMY, categoriaSelecionada\)/);
    assert.doesNotMatch(filterHandler, /renderizarFiltrosCategoria/);
});

test("an NGO with multiple causes exposes each category, and reload falls back to Todos if selection disappears", async () => {
    const { CAUSE_TAXONOMY } = await loadModule("cause-taxonomy.js");
    const { categoriasParaFiltro } = await loadModule("proximity-filter.js");
    const ngo = { id: 1, categorias: ["saude", "animais"] };
    const initial = categoriasParaFiltro([ngo], CAUSE_TAXONOMY, "animais");
    const reloaded = categoriasParaFiltro([{ id: 2, categorias: ["saude"] }], CAUSE_TAXONOMY, "animais");

    assert.deepEqual(initial.categorias.map(categoria => categoria.id), ["", "saude", "animais"]);
    assert.equal(initial.categoriaSelecionada, "animais");
    assert.equal(reloaded.categoriaSelecionada, "");
});

test("search and category work together, and clearing search retains the selected category", async () => {
    const { filtrarOngs } = await loadModule("proximity-filter.js");
    const ongs = [
        { id: 1, nome: "Casa de Saúde", categorias: ["saude"], categoriasBusca: "Saúde Meio ambiente" },
        { id: 2, nome: "Saúde Animal", categorias: ["animais"] }
    ];

    assert.deepEqual(filtrarOngs(ongs, "saude", "Casa").map(ong => ong.id), [1]);
    assert.deepEqual(filtrarOngs(ongs, "saude", "").map(ong => ong.id), [1]);
    assert.deepEqual(filtrarOngs(ongs, "saude", "Meio ambiente").map(ong => ong.id), [1]);
});

test("empty results are returned for unmatched category and search", async () => {
    const { filtrarOngs } = await loadModule("proximity-filter.js");

    assert.deepEqual(filtrarOngs([
        { id: 1, nome: "Casa", categorias: ["saude"] }
    ], "animais", "Casa"), []);
});

test("Todos resets only the category and retains the current search text", async () => {
    const { filtrarOngs } = await loadModule("proximity-filter.js");
    const ongs = [
        { id: 1, nome: "Casa de Saúde", categorias: ["saude"] },
        { id: 2, nome: "Casa Animal", categorias: ["animais"] },
        { id: 3, nome: "Centro de Saúde", categorias: ["saude"] }
    ];

    assert.deepEqual(filtrarOngs(ongs, "", "Casa").map(ong => ong.id), [1, 2]);
});

test("the filtered NGO set synchronizes existing list cards and map markers", async () => {
    const { filtrarOngs, sincronizarVisibilidadeOngs } = await loadModule("proximity-filter.js");
    const ongs = [
        { id: 1, nome: "Casa de Saúde", categorias: ["saude"] },
        { id: 2, nome: "Grupo Animal", categorias: ["animais"] }
    ];
    const cards = new Map(ongs.map(ong => [String(ong.id), { hidden: false }]));
    const markers = new Map(ongs.map(ong => [String(ong.id), { id: ong.id }]));
    const visibleMarkers = new Set(markers.values());
    const markerLayer = {
        hasLayer: marker => visibleMarkers.has(marker),
        addLayer: marker => visibleMarkers.add(marker),
        removeLayer: marker => visibleMarkers.delete(marker)
    };

    const firstVisible = filtrarOngs(ongs, "saude", "");
    const firstIds = sincronizarVisibilidadeOngs(firstVisible, cards, markers, markerLayer);
    assert.deepEqual([...firstIds], ["1"]);
    assert.equal(cards.get("1").hidden, false);
    assert.equal(cards.get("2").hidden, true);
    assert.deepEqual([...visibleMarkers].map(marker => marker.id), [1]);

    const allVisible = filtrarOngs(ongs, "", "");
    sincronizarVisibilidadeOngs(allVisible, cards, markers, markerLayer);
    assert.deepEqual([...visibleMarkers].map(marker => marker.id).sort(), [1, 2]);
    assert.equal(cards.get("1").hidden, false);
    assert.equal(cards.get("2").hidden, false);
});
