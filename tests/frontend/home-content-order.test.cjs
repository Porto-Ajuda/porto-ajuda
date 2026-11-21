const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

async function orderModule() {
    const source = await readFile(resolve(__dirname, "../../public/components/home-content-order.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("Fisher-Yates shuffles a copied full dataset without duplicates", async () => {
    const { fisherYatesShuffle, uniqueById } = await orderModule();
    const input = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }];
    const shuffled = fisherYatesShuffle(input, () => 0);
    assert.deepEqual(input.map(item => item.id), [1, 2, 3, 4]);
    assert.deepEqual(shuffled.map(item => item.id), [2, 3, 4, 1]);
    assert.equal(new Set(shuffled.map(item => item.id)).size, input.length);
    assert.deepEqual([...shuffled.map(item => item.id)].sort(), [1, 2, 3, 4]);
    assert.deepEqual(uniqueById([{ id: 1 }, { id: "1" }, { id: 2 }]).map(item => item.id), ["1", 2]);
});

test("a new shuffle can produce a fresh order, and empty or single-item inputs are safe", async () => {
    const { fisherYatesShuffle } = await orderModule();
    const items = [1, 2, 3, 4];
    assert.notDeepEqual(
        fisherYatesShuffle(items, () => 0),
        fisherYatesShuffle(items, () => 0.99)
    );
    assert.deepEqual(fisherYatesShuffle([]), []);
    assert.deepEqual(fisherYatesShuffle([1]), [1]);
});

test("loads every page, returns each eligible item once, and rejects changing datasets", async () => {
    const { loadCompleteDataset, fisherYatesShuffle } = await orderModule();
    const pages = [
        { content: [{ id: 1 }, { id: 2 }], totalPages: 3, totalElements: 5 },
        { content: [{ id: 3 }, { id: 4 }], totalPages: 3, totalElements: 5 },
        { content: [{ id: 5 }], totalPages: 3, totalElements: 5 }
    ];
    const requested = [];
    const allItems = await loadCompleteDataset(async (page, size) => {
        requested.push([page, size]);
        return pages[page];
    }, 2);
    const randomized = fisherYatesShuffle(allItems, () => 0.5);
    assert.deepEqual(requested, [[0, 2], [1, 2], [2, 2]]);
    assert.equal(randomized.length, 5);
    assert.deepEqual(randomized.map(item => item.id).sort(), [1, 2, 3, 4, 5]);
    await assert.rejects(loadCompleteDataset(async page => ({
        content: [{ id: page === 0 ? 1 : 1 }],
        totalPages: 2,
        totalElements: 2
    }), 1), /mudou durante o carregamento/);
});

test("homepage randomizes only the three requested feeds and leaves NGO recommendations intact", async () => {
    const home = await readFile(resolve(__dirname, "../../public/components/home-showcases.js"), "utf8");
    assert.match(home, /buscarItensAleatorios\("\/post\/catalogo\/publicacoes"\)/);
    assert.match(home, /buscarItensAleatorios\("\/post\/catalogo\/voluntariados"\)/);
    assert.match(home, /const eligible = uniqueById\(campaigns\s*\.filter\(item => item\?\.state === "ACTIVE"[\s\S]*?return fisherYatesShuffle\(eligible\)/);
    assert.match(home, /return orderNgoRecommendations\(unique\)/);
    assert.doesNotMatch(home, /fisherYatesShuffle\(unique\)/);
    assert.match(home, /loadCompleteDataset\(async \(page, size\)/);
    assert.match(home, /new URL\(endpoint, API_URL\)/);
});

test("catalog pagination and explicit chronological sorting remain deterministic", async () => {
    const catalog = await readFile(resolve(__dirname, "../../public/components/catalogo.js"), "utf8");
    assert.match(catalog, /params\.get\("ordem"\) \|\| "recentes"/);
    assert.match(catalog, /return itemDate\(b\) - itemDate\(a\)/);
    assert.match(catalog, /resultItems = sourceItems\.filter/);
    assert.match(catalog, /function navegarPaginaResultados\(nextPage\)/);
    assert.match(catalog, /resultItems\.slice\(start, start \+ PAGE_SIZE\)/);
    assert.doesNotMatch(catalog, /fisherYatesShuffle/);
});
