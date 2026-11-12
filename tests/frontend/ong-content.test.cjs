const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

async function loadContentModule() {
    const source = await readFile(resolve(__dirname, "../../public/components/ong-content.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

function donationPayload() {
    return { post: {
        tipo: "DOACAO", ativo: true, titulo: "Campanha de alimentos",
        conteudo: "Precisamos de doacoes.", imagemUrl: "https://images.example.invalid/campanha.jpg"
    }, vaga: null };
}

function opportunityPayload() {
    return { post: {
        tipo: "VOLUNTARIADO", ativo: true, titulo: "Voluntariado",
        conteudo: "Apoio semanal.", imagemUrl: null
    }, vaga: {
        quantidadeVagas: 2, modalidadeVoluntariado: "HIBRIDO", requisitos: "Disponibilidade",
        cargaHoraria: "4h", cidade: "Santos", bairro: "Centro", rua: null, numero: null,
        complemento: null, dataInicio: "2030-02-01", dataFim: "2030-02-28", status: "ABERTA"
    } };
}

test("accepts the existing DOACAO post contract and optional safe image URL", async () => {
    const { validarConteudoOsc } = await loadContentModule();
    assert.deepEqual(validarConteudoOsc(donationPayload()), { valido: true });
    const relative = donationPayload();
    relative.post.imagemUrl = "/images/campanha.jpg";
    assert.deepEqual(validarConteudoOsc(relative), { valido: true });
});

test("validates the actual volunteer vacancy fields and lifecycle enums", async () => {
    const { validarConteudoOsc } = await loadContentModule();
    assert.deepEqual(validarConteudoOsc(opportunityPayload()), { valido: true });
    for (const status of ["ABERTA", "ENCERRADA", "CANCELADA"]) {
        const payload = opportunityPayload();
        payload.vaga.status = status;
        assert.equal(validarConteudoOsc(payload).valido, true);
    }
});

test("rejects platform news, unsafe image schemes, and mismatched post/vacancy types", async () => {
    const { validarConteudoOsc } = await loadContentModule();
    const news = donationPayload();
    news.post.tipo = "NOTICIA";
    assert.equal(validarConteudoOsc(news).valido, false);
    const unsafe = donationPayload();
    unsafe.post.imagemUrl = "javascript:alert(1)";
    assert.equal(validarConteudoOsc(unsafe).campo, "post");
    const mismatch = opportunityPayload();
    mismatch.vaga = null;
    assert.equal(validarConteudoOsc(mismatch).campo, "vaga");
});

test("validates local JPEG and PNG image files and applies the 10 MB limit", async () => {
    const { validarArquivoImagem } = await loadContentModule();
    assert.equal(validarArquivoImagem({ type: "image/png", size: 1024 }).valido, true);
    assert.equal(validarArquivoImagem({ type: "image/jpeg", size: 1024 }).valido, true);
    assert.match(validarArquivoImagem({ type: "image/svg+xml", size: 1024 }).mensagem, /JPEG ou PNG/);
    assert.match(validarArquivoImagem({ type: "image/png", size: 0 }).mensagem, /não vazia/);
    assert.match(validarArquivoImagem({ type: "image/png", size: 10 * 1024 * 1024 + 1 }).mensagem, /10 MB/);
});

test("rejects invalid date range, quantity, and unknown opportunity statuses", async () => {
    const { validarConteudoOsc } = await loadContentModule();
    const reversedDates = opportunityPayload();
    reversedDates.vaga.dataInicio = "2030-03-01";
    assert.equal(validarConteudoOsc(reversedDates).campo, "vaga");
    const impossibleDate = opportunityPayload();
    impossibleDate.vaga.dataFim = "2030-02-31";
    assert.equal(validarConteudoOsc(impossibleDate).campo, "vaga");
    const negativeQuantity = opportunityPayload();
    negativeQuantity.vaga.quantidadeVagas = -1;
    assert.equal(validarConteudoOsc(negativeQuantity).campo, "vaga");
    const unknownStatus = opportunityPayload();
    unknownStatus.vaga.status = "PAUSADA";
    assert.equal(validarConteudoOsc(unknownStatus).campo, "vaga");
});

test("separates posts and linked volunteer opportunities", async () => {
    const { separarConteudoOsc } = await loadContentModule();
    const donation = { id: 1, tipo: "DOACAO" };
    const opportunity = { id: 2, tipo: "VOLUNTARIADO", vaga: { status: "ABERTA" } };
    const invalidOpportunity = { id: 3, tipo: "VOLUNTARIADO", vaga: null };
    assert.deepEqual(separarConteudoOsc([donation, opportunity, invalidOpportunity]), {
        posts: [donation], oportunidades: [opportunity, invalidOpportunity]
    });
    assert.deepEqual(separarConteudoOsc(null), { posts: [], oportunidades: [] });
});

test("public tabs expose only eligible content returned for an active organization", async () => {
    const { separarConteudoPublicoOsc } = await loadContentModule();
    const donation = {
        id: 1, oscId: 101, tipo: "DOACAO", ativo: true, titulo: "Campanha",
        conteudo: "Descrição", imagemUrl: null, podeEditar: false, vaga: null
    };
    const opportunity = {
        id: 2, oscId: 101, tipo: "VOLUNTARIADO", ativo: true, titulo: "Apoio",
        conteudo: "Descrição", imagemUrl: null, podeEditar: false, vaga: {
            id: 20, quantidadeVagas: 3, modalidadeVoluntariado: "HIBRIDO",
            status: "ABERTA", dataInicio: "2030-01-01", dataFim: "2030-12-31"
        }
    };
    const unpublished = { ...donation, id: 3, ativo: false };
    const closedOpportunity = { ...opportunity, id: 4, vaga: { ...opportunity.vaga, status: "ENCERRADA" } };
    const foreignOrganization = { ...donation, id: 5, oscId: 202 };

    assert.deepEqual(separarConteudoPublicoOsc(
        [donation, opportunity, unpublished, closedOpportunity, foreignOrganization], 101, "ATIVA"),
    { posts: [donation], oportunidades: [opportunity] });
    assert.deepEqual(separarConteudoPublicoOsc([donation, opportunity], 101, "INATIVA"),
        { posts: [], oportunidades: [] });
    assert.deepEqual(separarConteudoPublicoOsc([donation, opportunity], 101, "ATIVA", true),
        { posts: [], oportunidades: [] });
    assert.deepEqual(separarConteudoPublicoOsc([], 101, "ATIVA"),
        { posts: [], oportunidades: [] });
});

test("content DTOs are bound to the selected NGO before rendering", async () => {
    const { respostaConteudoOscValida } = await loadContentModule();
    const dto = {
        id: 12, oscId: 101, tipo: "DOACAO", ativo: true, titulo: "Post",
        conteudo: "Conteudo", imagemUrl: null, podeEditar: true
    };
    assert.equal(respostaConteudoOscValida(dto, 101), true);
    assert.equal(respostaConteudoOscValida({ ...dto, oscId: 202 }, 101), false);
    assert.equal(respostaConteudoOscValida({ ...dto, imagemUrl: "javascript:alert(1)" }, 101), false);
});

test("NGO profile exposes one accessible tab bar with public tabs and gated member tabs", async () => {
    const html = await readFile(resolve(__dirname, "../../public/pages/ong-profile.html"), "utf8");
    const source = await readFile(resolve(__dirname, "../../public/components/ong-profile.js"), "utf8");
    assert.match(html, /id="abas-perfil-osc"[^>]+role="tablist"[^>]+aria-label="Navega/);
    assert.equal((html.match(/class="abas-osc/g) || []).length, 1);
    assert.doesNotMatch(html, /id="abas-publicas-osc"|id="abas-osc"/);
    assert.match(html, /id="aba-informacoes"[^>]*aria-selected="true"[^>]*>Informações/);
    assert.match(html, /id="aba-publicacoes"[^>]*hidden>Publicações/);
    assert.match(html, /id="aba-voluntariado-publico"[^>]*hidden>Voluntariado/);
    assert.match(html, /id="painel-publicacoes"[^>]*role="tabpanel"[^>]*hidden/);
    assert.match(html, /id="painel-voluntariado-publico"[^>]*role="tabpanel"[^>]*hidden/);
    const infoPanelStart = html.indexOf('id="painel-publico"');
    const infoPanelEnd = html.indexOf('id="painel-publicacoes"', infoPanelStart);
    assert.ok(infoPanelEnd > infoPanelStart);
    assert.doesNotMatch(html.slice(infoPanelStart, infoPanelEnd), /posts-publicos-lista|oportunidades-publicas-lista/);
    assert.match(html, /id="gestao-osc"[^>]*hidden/);
    assert.match(html, /id="aba-conteudo"[^>]*data-hash="gestao-conteudo"[^>]*hidden>Posts e voluntariado/);
    assert.match(html, /id="aba-membros"[^>]*data-hash="membros"[^>]*hidden>Membros/);
    assert.match(html, /id="aba-dados"[^>]*data-hash="configuracoes"[^>]*hidden>Configura/);
    assert.match(html, /id="gestao-osc"[^>]*hidden/);
    assert.doesNotMatch(html, /<nav[^>]+aria-label="Gest/);
    assert.match(source, /function definirAcessoAbasPrivadas\(permitido\)/);
    assert.match(source, /const resposta = await requisicaoAutenticada\("\/osc\/minhas"\)/);
    assert.match(source, /if \(!vinculo\?\.cargo \|\| oscAtual\?\.arquivada\)/);
    assert.match(source, /if \(abaPrivada && !membroOscConfirmado\) return false/);
    assert.match(source, /if \(!privada && abaSolicitada\.id !== "aba-informacoes" && !verificacaoConteudoPublicoConcluida\) return/);
    assert.match(source, /if \(navegacao\.dataset\.inicializada === "true"\) return/);
    assert.match(source, /abas\[i\]\.setAttribute\("aria-selected", String\(ativa\)\)/);
    assert.match(source, /paineis\[i\]\.hidden = !ativa/);
    assert.match(source, /history\.replaceState\(null, "", `\$\{location\.pathname\}\$\{location\.search\}#\$\{abaSelecionada\.dataset\.hash\}`\)/);
    assert.match(html, /id="dialogo-conteudo-publico"[^>]+aria-labelledby/);
    assert.match(html, /id="fechar-visualizador-conteudo"[^>]+aria-label="Fechar visualizador"/);
    assert.match(html, /id="detalhes-visualizador-vaga"[^>]*hidden/);
    assert.doesNotMatch(html, /Coment[aá]rios|Deixe um coment[aá]rio/);
    assert.match(html, /<dialog id="dialogo-post-osc"[^>]+aria-labelledby/);
    assert.match(html, /<dialog id="dialogo-oportunidade-osc"[^>]+aria-labelledby/);
    assert.match(html, /id="vaga-status-osc"[^>]*>.*ABERTA.*ENCERRADA.*CANCELADA/s);
    assert.match(html, /id="posts-gestao-erro"[^>]+role="alert"/);
    assert.match(source, /fetchAutenticado\(caminho, opcoes\)/);
    assert.match(source, /\/post\/osc\/\$\{encodeURIComponent\(oscAtual\.id\)\}\/gestao/);
    assert.match(source, /\/post\/osc\/\$\{id\}\/put/);
    assert.match(source, /form\.dataset\.salvando === "true"/);
    assert.match(source, /form\.dataset\.salvando = "true"/);
    assert.match(source, /new FormData\(\)/);
    assert.match(source, /put-com-imagem/);
    assert.match(source, /create-com-imagem/);
    assert.match(source, /fetchAutenticado\(caminho, opcoes\)/);
    assert.match(html, /id="post-imagem-arquivo-osc" type="file" accept="image\/jpeg,image\/png"/);
    assert.match(html, /id="oportunidade-imagem-arquivo-osc" type="file" accept="image\/jpeg,image\/png"/);
    assert.doesNotMatch(html, /URL da imagem|id="post-imagem-osc"|id="oportunidade-imagem-osc"/);
    const renderStart = source.indexOf("function criarCartaoConteudo");
    const renderEnd = source.indexOf("async function carregarMembros", renderStart);
    assert.ok(renderStart >= 0 && renderEnd > renderStart);
    assert.doesNotMatch(source.slice(renderStart, renderEnd), /\.innerHTML\s*=/);
    assert.doesNotMatch(source, /\/admin\/posts|\/admin\/assignments/);
    const publicLoaderStart = source.indexOf("async function carregarConteudoPublico");
    const publicLoaderEnd = source.indexOf("async function carregarConteudoGestao", publicLoaderStart);
    assert.ok(publicLoaderStart >= 0 && publicLoaderEnd > publicLoaderStart);
    const publicLoader = source.slice(publicLoaderStart, publicLoaderEnd);
    assert.match(publicLoader, /\/post\/osc\/\$\{encodeURIComponent\(id\)\}\/publicos/);
    assert.doesNotMatch(publicLoader, /\/gestao/);
    assert.match(source, /separarConteudoPublicoOsc\(/);
    assert.match(source, /function configurarNavegacaoPerfil/);
    assert.match(source, /function configurarVisualizadorConteudoPublico/);
    assert.match(source, /dialogo\.showModal\(\)/);
    assert.match(source, /if \(evento\.key !== "Escape"\) return;\s*evento\.preventDefault\(\);\s*dialogo\.close\("escape"\)/s);
    assert.match(source, /origemVisualizadorConteudo\.focus\(\)/);
    assert.match(source, /body\.style\.overflow = "hidden"/);
});

test("NGO profile orders content before members and moves status changes into an accessible modal", async () => {
    const root = resolve(__dirname, "../..");
    const html = await readFile(resolve(root, "public/pages/ong-profile.html"), "utf8");
    const source = await readFile(resolve(root, "public/components/ong-profile.js"), "utf8");
    const styles = await readFile(resolve(root, "public/styles/ong-profile.css"), "utf8");

    const order = [
        html.indexOf('id="abas-perfil-osc"'),
        html.indexOf('id="painel-publico"'),
        html.indexOf('id="painel-publicacoes"'),
        html.indexOf('id="painel-voluntariado-publico"'),
        html.indexOf('id="gestao-osc"'),
        html.indexOf('id="painel-conteudo"'),
        html.indexOf('id="painel-membros"'),
        html.indexOf('id="painel-dados"'),
        html.indexOf('id="zona-perigo"')
    ];
    assert.ok(order.every(index => index >= 0));
    assert.deepEqual(order, [...order].sort((a, b) => a - b));
    assert.ok(html.indexOf('id="aba-informacoes"') < html.indexOf('id="aba-publicacoes"'));
    assert.ok(html.indexOf('id="aba-publicacoes"') < html.indexOf('id="aba-voluntariado-publico"'));
    assert.ok(html.indexOf('id="aba-voluntariado-publico"') < html.indexOf('id="aba-conteudo"'));
    assert.ok(html.indexOf('id="aba-conteudo"') < html.indexOf('id="aba-membros"'));
    assert.ok(html.indexOf('id="aba-membros"') < html.indexOf('id="aba-dados"'));
    assert.doesNotMatch(source, /function configurarAbasPublicas|function configurarAbas\(/);
    assert.match(html, /id="aba-dados"[^>]*>Configurações</);
    assert.match(html, /id="resumo-dados-osc"/);
    assert.match(html, /id="alterar-dados-osc"[^>]*>Alterar dados/);
    assert.ok(html.indexOf('id="painel-dados"') < html.indexOf('id="zona-perigo"'));
    assert.match(source, /formularioEdicao\.dataset\.carregado !== "true"/);
    assert.match(source, /motivo\.required = justificativaObrigatoria/);

    assert.doesNotMatch(html, /id="gestao-status-osc"/);
    assert.ok(html.indexOf('id="botao-status-osc"') < html.indexOf('id="excluir-osc"'));
    assert.match(html, /<dialog id="dialogo-status-osc"[^>]+aria-labelledby/);
    assert.match(html, /id="motivo-status-osc"[^>]*maxlength="2000"/);
    assert.match(html, /id="cancelar-status-osc"[^>]*>Cancelar/);
    assert.match(html, /id="confirmar-status-osc"/);

    assert.match(source, /dialogo\.showModal\(\)/);
    assert.match(source, /requisicaoAutenticada\(rota,\s*\{\s*method:\s*"PATCH"/);
    assert.match(source, /oscAtual\.podeAdministrarStatusPlataforma/);
    assert.match(source, /botaoConfirmar\.disabled = true/);
    assert.match(source, /dialogo\.addEventListener\("keydown"/);
    assert.match(source, /grupoExcluir\.hidden = !podeExcluir/);
    assert.match(source, /function abaPerfilPorHash\(hash\)/);
    assert.match(source, /hash === "conteudo" \? "gestao-conteudo"/);
    assert.match(source, /function resolverHashInicialPerfil\(\)/);
    assert.match(styles, /overflow-x: auto;[\s\S]*scroll-snap-type: x proximity/);

    assert.match(styles, /\.acoes-hero \.btn-secundario\s*\{/);
    assert.match(styles, /\.acoes-dialogo-conteudo-osc button\s*\{[^}]*margin:0/s);
    assert.match(styles, /\.dialogo-conteudo-osc\s*\{[^}]*transform:none/s);
    assert.match(styles, /\.dialogo-status-osc\s*\{[^}]*transform:none/s);
    assert.match(styles, /@media \(max-width: 520px\)/);
    assert.match(styles, /\.perfil-publico \.btn,/);
    assert.match(styles, /\.imagem-conteudo-preview/);
    assert.ok(styles.includes("grid-template-columns:repeat(3,minmax(0,1fr))"));
    assert.ok(styles.includes("@media (max-width:900px)"));
    assert.ok(styles.includes("@media (max-width:650px)"));
    assert.ok(styles.includes(".miniatura-publica-osc:focus-visible"));
    assert.ok(styles.includes("overflow-y:auto; padding:22px 22px 26px"));
    assert.ok(styles.includes("@media (max-width:350px)"));
    assert.match(source, /function criarPreviewSemImagem/);
    assert.match(source, /function preencherVisualizadorOportunidade/);
});
