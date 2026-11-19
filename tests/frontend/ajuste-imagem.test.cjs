const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

async function loadLogic() {
    const source = await readFile(resolve(__dirname, "../../public/components/ajuste-imagem-logic.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("crop geometry follows target ratio, clamps panning and never upscales source pixels", async () => {
    const { calcularRecorte } = await loadLogic();
    const landscape = calcularRecorte({ sourceWidth: 2400, sourceHeight: 1600, aspectRatio: 16 / 9,
        zoom: 1, panX: 9000, panY: -9000, viewWidth: 720 });
    assert.ok(Math.abs(landscape.width / landscape.height - 16 / 9) < 0.001);
    assert.ok(landscape.x >= 0 && landscape.y >= 0);
    assert.ok(landscape.x + landscape.width <= 2400 + 0.001);
    assert.ok(landscape.y + landscape.height <= 1600 + 0.001);
    assert.ok(landscape.outputWidth <= landscape.width + 1);
    assert.ok(landscape.outputHeight <= landscape.height + 1);

    const portrait = calcularRecorte({ sourceWidth: 1200, sourceHeight: 1800, aspectRatio: 1,
        zoom: 1.5, panX: -4000, panY: 4000, viewWidth: 400 });
    assert.ok(Math.abs(portrait.width / portrait.height - 1) < 0.001);
});

test("input formats normalize WebP to JPEG and preserve PNG", async () => {
    const { formatoSaidaImagem } = await loadLogic();
    assert.deepEqual(formatoSaidaImagem("image/webp"), { mimeType: "image/jpeg", extension: "jpg" });
    assert.deepEqual(formatoSaidaImagem("image/png"), { mimeType: "image/png", extension: "png" });
});

test("all current user and content upload entry points invoke the accessible adjustment dialog", async () => {
    const [profile, registration, ngo, crop, css] = await Promise.all([
        readFile(resolve(__dirname, "../../public/pages/perfil.html"), "utf8"),
        readFile(resolve(__dirname, "../../public/pages/cadastro-osc.html"), "utf8"),
        readFile(resolve(__dirname, "../../public/pages/ong-profile.html"), "utf8"),
        readFile(resolve(__dirname, "../../public/components/ajuste-imagem.js"), "utf8"),
        readFile(resolve(__dirname, "../../public/styles/ajuste-imagem.css"), "utf8")
    ]);
    assert.match(profile, /data-preview-shape="circle"/);
    assert.match(registration, /data-aspect-ratio="1"/);
    assert.match(ngo, /data-aspect-ratio="1\.7777778"/);
    assert.equal((ngo.match(/data-image-adjustment/g) || []).length, 3);
    assert.match(crop, /addEventListener\("pointermove"/);
    assert.match(crop, /addEventListener\("keydown"/);
    assert.match(css, /@media \(max-width: 600px\)/);
});
