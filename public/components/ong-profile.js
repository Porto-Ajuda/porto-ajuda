import { getAPI_URL } from "./modoBack.js";
const API_URL = getAPI_URL();

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
}

async function carregarPerfil() {
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
        const resposta = await fetch(`${API_URL}/osc/${encodeURIComponent(id)}`, {
            headers: { Accept: "application/json" },
            cache: "no-store"
        });
        if (resposta.status === 404) {
            tituloErro.textContent = "Organização não encontrada";
            mensagemErro.textContent = "Este perfil não está disponível. Confira o link ou volte à lista de organizações.";
            throw new Error("estado-ja-apresentado");
        }
        if (!resposta.ok) throw new Error("api-indisponivel");
        const osc = await resposta.json();
        if (!osc || Number(osc.id) !== id || typeof osc.nome !== "string") throw new Error("resposta-invalida");
        renderizarPerfil(osc);
        carregamento.hidden = true;
        conteudo.hidden = false;
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
