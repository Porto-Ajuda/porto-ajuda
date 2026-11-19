import { getAPI_URL } from "./modoBack.js";
import { criarCardVitrine, iniciarCarrossel, VITRINE_MAX_ITENS } from "./vitrine.js";
import { campaignMoney } from "./campaign-money.js";
import { createCampaignDonationDialog } from "./campaign-donation-dialog.js";

const API_URL = getAPI_URL();
const campaignDialog = createCampaignDonationDialog();

const ROTA_PERFIL_ONG = API_URL === "http://localhost:8080"
        || API_URL === "https://api.portoajuda.com.br"
    ? "/ong-profile"
    : "ong-profile.html";

const IMAGEM_PADRAO_ONG = "../assets/ong-padrao.png";

// nome exibido + símbolo (Font Awesome) de cada categoria de ONG
const CATEGORIAS_ONG = {
    EDUCACAO: { nome: "Educação", icone: "fa-graduation-cap" },
    SAUDE: { nome: "Saúde", icone: "fa-heart-pulse" },
    MEIO_AMBIENTE: { nome: "Meio Ambiente", icone: "fa-leaf" },
    ANIMAIS: { nome: "Proteção animal", icone: "fa-paw" },
    INCLUSAO_SOCIAL: { nome: "Inclusão social", icone: "fa-hands-holding-heart" },
    ESPORTE: { nome: "Esporte", icone: "fa-futbol" },
    CULTURA: { nome: "Cultura", icone: "fa-masks-theater" },
    COMBATE_POBREZA: { nome: "Combate à pobreza", icone: "fa-hand-holding-dollar" },
    ALIMENTACAO: { nome: "Alimentação", icone: "fa-utensils" },
    IDOSOS: { nome: "Pessoas idosas", icone: "fa-person-cane" }
};

const CATEGORIA_PADRAO = { nome: "Organização social", icone: "fa-hand-holding-heart" };


/* ---------------------------------------------------------
   BUSCA NA API (sempre a primeira página, no máximo 6 itens)
   --------------------------------------------------------- */

async function buscarItens(endpoint) {
    const resposta = await fetch(`${API_URL}${endpoint}?page=0&size=${VITRINE_MAX_ITENS}`, {
        headers: { Accept: "application/json" },
        cache: "no-store"
    });
    if (!resposta.ok) throw new Error("Não foi possível carregar este conteúdo. Tente novamente.");
    const dados = await resposta.json();
    if (!dados || !Array.isArray(dados.content)) {
        throw new TypeError("Resposta inválida da API");
    }
    return dados.content;
}


/* ---------------------------------------------------------
   MONTAGEM DOS CARDS (dados da API -> card padrão)
   --------------------------------------------------------- */

function criarCardOng(ong) {
    const nome = typeof ong.nome === "string" && ong.nome.trim()
        ? ong.nome.trim()
        : "Organização sem nome";
    const categoria = CATEGORIAS_ONG[ong.causaPrincipal]
        || (Array.isArray(ong.causas) && CATEGORIAS_ONG[ong.causas[0]])
        || CATEGORIA_PADRAO;
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
    carregar: () => buscarItens("/osc/list"),
    criarCard: criarCardOng
});

iniciarCarrossel({
    trilhoId: "cards-publicacoes",
    statusId: "publicacoes-status",
    tentarNovamenteId: "publicacoes-tentar-novamente",
    rotulo: "publicações",
    carregar: () => buscarItens("/post/publicos"),
    criarCard: item => criarCardConteudo(item, "Publicação")
});

iniciarCarrossel({
    trilhoId: "cards-voluntariacao",
    statusId: "voluntariado-status",
    tentarNovamenteId: "voluntariado-tentar-novamente",
    rotulo: "voluntariado",
    carregar: () => buscarItens("/post/voluntariados"),
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
        return campaigns
            .filter(item => item?.state === "ACTIVE" && Number.isSafeInteger(Number(item.id)) && Number(item.id) > 0)
            .slice(0, VITRINE_MAX_ITENS);
    },
    criarCard: criarCardCampanha
});
