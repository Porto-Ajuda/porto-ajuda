/* =========================================================
   AVATAR DO USUÁRIO NA NAVBAR (FOTO OU INICIAIS)
   Mesma regra de iniciais da área "Sua conta" (perfil.js):
   o nome social tem prioridade, o nome é abreviado e saem
   as primeiras letras das 2 primeiras partes.
   ========================================================= */

const CONECTORES = new Set(["de", "da", "do", "das", "dos", "e"]);

function abreviarNome(nome) {
    const partes = nome
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .filter((parte, indice, todas) =>
            indice === 0 ||
            indice === todas.length - 1 ||
            !CONECTORES.has(parte.toLocaleLowerCase("pt-BR"))
        );

    if (partes.length <= 2) {
        return partes.join(" ");
    }

    return [
        partes[0],
        ...partes.slice(1, -1).map(parte =>
            `${parte[0].toLocaleUpperCase("pt-BR")}.`
        ),
        partes.at(-1)
    ].join(" ");
}

export function obterIniciais(usuario) {
    const nome =
        usuario?.nomeSocial?.trim() ||
        usuario?.nome?.trim() ||
        "Usuário";

    return abreviarNome(nome)
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(parte => parte[0].toLocaleUpperCase("pt-BR"))
        .join("") || "?";
}

export function montarAvatar(icone, usuario) {
    const url = usuario?.fotoPerfilUrl;
    const iniciais = obterIniciais(usuario);

    icone.classList.add("tem-avatar");

    function mostrarIniciais() {
        const texto = document.createElement("span");

        texto.className = "avatar-nav-iniciais";
        texto.setAttribute("aria-hidden", "true");
        texto.textContent = iniciais;

        icone.replaceChildren(texto);
    }

    if (!url) {
        mostrarIniciais();
        return;
    }

    const imagem = document.createElement("img");

    imagem.className = "avatar-nav-img";
    imagem.alt = "";
    imagem.src = url;

    imagem.addEventListener("error", mostrarIniciais, { once: true });

    icone.replaceChildren(imagem);
}

export function restaurarIconePadrao(icone) {
    const simbolo = document.createElement("i");

    simbolo.className = "fa-regular fa-user";

    icone.classList.remove("tem-avatar");
    icone.replaceChildren(simbolo);
}
