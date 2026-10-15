
"use strict";

export function getAPI_URL() {
    const host = window.location.hostname;

    const ambienteLocal =
        host === "localhost" ||
        host === "127.0.0.1";

    const API_URL = ambienteLocal
        ? "http://localhost:8080"
        : "https://porto-ajuda-production.up.railway.app";

    console.log("Origem atual:", window.location.origin);
    console.log("API utilizada:", API_URL);

    return API_URL;
}
