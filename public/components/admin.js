import { getAPI_URL } from "./modoBack.js";

(() => {
    "use strict";

    const API_URL = getAPI_URL();
    const TOLERANCIA_CLOCK_MS = 1500;
    const formatarNumero = new Intl.NumberFormat("pt-BR");
    const formatarDataHora = new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short"
    });

    function aplicarPreferenciasVisuaisLocais() {
        try {
            const preferences = JSON.parse(localStorage.getItem("portoAjudaPreferencias") ?? "null");
            if (!preferences || typeof preferences !== "object" || Array.isArray(preferences)) return;
            const fontSize = Number(preferences.tamanhoFonte);
            if (Number.isFinite(fontSize)) {
                document.documentElement.style.fontSize = `${Math.min(150, Math.max(80, fontSize))}%`;
            }
            document.body.classList.toggle("modo-claro", preferences.tema === "claro");
            document.body.classList.toggle("alto-contraste", preferences.altoContraste === true);
        } catch {
            // Preferências de aparência não podem bloquear a área administrativa.
        }
    }

    aplicarPreferenciasVisuaisLocais();

    const elements = {
        content: document.getElementById("conteudo-dashboard"),
        loading: document.getElementById("estado-carregamento"),
        unauthenticated: document.getElementById("estado-nao-autenticado"),
        expired: document.getElementById("estado-sessao-expirada"),
        error: document.getElementById("estado-erro-indicadores"),
        errorMessage: document.getElementById("mensagem-erro-indicadores"),
        sessionStatus: document.getElementById("estado-sessao-admin"),
        metricGrid: document.querySelector(".metric-grid"),
        activityState: document.getElementById("estado-atividade-admin"),
        activityList: document.getElementById("lista-atividade-admin"),
        generatedAt: document.getElementById("indicadores-atualizados-em"),
        retry: document.getElementById("tentar-novamente-admin"),
        refresh: document.getElementById("atualizar-indicadores"),
        logout: document.getElementById("sair-painel-admin"),
        menu: document.getElementById("menu-admin"),
        menuToggle: document.getElementById("alternar-menu-admin")
    };

    let requestVersion = 0;
    let activeController = null;
    let expirationTimer = null;
    let sessionCheckInFlight = null;
    let lastSessionCheckAt = 0;
    let loading = false;
    let loggingOut = false;

    function limparDadosProtegidos() {
        document.querySelectorAll("[data-metric]").forEach(element => {
            element.textContent = "—";
        });
        elements.activityList.replaceChildren();
        elements.activityList.hidden = true;
        elements.activityState.textContent = "";
        elements.activityState.hidden = true;
        elements.generatedAt.textContent = "";
        elements.content.hidden = true;
        elements.refresh.hidden = true;
        elements.logout.hidden = true;
    }

    function esconderEstados() {
        elements.loading.hidden = true;
        elements.unauthenticated.hidden = true;
        elements.expired.hidden = true;
        elements.error.hidden = true;
    }

    function abortarRequisicoesAtuais() {
        requestVersion += 1;
        activeController?.abort();
        activeController = null;
        loading = false;
    }

    function encerrarComSessaoExpirada(mensagem = "Sua sessão administrativa expirou.") {
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparDadosProtegidos();
        esconderEstados();
        elements.expired.querySelector("h2").textContent = mensagem;
        elements.expired.hidden = false;
        elements.sessionStatus.textContent = "Sessão administrativa inativa";
    }

    function mostrarErro(mensagem) {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparDadosProtegidos();
        esconderEstados();
        elements.errorMessage.textContent = mensagem;
        elements.error.hidden = false;
        elements.sessionStatus.textContent = "Não foi possível validar a sessão";
    }

    function mostrarNaoAutenticado() {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparDadosProtegidos();
        esconderEstados();
        elements.unauthenticated.hidden = false;
        elements.sessionStatus.textContent = "Autenticação necessária";
    }

    async function obterSessao(signal) {
        const inicioRequisicao = performance.now();
        const resposta = await fetch(`${API_URL}/admin/auth/session`, {
            method: "GET",
            credentials: "include",
            cache: "no-store",
            headers: { Accept: "application/json" },
            signal
        });

        if (resposta.status === 401 || resposta.status === 403) {
            return { authenticated: false };
        }
        if (resposta.status !== 204) {
            throw new Error("session-unavailable");
        }

        const serverTime = Date.parse(resposta.headers.get("X-PA-ADMIN-SERVER-TIME"));
        const absoluteExpiry = Date.parse(resposta.headers.get("X-PA-ADMIN-ABSOLUTE-EXPIRES-AT"));
        const idleExpiry = Date.parse(resposta.headers.get("X-PA-ADMIN-IDLE-EXPIRES-AT"));
        if (![serverTime, absoluteExpiry, idleExpiry].every(Number.isFinite)) {
            throw new Error("invalid-session-metadata");
        }

        const expiresAt = Math.min(absoluteExpiry, idleExpiry);
        const timeFromServer = expiresAt - serverTime;
        const requestElapsed = performance.now() - inicioRequisicao;
        const remainingMs = timeFromServer - requestElapsed - TOLERANCIA_CLOCK_MS;
        if (remainingMs <= 0) return { authenticated: false };

        return {
            authenticated: true,
            expiresAt,
            remainingMs
        };
    }

    async function obterIndicadores(signal) {
        const resposta = await fetch(`${API_URL}/admin/dashboard/overview`, {
            method: "GET",
            credentials: "include",
            cache: "no-store",
            headers: { Accept: "application/json" },
            signal
        });

        if (resposta.status === 401 || resposta.status === 403) {
            return { expired: true };
        }
        if (!resposta.ok) {
            throw new Error(resposta.status === 503 ? "metrics-unavailable" : "metrics-failed");
        }

        const result = await resposta.json();
        const metricNames = [
            "registeredUsers",
            "activeOrganizations",
            "inactiveOrganizations",
            "archivedOrganizations",
            "publications",
            "volunteerOpportunities"
        ];
        if (!metricNames.every(name => Number.isSafeInteger(result?.[name]) && result[name] >= 0)) {
            throw new Error("invalid-metrics");
        }
        if (typeof result.recentActivityAvailable !== "boolean" || !Array.isArray(result.recentActivity)) {
            throw new Error("invalid-activity");
        }
        if (!Number.isFinite(Date.parse(result.generatedAt))) {
            throw new Error("invalid-generated-at");
        }
        return { expired: false, result };
    }

    function renderizarAtividade(overview) {
        elements.activityList.replaceChildren();
        elements.activityList.hidden = true;
        elements.activityState.hidden = false;

        if (!overview.recentActivityAvailable) {
            elements.activityState.textContent = "Atividade administrativa indisponível no momento.";
            return;
        }
        if (overview.recentActivity.length === 0) {
            elements.activityState.textContent = "Nenhuma atividade administrativa encontrada.";
            return;
        }

        elements.activityState.hidden = true;
        elements.activityList.hidden = false;
        overview.recentActivity.forEach(item => {
            const time = Date.parse(item?.occurredAt);
            if (typeof item?.description !== "string" || !Number.isFinite(time)) return;

            const row = document.createElement("li");
            row.className = "activity-item";
            const dot = document.createElement("span");
            dot.className = "activity-dot";
            dot.setAttribute("aria-hidden", "true");
            const description = document.createElement("span");
            description.className = "activity-description";
            description.textContent = item.description;
            const timestamp = document.createElement("time");
            timestamp.className = "activity-time";
            timestamp.dateTime = new Date(time).toISOString();
            timestamp.textContent = formatarDataHora.format(time);
            row.append(dot, description, timestamp);
            elements.activityList.append(row);
        });

        if (!elements.activityList.childElementCount) {
            elements.activityList.hidden = true;
            elements.activityState.hidden = false;
            elements.activityState.textContent = "Nenhuma atividade administrativa encontrada.";
        }
    }

    function renderizarIndicadores(overview) {
        const metrics = {
            registeredUsers: overview.registeredUsers,
            activeOrganizations: overview.activeOrganizations,
            inactiveOrganizations: overview.inactiveOrganizations,
            archivedOrganizations: overview.archivedOrganizations,
            publications: overview.publications,
            volunteerOpportunities: overview.volunteerOpportunities
        };

        Object.entries(metrics).forEach(([name, value]) => {
            const element = document.querySelector(`[data-metric="${name}"]`);
            if (element) element.textContent = formatarNumero.format(value);
        });

        renderizarAtividade(overview);
        elements.generatedAt.textContent = `Indicadores consultados em ${formatarDataHora.format(Date.parse(overview.generatedAt))}.`;
        elements.content.hidden = false;
        elements.refresh.hidden = false;
        elements.logout.hidden = false;
        elements.sessionStatus.textContent = "Sessão administrativa ativa";
    }

    function agendarExpiracao(session) {
        window.clearTimeout(expirationTimer);
        const deadline = session.remainingMs;
        const localExpiry = Date.now() + deadline;
        const when = formatarDataHora.format(localExpiry);
        elements.sessionStatus.textContent = `Sessão ativa · próxima expiração às ${when}`;
        expirationTimer = window.setTimeout(() => {
            encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
        }, deadline);
    }

    async function revalidarSessaoAoRetornar() {
        if (elements.content.hidden || loading || loggingOut || document.hidden) return;
        if (sessionCheckInFlight) return sessionCheckInFlight;
        if (performance.now() - lastSessionCheckAt < 3000) return;
        lastSessionCheckAt = performance.now();

        const validationVersion = requestVersion;
        const controller = new AbortController();
        sessionCheckInFlight = (async () => {
            try {
                const session = await obterSessao(controller.signal);
                if (validationVersion !== requestVersion || loggingOut) return;
                if (!session.authenticated) {
                    encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
                    return;
                }
                agendarExpiracao(session);
            } catch {
                if (validationVersion !== requestVersion || loggingOut) return;
                mostrarErro("Não foi possível revalidar sua sessão. Recarregue o painel para continuar.");
            } finally {
                sessionCheckInFlight = null;
            }
        })();
        return sessionCheckInFlight;
    }

    async function carregarDashboard() {
        if (loading || loggingOut) return;
        loading = true;
        const currentVersion = ++requestVersion;
        activeController?.abort();
        const controller = new AbortController();
        activeController = controller;
        window.clearTimeout(expirationTimer);
        limparDadosProtegidos();
        esconderEstados();
        elements.loading.hidden = false;
        elements.sessionStatus.textContent = "Verificando sessão administrativa…";
        elements.metricGrid?.setAttribute("aria-busy", "true");

        try {
            const session = await obterSessao(controller.signal);
            if (currentVersion !== requestVersion) return;
            if (!session.authenticated) {
                mostrarNaoAutenticado();
                return;
            }

            const data = await obterIndicadores(controller.signal);
            if (currentVersion !== requestVersion) return;
            if (data.expired) {
                encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
                return;
            }

            // Confirma o estado depois da consulta e usa a expiração mais recente
            // devolvida pelo servidor para limpar a tela sem polling que renove a sessão.
            const currentSession = await obterSessao(controller.signal);
            if (currentVersion !== requestVersion) return;
            if (!currentSession.authenticated) {
                encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
                return;
            }

            esconderEstados();
            renderizarIndicadores(data.result);
            agendarExpiracao(currentSession);
        } catch (error) {
            if (error?.name === "AbortError" || currentVersion !== requestVersion) return;
            const message = error?.message === "metrics-unavailable"
                ? "O banco não disponibilizou os indicadores. Tente novamente."
                : "Verifique a conexão e tente novamente.";
            mostrarErro(message);
        } finally {
            if (currentVersion === requestVersion) {
                loading = false;
                activeController = null;
                elements.loading.hidden = true;
                elements.metricGrid?.setAttribute("aria-busy", "false");
            }
        }
    }

    async function obterCsrf(signal) {
        const resposta = await fetch(`${API_URL}/usuario/csrf`, {
            method: "GET",
            credentials: "include",
            cache: "no-store",
            headers: { Accept: "application/json" },
            signal
        });
        if (!resposta.ok) throw new Error("csrf-unavailable");
        const csrf = await resposta.json();
        if (!csrf?.headerName || !csrf?.token) throw new Error("invalid-csrf");
        return csrf;
    }

    async function encerrarSessaoAdmin() {
        if (loggingOut) return;
        loggingOut = true;
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        elements.logout.disabled = true;
        elements.sessionStatus.textContent = "Encerrando sessão administrativa…";
        const controller = new AbortController();

        try {
            const csrf = await obterCsrf(controller.signal);
            const resposta = await fetch(`${API_URL}/admin/auth/logout`, {
                method: "POST",
                credentials: "include",
                cache: "no-store",
                headers: {
                    Accept: "application/json",
                    [csrf.headerName]: csrf.token
                },
                signal: controller.signal
            });

            if (resposta.status === 204) {
                mostrarNaoAutenticado();
                elements.sessionStatus.textContent = "Sessão administrativa encerrada";
                return;
            }

            if (resposta.status === 401 || resposta.status === 403) {
                const session = await obterSessao(controller.signal);
                if (!session.authenticated) {
                    encerrarComSessaoExpirada("A sessão administrativa não está mais ativa.");
                } else {
                    mostrarErro("O logout não foi confirmado; sua sessão administrativa continua ativa.");
                }
                return;
            }
            throw new Error("logout-failed");
        } catch (error) {
            if (error?.name === "AbortError") return;
            try {
                const session = await obterSessao(controller.signal);
                if (!session.authenticated) {
                    encerrarComSessaoExpirada("A sessão administrativa foi encerrada.");
                } else {
                    mostrarErro("Não foi possível confirmar o logout; sua sessão administrativa continua ativa.");
                }
            } catch {
                mostrarErro("Não foi possível confirmar o encerramento da sessão. Tente novamente.");
            }
        } finally {
            loggingOut = false;
            elements.logout.disabled = false;
        }
    }

    function abrirLoginAdmin() {
        window.dispatchEvent(new Event("porto-ajuda:abrir-admin-login"));
    }

    elements.retry.addEventListener("click", carregarDashboard);
    elements.refresh.addEventListener("click", carregarDashboard);
    elements.logout.addEventListener("click", encerrarSessaoAdmin);
    document.querySelectorAll("[data-abrir-login-admin]").forEach(button => {
        button.addEventListener("click", abrirLoginAdmin);
    });
    document.getElementById("abrir-login-admin").addEventListener("click", abrirLoginAdmin);

    elements.menuToggle.addEventListener("click", () => {
        const aberto = elements.menu.classList.toggle("is-open");
        elements.menuToggle.setAttribute("aria-expanded", String(aberto));
        elements.menuToggle.setAttribute("aria-label", aberto ? "Fechar navegação" : "Abrir navegação");
    });
    elements.menu.querySelectorAll(".admin-nav__link").forEach(link => {
        link.addEventListener("click", () => {
            elements.menu.classList.remove("is-open");
            elements.menuToggle.setAttribute("aria-expanded", "false");
            elements.menuToggle.setAttribute("aria-label", "Abrir navegação");
            elements.menu.querySelectorAll(".admin-nav__link").forEach(item => item.removeAttribute("aria-current"));
            link.setAttribute("aria-current", "page");
        });
    });

    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") void revalidarSessaoAoRetornar();
    });
    window.addEventListener("focus", () => void revalidarSessaoAoRetornar());

    document.addEventListener("keydown", evento => {
        if (evento.key === "Escape" && elements.menu.classList.contains("is-open")) {
            elements.menu.classList.remove("is-open");
            elements.menuToggle.setAttribute("aria-expanded", "false");
            elements.menuToggle.setAttribute("aria-label", "Abrir navegação");
            elements.menuToggle.focus();
        }
    });

    carregarDashboard();
})();
