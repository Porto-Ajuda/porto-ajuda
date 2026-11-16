import { getAPI_URL } from "./modoBack.js";
import {
    AdminRequestError,
    adminPageFromHash,
    clearAdminAuthorizationState,
    clearProtectedDashboard,
    createAdminAuthorizationStore,
    fetchAdminCapabilities,
    fetchAdminDashboard,
    fetchAdminSession,
    renderPermissionNavigation,
    renderAdminModuleFailure,
    showAdminDialog
} from "./admin-rbac.js";
import { initializeAdminAssignments } from "./admin-assignments.js";
import { initializeAdminVerification } from "./admin-verification.js";
import { initializeAdminSupport } from "./admin-support.js";

(() => {
    "use strict";

    const API_URL = getAPI_URL();
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
        forbidden: document.getElementById("estado-acesso-negado"),
        forbiddenMessage: document.getElementById("mensagem-acesso-negado"),
        forbiddenRetry: document.getElementById("tentar-novamente-acesso-admin"),
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
        menuToggle: document.getElementById("alternar-menu-admin"),
        navigation: document.getElementById("navegacao-admin"),
        profile: document.getElementById("perfil-admin"),
        profileName: document.getElementById("nome-perfil-admin"),
        overviewPage: document.getElementById("visao-geral"),
        assignmentsPage: document.getElementById("administradores-admin"),
        reportsPage: document.getElementById("denuncias-admin"),
        entitiesPage: document.getElementById("entidades-admin"),
        appealsPage: document.getElementById("recursos-admin"),
        verificationPage: document.getElementById("verificacao-representantes-admin"),
        supportPage: document.getElementById("atendimentos-admin"),
        reportStatus: document.getElementById("status-denuncias"),
        reportList: document.getElementById("lista-denuncias"),
        reportDetail: document.getElementById("detalhe-denuncia-admin"),
        reportFilter: document.getElementById("filtro-tipo-denuncia"),
        reportState: document.getElementById("filtro-estado-denuncia"),
        reportPrevious: document.getElementById("denuncias-anterior"),
        reportNext: document.getElementById("denuncias-proxima"),
        reportPageLabel: document.getElementById("denuncias-pagina"),
        entityCategory: document.getElementById("filtro-entidades-admin"),
        entitySearch: document.getElementById("busca-entidades-admin"),
        entityStatus: document.getElementById("status-entidades-admin"),
        entityList: document.getElementById("lista-entidades-admin"),
        entityPrevious: document.getElementById("entidades-anterior"),
        entityNext: document.getElementById("entidades-proxima"),
        entityPage: document.getElementById("entidades-pagina"),
        appealStatus: document.getElementById("status-recursos-admin"),
        appealList: document.getElementById("lista-recursos-admin"),
        appealFilter: document.getElementById("filtro-recursos-admin"),
        appealPrevious: document.getElementById("recursos-anterior"),
        appealNext: document.getElementById("recursos-proxima"),
        appealPage: document.getElementById("recursos-pagina"),
        breadcrumbTitle: document.querySelector(".admin-breadcrumb strong")
    };

    const authorization = createAdminAuthorizationStore();
    const profileLabels = Object.freeze({
        SUPER_ADMIN: "Superadministrador",
        PLATFORM_ADMIN: "Administrador da plataforma",
        MODERATOR: "Moderador",
        AUDITOR: "Auditor"
    });
    const implementedNavigation = Object.freeze([
        { id: "nav-visao-geral", permission: "DASHBOARD_READ" },
        { id: "nav-indicadores", permission: "DASHBOARD_READ" },
        { id: "nav-atividade", permission: "DASHBOARD_READ" },
        { id: "nav-administradores", permission: "ADMIN_MANAGE" },
        { id: "nav-denuncias", permission: "REPORT_READ" },
        { id: "nav-entidades", permission: "ENTITY_READ" },
        { id: "nav-recursos", permission: "APPEAL_READ" },
        { id: "nav-verificacao-representantes", permission: "NGO_VERIFICATION_READ" },
        { id: "nav-atendimentos", permission: "SUPPORT_READ" }
    ]);

    let requestVersion = 0;
    let activeController = null;
    let expirationTimer = null;
    let sessionCheckInFlight = null;
    let lastSessionCheckAt = 0;
    let loading = false;
    let loggingOut = false;
    let sessionValidated = false;
    let activePage = null;
    let assignmentPanel = null;
    let verificationFeatureEnabled = false;
    let verificationPanel = null;
    let supportPanel = null;

    function limparDadosProtegidos({ keepLogout = false } = {}) {
        clearProtectedDashboard({
            metrics: [...document.querySelectorAll("[data-metric]")],
            activityList: elements.activityList,
            activityState: elements.activityState,
            generatedAt: elements.generatedAt,
            content: elements.content,
            refresh: elements.refresh,
            logout: elements.logout
        }, { keepLogout });
        elements.navigation.hidden = true;
        elements.overviewPage.hidden = true;
        elements.assignmentsPage.hidden = true;
        elements.reportsPage.hidden = true;
        elements.entitiesPage.hidden = true;
        elements.appealsPage.hidden = true;
        elements.verificationPage.hidden = true;
        elements.supportPage.hidden = true;
        elements.reportList.replaceChildren();
        elements.reportDetail.replaceChildren();
        elements.reportDetail.hidden = true;
        elements.entityList.replaceChildren();
        elements.appealList.replaceChildren();
        assignmentPanel?.clear();
        verificationPanel?.clear();
        supportPanel?.clear();
    }

    function limparAutorizacao() {
        clearAdminAuthorizationState(authorization, {
            navigation: elements.navigation,
            profile: elements.profile,
            profileName: elements.profileName
        });
        sessionValidated = false;
    }

    function esconderEstados() {
        elements.loading.hidden = true;
        elements.unauthenticated.hidden = true;
        elements.expired.hidden = true;
        elements.forbidden.hidden = true;
        elements.error.hidden = true;
    }

    function paginaSolicitada() {
        return adminPageFromHash(window.location.hash);
    }

    function atualizarNavegacaoAtiva(page = activePage) {
        const hash = window.location.hash;
        const activeLinkId = page === "administrators" ? "nav-administradores"
            : page === "reports" ? "nav-denuncias"
            : page === "entities" ? "nav-entidades"
            : page === "appeals" ? "nav-recursos"
            : page === "verification" ? "nav-verificacao-representantes"
            : page === "support" ? "nav-atendimentos"
            : hash === "#indicadores" ? "nav-indicadores"
                : hash === "#atividade" ? "nav-atividade" : "nav-visao-geral";
        elements.menu.querySelectorAll(".admin-nav__link").forEach(link => {
            if (link.id === activeLinkId) link.setAttribute("aria-current", "page");
            else link.removeAttribute("aria-current");
        });
    }

    function mostrarPagina(page) {
        activePage = page;
        elements.overviewPage.hidden = page !== "dashboard";
        elements.assignmentsPage.hidden = page !== "administrators";
        elements.reportsPage.hidden = page !== "reports";
        elements.entitiesPage.hidden = page !== "entities";
        elements.appealsPage.hidden = page !== "appeals";
        elements.verificationPage.hidden = page !== "verification";
        elements.supportPage.hidden = page !== "support";
        elements.breadcrumbTitle.textContent = page === "administrators" ? "Administradores"
            : page === "reports" ? "Denúncias" : page === "entities" ? "Entidades"
                : page === "appeals" ? "Recursos" : "Visão geral";
        if (page === "verification") elements.breadcrumbTitle.textContent = "Verificação de ONGs";
        if (page === "support") elements.breadcrumbTitle.textContent = "Atendimentos";
        atualizarNavegacaoAtiva(page);
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
        limparAutorizacao();
        limparDadosProtegidos();
        esconderEstados();
        mostrarPagina("dashboard");
        elements.expired.querySelector("h2").textContent = mensagem;
        elements.expired.hidden = false;
        elements.sessionStatus.textContent = "Sessão administrativa inativa";
    }

    function mostrarErro(mensagem, { keepSession = sessionValidated } = {}) {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        clearProtectedDashboard({
            metrics: [...document.querySelectorAll("[data-metric]")],
            activityList: elements.activityList,
            activityState: elements.activityState,
            generatedAt: elements.generatedAt,
            content: elements.content,
            refresh: elements.refresh,
            logout: elements.logout
        }, { keepLogout: keepSession });
        elements.reportList.replaceChildren();
        elements.reportDetail.replaceChildren();
        elements.reportDetail.hidden = true;
        elements.entityList.replaceChildren();
        elements.appealList.replaceChildren();
        assignmentPanel?.clear();
        verificationPanel?.clear();
        elements.navigation.hidden = !keepSession || !authorization.current;
        elements.profile.hidden = !keepSession || !authorization.current;
        elements.logout.hidden = !keepSession;
        esconderEstados();
        mostrarPagina("dashboard");
        elements.errorMessage.textContent = mensagem;
        elements.error.hidden = false;
        elements.sessionStatus.textContent = keepSession
            ? "Dados protegidos ocultos; sessão preservada"
            : "Não foi possível validar a sessão";
    }

    function mostrarFalhaModulo(page, mensagem) {
        if (!renderAdminModuleFailure(document, page, mensagem)) return false;
        esconderEstados();
        mostrarPagina(page);
        elements.loading.hidden = true;
        elements.logout.hidden = !sessionValidated;
        elements.sessionStatus.textContent = sessionValidated
            ? "Sessão administrativa ativa"
            : "Não foi possível validar a sessão";
        elements.metricGrid?.setAttribute("aria-busy", "false");
        return true;
    }

    function mostrarNaoAutenticado() {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparAutorizacao();
        limparDadosProtegidos();
        esconderEstados();
        mostrarPagina("dashboard");
        elements.unauthenticated.hidden = false;
        elements.sessionStatus.textContent = "Autenticação necessária";
    }

    function mostrarAcessoNegado(mensagem = "Seu perfil administrativo não tem permissão para consultar esta área.") {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        // Keep an explicit self-logout control available even when the session
        // endpoint itself returned 403; never convert that response into logout.
        limparDadosProtegidos({ keepLogout: true });
        elements.logout.hidden = false;
        esconderEstados();
        mostrarPagina("dashboard");
        elements.forbiddenMessage.textContent = mensagem;
        elements.forbidden.hidden = false;
        elements.sessionStatus.textContent = sessionValidated
            ? "Sessão ativa; acesso não autorizado"
            : "Acesso administrativo não autorizado";
    }

    function renderizarContextoAutorizacao() {
        const context = authorization.current;
        if (!context) {
            elements.profile.hidden = true;
            elements.navigation.hidden = true;
            return;
        }

        elements.profileName.textContent = profileLabels[context.profile];
        elements.profile.hidden = false;

        const navigationEntries = implementedNavigation.map(entry => ({
            ...entry,
            element: document.getElementById(entry.id)
        })).filter(entry => entry.id !== "nav-verificacao-representantes" || verificationFeatureEnabled);
        renderPermissionNavigation(elements.navigation, navigationEntries, authorization);
    }

    assignmentPanel = initializeAdminAssignments({
        apiUrl: API_URL,
        authorization,
        onUnauthorized: () => encerrarComSessaoExpirada("Sua sessão administrativa expirou ou foi revogada.")
    });

    verificationPanel = initializeAdminVerification({
        apiUrl: API_URL,
        onUnauthorized: () => encerrarComSessaoExpirada("Sua sessão administrativa expirou ou foi revogada.")
    });

    supportPanel = initializeAdminSupport({ apiUrl: API_URL, authorization,
        onUnauthorized: () => encerrarComSessaoExpirada("Sua sessão administrativa expirou ou foi revogada.") });

    async function obterSessao(signal) {
        return fetchAdminSession(API_URL, fetch, signal);
    }

    async function obterIndicadores(signal) {
        return fetchAdminDashboard(API_URL, fetch, signal);
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
        const protectedPageVisible = activePage === "administrators"
            ? !elements.assignmentsPage.hidden
            : activePage === "reports" ? !elements.reportsPage.hidden
                : activePage === "entities" ? !elements.entitiesPage.hidden
                    : activePage === "appeals" ? !elements.appealsPage.hidden : !elements.content.hidden;
        if (!protectedPageVisible || loading || loggingOut || document.hidden) return;
        if (sessionCheckInFlight) return sessionCheckInFlight;
        if (performance.now() - lastSessionCheckAt < 3000) return;
        lastSessionCheckAt = performance.now();

        const validationVersion = requestVersion;
        const controller = new AbortController();
        sessionCheckInFlight = (async () => {
            try {
                const session = await obterSessao(controller.signal);
                if (validationVersion !== requestVersion || loggingOut) return;
                sessionValidated = true;
                agendarExpiracao(session);
            } catch (error) {
                if (validationVersion !== requestVersion || loggingOut) return;
                if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                    encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
                } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                    mostrarAcessoNegado("Sua sessão não tem autorização para esta área. A sessão não foi encerrada.");
                } else {
                    mostrarErro("Não foi possível revalidar a sessão. Verifique a conexão e tente novamente.");
                }
            } finally {
                sessionCheckInFlight = null;
            }
        })();
        return sessionCheckInFlight;
    }

    async function carregarDashboard() {
        if (loading || loggingOut) return;
        const requestedPage = paginaSolicitada();
        const sessionWasValidated = sessionValidated;
        loading = true;
        sessionValidated = false;
        activePage = requestedPage;
        const currentVersion = ++requestVersion;
        activeController?.abort();
        const controller = new AbortController();
        activeController = controller;
        window.clearTimeout(expirationTimer);
        limparDadosProtegidos();
        esconderEstados();
        elements.overviewPage.hidden = requestedPage !== "dashboard";
        elements.assignmentsPage.hidden = true;
        elements.reportsPage.hidden = true;
        elements.loading.hidden = false;
        elements.profile.hidden = true;
        elements.navigation.hidden = true;
        elements.sessionStatus.textContent = "Verificando sessão administrativa…";
        elements.metricGrid?.setAttribute("aria-busy", "true");

        try {
            const session = await obterSessao(controller.signal);
            if (currentVersion !== requestVersion) return;
            sessionValidated = true;
            agendarExpiracao(session);

            let verificationConfigUnavailable = false;
            try {
                const featureResponse = await fetch(`${API_URL}/osc/verificacao/config`, {
                    credentials: "include", cache: "no-store", signal: controller.signal,
                    headers: { Accept: "application/json" }
                });
                if (!featureResponse.ok) {
                    verificationFeatureEnabled = false;
                    verificationConfigUnavailable = true;
                } else {
                    verificationFeatureEnabled = (await featureResponse.json()).enabled === true;
                }
            } catch (error) {
                if (error?.name === "AbortError") return;
                verificationFeatureEnabled = false;
                verificationConfigUnavailable = true;
            }

            if (!authorization.current) {
                authorization.set(await fetchAdminCapabilities(API_URL, fetch, controller.signal));
            }
            if (currentVersion !== requestVersion) return;
            renderizarContextoAutorizacao();
            if (requestedPage === "administrators") {
                if (!authorization.hasPermission("ADMIN_MANAGE")) {
                    mostrarFalhaModulo("administrators", "Seu perfil administrativo não pode gerenciar atribuições.");
                    return;
                }
                mostrarPagina("administrators");
                elements.loading.hidden = true;
                const assignmentSession = await assignmentPanel.load(0);
                if (currentVersion === requestVersion && assignmentSession) {
                    sessionValidated = true;
                    agendarExpiracao(assignmentSession);
                }
                return;
            }

            if (requestedPage === "reports") {
                if (!authorization.hasPermission("REPORT_READ")) {
                    mostrarFalhaModulo("reports", "Seu perfil administrativo não pode consultar denúncias.");
                    return;
                }
                mostrarPagina("reports");
                elements.loading.hidden = true;
                await carregarCapacidadePontos(controller.signal);
                await carregarDenuncias(0, controller.signal);
                return;
            }

            if (requestedPage === "entities") {
                if (!authorization.hasPermission("ENTITY_READ")) {
                    mostrarFalhaModulo("entities", "Seu perfil administrativo não pode consultar estas entidades.");
                    return;
                }
                mostrarPagina("entities"); elements.loading.hidden = true;
                await carregarCapacidadePontos(controller.signal);
                await carregarEntidades(0); return;
            }

            if (requestedPage === "appeals") {
                if (!authorization.hasPermission("APPEAL_READ")) {
                    mostrarFalhaModulo("appeals", "Seu perfil administrativo não pode consultar recursos.");
                    return;
                }
                mostrarPagina("appeals"); elements.loading.hidden = true; await carregarRecursos(0); return;
            }

            if (requestedPage === "verification") {
                mostrarPagina("verification");
                elements.loading.hidden = true;
                if (verificationConfigUnavailable) {
                    mostrarFalhaModulo("verification", "Não foi possível verificar a disponibilidade deste módulo. Tente novamente.");
                    return;
                }
                if (!verificationFeatureEnabled) {
                    mostrarFalhaModulo("verification", "A verificação de representantes está desabilitada nesta instância.");
                    return;
                }
                if (!authorization.hasPermission("NGO_VERIFICATION_READ")) {
                    mostrarFalhaModulo("verification", "Seu perfil administrativo não pode consultar verificações de representação.");
                    return;
                }
                await verificationPanel.load(0);
                return;
            }

            if (requestedPage === "support") {
                if (!authorization.hasPermission("SUPPORT_READ")) {
                    mostrarFalhaModulo("support", "Seu perfil administrativo não pode consultar atendimentos.");
                    return;
                }
                mostrarPagina("support");
                elements.loading.hidden = true;
                await supportPanel.load(0);
                sessionValidated = true;
                return;
            }

            mostrarPagina("dashboard");
            if (!authorization.hasPermission("DASHBOARD_READ")) {
                mostrarAcessoNegado("Seu perfil administrativo não tem permissão para consultar o dashboard.");
                return;
            }

            const overview = await obterIndicadores(controller.signal);
            if (currentVersion !== requestVersion) return;

            // Confirma o estado depois da consulta e usa a expiração mais recente
            // devolvida pelo servidor para limpar a tela sem polling que renove a sessão.
            const currentSession = await obterSessao(controller.signal);
            if (currentVersion !== requestVersion) return;
            sessionValidated = true;

            esconderEstados();
            renderizarIndicadores(overview);
            agendarExpiracao(currentSession);
        } catch (error) {
            if (error?.name === "AbortError" || currentVersion !== requestVersion) return;
            if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                if (sessionValidated || sessionWasValidated) {
                    encerrarComSessaoExpirada("Sua sessão administrativa expirou ou foi revogada.");
                } else {
                    mostrarNaoAutenticado();
                }
            } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                sessionValidated = sessionValidated || sessionWasValidated;
                if (requestedPage !== "dashboard") {
                    mostrarFalhaModulo(requestedPage, "Acesso não autorizado para este módulo. Os dados protegidos não foram exibidos.");
                } else if (sessionValidated && authorization.current) {
                    mostrarErro("Seu perfil administrativo não pode consultar os indicadores do dashboard.", { keepSession: true });
                } else {
                    mostrarAcessoNegado("A solicitação foi recusada por autorização. Os dados protegidos não foram exibidos.");
                }
            } else if (requestedPage !== "dashboard") {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarFalhaModulo(requestedPage, "Não foi possível carregar este módulo agora. Tente novamente.");
            } else if (error instanceof AdminRequestError && error.kind === "unavailable"
                    && error.status === 503) {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarErro("Os indicadores estão temporariamente indisponíveis. Tente novamente.");
            } else if (error instanceof AdminRequestError && error.kind === "network") {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarErro("Não foi possível conectar ao serviço administrativo. Verifique a rede e tente novamente.");
            } else {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarErro("Não foi possível carregar os dados administrativos. Tente novamente.");
            }
        } finally {
            if (currentVersion === requestVersion) {
                loading = false;
                activeController = null;
                elements.loading.hidden = true;
                elements.metricGrid?.setAttribute("aria-busy", "false");
            }
        }
    }

    elements.forbiddenRetry.addEventListener("click", carregarDashboard);

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

    let offsetDenuncias = 0;
    let pointsFeatureEnabled = false;
    let pointsFeatureLoaded = false;
    const tamanhoPaginaDenuncias = 25;
    async function carregarCapacidadePontos(signal){
        pointsFeatureLoaded=false;pointsFeatureEnabled=false;
        try{const response=await fetch(`${API_URL}/admin/moderation/capabilities`,{credentials:"include",cache:"no-store",signal,headers:{Accept:"application/json"}});if(response.status===401)return encerrarComSessaoExpirada();if(!response.ok)return;const result=await response.json();pointsFeatureEnabled=result.pointsEnabled===true;pointsFeatureLoaded=true;}catch(error){if(error?.name!=="AbortError")pointsFeatureLoaded=false;}
    }
    async function carregarDenuncias(offset = offsetDenuncias, signal) {
        offsetDenuncias = Math.max(0, offset);
        elements.reportStatus.textContent = "Carregando a fila…";
        elements.reportList.setAttribute("aria-busy", "true");
        const params = new URLSearchParams({ offset: String(offsetDenuncias), limit: String(tamanhoPaginaDenuncias) });
        if (elements.reportFilter.value) params.set("tipo", elements.reportFilter.value);
        params.set("estado", elements.reportState.value);
        try {
            const response = await fetch(`${API_URL}/admin/denuncias?${params}`, {
                credentials: "include", cache: "no-store", headers: { Accept: "application/json" }, signal
            });
            if (response.status === 401) return encerrarComSessaoExpirada();
            if (response.status === 403) return mostrarAcessoNegado("Seu perfil não pode consultar esta fila.");
            if (!response.ok) throw new Error("queue");
            const page = await response.json();
            if (!Array.isArray(page.itens)) throw new Error("queue-shape");
            elements.reportList.replaceChildren();
            elements.reportDetail.replaceChildren();
            elements.reportDetail.hidden = true;
            for (const item of page.itens) elements.reportList.append(criarCartaoDenuncia(item));
            elements.reportStatus.textContent = page.itens.length
                ? `${page.totalItens} alvo(s) nesta fila.`
                : "Não há alvos nesta fila.";
            elements.reportPageLabel.textContent = `Página ${Math.floor(offsetDenuncias / tamanhoPaginaDenuncias) + 1}`;
            elements.reportPrevious.disabled = offsetDenuncias === 0;
            elements.reportNext.disabled = page.itens.length < tamanhoPaginaDenuncias;
        } catch (error) {
            if (error?.name === "AbortError") return;
            elements.reportStatus.textContent = "Não foi possível carregar a fila. Tente novamente.";
        } finally {
            elements.reportList.setAttribute("aria-busy", "false");
        }
    }

    function criarCartaoDenuncia(item) {
        const card = document.createElement("article");
        card.className = "admin-report-card";
        const info = document.createElement("div");
        const title = document.createElement("h2");
        title.textContent = item.titulo || "Conteúdo indisponível";
        const type = document.createElement("p");
        type.textContent = item.tipo === "OSC" ? `ONG #${item.alvoId}` : `Publicação/voluntariado #${item.alvoId}`;
        const count = document.createElement("p");
        count.textContent = `${item.pendentes} pendente(s); ${item.qualificadasNaJanela} dentro da janela. ${item.protecaoAutomaticaAtiva ? "Ocultação preventiva ativa." : "Sem ocultação preventiva."}`;
        info.append(title, type, count);
        const actions = document.createElement("div");
        actions.className = "admin-report-card__actions";
        const inspect = document.createElement("button");
        inspect.className = "admin-secondary-button";
        inspect.type = "button";
        inspect.textContent = "Analisar";
        inspect.addEventListener("click", () => abrirDetalheDenuncia(item, inspect));
        actions.append(inspect);
        card.append(info, actions);
        return card;
    }

    async function abrirDetalheDenuncia(item, trigger, panelTarget = elements.reportDetail) {
        trigger.disabled = true;
        try {
            const response = await fetch(`${API_URL}/admin/denuncias/${encodeURIComponent(item.tipo)}/${encodeURIComponent(item.alvoId)}`, {
                credentials: "include", cache: "no-store", headers: { Accept: "application/json" }
            });
            if (response.status === 401) return encerrarComSessaoExpirada();
            if (response.status === 403) return mostrarAcessoNegado("Seu perfil não pode consultar os detalhes.");
            if (response.status === 404) { elements.reportStatus.textContent = "O conteúdo não está mais disponível; atualize a fila."; return; }
            if (!response.ok) throw new Error("detail");
            renderizarDetalheDenuncia(await response.json(), item, panelTarget);
        } catch {
            elements.reportStatus.textContent = "Não foi possível carregar os detalhes da denúncia.";
        } finally { trigger.disabled = false; }
    }

    const rotulosMotivoDenuncia = Object.freeze({
        FRAUD: "Fraude",
        MISLEADING: "Informação enganosa",
        INAPPROPRIATE: "Conteúdo inadequado",
        PRIVACY: "Exposição de dados pessoais",
        OTHER: "Outro"
    });
    const rotulosStatusDenuncia = Object.freeze({
        PENDING: "Pendente",
        DISMISSED: "Descartada",
        UPHELD: "Procedente"
    });
    const rotulosDecisaoDenuncia = Object.freeze({
        DISMISS: "Descartar denúncias",
        UPHOLD: "Manter den\u00fancia procedente",
        RESTORE: "Restaura\u00e7\u00e3o de visibilidade"
    });

    function renderizarDetalheDenuncia(detail, item, targetPanel = elements.reportDetail) {
        const panel = targetPanel;
        panel.replaceChildren();
        const title = document.createElement("h2"); title.textContent = detail.titulo || "Conteúdo indisponível";
        const text = document.createElement("p"); text.className = "admin-report-detail__content"; text.textContent = detail.conteudo || "Sem descrição disponível.";
        const heading = document.createElement("h3"); heading.textContent = "Denúncias";
        const list = document.createElement("ul"); list.className = "admin-report-reasons";
        for (const report of detail.denuncias || []) {
            const row = document.createElement("li");
            const reason = document.createElement("strong"); reason.textContent = rotulosMotivoDenuncia[report.motivo] || "Outro motivo";
            const explanation = document.createElement("p"); explanation.textContent = report.justificativa || "Sem detalhes adicionais.";
            const reportStatus = document.createElement("span"); reportStatus.textContent = ` · ${rotulosStatusDenuncia[report.status] || "Status não reconhecido"}`;
            const date = document.createElement("time"); date.dateTime = report.registradaEm || "";
            date.textContent = report.registradaEm ? formatarDataHora.format(Date.parse(report.registradaEm)) : "";
            reason.append(reportStatus); row.append(reason, explanation, date); list.append(row);
        }
        panel.append(title, text, heading, list);
        if (detail.historicoRevisao?.length) {
            const historyHeading = document.createElement("h3"); historyHeading.textContent = "Histórico de revisão";
            const historyList = document.createElement("ul"); historyList.className = "admin-report-reasons";
            for (const entry of detail.historicoRevisao) {
                const row = document.createElement("li");
                const label = document.createElement("strong"); label.textContent = `${rotulosDecisaoDenuncia[entry.decisao] || "Decisão não reconhecida"} · ${profileLabels[entry.perfil] || entry.perfil || "Perfil não informado"}`;
                const note = document.createElement("p"); note.textContent = entry.justificativa;
                const when = document.createElement("time"); when.dateTime = entry.ocorreuEm || "";
                when.textContent = entry.ocorreuEm ? formatarDataHora.format(Date.parse(entry.ocorreuEm)) : "";
                row.append(label, note, when); historyList.append(row);
            }
            panel.append(historyHeading, historyList);
        }
        const activeRestrictions = Array.isArray(detail.restricoesAtivas) ? detail.restricoesAtivas : [];
        if (activeRestrictions.length) {
            const restrictionHeading = document.createElement("h3");
            restrictionHeading.textContent = "Restri\u00e7\u00f5es de visibilidade ativas";
            const restrictionList = document.createElement("ul");
            restrictionList.className = "admin-report-restrictions";
            for (const block of activeRestrictions) {
                const row = document.createElement("li");
                const title = document.createElement("strong");
                title.textContent = block.origem === "AUTOMATIC_PROTECTION" ? "Oculta\u00e7\u00e3o autom\u00e1tica preventiva" : "Restri\u00e7\u00e3o administrativa de visibilidade";
                const reason = document.createElement("p");
                reason.textContent = block.justificativa || "Sem justificativa dispon\u00edvel.";
                const created = document.createElement("time");
                created.dateTime = block.criadoEm || "";
                created.textContent = block.criadoEm ? formatarDataHora.format(Date.parse(block.criadoEm)) : "";
                row.append(title, reason, created);
                if (authorization.hasPermission("CONTENT_REACTIVATE")) {
                    const restore = document.createElement("button");
                    restore.type = "button";
                    restore.className = "admin-secondary-button";
                    restore.textContent = block.origem === "AUTOMATIC_PROTECTION" ? "Restaurar apenas esta prote\u00e7\u00e3o autom\u00e1tica" : "Restaurar apenas esta restri\u00e7\u00e3o administrativa";
                    restore.addEventListener("click", () => confirmarRestauracaoRestricao(item, block, restore, panel));
                    row.append(restore);
                }
                restrictionList.append(row);
            }
            panel.append(restrictionHeading, restrictionList);
        }
        if (authorization.hasPermission("REPORT_REVIEW") && authorization.hasPermission("CONTENT_DEACTIVATE")
                && item.pendentes > 0 && detail.disponivel && activeRestrictions.length === 0) {
            const hide = document.createElement("button");
            hide.type = "button";
            hide.className = "admin-secondary-button";
            hide.textContent = "Ocultar para an\u00e1lise enquanto pendente";
            hide.addEventListener("click", () => confirmarOcultacaoPreventiva(item, hide, panel));
            panel.append(hide);
        }
        if (authorization.hasPermission("REPORT_REVIEW") && item.pendentes > 0) {
            const actions = document.createElement("div"); actions.className = "admin-report-card__actions";
            for (const [decision, label] of [["DISMISS", "Descartar den\u00fancias"], ...(authorization.hasPermission("REPORT_RESOLVE") ? [["UPHOLD", "Manter den\u00fancia procedente"]] : [])]) {
                const button = document.createElement("button"); button.type = "button";
                button.className = decision === "UPHOLD" ? "admin-primary-button" : "admin-secondary-button";
                button.textContent = label;
                button.addEventListener("click", () => {
                    if(decision==="UPHOLD"&&!pointsFeatureLoaded){elements.reportStatus.textContent="Não foi possível confirmar se a pontuação está habilitada. Atualize a fila e tente novamente.";return;}
                    confirmarDecisaoDenuncia(item, decision, button);
                });
                actions.append(button);
            }
            panel.append(actions);
        }
        panel.hidden = false;
        panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function confirmarDecisaoDenuncia(item, decision, trigger) {
        if (decision !== "DISMISS" && decision !== "UPHOLD") return;
        if (decision === "UPHOLD" && !pointsFeatureLoaded) {
            elements.reportStatus.textContent = "N\u00e3o foi poss\u00edvel confirmar se a pontua\u00e7\u00e3o est\u00e1 habilitada. Atualize e tente novamente.";
            return;
        }
        const dialog = document.createElement("dialog");
        dialog.className = "admin-dialog admin-report-decision-dialog";
        dialog.setAttribute("aria-labelledby", "titulo-decisao-denuncia");
        const form = document.createElement("form");
        form.className = "admin-dialog__card admin-report-decision-dialog__card";
        const title = document.createElement("h2");
        title.id = "titulo-decisao-denuncia";
        title.textContent = decision === "UPHOLD" ? "Manter den\u00fancia procedente" : "Descartar den\u00fancias";
        const description = document.createElement("p");
        description.className = "admin-dialog__description";
        description.textContent = decision === "UPHOLD"
            ? "A pontua\u00e7\u00e3o e a visibilidade s\u00e3o decis\u00f5es separadas. Escolha explicitamente se o conte\u00fado tamb\u00e9m deve ficar oculto."
            : "As den\u00fancias ser\u00e3o encerradas. Restri\u00e7\u00f5es de visibilidade existentes ser\u00e3o preservadas.";
        const fields = [];
        let points = null;
        let visibility = null;
        if (decision === "UPHOLD" && pointsFeatureEnabled) {
            const field = document.createElement("div");
            field.className = "admin-dialog__field";
            const label = document.createElement("label");
            label.htmlFor = "pontos-denuncia-" + item.alvoId;
            label.textContent = "Pontos da infra\u00e7\u00e3o";
            points = document.createElement("select");
            points.id = label.htmlFor;
            points.required = true;
            points.add(new Option("Selecione de 1 a 3 pontos", ""));
            for (const value of [1, 2, 3]) points.add(new Option(value + (value > 1 ? " pontos" : " ponto"), String(value)));
            field.append(label, points);
            fields.push(field);
        }
        if (decision === "UPHOLD") {
            const option = document.createElement("div");
            option.className = "admin-report-visibility-option";
            visibility = document.createElement("input");
            visibility.type = "checkbox";
            visibility.id = "restringir-visibilidade-denuncia-" + item.alvoId;
            const label = document.createElement("label");
            label.htmlFor = visibility.id;
            label.textContent = "Aplicar tamb\u00e9m restri\u00e7\u00e3o de visibilidade";
            const help = document.createElement("small");
            help.textContent = "Sem marcar, pontos n\u00e3o ocultam o conte\u00fado. Oculta\u00e7\u00f5es autom\u00e1ticas ou administrativas existentes n\u00e3o ser\u00e3o removidas.";
            option.append(visibility, label, help);
            fields.push(option);
        }
        const reasonField = document.createElement("div");
        reasonField.className = "admin-dialog__field";
        const label = document.createElement("label");
        label.htmlFor = "justificativa-denuncia-" + item.alvoId;
        label.textContent = "Justificativa obrigat\u00f3ria";
        const textarea = document.createElement("textarea");
        textarea.id = label.htmlFor;
        textarea.required = true;
        textarea.minLength = 8;
        textarea.maxLength = 2000;
        textarea.rows = 5;
        textarea.placeholder = decision === "UPHOLD"
            ? "Explique a decis\u00e3o e, se selecionada, a restri\u00e7\u00e3o de visibilidade."
            : "Explique por que as den\u00fancias devem ser descartadas.";
        reasonField.append(label, textarea);
        const status = document.createElement("p");
        status.setAttribute("role", "alert");
        status.setAttribute("aria-live", "polite");
        const actions = document.createElement("div");
        actions.className = "admin-dialog__actions";
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "admin-secondary-button";
        cancel.textContent = "Voltar";
        const submit = document.createElement("button");
        submit.type = "submit";
        submit.className = "admin-primary-button";
        submit.textContent = decision === "UPHOLD" ? "Registrar decis\u00e3o" : "Confirmar descarte";
        actions.append(cancel, submit);
        form.append(title, description, ...fields, reasonField, status, actions);
        dialog.append(form);
        document.body.append(dialog);
        cancel.addEventListener("click", () => dialog.close());
        dialog.addEventListener("close", () => { dialog.remove(); if (trigger?.isConnected) trigger.focus(); });
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (submit.disabled || !form.reportValidity()) return;
            const justification = textarea.value.trim();
            if (justification.length < 8) {
                status.textContent = "A justificativa deve ter ao menos 8 caracteres.";
                textarea.setAttribute("aria-invalid", "true");
                textarea.focus();
                return;
            }
            submit.disabled = true;
            const originalText = submit.textContent;
            submit.textContent = "Salvando\u2026";
            status.textContent = "Salvando a decis\u00e3o\u2026";
            try {
                const csrf = await obterCsrf();
                const response = await fetch(API_URL + "/admin/denuncias/" + encodeURIComponent(item.tipo) + "/" + encodeURIComponent(item.alvoId) + "/decisao", {
                    method: "POST", credentials: "include", cache: "no-store",
                    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                    body: JSON.stringify({ decisao: decision, justificativa: justification,
                        ...(points ? { pontos: Number(points.value) } : {}),
                        restringirVisibilidade: visibility?.checked === true })
                });
                if (response.status === 401) { dialog.close(); return encerrarComSessaoExpirada(); }
                if (response.status === 403) throw new Error("Seu perfil n\u00e3o tem permiss\u00e3o para esta decis\u00e3o.");
                if (response.status === 409) throw new Error("A fila ou as restri\u00e7\u00f5es mudaram. Atualize e revise novamente.");
                if (!response.ok) throw new Error("N\u00e3o foi poss\u00edvel salvar a decis\u00e3o.");
                dialog.close();
                if (activePage === "entities") await carregarEntidades(entityPageNumber);
                else await carregarDenuncias(offsetDenuncias);
            } catch (error) {
                status.textContent = error?.message || "Falha ao salvar a decis\u00e3o.";
            } finally {
                submit.disabled = false;
                submit.textContent = originalText;
            }
        });
        showAdminDialog(dialog);
        if (points) points.focus(); else textarea.focus();
    }

    async function enviarRestricaoVisibilidade(url, justification, deniedMessage) {
        const csrf = await obterCsrf();
        const response = await fetch(url, { method: "POST", credentials: "include", cache: "no-store",
            headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
            body: JSON.stringify({ justificativa: justification }) });
        if (response.status === 401) { encerrarComSessaoExpirada(); throw new Error("Sua sess\u00e3o administrativa expirou."); }
        if (response.status === 403) throw new Error(deniedMessage);
        if (response.status === 404) throw new Error("A restri\u00e7\u00e3o n\u00e3o est\u00e1 mais ativa para este conte\u00fado.");
        if (response.status === 409) throw new Error("O estado das den\u00fancias ou da restri\u00e7\u00e3o mudou. Atualize a fila.");
        if (!response.ok) throw new Error("N\u00e3o foi poss\u00edvel atualizar a visibilidade.");
    }

    function abrirDialogoJustificativaVisibilidade(config) {
        const dialog = document.createElement("dialog");
        dialog.className = "admin-dialog admin-visibility-action-dialog";
        dialog.setAttribute("aria-labelledby", "titulo-acao-visibilidade");
        const form = document.createElement("form");
        form.className = "admin-dialog__card admin-visibility-action-dialog__card";
        const title = document.createElement("h2");
        title.id = "titulo-acao-visibilidade";
        title.textContent = config.title;
        const description = document.createElement("p");
        description.className = "admin-dialog__description";
        description.textContent = config.description;
        const field = document.createElement("div");
        field.className = "admin-dialog__field";
        const label = document.createElement("label");
        label.htmlFor = "justificativa-visibilidade-" + crypto.randomUUID();
        label.textContent = "Justificativa obrigat\u00f3ria";
        const textarea = document.createElement("textarea");
        textarea.id = label.htmlFor;
        textarea.required = true;
        textarea.minLength = 8;
        textarea.maxLength = 2000;
        textarea.rows = 5;
        textarea.placeholder = config.placeholder;
        field.append(label, textarea);
        const status = document.createElement("p");
        status.setAttribute("role", "alert");
        status.setAttribute("aria-live", "polite");
        const actions = document.createElement("div");
        actions.className = "admin-dialog__actions";
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "admin-secondary-button";
        cancel.textContent = "Voltar";
        const submit = document.createElement("button");
        submit.type = "submit";
        submit.className = "admin-primary-button";
        submit.textContent = config.submitText;
        actions.append(cancel, submit);
        form.append(title, description, field, status, actions);
        dialog.append(form);
        document.body.append(dialog);
        cancel.addEventListener("click", () => dialog.close());
        dialog.addEventListener("close", () => { dialog.remove(); if (config.trigger?.isConnected) config.trigger.focus(); });
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (submit.disabled || !form.reportValidity()) return;
            const reason = textarea.value.trim();
            if (reason.length < 8) {
                status.textContent = "A justificativa deve ter ao menos 8 caracteres.";
                textarea.setAttribute("aria-invalid", "true");
                textarea.focus();
                return;
            }
            submit.disabled = true;
            submit.textContent = "Salvando\u2026";
            status.textContent = "Salvando\u2026";
            try {
                await config.submit(reason);
                dialog.close();
                await config.afterSuccess();
            } catch (error) {
                status.textContent = error?.message || "N\u00e3o foi poss\u00edvel concluir a opera\u00e7\u00e3o.";
                submit.disabled = false;
                submit.textContent = config.submitText;
            }
        });
        showAdminDialog(dialog);
        textarea.focus();
    }

    function atualizarAposAcaoVisibilidade() {
        if (activePage === "entities") return carregarEntidades(entityPageNumber);
        return carregarDenuncias(offsetDenuncias);
    }

    function confirmarOcultacaoPreventiva(item, trigger) {
        abrirDialogoJustificativaVisibilidade({
            title: "Ocultar para an\u00e1lise",
            description: "O conte\u00fado ficar\u00e1 oculto enquanto houver den\u00fancias pendentes. Esta a\u00e7\u00e3o n\u00e3o atribui pontos nem decide as den\u00fancias.",
            placeholder: "Explique por que o conte\u00fado deve ficar oculto durante a an\u00e1lise.",
            submitText: "Ocultar conte\u00fado",
            trigger,
            submit: reason => enviarRestricaoVisibilidade(API_URL + "/admin/denuncias/" + encodeURIComponent(item.tipo) + "/" + encodeURIComponent(item.alvoId) + "/ocultacao-preventiva", reason, "Seu perfil n\u00e3o pode ocultar conte\u00fado preventivamente."),
            afterSuccess: atualizarAposAcaoVisibilidade
        });
    }

    function confirmarRestauracaoRestricao(item, block, trigger) {
        const name = block.origem === "AUTOMATIC_PROTECTION"
            ? "prote\u00e7\u00e3o autom\u00e1tica" : "restri\u00e7\u00e3o administrativa";
        abrirDialogoJustificativaVisibilidade({
            title: "Restaurar visibilidade",
            description: "Somente esta " + name + " ser\u00e1 removida. Status inativo, arquivamento, outras restri\u00e7\u00f5es e pontos ser\u00e3o preservados.",
            placeholder: "Explique por que esta restri\u00e7\u00e3o espec\u00edfica pode ser removida.",
            submitText: "Restaurar esta restri\u00e7\u00e3o",
            trigger,
            submit: reason => enviarRestricaoVisibilidade(API_URL + "/admin/denuncias/" + encodeURIComponent(item.tipo) + "/" + encodeURIComponent(item.alvoId) + "/bloqueios/" + encodeURIComponent(block.id) + "/restauracao", reason, "Seu perfil n\u00e3o pode restaurar restri\u00e7\u00f5es de visibilidade."),
            afterSuccess: atualizarAposAcaoVisibilidade
        });
    }

    document.getElementById("atualizar-denuncias").addEventListener("click", () => carregarDenuncias(offsetDenuncias));
    elements.reportFilter.addEventListener("change", () => carregarDenuncias(0));
    elements.reportState.addEventListener("change", () => carregarDenuncias(0));
    elements.reportPrevious.addEventListener("click", () => carregarDenuncias(Math.max(0, offsetDenuncias - tamanhoPaginaDenuncias)));
    elements.reportNext.addEventListener("click", () => carregarDenuncias(offsetDenuncias + tamanhoPaginaDenuncias));

    let entityPageNumber=0, entityTotalPages=0, entityRequest=0;
    const entityPageSize=24;
    async function carregarEntidades(page=entityPageNumber){
        entityPageNumber=Math.max(0,page);const requestId=++entityRequest;
        const category=elements.entityCategory.value;
        elements.entityStatus.textContent="Carregando entidades…";elements.entityList.setAttribute("aria-busy","true");
        if(category==="DONATIONS"){elements.entityList.replaceChildren();elements.entityStatus.textContent="A integração administrativa de doações ainda não está disponível.";elements.entityList.setAttribute("aria-busy","false");return;}
        const params=new URLSearchParams({category,page:String(entityPageNumber),size:String(entityPageSize)});
        if(elements.entitySearch.value.trim())params.set("q",elements.entitySearch.value.trim());
        try{
            const response=await fetch(`${API_URL}/admin/moderation/entities?${params}`,{credentials:"include",cache:"no-store",headers:{Accept:"application/json"}});
            if(response.status===401)return encerrarComSessaoExpirada();
            if(response.status===403)throw new Error("Seu perfil não tem permissão para esta categoria.");
            if(!response.ok)throw new Error("A integração de moderação não está disponível.");
            const result=await response.json();if(requestId!==entityRequest)return;
            if(!Array.isArray(result.content))throw new Error("Resposta administrativa inválida.");
            elements.entityList.replaceChildren();
            for(const item of result.content)elements.entityList.append(criarItemEntidade(item));
            entityTotalPages=Number(result.totalPages)||0;
            elements.entityStatus.textContent=result.content.length?`${result.totalElements} entidade(s) nesta categoria.`:"Nenhuma entidade encontrada.";
            elements.entityPage.textContent=entityTotalPages?`Página ${entityPageNumber+1} de ${entityTotalPages}`:"Sem páginas";
            elements.entityPrevious.disabled=entityPageNumber<=0;elements.entityNext.disabled=entityPageNumber+1>=entityTotalPages;
        }catch(error){if(requestId!==entityRequest)return;elements.entityStatus.textContent=error?.message||"Não foi possível carregar as entidades.";elements.entityList.replaceChildren();}
        finally{if(requestId===entityRequest)elements.entityList.setAttribute("aria-busy","false");}
    }
    function criarItemEntidade(item){
        const card=document.createElement("article");card.className="admin-report-card admin-moderation-entity-card";
        const info=document.createElement("div"),title=document.createElement("h2"),related=document.createElement("p"),state=document.createElement("p"),points=document.createElement("p");
        title.textContent=item.label||`${item.type} #${item.id}`;
        related.textContent=item.type==="USER"?`Usuário #${item.id}`:item.type==="OSC"?`ONG #${item.id}`:`${item.relatedLabel||"ONG indisponível"} · ${item.authorLabel?`Publicador: ${item.authorLabel}`:"Publicador indisponível"}`;
        if(item.type==="VOLUNTEERING")related.textContent+=` · ${item.opportunityModality||"Modalidade não informada"} · ${item.opportunityLocation||"Local não informado"} · ${item.opportunityVacancies??0} vaga(s)`;
        state.textContent=`Status: ${item.status||"não informado"}${item.statusOrigin?` · origem: ${item.statusOrigin}`:""}${item.archived?" · arquivada":""}`;
        const temporaryRestriction=item.temporaryRestrictionEndsAt?`Período temporário ${item.temporaryRestrictionActive?"ativo até":"encerrado em"} ${formatarDataHora.format(Date.parse(item.temporaryRestrictionEndsAt))}`:"";
        const restriction=[item.permanentRestrictionActive?"Restrição permanente por pontuação":"",temporaryRestriction].filter(Boolean).join(" · ")||"Sem restrição por pontos ativa";
        const reporting=item.automaticProtectionActive?"Proteção preventiva de conteúdo ativa":item.administrativeContentRestrictionActive?"Restrição administrativa de conteúdo ativa":"Sem bloqueio independente de conteúdo";
        points.textContent=`Pontuação do sujeito: ${item.score} · ${restriction} · ${reporting}`;
        info.append(title,related,state,points);card.append(info);
        if(item.authorUserId)card.dataset.authorUserId=String(item.authorUserId);
        if(item.type!=="USER"&&(item.pendingReports>0||item.automaticProtectionActive||item.administrativeContentRestrictionActive)){
            const details=document.createElement("section");details.className="admin-report-detail";details.hidden=true;card.append(details);
            const review=document.createElement("button");review.type="button";review.className="admin-secondary-button";review.textContent="Analisar denúncias";
            review.addEventListener("click",async()=>{if(!details.hidden){details.hidden=true;review.textContent="Analisar denúncias";return;}await abrirDetalheDenuncia({tipo:item.type==="OSC"?"OSC":"POST",alvoId:item.id,titulo:item.label,pendentes:item.pendingReports||0,protecaoAutomaticaAtiva:item.automaticProtectionActive===true,restricaoAdministrativaAtiva:item.administrativeContentRestrictionActive===true},review,details);if(!details.hidden)review.textContent="Ocultar detalhes";});card.append(review);
        }
        if(item.type==="OSC"){
            const action=item.archived?"RESTORE_ARCHIVED":item.status==="ATIVA"?"DEACTIVATE":"REACTIVATE";
            const permission=action==="RESTORE_ARCHIVED"?"ORG_RESTORE_ARCHIVED":action==="DEACTIVATE"?"ORG_DEACTIVATE":"ORG_REACTIVATE";
            if(authorization.hasPermission(permission)){
                const button=document.createElement("button");button.type="button";button.className=action==="DEACTIVATE"?"admin-secondary-button":"admin-primary-button";
                button.textContent=action==="RESTORE_ARCHIVED"?"Restaurar ONG arquivada":action==="DEACTIVATE"?"Desativar ONG":"Reativar ONG";
                button.addEventListener("click",()=>abrirStatusEntidade(item,action,button));card.append(button);
            }
        }
        if(authorization.hasPermission("POINTS_READ")&&(item.type==="USER"||item.type==="OSC")){
            const details=document.createElement("section");details.className="admin-report-detail";details.hidden=true;card.append(details);
            const button=document.createElement("button");button.type="button";button.className="admin-secondary-button";button.textContent="Ver histórico de pontos";
            button.addEventListener("click",()=>abrirHistoricoPontos(item,details,button));card.append(button);
        }
        if(authorization.hasPermission("POINTS_ADJUST")&&(item.type==="USER"||item.type==="OSC")){
            const button=document.createElement("button");button.type="button";button.className="admin-secondary-button";button.textContent="Ajustar pontos";
            button.addEventListener("click",()=>abrirAjustePontos(item,button));card.append(button);
        }
        return card;
    }
    async function abrirHistoricoPontos(item,target,button){
        if(!target.hidden){target.hidden=true;button.textContent="Ver histórico de pontos";return;}
        button.disabled=true;
        try{
            const response=await fetch(`${API_URL}/admin/moderation/subjects/${encodeURIComponent(item.type)}/${encodeURIComponent(item.id)}`,{credentials:"include",cache:"no-store",headers:{Accept:"application/json"}});
            if(response.status===401)return encerrarComSessaoExpirada();if(response.status===403)throw new Error("Seu perfil não pode consultar este histórico.");if(!response.ok)throw new Error("Não foi possível carregar o histórico de pontos.");
            const subject=await response.json();target.replaceChildren();const heading=document.createElement("h3");heading.textContent=`Histórico · ${subject.subjectLabel||`${subject.subjectType} #${subject.subjectId}`}`;target.append(heading);
            const entries=subject.pointsHistory||[],appeals=subject.appeals||[];
            for(const entry of entries){const row=document.createElement("p");const origin=({UPHELD_DECISION:"Decisão procedente",MANUAL_ADJUSTMENT:"Ajuste administrativo",APPEAL_CORRECTION:"Correção de recurso"})[entry.origin]||"Lançamento";row.textContent=`${entry.delta>0?"+":""}${entry.delta} · ${origin} · ${entry.occurredAt?formatarDataHora.format(Date.parse(entry.occurredAt)):"data não informada"} · ${entry.justification}`;target.append(row);}
            for(const appeal of appeals){const row=document.createElement("p");const state=({PENDING:"Pendente",REJECTED:"Rejeitado",PARTIAL:"Parcialmente aceito",ACCEPTED:"Aceito"})[appeal.status]||"Status desconhecido";row.textContent=`Recurso #${appeal.id} · ${state} · ${appeal.response||appeal.justification}`;target.append(row);}
            if(!entries.length&&!appeals.length){const empty=document.createElement("p");empty.textContent="Ainda não há lançamentos ou recursos.";target.append(empty);}target.hidden=false;button.textContent="Ocultar histórico";
        }catch(error){target.replaceChildren();const message=document.createElement("p");message.textContent=error?.message||"Falha ao carregar o histórico.";target.append(message);target.hidden=false;}
        finally{button.disabled=false;}
    }
    function abrirStatusEntidade(item, action, trigger) {
        const nextStatus = action === "DEACTIVATE" ? "INATIVA" : "ATIVA";
        const dialog = document.createElement("dialog");
        dialog.className = "admin-dialog admin-status-entity-dialog";
        dialog.setAttribute("aria-labelledby", "titulo-status-entidade");
        const form = document.createElement("form");
        form.className = "admin-dialog__card admin-status-entity-dialog__card";
        const title = document.createElement("h2");
        title.id = "titulo-status-entidade";
        title.textContent = action === "DEACTIVATE" ? "Desativar ONG"
            : action === "RESTORE_ARCHIVED" ? "Restaurar ONG arquivada" : "Reativar ONG";
        const description = document.createElement("p");
        description.className = "admin-dialog__description";
        description.textContent = action === "DEACTIVATE"
            ? "A ONG e seu conte\u00fado p\u00fablico ficar\u00e3o indispon\u00edveis enquanto permanecer inativa."
            : "A ONG voltar\u00e1 a ficar dispon\u00edvel conforme suas regras de visibilidade e restri\u00e7\u00f5es independentes.";
        const field = document.createElement("div");
        field.className = "admin-dialog__field";
        const label = document.createElement("label");
        label.htmlFor = "justificativa-status-entidade";
        label.textContent = "Justificativa obrigat\u00f3ria";
        const reason = document.createElement("textarea");
        reason.id = label.htmlFor;
        reason.maxLength = 2000;
        reason.minLength = 8;
        reason.required = true;
        reason.rows = 4;
        reason.placeholder = action === "RESTORE_ARCHIVED"
            ? "Explique por que a ONG arquivada pode ser restaurada."
            : "Explique a altera\u00e7\u00e3o de status da ONG.";
        field.append(label, reason);
        const status = document.createElement("p");
        status.setAttribute("role", "alert");
        status.setAttribute("aria-live", "polite");
        const actions = document.createElement("div");
        actions.className = "admin-dialog__actions";
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "admin-secondary-button";
        cancel.textContent = "Voltar";
        const save = document.createElement("button");
        save.type = "submit";
        save.className = "admin-primary-button";
        save.textContent = action === "RESTORE_ARCHIVED" ? "Restaurar ONG arquivada" : "Confirmar";
        actions.append(cancel, save);
        form.append(title, description, field, status, actions);
        dialog.append(form);
        document.body.append(dialog);
        cancel.addEventListener("click", () => dialog.close());
        dialog.addEventListener("close", () => { dialog.remove(); if (trigger?.isConnected) trigger.focus(); });
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (save.disabled || !form.reportValidity()) return;
            const justification = reason.value.trim();
            if (justification.length < 8) {
                status.textContent = "Informe uma justificativa com pelo menos 8 caracteres.";
                reason.setAttribute("aria-invalid", "true");
                reason.focus();
                return;
            }
            save.disabled = true;
            const originalText = save.textContent;
            save.textContent = "Salvando\u2026";
            status.textContent = "Salvando status\u2026";
            try {
                const csrf = await obterCsrf();
                const response = await fetch(API_URL + "/admin/oscs/" + encodeURIComponent(item.id) + "/status", {
                    method: "PATCH", credentials: "include", cache: "no-store",
                    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                    body: JSON.stringify({ status: nextStatus, motivo: justification })
                });
                if (response.status === 401) { dialog.close(); return encerrarComSessaoExpirada(); }
                if (response.status === 403) throw new Error("Seu perfil n\u00e3o pode alterar este status.");
                if (response.status === 409) throw new Error("O status ou a origem da restri\u00e7\u00e3o mudou. Atualize a lista.");
                if (!response.ok) throw new Error("N\u00e3o foi poss?vel alterar o status da ONG.");
                dialog.close();
                await carregarEntidades(entityPageNumber);
            } catch (error) {
                status.textContent = error?.message || "Falha ao alterar o status.";
            } finally {
                save.disabled = false;
                save.textContent = originalText;
            }
        });
        showAdminDialog(dialog);
        reason.focus();
    }

    function abrirAjustePontos(item,trigger){
        const referenceKey=crypto.randomUUID();
        const dialog=document.createElement("dialog");dialog.className="admin-dialog admin-points-adjustment-dialog";dialog.setAttribute("aria-labelledby","titulo-ajuste-pontos");
        const form=document.createElement("form");form.className="admin-dialog__card admin-points-adjustment-dialog__card";
        const title=document.createElement("h2");title.id="titulo-ajuste-pontos";title.textContent=`Ajustar pontos — ${item.label||item.type}`;
        const deltaLabel=document.createElement("label");deltaLabel.htmlFor="delta-ajuste-pontos";deltaLabel.textContent="Variação de pontos (-10 a 10, exceto zero)";
        const delta=document.createElement("input");delta.id=deltaLabel.htmlFor;delta.type="number";delta.min="-10";delta.max="10";delta.step="1";delta.required=true;
        const reasonLabel=document.createElement("label");reasonLabel.htmlFor="motivo-ajuste-pontos";reasonLabel.textContent="Justificativa obrigatória";
        const reason=document.createElement("textarea");reason.id=reasonLabel.htmlFor;reason.maxLength=2000;reason.required=true;reason.rows=4;
        const status=document.createElement("p");status.setAttribute("role","status");status.setAttribute("aria-live","polite");
        const actions=document.createElement("div");actions.className="admin-dialog__actions";
        const cancel=document.createElement("button");cancel.type="button";cancel.className="admin-secondary-button";cancel.textContent="Voltar";
        const save=document.createElement("button");save.type="submit";save.className="admin-primary-button";save.textContent="Registrar ajuste";actions.append(cancel,save);
        form.append(title,deltaLabel,delta,reasonLabel,reason,status,actions);dialog.append(form);document.body.append(dialog);
        cancel.addEventListener("click",()=>dialog.close());dialog.addEventListener("close",()=>{dialog.remove();trigger.focus();});
        form.addEventListener("submit",async event=>{event.preventDefault();if(save.disabled)return;const value=Number(delta.value);if(!Number.isInteger(value)||value===0||value < -10||value>10||!reason.value.trim())return;save.disabled=true;status.textContent="Registrando ajuste…";
            try{const csrf=await obterCsrf();const response=await fetch(`${API_URL}/admin/moderation/subjects/${encodeURIComponent(item.type)}/${encodeURIComponent(item.id)}/points`,{method:"POST",credentials:"include",cache:"no-store",headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},body:JSON.stringify({referenceKey,delta:value,justificativa:reason.value.trim()})});
                if(response.status===401){dialog.close();return encerrarComSessaoExpirada();}if(response.status===403)throw new Error("Seu perfil não pode ajustar pontos.");if(response.status===409)throw new Error("O saldo mudou, o ajuste seria negativo ou a chave já foi usada com dados diferentes. Atualize a lista.");if(!response.ok)throw new Error("Não foi possível registrar o ajuste.");dialog.close();await carregarEntidades(entityPageNumber);
            }catch(error){status.textContent=error?.message||"Falha ao registrar ajuste.";}finally{save.disabled=false;}});
        showAdminDialog(dialog);delta.focus();
    }

    let appealPageNumber=0,appealTotalPages=0,appealRequest=0;const appealPageSize=25;
    async function carregarRecursos(page=appealPageNumber){
        appealPageNumber=Math.max(0,page);const requestId=++appealRequest;elements.appealStatus.textContent="Carregando recursos…";elements.appealList.setAttribute("aria-busy","true");
        const params=new URLSearchParams({status:elements.appealFilter.value,page:String(appealPageNumber),size:String(appealPageSize)});
        try{const response=await fetch(`${API_URL}/admin/moderation/appeals?${params}`,{credentials:"include",cache:"no-store",headers:{Accept:"application/json"}});if(response.status===401)return encerrarComSessaoExpirada();if(response.status===403)throw new Error("Seu perfil não pode consultar recursos.");if(!response.ok)throw new Error("Não foi possível carregar recursos.");const result=await response.json();if(requestId!==appealRequest)return;if(!Array.isArray(result.content))throw new Error("Resposta de recursos inválida.");
            elements.appealList.replaceChildren();for(const item of result.content)elements.appealList.append(criarItemRecurso(item));appealTotalPages=Number(result.totalPages)||0;elements.appealStatus.textContent=result.content.length?`${result.totalElements} recurso(s) nesta fila.`:"Não há recursos nesta fila.";elements.appealPage.textContent=appealTotalPages?`Página ${appealPageNumber+1} de ${appealTotalPages}`:"Sem páginas";elements.appealPrevious.disabled=appealPageNumber<=0;elements.appealNext.disabled=appealPageNumber+1>=appealTotalPages;
        }catch(error){if(requestId!==appealRequest)return;elements.appealStatus.textContent=error?.message||"Não foi possível carregar recursos.";elements.appealList.replaceChildren();}finally{if(requestId===appealRequest)elements.appealList.setAttribute("aria-busy","false");}
    }
    const appealStatusLabels = Object.freeze({ PENDING: "Pendente", REJECTED: "Rejeitado", PARTIAL: "Parcialmente aceito", ACCEPTED: "Aceito" });
    function criarItemRecurso(item){const card=document.createElement("article");card.className="admin-report-card";const info=document.createElement("div"),title=document.createElement("h2"),detail=document.createElement("p"),reason=document.createElement("p"),state=document.createElement("p");title.textContent=`${item.subjectLabel||item.subjectType} · ${item.subjectType} #${item.subjectId}`;detail.textContent=`Solicitado por ${item.appellantLabel||"conta indisponível"} · decisão #${item.decisionHistoryId}`;reason.textContent=item.justification||"Sem justificativa disponível.";state.textContent=`Status: ${appealStatusLabels[item.status]||"Status desconhecido"} · Pontos ainda reversíveis: ${item.pointsRemaining}`;info.append(title,detail,reason,state);card.append(info);if(item.status==="PENDING"&&authorization.hasPermission("APPEAL_RESOLVE")){const button=document.createElement("button");button.type="button";button.className="admin-primary-button";button.textContent="Analisar recurso";button.addEventListener("click",()=>abrirDetalheRecurso(item,button));card.append(button);}return card;}
    async function abrirDetalheRecurso(item,trigger){trigger.disabled=true;try{const response=await fetch(`${API_URL}/admin/moderation/appeals/${encodeURIComponent(item.id)}`,{credentials:"include",cache:"no-store",headers:{Accept:"application/json"}});if(response.status===401)return encerrarComSessaoExpirada();if(response.status===403)throw new Error("Seu perfil não pode consultar os detalhes deste recurso.");if(!response.ok)throw new Error("Não foi possível carregar os detalhes do recurso.");abrirDecisaoRecurso(await response.json(),trigger);}catch(error){elements.appealStatus.textContent=error?.message||"Falha ao carregar o recurso.";}finally{trigger.disabled=false;}}
    function abrirDecisaoRecurso(item, trigger) {
        const dialog = document.createElement("dialog");
        dialog.className = "admin-dialog admin-appeal-review-dialog";
        dialog.setAttribute("aria-labelledby", "titulo-decisao-recurso");
        dialog.setAttribute("aria-describedby", "contexto-decisao-recurso");
        const form = document.createElement("form");
        form.className = "admin-dialog__card admin-appeal-review-dialog__card";
        const title = document.createElement("h2");
        title.id = "titulo-decisao-recurso";
        title.textContent = "Analisar recurso";
        const context = document.createElement("div");
        context.id = "contexto-decisao-recurso";
        context.className = "admin-operation-review";
        for (const text of [
            "Sujeito: " + item.subjectLabel + " \u00b7 " + item.subjectType + " #" + item.subjectId,
            "Decis\u00e3o original #" + item.decisionHistoryId + ": " + item.originalDecisionJustification,
            "Pontos originais: " + item.originalPoints + " \u00b7 revertidos: " + item.pointsReversed + " \u00b7 restantes: " + item.pointsRemaining,
            "Recurso de " + item.appellantLabel + ": " + item.appealJustification
        ]) {
            const paragraph = document.createElement("p");
            paragraph.textContent = text;
            context.append(paragraph);
        }
        const decisionField = document.createElement("div");
        decisionField.className = "admin-dialog__field";
        const decisionLabel = document.createElement("label");
        decisionLabel.htmlFor = "decisao-recurso-admin";
        decisionLabel.textContent = "Decis\u00e3o";
        const decision = document.createElement("select");
        decision.id = decisionLabel.htmlFor;
        decision.required = true;
        decision.add(new Option("Selecione", ""));
        decision.add(new Option("Rejeitar", "REJECT"));
        const partialOption = new Option("Aceitar parcialmente", "PARTIAL");
        partialOption.disabled = item.subjectScore < 1 || item.pointsRemaining <= 1;
        decision.add(partialOption);
        const acceptOption = new Option("Aceitar integralmente", "ACCEPT");
        acceptOption.disabled = item.subjectScore < item.pointsRemaining;
        decision.add(acceptOption);
        decisionField.append(decisionLabel, decision);
        const pointsField = document.createElement("div");
        pointsField.className = "admin-dialog__field";
        pointsField.hidden = true;
        const pointsLabel = document.createElement("label");
        pointsLabel.htmlFor = "pontos-recurso-admin";
        pointsLabel.textContent = "Pontos a reverter";
        const points = document.createElement("input");
        points.id = pointsLabel.htmlFor;
        points.type = "number";
        points.min = "1";
        points.max = String(Math.min(item.pointsRemaining - 1, item.subjectScore));
        points.step = "1";
        pointsField.append(pointsLabel, points);
        const reasonField = document.createElement("div");
        reasonField.className = "admin-dialog__field";
        const reasonLabel = document.createElement("label");
        reasonLabel.htmlFor = "justificativa-recurso-admin";
        reasonLabel.textContent = "Resposta obrigat\u00f3ria";
        const reason = document.createElement("textarea");
        reason.id = reasonLabel.htmlFor;
        reason.required = true;
        reason.maxLength = 2000;
        reason.rows = 4;
        reason.placeholder = "Explique a an\u00e1lise deste recurso.";
        reasonField.append(reasonLabel, reason);
        const status = document.createElement("p");
        status.setAttribute("role", "alert");
        status.setAttribute("aria-live", "polite");
        const actions = document.createElement("div");
        actions.className = "admin-dialog__actions";
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "admin-secondary-button";
        cancel.textContent = "Voltar";
        const submit = document.createElement("button");
        submit.type = "submit";
        submit.className = "admin-primary-button";
        submit.textContent = "Salvar decis\u00e3o";
        actions.append(cancel, submit);
        form.append(title, context, decisionField, pointsField, reasonField, status, actions);
        dialog.append(form);
        document.body.append(dialog);
        decision.addEventListener("change", () => {
            const partial = decision.value === "PARTIAL";
            pointsField.hidden = !partial;
            points.required = partial;
        });
        cancel.addEventListener("click", () => dialog.close());
        dialog.addEventListener("close", () => { dialog.remove(); if (trigger?.isConnected) trigger.focus(); });
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (submit.disabled || !form.reportValidity()) return;
            const choice = decision.value;
            const reasonText = reason.value.trim();
            const invalidPartial = choice === "PARTIAL" && (!Number.isInteger(Number(points.value))
                || Number(points.value) < 1 || Number(points.value) >= item.pointsRemaining
                || Number(points.value) > item.subjectScore);
            if (!choice || !reasonText || invalidPartial) {
                status.textContent = "Confira a decis\u00e3o, os pontos e a justificativa.";
                return;
            }
            submit.disabled = true;
            submit.textContent = "Salvando\u2026";
            status.textContent = "Salvando decis\u00e3o\u2026";
            const payload = { decisao: choice, justificativa: reasonText };
            if (choice === "PARTIAL") payload.pontosRevertidos = Number(points.value);
            else if (choice === "ACCEPT") payload.pontosRevertidos = item.pointsRemaining;
            else payload.pontosRevertidos = 0;
            try {
                const csrf = await obterCsrf();
                const response = await fetch(API_URL + "/admin/moderation/appeals/" + encodeURIComponent(item.id) + "/decision", {
                    method: "POST", credentials: "include", cache: "no-store",
                    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                    body: JSON.stringify(payload)
                });
                if (response.status === 401) { dialog.close(); return encerrarComSessaoExpirada(); }
                if (response.status === 403) throw new Error("Seu perfil n\u00e3o pode revisar este recurso ou avaliar a pr\u00f3pria decis\u00e3o.");
                if (response.status === 409) throw new Error("O recurso ou o saldo mudou. Atualize a fila antes de tentar novamente.");
                if (!response.ok) throw new Error("N\u00e3o foi poss\u00edvel salvar a decis\u00e3o.");
                dialog.close();
                await carregarRecursos(appealPageNumber);
            } catch (error) {
                status.textContent = error?.message || "Falha ao salvar o recurso.";
            } finally {
                submit.disabled = false;
                submit.textContent = "Salvar decis\u00e3o";
            }
        });
        showAdminDialog(dialog);
        decision.focus();
    }

    elements.entityCategory.addEventListener("change",()=>carregarEntidades(0));
    elements.entitySearch.addEventListener("keydown",event=>{if(event.key==="Enter"){event.preventDefault();carregarEntidades(0);}});
    elements.entityPrevious.addEventListener("click",()=>carregarEntidades(Math.max(0,entityPageNumber-1)));
    elements.entityNext.addEventListener("click",()=>carregarEntidades(Math.min(entityTotalPages-1,entityPageNumber+1)));
    elements.appealFilter.addEventListener("change",()=>carregarRecursos(0));
    elements.appealPrevious.addEventListener("click",()=>carregarRecursos(Math.max(0,appealPageNumber-1)));
    elements.appealNext.addEventListener("click",()=>carregarRecursos(Math.min(appealTotalPages-1,appealPageNumber+1)));

    async function encerrarSessaoAdmin() {
        if (loggingOut) return;
        loggingOut = true;
        const sessionWasValidated = sessionValidated;
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        limparAutorizacao();
        limparDadosProtegidos();
        esconderEstados();
        elements.logout.disabled = true;
        elements.sessionStatus.textContent = "Encerrando sessão administrativa…";
        const controller = new AbortController();

        async function lidarComLogoutNaoConfirmado(mensagem) {
            try {
                await obterSessao(controller.signal);
                sessionValidated = true;
                mostrarErro(mensagem, { keepSession: true });
            } catch (error) {
                if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                    encerrarComSessaoExpirada("A sessão administrativa não está mais ativa.");
                } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                    sessionValidated = sessionWasValidated;
                    mostrarAcessoNegado("O logout não foi confirmado; a sessão administrativa não foi encerrada.");
                } else {
                    sessionValidated = sessionWasValidated;
                    mostrarErro("Não foi possível confirmar o logout. Tente novamente.", {
                        keepSession: sessionWasValidated
                    });
                }
            }
        }

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

            if (resposta.status === 401) {
                encerrarComSessaoExpirada("A sessão administrativa não está mais ativa.");
                return;
            }
            if (resposta.status === 403) {
                await lidarComLogoutNaoConfirmado("O logout foi recusado. A sessão administrativa continua ativa.");
                return;
            }
            throw new Error("logout-failed");
        } catch (error) {
            if (error?.name === "AbortError") return;
            await lidarComLogoutNaoConfirmado("Não foi possível confirmar o logout. Tente novamente.");
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
        });
    });

    let lastHandledHash = window.location.hash || "#visao-geral";
    window.addEventListener("hashchange", () => {
        const requestedPage = paginaSolicitada();
        if ((assignmentPanel.isMutating() || verificationPanel.isMutating() || supportPanel.isMutating()) && requestedPage !== activePage) {
            window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${lastHandledHash}`);
            return;
        }
        lastHandledHash = window.location.hash || "#visao-geral";
        if (requestedPage === activePage) {
            atualizarNavegacaoAtiva(requestedPage);
            return;
        }
        if (loading) {
            activeController?.abort();
            activeController = null;
            requestVersion += 1;
            loading = false;
        }
        void carregarDashboard();
    });

    window.addEventListener("porto-ajuda:admin-session-ended", () => {
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        mostrarNaoAutenticado();
        elements.sessionStatus.textContent = "Sessão administrativa encerrada";
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
