function perfilPublicoValido(perfil, id) {
    return perfil
        && Number(perfil.id) === Number(id)
        && typeof perfil.nome === "string";
}

function perfilPublicamenteDisponivel(perfil) {
    return perfil.status === "ATIVA" && perfil.arquivada !== true;
}

export async function carregarPerfilOngAcessivel(id, {
    buscarPerfilPublico,
    listarOngsDoUsuario,
    buscarPerfilPrivado,
    podeLerPerfilPrivadoComoAdmin = async () => false,
    buscarPerfilPrivadoComoAdmin = async () => null
}) {
    const respostaPublica = await buscarPerfilPublico(id);
    if (!respostaPublica) return { resposta: null };

    if (respostaPublica.ok) {
        const perfilPublico = await respostaPublica.json();
        if (!perfilPublicoValido(perfilPublico, id)) {
            return { resposta: respostaPublica, respostaInvalida: true };
        }
        if (perfilPublicamenteDisponivel(perfilPublico)) {
            return { perfil: perfilPublico, origem: "publica" };
        }
    } else if (respostaPublica.status !== 404) {
        return { resposta: respostaPublica };
    }

    const organizacoes = await listarOngsDoUsuario();
    const vinculo = Array.isArray(organizacoes)
        ? organizacoes.find(osc => Number(osc.id) === Number(id))
        : null;

    let respostaPrivada;
    if (vinculo?.cargo) {
        respostaPrivada = await buscarPerfilPrivado(id);
    } else {
        if (!await podeLerPerfilPrivadoComoAdmin()) return { naoEncontrado: true, organizacoes };
        respostaPrivada = await buscarPerfilPrivadoComoAdmin(id);
    }
    if (!respostaPrivada?.ok) {
        if ([401, 403, 404].includes(respostaPrivada?.status)) {
            return { naoEncontrado: true, organizacoes };
        }
        return { resposta: respostaPrivada, organizacoes };
    }

    const perfilPrivado = await respostaPrivada.json();
    if (!perfilPublicoValido(perfilPrivado, id)) {
        return { resposta: respostaPrivada, respostaInvalida: true, organizacoes };
    }
    return { perfil: perfilPrivado, origem: "privada", organizacoes };
}
