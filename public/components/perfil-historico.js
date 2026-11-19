import { fetchAutenticado, sessaoInvalidaConfirmada } from "./api.js";
import { definirAbaDisponivel } from "./abas-perfil.js";
import { calcularVisibilidadeHistorico, criarRegistroHistorico, normalizarPaginaCandidaturas } from "./perfil-historico-logic.js";

const PAGE_SIZE = 20;
const $ = id => document.getElementById(id);
const lista = $("linha-tempo-registros");
const estadoCarregamento = $("registro-carregando");
const erro = $("registro-erro");
const mensagemErro = $("registro-erro-mensagem");
const linkEntrar = $("registro-entrar");
const tentarNovamente = $("registro-tentar-novamente");
const carregarMais = $("registro-carregar-mais");
const totalCandidaturas = $("registro-total-candidaturas");
const areaHistorico = $("area-historico-atividades");
const semAtividades = $("registro-sem-atividades");

let registros = [];
let doacoesCampanha = [];
let totalElements = 0;
let currentPage = 0;
let totalPages = 0;
let busy = false;
let retry = null;

function notificarFiltros() {
    const total = totalElements + doacoesCampanha.length;
    const types = [...(totalElements > 0 ? ["voluntariado"] : []), ...(doacoesCampanha.length ? ["doacao"] : [])];
    lista.dataset.totalRecords = String(total);
    const visibility = calcularVisibilidadeHistorico(total,types);
    lista.dataset.availableTypes = JSON.stringify(visibility.availableTypes);
    lista.dataset.showTypeFilters = String(visibility.showTypeFilters);
    lista.dataset.hasMore = String(currentPage + 1 < totalPages);
    lista.dataset.ready = "true";
    lista.dataset.loading = "false";
    document.dispatchEvent(new Event("porto:historico-perfil-atualizado"));
}

function criarRegistroDoacao(documento,item){
    const li=documento.createElement("li");li.className="registro";li.dataset.tipo="doacao";
    const title=documento.createElement("h3");title.className="registro-atividade-titulo";title.textContent=item.campaignTitle||"Campanha de doação";
    const status=documento.createElement("span");status.className="etiqueta";
    status.textContent=({PENDING:"Aguardando pagamento",SUCCEEDED:"Confirmada",FAILED:"Falhou",CANCELED:"Cancelada",PARTIALLY_REFUNDED:"Estorno parcial",REFUNDED:"Estornada",DISPUTED:"Em contestação",DISPUTE_LOST:"Contestação perdida"})[item.status]||"Status indisponível";
    const heading=documento.createElement("div");heading.className="registro-atividade-cabecalho";heading.append(title,status);
    const ngo=documento.createElement("p");ngo.textContent=`ONG: ${item.ngoName||"Organização"}`;
    const amount=documento.createElement("p");amount.textContent=`Valor líquido destinado à ONG: ${new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(item.netAmountCents||0)/100)}`;
    const date=documento.createElement("time");date.className="data-registro";
    const parsed=new Date(item.createdAt);date.textContent=Number.isNaN(parsed.getTime())?"Data indisponível":`Contribuição em ${parsed.toLocaleDateString("pt-BR")}`;
    if(!Number.isNaN(parsed.getTime()))date.dateTime=parsed.toISOString();
    li.append(heading,ngo,amount,date);
    if(Number(item.feeShortfallCents)>0){const alert=documento.createElement("p");alert.textContent=`Conciliação financeira pendente: custos acima da estimativa em ${new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(item.feeShortfallCents)/100)}.`;li.append(alert);}
    return li;
}
function renderRegistros(){
    if(!lista)return;
    lista.replaceChildren(...[...registros.map(item=>criarRegistroHistorico(document,item)),...doacoesCampanha.map(item=>criarRegistroDoacao(document,item))]);
    const total=registros.length+doacoesCampanha.length;
    const visibility=calcularVisibilidadeHistorico(total,[...(registros.length?["voluntariado"]:[]),...(doacoesCampanha.length?["doacao"]:[])]);
    definirAbaDisponivel("aba-registro",visibility.hasRecords);
    if(areaHistorico)areaHistorico.hidden=!visibility.hasRecords;
    if(semAtividades)semAtividades.hidden=visibility.hasRecords;
    if(totalCandidaturas)totalCandidaturas.textContent=String(total);
    notificarFiltros();
}

async function carregarDoacoesCampanha(){
    if(!lista)return;
    try{
        const response=await fetchAutenticado("/donation-campaigns/history",{headers:{Accept:"application/json"}});
        if(!response.ok)return;
        const result=await response.json();doacoesCampanha=Array.isArray(result)?result:[];
        const unavailable=$("registro-doacoes-indisponivel");if(unavailable)unavailable.hidden=true;
        renderRegistros();
    }catch{/* Preserve the existing candidacy history if campaign history is unavailable. */}
}

function mostrarErro(mensagem, precisaEntrar = false) {
    erro.hidden = false;
    mensagemErro.textContent = mensagem;
    linkEntrar.hidden = !precisaEntrar;
    estadoCarregamento.hidden = true;
    lista.setAttribute("aria-busy", "false");
    lista.dataset.loading = "false";
    lista.dataset.error = "true";
    if (areaHistorico) areaHistorico.hidden = false;
    if (semAtividades) semAtividades.hidden = true;
}

async function carregarPagina(page, append) {
    if (busy || !lista) return;
    busy = true;
    retry = () => carregarPagina(page, append);
    erro.hidden = true;
    estadoCarregamento.hidden = false;
    estadoCarregamento.textContent = append ? "Carregando mais candidaturas…" : "Carregando seu histórico…";
    lista.setAttribute("aria-busy", "true");
    lista.dataset.loading = "true";
    carregarMais.disabled = true;
    try {
        const response = await fetchAutenticado(`/usuario/candidaturas?page=${page}&size=${PAGE_SIZE}`, {
            headers: { Accept: "application/json" }
        });
        if (response.status === 401 || response.status === 403) {
            const invalidSession = response.status === 401 && sessaoInvalidaConfirmada(response);
            mostrarErro(invalidSession || response.status === 401
                ? "Entre na sua conta para consultar seu histórico."
                : "Sua conta não tem autorização para consultar este histórico.", invalidSession || response.status === 401);
            return;
        }
        if (!response.ok) throw new Error("Não foi possível carregar seu histórico.");

        const result = normalizarPaginaCandidaturas(await response.json());
        currentPage = result.number;
        totalPages = result.totalPages;
        totalElements = result.totalElements;
        registros = append ? [...registros, ...result.content] : result.content;
        renderRegistros();
        lista.setAttribute("aria-busy", "false");
        estadoCarregamento.hidden = true;
        erro.hidden = true;
        lista.dataset.error = "false";
        carregarMais.hidden = currentPage + 1 >= totalPages;
        carregarMais.disabled = false;
        totalCandidaturas.textContent = String(totalElements+doacoesCampanha.length);
        notificarFiltros();
    } catch {
        mostrarErro("Não foi possível carregar seu histórico. Tente novamente.");
        carregarMais.hidden = !append || currentPage + 1 >= totalPages;
        carregarMais.disabled = false;
    } finally {
        busy = false;
    }
}

tentarNovamente?.addEventListener("click", () => { if (retry) void retry(); });
carregarMais?.addEventListener("click", () => {
    if (!busy && currentPage + 1 < totalPages) void carregarPagina(currentPage + 1, true);
});

if (lista) void carregarPagina(0, false);
if (lista) void carregarDoacoesCampanha();
for (const evento of ["porto:activity-created", "porto:application-created"]) {
    document.addEventListener(evento, () => { if (!busy) void carregarPagina(0, false); });
}
