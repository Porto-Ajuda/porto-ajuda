
        /* =============================================
           4. ABAS (com transição suave)
           ============================================= */

        const listaAbas = document.querySelector(".abas-perfil");
        const abas = document.querySelectorAll(".aba");
        const paineis = document.querySelectorAll(".painel-aba");
        const indicadorAba = document.querySelector(".aba-indicador");
        const reduzirMovimento = window.matchMedia("(prefers-reduced-motion: reduce)");

        let painelAtual = document.querySelector(".painel-aba:not([hidden])");
        let versaoTroca = 0;

              const EASE = "cubic-bezier(.22, 1, .36, 1)";

        function posicionarIndicador(animar = true) {
            const ativa = document.querySelector('.aba[aria-selected="true"]');

            if (!ativa || !indicadorAba) return;

            indicadorAba.style.transition = animar && !reduzirMovimento.matches
                ? `transform .45s ${EASE}, width .45s ${EASE}`
                : "none";

            indicadorAba.style.width = `${ativa.offsetWidth * 0.7}px`;
            indicadorAba.style.transform =
                `translateX(${ativa.offsetLeft + ativa.offsetWidth * 0.15}px)`;
        }

        function trocarAba(aba) {
            const novo = document.getElementById(aba.getAttribute("aria-controls"));

            if (!novo || novo === painelAtual) return;

            abas.forEach(item => {
                item.setAttribute("aria-selected", String(item === aba));
            });

            posicionarIndicador();

            const anterior = painelAtual;
            const minhaVersao = ++versaoTroca;
            const reduzir = reduzirMovimento.matches;

            painelAtual = novo;

            const mostrarNovo = () => {
                // se o usuário clicou em outra aba nesse meio tempo, ignora
                if (minhaVersao !== versaoTroca) return;

                paineis.forEach(painel => {
                    if (painel !== novo) {
                        painel.getAnimations?.({ subtree: true })
                            .forEach(animacao => animacao.cancel());
                        painel.hidden = true;
                    }
                });

                novo.hidden = false;

                if (typeof novo.animate !== "function") return;

                if (reduzir) {
                    novo.animate(
                        [{ opacity: 0 }, { opacity: 1 }],
                        { duration: 150 }
                    );
                    return;
                }

                novo.animate(
                    [
                        { opacity: 0, transform: "translateY(16px) scale(.99)" },
                        { opacity: 1, transform: "none" }
                    ],
                    { duration: 450, easing: EASE }
                );

                // cada bloco do painel entra um pouco depois do anterior
                Array.from(novo.children).forEach((filho, indice) => {
                    filho.animate(
                        [
                            { opacity: 0, transform: "translateY(10px)" },
                            { opacity: 1, transform: "none" }
                        ],
                        {
                            duration: 500,
                            easing: EASE,
                            delay: 60 + Math.min(indice, 8) * 45,
                            fill: "backwards"
                        }
                    );
                });
            };

            if (!anterior || typeof anterior.animate !== "function") {
                mostrarNovo();
                return;
            }

            const saida = anterior.animate(
                reduzir
                    ? [{ opacity: 1 }, { opacity: 0 }]
                    : [
                        { opacity: 1, transform: "none" },
                        { opacity: 0, transform: "translateY(-8px) scale(.995)" }
                    ],
                { duration: reduzir ? 100 : 180, easing: "ease-in", fill: "forwards" }
            );

            saida.onfinish = mostrarNovo;
        }
                abas.forEach(aba => {
            aba.addEventListener("click", () => trocarAba(aba));
        });

        // Setas ← → trocam de aba
        listaAbas?.addEventListener("keydown", (evento) => {
            if (!["ArrowLeft", "ArrowRight"].includes(evento.key)) return;

            const lista = Array.from(abas);
            const indice = lista.indexOf(document.activeElement);

            if (indice === -1) return;

            evento.preventDefault();

            const passo = evento.key === "ArrowRight" ? 1 : -1;
            const proxima = lista[(indice + passo + lista.length) % lista.length];

            proxima.focus();
            trocarAba(proxima);
        });

        // posição inicial da linha dourada (sem animar)
        posicionarIndicador(false);
        window.addEventListener("resize", () => posicionarIndicador(false));
        document.fonts?.ready.then(() => posicionarIndicador(false));