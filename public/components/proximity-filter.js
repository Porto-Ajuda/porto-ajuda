export function normalizarTextoBusca(valor) {
    return String(valor || "")
        .normalize("NFD")
        .replace(/\p{M}/gu, "")
        .toLocaleLowerCase("pt-BR")
        .replace(/[^\p{L}\p{N}]/gu, "");
}

export function filtrarOngs(ongs, categoriaId, termoBusca) {
    const termo = normalizarTextoBusca(termoBusca);
    const categoria = String(categoriaId || "");
    return (Array.isArray(ongs) ? ongs : []).filter(ong => {
        const categorias = Array.isArray(ong.categorias) ? ong.categorias : [];
        if (categoria && !categorias.includes(categoria)) return false;
        if (!termo) return true;
        const textoBusca = [ong.nome, ong.categoria, ong.categoriasBusca, ong.endereco, ong.descricao]
            .map(normalizarTextoBusca)
            .join("");
        return textoBusca.includes(termo);
    });
}

export function categoriasParaFiltro(ongsElegiveis, taxonomia, categoriaSelecionada = "") {
    const disponiveis = new Set(
        (Array.isArray(ongsElegiveis) ? ongsElegiveis : [])
            .flatMap(ong => Array.isArray(ong.categorias) ? ong.categorias : [])
    );
    const categorias = (Array.isArray(taxonomia) ? taxonomia : [])
        .filter(categoria => disponiveis.has(categoria.id));

    return {
        categorias: [{ id: "", label: "Todos" }, ...categorias],
        categoriaSelecionada: categorias.some(categoria => categoria.id === categoriaSelecionada)
            ? categoriaSelecionada
            : ""
    };
}

export function sincronizarVisibilidadeOngs(ongsVisiveis, cartoes, marcadores, camadaMarcadores) {
    const idsVisiveis = new Set(ongsVisiveis.map(ong => String(ong.id)));

    cartoes.forEach((cartao, id) => {
        cartao.hidden = !idsVisiveis.has(String(id));
    });
    marcadores.forEach((marcador, id) => {
        const deveExibir = idsVisiveis.has(String(id));
        const estaExibido = camadaMarcadores.hasLayer(marcador);
        if (deveExibir && !estaExibido) camadaMarcadores.addLayer(marcador);
        if (!deveExibir && estaExibido) camadaMarcadores.removeLayer(marcador);
    });

    return idsVisiveis;
}
