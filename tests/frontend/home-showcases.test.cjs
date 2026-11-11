const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

async function carregarPaginador() {
    const source = await readFile(resolve(__dirname, "../../public/components/showcase-pagination.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("homepage paginates in groups of six and handles a short final page", async () => {
    const { createShowcasePager, HOME_SHOWCASE_PAGE_SIZE } = await carregarPaginador();
    const requested = [];
    const pager = createShowcasePager({
        fetchPage: async (page, size) => {
            requested.push({ page, size });
            return page === 0
                ? { number: 0, totalPages: 2, content: Array.from({ length: 6 }, (_, i) => `first-${i}`) }
                : { number: 1, totalPages: 2, content: ["last-a", "last-b"] };
        }
    });

    await pager.load();
    assert.equal(HOME_SHOWCASE_PAGE_SIZE, 6);
    assert.equal(pager.getState().items.length, 6);
    assert.equal(pager.getState().canPrevious, false);
    assert.equal(pager.getState().canNext, true);

    await pager.next();
    assert.equal(pager.getState().page, 1);
    assert.deepEqual(pager.getState().items, ["last-a", "last-b"]);
    assert.equal(pager.getState().canNext, false);
    assert.equal(pager.getState().canPrevious, true);
    assert.deepEqual(requested, [{ page: 0, size: 6 }, { page: 1, size: 6 }]);

    await pager.next();
    assert.equal(requested.length, 2);
    await pager.previous();
    assert.equal(pager.getState().page, 0);
    assert.equal(pager.getState().canPrevious, false);
    assert.deepEqual(requested[2], { page: 0, size: 6 });
});

test("showcase pagination state is independent per category", async () => {
    const { createShowcasePager } = await carregarPaginador();
    const pages = { publications: 0, volunteering: 0 };
    const create = key => createShowcasePager({
        fetchPage: async page => {
            pages[key] = page;
            return { number: page, totalPages: 3, content: [`${key}-${page}`] };
        }
    });
    const publications = create("publications");
    const volunteering = create("volunteering");

    await publications.load();
    await volunteering.load();
    await publications.next();

    assert.equal(publications.getState().page, 1);
    assert.equal(volunteering.getState().page, 0);
    assert.deepEqual(pages, { publications: 1, volunteering: 0 });
});

test("showcase arrows report correct disabled states and retries recover errors", async () => {
    const { createShowcasePager } = await carregarPaginador();
    let fail = true;
    const pager = createShowcasePager({
        fetchPage: async page => {
            if (fail) throw new Error("offline");
            return { number: page, totalPages: 1, content: ["recovered"] };
        }
    });

    await pager.load();
    assert.equal(pager.getState().status, "error");
    assert.equal(pager.getState().canPrevious, false);
    assert.equal(pager.getState().canNext, false);
    fail = false;
    await pager.retry();
    assert.equal(pager.getState().status, "ready");
    assert.deepEqual(pager.getState().items, ["recovered"]);
    assert.equal(pager.getState().canNext, false);
});

test("empty public pages settle into an accessible empty state with disabled arrows", async () => {
    const { createShowcasePager } = await carregarPaginador();
    const pager = createShowcasePager({
        fetchPage: async () => ({ number: 0, totalPages: 0, content: [] })
    });

    await pager.load();

    assert.equal(pager.getState().status, "empty");
    assert.deepEqual(pager.getState().items, []);
    assert.equal(pager.getState().canPrevious, false);
    assert.equal(pager.getState().canNext, false);
});

test("homepage uses category-specific public paged APIs and a verified-donations empty state", async () => {
    const home = await readFile(resolve(__dirname, "../../public/components/home-showcases.js"), "utf8");
    const html = await readFile(resolve(__dirname, "../../public/pages/Porto-Ajuda.html"), "utf8");
    assert.match(home, /endpoint: "\/osc\/list"/);
    assert.match(home, /endpoint: "\/post\/publicos"/);
    assert.match(home, /endpoint: "\/post\/voluntariados"/);
    assert.doesNotMatch(home, /endpoint:.*doa[cç][aã]o/i);
    assert.match(home, /Ainda não há campanhas de arrecadação verificadas/);
    assert.match(html, /href="\/catalogo\?categoria=ongs"[^>]*>Ver todas as ONGs/);
    assert.match(html, /href="\/catalogo\?categoria=publicacoes"[^>]*>Ver todas as publicações/);
    assert.match(html, /href="\/catalogo\?categoria=voluntariado"[^>]*>Ver todos os voluntariados/);
    assert.match(html, /href="\/catalogo\?categoria=doacoes"[^>]*>Ver todas as doações/);
});

test("homepage showcase controls and states are keyboard-accessible and category-specific", async () => {
    const html = await readFile(resolve(__dirname, "../../public/pages/Porto-Ajuda.html"), "utf8");
    const source = await readFile(resolve(__dirname, "../../public/components/home-showcases.js"), "utf8");
    const modal = await readFile(resolve(__dirname, "../../public/components/crud.js"), "utf8");
    assert.match(html, /id="ongs-status"[^>]+role="status"[^>]+aria-live="polite"/);
    assert.match(html, /id="publicacoes-status"[^>]+role="status"[^>]+aria-live="polite"/);
    assert.match(html, /id="voluntariado-status"[^>]+role="status"[^>]+aria-live="polite"/);
    assert.match(html, /id="ongs-anterior"[^>]+aria-label=/);
    assert.match(html, /id="publicacoes-proximo"[^>]+aria-label=/);
    assert.match(html, /id="voluntariado-proximo"[^>]+aria-label=/);
    assert.match(source, /anterior\.addEventListener\("click"/);
    assert.match(source, /proximo\.addEventListener\("click"/);
    assert.match(source, /aria-haspopup/, "content cards announce the dialog viewer");
    assert.match(source, /vaga\.quantidadeVagas/);
    assert.match(source, /vaga\.modalidadeVoluntariado/);
    assert.match(html, /id="modal-card-container"[^>]+role="dialog"[^>]+aria-modal="true"/);
    assert.match(html, /id="modal-card-vaga-detalhes"[^>]*hidden/);
    assert.match(modal, /window\.abrirModalConteudoPublico/);
    assert.match(modal, /function imagemPublicaValida/);
    assert.match(modal, /evento\.key === "Escape"/);
    assert.match(modal, /origemModalConteudoPublico\.focus\(\)/);
});

test("category catalog routes, bounded public feeds, filters, and location controls are present", async () => {
    const html = await readFile(resolve(__dirname, "../../public/pages/catalogo.html"), "utf8");
    const script = await readFile(resolve(__dirname, "../../public/components/catalogo.js"), "utf8");
    const home = await readFile(resolve(__dirname, "../../public/pages/Porto-Ajuda.html"), "utf8");
    const vercel = JSON.parse(await readFile(resolve(__dirname, "../../vercel.json"), "utf8"));
    assert.equal(vercel.cleanUrls, true);
    assert.ok(vercel.rewrites.some(({ source, destination }) => source === "/catalogo" && destination === "/pages/catalogo"));
    for (const category of ["ongs", "publicacoes", "voluntariado", "doacoes"]) {
        assert.match(home, new RegExp(`/catalogo\\?categoria=${category}`));
        assert.match(script, new RegExp(`${category}: \\{`));
    }
    assert.match(html, /id="catalogo-filtros"[^>]+role="search"/);
    assert.match(script, /const MAX_RECORDS = 120/);
    assert.match(script, /endpoint: "\/post\/catalogo\/publicacoes"/);
    assert.match(script, /endpoint: "\/post\/catalogo\/voluntariados"/);
    assert.match(script, /url\.searchParams\.set\("size", String\(API_PAGE_SIZE\)\)/);
    assert.match(script, /fetchAutenticado\("\/usuario\/perfil"\)/);
    assert.match(script, /getCurrentPosition/);
    assert.match(script, /Coordenadas mantidas apenas nesta página/);
    assert.match(script, /page < Math\.min\(sourceTotalPages, batchLimit\)/);
    assert.match(script, /abrirModalConteudoPublico/);
    assert.match(script, /catalogo-doacoes-vazio/);
    assert.doesNotMatch(script, /payment|stripe|checkout/i);
});

test("NGO expansion stays in a fixed two-row layout and is disabled as a hover interaction on touch", async () => {
    const styles = await readFile(resolve(__dirname, "../../public/styles/carrossel-amostra.css"), "utf8");
    const html = await readFile(resolve(__dirname, "../../public/pages/Porto-Ajuda.html"), "utf8");
    assert.match(html, /id="cards-osc"[^>]+data-home-showcase="true"/);
    assert.match(styles, /\.ong-showcase-row\s*\{[^}]*height:\s*350px/s);
    assert.match(styles, /\.ong-showcase-row \.card-osc:hover[^}]*flex:\s*2 1 0/s);
    assert.match(styles, /@media \(max-width: 768px\)[\s\S]*\.ong-showcase-row\s*\{[^}]*display:\s*grid/s);
    assert.match(styles, /@media \(prefers-reduced-motion: reduce\)/);
});
