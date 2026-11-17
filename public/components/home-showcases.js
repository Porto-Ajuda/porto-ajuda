import { getAPI_URL } from "./modoBack.js";
import { criarCardVitrine, iniciarCarrossel, VITRINE_MAX_ITENS } from "./vitrine.js";

const API_URL = getAPI_URL();

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
    if (!resposta.ok) throw new Error(`HTTP ${resposta.status}`);
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

// Doações: ainda não há API de campanhas verificadas.
// Quando houver, é só trocar este bloco por um iniciarCarrossel(...) igual aos de cima.
document.getElementById("cards-doacao")?.replaceChildren();
const statusDoacoes = document.getElementById("doacoes-status");
if (statusDoacoes) {
    statusDoacoes.textContent = "Ainda não há campanhas de arrecadação verificadas para exibir.";
}
const tentarNovamenteDoacoes = document.getElementById("doacoes-tentar-novamente");
if (tentarNovamenteDoacoes) tentarNovamenteDoacoes.hidden = true;
