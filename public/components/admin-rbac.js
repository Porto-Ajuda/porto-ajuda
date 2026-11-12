const PERFIS_ADMINISTRATIVOS = new Set([
    "SUPER_ADMIN",
    "PLATFORM_ADMIN",
    "MODERATOR",
    "AUDITOR"
]);

export class AdminRequestError extends Error {
    constructor(kind, status = null) {
        super(kind);
        this.name = "AdminRequestError";
        this.kind = kind;
        this.status = status;
    }
}

export function validarCapabilities(payload) {
    if (!payload || typeof payload !== "object" || Array.isArray(payload)
            || !PERFIS_ADMINISTRATIVOS.has(payload.profile)
            || !Array.isArray(payload.permissions)
            || payload.permissions.length === 0
            || payload.permissions.length > 64) {
        throw new AdminRequestError("invalid-response");
    }

    const permissions = payload.permissions;
    if (!permissions.every(permission => typeof permission === "string"
            && /^[A-Z][A-Z0-9_]{1,63}$/.test(permission))
            || new Set(permissions).size !== permissions.length) {
        throw new AdminRequestError("invalid-response");
    }

    return Object.freeze({
        profile: payload.profile,
        permissions: Object.freeze([...permissions])
    });
}

export function createAdminAuthorizationStore() {
    let context = null;
    let permissionSet = new Set();

    return Object.freeze({
        get current() {
            return context;
        },
        hasPermission(permission) {
            return permissionSet.has(permission);
        },
        set(payload) {
            context = validarCapabilities(payload);
            permissionSet = new Set(context.permissions);
            return context;
        },
        clear() {
            context = null;
            permissionSet.clear();
        }
    });
}

export function filtrarNavegacao(entries, authorization) {
    return entries.filter(entry => entry && typeof entry.permission === "string"
        && authorization.hasPermission(entry.permission));
}

export function renderPermissionNavigation(navigation, entries, authorization) {
    const visible = filtrarNavegacao(entries, authorization);
    const visibleIds = new Set(visible.map(entry => entry.id));
    entries.forEach(entry => {
        if (entry.element) entry.element.hidden = !visibleIds.has(entry.id);
    });
    navigation.hidden = visible.length === 0;
    return visible;
}

export function clearAdminAuthorizationState(authorization, elements) {
    authorization.clear();
    elements.navigation.hidden = true;
    elements.profile.hidden = true;
    elements.profileName.textContent = "";
}

export function clearProtectedDashboard(elements, { keepLogout = false } = {}) {
    elements.metrics.forEach(element => {
        element.textContent = "—";
    });
    elements.activityList.replaceChildren();
    elements.activityList.hidden = true;
    elements.activityState.textContent = "";
    elements.activityState.hidden = true;
    elements.generatedAt.textContent = "";
    elements.content.hidden = true;
    elements.refresh.hidden = true;
    if (!keepLogout) elements.logout.hidden = true;
}

async function fetchAdminResponse(url, fetchImpl, signal, options = {}) {
    let response;
    try {
        response = await fetchImpl(url, {
            ...options,
            method: options.method ?? "GET",
            credentials: "include",
            cache: "no-store",
            headers: { Accept: "application/json", ...(options.headers ?? {}) },
            signal
        });
    } catch (error) {
        if (error?.name === "AbortError") throw error;
        throw new AdminRequestError("network");
    }

    if (response.status === 401) throw new AdminRequestError("unauthorized", 401);
    if (response.status === 403) throw new AdminRequestError("forbidden", 403);
    return response;
}

export async function fetchAdminCapabilities(apiUrl, fetchImpl = fetch, signal) {
    const response = await fetchAdminResponse(
        `${apiUrl}/admin/auth/capabilities`, fetchImpl, signal);
    if (response.status !== 200 || !response.ok) {
        throw new AdminRequestError("unavailable", response.status);
    }

    let payload;
    try {
        payload = await response.json();
    } catch {
        throw new AdminRequestError("invalid-response", response.status);
    }
    return validarCapabilities(payload);
}

export async function fetchAdminSession(apiUrl, fetchImpl = fetch, signal, now = () => performance.now()) {
    const startedAt = now();
    const response = await fetchAdminResponse(
        `${apiUrl}/admin/auth/session`, fetchImpl, signal);
    if (response.status !== 204 || !response.ok) {
        throw new AdminRequestError("unavailable", response.status);
    }

    const serverTime = Date.parse(response.headers.get("X-PA-ADMIN-SERVER-TIME"));
    const absoluteExpiry = Date.parse(response.headers.get("X-PA-ADMIN-ABSOLUTE-EXPIRES-AT"));
    const idleExpiry = Date.parse(response.headers.get("X-PA-ADMIN-IDLE-EXPIRES-AT"));
    if (![serverTime, absoluteExpiry, idleExpiry].every(Number.isFinite)) {
        throw new AdminRequestError("invalid-response", response.status);
    }

    const remainingMs = Math.min(absoluteExpiry, idleExpiry) - serverTime
        - (now() - startedAt) - 1500;
    if (remainingMs <= 0) throw new AdminRequestError("unauthorized", 401);
    return { remainingMs };
}

export async function fetchAdminDashboard(apiUrl, fetchImpl = fetch, signal) {
    const response = await fetchAdminResponse(
        `${apiUrl}/admin/dashboard/overview`, fetchImpl, signal);
    if (response.status !== 200 || !response.ok) {
        throw new AdminRequestError("unavailable", response.status);
    }

    let overview;
    try {
        overview = await response.json();
    } catch {
        throw new AdminRequestError("invalid-response", response.status);
    }

    const metricNames = [
        "registeredUsers",
        "activeOrganizations",
        "inactiveOrganizations",
        "archivedOrganizations",
        "publications",
        "volunteerOpportunities"
    ];
    if (!metricNames.every(name => Number.isSafeInteger(overview?.[name]) && overview[name] >= 0)
            || typeof overview.recentActivityAvailable !== "boolean"
            || !Array.isArray(overview.recentActivity)
            || !Number.isFinite(Date.parse(overview.generatedAt))) {
        throw new AdminRequestError("invalid-response", response.status);
    }
    return overview;
}

function validarAtribuicao(assignment) {
    const profiles = PERFIS_ADMINISTRATIVOS;
    const validOptionalUserId = value => value === null
        || (Number.isSafeInteger(value) && value > 0 && value <= 2147483647);
    const validInstant = value => typeof value === "string" && Number.isFinite(Date.parse(value));
    if (!assignment || typeof assignment !== "object" || Array.isArray(assignment)
            || typeof assignment.id !== "string"
            || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(assignment.id)
            || !Number.isSafeInteger(assignment.userId) || assignment.userId < 1 || assignment.userId > 2147483647
            || !profiles.has(assignment.profile)
            || !["ACTIVE", "REVOKED"].includes(assignment.status)
            || !validInstant(assignment.grantedAt)
            || !validOptionalUserId(assignment.grantedByUserId)
            || !validInstant(assignment.lastModifiedAt)
            || !validOptionalUserId(assignment.lastModifiedByUserId)
            || (assignment.revokedAt !== null && !validInstant(assignment.revokedAt))) {
        throw new AdminRequestError("invalid-response");
    }
    return Object.freeze({
        id: assignment.id,
        userId: assignment.userId,
        profile: assignment.profile,
        status: assignment.status,
        grantedAt: assignment.grantedAt,
        grantedByUserId: assignment.grantedByUserId,
        lastModifiedAt: assignment.lastModifiedAt,
        lastModifiedByUserId: assignment.lastModifiedByUserId,
        revokedAt: assignment.revokedAt,
        reason: typeof assignment.reason === "string" ? assignment.reason : null
    });
}

export async function fetchAdminAssignments(apiUrl, limit, offset, fetchImpl = fetch, signal) {
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 200
            || !Number.isSafeInteger(offset) || offset < 0) {
        throw new AdminRequestError("invalid-request");
    }
    const query = new URLSearchParams({ limit: String(limit), offset: String(offset) });
    const response = await fetchAdminResponse(
        `${apiUrl}/admin/assignments?${query}`, fetchImpl, signal);
    if (response.status !== 200 || !response.ok) {
        throw new AdminRequestError(response.status === 503 ? "unavailable" : "unexpected-response", response.status);
    }
    let payload;
    try {
        payload = await response.json();
    } catch {
        throw new AdminRequestError("invalid-response", response.status);
    }
    if (!Array.isArray(payload) || payload.length > limit) {
        throw new AdminRequestError("invalid-response", response.status);
    }
    return payload.map(validarAtribuicao);
}

function validarMutationPayload(operation, assignmentId, payload) {
    if (!payload || typeof payload !== "object" || Array.isArray(payload)
            || typeof payload.reason !== "string" || !payload.reason.trim()
            || payload.reason.length > 2000) {
        throw new AdminRequestError("invalid-request");
    }
    if (operation === "assign") {
        if (!Number.isSafeInteger(payload.userId) || payload.userId < 1 || payload.userId > 2147483647
                || !PERFIS_ADMINISTRATIVOS.has(payload.profile)) {
            throw new AdminRequestError("invalid-request");
        }
        return;
    }
    if (typeof assignmentId !== "string"
            || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(assignmentId)) {
        throw new AdminRequestError("invalid-request");
    }
    if (operation === "change-profile" && !PERFIS_ADMINISTRATIVOS.has(payload.profile)) {
        throw new AdminRequestError("invalid-request");
    }
}

export async function mutateAdminAssignment(apiUrl, operation, assignmentId, payload, fetchImpl = fetch) {
    validarMutationPayload(operation, assignmentId, payload);
    let method;
    let path;
    let expectedStatus;
    if (operation === "assign") {
        method = "POST";
        path = "/admin/assignments";
        expectedStatus = 201;
    } else {
        if (typeof assignmentId !== "string"
                || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(assignmentId)) {
            throw new AdminRequestError("invalid-request");
        }
        const encodedId = encodeURIComponent(assignmentId);
        if (operation === "change-profile") {
            method = "PATCH";
            path = `/admin/assignments/${encodedId}/profile`;
            expectedStatus = 200;
        } else if (operation === "revoke") {
            method = "POST";
            path = `/admin/assignments/${encodedId}/revoke`;
            expectedStatus = 200;
        } else {
            throw new AdminRequestError("invalid-request");
        }
    }

    const csrfResponse = await fetchAdminResponse(`${apiUrl}/usuario/csrf`, fetchImpl);
    if (csrfResponse.status !== 200 || !csrfResponse.ok) {
        throw new AdminRequestError("csrf-unavailable", csrfResponse.status);
    }
    let csrf;
    try {
        csrf = await csrfResponse.json();
    } catch {
        throw new AdminRequestError("csrf-invalid");
    }
    if (!csrf || typeof csrf.headerName !== "string" || !/^[A-Za-z0-9-]+$/.test(csrf.headerName)
            || typeof csrf.token !== "string" || !csrf.token) {
        throw new AdminRequestError("csrf-invalid");
    }

    // State-changing operations are deliberately sent exactly once. Never retry them automatically.
    const response = await fetchAdminResponse(`${apiUrl}${path}`, fetchImpl, undefined, {
        method,
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify(payload)
    });
    if (response.status !== expectedStatus) {
        const kind = response.status === 400 ? "validation"
            : response.status === 404 ? "not-found"
                : response.status === 409 ? "conflict"
                    : response.status >= 500 ? "unavailable" : "unexpected-response";
        throw new AdminRequestError(kind, response.status);
    }

    let assignment;
    try {
        assignment = await response.json();
    } catch {
        throw new AdminRequestError("invalid-response", response.status);
    }
    return validarAtribuicao(assignment);
}
