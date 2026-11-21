import { getAPI_URL } from "./modoBack.js";
import { criarCardVitrine, iniciarCarrossel } from "./vitrine.js";
import { campaignMoney } from "./campaign-money.js";
import { createCampaignDonationDialog } from "./campaign-donation-dialog.js";
import { CAUSE_TAXONOMY, causeCategoriesForNgo } from "./cause-taxonomy.js";
import { ready as personalizationReady, orderNgoRecommendations } from "./recommendation-personalization.js";
import { fisherYatesShuffle, loadCompleteDataset, uniqueById } from "./home-content-order.js";

const API_URL = getAPI_URL();
const campaignDialog = createCampaignDonationDialog();

const ROTA_PERFIL_ONG = API_URL === "http://localhost:8080"
        || API_URL === "https://api.portoajuda.com.br"
    ? "/ong-profile"
    : "ong-profile.html";

const IMAGEM_PADRAO_ONG = "../assets/ong-padrao.png";

const CATEGORIA_PADRAO = { nome: "Organização social", icone: "fa-hand-holding-heart" };


/* ---------------------------------------------------------
   BUSCA NA API (carrega o conjunto elegível completo)
   --------------------------------------------------------- */

async function buscarItens(endpoint) {
    return loadCompleteDataset(async (page, size) => {
        const url = new URL(endpoint, API_URL);
        url.searchParams.set("page", String(page));
        url.searchParams.set("size", String(size));
        const resposta = await fetch(url, {
            headers: { Accept: "application/json" },
            cache: "no-store"
        });
        if (!resposta.ok) throw new Error("Não foi possível carregar este conteúdo. Tente novamente.");
        return resposta.json();
    });
}

async function buscarItensAleatorios(endpoint) {
    return fisherYatesShuffle(await buscarItens(endpoint));
}

async function buscarTodasOngsParaRecomendacao() {
    const pageSize = 24;
    const firstResponse = await fetch(`${API_URL}/osc/list?page=0&size=${pageSize}`, { headers: { Accept: "application/json" }, cache: "no-store" });
    if (!firstResponse.ok) throw new Error("Não foi possível carregar as ONGs. Tente novamente.");
    const first = await firstResponse.json();
    if (!first || !Array.isArray(first.content) || !Number.isInteger(Number(first.totalPages))) throw new TypeError("Resposta de ONGs inválida");
    const records = [...first.content];
    for (let page = 1; page < Number(first.totalPages); page += 1) {
        const response = await fetch(`${API_URL}/osc/list?page=${page}&size=${pageSize}`, { headers: { Accept: "application/json" }, cache: "no-store" });
        if (!response.ok) throw new Error("Não foi possível concluir o carregamento das ONGs.");
        const result = await response.json();
        if (!result || !Array.isArray(result.content)) throw new TypeError("Resposta de ONGs inválida");
        records.push(...result.content);
    }
    const unique = [...new Map(records.filter(item => item?.status === "ATIVA" && item.arquivada !== true && item.id != null)
        .map(item => [String(item.id), item])).values()];
    if (unique.length !== Number(first.totalElements)) throw new Error("A lista de ONGs mudou durante o carregamento; tente novamente.");
    await personalizationReady();
    return orderNgoRecommendations(unique);
}


/* ---------------------------------------------------------
   MONTAGEM DOS CARDS (dados da API -> card padrão)
   --------------------------------------------------------- */

function criarCardOng(ong) {
    const nome = typeof ong.nome === "string" && ong.nome.trim()
        ? ong.nome.trim()
        : "Organização sem nome";
    const causaId = causeCategoriesForNgo(ong)[0];
    const causa = CAUSE_TAXONOMY.find(item => item.id === causaId);
    const categoria = causa ? { nome: causa.label, icone: causa.icon } : CATEGORIA_PADRAO;
    const local = [ong.cidade, ong.estado].filter(Boolean).join(" - ");

    return criarCardVitrine({
        imagem: ong.logo || IMAGEM_PADRAO_ONG,
        imagemFallback: IMAGEM_PADRAO_ONG,
        icone: categoria.icone,
        categoria: categoria.nome,
        titulo: nome,
        local,
        cta: "Conhecer ONG",
        href: `${ROTA_PERFIL_ONG}?id=${encodeURIComponent(ong.id)}`,
        rotulo: `Conhecer ${nome}, ${categoria.nome}${local ? `, ${local}` : ""}`
    });
}

function criarCardConteudo(item, tipo) {
    const oportunidade = tipo === "Voluntariado";
    const titulo = item.titulo || "Conteúdo público";
    const organizacao = item.nomeOsc || "Organização parceira";
    const cidade = item.vaga?.cidade;

    return criarCardVitrine({
        imagem: item.imagemUrl,
        icone: oportunidade ? "fa-hand-holding-heart" : "fa-newspaper",
        categoria: tipo,
        titulo,
        local: oportunidade && cidade ? cidade : organizacao,
        cta: oportunidade ? "Ver oportunidade" : "Ler publicação",
        rotulo: `Abrir ${oportunidade ? "oportunidade" : "publicação"}: ${titulo}, ${organizacao}`,
        aoClicar: card => {
            window.abrirModalConteudoPublico?.(item, oportunidade ? "voluntariado" : "publicacao", card);
        }
    });
}

function criarCardCampanha(campanha) {
    const titulo = campanha.title || "Campanha de doação";
    const ong = campanha.ngoName || "Organização parceira";
    const valorArrecadado = Number(campanha.raisedNetCents) || 0;
    const metaLiquida = Number(campanha.goalNetCents) || 0;
    const percentual = metaLiquida > 0
        ? Math.max(0, Math.min(100, Math.floor(valorArrecadado * 100 / metaLiquida)))
        : 0;
    const cardItem = criarCardVitrine({
        imagem: campanha.imageUrl,
        icone: "fa-hand-holding-heart",
        categoria: "VAQUINHA",
        titulo,
        local: ong,
        cta: "Ver vaquinha",
        rotulo: `Ver vaquinha ${titulo}, de ${ong}. ${campaignMoney(valorArrecadado)} arrecadados de ${campaignMoney(metaLiquida)} de meta líquida.`,
        aoClicar: card => campaignDialog.open(campanha, card)
    });
    cardItem.classList.add("vitrine-item-campanha");

    const vidro = cardItem.querySelector(".vitrine-vidro");
    const detalhes = document.createElement("div");
    detalhes.className = "vitrine-campanha-detalhes";
    detalhes.setAttribute("aria-label", "Progresso da vaquinha");
    const arrecadado = document.createElement("span");
    arrecadado.className = "vitrine-campanha-arrecadado";
    arrecadado.textContent = `${campaignMoney(valorArrecadado)} arrecadados`;
    const meta = document.createElement("span");
    meta.className = "vitrine-campanha-meta";
    meta.textContent = `Meta líquida: ${campaignMoney(metaLiquida)}`;
    const progresso = document.createElement("progress");
    progresso.className = "vitrine-campanha-progresso";
    progresso.max = 100;
    progresso.value = percentual;
    progresso.setAttribute("aria-label", `${percentual}% da meta líquida`);
    detalhes.append(arrecadado, meta, progresso);
    vidro.insertBefore(detalhes, vidro.querySelector(".vitrine-base"));
    return cardItem;
}


/* ---------------------------------------------------------
   CARROSSÉIS DA HOME
   --------------------------------------------------------- */

iniciarCarrossel({
    trilhoId: "cards-osc",
    statusId: "ongs-status",
    tentarNovamenteId: "ongs-tentar-novamente",
    rotulo: "ONGs",
    carregar: buscarTodasOngsParaRecomendacao,
    criarCard: criarCardOng
});

iniciarCarrossel({
    trilhoId: "cards-publicacoes",
    statusId: "publicacoes-status",
    tentarNovamenteId: "publicacoes-tentar-novamente",
    rotulo: "publicações",
    carregar: () => buscarItensAleatorios("/post/catalogo/publicacoes"),
    criarCard: item => criarCardConteudo(item, "Publicação")
});

iniciarCarrossel({
    trilhoId: "cards-voluntariacao",
    statusId: "voluntariado-status",
    tentarNovamenteId: "voluntariado-tentar-novamente",
    rotulo: "voluntariado",
    carregar: () => buscarItensAleatorios("/post/catalogo/voluntariados"),
    criarCard: item => criarCardConteudo(item, "Voluntariado")
});

iniciarCarrossel({
    trilhoId: "cards-doacao",
    statusId: "doacoes-status",
    tentarNovamenteId: "doacoes-tentar-novamente",
    rotulo: "doações",
    carregar: async () => {
        const response = await fetch(`${API_URL}/donation-campaigns`, {
            headers: { Accept: "application/json" },
            cache: "no-store"
        });
        if (!response.ok) throw new Error("Não foi possível carregar as vaquinhas. Tente novamente.");
        const campaigns = await response.json();
        if (!Array.isArray(campaigns)) throw new TypeError("Resposta de campanhas inválida.");
        // A API já aplica elegibilidade pública da campanha e da ONG; preserve
        // ACTIVE como defesa de estado e não exiba estados de gestão.
        const eligible = uniqueById(campaigns
            .filter(item => item?.state === "ACTIVE" && Number.isSafeInteger(Number(item.id)) && Number(item.id) > 0));
        return fisherYatesShuffle(eligible);
    },
    criarCard: criarCardCampanha
});
