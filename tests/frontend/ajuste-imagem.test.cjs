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

test("all current user and content image uploads invoke crop and pass the adjusted file to their upload handlers", async () => {
    const [profile, registration, ngo, crop, css, ngoLogic, campaignLogic] = await Promise.all([
        readFile(resolve(__dirname, "../../public/pages/perfil.html"), "utf8"),
        readFile(resolve(__dirname, "../../public/pages/cadastro-osc.html"), "utf8"),
        readFile(resolve(__dirname, "../../public/pages/ong-profile.html"), "utf8"),
        readFile(resolve(__dirname, "../../public/components/ajuste-imagem.js"), "utf8"),
        readFile(resolve(__dirname, "../../public/styles/ajuste-imagem.css"), "utf8"),
        readFile(resolve(__dirname, "../../public/components/ong-profile.js"), "utf8"),
        readFile(resolve(__dirname, "../../public/components/campanhas-gestao-osc.js"), "utf8")
    ]);
    assert.match(profile, /data-preview-shape="circle"/);
    assert.match(registration, /data-aspect-ratio="1"/);
    assert.match(ngo, /components\/ajuste-imagem\.js/);
    const fileInputs = [...ngo.matchAll(/<input\b[^>]*type="file"[^>]*>/g)].map(match => match[0]);
    const expectedUploads = ["input-logo-osc", "post-imagem-arquivo-osc", "oportunidade-imagem-arquivo-osc", "campanha-imagem"];
    assert.equal(fileInputs.length, 6, "the page also contains two non-image evidence uploads");
    const imageInputs = fileInputs.filter(markup => /accept="image\/jpeg,image\/png,image\/webp"/.test(markup));
    assert.equal(imageInputs.length, expectedUploads.length, "all image uploads are accounted for separately from evidence documents");
    for (const id of expectedUploads) {
        const input = imageInputs.find(markup => markup.includes(`id="${id}"`));
        assert.ok(input, `expected upload input ${id}`);
        assert.match(input, /data-image-adjustment/);
        assert.match(input, /data-aspect-ratio="(?:1|1\.7777778)"/);
        assert.match(input, /data-image-feedback=/);
    }
    const evidenceInputs = fileInputs.filter(markup => /id="arquivo-evidencia-/.test(markup));
    assert.equal(evidenceInputs.length, 2);
    for (const input of evidenceInputs) {
        assert.match(input, /accept="application\/pdf,image\/jpeg,image\/png"/);
        assert.doesNotMatch(input, /data-image-adjustment/);
    }
    assert.match(crop, /querySelectorAll\("input\[type=file\]\[data-image-adjustment\]"\)/);
    assert.match(crop, /input\.dispatchEvent\(new Event\("change", \{ bubbles: true \}\)\)/);
    assert.match(ngoLogic, /input\.addEventListener\("change", async \(\) => \{[\s\S]*FormData\(\)[\s\S]*form\.append\("logo", file\)/);
    assert.match(ngoLogic, /estado\.arquivo = arquivo/);
    assert.match(ngoLogic, /if \(arquivoImagem\) dados\.append\("imagem", arquivoImagem/);
    assert.match(campaignLogic, /image\.addEventListener\("change", \(\) => renderPreview/);
    assert.match(campaignLogic, /if \(image\.files\?\.\[0\]\) data\.append\("imagem", image\.files\[0\]/);
    assert.match(ngoLogic, /file\.accept = "application\/pdf,image\/jpeg,image\/png"/,
        "completion certificates are documents and do not go through the image cropper");
    assert.match(crop, /addEventListener\("pointermove"/);
    assert.match(crop, /addEventListener\("keydown"/);
    assert.match(css, /@media \(max-width: 600px\)/);
});
