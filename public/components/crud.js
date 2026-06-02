let db;

// ========================= //
// BANCO DE DADOS             //
// ========================= //

const requisicaoBanco = indexedDB.open("crudPosts", 2);

requisicaoBanco.onupgradeneeded = (event) => {

    db = event.target.result;

            if (!db.objectStoreNames.contains("posts")) {
        db.createObjectStore("posts", { keyPath: "id" });
    }

        if (!db.objectStoreNames.contains("postsOsc")) {
        db.createObjectStore("postsOsc", { keyPath: "id" });
    }

    if (!db.objectStoreNames.contains("postsVoluntariacao")) {
        db.createObjectStore("postsVoluntariacao", { keyPath: "id" });
    }

    if (!db.objectStoreNames.contains("postsDoacao")) {
        db.createObjectStore("postsDoacao", { keyPath: "id" });
    }
};

requisicaoBanco.onsuccess = (event) => {

    db = event.target.result;

    renderizarCardsSecao("osc");
    renderizarCardsSecao("voluntariacao");
    renderizarCardsSecao("doacao");

    inicializarAnimacoes();
};

requisicaoBanco.onerror = (event) => {
    console.error("Erro ao abrir banco:", event.target.error);
};

// ========================= //
// UTILITÁRIOS DE STORE       //
// ========================= //

function obterNomeStore(tipo) {
    const mapa = {
        osc: "postsOsc",
        voluntariacao: "postsVoluntariacao",
        doacao: "postsDoacao"
    };
    return mapa[tipo] || "posts";
}

function obterLabelTipo(tipo) {
    const mapa = {
        osc: "OSC's",
        voluntariacao: "Voluntariações",
        doacao: "Doações"
    };
    return mapa[tipo] || tipo;
}

// ========================= //
// CRUD POSTS POR TIPO        //
// ========================= //

function salvarPostTipo(tipo, post) {

    const nomeStore = obterNomeStore(tipo);

    const transacao = db.transaction([nomeStore], "readwrite");
    const store = transacao.objectStore(nomeStore);

    store.put(post);

    transacao.oncomplete = () => {
        renderizarCardsSecao(tipo);
        renderizarListaModal(tipo);
        limparFormPost();
    };
}

function listarPostsTipo(tipo, callback) {

    const nomeStore = obterNomeStore(tipo);

    const transacao = db.transaction([nomeStore], "readonly");
    const store = transacao.objectStore(nomeStore);
    const requisicao = store.getAll();

    requisicao.onsuccess = () => {
        callback(requisicao.result);
    };
}

function buscarPostTipo(tipo, id, callback) {

    const nomeStore = obterNomeStore(tipo);

    const transacao = db.transaction([nomeStore], "readonly");
    const store = transacao.objectStore(nomeStore);
    const requisicao = store.get(id);

    requisicao.onsuccess = () => {
        callback(requisicao.result);
    };
}

function excluirPostTipo(tipo, id) {

    if (!confirm("Deseja excluir este post?")) return;

    const nomeStore = obterNomeStore(tipo);

    const transacao = db.transaction([nomeStore], "readwrite");
    const store = transacao.objectStore(nomeStore);

    store.delete(id);

    transacao.oncomplete = () => {
        renderizarCardsSecao(tipo);
        renderizarListaModal(tipo);
    };
}

// ========================= //
// RENDERIZAÇÃO DOS CARDS     //
// ========================= //

function renderizarCardsSecao(tipo) {

    const idContainer = `cards-${tipo}`;
    const container = document.getElementById(idContainer);

    if (!container) return;

    listarPostsTipo(tipo, (posts) => {

        const postAtivos = posts.filter(p => p.ativo);

        // Mantém no máximo 4 cards visíveis
        const visiveis = postAtivos.slice(0, 4);

        let html = "";

        visiveis.forEach((post, indice) => {

            let imagemHtml = "";

            if (post.imagem) {
                const url = URL.createObjectURL(post.imagem);
                imagemHtml = `<img class="card-img" src="${url}" alt="${post.titulo}">`;
            }

            const semImagem = !post.imagem
                ? `<div class="card-sem-img"><i class="fa-solid fa-image"></i></div>`
                : "";

            html += `
                <div
                    class="card card-post show"
                    style="transition-delay: ${indice * 0.08}s"
                    onclick="abrirModalCard('${tipo}', ${post.id})"
                >
                    ${semImagem}
                    ${imagemHtml}
                    <div class="card-overlay"></div>
                    <div class="card-body">
                        <div class="card-titulo">${post.titulo}</div>
                        <div class="card-osc">${post.nomeOsc}</div>
                    </div>
                </div>
            `;
        });

        // Preenche com cards vazios caso haja menos de 4
        const quantidade = visiveis.length;
        for (let i = quantidade; i < 4; i++) {
            html += `<div class="card card-vazio" style="transition-delay: ${i * 0.08}s"></div>`;
        }

        container.innerHTML = html;

        // Aciona animação nos cards vazios também
        requestAnimationFrame(() => {
            container.querySelectorAll(".card-vazio").forEach((card, i) => {
                setTimeout(() => card.classList.add("show"), i * 80);
            });
        });
    });
}

// ========================= //
// MODAL DE EDIÇÃO            //
// ========================= //

let tipoModalAtual = null;
let arquivoImagemModal = null;

function abrirModalEdicao(tipo) {

    tipoModalAtual = tipo;
    arquivoImagemModal = null;

    document.getElementById("modal-titulo").textContent =
        `Gerenciar ${obterLabelTipo(tipo)}`;

    limparFormPost();
    renderizarListaModal(tipo);

    const overlay = document.getElementById("modal-overlay");
    overlay.classList.add("aberto");

    document.body.style.overflow = "hidden";
}

function fecharModal() {

    const overlay = document.getElementById("modal-overlay");
    overlay.classList.remove("aberto");

    document.body.style.overflow = "";

    tipoModalAtual = null;
}

function fecharModalEdicao(event) {
    if (event.target === document.getElementById("modal-overlay")) {
        fecharModal();
    }
}

// ========================= //
// FORMULÁRIO DO MODAL        //
// ========================= //

document.addEventListener("DOMContentLoaded", () => {

    // Preview da imagem no modal
    const inputImagem = document.getElementById("postImagem");
    if (inputImagem) {
        inputImagem.addEventListener("change", (e) => {

            arquivoImagemModal = e.target.files[0];

            if (!arquivoImagemModal) return;

            const preview = document.getElementById("uploadPreview");
            preview.src = URL.createObjectURL(arquivoImagemModal);
            preview.style.display = "block";

            document.querySelector(".upload-icone").style.display = "none";
            document.querySelector(".upload-texto").style.display = "none";
        });
    }

    // Submit do formulário
    const formPost = document.getElementById("formPost");
    if (formPost) {
        formPost.addEventListener("submit", (e) => {
            e.preventDefault();
            submeterFormPost();
        });
    }
});

function submeterFormPost() {

    if (!tipoModalAtual) return;

    const idEdicao = document.getElementById("postId").value;
    const agora = new Date().toISOString();

    if (idEdicao) {

        buscarPostTipo(tipoModalAtual, Number(idEdicao), (postAntigo) => {

            const postAtualizado = {
                ...postAntigo,
                nomeUsuario: document.getElementById("postNomeUsuario").value,
                nomeOsc: document.getElementById("postNomeOsc").value,
                ativo: document.getElementById("postAtivo").checked,
                titulo: document.getElementById("postTitulo").value,
                conteudo: document.getElementById("postConteudo").value,
                imagem: arquivoImagemModal || postAntigo.imagem,
                dataAtualizacao: agora
            };

            salvarPostTipo(tipoModalAtual, postAtualizado);
        });

    } else {

        const post = {
            id: Date.now(),
            tipo: tipoModalAtual,
            nomeUsuario: document.getElementById("postNomeUsuario").value,
            nomeOsc: document.getElementById("postNomeOsc").value,
            ativo: document.getElementById("postAtivo").checked,
            titulo: document.getElementById("postTitulo").value,
            conteudo: document.getElementById("postConteudo").value,
            imagem: arquivoImagemModal,
            dataCriacao: agora,
            dataAtualizacao: agora
        };

        salvarPostTipo(tipoModalAtual, post);
    }
}

function editarPostModal(tipo, id) {

    buscarPostTipo(tipo, id, (post) => {

        document.getElementById("postId").value = post.id;
        document.getElementById("postTipo").value = tipo;
        document.getElementById("postNomeUsuario").value = post.nomeUsuario;
        document.getElementById("postNomeOsc").value = post.nomeOsc;
        document.getElementById("postAtivo").checked = post.ativo;
        document.getElementById("postTitulo").value = post.titulo;
        document.getElementById("postConteudo").value = post.conteudo;

        arquivoImagemModal = post.imagem;

        const preview = document.getElementById("uploadPreview");

        if (post.imagem) {
            preview.src = URL.createObjectURL(post.imagem);
            preview.style.display = "block";
            document.querySelector(".upload-icone").style.display = "none";
            document.querySelector(".upload-texto").style.display = "none";
        }

        document.getElementById("modal-container").scrollTo({
            top: 0,
            behavior: "smooth"
        });
    });
}

function limparFormPost() {

    const formPost = document.getElementById("formPost");
    if (formPost) formPost.reset();

    document.getElementById("postId").value = "";

    arquivoImagemModal = null;

    const preview = document.getElementById("uploadPreview");
    if (preview) {
        preview.src = "";
        preview.style.display = "none";
    }

    const icone = document.querySelector(".upload-icone");
    const texto = document.querySelector(".upload-texto");
    if (icone) icone.style.display = "";
    if (texto) texto.style.display = "";
}

// ========================= //
// LISTA DE POSTS NO MODAL    //
// ========================= //

function renderizarListaModal(tipo) {

    const container = document.getElementById("modalListaPosts");
    if (!container) return;

    listarPostsTipo(tipo, (posts) => {

        if (posts.length === 0) {
            container.innerHTML = `
                <div class="modal-lista-vazio">
                    <i class="fa-solid fa-inbox"></i>
                    Nenhum post cadastrado ainda.
                </div>
            `;
            return;
        }

        let html = "";

        posts.forEach(post => {

            let thumbHtml = "";

            if (post.imagem) {
                const url = URL.createObjectURL(post.imagem);
                thumbHtml = `<img class="modal-lista-thumb" src="${url}" alt="">`;
            } else {
                thumbHtml = `
                    <div class="modal-lista-thumb-vazio">
                        <i class="fa-solid fa-image"></i>
                    </div>
                `;
            }

            const statusClasse = post.ativo ? "" : "inativo";
            const statusTexto = post.ativo ? "Ativo" : "Inativo";

            html += `
                <div class="modal-lista-item">
                    ${thumbHtml}
                    <div class="modal-lista-info">
                        <div class="modal-lista-titulo-post">${post.titulo}</div>
                        <div class="modal-lista-subtitulo">${post.nomeOsc} · ${post.nomeUsuario}</div>
                    </div>
                    <span class="modal-lista-ativo ${statusClasse}">${statusTexto}</span>
                    <div class="modal-lista-acoes">
                        <button
                            class="modal-lista-btn"
                            title="Editar"
                            onclick="editarPostModal('${tipo}', ${post.id})"
                        >
                            <i class="fa-solid fa-pen"></i>
                        </button>
                        <button
                            class="modal-lista-btn excluir"
                            title="Excluir"
                            onclick="excluirPostTipo('${tipo}', ${post.id})"
                        >
                            <i class="fa-solid fa-trash"></i>
                        </button>
                    </div>
                </div>
            `;
        });

        container.innerHTML = html;
    });
}

// ========================= //
// MODAL DE VISUALIZAÇÃO CARD //
// ========================= //

function abrirModalCard(tipo, id) {

    buscarPostTipo(tipo, id, (post) => {

        if (!post) return;

        document.getElementById("modal-card-tag").textContent = obterLabelTipo(tipo);
        document.getElementById("modal-card-titulo").textContent = post.titulo;
        document.getElementById("modal-card-osc").textContent = post.nomeOsc;
        document.getElementById("modal-card-conteudo").textContent = post.conteudo;
        document.getElementById("modal-card-usuario-nome").textContent = post.nomeUsuario;

        const data = new Date(post.dataCriacao).toLocaleDateString("pt-BR", {
            day: "2-digit",
            month: "short",
            year: "numeric"
        });
        document.getElementById("modal-card-data").textContent = data;

        const imagem = document.getElementById("modal-card-imagem");
        const placeholder = document.getElementById("modal-card-imagem-placeholder");

        if (post.imagem) {
            imagem.src = URL.createObjectURL(post.imagem);
            imagem.style.display = "block";
            placeholder.style.display = "none";
        } else {
            imagem.style.display = "none";
            placeholder.style.display = "flex";
        }

        const overlay = document.getElementById("modal-card-overlay");
        overlay.classList.add("aberto");
        document.body.style.overflow = "hidden";
    });
}

function fecharModalCard(event) {
    if (event.target === document.getElementById("modal-card-overlay")) {
        fecharModalCardDireto();
    }
}

function fecharModalCardDireto() {
    const overlay = document.getElementById("modal-card-overlay");
    overlay.classList.remove("aberto");
    document.body.style.overflow = "";
}

// ========================= //
// ANIMAÇÕES DE SURGIR        //
// ========================= //

function inicializarAnimacoes() {

    const elementosSurgir = document.querySelectorAll(".surgir");

    const observador = new IntersectionObserver((entradas) => {

        entradas.forEach(entrada => {

            if (entrada.isIntersecting) {
                entrada.target.classList.add("active");

                entrada.target.querySelectorAll(".card").forEach((card, i) => {
                    setTimeout(() => card.classList.add("show"), i * 120);
                });
            }
        });

    }, { threshold: 0.15 });

    elementosSurgir.forEach(el => observador.observe(el));
}

// Garante inicialização mesmo se o banco demorar
document.addEventListener("DOMContentLoaded", () => {
    if (db) inicializarAnimacoes();
});