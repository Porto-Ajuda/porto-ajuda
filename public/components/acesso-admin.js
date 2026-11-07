import { getAPI_URL } from "./modoBack.js";

(() => {
    'use strict';

    const CODIGO_ACESSO = 'IDDQD';

    // Evita inicializar o sistema mais de uma vez.
    if (window.__portoAcessoAdminInicializado) return;
    window.__portoAcessoAdminInicializado = true;

    function inicializarAcessoAdmin() {

        const API_URL = getAPI_URL();
        // API_URL escolhe o padrão de rotas existente; a navegação usa a origem atual do frontend.
        const ROTAS = API_URL === "http://localhost:8080"
            || API_URL === "https://api.portoajuda.com.br"
            ? { admin: "/admin" }
            : { admin: "../admin/index.html" };

        /* =========================================
           1. CRIAÇÃO AUTOMÁTICA DO MODAL
           ========================================= */

        if (!document.querySelector('#modal-acesso-admin')) {

            const conteudoModal = `

<dialog id="modal-acesso-admin">

    <canvas id="chuva-matrix" aria-hidden="true"></canvas>

    <div class="acesso-admin-painel">

        <div class="modal-header">
            <h1 class="modal-title">⚠ SISTEMA DE SEGURANÇA</h1>

            <button
                class="close-modal"
                id="fechar-acesso-admin"
                type="button"
                aria-label="Fechar"
            >
                <span class="material-symbols-outlined">close</span>
            </button>
        </div>

        <!-- ETAPA 1: AVISO DE ACESSO -->
        <section id="etapa-aviso-admin">

            <div class="modal-body">
                <h2>VOCÊ NÃO DEVERIA ESTAR AQUI.</h2>

                <p>
                    Uma sequência não autorizada foi identificada.
                    Esta área não está disponível para acessos públicos.
                </p>

                <p>Sua presença nesta área foi registrada.</p>

                <h2 class="alerta-acesso-admin">
                    TEM CERTEZA DE QUE DESEJA CONTINUAR?
                </h2>

                <p class="status-acesso-admin">
                    AGUARDANDO DECISÃO... // SYS://UNKNOWN
                </p>
            </div>

            <div class="modal-footer">
                <button
                    type="button"
                    class="confirm-modal"
                    id="continuar-acesso-admin"
                >
                    CONTINUAR MESMO ASSIM
                </button>
            </div>

        </section>

        <!-- ETAPA 2: LOGIN ADMINISTRATIVO -->
        <section id="etapa-login-admin" hidden>

            <div class="modal-body">

                <div class="login-admin-identificacao">
                    <span>AUTHENTICATION REQUIRED</span>
                    <span>LEVEL 01 // ADMIN ACCESS</span>
                </div>

                <h2 class="titulo-login-admin">
                    IDENTIFIQUE-SE.
                </h2>

                <p class="descricao-login-admin">
                    Informe suas credenciais administrativas
                    para solicitar acesso ao sistema.
                </p>

                <form id="form-login-admin" autocomplete="on">

                    <div class="campo-login-admin">
                        <label for="email-login-admin">
                            E-MAIL ADMINISTRATIVO
                        </label>

                        <input
                            type="email"
                            id="email-login-admin"
                            name="email"
                            placeholder="admin@portoajuda.com.br"
                            autocomplete="username"
                            required
                        >
                    </div>

                    <div class="campo-login-admin">
                        <label for="senha-login-admin">
                            SENHA
                        </label>

                        <input
                            type="password"
                            id="senha-login-admin"
                            name="senha"
                            placeholder="Digite sua senha"
                            autocomplete="current-password"
                            required
                        >
                    </div>

                    <p
                        id="mensagem-login-admin"
                        class="mensagem-login-admin"
                        role="alert"
                        hidden
                    ></p>

                    <div class="acoes-login-admin">

                        <button
                            type="button"
                            id="voltar-aviso-admin"
                            class="botao-voltar-admin"
                        >
                            VOLTAR
                        </button>

                        <button
                            type="submit"
                            class="confirm-modal"
                            id="enviar-login-admin"
                        >
                            VERIFICAR CREDENCIAIS
                        </button>

                    </div>

                </form>

                <p class="status-acesso-admin">
                    AGUARDANDO AUTENTICAÇÃO... // SYS://SECURE
                </p>

            </div>

        </section>

        <section id="etapa-email-admin" hidden>
            <div class="modal-body">
                <div class="login-admin-identificacao">
                    <span>AUTHENTICATION REQUIRED</span>
                    <span>LEVEL 02 // EMAIL VERIFICATION</span>
                </div>
                <h2 class="titulo-login-admin">CONFIRME SEU E-MAIL.</h2>
                <p class="descricao-login-admin">Se as credenciais forem elegíveis, um código será enviado ao e-mail cadastrado.</p>
                <form id="form-email-admin" autocomplete="off">
                    <div class="campo-login-admin">
                        <label for="codigo-email-admin">CÓDIGO DE VERIFICAÇÃO</label>
                        <input id="codigo-email-admin" name="emailCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required aria-describedby="mensagem-email-admin">
                    </div>
                    <p id="mensagem-email-admin" class="mensagem-login-admin" role="alert" aria-live="polite" hidden></p>
                    <div class="acoes-login-admin">
                        <button type="button" class="botao-voltar-admin" data-voltar-login>VOLTAR</button>
                        <button type="submit" class="confirm-modal">VALIDAR CÓDIGO</button>
                    </div>
                </form>
            </div>
        </section>

        <section id="etapa-sms-admin" hidden>
            <div class="modal-body">
                <div class="login-admin-identificacao">
                    <span>AUTHENTICATION REQUIRED</span>
                    <span>LEVEL 03 // SMS VERIFICATION</span>
                </div>
                <h2 class="titulo-login-admin">CONFIRME SEU TELEFONE.</h2>
                <p class="descricao-login-admin">Informe o código enviado ao telefone cadastrado.</p>
                <form id="form-sms-admin" autocomplete="off">
                    <div class="campo-login-admin">
                        <label for="codigo-sms-admin">CÓDIGO DE VERIFICAÇÃO</label>
                        <input id="codigo-sms-admin" name="smsCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required aria-describedby="mensagem-sms-admin">
                    </div>
                    <p id="mensagem-sms-admin" class="mensagem-login-admin" role="alert" aria-live="polite" hidden></p>
                    <div class="acoes-login-admin">
                        <button type="button" class="botao-voltar-admin" data-reiniciar-login>REINICIAR</button>
                        <button type="submit" class="confirm-modal">VALIDAR CÓDIGO</button>
                    </div>
                </form>
            </div>
        </section>

        <section id="etapa-sessao-admin" hidden>
            <div class="modal-body">
                <div class="login-admin-identificacao">
                    <span>AUTHENTICATION COMPLETE</span>
                    <span>PRIVILEGED SESSION // ACTIVE</span>
                </div>
                <h2 class="titulo-login-admin">ACESSO ADMINISTRATIVO ATIVO.</h2>
                <p class="descricao-login-admin">Sua sessão administrativa está protegida e será encerrada por inatividade ou expiração.</p>
                <p id="mensagem-sessao-admin" class="mensagem-login-admin" role="alert" aria-live="polite" hidden></p>
                <div class="acoes-login-admin">
                    <button type="button" class="botao-voltar-admin" id="sair-admin">ENCERRAR SESSÃO</button>
                    <button type="button" class="confirm-modal" id="continuar-painel-admin">CONTINUAR</button>
                </div>
            </div>
        </section>

    </div>
</dialog>`;

            document.body.insertAdjacentHTML(
                'beforeend',
                conteudoModal
            );
        }

        /* =========================================
           2. REFERÊNCIAS DOS ELEMENTOS
           ========================================= */

        const modal = document.querySelector('#modal-acesso-admin');
        const canvas = document.querySelector('#chuva-matrix');
        const botaoFechar = document.querySelector('#fechar-acesso-admin');

        if (!modal || !canvas || !botaoFechar) {
            console.error('[Acesso Admin] Não foi possível inicializar o modal.');
            return;
        }

        const ctx = canvas.getContext('2d');

        if (!ctx) {
            console.error('[Acesso Admin] Não foi possível criar o contexto do canvas.');
            return;
        }

        /* =========================================
           3. FECHAMENTO DO MODAL
           ========================================= */

        botaoFechar.addEventListener('click', () => {
            modal.close();
        });


        const etapaAviso = document.querySelector('#etapa-aviso-admin');
        const etapaLogin = document.querySelector('#etapa-login-admin');
        const etapaEmail = document.querySelector('#etapa-email-admin');
        const etapaSms = document.querySelector('#etapa-sms-admin');
        const etapaSessao = document.querySelector('#etapa-sessao-admin');
        const botaoContinuarPainel = document.querySelector('#continuar-painel-admin');
        const botaoContinuar = document.querySelector('#continuar-acesso-admin');
        const botaoVoltar = document.querySelector('#voltar-aviso-admin');
        const formularioLogin = document.querySelector('#form-login-admin');
        const formularioEmail = document.querySelector('#form-email-admin');
        const formularioSms = document.querySelector('#form-sms-admin');
        const mensagemLogin = document.querySelector('#mensagem-login-admin');
        const mensagemEmail = document.querySelector('#mensagem-email-admin');
        const mensagemSms = document.querySelector('#mensagem-sms-admin');
        const mensagemSessao = document.querySelector('#mensagem-sessao-admin');
        let challengeId = null;
        let flowGeneration = 0;
        let activeRequestController = null;
        const etapas = [etapaAviso, etapaLogin, etapaEmail, etapaSms, etapaSessao];

        function iniciarRequisicao() {
            if (activeRequestController) return null;
            activeRequestController = new AbortController();
            return activeRequestController;
        }

        function encerrarRequisicao(controller) {
            if (activeRequestController === controller) activeRequestController = null;
        }

        function mostrarEtapa(etapa, seletorFoco) {
            etapas.forEach(item => { item.hidden = item !== etapa; });
            if (seletorFoco) document.querySelector(seletorFoco)?.focus();
        }

        function mostrarMensagem(elemento, texto) {
            elemento.textContent = texto;
            elemento.hidden = false;
        }

        function limparMensagem(elemento) {
            elemento.textContent = '';
            elemento.hidden = true;
        }

        function mensagemDeFalha(status) {
            if (status === 401) return 'Código inválido ou expirado. Confira e tente novamente.';
            if (status === 403) return 'A solicitação foi recusada. Reabra o acesso e tente novamente.';
            if (status === 429) return 'Muitas tentativas. Aguarde antes de tentar novamente.';
            return 'Não foi possível concluir a solicitação. Tente novamente em instantes.';
        }

        async function obterCsrf(signal) {
            const resposta = await fetch(`${API_URL}/usuario/csrf`, {
                method: 'GET', credentials: 'include', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal
            });
            if (!resposta.ok) throw new Error('csrf');
            const csrf = await resposta.json();
            if (!csrf?.headerName || !csrf?.token) throw new Error('csrf');
            return csrf;
        }

        async function enviarPost(caminho, corpo, signal) {
            const csrf = await obterCsrf(signal);
            return fetch(`${API_URL}${caminho}`, {
                method: 'POST', credentials: 'include', cache: 'no-store',
                headers: {
                    Accept: 'application/json', 'Content-Type': 'application/json',
                    [csrf.headerName]: csrf.token
                },
                body: corpo === undefined ? undefined : JSON.stringify(corpo),
                signal
            });
        }

        async function verificarSessaoAtual(signal) {
            const resposta = await fetch(`${API_URL}/admin/auth/session`, {
                method: 'GET', credentials: 'include', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal
            });
            if (resposta.status === 401 || resposta.status === 403) return false;
            // Keep the existing 204 contract; expiry metadata is exposed in safe headers.
            if (resposta.status === 204) return true;
            throw new Error('session');
        }

        async function mostrarSessaoSeValida(mensagemErro, generation, signal) {
            try {
                const sessaoValida = await verificarSessaoAtual(signal);
                if (generation !== flowGeneration) return false;
                if (sessaoValida) {
                    mostrarEtapa(etapaSessao, '#continuar-painel-admin');
                    return true;
                }
                mostrarMensagem(mensagemErro, 'Não foi possível confirmar a sessão neste navegador. Inicie a autenticação novamente.');
            } catch {
                if (generation !== flowGeneration) return false;
                mostrarMensagem(mensagemErro, 'Não foi possível confirmar a sessão por falha de conexão. Verifique a rede e tente novamente.');
            }
            return false;
        }

        botaoFechar.addEventListener('click', () => modal.close());

        botaoContinuar.addEventListener('click', async () => {
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            botaoContinuar.disabled = true;
            try {
                const sessaoValida = await verificarSessaoAtual(controller.signal);
                if (generation !== flowGeneration) return;
                if (sessaoValida) mostrarEtapa(etapaSessao, '#continuar-painel-admin');
                else mostrarEtapa(etapaLogin, '#email-login-admin');
            } catch {
                if (generation !== flowGeneration) return;
                mostrarEtapa(etapaLogin, '#email-login-admin');
                mostrarMensagem(mensagemLogin, 'Não foi possível consultar uma sessão existente. Você pode tentar entrar novamente.');
            } finally {
                encerrarRequisicao(controller);
                botaoContinuar.disabled = false;
            }
        });

        botaoVoltar.addEventListener('click', () => {
            mostrarEtapa(etapaAviso);
            formularioLogin.reset();
            limparMensagem(mensagemLogin);
        });

        formularioLogin.addEventListener('submit', async event => {
            event.preventDefault();
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            limparMensagem(mensagemLogin);
            const botao = formularioLogin.querySelector('[type="submit"]');
            botao.disabled = true;
            botao.textContent = 'VERIFICANDO…';
            try {
                const resposta = await enviarPost('/admin/auth/challenges', {
                    email: document.querySelector('#email-login-admin').value.trim(),
                    senha: document.querySelector('#senha-login-admin').value
                }, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status !== 202) {
                    mostrarMensagem(mensagemLogin, mensagemDeFalha(resposta.status));
                    return;
                }
                const dados = await resposta.json();
                if (generation !== flowGeneration) return;
                if (!dados.challengeId) throw new Error('challenge');
                challengeId = dados.challengeId;
                document.querySelector('#senha-login-admin').value = '';
                mostrarEtapa(etapaEmail, '#codigo-email-admin');
            } catch {
                if (generation === flowGeneration) {
                    mostrarMensagem(mensagemLogin, 'Não foi possível obter resposta da API. Confira a disponibilidade do backend, a URL e a origem CORS; tente iniciar novamente.');
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
                botao.textContent = 'VERIFICAR CREDENCIAIS';
            }
        });

        formularioEmail.addEventListener('submit', async event => {
            event.preventDefault();
            const generation = flowGeneration;
            if (!challengeId) {
                mostrarMensagem(mensagemEmail, 'Esta tentativa expirou. Inicie novamente.');
                return;
            }
            const controller = iniciarRequisicao();
            if (!controller) return;
            limparMensagem(mensagemEmail);
            const botao = formularioEmail.querySelector('[type="submit"]');
            botao.disabled = true;
            botao.textContent = 'VALIDANDO…';
            try {
                const resposta = await enviarPost(`/admin/auth/challenges/${encodeURIComponent(challengeId)}/verify`, {
                    emailCode: document.querySelector('#codigo-email-admin').value.trim()
                }, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status === 202) mostrarEtapa(etapaSms, '#codigo-sms-admin');
                else if (resposta.status === 204) {
                    challengeId = null;
                    await mostrarSessaoSeValida(mensagemEmail, generation, controller.signal);
                }
                else {
                    mostrarMensagem(mensagemEmail, mensagemDeFalha(resposta.status));
                    if (resposta.status === 401) document.querySelector('#codigo-email-admin').value = '';
                }
            } catch {
                if (generation === flowGeneration) {
                    challengeId = null;
                    mostrarMensagem(mensagemEmail, 'A resposta do servidor não chegou. O código pode ter sido processado; reinicie a autenticação para obter novos códigos.');
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
                botao.textContent = 'VALIDAR CÓDIGO';
            }
        });

        formularioSms.addEventListener('submit', async event => {
            event.preventDefault();
            const generation = flowGeneration;
            if (!challengeId) {
                mostrarMensagem(mensagemSms, 'Esta tentativa expirou. Inicie novamente.');
                return;
            }
            const controller = iniciarRequisicao();
            if (!controller) return;
            limparMensagem(mensagemSms);
            const botao = formularioSms.querySelector('[type="submit"]');
            botao.disabled = true;
            botao.textContent = 'VALIDANDO…';
            try {
                const resposta = await enviarPost(`/admin/auth/challenges/${encodeURIComponent(challengeId)}/verify`, {
                    smsCode: document.querySelector('#codigo-sms-admin').value.trim()
                }, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status === 204) {
                    challengeId = null;
                    await mostrarSessaoSeValida(mensagemSms, generation, controller.signal);
                } else if (resposta.status === 202) {
                    mostrarEtapa(etapaEmail, '#codigo-email-admin');
                    mostrarMensagem(mensagemEmail, 'Ainda falta confirmar o outro canal de verificação.');
                } else {
                    mostrarMensagem(mensagemSms, mensagemDeFalha(resposta.status));
                    if (resposta.status === 401) document.querySelector('#codigo-sms-admin').value = '';
                }
            } catch {
                if (generation === flowGeneration) {
                    challengeId = null;
                    await mostrarSessaoSeValida(mensagemSms, generation, controller.signal);
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
                botao.textContent = 'VALIDAR CÓDIGO';
            }
        });

        document.querySelectorAll('[data-voltar-login], [data-reiniciar-login]').forEach(botao => {
            botao.addEventListener('click', () => {
                challengeId = null;
                formularioEmail.reset();
                formularioSms.reset();
                limparMensagem(mensagemEmail);
                limparMensagem(mensagemSms);
                mostrarEtapa(etapaLogin, '#email-login-admin');
            });
        });
        botaoContinuarPainel.addEventListener('click', async () => {
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            botaoContinuarPainel.disabled = true;
            limparMensagem(mensagemSessao);
            try {
                const sessaoValida = await verificarSessaoAtual(controller.signal);
                if (generation !== flowGeneration) return;
                if (sessaoValida) {
                    window.location.assign(ROTAS.admin);
                    return;
                }
                challengeId = null;
                formularioLogin.reset();
                mostrarEtapa(etapaLogin, '#email-login-admin');
                mostrarMensagem(mensagemLogin, 'Sua sessão administrativa expirou. Autentique-se novamente.');
            } catch {
                if (generation === flowGeneration) {
                    mostrarMensagem(mensagemSessao, 'Não foi possível confirmar sua sessão. Tente novamente antes de continuar.');
                }
            } finally {
                encerrarRequisicao(controller);
                botaoContinuarPainel.disabled = false;
            }
        });
        document.querySelector('#sair-admin').addEventListener('click', async event => {
            const controller = iniciarRequisicao();
            if (!controller) return;
            const generation = flowGeneration;
            const botao = event.currentTarget;
            limparMensagem(mensagemSessao);
            botao.disabled = true;
            try {
                const resposta = await enviarPost('/admin/auth/logout', undefined, controller.signal);
                if (generation !== flowGeneration) return;
                if (resposta.status === 204) {
                    challengeId = null;
                    formularioLogin.reset();
                    mostrarEtapa(etapaLogin, '#email-login-admin');
                } else mostrarMensagem(mensagemSessao, mensagemDeFalha(resposta.status));
            } catch {
                if (generation === flowGeneration) {
                    mostrarMensagem(mensagemSessao, 'A resposta do logout não chegou. A sessão pode ter sido encerrada; reabra o modal para consultar o estado atual.');
                }
            } finally {
                encerrarRequisicao(controller);
                botao.disabled = false;
            }
        });

        modal.addEventListener('close', () => {
            flowGeneration += 1;
            activeRequestController?.abort();
            activeRequestController = null;
            challengeId = null;
            formularioLogin.reset();
            formularioEmail.reset();
            formularioSms.reset();
            [mensagemLogin, mensagemEmail, mensagemSms, mensagemSessao].forEach(limparMensagem);
            mostrarEtapa(etapaAviso);
        });
        /* =========================================
           4. ANIMAÇÃO MATRIX
           ========================================= */

        const caracteres =
            '01アイウエオカキクケコサシスセソタチツテトナニヌネノ';

        const tamanhoFonte = 15;
        const velocidade = 0.65;

        const movimentoReduzido = window.matchMedia(
            '(prefers-reduced-motion: reduce)'
        );

        let largura = 0;
        let altura = 0;
        let proporcao = 1;

        let colunas = [];
        let animacao = null;

        function redimensionarCanvas() {

            proporcao = Math.min(
                window.devicePixelRatio || 1,
                2
            );

            largura = window.innerWidth;
            altura = window.innerHeight;

            canvas.width = Math.floor(largura * proporcao);
            canvas.height = Math.floor(altura * proporcao);

            canvas.style.width = `${largura}px`;
            canvas.style.height = `${altura}px`;

            ctx.setTransform(
                proporcao,
                0,
                0,
                proporcao,
                0,
                0
            );

            const quantidadeColunas = Math.ceil(
                largura / tamanhoFonte
            );

            colunas = Array.from(
                { length: quantidadeColunas },
                () => Math.random() * altura
            );

            ctx.clearRect(0, 0, largura, altura);
        }

        function desenharMatrix() {

            if (!modal.open || movimentoReduzido.matches) {
                pararChuvaMatrix();
                return;
            }

            // Cria o rastro dos caracteres.
            ctx.fillStyle = 'rgba(5, 12, 8, 0.12)';
            ctx.fillRect(0, 0, largura, altura);

            ctx.font = `${tamanhoFonte}px monospace`;
            ctx.fillStyle = '#008f52';

            colunas.forEach((y, indice) => {

                const caractere = caracteres.charAt(
                    Math.floor(Math.random() * caracteres.length)
                );

                const x = indice * tamanhoFonte;

                ctx.fillText(
                    caractere,
                    x,
                    y
                );

                // Reinicia a coluna ao ultrapassar a tela.
                if (
                    y > altura &&
                    Math.random() > 0.975
                ) {
                    colunas[indice] = -Math.random() * 200;
                } else {
                    colunas[indice] += tamanhoFonte * velocidade;
                }

            });

            animacao = requestAnimationFrame(desenharMatrix);
        }

        function iniciarChuvaMatrix() {

            if (
                animacao !== null ||
                movimentoReduzido.matches ||
                !modal.open
            ) {
                return;
            }

            redimensionarCanvas();

            animacao = requestAnimationFrame(desenharMatrix);
        }

        function pararChuvaMatrix() {

            if (animacao !== null) {
                cancelAnimationFrame(animacao);
                animacao = null;
            }

            ctx.clearRect(0, 0, largura, altura);
        }

        /* =========================================
           5. EVENTOS DO MODAL
           ========================================= */

        // Inicia a animação sempre que o modal for aberto.
        modal.addEventListener('close', () => {
            pararChuvaMatrix();
        });

        // Recalcula o canvas se a janela mudar de tamanho.
        window.addEventListener('resize', () => {

            if (modal.open) {
                redimensionarCanvas();
            }

        });

        // Interrompe ou reinicia conforme a preferência de movimento.
        movimentoReduzido.addEventListener('change', () => {

            if (movimentoReduzido.matches) {
                pararChuvaMatrix();
            } else if (modal.open) {
                iniciarChuvaMatrix();
            }

        });

        // Pausa a animação quando a aba fica oculta.
        document.addEventListener('visibilitychange', () => {

            if (document.hidden) {
                pararChuvaMatrix();
            } else if (modal.open) {
                iniciarChuvaMatrix();
            }

        });

        /* =========================================
           6. DETECÇÃO DE CAMPOS DE TEXTO
           ========================================= */

        function estaEmCampoDeTexto(elemento) {

            if (!(elemento instanceof Element)) {
                return false;
            }

            return Boolean(
                elemento.closest(`
                    input,
                    textarea,
                    select,
                    [contenteditable]:not([contenteditable="false"]),
                    [role="textbox"],
                    [role="searchbox"]
                `)
            ) || elemento.isContentEditable;

        }

        /* =========================================
           7. DETECÇÃO DO CÓDIGO IDDQD
           ========================================= */

        let sequenciaDigitada = '';

        document.addEventListener('keydown', (event) => {

            // Não acumula caracteres enquanto o modal estiver aberto.
            if (modal.open) {
                sequenciaDigitada = '';
                return;
            }

            // Ignora composição de texto e campos editáveis.
            if (
                event.isComposing ||
                estaEmCampoDeTexto(event.target)
            ) {
                sequenciaDigitada = '';
                return;
            }

            // Considera somente letras.
            if (!/^[a-z]$/i.test(event.key)) {
                sequenciaDigitada = '';
                return;
            }

            sequenciaDigitada += event.key.toUpperCase();

            // Mantém somente os últimos caracteres necessários.
            sequenciaDigitada = sequenciaDigitada.slice(
                -CODIGO_ACESSO.length
            );

            // Código identificado.
            if (sequenciaDigitada === CODIGO_ACESSO) {

                sequenciaDigitada = '';

                abrirModalAdmin();

            }

        });

        function abrirModalAdmin() {
            if (modal.open) return;
            modal.showModal();
            iniciarChuvaMatrix();
        }

        window.addEventListener('porto-ajuda:abrir-admin-login', abrirModalAdmin);

        /* =========================================
           8. LIMPEZA DA SEQUÊNCIA
           ========================================= */

        document.addEventListener('focusin', (event) => {

            if (estaEmCampoDeTexto(event.target)) {
                sequenciaDigitada = '';
            }

        });

        // Limpa a sequência quando a janela perde o foco.
        window.addEventListener('blur', () => {
            sequenciaDigitada = '';
        });

    }

    /* =========================================
       9. INICIALIZAÇÃO SEGURA DO DOM
       ========================================= */

    if (document.readyState === 'loading') {

        document.addEventListener(
            'DOMContentLoaded',
            inicializarAcessoAdmin,
            { once: true }
        );

    } else {

        inicializarAcessoAdmin();

    }

})();
