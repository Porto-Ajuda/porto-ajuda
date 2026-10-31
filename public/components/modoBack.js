
/* =====================================================
 * modoBack.js
 * Define a URL da API utilizada pelo frontend.
 *
 * Local:
 * http://localhost:8080
 *
 * Produção:
 * ${APP_BACKEND_URL}
 * ===================================================== */

"use strict";

export function getAPI_URL() {
    const hostname = window.location.hostname;

    const API_URL = (
        hostname === "localhost" ||
        hostname === "127.0.0.1"
    )
        ? "http://localhost:8080"
        : "${APP_BACKEND_URL}";

    console.log("========================================");
    console.log("VERIFICAÇÃO DO BACKEND");
    console.log("========================================");
    console.log("Origem atual:", window.location.origin);
    console.log("API utilizada:", API_URL);

    return API_URL;
}
