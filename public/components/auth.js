import { getAPI_URL } from "./modoBack.js";

const API_URL = getAPI_URL();
const INTERVALO_RENOVACAO = 10 * 60 * 1000;
const LOCK_RENOVACAO = "porto-ajuda-renovacao-sessao";
const CHAVE_RENOVACAO = "porto-ajuda-renovacao-sessao-concluida";
export const CHAVE_ESTADO_SESSAO = "porto-ajuda-estado-sessao";

let temporizadorRenovacao = null;
let renovacaoEmAndamento = null;
let tokenCsrfEmAndamento = null;
let renovacaoPeriodicaAtiva = false;
let ultimaTentativaRenovacao = null;

function lerArmazenamento(chave) {
    try {
        return localStorage.getItem(chave);
    } catch {
        return null;
    }
}

function gravarArmazenamento(chave, valor) {
    try {
        localStorage.setItem(chave, valor);
    } catch {
        // O fluxo de autenticação continua funcionando sem localStorage.
    }
}

export function obterEstadoSessao() {
    return lerArmazenamento(CHAVE_ESTADO_SESSAO);
}

export function marcarSessaoAutenticada({ novaSessao = false } = {}) {
    if (novaSessao) {
        registrarRenovacaoConcluida();
    }

    gravarArmazenamento(CHAVE_ESTADO_SESSAO, "authenticated");
}

export function marcarSessaoAnonima() {
    gravarArmazenamento(CHAVE_ESTADO_SESSAO, "anonymous");
    pararRenovacaoPeriodica();
}

function lerMarcadorRenovacao() {
    return lerArmazenamento(CHAVE_RENOVACAO);
}

function obterHorarioMarcador(marcador = lerMarcadorRenovacao()) {
    if (!marcador) {
        return null;
    }

    const horario = Number.parseInt(marcador, 10);
    return Number.isFinite(horario) ? horario : null;
}

function renovacaoRecente(marcador) {
    const horario = obterHorarioMarcador(marcador);
    return horario !== null && Date.now() - horario < INTERVALO_RENOVACAO;
}

async function carregarTokenCSRF() {
    const csrfResponse = await fetch(`${API_URL}/usuario/csrf`, {
        method: "GET",
        credentials: "include",
        cache: "no-store"
    });

    if (!csrfResponse.ok) {
        throw new Error(
            `Não foi possível obter o token CSRF (HTTP ${csrfResponse.status}).`
        );
    }

    const csrf = await csrfResponse.json();

    if (!csrf.headerName || !csrf.token) {
        throw new Error("A API não retornou um token CSRF válido.");
    }

    return csrf;
}

export function obterTokenCSRF() {
    if (tokenCsrfEmAndamento !== null) {
        return tokenCsrfEmAndamento;
    }

    const carregarComCoordenacao = () => {
        if (
            typeof navigator !== "undefined" &&
            typeof navigator.locks?.request === "function"
        ) {
            return navigator.locks.request(
                "porto-ajuda-token-csrf",
                carregarTokenCSRF
            );
        }

        return carregarTokenCSRF();
    };

    tokenCsrfEmAndamento = carregarComCoordenacao()
        .finally(() => {
            tokenCsrfEmAndamento = null;
        });

    return tokenCsrfEmAndamento;
}

async function solicitarRenovacao() {
    const csrf = await obterTokenCSRF();

    const refreshResponse = await fetch(`${API_URL}/usuario/refresh`, {
        method: "POST",
        credentials: "include",
        headers: {
            [csrf.headerName]: csrf.token
        },
        cache: "no-store"
    });

    if (refreshResponse.status === 401) {
        let erroRefresh;

        try {
            erroRefresh = await refreshResponse.clone().json();
        } catch {
            // Um 401 sem código explícito pode vir de proxy ou intermediário.
        }

        if (erroRefresh?.codigo !== "SESSAO_INVALIDA") {
            return {
                ok: false,
                response: refreshResponse
            };
        }

        pararRenovacaoPeriodica();
        marcarSessaoAnonima();

        return {
            ok: false,
            sessaoInvalida: true,
            response: refreshResponse
        };
    }

    return {
        ok: refreshResponse.ok,
        response: refreshResponse,
        csrf: refreshResponse.ok ? csrf : null
    };
}

function registrarRenovacaoConcluida() {
    gravarArmazenamento(
        CHAVE_RENOVACAO,
        `${Date.now()}-${Math.random().toString(36).slice(2)}`
    );
}

async function renovarComCoordenacao(forcar) {
    const marcadorInicial = lerMarcadorRenovacao();

    const renovarSeNecessario = async () => {
        const marcadorAtual = lerMarcadorRenovacao();
        const outroRefreshConcluido = marcadorAtual !== marcadorInicial;

        if (
            renovacaoRecente(marcadorAtual) &&
            (!forcar || outroRefreshConcluido)
        ) {
            return {
                ok: true,
                compartilhada: true
            };
        }

        const resultado = await solicitarRenovacao();

        if (resultado.ok) {
            registrarRenovacaoConcluida();
            marcarSessaoAutenticada();
        }

        return resultado;
    };

    if (
        typeof navigator !== "undefined" &&
        typeof navigator.locks?.request === "function"
    ) {
        return navigator.locks.request(LOCK_RENOVACAO, renovarSeNecessario);
    }

    return renovarSeNecessario();
}

/** Retorna o resultado do backend sem confundir falha temporária com logout. */
export function renovarAccessToken({ forcar = false } = {}) {
    if (renovacaoEmAndamento !== null) {
        return renovacaoEmAndamento;
    }

    ultimaTentativaRenovacao = Date.now();

    // Um marcador local de anonimato pode estar desatualizado (por exemplo,
    // após login em outra aba). Nesse caso, valide o refresh diretamente no
    // backend em vez de transformar o marcador em uma decisão de autenticação.
    const validarNoBackend = forcar || obterEstadoSessao() === "anonymous";

    renovacaoEmAndamento = renovarComCoordenacao(validarNoBackend)
        .catch((erro) => {
            console.error("Erro ao renovar a sessão:", erro);
            return {
                ok: false,
                error: erro
            };
        })
        .finally(() => {
            renovacaoEmAndamento = null;

            if (renovacaoPeriodicaAtiva) {
                reagendarRenovacaoPeriodica();
            }
        });

    return renovacaoEmAndamento;
}

function calcularAtrasoRenovacao() {
    const agora = Date.now();
    const horarioUltimaRenovacao = obterHorarioMarcador();
    const proximaRenovacao = horarioUltimaRenovacao === null
        ? agora + INTERVALO_RENOVACAO
        : horarioUltimaRenovacao + INTERVALO_RENOVACAO;
    const proximaTentativa = ultimaTentativaRenovacao === null
        ? agora
        : ultimaTentativaRenovacao + INTERVALO_RENOVACAO;

    return Math.max(0, Math.max(proximaRenovacao, proximaTentativa) - agora);
}

function agendarRenovacaoPeriodica() {
    if (!renovacaoPeriodicaAtiva || temporizadorRenovacao !== null) {
        return;
    }

    temporizadorRenovacao = setTimeout(async () => {
        temporizadorRenovacao = null;

        const resultado = await renovarAccessToken();

        if (!resultado.ok && !resultado.sessaoInvalida) {
            const status = resultado.response?.status;
            console.warn(
                status
                    ? `Falha temporária ao renovar a sessão (HTTP ${status}).`
                    : "Falha temporária ao renovar a sessão."
            );
        }

        agendarRenovacaoPeriodica();
    }, calcularAtrasoRenovacao());
}

function reagendarRenovacaoPeriodica() {
    if (temporizadorRenovacao !== null) {
        clearTimeout(temporizadorRenovacao);
        temporizadorRenovacao = null;
    }

    agendarRenovacaoPeriodica();
}

/** Inicia uma renovação alinhada ao último refresh concluído entre as abas. */
export function iniciarRenovacaoPeriodica() {
    if (renovacaoPeriodicaAtiva) {
        return;
    }

    renovacaoPeriodicaAtiva = true;
    agendarRenovacaoPeriodica();
}

/** Interrompe o timer após logout ou confirmação de sessão inválida. */
export function pararRenovacaoPeriodica() {
    renovacaoPeriodicaAtiva = false;

    if (temporizadorRenovacao !== null) {
        clearTimeout(temporizadorRenovacao);
        temporizadorRenovacao = null;
    }
}

if (typeof window !== "undefined") {
    window.addEventListener("storage", (evento) => {
        if (
            evento.key === CHAVE_ESTADO_SESSAO &&
            evento.newValue === "anonymous"
        ) {
            pararRenovacaoPeriodica();
        }

        if (evento.key === CHAVE_RENOVACAO && renovacaoPeriodicaAtiva) {
            reagendarRenovacaoPeriodica();
        }
    });
}
