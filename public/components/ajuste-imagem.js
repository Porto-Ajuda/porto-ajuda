import { calcularRecorte, formatoSaidaImagem } from "./ajuste-imagem-logic.js";

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const SOURCE_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const adjustedInputs = new WeakSet();
const acceptedFiles = new WeakMap();
let active = null;

function criarDialogo() {
    const dialog = document.createElement("dialog");
    dialog.className = "dialogo-ajuste-imagem";
    dialog.setAttribute("aria-labelledby", "titulo-ajuste-imagem");
    dialog.setAttribute("aria-describedby", "descricao-ajuste-imagem estado-ajuste-imagem");
    dialog.innerHTML = `
        <section class="ajuste-imagem-cartao">
          <header class="ajuste-imagem-cabecalho">
            <div><h2 id="titulo-ajuste-imagem">Ajustar imagem</h2><p id="descricao-ajuste-imagem">Arraste para reposicionar, ajuste o zoom ou use as setas do teclado.</p></div>
            <button type="button" class="ajuste-imagem-fechar" data-cancelar aria-label="Cancelar ajuste da imagem">×</button>
          </header>
          <div class="ajuste-imagem-viewport"><canvas tabindex="0" role="img" aria-label="Pré-visualização do recorte. Use as setas para reposicionar a imagem."></canvas></div>
          <div class="ajuste-imagem-controles">
            <label for="zoom-ajuste-imagem">Zoom</label>
            <input id="zoom-ajuste-imagem" type="range" min="1" max="3" step="0.01" value="1" aria-label="Zoom da imagem">
            <button type="button" class="ajuste-imagem-botao ajuste-imagem-botao--secundario" data-resetar>Restaurar enquadramento</button>
          </div>
          <p id="estado-ajuste-imagem" class="ajuste-imagem-estado" role="status" aria-live="polite"></p>
          <footer class="ajuste-imagem-acoes">
            <button type="button" class="ajuste-imagem-botao ajuste-imagem-botao--secundario" data-cancelar>Cancelar</button>
            <button type="button" class="ajuste-imagem-botao ajuste-imagem-botao--primario" data-aplicar>Aplicar recorte</button>
          </footer>
        </section>`;
    document.body.append(dialog);
    return dialog;
}

const dialog = criarDialogo();
const canvas = dialog.querySelector("canvas");
const context = canvas.getContext("2d");
const viewport = dialog.querySelector(".ajuste-imagem-viewport");
const zoomInput = dialog.querySelector("#zoom-ajuste-imagem");
const status = dialog.querySelector("#estado-ajuste-imagem");
const applyButton = dialog.querySelector("[data-aplicar]");
let bitmap = null;
let sourceFile = null;
let view = null;
let zoom = 1;
let panX = 0;
let panY = 0;
let pointer = null;
let resolutionObserver = null;
let resizeFallback = null;

function avisar(input, message, error = false) {
    const id = input?.dataset.imageFeedback;
    const target = id ? document.getElementById(id) : input?.closest("[data-image-field]")?.querySelector("[role=status]");
    if (target) {
        target.textContent = message;
        target.dataset.kind = error ? "erro" : "";
    }
}

function getAspect(input) {
    const value = Number(input.dataset.aspectRatio || 1);
    return Number.isFinite(value) && value > 0.3 && value < 4 ? value : 1;
}

function dimensoesExibicao() {
    const bounds = viewport.getBoundingClientRect();
    const width = Math.max(1, bounds.width);
    return { width, height: Math.max(1, width / view.aspect) };
}

function clampPan() {
    const { width } = dimensoesExibicao();
    const crop = calcularRecorte({ sourceWidth: bitmap.width, sourceHeight: bitmap.height,
        aspectRatio: view.aspect, zoom, panX, panY, viewWidth: width });
    panX = crop.panX; panY = crop.panY;
}

function desenhar() {
    if (!bitmap || !view) return;
    const { width, height } = dimensoesExibicao();
    const dpr = Math.max(1, window.devicePixelRatio || 1);
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, width, height);
    clampPan();
    const scale = Math.max(width / bitmap.width, height / bitmap.height) * zoom;
    const drawnWidth = bitmap.width * scale;
    const drawnHeight = bitmap.height * scale;
    context.drawImage(bitmap,
        (width - drawnWidth) / 2 + panX,
        (height - drawnHeight) / 2 + panY,
        drawnWidth,
        drawnHeight);
}

async function abrir(input, file) {
    if (!SOURCE_TYPES.has(file.type)) throw new Error("Selecione uma imagem JPEG, PNG ou WebP.");
    if (file.size <= 0 || file.size > MAX_FILE_BYTES) throw new Error("A imagem precisa ter até 10 MB.");
    if (!context) throw new Error("Este navegador não oferece suporte ao ajuste de imagem. Tente outro navegador ou dispositivo.");
    sourceFile = file;
    view = { aspect: getAspect(input), circle: input.dataset.previewShape === "circle" };
    zoom = 1; panX = 0; panY = 0;
    zoomInput.value = "1";
    dialog.classList.toggle("dialogo-ajuste-imagem--circular", view.circle);
    dialog.querySelector("#titulo-ajuste-imagem").textContent = input.dataset.imageTitle || "Ajustar imagem";
    status.textContent = "Carregando pré-visualização…";
    applyButton.disabled = true;

    try {
        if (typeof createImageBitmap === "function") {
            try { bitmap = await createImageBitmap(file, { imageOrientation: "from-image" }); }
            catch { bitmap = await createImageBitmap(file); }
        } else {
            const objectUrl = URL.createObjectURL(file);
            try {
                const image = new Image();
                image.decoding = "async";
                image.src = objectUrl;
                await image.decode();
                bitmap = image;
            } finally { URL.revokeObjectURL(objectUrl); }
        }
        if (!bitmap.width || !bitmap.height || bitmap.width > 6000 || bitmap.height > 6000
                || bitmap.width * bitmap.height > 12_000_000) {
            throw new Error("A imagem tem dimensões acima do limite permitido. Escolha uma imagem menor.");
        }
        status.textContent = "Ajuste o recorte. A imagem não será ampliada além da resolução original.";
        applyButton.disabled = false;
        desenhar();
        if ("ResizeObserver" in window) {
            resolutionObserver = new ResizeObserver(desenhar);
            resolutionObserver.observe(viewport);
        } else {
            resizeFallback = desenhar;
            window.addEventListener("resize", resizeFallback);
        }
        dialog.showModal();
        zoomInput.focus();
    } catch (error) {
        bitmap?.close?.(); bitmap = null;
        sourceFile = null; view = null;
        throw error;
    }
}

function liberarPrevia() {
    resolutionObserver?.disconnect();
    resolutionObserver = null;
    if (resizeFallback) window.removeEventListener("resize", resizeFallback);
    resizeFallback = null;
    bitmap?.close?.();
    bitmap = null;
    sourceFile = null;
    view = null;
    pointer = null;
    canvas.width = 0; canvas.height = 0;
}

function restaurarInput(input) {
    input.value = "";
    const previous = acceptedFiles.get(input);
    if (previous) {
        const transfer = new DataTransfer();
        transfer.items.add(previous);
        input.files = transfer.files;
    }
    input.focus({ preventScroll: true });
}

function cancelar() {
    if (active) restaurarInput(active);
    dialog.close("cancelar");
}

function criarArquivoRecortado(input) {
    const { width, height } = dimensoesExibicao();
    const crop = calcularRecorte({ sourceWidth: bitmap.width, sourceHeight: bitmap.height,
        aspectRatio: view.aspect, zoom, panX, panY, viewWidth: width });
    const output = document.createElement("canvas");
    output.width = crop.outputWidth;
    output.height = crop.outputHeight;
    const outputType = formatoSaidaImagem(sourceFile.type).mimeType;
    const outputContext = output.getContext("2d", { alpha: outputType === "image/png" });
    if (outputType === "image/jpeg") {
        outputContext.fillStyle = "#ffffff";
        outputContext.fillRect(0, 0, output.width, output.height);
    }
    outputContext.drawImage(bitmap, crop.x, crop.y, crop.width, crop.height, 0, 0, output.width, output.height);
    return new Promise((resolve, reject) => output.toBlob(blob => {
        if (!blob) { reject(new Error("Não foi possível preparar a imagem. Tente outra.")); return; }
        const extension = formatoSaidaImagem(sourceFile.type).extension;
        const baseName = sourceFile.name.replace(/\.[^.]+$/, "") || "imagem";
        resolve(new File([blob], `${baseName}-ajustada.${extension}`, { type: outputType, lastModified: Date.now() }));
    }, outputType, outputType === "image/jpeg" ? 0.92 : undefined));
}

document.querySelectorAll("input[type=file][data-image-adjustment]").forEach(input => {
    input.addEventListener("change", async event => {
        if (adjustedInputs.has(input)) { adjustedInputs.delete(input); return; }
        const selected = input.files?.[0];
        if (!selected) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        active = input;
        input.value = "";
        try {
            await abrir(input, selected);
        } catch (error) {
            restaurarInput(input);
            avisar(input, error.message || "Não foi possível abrir esta imagem.", true);
            active = null;
        }
    }, true);
});

dialog.querySelectorAll("[data-cancelar]").forEach(button => button.addEventListener("click", cancelar));
dialog.querySelector("[data-resetar]").addEventListener("click", () => {
    zoom = 1; panX = 0; panY = 0; zoomInput.value = "1"; desenhar(); canvas.focus();
});
zoomInput.addEventListener("input", () => { zoom = Number(zoomInput.value) || 1; desenhar(); });
canvas.addEventListener("pointerdown", event => {
    pointer = { id: event.pointerId, x: event.clientX, y: event.clientY };
    canvas.setPointerCapture(event.pointerId);
});
canvas.addEventListener("pointermove", event => {
    if (!pointer || pointer.id !== event.pointerId) return;
    panX += event.clientX - pointer.x; panY += event.clientY - pointer.y;
    pointer.x = event.clientX; pointer.y = event.clientY; desenhar();
});
for (const name of ["pointerup", "pointercancel", "lostpointercapture"]) canvas.addEventListener(name, () => { pointer = null; });
canvas.addEventListener("keydown", event => {
    const amount = event.shiftKey ? 40 : 12;
    if (event.key === "ArrowLeft") panX -= amount;
    else if (event.key === "ArrowRight") panX += amount;
    else if (event.key === "ArrowUp") panY -= amount;
    else if (event.key === "ArrowDown") panY += amount;
    else return;
    event.preventDefault(); desenhar();
});
applyButton.addEventListener("click", async () => {
    if (!active || !bitmap || applyButton.disabled) return;
    applyButton.disabled = true;
    try {
        const result = await criarArquivoRecortado(active);
        const transfer = new DataTransfer();
        transfer.items.add(result);
        active.files = transfer.files;
        acceptedFiles.set(active, result);
        adjustedInputs.add(active);
        const input = active;
        avisar(input, "Imagem ajustada. Ela será enviada ao salvar.");
        liberarPrevia();
        dialog.close("aplicada");
        active = null;
        input.dispatchEvent(new Event("change", { bubbles: true }));
    } catch (error) {
        status.textContent = error.message || "Não foi possível preparar a imagem.";
        applyButton.disabled = false;
    }
});
dialog.addEventListener("close", () => {
    if (dialog.returnValue !== "aplicada") liberarPrevia();
    active = null;
});
dialog.addEventListener("keydown", event => {
    if (event.key === "Escape") { event.preventDefault(); cancelar(); }
});
dialog.addEventListener("click", event => { if (event.target === dialog) cancelar(); });
dialog.addEventListener("cancel", event => { event.preventDefault(); cancelar(); });

export function resetarAjusteImagem(input) {
    if (!input) return;
    input.value = "";
    acceptedFiles.delete(input);
}


