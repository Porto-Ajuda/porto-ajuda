const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const read = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("admin entity and appeals areas are permission-gated and donations are explicitly unavailable", async () => {
    const [html, admin, controller, permission, catalog] = await Promise.all([
        read("public/admin/index.html"), read("public/components/admin.js"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/controller/ModerationController.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/admin/AdminPermissionCatalog.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/admin/AdminPermission.java")
    ]);
    for (const category of ["OSC", "PUBLICATION", "VOLUNTEERING", "USER"]) assert.match(html, new RegExp(`value="${category}"`));
    assert.match(html, /value="DONATIONS" disabled[^>]*>Doações[^<]*indisponível/);
    assert.match(admin, /permission: "ENTITY_READ"/);
    assert.match(admin, /permission: "APPEAL_READ"/);
    assert.match(admin, /API_URL \+ "\/admin\/oscs\/" \+ encodeURIComponent\(item\.id\) \+ "\/status"/);
    assert.match(admin, /Analisar den\u00fancias/);
    assert.match(admin, /ORG_RESTORE_ARCHIVED/);
    assert.match(controller, /\/admin\/moderation\/appeals/);
    assert.match(permission, /AdminProfile\.SUPER_ADMIN, Set\.copyOf\(EnumSet\.allOf/);
    assert.match(permission, /AdminPermission\.POINTS_READ/);
    assert.match(catalog, /POINTS_ADJUST/);
});

test("appeal review sends one decision with bounded points and backend forbids self-review", async () => {
    const [admin, request, adjustment, service] = await Promise.all([
        read("public/components/admin.js"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/dto/request/RequestModerationAppealDecisionDTO.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/dto/request/RequestModerationPointAdjustmentDTO.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/service/ModerationPointsService.java")
    ]);
    assert.match(admin, /pontosRevertidos/);
    assert.match(admin, /pointsRemaining/);
    assert.match(admin, /avaliar a pr\\u00f3pria decis\\u00e3o/);
    assert.match(service, /actor\.userId\(\)==appeal\.originalReviewerId\(\)/);
    assert.match(request, /Decision \{ REJECT, PARTIAL, ACCEPT \}/);
    assert.match(adjustment, /UUID referenceKey/);
});

test("report decisions separate point penalties from visibility and expose preventive hide and exact restore", async () => {
    const [admin, service, repository, controller] = await Promise.all([
        read("public/components/admin.js"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/service/ContentReportService.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/repository/ContentReportJdbcRepository.java"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/controller/ContentReportController.java")
    ]);
    assert.match(admin, /restringirVisibilidade: visibility\?\.checked === true/);
    assert.match(admin, /ocultacao-preventiva/);
    assert.match(admin, /bloqueios\/.*\/restauracao/);
    assert.match(service, /request\.restringirVisibilidade\(\)/);
    assert.match(repository, /createPreventiveRestriction/);
    assert.match(repository, /restoreRestriction/);
    assert.match(controller, /ocultacao-preventiva/);
    assert.match(controller, /bloqueios\/\{blockId\}\/restauracao/);
});

test("entity report review reuses the same decision flow after loading feature capabilities", async () => {
    const admin = await read("public/components/admin.js");
    const entityBranch = admin.slice(admin.indexOf('if (requestedPage === "entities")'), admin.indexOf('if (requestedPage === "appeals")'));
    assert.match(entityBranch, /await carregarCapacidadePontos\(controller\.signal\)[\s\S]*await carregarEntidades/);
    assert.match(admin, /review\.addEventListener\("click",async\(\)=>[\s\S]*abrirDetalheDenuncia\(/);
    assert.match(admin, /renderizarDetalheDenuncia\(await response\.json\(\), item, panelTarget\)/);
    assert.match(admin, /confirmarDecisaoDenuncia\(item, decision, button\)/);
});

test("moderation UI presents localized appeal states and admin profiles", async () => {
    const admin = await read("public/components/admin.js");
    assert.match(admin, /appealStatusLabels = Object\.freeze\(\{ PENDING: "Pendente"/);
    assert.match(admin, /appealStatusLabels\[item\.status\]\|\|"Status desconhecido"/);
    assert.match(admin, /profileLabels\[entry\.perfil\]\s*\|\|\s*entry\.perfil/);
});

test("manual points adjustment uses its own vertical responsive dialog styles", async () => {
    const [admin, css] = await Promise.all([
        read("public/components/admin.js"), read("public/styles/admin.css")
    ]);
    assert.match(admin, /admin-points-adjustment-dialog__card/);
    assert.match(css, /\.admin-points-adjustment-dialog__card\s*\{\s*display:\s*grid/);
    assert.match(css, /\.admin-points-adjustment-dialog__card input\[type="number"\]/);
    assert.match(css, /\.admin-points-adjustment-dialog__card textarea\s*\{[^}]*min-height:\s*140px/s);
    assert.match(css, /@media \(max-width:\s*520px\)[\s\S]*?admin-points-adjustment-dialog__card input/);
});

test("user and NGO point panels use authenticated scoped endpoints and appeal API", async () => {
    const [profile, ngoHtml, ngoScript, module] = await Promise.all([
        read("public/pages/perfil.html"), read("public/pages/ong-profile.html"),
        read("public/components/ong-profile.js"), read("public/components/moderation-profile.js")
    ]);
    assert.match(profile, /id="moderacao-perfil-usuario"/);
    assert.match(profile, /id="moderacao-organizacoes-usuario"/);
    assert.match(ngoHtml, /id="moderacao-configuracoes-osc"[^>]+hidden/);
    assert.match(ngoScript, /cargoAtual/);
    assert.match(module, /fetchAutenticado\("\/moderation\/appeals"/);
    assert.match(module, /\/moderation\/me/);
    assert.match(module, /fetchAutenticado\("\/moderation\/organizations"/);
    assert.match(module, /\/osc\/.*\/moderation\/restriction/);
    assert.match(module, /hasPointHistory === true/);
    assert.match(module, /aria-expanded/);
    assert.match(module, /state\.requests\.get\(id\)/);
    assert.doesNotMatch(module, /state\.details/);
    assert.match(module, /setWriteFormsState\("checking"\)/);
    assert.match(module, /countdownText/);
    assert.match(module, /decisionHistoryId/);
    assert.match(module, /pontos permanecem/i);
    assert.doesNotMatch(module, /window\.prompt|window\.alert/);
});
