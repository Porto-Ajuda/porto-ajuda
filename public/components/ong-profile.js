import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado, sessaoInvalidaConfirmada } from "./api.js";
import { obterEstadoSessao } from "./auth.js";
import { confirmarPorDigitacao, notificar as notificarSistema } from "./confirm-modal.js";
import { respostaConteudoOscValida, separarConteudoOsc, separarConteudoPublicoOsc, urlImagemSegura, validarArquivoImagem, validarConteudoOsc } from "./ong-content.js";
import { obterTokenCSRF } from "./auth.js";
const API_URL = getAPI_URL();
let oscAtual = null;
let cargoAtual = null;
let conteudoOscAtual = [];
let edicaoPostOsc = null;
let edicaoOportunidadeOsc = null;
const imagensConteudoOsc = {
    post: { arquivo: null, urlPreview: null, urlAtual: null },
    oportunidade: { arquivo: null, urlPreview: null, urlAtual: null }
};
let origemVisualizadorConteudo = null;
let overflowAnteriorVisualizador = null;
let requisicaoConteudoPublicoAtual = 0;
let membroOscConfirmado = false;
let verificacaoMembroOscConcluida = false;
let verificacaoConteudoPublicoConcluida = false;
let hashInicialPerfil = window.location.hash.slice(1);

// ========================================
// DADOS DAS ODS
// ========================================

const dadosODS = {
    1: {
        titulo: "ErradicaÃ§Ã£o da Pobreza",
        descricao: "Erradicar a pobreza em todas as suas formas e em todos os lugares.",
        busca: "A ODS 1 busca reduzir a pobreza em suas diferentes dimensÃµes, ampliar a proteÃ§Ã£o social e garantir que pessoas em situaÃ§Ã£o de vulnerabilidade tenham acesso a recursos, serviÃ§os bÃ¡sicos e oportunidades.",
        metas: [
            "Erradicar a pobreza extrema.",
            "Reduzir significativamente a proporÃ§Ã£o de pessoas vivendo na pobreza.",
            "Ampliar sistemas e medidas de proteÃ§Ã£o social.",
            "Garantir acesso a serviÃ§os bÃ¡sicos e recursos econÃ´micos.",
            "Aumentar a resiliÃªncia de pessoas em situaÃ§Ã£o de vulnerabilidade."
        ],
        contribuicao: "ONGs podem contribuir oferecendo assistÃªncia social, alimentaÃ§Ã£o, moradia, capacitaÃ§Ã£o, acesso a serviÃ§os e apoio direto a pessoas e famÃ­lias em situaÃ§Ã£o de vulnerabilidade."
    },

    2: {
        titulo: "Fome Zero e Agricultura SustentÃ¡vel",
        descricao: "Acabar com a fome, alcanÃ§ar a seguranÃ§a alimentar e melhoria da nutriÃ§Ã£o e promover a agricultura sustentÃ¡vel.",
        busca: "A ODS 2 busca garantir que todas as pessoas tenham acesso regular a alimentos seguros, nutritivos e suficientes, alÃ©m de combater todas as formas de mÃ¡ nutriÃ§Ã£o e promover sistemas alimentares sustentÃ¡veis.",
        metas: [
            "Acabar com a fome e garantir acesso a alimentos seguros e nutritivos.",
            "Combater todas as formas de mÃ¡ nutriÃ§Ã£o.",
            "Promover uma agricultura sustentÃ¡vel e produtiva.",
            "Aumentar a produtividade e a renda de pequenos produtores.",
            "Preservar a diversidade genÃ©tica de sementes, plantas e animais."
        ],
        contribuicao: "ONGs podem contribuir por meio da distribuiÃ§Ã£o de alimentos, cozinhas comunitÃ¡rias, combate ao desperdÃ­cio, apoio a agricultores e iniciativas de seguranÃ§a alimentar."
    },

    3: {
        titulo: "SaÃºde e Bem-Estar",
        descricao: "Assegurar uma vida saudÃ¡vel e promover o bem-estar para todas as pessoas, em todas as idades.",
        busca: "A ODS 3 busca melhorar a saÃºde da populaÃ§Ã£o, reduzir mortes evitÃ¡veis, combater doenÃ§as e ampliar o acesso a serviÃ§os de saÃºde e prevenÃ§Ã£o.",
        metas: [
            "Reduzir a mortalidade materna.",
            "Reduzir mortes evitÃ¡veis de recÃ©m-nascidos e crianÃ§as.",
            "Combater doenÃ§as transmissÃ­veis.",
            "Promover a saÃºde mental e o bem-estar.",
            "Ampliar o acesso universal aos serviÃ§os de saÃºde."
        ],
        contribuicao: "ONGs podem atuar com prevenÃ§Ã£o, atendimento, campanhas de saÃºde, apoio psicolÃ³gico, orientaÃ§Ã£o, promoÃ§Ã£o de hÃ¡bitos saudÃ¡veis e encaminhamento para serviÃ§os pÃºblicos."
    },

    4: {
        titulo: "EducaÃ§Ã£o de Qualidade",
        descricao: "Assegurar a educaÃ§Ã£o inclusiva, equitativa e de qualidade e promover oportunidades de aprendizagem ao longo da vida.",
        busca: "A ODS 4 busca garantir que todas as pessoas tenham acesso a uma educaÃ§Ã£o de qualidade, inclusiva e equitativa.",
        metas: [
            "Garantir educaÃ§Ã£o bÃ¡sica gratuita e de qualidade.",
            "Ampliar o acesso Ã  educaÃ§Ã£o infantil.",
            "Aumentar o acesso ao ensino tÃ©cnico e superior.",
            "Desenvolver competÃªncias relevantes para o trabalho.",
            "Promover educaÃ§Ã£o para o desenvolvimento sustentÃ¡vel."
        ],
        contribuicao: "ONGs podem oferecer reforÃ§o escolar, cursos, oficinas, bibliotecas, acesso Ã  tecnologia, capacitaÃ§Ã£o profissional e atividades educativas."
    },

    5: {
        titulo: "Igualdade de GÃªnero",
        descricao: "AlcanÃ§ar a igualdade de gÃªnero e empoderar todas as mulheres e meninas.",
        busca: "A ODS 5 busca eliminar a discriminaÃ§Ã£o e a violÃªncia contra mulheres e meninas e ampliar sua participaÃ§Ã£o e autonomia.",
        metas: [
            "Eliminar todas as formas de discriminaÃ§Ã£o contra mulheres e meninas.",
            "Combater a violÃªncia e a exploraÃ§Ã£o.",
            "Eliminar prÃ¡ticas prejudiciais Ã s meninas e mulheres.",
            "Reconhecer e valorizar o trabalho de cuidado.",
            "Promover a participaÃ§Ã£o e a igualdade de oportunidades."
        ],
        contribuicao: "ONGs podem oferecer acolhimento, orientaÃ§Ã£o, capacitaÃ§Ã£o, apoio social, educaÃ§Ã£o e serviÃ§os de proteÃ§Ã£o para mulheres e meninas."
    },

    6: {
        titulo: "Ãgua PotÃ¡vel e Saneamento",
        descricao: "Assegurar a disponibilidade e a gestÃ£o sustentÃ¡vel da Ã¡gua e do saneamento para todas as pessoas.",
        busca: "A ODS 6 busca garantir acesso universal Ã  Ã¡gua potÃ¡vel, saneamento e higiene, alÃ©m de promover a gestÃ£o sustentÃ¡vel dos recursos hÃ­dricos.",
        metas: [
            "Garantir acesso universal Ã  Ã¡gua potÃ¡vel.",
            "Garantir saneamento e higiene adequados.",
            "Melhorar a qualidade da Ã¡gua.",
            "Aumentar a eficiÃªncia no uso da Ã¡gua.",
            "Proteger ecossistemas relacionados Ã  Ã¡gua."
        ],
        contribuicao: "ONGs podem desenvolver projetos de saneamento, educaÃ§Ã£o ambiental, acesso Ã  Ã¡gua, preservaÃ§Ã£o de recursos hÃ­dricos e higiene."
    },

    7: {
        titulo: "Energia Limpa e AcessÃ­vel",
        descricao: "Assegurar o acesso confiÃ¡vel, sustentÃ¡vel, moderno e a preÃ§o acessÃ­vel Ã  energia para todas as pessoas.",
        busca: "A ODS 7 busca ampliar o acesso Ã  energia e aumentar a participaÃ§Ã£o de fontes renovÃ¡veis e tecnologias mais eficientes.",
        metas: [
            "Garantir acesso universal Ã  energia.",
            "Aumentar a participaÃ§Ã£o das energias renovÃ¡veis.",
            "Melhorar a eficiÃªncia energÃ©tica.",
            "Ampliar o acesso a tecnologias de energia limpa.",
            "Fortalecer a infraestrutura energÃ©tica sustentÃ¡vel."
        ],
        contribuicao: "ONGs podem atuar com educaÃ§Ã£o energÃ©tica, projetos de energia solar, eficiÃªncia energÃ©tica e acesso Ã  energia em comunidades vulnerÃ¡veis."
    },

    8: {
        titulo: "Trabalho Decente e Crescimento EconÃ´mico",
        descricao: "Promover o crescimento econÃ´mico sustentado, inclusivo e sustentÃ¡vel, emprego pleno e produtivo e trabalho decente para todas as pessoas.",
        busca: "A ODS 8 busca promover oportunidades de trabalho digno, crescimento econÃ´mico sustentÃ¡vel e condiÃ§Ãµes adequadas de trabalho.",
        metas: [
            "Promover crescimento econÃ´mico sustentÃ¡vel.",
            "Aumentar oportunidades de trabalho decente.",
            "Reduzir o desemprego entre jovens.",
            "Eliminar trabalho forÃ§ado e trabalho infantil.",
            "Proteger direitos trabalhistas."
        ],
        contribuicao: "ONGs podem oferecer capacitaÃ§Ã£o profissional, orientaÃ§Ã£o para o mercado de trabalho, empreendedorismo e programas de inclusÃ£o produtiva."
    },

    9: {
        titulo: "IndÃºstria, InovaÃ§Ã£o e Infraestrutura",
        descricao: "Construir infraestruturas resilientes, promover a industrializaÃ§Ã£o inclusiva e sustentÃ¡vel e fomentar a inovaÃ§Ã£o.",
        busca: "A ODS 9 busca desenvolver infraestrutura sustentÃ¡vel, estimular a inovaÃ§Ã£o e ampliar o acesso a tecnologias e oportunidades econÃ´micas.",
        metas: [
            "Desenvolver infraestrutura sustentÃ¡vel e resiliente.",
            "Promover industrializaÃ§Ã£o inclusiva.",
            "Ampliar o acesso de pequenas empresas a recursos.",
            "Modernizar infraestruturas de forma sustentÃ¡vel.",
            "Aumentar a pesquisa e a inovaÃ§Ã£o."
        ],
        contribuicao: "ONGs podem desenvolver projetos tecnolÃ³gicos, capacitaÃ§Ã£o digital, inovaÃ§Ã£o social e iniciativas que ampliem o acesso Ã  infraestrutura."
    },

    10: {
        titulo: "ReduÃ§Ã£o das Desigualdades",
        descricao: "Reduzir a desigualdade dentro dos paÃ­ses e entre eles.",
        busca: "A ODS 10 busca promover a inclusÃ£o social, econÃ´mica e polÃ­tica e reduzir desigualdades relacionadas Ã  renda e Ã s condiÃ§Ãµes sociais.",
        metas: [
            "Aumentar a renda da populaÃ§Ã£o mais pobre.",
            "Promover inclusÃ£o social, econÃ´mica e polÃ­tica.",
            "Garantir igualdade de oportunidades.",
            "Reduzir prÃ¡ticas discriminatÃ³rias.",
            "Facilitar migraÃ§Ã£o segura e responsÃ¡vel."
        ],
        contribuicao: "ONGs podem atuar diretamente com populaÃ§Ãµes vulnerÃ¡veis, oferecendo assistÃªncia social, educaÃ§Ã£o, inclusÃ£o profissional e acesso a direitos."
    },

    11: {
        titulo: "Cidades e Comunidades SustentÃ¡veis",
        descricao: "Tornar as cidades e os assentamentos humanos inclusivos, seguros, resilientes e sustentÃ¡veis.",
        busca: "A ODS 11 busca melhorar as cidades, tornando-as mais acessÃ­veis, seguras, inclusivas e sustentÃ¡veis para todas as pessoas.",
        metas: [
            "Garantir acesso Ã  habitaÃ§Ã£o adequada.",
            "Melhorar o transporte pÃºblico.",
            "Promover urbanizaÃ§Ã£o inclusiva e sustentÃ¡vel.",
            "Proteger o patrimÃ´nio cultural e natural.",
            "Reduzir impactos ambientais das cidades."
        ],
        contribuicao: "ONGs podem trabalhar com moradia, mobilidade, espaÃ§os pÃºblicos, meio ambiente, inclusÃ£o urbana e apoio a comunidades."
    },

    12: {
        titulo: "Consumo e ProduÃ§Ã£o ResponsÃ¡veis",
        descricao: "Assegurar padrÃµes de produÃ§Ã£o e de consumo sustentÃ¡veis.",
        busca: "A ODS 12 busca reduzir o desperdÃ­cio e tornar a produÃ§Ã£o e o consumo mais eficientes e sustentÃ¡veis.",
        metas: [
            "Promover uso eficiente dos recursos naturais.",
            "Reduzir desperdÃ­cio de alimentos.",
            "Diminuir a geraÃ§Ã£o de resÃ­duos.",
            "Estimular reciclagem e reutilizaÃ§Ã£o.",
            "Promover prÃ¡ticas empresariais sustentÃ¡veis."
        ],
        contribuicao: "ONGs podem atuar com reciclagem, reaproveitamento, educaÃ§Ã£o ambiental, combate ao desperdÃ­cio e consumo consciente."
    },

    13: {
        titulo: "AÃ§Ã£o Contra a MudanÃ§a Global do Clima",
        descricao: "Tomar medidas urgentes para combater a mudanÃ§a climÃ¡tica e seus impactos.",
        busca: "A ODS 13 busca fortalecer a capacidade de adaptaÃ§Ã£o Ã s mudanÃ§as climÃ¡ticas e ampliar aÃ§Ãµes de educaÃ§Ã£o, conscientizaÃ§Ã£o e planejamento climÃ¡tico.",
        metas: [
            "Fortalecer a adaptaÃ§Ã£o Ã s mudanÃ§as climÃ¡ticas.",
            "Aumentar a resiliÃªncia diante de eventos climÃ¡ticos.",
            "Integrar medidas climÃ¡ticas Ã s polÃ­ticas pÃºblicas.",
            "Ampliar educaÃ§Ã£o e conscientizaÃ§Ã£o climÃ¡tica.",
            "Mobilizar recursos para aÃ§Ãµes climÃ¡ticas."
        ],
        contribuicao: "ONGs podem atuar com educaÃ§Ã£o ambiental, recuperaÃ§Ã£o de Ã¡reas degradadas, prevenÃ§Ã£o de riscos e projetos de adaptaÃ§Ã£o climÃ¡tica."
    },

    14: {
        titulo: "Vida na Ãgua",
        descricao: "Conservar e usar de forma sustentÃ¡vel os oceanos, mares e recursos marinhos para o desenvolvimento sustentÃ¡vel.",
        busca: "A ODS 14 busca proteger os ecossistemas marinhos, combater a poluiÃ§Ã£o e promover o uso sustentÃ¡vel dos oceanos e recursos marinhos.",
        metas: [
            "Reduzir a poluiÃ§Ã£o dos oceanos.",
            "Proteger ecossistemas marinhos e costeiros.",
            "Combater a pesca predatÃ³ria.",
            "Promover pesca sustentÃ¡vel.",
            "Conservar Ã¡reas marinhas."
        ],
        contribuicao: "ONGs podem desenvolver aÃ§Ãµes de limpeza de praias, educaÃ§Ã£o ambiental, proteÃ§Ã£o da fauna marinha e preservaÃ§Ã£o de ecossistemas costeiros."
    },

    15: {
        titulo: "Vida Terrestre",
        descricao: "Proteger, recuperar e promover o uso sustentÃ¡vel dos ecossistemas terrestres, combater a desertificaÃ§Ã£o e deter a perda de biodiversidade.",
        busca: "A ODS 15 busca preservar florestas, ecossistemas terrestres e biodiversidade, alÃ©m de combater a degradaÃ§Ã£o do solo e a perda de espÃ©cies.",
        metas: [
            "Proteger ecossistemas terrestres.",
            "Promover manejo sustentÃ¡vel das florestas.",
            "Combater a desertificaÃ§Ã£o.",
            "Reduzir a perda de biodiversidade.",
            "Combater o trÃ¡fico de espÃ©cies protegidas."
        ],
        contribuicao: "ONGs podem trabalhar com proteÃ§Ã£o animal, reflorestamento, conservaÃ§Ã£o ambiental, recuperaÃ§Ã£o de Ã¡reas e educaÃ§Ã£o ambiental."
    },

    16: {
        titulo: "Paz, JustiÃ§a e InstituiÃ§Ãµes Eficazes",
        descricao: "Promover sociedades pacÃ­ficas e inclusivas, proporcionar acesso Ã  justiÃ§a para todos e construir instituiÃ§Ãµes eficazes, responsÃ¡veis e inclusivas.",
        busca: "A ODS 16 busca promover sociedades pacÃ­ficas, reduzir a violÃªncia, ampliar o acesso Ã  justiÃ§a e fortalecer instituiÃ§Ãµes transparentes e responsÃ¡veis.",
        metas: [
            "Reduzir violÃªncia e mortes relacionadas Ã  violÃªncia.",
            "Combater abuso, exploraÃ§Ã£o e trÃ¡fico de pessoas.",
            "Garantir acesso igualitÃ¡rio Ã  justiÃ§a.",
            "Reduzir corrupÃ§Ã£o e suborno.",
            "Garantir acesso pÃºblico Ã  informaÃ§Ã£o."
        ],
        contribuicao: "ONGs podem atuar na defesa de direitos, assistÃªncia jurÃ­dica, proteÃ§Ã£o de grupos vulnerÃ¡veis, prevenÃ§Ã£o da violÃªncia e fortalecimento da cidadania."
    },

    17: {
        titulo: "Parcerias e Meios de ImplementaÃ§Ã£o",
        descricao: "Fortalecer os meios de implementaÃ§Ã£o e revitalizar a parceria global para o desenvolvimento sustentÃ¡vel.",
        busca: "A ODS 17 reconhece que o cumprimento dos demais objetivos depende de cooperaÃ§Ã£o, recursos, tecnologia, conhecimento e parcerias entre governos, organizaÃ§Ãµes e sociedade civil.",
        metas: [
            "Mobilizar recursos financeiros para o desenvolvimento sustentÃ¡vel.",
            "Ampliar cooperaÃ§Ã£o cientÃ­fica e tecnolÃ³gica.",
            "Fortalecer capacidades institucionais.",
            "Promover um sistema de comÃ©rcio mais justo.",
            "Fortalecer parcerias entre governos, empresas e sociedade civil."
        ],
        contribuicao: "ONGs podem estabelecer parcerias, compartilhar conhecimento, mobilizar voluntÃ¡rios e recursos e trabalhar conjuntamente com outras organizaÃ§Ãµes."
    }
};


// ========================================
// CRIAR MODAL DAS ODS
// ========================================


const NOMES_CAUSAS = {
    EDUCACAO: "Educação",
    SAUDE: "Saúde",
    MEIO_AMBIENTE: "Meio ambiente",
    ANIMAIS: "Proteção animal",
    INCLUSAO_SOCIAL: "Inclusão social",
    ESPORTE: "Esporte",
    CULTURA: "Cultura",
    COMBATE_POBREZA: "Combate à pobreza"
};

const modalODS = document.createElement("div");
modalODS.className = "modal-ods";
modalODS.hidden = true;
modalODS.setAttribute("role", "dialog");
modalODS.setAttribute("aria-modal", "true");
modalODS.setAttribute("aria-labelledby", "modal-ods-titulo");
modalODS.innerHTML = `
    <div class="modal-ods-caixa" tabindex="-1">
        <div class="modal-ods-cabecalho">
            <div class="modal-ods-cabecalho-titulo">
                <span class="modal-ods-numero"></span>
                <h2 class="modal-ods-titulo" id="modal-ods-titulo"></h2>
            </div>
            <button class="fechar-modal-ods" type="button" aria-label="Fechar informações da ODS">&times;</button>
        </div>
        <div class="modal-ods-scroll">
            <div class="modal-ods-conteudo">
                <div class="modal-ods-imagem"><img class="modal-ods-img" alt=""></div>
                <div class="modal-ods-informacoes">
                    <p class="modal-ods-descricao"></p>
                    <h3>O que essa ODS busca?</h3><p class="modal-ods-busca"></p>
                    <h3>Principais metas</h3><ul class="modal-ods-metas"></ul>
                    <h3>Como uma OSC pode contribuir?</h3><p class="modal-ods-contribuicao"></p>
                </div>
            </div>
        </div>
    </div>`;
document.body.appendChild(modalODS);

let acionadorModalODS = null;
function fecharModalODS() {
    if (modalODS.hidden) return;
    modalODS.hidden = true;
    modalODS.classList.remove("aberto");
    document.body.style.overflow = "";
    document.querySelector(".perfil-publico")?.removeAttribute("inert");
    document.querySelector(".navbar")?.removeAttribute("inert");
    document.querySelector("#footer")?.removeAttribute("inert");
    acionadorModalODS?.focus();
}

function abrirModalODS(numero, acionador) {
    const dados = dadosODS[numero];
    if (!dados) return;
    acionadorModalODS = acionador;
    const imagem = modalODS.querySelector(".modal-ods-img");
    imagem.src = `../assets/ods${numero}.png`;
    imagem.alt = `ODS ${numero}: ${dados.titulo}`;
    modalODS.querySelector(".modal-ods-numero").textContent = `ODS ${numero}`;
    modalODS.querySelector(".modal-ods-titulo").textContent = dados.titulo;
    modalODS.querySelector(".modal-ods-descricao").textContent = dados.descricao;
    modalODS.querySelector(".modal-ods-busca").textContent = dados.busca;
    modalODS.querySelector(".modal-ods-contribuicao").textContent = dados.contribuicao;
    const metas = modalODS.querySelector(".modal-ods-metas");
    metas.replaceChildren(...dados.metas.map(meta => {
        const item = document.createElement("li");
        item.textContent = meta;
        return item;
    }));
    modalODS.hidden = false;
    modalODS.classList.add("aberto");
    document.body.style.overflow = "hidden";
    document.querySelector(".perfil-publico")?.setAttribute("inert", "");
    document.querySelector(".navbar")?.setAttribute("inert", "");
    document.querySelector("#footer")?.setAttribute("inert", "");
    modalODS.querySelector(".fechar-modal-ods").focus();
}

modalODS.querySelector(".fechar-modal-ods").addEventListener("click", fecharModalODS);
modalODS.addEventListener("click", evento => {
    if (evento.target === modalODS) fecharModalODS();
});
document.addEventListener("keydown", evento => {
    if (modalODS.hidden) return;
    if (evento.key === "Escape") {
        fecharModalODS();
    } else if (evento.key === "Tab") {
        const controles = [...modalODS.querySelectorAll("button, a[href], [tabindex]:not([tabindex='-1'])")];
        if (!controles.length) return;
        const primeiro = controles[0];
        const ultimo = controles.at(-1);
        if (evento.shiftKey && document.activeElement === primeiro) {
            evento.preventDefault();
            ultimo.focus();
        } else if (!evento.shiftKey && document.activeElement === ultimo) {
            evento.preventDefault();
            primeiro.focus();
        }
    }
});

function textoLimpo(valor) {
    return typeof valor === "string" ? valor.trim() : "";
}

function adicionarItem(lista, rotulo, valor) {
    const texto = textoLimpo(valor);
    if (!texto) return false;
    const item = document.createElement("li");
    const titulo = document.createElement("strong");
    titulo.textContent = `${rotulo}: `;
    item.append(titulo, document.createTextNode(texto));
    lista.appendChild(item);
    return true;
}

function urlSegura(valor) {
    let texto = textoLimpo(valor);
    if (!texto) return null;
    if (!/^[a-z][a-z\d+.-]*:/i.test(texto)) texto = `https://${texto}`;
    try {
        const url = new URL(texto);
        return ["http:", "https:"].includes(url.protocol) && !url.username && !url.password
            ? url
            : null;
    } catch {
        return null;
    }
}

function adicionarContato(lista, rotulo, valor, href, externo = false) {
    const texto = textoLimpo(valor);
    if (!texto || !href) return false;
    const item = document.createElement("li");
    const link = document.createElement("a");
    link.textContent = `${rotulo}: ${texto}`;
    link.href = href;
    if (externo) {
        link.target = "_blank";
        link.rel = "noopener noreferrer";
    }
    item.appendChild(link);
    lista.appendChild(item);
    return true;
}

function renderizarContato(osc) {
    const lista = document.getElementById("contatos-lista");
    lista.replaceChildren();
    let quantidade = 0;
    const email = textoLimpo(osc.email);
    if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        quantidade += Number(adicionarContato(lista, "E-mail institucional", email, `mailto:${email}`));
    }
    const telefone = textoLimpo(osc.telefone);
    const digitosTelefone = telefone.replace(/\D/g, "");
    if (digitosTelefone.length >= 8) {
        quantidade += Number(adicionarContato(lista, "Telefone", telefone, `tel:${telefone.replace(/[^\d+]/g, "")}`));
    }
    const whatsapp = textoLimpo(osc.whatsapp);
    let digitosWhatsApp = whatsapp.replace(/\D/g, "");
    if (digitosWhatsApp.length === 10 || digitosWhatsApp.length === 11) digitosWhatsApp = `55${digitosWhatsApp}`;
    if (digitosWhatsApp.length >= 10) {
        quantidade += Number(adicionarContato(lista, "WhatsApp", whatsapp, `https://wa.me/${digitosWhatsApp}`, true));
    }
    for (const [campo, rotulo] of [["site", "Site"], ["instagram", "Instagram"], ["linkedin", "LinkedIn"], ["youtube", "YouTube"]]) {
        const url = urlSegura(osc[campo]);
        if (url) quantidade += Number(adicionarContato(lista, rotulo, url.hostname.replace(/^www\./, ""), url.href, true));
    }
    document.getElementById("contato-vazio").hidden = quantidade > 0;
}

function renderizarFuncionamento(osc) {
    const lista = document.getElementById("funcionamento-lista");
    lista.replaceChildren();
    let quantidade = 0;
    quantidade += Number(adicionarItem(lista, "Horários informados", osc.diasHorariosFuncionamento));
    if (!textoLimpo(osc.diasHorariosFuncionamento)) {
        const abertura = textoLimpo(osc.horarioAbertura).slice(0, 5);
        const fechamento = textoLimpo(osc.horarioFechamento).slice(0, 5);
        if (abertura && fechamento) quantidade += Number(adicionarItem(lista, "Horário", `${abertura}–${fechamento}`));
    }
    for (const [campo, rotulo] of [["atendimentoPresencial", "Atendimento presencial"], ["atendimentoOnline", "Atendimento online"], ["necessitaAgendamento", "Agendamento necessário"]]) {
        if (typeof osc[campo] === "boolean") {
            quantidade += Number(adicionarItem(lista, rotulo, osc[campo] ? "Sim" : "Não"));
        }
    }
    document.getElementById("funcionamento-vazio").hidden = quantidade > 0;
}

function renderizarVoluntariado(osc) {
    const lista = document.getElementById("voluntariado-lista");
    lista.replaceChildren();
    const status = document.getElementById("voluntariado-status");
    const detalhes = [];
    if (typeof osc.aceitaVoluntarios === "boolean") {
        status.textContent = osc.aceitaVoluntarios
            ? "Esta organização informa que aceita pessoas voluntárias."
            : "Esta organização informa que não está aceitando pessoas voluntárias.";
    } else {
        status.textContent = "A disponibilidade de voluntariado não foi informada.";
    }
    if (osc.aceitaVoluntarios === true) {
        if (osc.frequenciaVoluntariado) detalhes.push(["Frequência", osc.frequenciaVoluntariado]);
        if (Number.isInteger(osc.idadeMinimaVoluntario) && osc.idadeMinimaVoluntario >= 0) detalhes.push(["Idade mínima", `${osc.idadeMinimaVoluntario} anos`]);
        if (typeof osc.processoSeletivoVoluntario === "boolean") detalhes.push(["Processo seletivo", osc.processoSeletivoVoluntario ? "Sim" : "Não"]);
        if (typeof osc.emiteCertificadoVoluntario === "boolean") detalhes.push(["Emite certificado", osc.emiteCertificadoVoluntario ? "Sim" : "Não"]);
        for (const [rotulo, valor] of detalhes) adicionarItem(lista, rotulo, valor);
        const atividades = typeof osc.atividadesVoluntariado === "string"
            ? osc.atividadesVoluntariado.split(/[\r\n;]+/).map(item => item.trim()).filter(Boolean)
            : [];
        for (const atividade of atividades) adicionarItem(lista, "Atividade", atividade);
    }
    document.getElementById("voluntariado-vazio").hidden = lista.children.length > 0;
}

function renderizarODS(osc) {
    const container = document.getElementById("ods-container");
    container.replaceChildren();
    const valores = Array.isArray(osc.ods) ? [...new Set(osc.ods.map(Number))] : [];
    const validos = valores.filter(numero => Number.isInteger(numero) && numero >= 1 && numero <= 17 && dadosODS[numero]);
    if (!validos.length) {
        const aviso = document.createElement("p");
        aviso.className = "texto-ausente";
        aviso.textContent = valores.length ? "Os ODS cadastrados não puderam ser reconhecidos." : "Nenhum ODS foi cadastrado para esta organização.";
        container.appendChild(aviso);
        return;
    }
    for (const numero of validos) {
        const botao = document.createElement("button");
        botao.type = "button";
        botao.className = "ods-item";
        botao.dataset.ods = String(numero);
        botao.setAttribute("aria-haspopup", "dialog");
        botao.setAttribute("aria-label", `Ver informações sobre a ODS ${numero}: ${dadosODS[numero].titulo}`);
        const imagem = document.createElement("img");
        imagem.src = `../assets/ods${numero}.png`;
        imagem.alt = "";
        imagem.onerror = () => {
            botao.remove();
            if (container.querySelectorAll(".ods-item").length === 0) {
                const aviso = document.createElement("p");
                aviso.className = "texto-ausente";
                aviso.textContent = "Não foi possível carregar os ícones dos ODS desta organização.";
                container.replaceChildren(aviso);
            }
        };
        botao.appendChild(imagem);
        botao.addEventListener("click", () => abrirModalODS(numero, botao));
        container.appendChild(botao);
    }
}

function renderizarCausas(osc) {
    const principal = document.getElementById("causa-principal");
    const secundarias = document.getElementById("causas-secundarias");
    const enumCausas = Object.keys(NOMES_CAUSAS);
    principal.textContent = "";
    secundarias.textContent = "";
    secundarias.hidden = true;
    let causaPrincipal = enumCausas.includes(osc.causaPrincipal) ? osc.causaPrincipal : null;
    let causasSecundarias = Array.isArray(osc.causasSecundarias)
        ? osc.causasSecundarias.filter(valor => enumCausas.includes(valor))
        : [];
    if (!causaPrincipal && causasSecundarias.length === 0 && Array.isArray(osc.causas)) {
        causasSecundarias = osc.causas.filter(valor => enumCausas.includes(valor));
    }
    causasSecundarias = [...new Set(causasSecundarias)].filter(valor => valor !== causaPrincipal);
    principal.textContent = causaPrincipal ? `Causa principal: ${NOMES_CAUSAS[causaPrincipal]}` : "Causa principal não informada";
    if (causasSecundarias.length) {
        secundarias.textContent = `Outras causas: ${causasSecundarias.map(valor => NOMES_CAUSAS[valor]).join(" · ")}`;
        secundarias.hidden = false;
    }
}

function urlPublicaPerfil(id) {
    const url = new URL("/ong-profile", "https://portoajuda.com.br");
    url.searchParams.set("id", String(id));
    return url.href;
}

async function copiarTexto(texto) {
    if (navigator.clipboard?.writeText && window.isSecureContext) {
        await navigator.clipboard.writeText(texto);
        return;
    }
    const campo = document.createElement("textarea");
    campo.value = texto;
    campo.setAttribute("readonly", "");
    campo.style.position = "fixed";
    campo.style.opacity = "0";
    document.body.appendChild(campo);
    campo.select();
    const copiado = document.execCommand("copy");
    campo.remove();
    if (!copiado) throw new Error("Cópia indisponível");
}

function configurarCompartilhamento(osc) {
    const botao = document.getElementById("compartilhar");
    const feedback = document.getElementById("mensagem-compartilhar");
    botao.addEventListener("click", async () => {
        const url = urlPublicaPerfil(osc.id);
        feedback.textContent = "";
        if (navigator.share) {
            try {
                await navigator.share({ title: osc.nome || "Perfil de OSC", text: "Conheça esta organização no Porto Ajuda.", url });
                feedback.textContent = "Link compartilhado.";
                return;
            } catch (erro) {
                if (erro?.name === "AbortError") return;
            }
        }
        try {
            await copiarTexto(url);
            feedback.textContent = "Link público copiado.";
        } catch {
            feedback.textContent = "Não foi possível compartilhar o link neste navegador.";
        }
    });
}

function renderizarPerfil(osc) {
    document.title = `${textoLimpo(osc.nome) || "Organização"} | Porto Ajuda`;
    const avisoStatus = document.getElementById("estado-status-osc");
    avisoStatus.hidden = osc.status !== "INATIVA" && !osc.arquivada;
    avisoStatus.querySelector("strong").textContent = osc.arquivada
        ? "Organização arquivada"
        : "Organização inativa";
    avisoStatus.querySelector("p").textContent = osc.arquivada
        ? "O registro e o histórico foram preservados. A organização não aparece nas áreas públicas."
        : "Este perfil está disponível para seus membros, mas não aparece nas áreas públicas do Porto Ajuda.";
    document.getElementById("nome-ong").textContent = textoLimpo(osc.nome) || "Organização da sociedade civil";
    const descricaoCurta = document.getElementById("descricao-texto");
    descricaoCurta.textContent = textoLimpo(osc.descricaoCurta);
    descricaoCurta.hidden = !textoLimpo(osc.descricaoCurta);
    document.getElementById("sobre-texto").textContent = textoLimpo(osc.descricao) || "A organização não cadastrou uma apresentação institucional.";
    if (Number.isInteger(osc.anoFundacao) && osc.anoFundacao > 0) {
        const ano = document.getElementById("ano-fundacao");
        ano.textContent = `Fundada em ${osc.anoFundacao}`;
        ano.hidden = false;
    }
    renderizarCausas(osc);

    const logo = document.getElementById("logo-ong");
    const fallback = document.getElementById("logo-fallback");
    const urlLogo = urlSegura(osc.logo);
    if (urlLogo) {
        logo.src = urlLogo.href;
        logo.alt = `Logotipo de ${textoLimpo(osc.nome) || "organização"}`;
        logo.hidden = false;
        fallback.hidden = true;
        logo.onerror = () => {
            logo.hidden = true;
            fallback.hidden = false;
        };
    }

    const localizacao = [textoLimpo(osc.cidade), textoLimpo(osc.estado), textoLimpo(osc.pais)]
        .filter(Boolean).join(", ");
    document.getElementById("localizacao-texto").textContent = localizacao || "Localização não informada.";
    const abrangencia = textoLimpo(osc.areaGeografica);
    if (abrangencia) {
        const rotulosAbrangencia = {
            bairro: "Bairro",
            cidade: "Cidade",
            municipal: "Municipal",
            regional: "Regional",
            estadual: "Estadual",
            nacional: "Nacional",
            internacional: "Internacional"
        };
        const campo = document.getElementById("abrangencia-texto");
        campo.textContent = `Área de atuação informada: ${rotulosAbrangencia[abrangencia.toLocaleLowerCase("pt-BR")] || abrangencia}.`;
        campo.hidden = false;
    }
    // O endpoint não informa se logradouro e CEP têm autorização de publicação.
    document.getElementById("endereco-institucional").hidden = true;

    renderizarContato(osc);
    renderizarFuncionamento(osc);
    renderizarVoluntariado(osc);
    renderizarODS(osc);
    configurarCompartilhamento(osc);
    configurarControleStatus(osc);
    renderizarResumoConfiguracoes(osc);
    configurarNavegacaoPerfil();
    configurarVisualizadorConteudoPublico();
}

function renderizarResumoConfiguracoes(osc) {
    const valor = (id, texto) => { document.getElementById(id).textContent = texto || "Não informado"; };
    const causas = Object.entries(NOMES_CAUSAS);
    valor("resumo-nome-osc", textoLimpo(osc.nome));
    valor("resumo-status-osc", osc.arquivada ? "Arquivada" : osc.status === "ATIVA" ? "Ativa" : "Inativa");
    valor("resumo-causa-osc", causas.find(([chave]) => chave === osc.causaPrincipal)?.[1] || osc.causaPrincipal);
    valor("resumo-localizacao-osc", [textoLimpo(osc.cidade), textoLimpo(osc.estado), textoLimpo(osc.pais)].filter(Boolean).join(", "));
    valor("resumo-descricao-osc", textoLimpo(osc.descricaoCurta) || textoLimpo(osc.descricao));
}

function configurarControleStatus(osc) {
    const botao = document.getElementById("botao-status-osc");
    const dialogo = document.getElementById("dialogo-status-osc");
    const tituloDialogo = document.getElementById("titulo-dialogo-status-osc");
    const descricaoDialogo = document.getElementById("descricao-dialogo-status-osc");
    const estadoAtual = document.getElementById("estado-atual-status-osc");
    const motivo = document.getElementById("motivo-status-osc");
    const rotuloMotivo = document.getElementById("rotulo-motivo-status-osc");
    const feedback = document.getElementById("feedback-status-osc");
    const botaoCancelar = document.getElementById("cancelar-status-osc");
    const botaoConfirmar = document.getElementById("confirmar-status-osc");
    const podeAdministrar = Boolean(osc.podeAlterarStatus || osc.podeAdministrarStatusPlataforma);
    const justificativaObrigatoria = Boolean(osc.podeAdministrarStatusPlataforma);
    motivo.required = justificativaObrigatoria;
    rotuloMotivo.textContent = justificativaObrigatoria
        ? "Justificativa obrigatória"
        : "Justificativa (opcional)";
    botao.hidden = !podeAdministrar;
    botao.textContent = obterAcaoStatusOsc(osc).rotulo;
    atualizarZonaPerigo();
    if (botao.dataset.inicializado) return;

    botao.addEventListener("click", () => {
        const acao = obterAcaoStatusOsc(oscAtual);
        tituloDialogo.textContent = acao.titulo;
        descricaoDialogo.textContent = acao.descricao;
        estadoAtual.textContent = `Status atual: ${acao.estadoAtual}`;
        botaoConfirmar.textContent = acao.rotulo;
        motivo.value = "";
        feedback.textContent = "";
        dialogo.showModal();
        botaoCancelar.focus();
    });

    botaoCancelar.addEventListener("click", () => dialogo.close("cancelar"));
    botaoConfirmar.addEventListener("click", async () => {
        if (botaoConfirmar.disabled) return;
        if (motivo.required && !motivo.value.trim()) {
            motivo.setCustomValidity("Informe a justificativa para continuar.");
            motivo.reportValidity();
            feedback.textContent = "A justificativa é obrigatória para esta ação.";
            return;
        }
        motivo.setCustomValidity("");
        const acao = obterAcaoStatusOsc(oscAtual);
        const novoStatus = oscAtual.arquivada || oscAtual.status === "INATIVA"
            ? "ATIVA"
            : "INATIVA";
        const rota = oscAtual.podeAdministrarStatusPlataforma
            ? `/admin/oscs/${oscAtual.id}/status`
            : `/osc/${oscAtual.id}/status`;
        botaoConfirmar.disabled = true;
        botaoCancelar.disabled = true;
        dialogo.setAttribute("aria-busy", "true");
        feedback.textContent = "Atualizando status…";
        try {
            const resposta = await requisicaoAutenticada(rota, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    status: novoStatus,
                    motivo: motivo.value.trim() || null
                })
            });
            if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "alterar status"));
            oscAtual.status = novoStatus;
            oscAtual.arquivada = false;
            dialogo.close("salvo");
            renderizarPerfil(oscAtual);
            void carregarConteudoPublico(oscAtual.id);
            atualizarZonaPerigo();
            notificar(null, novoStatus === "ATIVA"
                ? "Organização ativada com sucesso."
                : "Organização desativada com sucesso.");
        } catch (erro) {
            feedback.textContent = erro.message || "Não foi possível alterar o status da organização.";
            notificar(null, feedback.textContent, "erro");
        } finally {
            botaoConfirmar.disabled = false;
            botaoCancelar.disabled = false;
            dialogo.removeAttribute("aria-busy");
        }
    });

    dialogo.addEventListener("click", (evento) => {
        if (evento.target === dialogo && !botaoConfirmar.disabled) dialogo.close("fora");
    });
    dialogo.addEventListener("keydown", (evento) => {
        if (evento.key !== "Escape" || botaoConfirmar.disabled) return;
        evento.preventDefault();
        dialogo.close("escape");
    });
    dialogo.addEventListener("cancel", (evento) => {
        if (botaoConfirmar.disabled) evento.preventDefault();
    });
    dialogo.addEventListener("close", () => {
        motivo.value = "";
        motivo.setCustomValidity("");
        feedback.textContent = "";
        if (!botao.hidden) botao.focus();
    });
    botao.dataset.inicializado = "true";
}

function obterAcaoStatusOsc(osc) {
    const restaurando = Boolean(osc.arquivada);
    const ativando = restaurando || osc.status === "INATIVA";
    const estadoAtual = restaurando ? "Arquivada" : osc.status === "ATIVA" ? "Ativa" : "Inativa";
    const rotulo = restaurando ? "Restaurar ONG" : ativando ? "Ativar ONG" : "Desativar ONG";
    const titulo = restaurando ? "Restaurar ONG?" : ativando ? "Ativar ONG?" : "Desativar ONG?";
    const descricao = restaurando || ativando
        ? "A organização voltará a ficar ativa. A visibilidade pública continuará respeitando as regras do perfil e de cada publicação."
        : "A organização deixará de aparecer nas áreas públicas. Seus membros continuarão com acesso de gestão conforme suas permissões.";
    return { rotulo, titulo, descricao, estadoAtual };
}

function atualizarZonaPerigo() {
    const zona = document.getElementById("zona-perigo");
    const botaoStatus = document.getElementById("botao-status-osc");
    const botaoExcluir = document.getElementById("excluir-osc");
    const grupoExcluir = document.getElementById("grupo-excluir-osc");
    if (!zona || !botaoStatus || !botaoExcluir || !grupoExcluir || !oscAtual) return;

    const podeAlterarStatus = Boolean(oscAtual.podeAlterarStatus || oscAtual.podeAdministrarStatusPlataforma);
    const podeExcluir = !oscAtual.arquivada && ["CRIADOR", "ADMINISTRADOR"].includes(cargoAtual);
    botaoStatus.hidden = !podeAlterarStatus;
    botaoExcluir.hidden = !podeExcluir;
    grupoExcluir.hidden = !podeExcluir;
    zona.hidden = !podeAlterarStatus && !podeExcluir;
}

function rotuloCargo(cargo) {
    return ({ CRIADOR: "Criador", ADMINISTRADOR: "Administrador", EDITOR: "Editor", MEMBRO: "Membro" })[cargo] || cargo;
}

function notificar(elemento, mensagem, tipoInformado = null) {
    const tipo = tipoInformado || (/mas n[aã]o foi poss[ií]vel atualizar/i.test(mensagem) ? "aviso"
        : /n[aã]o foi|inv[aá]lido|permiss[aã]o|erro|falha|n[aã]o encontramos|j[aá] faz parte/i.test(mensagem) ? "erro" : "sucesso");
    if (elemento) {
        clearTimeout(elemento._timerNotificacao);
        elemento.textContent = mensagem;
        elemento._timerNotificacao = setTimeout(() => { elemento.textContent = ""; }, 6000);
    }
    notificarSistema(mensagem, tipo);
}

async function mensagemErroApi(resposta, acao = "concluir a operação") {
    if (resposta?.status === 403 && acao !== "alterar status") return "Você não possui permissão para realizar esta ação.";
    if (resposta?.status === 404 && acao === "adicionar") return "Não encontramos uma conta cadastrada com esse e-mail. Verifique o endereço ou solicite que a pessoa crie uma conta.";
    if (resposta?.status === 409 && acao === "adicionar") return "Essa pessoa já faz parte desta organização.";
    if (resposta?.status === 409 && acao === "excluir organização") return "Esta organização possui histórico de doações e não pode ser excluída.";
    if (resposta?.status === 400 && acao === "adicionar") return "Informe um endereço de e-mail válido e selecione um cargo.";
    if (acao === "carregar dados") return "Não foi possível carregar os dados da organização. Tente novamente em instantes.";
    if (acao === "carregar membros") return "Não foi possível carregar a lista de membros. Tente novamente em instantes.";
    try {
        const body = await resposta.clone().json();
        const detalhe = body?.detail || body?.message || body?.mensagem;
        if (typeof detalhe === "string" && detalhe.trim()) return detalhe;
    } catch { /* Respostas sem corpo JSON usam a mensagem amigável padrão. */ }
    return "Não foi possível concluir a operação. Tente novamente.";
}

const causasOsc = [
    ["EDUCACAO", "Educação"], ["SAUDE", "Saúde"], ["MEIO_AMBIENTE", "Meio ambiente"],
    ["ANIMAIS", "Proteção animal"], ["INCLUSAO_SOCIAL", "Inclusão social"],
    ["ESPORTE", "Esporte"], ["CULTURA", "Cultura"], ["COMBATE_POBREZA", "Combate à pobreza"]
];
const diasOsc = [["segunda", "Segunda-feira"], ["terca", "Terça-feira"], ["quarta", "Quarta-feira"], ["quinta", "Quinta-feira"], ["sexta", "Sexta-feira"], ["sabado", "Sábado"], ["domingo", "Domingo"]];
const nomesOds = ["Erradicação da pobreza", "Fome zero e agricultura sustentável", "Saúde e bem-estar", "Educação de qualidade", "Igualdade de gênero", "Água potável e saneamento", "Energia limpa e acessível", "Trabalho decente e crescimento econômico", "Indústria, inovação e infraestrutura", "Redução das desigualdades", "Cidades e comunidades sustentáveis", "Consumo e produção responsáveis", "Ação contra a mudança global do clima", "Vida na água", "Vida terrestre", "Paz, justiça e instituições eficazes", "Parcerias e meios de implementação"];
let causasEdicao = new Set();
let odsEdicao = new Set();
let snapshotEdicao = null;
let snapshotDadosEdicao = null;

function prepararOpcoesCausas() {
    const causaPrincipal = oscAtual?.causaPrincipal;
    document.getElementById("edicao-causa-principal").textContent = causasOsc.find(([v]) => v === causaPrincipal)?.[1] || causaPrincipal || "Não informado";
    const opcoesCausas = document.getElementById("edicao-opcoes-causas");
    if (!opcoesCausas.childElementCount) causasOsc.forEach(([valor, rotulo]) => {
        const botao = document.createElement("button"); botao.type = "button"; botao.dataset.valor = valor; botao.textContent = rotulo;
        botao.addEventListener("click", () => { if (valor === causaPrincipal || causasEdicao.has(valor)) return; causasEdicao.add(valor); renderizarCausasEdicao(); sincronizarOpcoesSelecao(); atualizarEstadoEdicao(); });
        opcoesCausas.append(botao);
    });
    const opcoesOds = document.getElementById("edicao-opcoes-ods");
    if (!opcoesOds.childElementCount) nomesOds.forEach((nome, indice) => {
        const numero = String(indice + 1); const botao = document.createElement("button"); botao.type = "button"; botao.dataset.valor = numero;
        botao.innerHTML = `<span class="ods-numero">ODS ${numero}</span> ${nome}`;
        botao.addEventListener("click", () => { if (odsEdicao.has(numero)) return; odsEdicao.add(numero); renderizarOdsEdicao(); sincronizarOpcoesSelecao(); atualizarEstadoEdicao(); }); opcoesOds.append(botao);
    });
    const dias = document.getElementById("edicao-dias-semana");
    if (!dias.childElementCount) diasOsc.forEach(([valor, rotulo]) => { const label = document.createElement("label"); const input = document.createElement("input"); input.type = "checkbox"; input.value = valor; input.dataset.dia = "true"; input.addEventListener("change", atualizarEstadoEdicao); label.append(input, document.createTextNode(` ${rotulo}`)); dias.append(label); });
    const botaoCausas = document.getElementById("edicao-adicionar-causa");
    const botaoOds = document.getElementById("edicao-adicionar-ods");
    if (!botaoCausas.dataset.inicializado) { botaoCausas.addEventListener("click", () => alternarDropdown("edicao-dropdown-causas")); botaoCausas.dataset.inicializado = "true"; }
    if (!botaoOds.dataset.inicializado) { botaoOds.addEventListener("click", () => alternarDropdown("edicao-dropdown-ods")); botaoOds.dataset.inicializado = "true"; }
}

function alternarDropdown(id) { const atual = document.getElementById(id); const lista = atual.querySelector(".dropdown-opcoes"); const visivel = lista.classList.toggle("aberto"); sincronizarOpcoesSelecao(); if (visivel) lista.querySelector("button:not([hidden])")?.focus(); }
function sincronizarOpcoesSelecao() {
    document.querySelectorAll("#edicao-opcoes-causas button").forEach(botao => { botao.hidden = causasEdicao.has(botao.dataset.valor) || botao.dataset.valor === oscAtual?.causaPrincipal; });
    document.querySelectorAll("#edicao-opcoes-ods button").forEach(botao => { botao.hidden = odsEdicao.has(botao.dataset.valor); });
}
function renderizarCausasEdicao() { const area = document.getElementById("edicao-causas-selecionadas"); area.replaceChildren(); for (const valor of causasEdicao) { const item = document.createElement("span"); item.className = "area-selecionada"; item.textContent = causasOsc.find(([v]) => v === valor)?.[1] || valor; const btn = document.createElement("button"); btn.type = "button"; btn.setAttribute("aria-label", `Remover ${item.textContent}`); btn.textContent = "×"; btn.addEventListener("click", () => { causasEdicao.delete(valor); renderizarCausasEdicao(); sincronizarOpcoesSelecao(); atualizarEstadoEdicao(); }); item.append(btn); area.append(item); } }
function renderizarOdsEdicao() { const area = document.getElementById("edicao-ods-selecionadas"); area.replaceChildren(); for (const valor of odsEdicao) { const item = document.createElement("span"); item.className = "ods-selecionada"; const numero = document.createElement("strong"); numero.className = "ods-numero"; numero.textContent = `ODS ${valor}`; const titulo = document.createElement("span"); titulo.textContent = nomesOds[Number(valor)-1]; item.append(numero, titulo); const btn = document.createElement("button"); btn.type = "button"; btn.setAttribute("aria-label", `Remover ODS ${valor}`); btn.textContent = "×"; btn.addEventListener("click", () => { odsEdicao.delete(valor); renderizarOdsEdicao(); sincronizarOpcoesSelecao(); atualizarEstadoEdicao(); }); item.append(btn); area.append(item); } }
function capturarEdicao() { const campos = [...document.querySelectorAll("#form-edicao-osc input:not([data-dia]), #form-edicao-osc select, #form-edicao-osc textarea")].map(el => [el.id, el.value]); const dias = [...document.querySelectorAll("#edicao-dias-semana input:checked")].map(el => el.value).sort(); return JSON.stringify({ campos, causas: [...causasEdicao].sort(), ods: [...odsEdicao].sort((a,b)=>Number(a)-Number(b)), dias }); }
function atualizarEstadoEdicao() { if (!snapshotEdicao) return; document.getElementById("edicao-descartar").disabled = capturarEdicao() === snapshotEdicao; }
function definirSnapshotEdicao() { snapshotEdicao = capturarEdicao(); atualizarEstadoEdicao(); }

function definirSelectBooleano(id, valor) {
    document.getElementById(id).value = typeof valor === "boolean" ? String(valor) : "";
}

function valorBooleano(id) {
    const valor = document.getElementById(id).value;
    return valor === "" ? null : valor === "true";
}

function preencherCamposEdicao(dados) {
    prepararOpcoesCausas();
    const campos = {
        "edicao-descricao-curta": dados.descricaoCurta, "edicao-descricao": dados.descricao,
        "edicao-pais": dados.pais, "edicao-estado": dados.estado,
        "edicao-cidade": dados.cidade, "edicao-cep": dados.cep, "edicao-bairro": dados.bairro,
        "edicao-rua": dados.rua, "edicao-numero": dados.numero, "edicao-complemento": dados.complemento,
        "edicao-area-geografica": dados.areaGeografica, "edicao-email": dados.email,
        "edicao-telefone": dados.telefone, "edicao-whatsapp": dados.whatsapp, "edicao-site": dados.site,
        "edicao-instagram": dados.instagram, "edicao-linkedin": dados.linkedin, "edicao-youtube": dados.youtube,
        "edicao-horario-abertura": dados.horarioAbertura,
        "edicao-horario-fechamento": dados.horarioFechamento, "edicao-frequencia": dados.frequenciaVoluntariado,
        "edicao-idade-minima": dados.idadeMinimaVoluntario, "edicao-atividades": dados.atividadesVoluntariado
    };
    for (const [id, valor] of Object.entries(campos)) document.getElementById(id).value = valor ?? "";
    document.getElementById("edicao-cnpj").textContent = dados.cnpj || "Não informado";
    document.getElementById("edicao-nome").textContent = dados.nome || "Não informado";
    document.getElementById("edicao-ano").textContent = dados.anoFundacao ?? "Não informado";
    document.getElementById("edicao-causa-principal").textContent = causasOsc.find(([v]) => v === dados.causaPrincipal)?.[1] || dados.causaPrincipal || "Não informado";
    causasEdicao = new Set(dados.causasSecundarias || []); renderizarCausasEdicao();
    odsEdicao = new Set((dados.ods || []).map(String)); renderizarOdsEdicao();
    sincronizarOpcoesSelecao();
    const parseDias = (dados.diasHorariosFuncionamento || "").toLocaleLowerCase("pt-BR");
    const diasCadastrados = new Set(diasOsc.filter(([valor, nome]) => parseDias.includes(valor) || parseDias.includes(nome.toLocaleLowerCase("pt-BR"))).map(([valor]) => valor));
    document.querySelectorAll("#edicao-dias-semana input").forEach(input => { input.checked = diasCadastrados.has(input.value); });
    definirSelectBooleano("edicao-presencial", dados.atendimentoPresencial);
    definirSelectBooleano("edicao-online", dados.atendimentoOnline);
    definirSelectBooleano("edicao-agendamento", dados.necessitaAgendamento);
    definirSelectBooleano("edicao-aceita-voluntarios", dados.aceitaVoluntarios);
    definirSelectBooleano("edicao-processo-seletivo", dados.processoSeletivoVoluntario);
    definirSelectBooleano("edicao-certificado", dados.emiteCertificadoVoluntario);
    atualizarCamposVoluntariado();
}
function atualizarCamposVoluntariado() { const sim = document.getElementById("edicao-aceita-voluntarios").value === "true"; const grupo = document.getElementById("edicao-campos-voluntariado"); grupo.hidden = !sim; grupo.querySelectorAll("input,select,textarea").forEach(el => { el.disabled = !sim; }); }

async function requisicaoAutenticada(caminho, opcoes = {}) {
    const resposta = await fetchAutenticado(caminho, opcoes);
    return await sessaoInvalidaConfirmada(resposta) ? null : resposta;
}

function dataConteudo(valor) {
    if (!valor) return "Data não informada";
    const data = new Date(valor);
    return Number.isNaN(data.valueOf()) ? "Data não informada" : data.toLocaleDateString("pt-BR");
}

function criarCartaoConteudo(item, oportunidade, gerenciavel) {
    const cartao = document.createElement("article");
    cartao.className = "cartao-conteudo-osc";
    const cabecalho = document.createElement("div");
    cabecalho.className = "cartao-conteudo-osc-cabecalho";
    const titulo = document.createElement("h3");
    titulo.textContent = item.titulo;
    const tipo = document.createElement("span");
    tipo.className = "estado-conteudo-osc";
    tipo.textContent = oportunidade
        ? `${item.vaga?.status || "Dados de vaga incompletos"}${item.ativo ? "" : " · Post não publicado"}`
        : (item.ativo ? "Publicado" : "Não publicado");
    cabecalho.append(titulo, tipo);
    cartao.append(cabecalho);

    if (item.imagemUrl && urlImagemSegura(item.imagemUrl)) {
        const imagem = document.createElement("img");
        imagem.src = item.imagemUrl;
        imagem.alt = `Imagem de ${item.titulo}`;
        imagem.loading = "lazy";
        imagem.referrerPolicy = "no-referrer";
        cartao.append(imagem);
    }

    const texto = document.createElement("p");
    texto.className = "cartao-conteudo-osc-texto";
    texto.textContent = item.conteudo;
    cartao.append(texto);

    if (oportunidade && item.vaga) {
        const detalhes = document.createElement("dl");
        detalhes.className = "detalhes-vaga-osc";
        const dados = [
            ["Modalidade", item.vaga.modalidadeVoluntariado],
            ["Vagas", Number.isInteger(item.vaga.quantidadeVagas) ? String(item.vaga.quantidadeVagas) : "Não informada"],
            ["Carga horária", item.vaga.cargaHoraria],
            ["Requisitos", item.vaga.requisitos],
            ["Local", [item.vaga.bairro, item.vaga.cidade].filter(Boolean).join(" · ")],
            ["Período", `${dataConteudo(item.vaga.dataInicio)} a ${dataConteudo(item.vaga.dataFim)}`]
        ];
        for (const [rotulo, valor] of dados) {
            const termo = document.createElement("dt");
            const descricao = document.createElement("dd");
            termo.textContent = rotulo;
            descricao.textContent = valor || "Não informado";
            detalhes.append(termo, descricao);
        }
        cartao.append(detalhes);
    }

    const rodape = document.createElement("div");
    rodape.className = "cartao-conteudo-osc-rodape";
    const atualizado = document.createElement("small");
    atualizado.textContent = `Atualizado em ${dataConteudo(item.dataAtualizacao || item.dataCriacao)}`;
    rodape.append(atualizado);
    if (gerenciavel && item.podeEditar) {
        const editar = document.createElement("button");
        editar.type = "button";
        editar.className = "btn btn-secundario";
        editar.textContent = oportunidade ? "Editar oportunidade" : "Editar post";
        editar.addEventListener("click", () => oportunidade
            ? abrirDialogoOportunidade(item)
            : abrirDialogoPost(item));
        rodape.append(editar);
    }
    cartao.append(rodape);
    return cartao;
}

function criarPreviewSemImagem(item, oportunidade) {
    const preview = document.createElement("div");
    preview.className = "miniatura-texto-osc";
    const categoria = document.createElement("span");
    categoria.className = "miniatura-categoria-osc";
    categoria.textContent = oportunidade ? "VOLUNTARIADO" : "PUBLICAÇÃO";
    const titulo = document.createElement("strong");
    titulo.textContent = item.titulo;
    const trecho = document.createElement("span");
    trecho.className = "miniatura-trecho-osc";
    trecho.textContent = item.conteudo.trim().slice(0, 115);
    preview.append(categoria, titulo, trecho);
    return preview;
}

function criarMiniaturaPublica(item, oportunidade) {
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "miniatura-publica-osc";
    botao.setAttribute("aria-haspopup", "dialog");
    botao.setAttribute("aria-label", `Abrir ${oportunidade ? "oportunidade" : "publicação"}: ${item.titulo}`);
    if (item.imagemUrl && urlImagemSegura(item.imagemUrl)) {
        const imagem = document.createElement("img");
        imagem.src = item.imagemUrl;
        imagem.alt = "";
        imagem.loading = "lazy";
        imagem.referrerPolicy = "no-referrer";
        imagem.addEventListener("error", () => {
            botao.replaceChildren(criarPreviewSemImagem(item, oportunidade));
        }, { once: true });
        botao.append(imagem);
        const legenda = document.createElement("span");
        legenda.className = "miniatura-legenda-osc";
        legenda.textContent = item.titulo;
        botao.append(legenda);
    } else {
        botao.append(criarPreviewSemImagem(item, oportunidade));
    }
    botao.addEventListener("click", () => abrirVisualizadorConteudo(item, oportunidade, botao));
    return botao;
}

function renderizarListaPublica(lista, items, oportunidade) {
    lista.replaceChildren(...items.map(item => criarMiniaturaPublica(item, oportunidade)));
}

function adicionarDetalheVisualizador(lista, rotulo, valor) {
    if (valor == null || String(valor).trim() === "") return;
    const termo = document.createElement("dt");
    termo.textContent = rotulo;
    const descricao = document.createElement("dd");
    descricao.textContent = String(valor);
    lista.append(termo, descricao);
}

function preencherVisualizadorOportunidade(item) {
    const detalhes = document.getElementById("detalhes-visualizador-vaga");
    detalhes.replaceChildren();
    const vaga = item.vaga;
    if (!vaga) {
        detalhes.hidden = true;
        return;
    }
    const modalidades = { PRESENCIAL: "Presencial", REMOTO: "Remoto", HIBRIDO: "Híbrido" };
    const status = { ABERTA: "Aberta", ENCERRADA: "Encerrada", CANCELADA: "Cancelada" };
    const local = [vaga.rua, vaga.numero, vaga.bairro, vaga.cidade].filter(Boolean).join(", ");
    adicionarDetalheVisualizador(detalhes, "Modalidade", modalidades[vaga.modalidadeVoluntariado] || vaga.modalidadeVoluntariado);
    adicionarDetalheVisualizador(detalhes, "Local", local);
    adicionarDetalheVisualizador(detalhes, "Requisitos", vaga.requisitos);
    adicionarDetalheVisualizador(detalhes, "Quantidade de vagas", vaga.quantidadeVagas);
    adicionarDetalheVisualizador(detalhes, "Carga horária", vaga.cargaHoraria);
    adicionarDetalheVisualizador(detalhes, "Início", dataConteudo(vaga.dataInicio));
    adicionarDetalheVisualizador(detalhes, "Encerramento", dataConteudo(vaga.dataFim));
    adicionarDetalheVisualizador(detalhes, "Status", status[vaga.status] || vaga.status);
    detalhes.hidden = detalhes.childElementCount === 0;
}

function abrirVisualizadorConteudo(item, oportunidade, origem) {
    const dialogo = document.getElementById("dialogo-conteudo-publico");
    if (dialogo.open) return;
    origemVisualizadorConteudo = origem;
    overflowAnteriorVisualizador = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const media = document.getElementById("visualizador-midia-conteudo");
    media.replaceChildren();
    if (item.imagemUrl && urlImagemSegura(item.imagemUrl)) {
        const imagem = document.createElement("img");
        imagem.src = item.imagemUrl;
        imagem.alt = `Imagem de ${item.titulo}`;
        imagem.referrerPolicy = "no-referrer";
        imagem.addEventListener("error", () => {
            media.replaceChildren(criarPreviewSemImagem(item, oportunidade));
        }, { once: true });
        media.append(imagem);
    } else {
        media.append(criarPreviewSemImagem(item, oportunidade));
    }

    document.getElementById("visualizador-ong-nome").textContent = textoLimpo(oscAtual?.nome) || "Organização";
    document.getElementById("visualizador-tipo-conteudo").textContent = oportunidade
        ? "Oportunidade de voluntariado"
        : item.tipo === "NOTICIA" ? "Notícia" : "Publicação";
    document.getElementById("titulo-visualizador-conteudo").textContent = item.titulo;
    document.getElementById("texto-visualizador-conteudo").textContent = item.conteudo;
    preencherVisualizadorOportunidade(oportunidade ? item : { vaga: null });
    const data = item.dataAtualizacao || item.dataCriacao;
    const dataElemento = document.getElementById("data-visualizador-conteudo");
    dataElemento.textContent = `${item.dataAtualizacao ? "Atualizado" : "Publicado"} em ${dataConteudo(data)}`;
    dataElemento.dateTime = data || "";

    dialogo.showModal();
    document.getElementById("fechar-visualizador-conteudo").focus();
}

function configurarVisualizadorConteudoPublico() {
    const dialogo = document.getElementById("dialogo-conteudo-publico");
    if (dialogo.dataset.inicializado === "true") return;
    document.getElementById("fechar-visualizador-conteudo").addEventListener("click", () => dialogo.close("botao"));
    dialogo.addEventListener("keydown", evento => {
        if (evento.key !== "Escape") return;
        evento.preventDefault();
        dialogo.close("escape");
    });
    dialogo.addEventListener("click", evento => {
        if (evento.target === dialogo) dialogo.close("fora");
    });
    dialogo.addEventListener("close", () => {
        document.body.style.overflow = overflowAnteriorVisualizador ?? "";
        if (origemVisualizadorConteudo?.isConnected) origemVisualizadorConteudo.focus();
        origemVisualizadorConteudo = null;
        overflowAnteriorVisualizador = null;
    });
    dialogo.dataset.inicializado = "true";
}

function abaPerfilPorHash(hash) {
    const normalizado = hash === "conteudo" ? "gestao-conteudo"
        : hash === "dados" ? "configuracoes" : hash;
    return [...document.querySelectorAll("#abas-perfil-osc [role=tab]")]
        .find(aba => aba.dataset.hash === normalizado) || null;
}

function ativarAbaPerfil(abaSelecionada, atualizarHash = true) {
    if (!abaSelecionada || abaSelecionada.hidden) return false;
    const abaPrivada = ["aba-conteudo", "aba-membros", "aba-dados"].includes(abaSelecionada.id);
    if (abaPrivada && !membroOscConfirmado) return false;

    const navegacao = document.getElementById("abas-perfil-osc");
    const abas = [...navegacao.querySelectorAll('[role="tab"]')];
    const wrapperGestao = document.getElementById("gestao-osc");
    const paineis = abas.map(aba => document.getElementById(aba.getAttribute("aria-controls")));
    for (let i = 0; i < abas.length; i += 1) {
        const ativa = abas[i] === abaSelecionada;
        abas[i].setAttribute("aria-selected", String(ativa));
        abas[i].tabIndex = ativa ? 0 : -1;
        paineis[i].hidden = !ativa;
    }
    wrapperGestao.hidden = !abaPrivada;
    if (atualizarHash) {
        history.replaceState(null, "", `${location.pathname}${location.search}#${abaSelecionada.dataset.hash}`);
    }
    return true;
}

function resolverHashInicialPerfil() {
    if (!hashInicialPerfil) return;
    const abaSolicitada = abaPerfilPorHash(hashInicialPerfil);
    if (!abaSolicitada) return;
    const privada = ["aba-conteudo", "aba-membros", "aba-dados"].includes(abaSolicitada.id);
    if (privada && !verificacaoMembroOscConcluida) return;
    if (!privada && abaSolicitada.id !== "aba-informacoes" && !verificacaoConteudoPublicoConcluida) return;
    if (abaSolicitada.hidden || (privada && !membroOscConfirmado)) {
        ativarAbaPerfil(document.getElementById("aba-informacoes"));
        history.replaceState(null, "", `${location.pathname}${location.search}#informacoes`);
        hashInicialPerfil = "informacoes";
        return;
    }
    ativarAbaPerfil(abaSolicitada, false);
}

function configurarNavegacaoPerfil() {
    const navegacao = document.getElementById("abas-perfil-osc");
    if (navegacao.dataset.inicializada === "true") return;
    const abas = [...navegacao.querySelectorAll('[role="tab"]')];
    for (const aba of abas) {
        aba.addEventListener("click", () => {
            hashInicialPerfil = aba.dataset.hash;
            ativarAbaPerfil(aba);
        });
        aba.addEventListener("keydown", evento => {
            if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(evento.key)) return;
            const visiveis = abas.filter(item => !item.hidden);
            const posicao = visiveis.indexOf(aba);
            if (posicao < 0) return;
            evento.preventDefault();
            const proximaPosicao = evento.key === "Home" ? 0
                : evento.key === "End" ? visiveis.length - 1
                    : (posicao + (evento.key === "ArrowRight" ? 1 : visiveis.length - 1)) % visiveis.length;
            const proxima = visiveis[proximaPosicao];
            proxima.focus();
            hashInicialPerfil = proxima.dataset.hash;
            ativarAbaPerfil(proxima);
        });
    }
    navegacao.hidden = false;
    navegacao.dataset.inicializada = "true";
    ativarAbaPerfil(document.getElementById("aba-informacoes"), false);
    resolverHashInicialPerfil();
}

function definirAcessoAbasPrivadas(permitido) {
    membroOscConfirmado = permitido;
    verificacaoMembroOscConcluida = true;
    const abasPrivadas = ["aba-conteudo", "aba-membros", "aba-dados"]
        .map(id => document.getElementById(id));
    abasPrivadas.forEach(aba => { aba.hidden = !permitido; });
    if (!permitido && abasPrivadas.some(aba => aba.getAttribute("aria-selected") === "true")) {
        ativarAbaPerfil(document.getElementById("aba-informacoes"));
    }
    resolverHashInicialPerfil();
}

function atualizarAbasPublicas(posts, oportunidades) {
    document.getElementById("aba-publicacoes").hidden = posts.length === 0;
    document.getElementById("aba-voluntariado-publico").hidden = oportunidades.length === 0;
    resolverHashInicialPerfil();
    const ativa = document.querySelector('#abas-perfil-osc [aria-selected="true"]');
    if (ativa?.hidden) {
        ativarAbaPerfil(document.getElementById("aba-informacoes"));
    }
}

function resetarConteudoPublico() {
    requisicaoConteudoPublicoAtual += 1;
    verificacaoConteudoPublicoConcluida = false;
    membroOscConfirmado = false;
    verificacaoMembroOscConcluida = false;
    for (const id of ["aba-conteudo", "aba-membros", "aba-dados"]) {
        const aba = document.getElementById(id);
        aba.hidden = true;
        aba.setAttribute("aria-selected", "false");
        aba.tabIndex = -1;
    }
    document.getElementById("gestao-osc").hidden = true;
    const info = document.getElementById("aba-informacoes");
    const publicTabs = [document.getElementById("aba-publicacoes"), document.getElementById("aba-voluntariado-publico")];
    for (const aba of publicTabs) {
        aba.hidden = true;
        aba.setAttribute("aria-selected", "false");
        aba.tabIndex = -1;
    }
    if (info) {
        info.setAttribute("aria-selected", "true");
        info.tabIndex = 0;
    }
    document.getElementById("painel-publico").hidden = false;
    document.getElementById("painel-publicacoes").hidden = true;
    document.getElementById("painel-voluntariado-publico").hidden = true;
    for (const id of ["painel-conteudo", "painel-membros", "painel-dados"]) {
        document.getElementById(id).hidden = true;
    }
    document.getElementById("posts-publicos-lista").replaceChildren();
    document.getElementById("oportunidades-publicas-lista").replaceChildren();
    const status = document.getElementById("conteudo-publico-status");
    status.hidden = true;
    status.textContent = "";
}

function renderizarListaConteudo(lista, vazio, items, oportunidade, gerenciavel) {
    lista.replaceChildren(...items.map(item => criarCartaoConteudo(item, oportunidade, gerenciavel)));
    vazio.hidden = items.length > 0;
}

async function carregarConteudoPublico(id) {
    const requisicaoId = ++requisicaoConteudoPublicoAtual;
    const status = document.getElementById("conteudo-publico-status");
    const abaPosts = document.getElementById("aba-publicacoes");
    const abaOportunidades = document.getElementById("aba-voluntariado-publico");
    const painelPosts = document.getElementById("painel-publicacoes");
    const painelOportunidades = document.getElementById("painel-voluntariado-publico");
    abaPosts.hidden = true;
    abaOportunidades.hidden = true;
    painelPosts.hidden = true;
    painelOportunidades.hidden = true;
    const abaSelecionada = document.querySelector('#abas-perfil-osc [aria-selected="true"]');
    if (!abaSelecionada || abaSelecionada.hidden) {
        ativarAbaPerfil(document.getElementById("aba-informacoes"));
    }
    document.getElementById("posts-publicos-lista").replaceChildren();
    document.getElementById("oportunidades-publicas-lista").replaceChildren();
    status.hidden = false;
    status.textContent = "Carregando publicações e oportunidades públicas…";
    try {
        const resposta = await fetch(`${API_URL}/post/osc/${encodeURIComponent(id)}/publicos`, {
            headers: { Accept: "application/json" },
            cache: "no-store"
        });
        if (requisicaoId !== requisicaoConteudoPublicoAtual || Number(id) !== Number(oscAtual?.id)) return;
        if (resposta.status === 404 && oscAtual?.status !== "ATIVA") {
            verificacaoConteudoPublicoConcluida = true;
            status.textContent = "O conteúdo público fica oculto enquanto a organização está inativa.";
            resolverHashInicialPerfil();
            return;
        }
        if (!resposta.ok) throw new Error("Conteúdo público indisponível.");
        const items = await resposta.json();
        if (requisicaoId !== requisicaoConteudoPublicoAtual || Number(id) !== Number(oscAtual?.id)) return;
        if (!Array.isArray(items) || items.some(item => !respostaConteudoOscValida(item, id))) {
            throw new Error("Resposta inválida.");
        }
        const separado = separarConteudoPublicoOsc(
            items, id, oscAtual?.status, oscAtual?.arquivada);
        verificacaoConteudoPublicoConcluida = true;
        renderizarListaPublica(document.getElementById("posts-publicos-lista"), separado.posts, false);
        renderizarListaPublica(document.getElementById("oportunidades-publicas-lista"), separado.oportunidades, true);
        atualizarAbasPublicas(separado.posts, separado.oportunidades);
        status.hidden = true;
    } catch {
        if (requisicaoId !== requisicaoConteudoPublicoAtual || Number(id) !== Number(oscAtual?.id)) return;
        verificacaoConteudoPublicoConcluida = true;
        resolverHashInicialPerfil();
        status.textContent = "Não foi possível verificar as publicações e oportunidades públicas.";
        status.hidden = false;
    }
}

async function carregarConteudoGestao() {
    const postsStatus = document.getElementById("posts-gestao-status");
    const oportunidadesStatus = document.getElementById("oportunidades-gestao-status");
    const postsErro = document.getElementById("posts-gestao-erro");
    const oportunidadesErro = document.getElementById("oportunidades-gestao-erro");
    postsStatus.hidden = false;
    oportunidadesStatus.hidden = false;
    postsErro.hidden = true;
    oportunidadesErro.hidden = true;
    try {
        const resposta = await requisicaoAutenticada(`/post/osc/${encodeURIComponent(oscAtual.id)}/gestao`);
        if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "carregar conteudo"));
        const items = await resposta.json();
        if (!Array.isArray(items) || items.some(item => !respostaConteudoOscValida(item, oscAtual.id))) {
            throw new Error("Resposta de conteudo invalida.");
        }
        conteudoOscAtual = items;
        const separado = separarConteudoOsc(items);
        renderizarListaConteudo(document.getElementById("posts-gestao-lista"),
            document.getElementById("posts-gestao-vazio"), separado.posts, false, true);
        renderizarListaConteudo(document.getElementById("oportunidades-gestao-lista"),
            document.getElementById("oportunidades-gestao-vazio"), separado.oportunidades, true, true);
    } catch (erro) {
        postsErro.textContent = erro.message || "Não foi possível carregar os posts.";
        oportunidadesErro.textContent = erro.message || "Não foi possível carregar as oportunidades.";
        postsErro.hidden = false;
        oportunidadesErro.hidden = false;
    } finally {
        postsStatus.hidden = true;
        oportunidadesStatus.hidden = true;
    }
}

function abrirDialogoPost(item = null) {
    edicaoPostOsc = item;
    const dialogo = document.getElementById("dialogo-post-osc");
    const form = document.getElementById("form-post-osc");
    form.reset();
    document.getElementById("titulo-dialogo-post-osc").textContent = item ? "Editar post" : "Criar post";
    document.getElementById("post-titulo-osc").value = item?.titulo || "";
    document.getElementById("post-conteudo-osc").value = item?.conteudo || "";
    resetarImagemConteudo("post", item?.imagemUrl || null);
    document.getElementById("post-ativo-osc").checked = item ? item.ativo : true;
    document.getElementById("post-form-feedback-osc").textContent = "";
    dialogo.showModal();
    document.getElementById("post-titulo-osc").focus();
}

function preencherVagaForm(vaga = null) {
    const campos = {
        "vaga-quantidade-osc": vaga?.quantidadeVagas ?? 0,
        "vaga-modalidade-osc": vaga?.modalidadeVoluntariado || "",
        "vaga-requisitos-osc": vaga?.requisitos || "",
        "vaga-carga-osc": vaga?.cargaHoraria || "",
        "vaga-cidade-osc": vaga?.cidade || "",
        "vaga-bairro-osc": vaga?.bairro || "",
        "vaga-rua-osc": vaga?.rua || "",
        "vaga-numero-osc": vaga?.numero || "",
        "vaga-complemento-osc": vaga?.complemento || "",
        "vaga-inicio-osc": vaga?.dataInicio || "",
        "vaga-fim-osc": vaga?.dataFim || "",
        "vaga-status-osc": vaga?.status || "ABERTA"
    };
    for (const [id, value] of Object.entries(campos)) document.getElementById(id).value = value;
}

function abrirDialogoOportunidade(item = null) {
    edicaoOportunidadeOsc = item;
    const dialogo = document.getElementById("dialogo-oportunidade-osc");
    const form = document.getElementById("form-oportunidade-osc");
    form.reset();
    document.getElementById("titulo-dialogo-oportunidade-osc").textContent = item
        ? "Editar oportunidade" : "Criar oportunidade";
    document.getElementById("oportunidade-titulo-osc").value = item?.titulo || "";
    document.getElementById("oportunidade-conteudo-osc").value = item?.conteudo || "";
    resetarImagemConteudo("oportunidade", item?.imagemUrl || null);
    document.getElementById("oportunidade-ativo-osc").checked = item ? item.ativo : true;
    preencherVagaForm(item?.vaga || null);
    document.getElementById("oportunidade-form-feedback-osc").textContent = "";
    dialogo.showModal();
    document.getElementById("oportunidade-titulo-osc").focus();
}

function criarPayloadPost(tipo, ativo, titulo, conteudo) {
    return {
        post: { tipo, ativo, titulo: titulo.trim(), conteudo: conteudo.trim(), imagemUrl: null },
        vaga: null
    };
}

function criarPayloadOportunidade(item = null) {
    return {
        post: {
            tipo: "VOLUNTARIADO",
            ativo: document.getElementById("oportunidade-ativo-osc").checked,
            titulo: document.getElementById("oportunidade-titulo-osc").value.trim(),
            conteudo: document.getElementById("oportunidade-conteudo-osc").value.trim(),
            imagemUrl: null
        },
        vaga: {
            quantidadeVagas: Number(document.getElementById("vaga-quantidade-osc").value),
            modalidadeVoluntariado: document.getElementById("vaga-modalidade-osc").value,
            requisitos: document.getElementById("vaga-requisitos-osc").value.trim(),
            cargaHoraria: document.getElementById("vaga-carga-osc").value.trim(),
            cidade: document.getElementById("vaga-cidade-osc").value.trim(),
            bairro: document.getElementById("vaga-bairro-osc").value.trim(),
            rua: document.getElementById("vaga-rua-osc").value.trim() || null,
            numero: document.getElementById("vaga-numero-osc").value.trim() || null,
            complemento: document.getElementById("vaga-complemento-osc").value.trim() || null,
            dataInicio: document.getElementById("vaga-inicio-osc").value,
            dataFim: document.getElementById("vaga-fim-osc").value,
            status: item?.vaga?.status || "ABERTA"
        }
    };
}

function resetarImagemConteudo(tipo, urlAtual = null) {
    const estado = imagensConteudoOsc[tipo];
    if (estado.urlPreview) URL.revokeObjectURL(estado.urlPreview);
    estado.arquivo = null;
    estado.urlPreview = null;
    estado.urlAtual = urlImagemSegura(urlAtual) ? urlAtual : null;
    const prefixo = tipo === "post" ? "post" : "oportunidade";
    const input = document.getElementById(`${prefixo}-imagem-arquivo-osc`);
    input.value = "";
    document.getElementById(`${prefixo}-imagem-feedback-osc`).textContent = "";
    renderizarPreviewImagemConteudo(tipo);
}

function renderizarPreviewImagemConteudo(tipo) {
    const estado = imagensConteudoOsc[tipo];
    const prefixo = tipo === "post" ? "post" : "oportunidade";
    const figura = document.getElementById(`${prefixo}-imagem-preview-osc`);
    const imagem = figura.querySelector("img");
    const nome = figura.querySelector("figcaption span");
    const remover = figura.querySelector("figcaption button");
    const fonte = estado.urlPreview || estado.urlAtual;
    figura.hidden = !fonte;
    if (!fonte) {
        imagem.removeAttribute("src");
        nome.textContent = "";
        remover.hidden = true;
        return;
    }
    imagem.src = fonte;
    nome.textContent = estado.arquivo
        ? estado.arquivo.name
        : "Imagem atual — será mantida se nenhuma imagem nova for selecionada.";
    remover.hidden = !estado.arquivo;
}

function liberarImagemConteudo(tipo) {
    resetarImagemConteudo(tipo, null);
}

function mensagemErroConteudo(resposta) {
    if (!resposta) return "Não foi possível confirmar a sessão. Entre novamente e tente de novo.";
    if (resposta.status === 400) return "Revise os campos e as datas informadas.";
    if (resposta.status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
    if (resposta.status === 403) return "Seu vínculo ou cargo não permite esta operação.";
    if (resposta.status === 404) return "A organização ou o conteúdo não está disponível.";
    if (resposta.status === 409) return "A operação conflita com o estado atual. Atualize a lista e revise as candidaturas antes de tentar novamente.";
    if (resposta.status === 413) return "O arquivo enviado excede o limite permitido de 10 MB.";
    return "Não foi possível salvar o conteúdo. Tente novamente.";
}

async function salvarConteudo(form, operation) {
    if (form.dataset.salvando === "true" || !form.reportValidity()) return;
    const isOpportunity = operation === "opportunity";
    const button = form.querySelector("button[type=submit]");
    const feedback = document.getElementById(isOpportunity ? "oportunidade-form-feedback-osc" : "post-form-feedback-osc");
    const item = isOpportunity ? edicaoOportunidadeOsc : edicaoPostOsc;
    const payload = isOpportunity
        ? criarPayloadOportunidade(item)
        : criarPayloadPost("DOACAO", document.getElementById("post-ativo-osc").checked,
            document.getElementById("post-titulo-osc").value,
            document.getElementById("post-conteudo-osc").value);
    const validacao = validarConteudoOsc(payload);
    if (!validacao.valido) {
        feedback.textContent = "Revise o tipo, os campos obrigatórios e as datas informadas.";
        return;
    }
    const tipoImagem = isOpportunity ? "oportunidade" : "post";
    const arquivoImagem = imagensConteudoOsc[tipoImagem].arquivo;
    if (arquivoImagem) {
        const validacaoImagem = validarArquivoImagem(arquivoImagem);
        if (!validacaoImagem.valido) {
            document.getElementById(`${tipoImagem === "post" ? "post" : "oportunidade"}-imagem-feedback-osc`).textContent = validacaoImagem.mensagem;
            return;
        }
    }
    form.dataset.salvando = "true";
    button.disabled = true;
    feedback.textContent = "Salvando…";
    const id = encodeURIComponent(oscAtual.id);
    const caminho = item
        ? `/post/osc/${id}/put-com-imagem`
        : `/post/osc/${id}/create-com-imagem`;
    const method = item ? "PUT" : "POST";
    if (item) payload.id = item.id;
    const dados = new FormData();
    dados.append("dados", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    if (arquivoImagem) dados.append("imagem", arquivoImagem, arquivoImagem.name);
    try {
        const resposta = await requisicaoAutenticada(caminho, {
            method,
            body: dados
        });
        if (!resposta?.ok) throw new Error(mensagemErroConteudo(resposta));
        await resposta.json();
        document.getElementById(isOpportunity ? "dialogo-oportunidade-osc" : "dialogo-post-osc").close();
        notificar(document.getElementById("notificacao-osc"),
            isOpportunity ? "Oportunidade salva com sucesso." : "Post salvo com sucesso.");
        await carregarConteudoGestao();
        await carregarConteudoPublico(oscAtual.id);
    } catch (erro) {
        feedback.textContent = erro.message || "Não foi possível salvar. Atualize a lista e tente novamente.";
    } finally {
        form.dataset.salvando = "false";
        button.disabled = false;
    }
}

function configurarConteudo() {
    if (document.getElementById("painel-conteudo").dataset.inicializado === "true") return;
    document.getElementById("criar-post-osc").addEventListener("click", () => abrirDialogoPost());
    document.getElementById("criar-oportunidade-osc").addEventListener("click", () => abrirDialogoOportunidade());
    document.getElementById("cancelar-post-osc").addEventListener("click", () => document.getElementById("dialogo-post-osc").close());
    document.getElementById("cancelar-oportunidade-osc").addEventListener("click", () => document.getElementById("dialogo-oportunidade-osc").close());
    for (const tipo of ["post", "oportunidade"]) {
        const prefixo = tipo === "post" ? "post" : "oportunidade";
        const input = document.getElementById(`${prefixo}-imagem-arquivo-osc`);
        const feedbackImagem = document.getElementById(`${prefixo}-imagem-feedback-osc`);
        input.addEventListener("change", () => {
            const estado = imagensConteudoOsc[tipo];
            if (estado.urlPreview) URL.revokeObjectURL(estado.urlPreview);
            estado.arquivo = null;
            estado.urlPreview = null;
            const arquivo = input.files?.[0];
            if (!arquivo) {
                feedbackImagem.textContent = "";
                renderizarPreviewImagemConteudo(tipo);
                return;
            }
            const validacao = validarArquivoImagem(arquivo);
            if (!validacao.valido) {
                input.value = "";
                feedbackImagem.textContent = validacao.mensagem;
                renderizarPreviewImagemConteudo(tipo);
                return;
            }
            estado.arquivo = arquivo;
            estado.urlPreview = URL.createObjectURL(arquivo);
            feedbackImagem.textContent = "";
            renderizarPreviewImagemConteudo(tipo);
        });
        document.querySelector(`#${prefixo}-imagem-preview-osc figcaption button`).addEventListener("click", () => {
            const estado = imagensConteudoOsc[tipo];
            if (estado.urlPreview) URL.revokeObjectURL(estado.urlPreview);
            estado.arquivo = null;
            estado.urlPreview = null;
            input.value = "";
            feedbackImagem.textContent = "Seleção removida. A imagem atual será mantida.";
            renderizarPreviewImagemConteudo(tipo);
        });
    }
    document.getElementById("form-post-osc").addEventListener("submit", event => {
        event.preventDefault();
        void salvarConteudo(event.currentTarget, "post");
    });
    document.getElementById("form-oportunidade-osc").addEventListener("submit", event => {
        event.preventDefault();
        const inicio = document.getElementById("vaga-inicio-osc");
        const fim = document.getElementById("vaga-fim-osc");
        fim.setCustomValidity(fim.value && inicio.value && fim.value < inicio.value
            ? "A data final deve ser igual ou posterior à data inicial." : "");
        void salvarConteudo(event.currentTarget, "opportunity");
    });
    for (const id of ["dialogo-post-osc", "dialogo-oportunidade-osc"]) {
        const dialogo = document.getElementById(id);
        dialogo.addEventListener("close", () => {
            liberarImagemConteudo(id === "dialogo-post-osc" ? "post" : "oportunidade");
        });
        const form = dialogo.querySelector("form");
        dialogo.addEventListener("click", event => {
            if (event.target === dialogo && form.dataset.salvando !== "true") dialogo.close("fora");
        });
        dialogo.addEventListener("keydown", event => {
            if (event.key !== "Escape") return;
            event.preventDefault();
            if (form.dataset.salvando !== "true") dialogo.close("escape");
        });
        dialogo.addEventListener("cancel", event => {
            if (form.dataset.salvando === "true") event.preventDefault();
        });
    }
    document.getElementById("painel-conteudo").dataset.inicializado = "true";
}

async function carregarMembros() {
    const resposta = await requisicaoAutenticada(`/osc/${oscAtual.id}/membros`);
    if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "carregar membros"));
    const membros = await resposta.json();
    const lista = document.getElementById("lista-membros");
    lista.replaceChildren();

    for (const membro of membros) {
        const item = document.createElement("li");
        const dados = document.createElement("div");
        const nome = document.createElement("strong");
        const email = document.createElement("span");
        nome.textContent = membro.nome;
        email.textContent = membro.email;
        dados.append(nome, email);

        const podeGerir = ["CRIADOR", "ADMINISTRADOR"].includes(cargoAtual);
        const podeAlterarEsteCargo = podeGerir && membro.cargo !== "CRIADOR" && (membro.cargo !== "ADMINISTRADOR" || cargoAtual === "CRIADOR");
        if (podeAlterarEsteCargo) {
            const cargo = document.createElement("select");
            cargo.setAttribute("aria-label", `Cargo de ${membro.nome}`);
            const opcoes = ["MEMBRO", "EDITOR", "ADMINISTRADOR"];
            if (cargoAtual === "CRIADOR") opcoes.push("CRIADOR");
            for (const valor of opcoes) {
                const opcao = document.createElement("option");
                opcao.value = valor;
                opcao.textContent = rotuloCargo(valor);
                cargo.append(opcao);
            }
            cargo.value = membro.cargo;
            cargo.addEventListener("change", async () => {
                const cargoAnterior = membro.cargo;
                const cargoSolicitado = cargo.value;
                cargo.disabled = true;
                try {
                    if (cargoSolicitado === "CRIADOR") {
                        const confirmado = await confirmarPorDigitacao({
                            titulo: "Transferir cargo de criador",
                            descricao: "Você está prestes a transferir o cargo de criador desta organização. O novo criador assumirá essa responsabilidade e você passará a ser administrador.",
                            identificacao: membro.nome,
                            instrucao: "Para confirmar, digite:",
                            frase: `TORNAR ${membro.nome} CRIADOR`,
                            textoConfirmar: "Transferir criador",
                            tipo: "Transferência de titularidade",
                            estilo: "destrutivo",
                            mensagemSucesso: "Cargo de criador transferido com sucesso.",
                            origem: cargo,
                            aoConfirmar: async () => {
                                const respostaCargo = await requisicaoAutenticada(`/osc/${oscAtual.id}/membros/${membro.usuarioId}/cargo`, {
                                    method: "PATCH",
                                    headers: { "Content-Type": "application/json" },
                                    body: JSON.stringify({ cargo: "CRIADOR" })
                                });
                                if (!respostaCargo?.ok) throw new Error(await mensagemErroApi(respostaCargo, "transferir criador"));
                                await atualizarCargoAtual();
                                await carregarMembros();
                            }
                        });
                        if (!confirmado) cargo.value = cargoAnterior;
                        return;
                    }
                    const respostaCargo = await requisicaoAutenticada(`/osc/${oscAtual.id}/membros/${membro.usuarioId}/cargo`, {
                        method: "PATCH",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ cargo: cargoSolicitado })
                    });
                    if (!respostaCargo?.ok) throw new Error(await mensagemErroApi(respostaCargo, "alterar cargo"));
                    await atualizarCargoAtual();
                    await carregarMembros();
                    notificar(document.getElementById("membros-feedback"), "Cargo do membro atualizado com sucesso.");
                } catch (erro) {
                    notificar(document.getElementById("membros-feedback"), erro.message || "Não foi possível concluir a operação. Tente novamente.", "erro");
                    cargo.value = cargoAnterior;
                } finally {
                    if (cargo.isConnected) cargo.disabled = false;
                }
            });
            item.append(dados, cargo);
        } else {
            const cargoRotulo = document.createElement("span");
            cargoRotulo.className = "cargo-membro-rotulo";
            cargoRotulo.textContent = rotuloCargo(membro.cargo);
            item.append(dados, cargoRotulo);
        }

        if (podeGerir && membro.cargo !== "CRIADOR" && (membro.cargo !== "ADMINISTRADOR" || cargoAtual === "CRIADOR")) {
            const remover = document.createElement("button");
            remover.type = "button";
            remover.className = "btn btn-secundario";
            remover.textContent = "Remover";
            remover.addEventListener("click", async () => {
                const feedback = document.getElementById("membros-feedback");
                try {
                    await confirmarPorDigitacao({
                        titulo: "Confirmar remoção de membro",
                        descricao: "Você está prestes a remover esta pessoa da organização. Ela perderá seu vínculo com esta ONG.",
                        identificacao: membro.nome,
                        instrucao: "Para confirmar, digite o texto abaixo:",
                        frase: `REMOVER ${membro.nome}`,
                        textoConfirmar: "Remover membro",
                        tipo: "Remoção de membro",
                        estilo: "destrutivo",
                        mensagemSucesso: "Membro removido com sucesso.",
                        origem: remover,
                        aoConfirmar: async () => {
                            const respostaRemocao = await requisicaoAutenticada(
                                `/osc/${oscAtual.id}/membros?email=${encodeURIComponent(membro.email)}`,
                                { method: "DELETE" }
                            );
                            if (!respostaRemocao?.ok) throw new Error(await mensagemErroApi(respostaRemocao, "remover membro"));
                            await carregarMembros();
                        }
                    });
                } catch (erro) {
                    notificar(feedback, erro.message || "Não foi possível concluir a operação. Tente novamente.", "erro");
                }
            });
            item.append(remover);
        }
        lista.append(item);
    }
}

async function configurarGestao() {
    const admin = ["CRIADOR", "ADMINISTRADOR"].includes(cargoAtual);
    const gestao = document.getElementById("gestao-osc");
    const painelMembros = document.getElementById("painel-membros");
    const painelDados = document.getElementById("painel-dados");
    const botaoExcluir = document.getElementById("excluir-osc");
    const painelEdicao = document.getElementById("edicao-osc");
    const resumoDados = document.getElementById("resumo-dados-osc");
    const botaoEditarDados = document.getElementById("alterar-dados-osc");
    const formularioEdicao = document.getElementById("form-edicao-osc");
    const feedbackEdicao = document.getElementById("edicao-feedback");
    const feedbackMembros = document.getElementById("membros-feedback");
    if (oscAtual.arquivada) {
        gestao.hidden = true;
        painelMembros.hidden = true;
        painelDados.hidden = true;
        atualizarZonaPerigo();
        return;
    }
    configurarConteudo();
    void carregarConteudoGestao();
    document.getElementById("form-adicionar-membro").hidden = !admin;
    const podeEditarDados = ["CRIADOR", "ADMINISTRADOR", "EDITOR"].includes(cargoAtual);
    renderizarResumoConfiguracoes(oscAtual);
    painelEdicao.hidden = true;
    botaoEditarDados.hidden = !podeEditarDados;
    document.getElementById("aviso-somente-leitura").hidden = podeEditarDados;
    document.getElementById("campos-edicao-osc").disabled = !podeEditarDados;
    document.querySelector("#form-edicao-osc button[type=submit]").hidden = !podeEditarDados;
    botaoExcluir.hidden = !admin;
    atualizarZonaPerigo();
    if (!botaoEditarDados.dataset.inicializado) {
        botaoEditarDados.addEventListener("click", () => {
            if (!podeEditarDados || formularioEdicao.dataset.carregado !== "true") return;
            resumoDados.hidden = true;
            painelEdicao.hidden = false;
            document.querySelector("#form-edicao-osc input:not([disabled])")?.focus();
        });
        document.getElementById("cancelar-edicao-dados").addEventListener("click", async () => {
            if (formularioEdicao.dataset.salvando === "true") return;
            if (snapshotEdicao && capturarEdicao() !== snapshotEdicao) {
                const dialogo = document.getElementById("dialogo-descartar");
                dialogo.showModal();
                const escolha = await new Promise(resolve => dialogo.addEventListener("close", () => resolve(dialogo.returnValue), { once: true }));
                if (escolha !== "descartar") return;
                if (snapshotDadosEdicao) preencherCamposEdicao(structuredClone(snapshotDadosEdicao));
                definirSnapshotEdicao();
            }
            painelEdicao.hidden = true;
            resumoDados.hidden = false;
            botaoEditarDados.focus();
        });
        botaoEditarDados.dataset.inicializado = "true";
    }
    if (!formularioEdicao.dataset.inicializado) {
        if (podeEditarDados) {
            preencherCamposEdicao(oscAtual);
            try {
                const resposta = await requisicaoAutenticada(`/osc/${oscAtual.id}/edicao`);
                if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "carregar dados"));
                const dados = await resposta.json();
                preencherCamposEdicao(dados);
                snapshotDadosEdicao = structuredClone(dados);
                formularioEdicao.dataset.carregado = "true";
            } catch (erro) {
                notificar(feedbackEdicao, erro.message || "Não foi possível carregar os dados da organização. Tente novamente.", "erro");
            }
        }
        formularioEdicao.addEventListener("submit", async (evento) => {
            evento.preventDefault();
            if (formularioEdicao.dataset.salvando === "true") return;
            if (!formularioEdicao.reportValidity()) return;
            formularioEdicao.dataset.salvando = "true";
            const botaoSalvar = formularioEdicao.querySelector("button[type=submit]");
            botaoSalvar.disabled = true;
            notificar(feedbackEdicao, "Salvando…");
            try {
                const opcionais = (id) => document.getElementById(id).value.trim() || null;
                const numeroOpcional = (id) => document.getElementById(id).value === "" ? null : Number(document.getElementById(id).value);
                const resposta = await requisicaoAutenticada("/osc/put", {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        cnpj: snapshotDadosEdicao?.cnpj || oscAtual.cnpj,
                        descricaoCurta: document.getElementById("edicao-descricao-curta").value.trim(),
                        descricao: document.getElementById("edicao-descricao").value,
                        email: document.getElementById("edicao-email").value,
                        telefone: opcionais("edicao-telefone"), whatsapp: opcionais("edicao-whatsapp"),
                        site: opcionais("edicao-site"), instagram: opcionais("edicao-instagram"),
                        linkedin: opcionais("edicao-linkedin"), youtube: opcionais("edicao-youtube"),
                        areaGeografica: document.getElementById("edicao-area-geografica").value,
                        pais: document.getElementById("edicao-pais").value.trim(),
                        estado: opcionais("edicao-estado"), cidade: document.getElementById("edicao-cidade").value.trim(),
                        cep: document.getElementById("edicao-cep").value.trim(), bairro: document.getElementById("edicao-bairro").value.trim(),
                        rua: document.getElementById("edicao-rua").value.trim(), numero: opcionais("edicao-numero"),
                        complemento: opcionais("edicao-complemento"),
                        diasHorariosFuncionamento: [...document.querySelectorAll("#edicao-dias-semana input:checked")].map(el => el.value).join(", ") || null,
                        horarioAbertura: opcionais("edicao-horario-abertura"), horarioFechamento: opcionais("edicao-horario-fechamento"),
                        atendimentoPresencial: valorBooleano("edicao-presencial"), atendimentoOnline: valorBooleano("edicao-online"),
                        necessitaAgendamento: valorBooleano("edicao-agendamento"), aceitaVoluntarios,
                        frequenciaVoluntariado: opcionais("edicao-frequencia"), idadeMinimaVoluntario: numeroOpcional("edicao-idade-minima"),
                        processoSeletivoVoluntario: valorBooleano("edicao-processo-seletivo"), emiteCertificadoVoluntario: valorBooleano("edicao-certificado"),
                        atividadesVoluntariado: opcionais("edicao-atividades"),
                        causasSecundarias: [...causasEdicao], ods: [...odsEdicao].map(Number)
                    })
                });
                if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "salvar dados"));
                const respostaEdicao = resposta.clone();
                const dadosSalvos = await respostaEdicao.json().catch(() => null);
                notificar(feedbackEdicao, "Informações da organização atualizadas com sucesso.");
                const perfil = await fetch(`${API_URL}/osc/${oscAtual.id}`, { cache: "no-store" });
                if (perfil.ok) { oscAtual = await perfil.json(); renderizarPerfil(oscAtual); }
                renderizarResumoConfiguracoes(oscAtual);
                const edicao = dadosSalvos || await (async () => { const r = await requisicaoAutenticada(`/osc/${oscAtual.id}/edicao`); return r?.ok ? r.json() : null; })();
                if (edicao) { snapshotDadosEdicao = structuredClone(edicao); preencherCamposEdicao(edicao); }
                else snapshotDadosEdicao = structuredClone({
                    ...snapshotDadosEdicao,
                    causasSecundarias: [...causasEdicao], ods: [...odsEdicao].map(Number),
                    diasHorariosFuncionamento: [...document.querySelectorAll("#edicao-dias-semana input:checked")].map(el => el.value).join(", ") || null,
                    aceitaVoluntarios: valorBooleano("edicao-aceita-voluntarios"),
                    frequenciaVoluntariado: opcionais("edicao-frequencia"), idadeMinimaVoluntario: numeroOpcional("edicao-idade-minima"),
                    processoSeletivoVoluntario: valorBooleano("edicao-processo-seletivo"), emiteCertificadoVoluntario: valorBooleano("edicao-certificado"),
                    atividadesVoluntariado: opcionais("edicao-atividades")
                });
                definirSnapshotEdicao();
                painelEdicao.hidden = true;
                resumoDados.hidden = false;
                botaoEditarDados.focus();
            } catch (erro) {
                notificar(feedbackEdicao, erro.message || "Não foi possível salvar as informações.", "erro");
            } finally {
                formularioEdicao.dataset.salvando = "false";
                botaoSalvar.disabled = false;
            }
        });
        formularioEdicao.addEventListener("input", atualizarEstadoEdicao);
        formularioEdicao.addEventListener("change", (evento) => { if (evento.target.id === "edicao-aceita-voluntarios") atualizarCamposVoluntariado(); atualizarEstadoEdicao(); });
        const dialogoDescartar = document.getElementById("dialogo-descartar");
        dialogoDescartar.addEventListener("click", (evento) => {
            if (evento.target === dialogoDescartar) dialogoDescartar.close("cancelar");
        });
        document.getElementById("edicao-descartar").addEventListener("click", async () => {
            const dialogo = dialogoDescartar;
            dialogo.showModal();
            const escolha = await new Promise(resolve => dialogo.addEventListener("close", () => resolve(dialogo.returnValue), { once: true }));
            if (escolha !== "descartar") return;
            if (!snapshotDadosEdicao) return;
            preencherCamposEdicao(structuredClone(snapshotDadosEdicao)); definirSnapshotEdicao();
        });
        formularioEdicao.dataset.inicializado = "true";
        if (formularioEdicao.dataset.carregado === "true") definirSnapshotEdicao();
    }

    if (admin && !document.getElementById("form-adicionar-membro").dataset.inicializado) {
        document.getElementById("form-adicionar-membro").addEventListener("submit", async (evento) => {
            evento.preventDefault();
            const formulario = evento.currentTarget;
            if (!formulario.reportValidity()) return;
            const emailInput = document.getElementById("novo-membro-email");
            const botaoAdicionar = formulario.querySelector("button[type=submit]");
            const email = emailInput.value.trim();
            if (!emailInput.validity.valid || !email) {
                emailInput.setCustomValidity("Informe um endereço de e-mail válido.");
                emailInput.reportValidity();
                emailInput.addEventListener("input", () => emailInput.setCustomValidity(""), { once: true });
                return;
            }
            botaoAdicionar.disabled = true;
            try {
                const resposta = await requisicaoAutenticada(`/osc/${oscAtual.id}/membros`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        email,
                        cargo: document.getElementById("novo-membro-cargo").value
                    })
                });
                if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "adicionar"));
                let listaAtualizada = true;
                try {
                    await carregarMembros();
                } catch {
                    listaAtualizada = false;
                }
                formulario.reset();
                notificar(feedbackMembros, listaAtualizada
                    ? "Membro adicionado à organização com sucesso."
                    : "Membro adicionado à organização, mas não foi possível atualizar a lista. Tente atualizar a lista em instantes.");
            } catch (erro) {
                notificar(feedbackMembros, erro.message || "Não foi possível concluir a operação. Tente novamente.", "erro");
            } finally {
                botaoAdicionar.disabled = false;
            }
        });
        document.getElementById("form-adicionar-membro").dataset.inicializado = "true";
    }

    try { await carregarMembros(); }
    catch (erro) { notificar(feedbackMembros, erro.message || "Não foi possível carregar a lista de membros.", "erro"); }

    if (!botaoExcluir.dataset.inicializado) {
        botaoExcluir.addEventListener("click", async () => {
            try {
                await confirmarPorDigitacao({
                    titulo: "Excluir organização",
                    descricao: "Esta ação excluirá a organização e poderá remover permanentemente suas informações e vínculos. Essa operação não deve ser realizada por engano.",
                    identificacao: oscAtual.nome,
                    instrucao: "Para confirmar, digite exatamente:",
                    frase: "EXCLUIR ONG",
                    textoConfirmar: "Excluir organização",
                    tipo: "Exclusão permanente",
                    estilo: "destrutivo",
                    mensagemSucesso: "Organização excluída com sucesso.",
                    origem: botaoExcluir,
                    aoConfirmar: async () => {
                        const resposta = await requisicaoAutenticada(`/osc/${oscAtual.id}`, { method: "DELETE" });
                        if (!resposta?.ok) throw new Error(await mensagemErroApi(resposta, "excluir organização"));
                        window.location.assign("/pages/oscs-page.html");
                    }
                });
            } catch (erro) {
                notificar(feedbackMembros, erro.message || "Não foi possível concluir a operação. Tente novamente.", "erro");
            }
        });
        botaoExcluir.dataset.inicializado = "true";
    }
}

async function carregarPermissoes(id) {
    if (obterEstadoSessao() === "anonymous") {
        definirAcessoAbasPrivadas(false);
        return;
    }
    try {
        const resposta = await requisicaoAutenticada("/osc/minhas");
        if (!resposta?.ok) {
            definirAcessoAbasPrivadas(false);
            return;
        }
        const organizacoes = await resposta.json();
        const vinculo = organizacoes.find((osc) => Number(osc.id) === Number(id));
        if (!vinculo?.cargo || oscAtual?.arquivada) {
            definirAcessoAbasPrivadas(false);
            if (vinculo && oscAtual?.arquivada) await configurarGestao();
            return;
        }
        cargoAtual = vinculo.cargo;
        definirAcessoAbasPrivadas(true);
        await configurarGestao();
    } catch {
        definirAcessoAbasPrivadas(false);
    }
}

async function atualizarCargoAtual() {
    const resposta = await requisicaoAutenticada("/osc/minhas");
    if (!resposta?.ok) return;
    const organizacoes = await resposta.json();
    cargoAtual = organizacoes.find((osc) => Number(osc.id) === Number(oscAtual.id))?.cargo || null;
}

async function carregarPerfil() {
    resetarConteudoPublico();
    const carregamento = document.getElementById("perfil-carregamento");
    const erro = document.getElementById("perfil-erro");
    const conteudo = document.getElementById("perfil-conteudo");
    const tituloErro = document.getElementById("perfil-erro-titulo");
    const mensagemErro = document.getElementById("perfil-erro-mensagem");
    carregamento.hidden = false;
    erro.hidden = true;
    conteudo.hidden = true;
    carregamento.textContent = "Carregando perfil da organização…";

    const id = Number(new URLSearchParams(window.location.search).get("id"));
    if (!Number.isInteger(id) || id <= 0) {
        carregamento.hidden = true;
        tituloErro.textContent = "Perfil não identificado";
        mensagemErro.textContent = "O link não contém um identificador válido para a organização.";
        erro.hidden = false;
        return;
    }

    try {
        const privada = await fetchAutenticado(`/osc/${encodeURIComponent(id)}/privado`);
        const resposta = [401, 403, 404].includes(privada.status)
            ? await fetch(`${API_URL}/osc/${encodeURIComponent(id)}`, {
                headers: { Accept: "application/json" },
                cache: "no-store"
            })
            : privada;
        if (resposta.status === 404) {
            tituloErro.textContent = "Organização não encontrada";
            mensagemErro.textContent = "Este perfil não está disponível. Confira o link ou volte à lista de organizações.";
            throw new Error("estado-ja-apresentado");
        }
        if (!resposta.ok) throw new Error("api-indisponivel");
        const osc = await resposta.json();
        if (!osc || Number(osc.id) !== id || typeof osc.nome !== "string") throw new Error("resposta-invalida");
        oscAtual = osc;
        renderizarPerfil(osc);
        carregamento.hidden = true;
        conteudo.hidden = false;
        void carregarConteudoPublico(id);
        void carregarPermissoes(id);
        return;
    } catch (falha) {
        carregamento.hidden = true;
        if (falha.message !== "estado-ja-apresentado") {
            tituloErro.textContent = "Não foi possível carregar o perfil";
            mensagemErro.textContent = "Verifique sua conexão e tente novamente. As informações da organização não foram carregadas.";
        }
        erro.hidden = false;
    }
}

document.getElementById("perfil-tentar-novamente").addEventListener("click", carregarPerfil);
document.addEventListener("DOMContentLoaded", carregarPerfil, { once: true });

const modalDoacao = document.getElementById("modal-doacao-perfil");
const formularioDoacao = document.getElementById("form-doacao-perfil");
const botaoDoar = document.getElementById("efetuar-doacao");
const botaoCancelarDoacao = document.getElementById("cancelar-doacao-perfil");
const campoValorDoacao = document.getElementById("valor-doacao-perfil");
let overflowAnterior = "";

function fecharModalDoacao() {
    if (modalDoacao.hidden) return;
    modalDoacao.hidden = true;
    document.body.style.overflow = overflowAnterior;
    document.querySelector(".perfil-publico")?.removeAttribute("inert");
    document.querySelector(".navbar")?.removeAttribute("inert");
    document.querySelector("#footer")?.removeAttribute("inert");
    botaoDoar.focus();
}

botaoDoar.addEventListener("click", () => {
    overflowAnterior = document.body.style.overflow;
    campoValorDoacao.value = "5.00";
    modalDoacao.hidden = false;
    document.body.style.overflow = "hidden";
    document.querySelector(".perfil-publico")?.setAttribute("inert", "");
    document.querySelector(".navbar")?.setAttribute("inert", "");
    document.querySelector("#footer")?.setAttribute("inert", "");
    campoValorDoacao.focus();
});

botaoCancelarDoacao.addEventListener("click", fecharModalDoacao);

formularioDoacao.addEventListener("submit", evento => {
    evento.preventDefault();
    iniciarCheckoutDoacao();
});

async function iniciarCheckoutDoacao() {
    const feedback = document.getElementById("feedback-doacao-perfil");
    const botaoConfirmar = formularioDoacao.querySelector('[type="submit"]');
    const valor = campoValorDoacao.value;
    const oscId = Number(new URLSearchParams(window.location.search).get("id"));

    feedback.textContent = "";
    if (!Number.isInteger(oscId) || oscId <= 0) {
        feedback.textContent = "Não foi possível identificar a organização para esta doação.";
        return;
    }

    if (!Number.isFinite(Number(valor)) || Number(valor) < 5) {
        feedback.textContent = "O valor mínimo para doação é R$ 5,00.";
        campoValorDoacao.focus();
        return;
    }

    botaoConfirmar.disabled = true;
    botaoConfirmar.textContent = "Conectando ao pagamento…";

    try {
        const csrf = await obterTokenCSRF();
        const formularioCheckout = document.createElement("form");
        formularioCheckout.method = "POST";
        formularioCheckout.action = `${API_URL.replace(/\/+$/, "")}/stripe/create-checkout-session`;
        formularioCheckout.hidden = true;

        for (const [nome, valorCampo] of Object.entries({
            oscId: String(oscId),
            valor,
            _csrf: csrf.token
        })) {
            const campo = document.createElement("input");
            campo.type = "hidden";
            campo.name = nome;
            campo.value = valorCampo;
            formularioCheckout.append(campo);
        }

        document.body.append(formularioCheckout);
        formularioCheckout.submit();
    } catch (erro) {
        console.error("Não foi possível iniciar o checkout da doação:", erro);
        feedback.textContent = "Não foi possível iniciar o pagamento. Verifique sua conexão e tente novamente.";
        botaoConfirmar.disabled = false;
        botaoConfirmar.textContent = "Confirmar";
    }
}

document.addEventListener("keydown", evento => {
    if (modalDoacao.hidden) return;
    if (evento.key === "Escape") {
        fecharModalDoacao();
    } else if (evento.key === "Tab") {
        const controles = [...modalDoacao.querySelectorAll("button, input")];
        const primeiro = controles[0];
        const ultimo = controles.at(-1);
        if (evento.shiftKey && document.activeElement === primeiro) {
            evento.preventDefault();
            ultimo.focus();
        } else if (!evento.shiftKey && document.activeElement === ultimo) {
            evento.preventDefault();
            primeiro.focus();
        }
    }
});
