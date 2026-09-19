document.addEventListener("DOMContentLoaded", () => {

    // ========================================
    // CARROSSEL
    // ========================================

    const track = document.querySelector(".carrossel-track");
    const imagens = document.querySelectorAll(".carrossel-track img");

    let indice = 0;

    if (track && imagens.length > 1) {

        function proximaImagem() {

            indice++;

            if (indice >= imagens.length) {
                indice = 0;
            }

            track.style.transform =
                `translateX(-${indice * 100}%)`;
        }

        setInterval(proximaImagem, 5000);
    }


    // ========================================
    // PEGAR ID DA URL
    // ========================================

    const params = new URLSearchParams(
        window.location.search
    );

    const idOng = Number(
        params.get("id")
    );

    console.log(
        "ID recebido pela URL:",
        idOng
    );

    console.log(
        "Array de ONGs:",
        ongs
    );


    // ========================================
    // VERIFICAR ID
    // ========================================

    if (!idOng) {

        console.error(
            "ID da ONG não foi informado na URL."
        );

        return;
    }


    // ========================================
    // ENCONTRAR ONG
    // ========================================

    const ong = ongs.find(
        ong => ong.id === idOng
    );


    if (!ong) {

        console.error(
            "ONG não encontrada."
        );

        return;
    }


    console.log(
        "ONG encontrada:",
        ong
    );


    // ========================================
    // NOME
    // ========================================

    const nomeOng =
        document.querySelector("#nome-ong");

    if (nomeOng) {

        nomeOng.textContent =
            ong.nome || "Nome não informado";
    }


    // ========================================
    // CATEGORIA
    // ========================================

    const categoriaOng =
        document.querySelector("#categoria-ong");

    if (categoriaOng) {

        categoriaOng.textContent =
            ong.categoria || "Categoria não informada";
    }


    // ========================================
    // DESCRIÇÃO CURTA
    // ========================================

    const descricaoOng =
        document.querySelector("#descricao-texto");

    if (descricaoOng) {

        descricaoOng.textContent =
            ong.descricao ||
            "Esta iniciativa ainda não possui uma descrição cadastrada.";
    }


    // ========================================
    // SOBRE
    // ========================================

    const sobreOng =
        document.querySelector("#sobre-texto");

    if (sobreOng) {

        sobreOng.textContent =
            ong.sobre ||
            ong.descricao ||
            "Informações sobre esta iniciativa ainda não foram cadastradas.";
    }


    // ========================================
    // PÚBLICO ATENDIDO
    // ========================================

    const publicoOng =
        document.querySelector("#publico-texto");

    if (publicoOng) {

        publicoOng.textContent =
            ong.publico ||
            "Público atendido não informado.";
    }


    // ========================================
    // SERVIÇOS / ATUAÇÃO
    // ========================================

    const listaServicos =
        document.querySelector("#servicos-lista");

    if (listaServicos) {

        listaServicos.innerHTML = "";

        if (
            Array.isArray(ong.servicos) &&
            ong.servicos.length > 0
        ) {

            ong.servicos.forEach(servico => {

                const item =
                    document.createElement("li");

                item.textContent = servico;

                listaServicos.appendChild(item);
            });

        } else {

            const item =
                document.createElement("li");

            item.textContent =
                "Informações sobre a atuação ainda não cadastradas.";

            listaServicos.appendChild(item);
        }
    }


    // ========================================
    // ODS
    // ========================================

    const containerOds =
        document.querySelector("#ods-container");

    if (containerOds) {

        containerOds.innerHTML = "";

        if (
            Array.isArray(ong.ods) &&
            ong.ods.length > 0
        ) {

            ong.ods.forEach(numero => {

                const item =
                    document.createElement("div");

                item.classList.add("ods-item");


                const imagem =
                    document.createElement("img");

                /*
                 * Se suas imagens forem PNG:
                 * ods1.png, ods2.png...
                 *
                 * Caso alguma seja .webp,
                 * altere o nome do arquivo.
                 */

                imagem.src =
                    `../assets/ods${numero}.png`;

                imagem.alt =
                    `Objetivo de Desenvolvimento Sustentável ${numero}`;

                imagem.onerror = () => {

                    console.warn(
                        `Imagem da ODS ${numero} não encontrada.`
                    );

                    item.remove();
                };


                item.appendChild(imagem);

                containerOds.appendChild(item);

            });

        } else {

            containerOds.innerHTML = `
                <p>
                    Nenhuma ODS cadastrada para esta iniciativa.
                </p>
            `;
        }
    }


    // ========================================
    // REGIÃO
    // ========================================

    const regiaoOng =
        document.querySelector("#regiao-ong");

    if (regiaoOng) {

        regiaoOng.textContent =
            descobrirRegiao(ong.endereco);
    }


    // ========================================
    // ENDEREÇO
    // ========================================

    console.log(
        "Endereço:",
        ong.endereco
    );


    // ========================================
    // IMAGEM DA ONG
    // ========================================

    /*
     * No seu HTML atual o carrossel usa
     * imagens fixas.
     *
     * Portanto, não existe mais #imagem-ong.
     *
     * Se a ONG tiver uma imagem própria,
     * podemos trocar a primeira imagem
     * do carrossel por ela.
     */

    if (ong.imagem && imagens.length > 0) {

        imagens[0].src =
            ong.imagem;

        imagens[0].alt =
            `Imagem de ${ong.nome}`;
    }


    // ========================================
    // DOAÇÃO
    // ========================================

    const botaoDoacao =
        document.querySelector("#Doacao");

    const botaoDoacaoFooter =
        document.querySelector("#Doacao-footer");


    /*
     * Se futuramente você colocar:
     *
     * tipo: "ONG"
     *
     * ou
     *
     * tipo: "PUBLICO"
     *
     * podemos controlar automaticamente
     * quem pode receber doações.
     */


    if (ong.tipo === "PUBLICO") {

        if (botaoDoacao) {
            botaoDoacao.style.display = "none";
        }

        if (botaoDoacaoFooter) {
            botaoDoacaoFooter.style.display = "none";
        }

    }


});


// ========================================
// DESCOBRIR REGIÃO
// ========================================

function descobrirRegiao(endereco) {

    if (!endereco) {

        return "Região não informada";
    }


    const enderecoMinusculo =
        endereco.toLowerCase();


    if (
        enderecoMinusculo.includes(
            "praia grande"
        )
    ) {

        return "Praia Grande - SP";
    }


    if (
        enderecoMinusculo.includes(
            "são vicente"
        ) ||
        enderecoMinusculo.includes(
            "sao vicente"
        )
    ) {

        return "São Vicente - SP";
    }


    if (
        enderecoMinusculo.includes(
            "santos"
        )
    ) {

        return "Santos - SP";
    }


    return "Baixada Santista";
}