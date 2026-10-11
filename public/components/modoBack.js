/* =====================================================
 * modoBack.js
 * Define a URL da API utilizada pelo frontend.
 *
 * A API será sempre o mesmo servidor que está
 * entregando o frontend.
 *
 * Local:
 * http://localhost:8080
 *
 * Produção:
 * https://porto-ajuda.up.railway.app
 * ===================================================== */

"use strict";

/* =====================================================
 * modoBack.js
 * Define a URL da API utilizada pelo frontend.
 * ===================================================== */

"use strict";

export function getAPI_URL() {
    const API_URL = window.location.hostname === "localhost"
        ? "http://localhost:8080"
        : "https://porto-ajuda.up.railway.app";

    console.log("========================================");
    console.log("VERIFICAÇÃO DO BACKEND");
    console.log("========================================");
    console.log("Origem atual:", window.location.origin);
    console.log("API utilizada:", API_URL);

    return API_URL;
}