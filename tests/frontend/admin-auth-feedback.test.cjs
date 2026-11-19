const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const helperPath = path.join(__dirname, "../../public/components/admin-auth-feedback.js");
const helperSource = fs.readFileSync(helperPath, "utf8").replace(/^export /gm, "");
const context = {};
vm.runInNewContext(helperSource, context, { filename: helperPath });

test("mapeia códigos públicos de falha para mensagens distintas sem exibir resposta bruta", () => {
  const cases = [
    [401, "INVALID_CODE", /incorreto/],
    [410, "EXPIRED_CODE", /expirou/],
    [409, "CHALLENGE_REPLACED", /substituída/],
    [409, "CODE_ALREADY_USED", /já foi utilizado/],
    [429, "TOO_MANY_ATTEMPTS", /limite de tentativas/],
    [503, "DELIVERY_FAILED", /entregar o código/],
    [401, "INVALID_CHALLENGE", /tentativa não é válida/]
  ];
  for (const [status, code, expected] of cases) {
    const message = context.adminAuthFailureMessage(status, code);
    assert.match(message, expected);
    assert.equal(message.includes("senha-do-banco"), false);
  }
});

test("distingue confirmação de entrega, próximo fator e resposta pendente", () => {
  assert.equal(context.adminAuthNextFactor({ codigo: "DELIVERY_PENDING" }), "DELIVERY");
  assert.equal(context.adminAuthNextFactor({ nextFactor: "EMAIL" }), "EMAIL");
  assert.equal(context.adminAuthNextFactor({ nextFactor: "SMS" }), "SMS");
  assert.equal(context.adminAuthNextFactor({ nextFactor: "unexpected" }), "UNKNOWN");
});
