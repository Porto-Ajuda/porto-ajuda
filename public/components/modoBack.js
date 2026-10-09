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

/**
 * Retorna a URL do backend.
 *
 * Como o frontend está sendo servido pelo próprio
 * Spring Boot, window.location.origin já aponta
 * para o servidor correto.
 */
export function getAPI_URL() {

    const API_URL = window.location.origin;

    console.log("========================================");
    console.log("VERIFICAÇÃO DO BACKEND");
    console.log("========================================");
    console.log("Origem atual:", API_URL);

    return API_URL;
}