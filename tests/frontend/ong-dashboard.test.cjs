const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("dashboard presents recorded volunteer hours and uses accepted applications for eligible slots", async () => {
    const [dashboard, service] = await Promise.all([
        read("public/components/ong-dashboard.js"),
        read("backend/src/main/java/com/portoajuda/aplicacao_osc/service/OscDashboardService.java")
    ]);
    assert.match(dashboard, /Participantes concluídos/);
    assert.match(dashboard, /Horas voluntárias registradas/);
    assert.match(dashboard, /applications\.completedHours/);
    assert.match(dashboard, /não inclui carga horária planejada/);
    assert.match(service, /COALESCE\(sum\(c\.horas_realizadas\) FILTER \(WHERE c\.status='CONCLUIDA'\),0\) AS completedHours/);
    assert.match(service, /WHERE status='APROVADA' GROUP BY id_vaga/);
    assert.match(service, /publicly_eligible AND status='ABERTA'/);
    assert.match(service, /data_conclusao IS NULL/);
    assert.match(service, /pendingApplications/);
});
