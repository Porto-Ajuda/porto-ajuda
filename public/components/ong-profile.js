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
    // DOAÇÃO
    // ========================================

    const botaoDoacao =
        document.querySelector("#Doacao");

    const botaoDoacaoFooter =
        document.querySelector("#Doacao-footer");

    const modalDoacao =
        document.querySelector("#modal-doacao");

    const fecharDoacao =
        document.querySelector("#fechar-doacao");

    const nomeOngDoacao =
        document.querySelector("#nome-ong-doacao");


    // ========================================
    // AVISO PIX
    // ========================================

    const avisoPix =
        document.querySelector("#aviso-pix");

    const fecharAvisoPix =
        document.querySelector("#fechar-aviso-pix");


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
    // ESCONDER DOAÇÃO PARA SERVIÇOS PÚBLICOS
    // ========================================

    if (ong.tipo === "PUBLICO") {

        if (botaoDoacao) {
            botaoDoacao.style.display = "none";
        }

        if (botaoDoacaoFooter) {
            botaoDoacaoFooter.style.display = "none";
        }

    }


    // ========================================
    // ABRIR MODAL
    // ========================================

    function abrirModalDoacao() {

        // Verificar se existe PIX cadastrado

        if (!ong.pix) {

            if (avisoPix) {
                avisoPix.style.display = "flex";
            }

            return;
        }


        // Colocar nome da ONG

        if (nomeOngDoacao) {

            nomeOngDoacao.textContent =
                `Doação para ${ong.nome}`;

        }


        // Abrir modal

        if (modalDoacao) {

            modalDoacao.style.display = "flex";

        }

    }

    // ========================================
    // FECHAR AVISO PIX
    // ========================================

    if (fecharAvisoPix) {

        fecharAvisoPix.addEventListener(
            "click",
            () => {

                avisoPix.style.display = "none";

            }
        );

    }


    // ========================================
    // CLICAR FORA DO AVISO
    // ========================================

    if (avisoPix) {

        avisoPix.addEventListener(
            "click",
            evento => {

                if (evento.target === avisoPix) {

                    avisoPix.style.display = "none";

                }

            }
        );

    }

    // ========================================
    // BOTÃO PRINCIPAL
    // ========================================

    if (botaoDoacao) {

        botaoDoacao.addEventListener(
            "click",
            abrirModalDoacao
        );

    }


    // ========================================
    // BOTÃO DO FOOTER
    // ========================================

    if (botaoDoacaoFooter) {

        botaoDoacaoFooter.addEventListener(
            "click",
            abrirModalDoacao
        );

    }


    // ========================================
    // FECHAR MODAL
    // ========================================

    if (fecharDoacao) {

        fecharDoacao.addEventListener(
            "click",
            () => {

                modalDoacao.style.display = "none";

            }
        );

    }


    // ========================================
    // CLICAR FORA DO MODAL
    // ========================================

    if (modalDoacao) {

        modalDoacao.addEventListener(
            "click",
            evento => {

                if (
                    evento.target === modalDoacao
                ) {

                    modalDoacao.style.display =
                        "none";

                }

            }
        );

    }

    // ========================================
    // CONFIGURAÇÃO DO PIX
    // ========================================

    const botoesValor =
        document.querySelectorAll(".valor-pix");

    const botaoOutro =
        document.querySelector("#botao-outro");

    const modalValor =
        document.querySelector("#modal-valor");

    const fecharModal =
        document.querySelector("#fechar-modal");

    const inputValor =
        document.querySelector("#valor");

    const botaoGerar =
        document.querySelector("#gerar-pix");

    const resultadoPix =
        document.querySelector("#resultado-pix");

    const qrcode =
        document.querySelector("#qrcode");

    const valorExibido =
        document.querySelector("#valor-exibido");

    const chavePixInput =
        document.querySelector("#chave-pix");

    const copiarChave =
        document.querySelector("#copiar-chave");

    const pixCopiaCola =
        document.querySelector("#pix-copia-cola");

    const botaoCopiar =
        document.querySelector("#copiar-pix");

    const mensagemCopia =
        document.querySelector("#mensagem-copia");

    // ========================================
    // LIMPAR TEXTO
    // ========================================

    function limparTexto(texto) {

        return texto
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/[^a-zA-Z0-9 ]/g, "")
            .toUpperCase();

    }


    // ========================================
    // CAMPO PIX
    // ========================================

    function campoPix(id, valor) {

        const tamanho =
            valor.length
                .toString()
                .padStart(2, "0");

        return id + tamanho + valor;

    }


    // ========================================
    // CRC16
    // ========================================

    function calcularCRC16(payload) {

        let crc = 0xFFFF;

        for (
            let i = 0;
            i < payload.length;
            i++
        ) {

            crc ^=
                payload.charCodeAt(i) << 8;

            for (
                let j = 0;
                j < 8;
                j++
            ) {

                if (
                    (crc & 0x8000) !== 0
                ) {

                    crc =
                        (crc << 1) ^ 0x1021;

                } else {

                    crc <<= 1;

                }

                crc &= 0xFFFF;

            }

        }

        return crc
            .toString(16)
            .toUpperCase()
            .padStart(4, "0");

    }

    // ========================================
    // GERAR PIX
    // ========================================

    function gerarPix(valor) {

        valor = Number(valor);

        if (
            !Number.isFinite(valor) ||
            valor <= 0
        ) {

            alert(
                "Digite um valor válido."
            );

            return null;

        }


        // Verificar PIX da ONG

        if (!ong.pix) {

            alert(
                "Esta ONG ainda não possui uma chave PIX cadastrada."
            );

            return null;

        }


        const chavePix =
            ong.pix.chave;

        const nomeRecebedor =
            ong.pix.nome;

        const cidadeRecebedor =
            ong.pix.cidade;


        const nome =
            limparTexto(nomeRecebedor)
                .substring(0, 25);

        const cidade =
            limparTexto(cidadeRecebedor)
                .substring(0, 15);


        // 00 - Payload Format Indicator

        const payloadFormat =
            campoPix("00", "01");


        // 26 - Merchant Account Information

        const gui =
            campoPix(
                "00",
                "BR.GOV.BCB.PIX"
            );

        const chave =
            campoPix(
                "01",
                chavePix
            );

        const merchantAccountInformation =
            campoPix(
                "26",
                gui + chave
            );


        // 52 - Merchant Category Code

        const merchantCategoryCode =
            campoPix(
                "52",
                "0000"
            );


        // 53 - Moeda

        const moeda =
            campoPix(
                "53",
                "986"
            );


        // 54 - Valor

        const valorFormatado =
            valor.toFixed(2);

        const valorPix =
            campoPix(
                "54",
                valorFormatado
            );


        // 58 - País

        const pais =
            campoPix(
                "58",
                "BR"
            );


        // 59 - Nome

        const nomePix =
            campoPix(
                "59",
                nome
            );


        // 60 - Cidade

        const cidadePix =
            campoPix(
                "60",
                cidade
            );


        // 62 - TXID

        const txid =
            campoPix(
                "05",
                "***"
            );

        const dadosAdicionais =
            campoPix(
                "62",
                txid
            );


        // Montar payload

        let payload =
            payloadFormat +
            merchantAccountInformation +
            merchantCategoryCode +
            moeda +
            valorPix +
            pais +
            nomePix +
            cidadePix +
            dadosAdicionais;


        // CRC

        payload += "6304";

        const crc =
            calcularCRC16(payload);

        payload += crc;


        return payload;

    }

    // ========================================
    // MOSTRAR PIX
    // ========================================

    function mostrarPix(valor) {

        const pix =
            gerarPix(valor);

        if (!pix) {
            return;
        }


        // Verificar biblioteca

        if (
            typeof QRCode === "undefined"
        ) {

            console.error(
                "QRCode não foi carregado."
            );

            alert(
                "A biblioteca do QR Code não foi carregada."
            );

            return;

        }


        // Limpar QR anterior

        qrcode.innerHTML = "";


        // Gerar QR

        new QRCode(qrcode, {

            text: pix,

            width: 275,

            height: 275,

            correctLevel:
                QRCode.CorrectLevel.M

        });


        // Mostrar valor

        valorExibido.textContent =
            Number(valor).toLocaleString(
                "pt-BR",
                {
                    style: "currency",
                    currency: "BRL"
                }
            );


        // Mostrar chave da ONG

        chavePixInput.value =
            ong.pix.chave;


        // Mostrar PIX Copia e Cola

        pixCopiaCola.value =
            pix;


        // Mostrar resultado

        resultadoPix.style.display =
            "block";


        // Limpar mensagem

        mensagemCopia.textContent = "";


        // Rolar até o resultado

        resultadoPix.scrollIntoView({
            behavior: "smooth",
            block: "center"
        });

    }

    // ========================================
    // BOTÕES DE VALOR
    // ========================================

    botoesValor.forEach(botao => {

        botao.addEventListener(
            "click",
            () => {

                const valor =
                    Number(
                        botao.dataset.valor
                    );


                botoesValor.forEach(
                    botaoAtual => {

                        botaoAtual.classList.remove(
                            "ativo"
                        );

                    }
                );


                botao.classList.add("ativo");


                mostrarPix(valor);

            }
        );

    });

    // ========================================
    // ABRIR OUTRO VALOR
    // ========================================

    if (botaoOutro) {

        botaoOutro.addEventListener(
            "click",
            () => {

                modalValor.style.display =
                    "flex";

                inputValor.value = "";

                setTimeout(() => {

                    inputValor.focus();

                }, 100);

            }
        );

    }


    // ========================================
    // FECHAR OUTRO VALOR
    // ========================================

    if (fecharModal) {

        fecharModal.addEventListener(
            "click",
            () => {

                modalValor.style.display =
                    "none";

            }
        );

    }


    // ========================================
    // CLICAR FORA
    // ========================================

    if (modalValor) {

        modalValor.addEventListener(
            "click",
            evento => {

                if (
                    evento.target === modalValor
                ) {

                    modalValor.style.display =
                        "none";

                }

            }
        );

    }

    // ========================================
    // CONFIRMAR OUTRO VALOR
    // ========================================

    if (botaoGerar) {

        botaoGerar.addEventListener(
            "click",
            () => {

                const valor =
                    Number(inputValor.value);


                if (
                    !Number.isFinite(valor) ||
                    valor <= 0
                ) {

                    alert(
                        "Digite um valor maior que R$ 0,00."
                    );

                    inputValor.focus();

                    return;

                }


                modalValor.style.display =
                    "none";


                botoesValor.forEach(
                    botao => {

                        botao.classList.remove(
                            "ativo"
                        );

                    }
                );


                mostrarPix(valor);

            }
        );

    }

    // ========================================
    // ENTER
    // ========================================

    if (inputValor) {

        inputValor.addEventListener(
            "keydown",
            evento => {

                if (evento.key === "Enter") {

                    evento.preventDefault();

                    botaoGerar.click();

                }

            }
        );

    }

    // ========================================
    // COPIAR CHAVE PIX
    // ========================================

    if (copiarChave) {

        copiarChave.addEventListener(
            "click",
            async () => {

                try {

                    await navigator.clipboard.writeText(
                        ong.pix.chave
                    );

                    mensagemCopia.textContent =
                        "Chave PIX copiada!";

                } catch (erro) {

                    chavePixInput.select();

                    document.execCommand("copy");

                    mensagemCopia.textContent =
                        "Chave PIX copiada!";

                }

            }
        );

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