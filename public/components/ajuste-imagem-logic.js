export function calcularRecorte({ sourceWidth, sourceHeight, aspectRatio = 1, zoom = 1, panX = 0, panY = 0, viewWidth }) {
    if (![sourceWidth, sourceHeight, aspectRatio, viewWidth].every(value => Number.isFinite(value) && value > 0)) {
        throw new TypeError("Dimensões de recorte inválidas.");
    }
    const safeZoom = Math.max(1, Math.min(3, Number(zoom) || 1));
    const viewHeight = viewWidth / aspectRatio;
    const scale = Math.max(viewWidth / sourceWidth, viewHeight / sourceHeight) * safeZoom;
    const maxPanX = Math.max(0, (sourceWidth * scale - viewWidth) / 2);
    const maxPanY = Math.max(0, (sourceHeight * scale - viewHeight) / 2);
    const offsetX = Math.max(-maxPanX, Math.min(maxPanX, Number(panX) || 0));
    const offsetY = Math.max(-maxPanY, Math.min(maxPanY, Number(panY) || 0));
    const drawnWidth = sourceWidth * scale;
    const drawnHeight = sourceHeight * scale;
    const x = Math.max(0, Math.min(sourceWidth, ((drawnWidth - viewWidth) / 2 - offsetX) / scale));
    const y = Math.max(0, Math.min(sourceHeight, ((drawnHeight - viewHeight) / 2 - offsetY) / scale));
    const width = Math.min(sourceWidth - x, viewWidth / scale);
    const height = Math.min(sourceHeight - y, viewHeight / scale);
    const outputScale = Math.min(1, 1600 / Math.max(width, height));
    return {
        x, y, width, height, scale, panX: offsetX, panY: offsetY,
        outputWidth: Math.max(1, Math.round(width * outputScale)),
        outputHeight: Math.max(1, Math.round(height * outputScale))
    };
}

export function formatoSaidaImagem(sourceType) {
    return sourceType === "image/png" ? { mimeType: "image/png", extension: "png" }
        : { mimeType: "image/jpeg", extension: "jpg" };
}
