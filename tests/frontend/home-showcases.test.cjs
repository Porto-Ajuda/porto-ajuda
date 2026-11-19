const { access, readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const raiz = resolve(__dirname, "../..");
const ler = caminho => readFile(resolve(raiz, caminho), "utf8");
const existe = caminho => access(resolve(raiz, caminho)).then(() => true, () => false);

test("homepage carousels load public content and campaigns through the vitrine API", async () => {
    const home = await ler("public/components/home-showcases.js");
    const vitrine = await ler("public/components/vitrine.js");
    const html = await ler("public/pages/Porto-Ajuda.html");
    assert.match(vitrine, /export const VITRINE_MAX_ITENS = 6;/);
    assert.match(vitrine, /\.slice\(0, VITRINE_MAX_ITENS\)/);
    assert.match(home, /buscarItens\("\/osc\/list"\)/);
    assert.match(home, /buscarItens\("\/post\/publicos"\)/);
    assert.match(home, /buscarItens\("\/post\/voluntariados"\)/);
    assert.match(home, /donation-campaigns/);
    assert.match(home, /state === "ACTIVE"/);
    assert.match(home, /slice\(0, VITRINE_MAX_ITENS\)/);
    assert.match(html, /href="\/catalogo\?categoria=ongs"[^>]*>Ver todas as ONGs/);
    assert.match(html, /href="\/catalogo\?categoria=publicacoes"[^>]*>Ver todas as publicações/);
    assert.match(html, /href="\/catalogo\?categoria=voluntariado"[^>]*>Ver todos os voluntariados/);
    assert.match(html, /href="\/catalogo\?categoria=doacoes"[^>]*>Ver todas as doações/);
});

test("homepage campaign cards use the shared detail modal without leaving home", async () => {
    const home = await ler("public/components/home-showcases.js");
    const modal = await ler("public/components/campaign-donation-dialog.js");
    const html = await ler("public/pages/Porto-Ajuda.html");
    const styles = await ler("public/styles/Porto-Ajuda.css");
    assert.match(home, /createCampaignDonationDialog\(\)/);
    assert.match(home, /campaignDialog\.open\(campanha, card\)/);
    assert.match(home, /campaignMoney\(valorArrecadado\)/);
    assert.match(home, /campaignMoney\(metaLiquida\)/);
    assert.match(home, /progresso\.setAttribute\("aria-label"/);
    assert.match(home, /trilhoId: "cards-doacao"/);
    assert.match(home, /criarCard: criarCardCampanha/);
    assert.match(modal, /dialog\.showModal\(\)/);
    assert.match(modal, /history\.pushState/);
    assert.match(html, /id="cards-doacao"[^>]+role="list"[^>]+aria-label="Vaquinhas públicas/);
    assert.match(html, /href="\/catalogo\?categoria=doacoes"[^>]*>Ver todas as doa/);
    assert.match(html, /campaign-donation-dialog\.css/);
    assert.match(styles, /\.vitrine-item-campanha \.vitrine-campanha-detalhes/);
    assert.doesNotMatch(home, /location\.(assign|href).*catalogo/);
});

test("homepage carousels expose accessible states, cards and arrows", async () => {
    const html = await ler("public/pages/Porto-Ajuda.html");
    const vitrine = await ler("public/components/vitrine.js");
    const modal = await ler("public/components/conteudo-publico-modal.js");
    assert.match(html, /components\/conteudo-publico-modal\.js/);
    assert.match(html, /id="ongs-status"[^>]+role="status"[^>]+aria-live="polite"/);
    assert.match(html, /id="publicacoes-status"[^>]+role="status"[^>]+aria-live="polite"/);
    assert.match(html, /id="voluntariado-status"[^>]+role="status"[^>]+aria-live="polite"/);
    assert.match(html, /id="doacoes-status"[^>]+role="status"[^>]+aria-live="polite"/);
    for (const id of ["cards-osc", "cards-publicacoes", "cards-voluntariacao", "cards-doacao"]) {
        assert.match(html, new RegExp(`<div class="vitrine-trilho" id="${id}"[^>]+role="list"[^>]+aria-label=`));
    }
    // O feed usa os trilhos da vitrine e preserva o marcador de integração.
    for (const id of ["cards-osc", "cards-voluntariacao", "cards-doacao"]) {
        assert.match(html, new RegExp(`id="${id}"[^>]+data-home-showcase="true"`));
    }
    assert.match(vitrine, /aria-label", lado === "esq" \? "Itens anteriores" : "Próximos itens"/);
    assert.match(vitrine, /esquerda\.disabled = viewport\.scrollLeft <= 2/);
    assert.match(vitrine, /aria-haspopup/, "content cards announce the dialog viewer");
    assert.match(vitrine, /card\.setAttribute\("aria-label", rotulo\)/);
    assert.match(html, /id="modal-card-container"[^>]+role="dialog"[^>]+aria-modal="true"/);
    assert.match(html, /id="modal-card-vaga-detalhes"[^>]*hidden/);
    assert.match(modal, /window\.abrirModalConteudoPublico/);
    assert.match(modal, /function imagemPublicaValida/);
    assert.match(modal, /evento\.key === "Escape"/);
    assert.match(modal, /origemModalConteudoPublico\.focus\(\)/);
});

test("home management stays membership-scoped and never loads legacy IndexedDB editing", async () => {
    const html = await ler("public/pages/Porto-Ajuda.html");
    const manager = await ler("public/components/gerenciar-ong-home.js");
    const logic = await ler("public/components/gerenciar-ong-home-logic.js");
    assert.match(html, /data-home-manage="ong"/);
    assert.match(html, /data-home-manage="conteudo"/);
    assert.match(html, /components\/gerenciar-ong-home\.js/);
    assert.match(html, /id="dialogo-gerenciar-ongs-home"/);
    assert.doesNotMatch(html, /components\/crud\.js|abrirModalEdicao\(|Editar Doações/);
    assert.match(manager, /fetchAutenticado\("\/osc\/minhas"/);
    assert.match(manager, /dialog\.showModal\(\)/);
    assert.match(manager, /window\.location\.assign\(destino\)/);
    assert.match(logic, /function rotaGerenciamentoOng/);
    assert.match(logic, /gestao-conteudo/);
});

test("carousel arrows only appear when the mouse is near and are hidden on touch devices", async () => {
    const vitrine = await ler("public/components/vitrine.js");
    const estilos = await ler("public/styles/vitrine.css");
    assert.match(vitrine, /const DISTANCIA_SETAS_PX = 200;/);
    assert.match(vitrine, /evento\.pointerType !== "mouse"/);
    assert.match(estilos, /\.vitrine-seta\s*\{[^}]*opacity:\s*0;[^}]*pointer-events:\s*none/s);
    assert.match(estilos, /\.vitrine-seta\.vitrine-seta-visivel,\s*\.vitrine-seta:focus-visible\s*\{[^}]*opacity:\s*1/s);
    assert.match(estilos, /@media \(hover: none\)[\s\S]*\.vitrine-seta\s*\{[^}]*display:\s*none/);
    assert.match(estilos, /@media \(hover: none\)[\s\S]*scroll-snap-type:\s*x mandatory/);
});

test("card expansion is a hover/keyboard interaction for fine pointers with a reduced-motion fallback", async () => {
    const estilos = await ler("public/styles/vitrine.css");
    assert.match(estilos, /@media \(hover: hover\) and \(pointer: fine\)\s*\{\s*\.vitrine-item:hover\s*\{[^}]*flex-basis:\s*var\(--v-w-aberto\)/s);
    assert.match(estilos, /\.vitrine-item:focus-within\s*\{[^}]*flex-basis:\s*var\(--v-w-aberto\)/s);
    assert.match(estilos, /height:\s*37\.5%/);
    assert.match(estilos, /\.vitrine-trilho:has\(\.vitrine-item:hover\) \.vitrine-item:not\(:hover\) \.vitrine-card\s*\{[^}]*scale\(\.92\)/s);
    assert.match(estilos, /@media \(prefers-reduced-motion: reduce\)/);
    // a imagem lateral fica por cima para os cards passarem por trás dela
    assert.match(estilos, /\.section:has\(\.vitrine\) \.section-image\s*\{[^}]*z-index:\s*2/s);
});

test("legacy paginated list and old NGO carousel code is gone", async () => {
    const html = await ler("public/pages/Porto-Ajuda.html");
    assert.equal(await existe("public/components/carrossel-amostra.js"), false);
    assert.equal(await existe("public/components/showcase-pagination.js"), false);
    const home = await ler("public/components/home-showcases.js");
    assert.doesNotMatch(home, /showcase-pagination\.js|carrossel-amostra\.js/);
    assert.doesNotMatch(html, /showcase-grid|showcase-controles|controles-carrossel|carrossel-ongs|ongs-anterior|ongs-proximo/);
    const cssHome = await ler("public/styles/Porto-Ajuda.css");
    assert.doesNotMatch(cssHome, /\.showcase-grid|\.showcase-controles|\.showcase-card-(media|preview|informacoes|title|ngo|detail)|\.showcase-card\s*[{:,]/);
    const cssOngs = await ler("public/styles/carrossel-amostra.css");
    assert.doesNotMatch(cssOngs, /\.card-osc|\.ong-showcase|\.carrossel-ongs|\.controles-carrossel|#cards-osc/);
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
