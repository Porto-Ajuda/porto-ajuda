const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const { test } = require("node:test");
const assert = require("node:assert/strict");

async function loadAccessModule() {
    const source = await readFile(resolve(__dirname, "../../public/components/ong-profile-access.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

function response(body, status = 200) {
    return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function activeProfile() {
    return { id: 9, nome: "OSC Pública", status: "ATIVA", arquivada: false };
}

test("anonymous visitor loads an active NGO from the public endpoint without requesting private data", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    let privateRequested = false;
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(activeProfile()),
        listarOngsDoUsuario: async () => { throw new Error("anonymous visitor must not be checked"); },
        buscarPerfilPrivado: async () => { privateRequested = true; return response(null, 403); }
    });

    assert.equal(acesso.origem, "publica");
    assert.equal(acesso.perfil.id, 9);
    assert.equal(privateRequested, false);
});

test("authenticated non-member can open an active public NGO without a private request", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    let privateRequested = false;
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(activeProfile()),
        listarOngsDoUsuario: async () => [],
        buscarPerfilPrivado: async () => { privateRequested = true; return response(null, 403); }
    });

    assert.equal(acesso.origem, "publica");
    assert.equal(privateRequested, false);
});

test("NGO member sees its active public profile and member permissions are checked separately", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    let privateRequested = false;
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(activeProfile()),
        listarOngsDoUsuario: async () => [{ id: 9, cargo: "ADMINISTRADOR" }],
        buscarPerfilPrivado: async () => { privateRequested = true; return response(null, 403); }
    });

    assert.equal(acesso.origem, "publica");
    assert.equal(privateRequested, false);
    const profileSource = await readFile(resolve(__dirname, "../../public/components/ong-profile.js"), "utf8");
    assert.match(profileSource, /void carregarPermissoes\(id, acesso\.organizacoes\)/);
});

test("Stripe setup is tied to the selected creator NGO and donations require a registered Stripe account", async () => {
    const profileSource = await readFile(resolve(__dirname, "../../public/components/ong-profile.js"), "utf8");
    const html = await readFile(resolve(__dirname, "../../public/pages/ong-profile.html"), "utf8");
    const settingsPage = await readFile(resolve(__dirname, "../../public/pages/perfil.html"), "utf8");

    assert.match(profileSource, /vinculo\?\.cargo !== "CRIADOR"/);
    assert.match(profileSource, /account-link\?oscId=.*oscAtual\.id/);
    assert.match(profileSource, /osc\.temContaStripe !== true/);
    assert.match(html, /id="criar-stripe"[^>]*hidden/);
    assert.match(html, /id="efetuar-doacao"[^>]*hidden/);
    assert.doesNotMatch(settingsPage, /id="criar-stripe"/);
});

test("NGO administrator reaches private profile only after membership confirmation", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    const callOrder = [];
    const privateProfile = { ...activeProfile(), status: "INATIVA", email: "private@example.invalid" };
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => { callOrder.push("public"); return response(null, 404); },
        listarOngsDoUsuario: async () => {
            callOrder.push("membership");
            return [{ id: 9, cargo: "ADMINISTRADOR" }];
        },
        buscarPerfilPrivado: async () => { callOrder.push("private"); return response(privateProfile); }
    });

    assert.deepEqual(callOrder, ["public", "membership", "private"]);
    assert.equal(acesso.origem, "privada");
    assert.equal(acesso.perfil.email, "private@example.invalid");
});

test("platform admin reaches private profile only with the explicit private-read capability", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    const calls = [];
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(null, 404),
        listarOngsDoUsuario: async () => [],
        podeLerPerfilPrivadoComoAdmin: async () => {
            calls.push("capability");
            return true;
        },
        buscarPerfilPrivado: async () => { throw new Error("not an NGO member"); },
        buscarPerfilPrivadoComoAdmin: async () => {
            calls.push("admin-private");
            return response({ ...activeProfile(), status: "INATIVA" });
        }
    });

    assert.deepEqual(calls, ["capability", "admin-private"]);
    assert.equal(acesso.origem, "privada");
});

test("global admin role without explicit capability does not request private NGO data", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    let privateRequested = false;
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(null, 404),
        listarOngsDoUsuario: async () => [],
        podeLerPerfilPrivadoComoAdmin: async () => false,
        buscarPerfilPrivado: async () => { privateRequested = true; return response(activeProfile()); },
        buscarPerfilPrivadoComoAdmin: async () => { privateRequested = true; return response(activeProfile()); }
    });

    assert.equal(acesso.naoEncontrado, true);
    assert.equal(privateRequested, false);
});

test("inactive NGO stays unavailable to non-member visitors", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    let privateRequested = false;
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(null, 404),
        listarOngsDoUsuario: async () => [],
        buscarPerfilPrivado: async () => { privateRequested = true; return response(activeProfile()); }
    });

    assert.equal(acesso.naoEncontrado, true);
    assert.equal(privateRequested, false);
});

test("a denied optional private endpoint cannot block a public profile", async () => {
    const { carregarPerfilOngAcessivel } = await loadAccessModule();
    const acesso = await carregarPerfilOngAcessivel(9, {
        buscarPerfilPublico: async () => response(activeProfile()),
        listarOngsDoUsuario: async () => null,
        buscarPerfilPrivado: async () => response(null, 403)
    });

    assert.equal(acesso.origem, "publica");
    assert.equal(acesso.perfil.nome, "OSC Pública");
});
