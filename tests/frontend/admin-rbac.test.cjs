const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

async function loadAdminRbacModule() {
    const source = await readFile(resolve(__dirname, "../../public/components/admin-rbac.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

function response(status, payload, headers = {}) {
    return {
        status,
        ok: status >= 200 && status < 300,
        json: async () => payload,
        headers: { get: name => headers[name] ?? null }
    };
}

test("carrega capabilities atuais via cookie e sem Authorization JWT", async () => {
    const { fetchAdminCapabilities } = await loadAdminRbacModule();
    const payload = {
        profile: "SUPER_ADMIN",
        permissions: ["DASHBOARD_READ", "ADMIN_MANAGE", "SESSION_REVOKE_SELF"]
    };
    let request;
    const capabilities = await fetchAdminCapabilities("https://api.test", async (url, init) => {
        request = { url, init };
        return response(200, payload);
    });

    assert.deepEqual(capabilities, payload);
    assert.equal(request.url, "https://api.test/admin/auth/capabilities");
    assert.equal(request.init.credentials, "include");
    assert.equal(request.init.cache, "no-store");
    assert.equal(request.init.headers.Authorization, undefined);
});

test("mantém permissões de SUPER_ADMIN recebidas do backend sem inferi-las", async () => {
    const { createAdminAuthorizationStore } = await loadAdminRbacModule();
    const store = createAdminAuthorizationStore();
    store.set({ profile: "SUPER_ADMIN", permissions: ["DASHBOARD_READ", "ADMIN_MANAGE"] });

    assert.equal(store.current.profile, "SUPER_ADMIN");
    assert.equal(store.hasPermission("ADMIN_MANAGE"), true);
    assert.equal(store.hasPermission("FINANCE_SUMMARY_READ"), false);
});

test("perfil restrito só recebe permissões e navegação presentes na resposta", async () => {
    const { createAdminAuthorizationStore, renderPermissionNavigation } = await loadAdminRbacModule();
    const store = createAdminAuthorizationStore();
    store.set({ profile: "MODERATOR", permissions: ["DASHBOARD_READ", "CONTENT_READ"] });
    const entries = [
        { id: "dashboard", permission: "DASHBOARD_READ", element: { hidden: true } },
        { id: "assignments", permission: "ADMIN_MANAGE", element: { hidden: true } }
    ];
    const navigation = { hidden: true };

    assert.equal(store.hasPermission("ADMIN_MANAGE"), false);
    assert.deepEqual(renderPermissionNavigation(navigation, entries, store), [entries[0]]);
    assert.equal(navigation.hidden, false);
    assert.equal(entries[0].element.hidden, false);
    assert.equal(entries[1].element.hidden, true);
});

test("não mostra dashboard nem links quando DASHBOARD_READ está ausente", async () => {
    const { createAdminAuthorizationStore, renderPermissionNavigation } = await loadAdminRbacModule();
    const store = createAdminAuthorizationStore();
    store.set({ profile: "AUDITOR", permissions: ["AUDIT_READ_GLOBAL"] });
    const navigation = { hidden: false };
    const dashboard = { hidden: false };

    assert.equal(store.hasPermission("DASHBOARD_READ"), false);
    assert.deepEqual(renderPermissionNavigation(navigation, [
        { id: "dashboard", permission: "DASHBOARD_READ", element: dashboard }
    ], store), []);
    assert.equal(navigation.hidden, true);
    assert.equal(dashboard.hidden, true);
});

test("trata HTTP 401 como sessão inválida", async () => {
    const { fetchAdminCapabilities } = await loadAdminRbacModule();
    await assert.rejects(
        fetchAdminCapabilities("https://api.test", async () => response(401, null)),
        error => error.kind === "unauthorized" && error.status === 401
    );
});

test("trata HTTP 403 como acesso negado, sem classificá-lo como expiração", async () => {
    const { fetchAdminCapabilities } = await loadAdminRbacModule();
    const { createAdminAuthorizationStore } = await loadAdminRbacModule();
    const store = createAdminAuthorizationStore();
    store.set({ profile: "MODERATOR", permissions: ["DASHBOARD_READ"] });
    await assert.rejects(
        fetchAdminCapabilities("https://api.test", async () => response(403, null)),
        error => error.kind === "forbidden" && error.status === 403
    );
    assert.equal(store.current.profile, "MODERATOR");
    assert.equal(store.hasPermission("DASHBOARD_READ"), true);
});

test("dashboard distingue 401 e 403 e valida o contrato existente", async () => {
    const { fetchAdminDashboard } = await loadAdminRbacModule();
    const payload = {
        registeredUsers: 10,
        activeOrganizations: 3,
        inactiveOrganizations: 1,
        archivedOrganizations: 2,
        publications: 8,
        volunteerOpportunities: 4,
        generatedAt: "2030-01-01T00:00:00Z",
        recentActivityAvailable: true,
        recentActivity: []
    };
    assert.deepEqual(await fetchAdminDashboard("https://api.test", async () => response(200, payload)), payload);
    await assert.rejects(
        fetchAdminDashboard("https://api.test", async () => response(401, null)),
        error => error.kind === "unauthorized"
    );
    await assert.rejects(
        fetchAdminDashboard("https://api.test", async () => response(403, null)),
        error => error.kind === "forbidden"
    );
});

test("network failure não é convertido em 401 ou 403", async () => {
    const { fetchAdminCapabilities } = await loadAdminRbacModule();
    await assert.rejects(
        fetchAdminCapabilities("https://api.test", async () => { throw new Error("offline"); }),
        error => error.kind === "network" && error.status === null
    );
});

test("rejeita capabilities inesperadas, duplicadas ou malformadas", async () => {
    const { fetchAdminCapabilities } = await loadAdminRbacModule();
    for (const payload of [
        { profile: "UNKNOWN", permissions: ["DASHBOARD_READ"] },
        { profile: "AUDITOR", permissions: ["DASHBOARD_READ", "DASHBOARD_READ"] },
        { profile: "AUDITOR", permissions: ["dashboard_read"] },
        { profile: "AUDITOR", permissions: [] }
    ]) {
        await assert.rejects(
            fetchAdminCapabilities("https://api.test", async () => response(200, payload)),
            error => error.kind === "invalid-response"
        );
    }
});

test("limpa capabilities em memória após logout sem usar armazenamento persistente", async () => {
    const { createAdminAuthorizationStore, clearAdminAuthorizationState } = await loadAdminRbacModule();
    const store = createAdminAuthorizationStore();
    store.set({ profile: "SUPER_ADMIN", permissions: ["DASHBOARD_READ"] });
    const elements = {
        navigation: { hidden: false },
        profile: { hidden: false },
        profileName: { textContent: "Superadministrador" }
    };
    clearAdminAuthorizationState(store, elements);

    assert.equal(store.current, null);
    assert.equal(store.hasPermission("DASHBOARD_READ"), false);
    assert.equal(elements.navigation.hidden, true);
    assert.equal(elements.profile.hidden, true);
    assert.equal(elements.profileName.textContent, "");
});

test("sessão com prazo esgotado é classificada como não autenticada", async () => {
    const { fetchAdminSession } = await loadAdminRbacModule();
    const fixedNow = 100;
    const serverTime = Date.parse("2030-01-01T00:00:00.000Z");
    const expiredHeaders = {
        "X-PA-ADMIN-SERVER-TIME": new Date(serverTime).toISOString(),
        "X-PA-ADMIN-ABSOLUTE-EXPIRES-AT": new Date(serverTime + 1000).toISOString(),
        "X-PA-ADMIN-IDLE-EXPIRES-AT": new Date(serverTime + 1000).toISOString()
    };

    await assert.rejects(
        fetchAdminSession("https://api.test", async () => response(204, null, expiredHeaders), undefined,
            () => fixedNow),
        error => error.kind === "unauthorized"
    );
});

test("limpa valores e nós protegidos para não deixar dados antigos visíveis", async () => {
    const { clearProtectedDashboard } = await loadAdminRbacModule();
    const metric = { textContent: "91" };
    const activityList = {
        hidden: false,
        replaced: false,
        replaceChildren() { this.replaced = true; }
    };
    const elements = {
        metrics: [metric],
        activityList,
        activityState: { textContent: "evento", hidden: false },
        generatedAt: { textContent: "ontem" },
        content: { hidden: false },
        refresh: { hidden: false },
        logout: { hidden: false }
    };

    clearProtectedDashboard(elements, { keepLogout: true });
    assert.equal(metric.textContent, "—");
    assert.equal(activityList.replaced, true);
    assert.equal(activityList.hidden, true);
    assert.equal(elements.generatedAt.textContent, "");
    assert.equal(elements.content.hidden, true);
    assert.equal(elements.refresh.hidden, true);
    assert.equal(elements.logout.hidden, false);
});

test("store não consulta nem grava localStorage/sessionStorage", async () => {
    const { createAdminAuthorizationStore } = await loadAdminRbacModule();
    const source = await readFile(resolve(__dirname, "../../public/components/admin-rbac.js"), "utf8");
    const store = createAdminAuthorizationStore();
    store.set({ profile: "AUDITOR", permissions: ["AUDIT_READ_GLOBAL"] });

    assert.equal(store.current.profile, "AUDITOR");
    assert.doesNotMatch(store.current.permissions.join(" "), /token|cookie|secret/i);
    assert.doesNotMatch(source, /localStorage|sessionStorage/);
});

function assignmentDto(overrides = {}) {
    return {
        id: "79ea5000-32fb-4d6f-9d5d-c9113d41e4e8",
        userId: 123,
        profile: "MODERATOR",
        status: "ACTIVE",
        grantedAt: "2030-01-01T00:00:00Z",
        grantedByUserId: 47,
        lastModifiedAt: "2030-01-01T00:00:00Z",
        lastModifiedByUserId: 47,
        revokedAt: null,
        reason: "Solicitação aprovada",
        ...overrides
    };
}

test("lista atribuições com paginação no servidor e valida DTO sem PII extra", async () => {
    const { fetchAdminAssignments } = await loadAdminRbacModule();
    let request;
    const assignments = await fetchAdminAssignments("https://api.test", 51, 50,
        async (url, init) => {
            request = { url: new URL(url), init };
            return response(200, [assignmentDto()]);
        });

    assert.equal(request.url.pathname, "/admin/assignments");
    assert.equal(request.url.searchParams.get("limit"), "51");
    assert.equal(request.url.searchParams.get("offset"), "50");
    assert.equal(request.init.credentials, "include");
    assert.equal(request.init.headers.Authorization, undefined);
    assert.deepEqual(assignments[0], assignmentDto());
});

test("concessão envia contrato real, CSRF e apenas uma solicitação mutável", async () => {
    const { mutateAdminAssignment } = await loadAdminRbacModule();
    const calls = [];
    const result = await mutateAdminAssignment("https://api.test", "assign", null, {
        userId: 123,
        profile: "MODERATOR",
        reason: "PA-12345"
    }, async (url, init) => {
        calls.push({ url: new URL(url), init });
        if (url.endsWith("/usuario/csrf")) {
            return response(200, { headerName: "X-XSRF-TOKEN", token: "mock-csrf" });
        }
        return response(201, assignmentDto());
    });

    assert.equal(calls.length, 2);
    assert.equal(calls[0].url.pathname, "/usuario/csrf");
    assert.equal(calls[1].url.pathname, "/admin/assignments");
    assert.equal(calls[1].init.method, "POST");
    assert.equal(calls[1].init.credentials, "include");
    assert.equal(calls[1].init.headers["X-XSRF-TOKEN"], "mock-csrf");
    assert.deepEqual(JSON.parse(calls[1].init.body), {
        userId: 123,
        profile: "MODERATOR",
        reason: "PA-12345"
    });
    assert.equal(result.userId, 123);
});

test("profile change e revocation usam rotas/verbos existentes", async () => {
    const { mutateAdminAssignment } = await loadAdminRbacModule();
    const assignmentId = assignmentDto().id;
    const calls = [];
    const fetchMock = async (url, init) => {
        calls.push({ url: new URL(url), init });
        if (url.endsWith("/usuario/csrf")) {
            return response(200, { headerName: "X-XSRF-TOKEN", token: "mock-csrf" });
        }
        return response(200, assignmentDto({ profile: "PLATFORM_ADMIN" }));
    };

    await mutateAdminAssignment("https://api.test", "change-profile", assignmentId, {
        profile: "PLATFORM_ADMIN", reason: "PA-23456"
    }, fetchMock);
    await mutateAdminAssignment("https://api.test", "revoke", assignmentId, {
        reason: "PA-34567"
    }, fetchMock);

    assert.equal(calls[1].url.pathname, `/admin/assignments/${assignmentId}/profile`);
    assert.equal(calls[1].init.method, "PATCH");
    assert.equal(calls[3].url.pathname, `/admin/assignments/${assignmentId}/revoke`);
    assert.equal(calls[3].init.method, "POST");
});

test("lista paginada rejeita DTOs malformados e erros HTTP sem ler detalhes privados", async () => {
    const { fetchAdminAssignments } = await loadAdminRbacModule();
    await assert.rejects(
        fetchAdminAssignments("https://api.test", 51, 0, async () => response(200, [
            assignmentDto({ userId: "private-value" })
        ])),
        error => error.kind === "invalid-response"
    );
    await assert.rejects(
        fetchAdminAssignments("https://api.test", 51, 0, async () => response(403, {
            message: "private server detail"
        })),
        error => error.kind === "forbidden" && !error.message.includes("private server detail")
    );
});

test("listagem vazia mantém resposta válida para o estado vazio da interface", async () => {
    const { fetchAdminAssignments } = await loadAdminRbacModule();
    assert.deepEqual(
        await fetchAdminAssignments("https://api.test", 51, 0, async () => response(200, [])),
        []
    );
});

test("interface administrativa usa dialog acessível e confirma antes da mutação", async () => {
    const html = await readFile(resolve(__dirname, "../../public/admin/index.html"), "utf8");
    const source = await readFile(resolve(__dirname, "../../public/components/admin-assignments.js"), "utf8");
    assert.match(html, /<dialog[^>]+aria-labelledby="titulo-dialogo-atribuicao"[^>]+aria-describedby="descricao-dialogo-atribuicao"/);
    assert.match(html, /id="motivo-atribuicao-admin"[^>]+required/);
    assert.match(source, /elements\.dialog\.showModal\(\)/);
    assert.match(source, /elements\.dialog\.addEventListener\("cancel"/);
    assert.match(source, /if \(isMutating\) event\.preventDefault\(\)/);
    assert.match(source, /async function submitMutation/);
    assert.match(source, /mutateAdminAssignment\(/);
    assert.doesNotMatch(source, /window\.(alert|confirm)\(/);
});

test("mutation conflicts are surfaced without retrying the state-changing request", async () => {
    const { mutateAdminAssignment } = await loadAdminRbacModule();
    let mutationCount = 0;
    await assert.rejects(
        mutateAdminAssignment("https://api.test", "revoke", assignmentDto().id, {
            reason: "PA-45678"
        }, async url => {
            if (url.endsWith("/usuario/csrf")) {
                return response(200, { headerName: "X-XSRF-TOKEN", token: "mock-csrf" });
            }
            mutationCount += 1;
            return response(409, { message: "server detail must not be rendered" });
        }),
        error => error.kind === "conflict" && error.status === 409
    );
    assert.equal(mutationCount, 1);
});

test("mutation maps backend 400/401/403/404/409 without exposing response bodies or retrying", async () => {
    const { mutateAdminAssignment } = await loadAdminRbacModule();
    const expected = new Map([
        [400, "validation"],
        [401, "unauthorized"],
        [403, "forbidden"],
        [404, "not-found"],
        [409, "conflict"]
    ]);
    for (const [status, kind] of expected) {
        let writeCount = 0;
        await assert.rejects(
            mutateAdminAssignment("https://api.test", "revoke", assignmentDto().id, {
                reason: "Solicitação revisada"
            }, async url => {
                if (url.endsWith("/usuario/csrf")) {
                    return response(200, { headerName: "X-XSRF-TOKEN", token: "mock-csrf" });
                }
                writeCount += 1;
                return response(status, { message: "sensitive backend detail" });
            }),
            error => error.kind === kind && error.status === status
                && !error.message.includes("sensitive backend detail")
        );
        assert.equal(writeCount, 1);
    }
});

test("não envia mutação sem justificativa ou com contrato inválido", async () => {
    const { mutateAdminAssignment } = await loadAdminRbacModule();
    let requestCount = 0;
    await assert.rejects(
        mutateAdminAssignment("https://api.test", "assign", null, {
            userId: 123, profile: "MODERATOR", reason: "   "
        }, async () => { requestCount += 1; return response(201, assignmentDto()); }),
        error => error.kind === "invalid-request"
    );
    assert.equal(requestCount, 0);
});
